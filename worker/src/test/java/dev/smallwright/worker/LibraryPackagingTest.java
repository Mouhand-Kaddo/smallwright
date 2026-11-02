package dev.smallwright.worker;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Libraries too large for one dex file are packaged as every classesN.dex, with their resources.
 */
public final class LibraryPackagingTest {
    private static final String PACKAGE = "dev.smallwright.generated.counter";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File platform() throws IOException {
        return PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
    }

    private File zip(String name, Map<String, byte[]> entries) throws IOException {
        File file = temporary.newFile(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file.toPath()))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return file;
    }

    /** A library whose two classes hold more fields than one dex file can name. */
    private File largeLibrary() throws Exception {
        File directory = temporary.newFolder("large-src", "org", "example", "large");
        List<File> sources = new ArrayList<>();
        for (String name : new String[] {"First", "Second"}) {
            StringBuilder source =
                    new StringBuilder("package org.example.large;\npublic final class ")
                            .append(name)
                            .append(" {\n");
            for (int field = 0; field < 40_000; field++)
                source.append("  public static int f").append(field).append(";\n");
            File file = new File(directory, name + ".java");
            Files.write(
                    file.toPath(),
                    source.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
            sources.add(file);
        }
        File classes = temporary.newFolder("large-classes");
        BuildService.compileJavaOrThrow(sources, classes, platform());
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/LIBRARY.SF", new byte[] {1});
        entries.put(
                "META-INF/services/org.example.Plugin",
                "org.example.large.First\n".getBytes(StandardCharsets.UTF_8));
        entries.put(
                "org/example/large/messages.properties",
                "hello=Hello\n".getBytes(StandardCharsets.UTF_8));
        entries.put("AndroidManifest.xml", new byte[] {2});
        entries.put("res/raw/x", new byte[] {4});
        for (String name : new String[] {"First", "Second"})
            entries.put(
                    "org/example/large/" + name + ".class",
                    Files.readAllBytes(
                            new File(classes, "org/example/large/" + name + ".class").toPath()));
        return zip("large.jar", entries);
    }

    private List<File> appClasses() throws Exception {
        File main =
                new File(
                        temporary.newFolder("app", "dev", "smallwright", "generated", "counter"),
                        "MainActivity.java");
        Files.write(
                main.toPath(),
                ("package "
                                + PACKAGE
                                + ";\n"
                                + "public final class MainActivity extends android.app.Activity {\n"
                                + "  int value() { return org.example.large.Second.f39999; }\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        File classes = temporary.newFolder("app-classes");
        BuildService.compileJavaOrThrow(
                List.of(main),
                classes,
                platform(),
                new java.util.TreeMap<>(Map.of("java/MainActivity.java", main)),
                List.of(new File(temporary.getRoot(), "large.jar")));
        return BuildService.generatedClassFiles(classes, PACKAGE);
    }

    @Test
    public void anAppWhoseLibrariesNeedSeveralDexFilesShipsEveryOneOfThem() throws Exception {
        File library = largeLibrary();
        File cache = temporary.newFolder("library-dex");
        List<File> libraryDex = new LibraryDex(cache).dexFiles(List.of(library), platform());
        File dex = temporary.newFolder("dex");
        BuildService.dex(appClasses(), platform(), dex, libraryDex);
        List<File> dexFiles = BuildService.dexFiles(dex);
        assertTrue(dexFiles.toString(), dexFiles.size() >= 2);

        File resources =
                zip(
                        "resources.apk",
                        Map.of(
                                "AndroidManifest.xml", new byte[] {9},
                                "resources.arsc", new byte[] {8, 8, 8, 8}));
        File apk = new File(temporary.getRoot(), "app.apk");
        BuildService.packageApk(resources, dexFiles, List.of(library), Map.of(), apk);
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(apk)) {
            for (ZipEntry entry : Collections.list(zip.entries())) names.add(entry.getName());
            for (File file : dexFiles)
                assertArrayEquals(
                        Files.readAllBytes(file.toPath()),
                        zip.getInputStream(zip.getEntry(file.getName())).readAllBytes());
            assertArrayEquals(
                    new byte[] {9},
                    zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes());
            assertEquals(ZipEntry.STORED, zip.getEntry("resources.arsc").getMethod());
            assertEquals(
                    "org.example.large.First\n",
                    new String(
                            zip.getInputStream(zip.getEntry("META-INF/services/org.example.Plugin"))
                                    .readAllBytes(),
                            StandardCharsets.UTF_8));
        }
        assertEquals(1, Collections.frequency(names, "AndroidManifest.xml"));
        assertTrue(names.toString(), names.contains("classes2.dex"));
        assertTrue(names.toString(), names.contains("org/example/large/messages.properties"));
        assertFalse(names.toString(), names.contains("org/example/large/First.class"));
        assertFalse(names.toString(), names.contains("META-INF/MANIFEST.MF"));
        assertFalse(names.toString(), names.contains("META-INF/LIBRARY.SF"));
        assertFalse(names.toString(), names.contains("res/raw/x"));
    }

    /** Each entry's name and the offset of its data, read as zipalign does from the headers. */
    static Map<String, Long> dataOffsets(File apk) throws IOException {
        Map<String, Long> offsets = new LinkedHashMap<>();
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(apk, "r")) {
            java.nio.ByteBuffer end = read(file, file.length() - 22, 22);
            assertEquals(0x06054b50, end.getInt(0));
            long central = end.getInt(16) & 0xffffffffL;
            for (int index = 0; index < (end.getShort(10) & 0xffff); index++) {
                java.nio.ByteBuffer record = read(file, central, 46);
                assertEquals(0x02014b50, record.getInt(0));
                int nameLength = record.getShort(28) & 0xffff;
                int extraLength = record.getShort(30) & 0xffff;
                int commentLength = record.getShort(32) & 0xffff;
                long local = record.getInt(42) & 0xffffffffL;
                String name =
                        new String(
                                read(file, central + 46, nameLength).array(),
                                StandardCharsets.UTF_8);
                java.nio.ByteBuffer header = read(file, local, 30);
                assertEquals(0x04034b50, header.getInt(0));
                offsets.put(
                        name,
                        local
                                + 30
                                + (header.getShort(26) & 0xffff)
                                + (header.getShort(28) & 0xffff));
                central += 46 + nameLength + extraLength + commentLength;
            }
        }
        return offsets;
    }

    private static java.nio.ByteBuffer read(java.io.RandomAccessFile file, long offset, int length)
            throws IOException {
        byte[] bytes = new byte[length];
        file.seek(offset);
        file.readFully(bytes);
        return java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void nativeLibrariesAreStoredUncompressedAnd16KbAlignedUnderLib() throws Exception {
        File resources =
                zip(
                        "resources.apk",
                        Map.of(
                                "AndroidManifest.xml", new byte[] {9},
                                "resources.arsc", new byte[] {8, 8, 8, 8, 8}));
        File dex = temporary.newFile("classes.dex");
        Files.write(dex.toPath(), new byte[12_345]);
        Map<String, File> natives = new java.util.TreeMap<>();
        byte[][] contents = new byte[3][];
        String[] names = {
            "lib/arm64-v8a/libconscrypt_jni.so",
            "lib/arm64-v8a/libz.so",
            "lib/armeabi-v7a/libconscrypt_jni.so"
        };
        for (int index = 0; index < names.length; index++) {
            contents[index] = new byte[20_001 + index * 7_777];
            new java.util.Random(index).nextBytes(contents[index]);
            File file = temporary.newFile("native-" + index + ".so");
            Files.write(file.toPath(), contents[index]);
            natives.put(names[index], file);
        }
        File jar =
                zip(
                        "sneaky.jar",
                        Map.of(
                                "lib/arm64-v8a/libsneaky.so", new byte[] {1},
                                "natives/libsneaky.so", new byte[] {1},
                                "org/example/data.txt", new byte[] {2}));
        File apk = new File(temporary.getRoot(), "native.apk");
        BuildService.packageApk(resources, List.of(dex), List.of(jar), natives, apk);
        Map<String, Long> offsets = dataOffsets(apk);
        try (ZipFile zip = new ZipFile(apk)) {
            for (int index = 0; index < names.length; index++) {
                ZipEntry entry = zip.getEntry(names[index]);
                assertEquals(names[index], ZipEntry.STORED, entry.getMethod());
                assertEquals(contents[index].length, entry.getCompressedSize());
                assertArrayEquals(contents[index], zip.getInputStream(entry).readAllBytes());
                assertEquals(names[index], 0, offsets.get(names[index]) % 16_384);
            }
            assertEquals(ZipEntry.STORED, zip.getEntry("resources.arsc").getMethod());
            assertEquals(0, offsets.get("resources.arsc") % 4);
            assertNotNull(zip.getEntry("org/example/data.txt"));
        }
        List<String> packaged = new ArrayList<>();
        for (String name : offsets.keySet()) if (name.endsWith(".so")) packaged.add(name);
        assertEquals(List.of(names), packaged);
    }

    @Test
    public void refusesNativeCodeAmongTheAppsResourcesOrAssets() throws Exception {
        File resources =
                zip(
                        "resources.apk",
                        Map.of(
                                "AndroidManifest.xml", new byte[] {9},
                                "assets/libpayload.so", new byte[] {1}));
        File apk = new File(temporary.getRoot(), "refused.apk");
        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                BuildService.packageApk(
                                        resources, List.of(), List.of(), Map.of(), apk));
        assertTrue(refused.getMessage(), refused.getMessage().contains("assets/libpayload.so"));
    }

    @Test
    public void aLibraryIsDexedOnceAndItsKeptDexIsReused() throws Exception {
        File library = largeLibrary();
        File cache = temporary.newFolder("library-dex");
        List<File> first = new LibraryDex(cache).dexFiles(List.of(library), platform());
        long written = first.get(0).lastModified();
        Thread.sleep(20);
        List<File> again = new LibraryDex(cache).dexFiles(List.of(library), platform());
        assertEquals(first, again);
        assertEquals(written, again.get(0).lastModified());
        String[] kept = cache.list();
        assertEquals(1, kept.length);
        assertEquals(LibraryInputs.sha256(library), kept[0]);
    }

    @Test
    public void aaptsManifestClassForDeclaredPermissionsIsCompiledWithR() throws Exception {
        File generated = temporary.newFolder("generated-r");
        File directory = new File(generated, PACKAGE.replace('.', '/'));
        assertTrue(directory.mkdirs());
        File r = new File(directory, "R.java");
        Files.write(r.toPath(), new byte[] {1});
        assertEquals(List.of(r), BuildService.generatedResourceSources(generated, PACKAGE));
        File manifest = new File(directory, "Manifest.java");
        Files.write(manifest.toPath(), new byte[] {1});
        assertEquals(
                List.of(r, manifest), BuildService.generatedResourceSources(generated, PACKAGE));
        Files.write(new File(directory, "Other.java").toPath(), new byte[] {1});
        assertThrows(
                IOException.class, () -> BuildService.generatedResourceSources(generated, PACKAGE));
    }

    @Test
    public void dexOutputMustBeContiguousClassesFiles() throws Exception {
        File dex = temporary.newFolder("gap");
        Files.write(new File(dex, "classes.dex").toPath(), new byte[] {1});
        Files.write(new File(dex, "classes3.dex").toPath(), new byte[] {1});
        assertThrows(IOException.class, () -> BuildService.dexFiles(dex));
        File other = temporary.newFolder("other");
        Files.write(new File(other, "classes.dex").toPath(), new byte[] {1});
        Files.write(new File(other, "extra.bin").toPath(), new byte[] {1});
        assertThrows(IOException.class, () -> BuildService.dexFiles(other));
    }

    @Test
    public void javaResourcesLeaveOutClassesSignaturesAndPathsTheApkOwns() {
        assertTrue(
                BuildService.isJavaResource(
                        "META-INF/services/kotlinx.coroutines.internal.MainDispatcherFactory"));
        assertTrue(BuildService.isJavaResource("okhttp3/internal/publicsuffix/publicsuffixes.gz"));
        assertTrue(BuildService.isJavaResource("kotlin/kotlin.kotlin_builtins"));
        for (String name :
                new String[] {
                    "a/B.class",
                    "META-INF/MANIFEST.MF",
                    "META-INF/CERT.RSA",
                    "META-INF/x.SF",
                    "META-INF/maven/g/a/pom.xml",
                    "META-INF/versions/9/module-info.class",
                    "AndroidManifest.xml",
                    "resources.arsc",
                    "classes2.dex",
                    "res/raw/x",
                    "lib/arm64-v8a/libx.so",
                    "natives/linux-arm64/libx.so",
                    "org/example/libx.SO",
                    "assets/x",
                    "commonMain/default/linkdata/package_androidx/0_androidx.knm",
                    "nonJvmMain/default/manifest",
                    "META-INF/kotlin-project-structure-metadata.json",
                    "../escape",
                    "a/../../escape",
                    "/absolute",
                    "a//b",
                    "back\\slash"
                }) assertFalse(name, BuildService.isJavaResource(name));
    }
}
