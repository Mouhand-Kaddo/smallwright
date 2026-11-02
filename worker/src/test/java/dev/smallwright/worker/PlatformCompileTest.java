package dev.smallwright.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PlatformCompileTest {
    private static final String PACKAGE = "dev.smallwright.generated.counter";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void generatedActivityUsingPlatformApisAndLambdasCompilesAndDexes() throws Exception {
        File platform = repositoryFile("toolchain/input/android/android-36.jar");
        String source =
                new String(
                                Files.readAllBytes(
                                        repositoryFile(
                                                        "controller/src/androidTest/assets/platform-fixtures/MainActivity.java")
                                                .toPath()),
                                StandardCharsets.UTF_8)
                        .replace("$PACKAGE", PACKAGE);
        File main = new File(temporaryFolder.newFolder("java"), "MainActivity.java");
        Files.write(main.toPath(), source.getBytes(StandardCharsets.UTF_8));
        File classes = temporaryFolder.newFolder("classes");
        List<File> sources = new ArrayList<>();
        sources.add(main);
        sources.addAll(
                LambdaStubs.write(
                        PlatformCompileTest::stubAsset, temporaryFolder.newFolder("scratch")));

        BuildService.compileJavaOrThrow(
                sources,
                classes,
                platform,
                new java.util.TreeMap<>(java.util.Map.of("java/MainActivity.java", main)));
        File dex = temporaryFolder.newFolder("dex");
        BuildService.dex(BuildService.generatedClassFiles(classes, PACKAGE), platform, dex);

        String dexText =
                new String(
                        Files.readAllBytes(new File(dex, "classes.dex").toPath()),
                        StandardCharsets.ISO_8859_1);
        assertTrue("D8 must desugar the lambda", dexText.contains("ExternalSyntheticLambda0"));
        assertTrue(
                "D8 must desugar the method reference",
                dexText.contains("ExternalSyntheticLambda1"));
        assertFalse(
                "The lambda stubs must not reach the app", dexText.contains("LambdaMetafactory"));
    }

    static java.io.InputStream stubAsset(String path) throws java.io.IOException {
        return Files.newInputStream(repositoryFile("worker/src/main/assets/" + path).toPath());
    }

    static File repositoryFile(String path) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File candidate = new File(directory, path);
            if (candidate.isFile()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IllegalStateException("Repository file is missing: " + path);
    }
}
