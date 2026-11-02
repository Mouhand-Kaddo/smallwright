package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SnapshotLimitsTest {
    private static final int MEBIBYTE = 1024 * 1024;
    private static final String PACKAGE =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final String JAVA =
            "java/dev/smallwright/generated/app0123456789abcdef0123456789abcdef/";
    private static final String MANIFEST =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                    + PACKAGE
                    + "\"><application/></manifest>";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void unpacksSubPackagesEveryResourceTypeAndAssets() throws Exception {
        Map<String, byte[]> files = baseFiles();
        files.put(JAVA + "ui/ListScreen.java", filled(10));
        files.put("res/drawable-xhdpi/logo.png", filled(10));
        files.put("res/menu/main_menu.xml", filled(10));
        files.put("assets/config/defaults.json", filled(10));
        File scratch = temporary.newFolder();

        unpack(snapshot(files), scratch);

        assertTrue(new File(scratch, JAVA + "ui/ListScreen.java").isFile());
        assertTrue(new File(scratch, "res/drawable-xhdpi/logo.png").isFile());
        assertTrue(new File(scratch, "assets/config/defaults.json").isFile());
    }

    @Test
    public void refusesPathsOutsideThePolicyEvenWithinTheLimits() throws Exception {
        for (String path :
                new String[] {
                    "res/unknown/x.xml",
                    "assets/../x.txt",
                    JAVA + "a/b/c/d/e/f/g/Deep.java",
                    "java/dev/smallwright/generated/counter/Other.java",
                    "libs/foo.jar"
                }) {
            Map<String, byte[]> files = baseFiles();
            files.put(path, filled(10));

            IOException refused =
                    assertThrows(
                            path,
                            IOException.class,
                            () -> unpack(snapshot(files), temporary.newFolder()));

            assertTrue(refused.getMessage(), refused.getMessage().contains("path"));
        }
    }

    @Test
    public void acceptsAFileOfExactlyOneMebibyteAndRefusesOneByteMore() throws Exception {
        Map<String, byte[]> exact = baseFiles();
        exact.put("assets/exact.bin", filled(MEBIBYTE));
        unpack(snapshot(exact), temporary.newFolder());

        Map<String, byte[]> over = baseFiles();
        over.put("assets/over.bin", filled(MEBIBYTE + 1));

        IOException refused =
                assertThrows(
                        IOException.class, () -> unpack(snapshot(over), temporary.newFolder()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("size limit"));
    }

    @Test
    public void acceptsFiveHundredFilesAndRefusesFiveHundredAndOne() throws Exception {
        Map<String, byte[]> exact = baseFiles();
        for (int i = 0; exact.size() < 500; i++) exact.put(JAVA + "pkg/C" + i + ".java", filled(1));
        assertEquals(500, exact.size());
        File scratch = temporary.newFolder();
        unpack(snapshot(exact), scratch);
        assertTrue(new File(scratch, "AndroidManifest.xml").isFile());

        Map<String, byte[]> over = new LinkedHashMap<>(exact);
        over.put("assets/one-more.txt", filled(1));

        IOException refused =
                assertThrows(
                        IOException.class, () -> unpack(snapshot(over), temporary.newFolder()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("disallowed"));
    }

    @Test
    public void acceptsSixteenMebibytesAndRefusesOneByteMore() throws Exception {
        Map<String, byte[]> exact = new LinkedHashMap<>();
        exact.put(JAVA + "MainActivity.java", filled(MEBIBYTE));
        exact.put("res/layout/empty.xml", filled(MEBIBYTE));
        for (int i = 0; i < 14; i++) exact.put("assets/blob" + i + ".bin", filled(MEBIBYTE));
        unpack(snapshot(exact), temporary.newFolder());

        Map<String, byte[]> over = new LinkedHashMap<>(exact);
        over.put("assets/one-more.bin", filled(1));

        IOException refused =
                assertThrows(
                        IOException.class, () -> unpack(snapshot(over), temporary.newFolder()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("size limit"));
    }

    @Test
    public void refusesAnArchiveBeyondItsInputBudget() {
        byte[] oversized = new byte[17 * MEBIBYTE + 1];

        IOException refused =
                assertThrows(IOException.class, () -> unpack(oversized, temporary.newFolder()));

        assertTrue(refused.getMessage().contains("size limit"));
    }

    @Test
    public void refusesAManifestBeyondItsOwnBudget() throws Exception {
        byte[] huge = (MANIFEST + " ".repeat(64 * 1024)).getBytes(StandardCharsets.UTF_8);

        IOException refused =
                assertThrows(
                        IOException.class,
                        () -> unpack(snapshot(baseFiles(), huge), temporary.newFolder()));

        assertTrue(refused.getMessage().contains("size limit"));
    }

    @Test
    public void classFilesInSubPackagesAreCollectedForDexing() throws Exception {
        File classes = temporary.newFolder();
        File base =
                new File(classes, "dev/smallwright/generated/app0123456789abcdef0123456789abcdef");
        File nested = new File(base, "ui/widgets");
        assertTrue(nested.mkdirs());
        for (File file :
                new File[] {
                    new File(base, "MainActivity.class"),
                    new File(base, "ui/ListScreen.class"),
                    new File(nested, "Chip.class")
                }) {
            assertTrue(file.createNewFile());
        }

        assertEquals(3, BuildService.generatedClassFiles(classes, PACKAGE).size());
    }

    @Test
    public void aProjectWhoseLauncherIsNotNamedMainActivityStillDexes() throws Exception {
        File classes = temporary.newFolder();
        File base =
                new File(classes, "dev/smallwright/generated/app0123456789abcdef0123456789abcdef");
        assertTrue(base.mkdirs());
        assertTrue(new File(base, "HomeActivity.class").createNewFile());
        assertEquals(1, BuildService.generatedClassFiles(classes, PACKAGE).size());
        assertThrows(
                IllegalStateException.class,
                () -> BuildService.generatedClassFiles(temporary.newFolder(), PACKAGE));
    }

    private static Map<String, byte[]> baseFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(JAVA + "MainActivity.java", filled(10));
        files.put("res/layout/empty.xml", filled(10));
        return files;
    }

    private static byte[] snapshot(Map<String, byte[]> files) throws IOException {
        return snapshot(files, MANIFEST.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] snapshot(Map<String, byte[]> files, byte[] manifest) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write(manifest);
            zip.closeEntry();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] filled(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'x');
        return bytes;
    }

    private static void unpack(byte[] archive, File scratch) throws Exception {
        Method method =
                BuildService.class.getDeclaredMethod(
                        "unpackSnapshot", java.io.InputStream.class, File.class);
        method.setAccessible(true);
        try {
            method.invoke(null, new ByteArrayInputStream(archive), scratch);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }
}
