package dev.smallwright.worker;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkerBoundaryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rejectsDuplicateSnapshotEntriesBeforeTheyCanOverwriteSource() throws Exception {
        byte[] archive =
                rawStoredZip(
                        entry(
                                "AndroidManifest.xml",
                                "<manifest package=\"dev.smallwright.generated.counter\"/>"),
                        entry(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "final class First {}"),
                        entry("res/values/strings.xml", "<resources/>"),
                        entry(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "final class Replacement {}"));
        File scratch = temporary.newFolder("snapshot");

        IOException rejected =
                assertThrows(
                        IOException.class,
                        () -> unpackSnapshot(new ByteArrayInputStream(archive), scratch));

        assertTrue(rejected.getMessage().contains("duplicate"));
    }

    @Test
    public void boundsRealEcjDiagnosticsFromAValidSizeMalformation() throws Exception {
        File source = temporary.newFile("Probe.java");
        File classes = temporary.newFolder("classes");
        StringBuilder java = new StringBuilder("public final class Probe {\n");
        StringBuilder unknown = new StringBuilder("unknown");
        for (int i = 0; i < 200; i++) unknown.append('X');
        String longUnknown = unknown.toString();
        for (int i = 0; i < 500; i++) {
            java.append("  int value").append(i).append(" = ").append(longUnknown).append(";\n");
        }
        java.append("}\n");
        assertTrue(java.length() < 256 * 1024);
        Files.write(source.toPath(), java.toString().getBytes(StandardCharsets.UTF_8));
        File bootClasspath = new File("../toolchain/input/android/android-36.jar");
        assertTrue("Android platform file must be present", bootClasspath.isFile());

        String diagnostics = compile(source, classes, bootClasspath);

        assertTrue(
                "ECJ diagnostics leaked " + diagnostics.length() + " characters",
                diagnostics.getBytes(StandardCharsets.UTF_8).length <= 16_384);
    }

    @Test
    public void rejectsArchiveEntryOutsideScratch() throws Exception {
        byte[] archive =
                rawStoredZip(
                        entry(
                                "AndroidManifest.xml",
                                "<manifest package=\"dev.smallwright.generated.counter\"/>"),
                        entry(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "final class Main {}"),
                        entry("../outside.xml", "not a resource"));
        File scratch = temporary.newFolder("path-snapshot");

        IOException rejected =
                assertThrows(
                        IOException.class,
                        () -> unpackSnapshot(new ByteArrayInputStream(archive), scratch));

        assertTrue(rejected.getMessage().contains("disallowed path"));
        assertTrue(!new File(temporary.getRoot(), "outside.xml").exists());
    }

    @Test
    public void rejectsSnapshotWithoutZipCentralDirectory() throws Exception {
        byte[] complete =
                rawStoredZip(
                        entry(
                                "AndroidManifest.xml",
                                "<manifest package=\"dev.smallwright.generated.counter\"/>"),
                        entry(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "final class Main {}"),
                        entry("res/values/strings.xml", "<resources/>"));
        byte[] missingCentral = java.util.Arrays.copyOf(complete, complete.length - 22);
        File scratch = temporary.newFolder("no-central");

        IOException rejected =
                assertThrows(
                        IOException.class,
                        () -> unpackSnapshot(new ByteArrayInputStream(missingCentral), scratch));

        assertTrue(rejected.getMessage().contains("archive"));
    }

    @Test
    public void rejectsCompressedSnapshotOverItsInputBudget() throws Exception {
        byte[] oversized = new byte[17 * 1024 * 1024 + 1];
        File scratch = temporary.newFolder("oversized-archive");

        IOException rejected =
                assertThrows(
                        IOException.class,
                        () -> unpackSnapshot(new ByteArrayInputStream(oversized), scratch));

        assertTrue(rejected.getMessage().contains("size limit"));
    }

    @Test
    public void boundsUnicodeBinderErrorTextInBytes() {
        StringBuilder message = new StringBuilder("ERROR: ");
        for (int i = 0; i < 6000; i++) message.append("\uD83D\uDE42");

        String bounded = BoundedDiagnostics.limit(message.toString());

        assertTrue(bounded.getBytes(StandardCharsets.UTF_8).length <= BoundedDiagnostics.MAX_BYTES);
        assertTrue(bounded.endsWith("[diagnostics truncated]"));
    }

    @Test
    public void rejectsOversizedAapt2ResourceEntryBeforeBufferingIt() throws Exception {
        byte[] oversized = new byte[32 * 1024 * 1024 + 1];

        IOException rejected =
                assertThrows(
                        IOException.class, () -> readEntry(new ByteArrayInputStream(oversized)));

        assertTrue(rejected.getMessage().contains("size limit"));
    }

    @Test
    public void stopsPackagingBeforeDexMakesApkOversized() throws Exception {
        File resourceApk = temporary.newFile("resources.apk");
        byte[] resourceBytes = new byte[4096];
        new java.util.Random(24).nextBytes(resourceBytes);
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(resourceApk.toPath()))) {
            zip.putNextEntry(new ZipEntry("resources.arsc"));
            zip.write(resourceBytes);
            zip.closeEntry();
        }
        File dex = temporary.newFile("classes.dex");
        byte[] incompressible = new byte[32 * 1024 * 1024 - 1024];
        new java.util.Random(42).nextBytes(incompressible);
        Files.write(dex.toPath(), incompressible);
        File output = new File(temporary.getRoot(), "generated.apk");

        IOException rejected =
                assertThrows(IOException.class, () -> addDex(resourceApk, dex, output));

        assertTrue(rejected.getMessage().contains("size limit"));
        assertTrue(output.length() <= 32 * 1024 * 1024);
    }

    private static Entry entry(String name, String text) {
        return new Entry(name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] rawStoredZip(Entry... entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int[] offsets = new int[entries.length];
        int index = 0;
        for (Entry entry : entries) {
            offsets[index++] = output.size();
            byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(entry.contents);
            little32(output, 0x04034b50);
            little16(output, 20);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little32(output, crc.getValue());
            little32(output, entry.contents.length);
            little32(output, entry.contents.length);
            little16(output, name.length);
            little16(output, 0);
            output.write(name);
            output.write(entry.contents);
        }
        int centralStart = output.size();
        index = 0;
        for (Entry entry : entries) {
            byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(entry.contents);
            little32(output, 0x02014b50);
            little16(output, 20);
            little16(output, 20);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little32(output, crc.getValue());
            little32(output, entry.contents.length);
            little32(output, entry.contents.length);
            little16(output, name.length);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little16(output, 0);
            little32(output, 0);
            little32(output, offsets[index++]);
            output.write(name);
        }
        int centralSize = output.size() - centralStart;
        little32(output, 0x06054b50);
        little16(output, 0);
        little16(output, 0);
        little16(output, entries.length);
        little16(output, entries.length);
        little32(output, centralSize);
        little32(output, centralStart);
        little16(output, 0);
        return output.toByteArray();
    }

    private static void little16(ByteArrayOutputStream output, long value) {
        output.write((int) value);
        output.write((int) (value >>> 8));
    }

    private static void little32(ByteArrayOutputStream output, long value) {
        little16(output, value);
        little16(output, value >>> 16);
    }

    private static void unpackSnapshot(ByteArrayInputStream input, File scratch) throws Exception {
        Method method =
                BuildService.class.getDeclaredMethod(
                        "unpackSnapshot", java.io.InputStream.class, File.class);
        method.setAccessible(true);
        try {
            method.invoke(null, input, scratch);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }

    private static String compile(File source, File classes, File bootClasspath) throws Exception {
        Method method =
                BuildService.class.getDeclaredMethod("compile", File.class, File.class, File.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(null, source, classes, bootClasspath);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }

    private static byte[] readEntry(ByteArrayInputStream input) throws Exception {
        Method method =
                BuildService.class.getDeclaredMethod(
                        "readEntry", java.io.InputStream.class, byte[].class);
        method.setAccessible(true);
        try {
            return (byte[]) method.invoke(null, input, new byte[8192]);
        } catch (InvocationTargetException exception) {
            throw (Exception) exception.getCause();
        }
    }

    private static void addDex(File resourceApk, File dex, File output) throws Exception {
        BuildService.packageApk(
                resourceApk,
                java.util.List.of(dex),
                java.util.List.of(),
                java.util.Map.of(),
                output);
    }

    private static final class Entry {
        final String name;
        final byte[] contents;

        Entry(String name, byte[] contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}
