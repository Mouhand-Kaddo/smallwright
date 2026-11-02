package dev.smallwright.worker;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class MultipleSourceCompileTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void compilesSourcesThatReferenceEachOtherInOneEcjInvocation() throws Exception {
        File sources = temporaryFolder.newFolder("sources");
        File classes = temporaryFolder.newFolder("classes");
        File main = new File(sources, "Main.java");
        File helper = new File(sources, "Helper.java");
        Files.write(
                main.toPath(),
                ("package example;\n"
                                + "public final class Main { int value() { return new Helper().value(); } }\n")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(
                helper.toPath(),
                ("package example;\n" + "public final class Helper { int value() { return 7; } }\n")
                        .getBytes(StandardCharsets.UTF_8));
        File platform = platformJar();

        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        boolean success =
                BatchCompiler.compile(
                        BuildService.compileArguments(
                                Arrays.asList(main, helper), classes, platform),
                        new PrintWriter(diagnostics, true),
                        new PrintWriter(diagnostics, true),
                        null);

        assertTrue(diagnostics.toString(), success);
        assertTrue(new File(classes, "example/Main.class").isFile());
        assertTrue(new File(classes, "example/Helper.class").isFile());
    }

    private static File platformJar() {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File candidate = new File(directory, "toolchain/input/android/android-36.jar");
            if (candidate.isFile()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IllegalStateException("Android 36 platform file is missing");
    }
}
