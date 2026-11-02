package dev.smallwright.worker;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Android libraries (AARs) unpacked for a build: untrusted archives, checked entry by entry. */
public final class AndroidLibrariesTest {
    private static final String APP =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final List<String> PHONE_ABIS = List.of("arm64-v8a", "armeabi-v7a", "armeabi");

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static String manifest(String packageName) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<manifest"
                + " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    package=\""
                + packageName
                + "\">\n<uses-sdk android:minSdkVersion=\"21\"/></manifest>\n";
    }

    File zip(String name, Map<String, byte[]> entries) throws IOException {
        File file = new File(temporary.getRoot(), name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    private File aar(String name, String packageName, Map<String, byte[]> entries)
            throws IOException {
        Map<String, byte[]> all = new LinkedHashMap<>();
        all.put("AndroidManifest.xml", text(manifest(packageName)));
        all.putAll(entries);
        return zip(name, all);
    }

    private AndroidLibraries unpack(File... libraries) throws IOException {
        return AndroidLibraries.unpack(List.of(libraries), temporary.newFolder(), APP, PHONE_ABIS);
    }

    private String refusal(File library) {
        try {
            unpack(library);
        } catch (IOException refused) {
            return refused.getMessage();
        }
        fail("expected a refusal");
        return null;
    }

    @Test
    public void unpacksClassesResourcesAndBundledJarsAndPassesPlainJarsThrough() throws Exception {
        File plain = zip("plain.jar", Map.of("org/example/A.class", new byte[] {1}));
        File aar =
                aar(
                        "card.aar",
                        "androidx.cardview",
                        Map.of(
                                "classes.jar", text("classes"),
                                "libs/repackaged.jar", text("bundled"),
                                "res/values/values.xml", text("<resources/>"),
                                "res/drawable-v21/card.xml", text("<shape/>"),
                                "R.txt", text("int attr cardElevation 0x7f010001\n"),
                                "proguard.txt", text("-keep class *"),
                                "lint.jar", text("lint")));
        AndroidLibraries libraries = unpack(plain, aar);
        assertEquals(3, libraries.jars.size());
        assertEquals(plain, libraries.jars.get(0));
        assertEquals("classes", new String(Files.readAllBytes(libraries.jars.get(1).toPath())));
        assertEquals("bundled", new String(Files.readAllBytes(libraries.jars.get(2).toPath())));
        assertEquals(1, libraries.android.size());
        AndroidLibraries.Android card = libraries.android.get(0);
        assertEquals("androidx.cardview", card.packageName);
        assertTrue(new File(card.resources, "values/values.xml").isFile());
        assertTrue(new File(card.resources, "drawable-v21/card.xml").isFile());
        assertEquals(
                "int attr cardElevation 0x7f010001\n",
                new String(Files.readAllBytes(card.symbols.toPath())));
        for (File jar : libraries.jars) assertNotEquals("lint.jar", jar.getName());
    }

    @Test
    public void anAarWithoutClassesOrResourcesIsOnlyItsManifest() throws Exception {
        AndroidLibraries libraries =
                unpack(aar("anchor.aar", "androidx.lifecycle.runtime.anchor", Map.of()));
        assertEquals(List.of(), libraries.jars);
        assertNull(libraries.android.get(0).resources);
        assertNull(libraries.android.get(0).symbols);
    }

    @Test
    public void refusesEntriesThatEscapeOrDoNotFitTheResourceLayout() throws Exception {
        for (String name :
                new String[] {
                    "res/../escape.xml",
                    "res/values/../../escape.xml",
                    "res/values/sub/deep.xml",
                    "res/values",
                    "res//values.xml",
                    "res/Values/x.xml",
                    "res/values/.hidden.xml",
                    "libs/../classes.jar",
                    "libs/sub/x.jar",
                    "/res/values/x.xml"
                }) {
            String message = refusal(aar("bad.aar", "org.example.bad", Map.of(name, text("x"))));
            assertTrue(name + ": " + message, message.contains("org.example.bad"));
        }
    }

    @Test
    public void keepsNativeLibrariesOnlyForThePhonesAbis() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("jni/arm64-v8a/libcrypto_jni.so", new byte[] {1, 2, 3});
        entries.put("jni/armeabi-v7a/libcrypto_jni.so", new byte[] {4});
        entries.put("jni/x86_64/libcrypto_jni.so", new byte[] {5});
        entries.put("jni/riscv64/libcrypto_jni.so", new byte[] {6});
        entries.put("jni/arm64-v8a/README.txt", text("notes"));
        File first = aar("crypto.aar", "org.example.crypto", entries);
        File second =
                aar(
                        "image.aar",
                        "org.example.image",
                        Map.of("jni/arm64-v8a/libimage.so", new byte[] {7}));
        AndroidLibraries libraries = unpack(first, second);
        assertEquals(
                List.of(
                        "lib/arm64-v8a/libcrypto_jni.so",
                        "lib/arm64-v8a/libimage.so",
                        "lib/armeabi-v7a/libcrypto_jni.so"),
                List.copyOf(libraries.nativeLibraries.keySet()));
        assertArrayEquals(
                new byte[] {1, 2, 3},
                Files.readAllBytes(
                        libraries.nativeLibraries.get("lib/arm64-v8a/libcrypto_jni.so").toPath()));
        assertArrayEquals(
                new byte[] {7},
                Files.readAllBytes(
                        libraries.nativeLibraries.get("lib/arm64-v8a/libimage.so").toPath()));
        assertEquals(List.of(), libraries.jars);
    }

    @Test
    public void refusesNativeCodeOnlyForOtherAbisAndTheSameLibraryTwice() throws Exception {
        Map<String, byte[]> otherAbis = new LinkedHashMap<>();
        otherAbis.put("jni/x86_64/libx.so", new byte[] {1});
        otherAbis.put("jni/x86/libx.so", new byte[] {1});
        String message = refusal(aar("desktop.aar", "org.example.desktop", otherAbis));
        assertTrue(message, message.contains("org.example.desktop"));
        assertTrue(message, message.contains("x86, x86_64"));
        assertTrue(message, message.contains("arm64-v8a"));
        File one = aar("one.aar", "org.example.one", Map.of("jni/arm64-v8a/libx.so", text("1")));
        File two = aar("two.aar", "org.example.two", Map.of("jni/arm64-v8a/libx.so", text("2")));
        try {
            unpack(one, two);
            fail("expected a refusal");
        } catch (IOException refused) {
            assertTrue(
                    refused.getMessage(), refused.getMessage().contains("lib/arm64-v8a/libx.so"));
        }
    }

    @Test
    public void refusesNativeLibrariesOutsideTheJniLayout() throws Exception {
        for (String name :
                new String[] {
                    "jni/libx.so",
                    "jni/arm64-v8a/sub/libx.so",
                    "jni/Arm64/libx.so",
                    "jni/arm64 v8a/libx.so",
                    "jni/arm64-v8a/x.so",
                    "jni/arm64-v8a/lib.so",
                    "jni/arm64-v8a/libx.SO",
                    "jni/../lib/arm64-v8a/libx.so",
                    "lib/arm64-v8a/libx.so",
                    "arm64-v8a/libx.so",
                    "res/raw/libx.so"
                }) {
            String message =
                    refusal(aar("misplaced.aar", "org.example.misplaced", Map.of(name, text("x"))));
            assertTrue(name + ": " + message, message.contains("org.example.misplaced"));
        }
    }

    @Test
    public void refusesBadPackages() throws Exception {
        for (String packageName :
                new String[] {
                    APP, "dev.smallwright.generated.other", "not a package", "a..b", ""
                }) {
            assertNotNull(packageName, refusal(aar("bad-package.aar", packageName, Map.of())));
        }
    }

    @Test
    public void refusesArchivesThatUnpackToMoreThanTheLimit() throws Exception {
        File bomb = new File(temporary.getRoot(), "bomb.aar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bomb.toPath()))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.write(text(manifest("org.example.bomb")));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes.jar"));
            byte[] zeros = new byte[1024 * 1024];
            for (long written = 0;
                    written <= AndroidLibraries.MAX_UNPACKED_BYTES;
                    written += zeros.length) zip.write(zeros);
            zip.closeEntry();
        }
        assertTrue(bomb.length() < 2 * 1024 * 1024);
        String message = refusal(bomb);
        assertTrue(message, message.contains("more bytes than the builder accepts"));
    }

    @Test
    public void refusesOversizedManifests() throws Exception {
        StringBuilder huge = new StringBuilder(manifest("org.example.big"));
        while (huge.length() <= AndroidLibraries.MAX_MANIFEST_BYTES) huge.append("<!-- -->");
        File big = zip("big.aar", Map.of("AndroidManifest.xml", text(huge.toString())));
        String message = refusal(big);
        assertTrue(message, message.contains("manifest"));
    }

    @Test
    public void writesEachLibraryRClassWithOnlyItsOwnSymbolsAndTheAppsValues() throws Exception {
        File aar =
                aar(
                        "card.aar",
                        "androidx.cardview",
                        Map.of(
                                "res/values/values.xml",
                                text("<resources/>"),
                                "R.txt",
                                text(
                                        "int attr cardElevation 0x7f010001\n"
                                                + "int[] styleable CardView { 0x7f010001 }\n"
                                                + "int styleable CardView_cardElevation 0\n"
                                                + "int attr gone 0x7f010009\n"
                                                + "int bogus line\n")));
        AndroidLibraries libraries = unpack(aar);
        File symbols = temporary.newFile("app-R.txt");
        Files.write(
                symbols.toPath(),
                text(
                        "int attr cardElevation 0x7f030042\n"
                                + "int attr colorPrimary 0x7f030043\n"
                                + "int[] styleable CardView { 0x7f030042, 0x0101013f }\n"
                                + "int styleable CardView_cardElevation 0\n"));
        File generated = temporary.newFolder("generated");
        Map<String, File> sources = libraries.writeRClasses(symbols, generated);
        assertEquals(
                Map.of("androidx.cardview", new File(generated, "androidx/cardview/R.java")),
                sources);
        String source = new String(Files.readAllBytes(sources.get("androidx.cardview").toPath()));
        assertTrue(source, source.startsWith("package androidx.cardview;"));
        assertTrue(source, source.contains("public static final int cardElevation = 0x7f030042;"));
        assertTrue(
                source,
                source.contains(
                        "public static final int[] CardView = { 0x7f030042, 0x0101013f };"));
        assertTrue(source, source.contains("public static final int CardView_cardElevation = 0;"));
        assertFalse(source, source.contains("colorPrimary"));
        assertFalse(source, source.contains("gone"));
        assertFalse(source, source.contains("bogus"));
        File classes = temporary.newFolder("classes");
        BuildService.compileJavaOrThrow(
                List.of(sources.get("androidx.cardview")),
                classes,
                PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar"));
        List<String> compiled = new java.util.ArrayList<>();
        for (File file : BuildService.rClassFiles(classes, "androidx.cardview"))
            compiled.add(file.getName());
        java.util.Collections.sort(compiled);
        assertEquals(List.of("R$attr.class", "R$styleable.class", "R.class"), compiled);
    }

    @Test
    public void refusesAppSymbolValuesThatAreNotNumbers() throws Exception {
        File aar = aar("x.aar", "org.example.x", Map.of("R.txt", text("int id x 0x7f010001\n")));
        AndroidLibraries libraries = unpack(aar);
        File symbols = temporary.newFile("app-R.txt");
        Files.write(symbols.toPath(), text("int id x 1; static { evil(); }\n"));
        assertThrows(
                IOException.class,
                () -> libraries.writeRClasses(symbols, temporary.newFolder("generated")));
    }
}
