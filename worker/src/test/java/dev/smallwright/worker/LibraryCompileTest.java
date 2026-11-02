package dev.smallwright.worker;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A library JAR on the compiler's class path is dexed into the app with D8. */
public final class LibraryCompileTest {
    private static final String PACKAGE = "dev.smallwright.generated.counter";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private File libraryJar(File platform) throws Exception {
        File source =
                new File(
                        temporary.newFolder("library-src", "org", "example", "notes"),
                        "Codec.java");
        Files.write(
                source.toPath(),
                ("package org.example.notes;\n"
                                + "public final class Codec {\n"
                                + "  public static String quote(String text) { return \"[\" + text + \"]\"; }\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        File classes = temporary.newFolder("library-classes");
        BuildService.compileJavaOrThrow(List.of(source), classes, platform);
        File jar = temporary.newFile("codec.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar.toPath()))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("org/example/notes/Codec.class"));
            zip.write(
                    Files.readAllBytes(
                            new File(classes, "org/example/notes/Codec.class").toPath()));
            zip.closeEntry();
        }
        return jar;
    }

    private File app(String body) throws Exception {
        File main =
                new File(
                        temporary.newFolder("java", "dev", "smallwright", "generated", "counter"),
                        "MainActivity.java");
        Files.write(
                main.toPath(),
                ("package "
                                + PACKAGE
                                + ";\n"
                                + "public final class MainActivity extends android.app.Activity {\n"
                                + body
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        return main;
    }

    @Test
    public void appCodeUsingALibraryCompilesAndTheLibraryIsDexedIntoTheApp() throws Exception {
        File platform =
                PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
        File library = libraryJar(platform);
        File main = app("  String label() { return org.example.notes.Codec.quote(\"note\"); }\n");
        File classes = temporary.newFolder("classes");
        Map<String, File> admitted = Map.of("java/MainActivity.java", main);
        BuildService.compileJavaOrThrow(
                List.of(main), classes, platform, new TreeMap<>(admitted), List.of(library));
        assertFalse(new File(classes, "org/example/notes/Codec.class").exists());
        File dex = temporary.newFolder("dex");
        BuildService.dex(
                BuildService.generatedClassFiles(classes, PACKAGE),
                platform,
                dex,
                List.of(library));
        String dexText =
                new String(
                        Files.readAllBytes(new File(dex, "classes.dex").toPath()),
                        StandardCharsets.ISO_8859_1);
        assertTrue(dexText.contains("Lorg/example/notes/Codec;"));
        assertTrue(dexText.contains("L" + PACKAGE.replace('.', '/') + "/MainActivity;"));
        assertFalse(new File(dex, "classes2.dex").exists());
    }

    @Test
    public void withoutTheLibraryTheSameCodeIsACompileError() throws Exception {
        File platform =
                PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
        File main = app("  String label() { return org.example.notes.Codec.quote(\"note\"); }\n");
        Map<String, File> admitted = Map.of("java/MainActivity.java", main);
        assertThrows(
                BuildService.JavaCompileFailure.class,
                () ->
                        BuildService.compileJavaOrThrow(
                                List.of(main),
                                temporary.newFolder("classes"),
                                platform,
                                new TreeMap<>(admitted),
                                new ArrayList<>()));
    }
}
