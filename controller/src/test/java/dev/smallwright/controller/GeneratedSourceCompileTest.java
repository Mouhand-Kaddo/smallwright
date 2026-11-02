package dev.smallwright.controller;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class GeneratedSourceCompileTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void defaultJavaTemplateCompilesAgainstAndroidPlatformFile() throws Exception {
        Method defaultSource = ProjectSourceStore.class.getDeclaredMethod("defaultSource");
        defaultSource.setAccessible(true);
        String source = (String) defaultSource.invoke(null);
        File sourceFile = temporaryFolder.newFile("MainActivity.java");
        File classes = temporaryFolder.newFolder("classes");
        Files.write(sourceFile.toPath(), source.getBytes(StandardCharsets.UTF_8));
        File platformFile = findToolchainFile("toolchain/input/android/android-36.jar");
        File ecj = findToolchainFile("toolchain/input/ecj-4.12.jar");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        boolean compiled;
        try (URLClassLoader loader =
                        new URLClassLoader(new java.net.URL[] {ecj.toURI().toURL()}, null);
                PrintWriter output = new PrintWriter(diagnostics, true)) {
            Class<?> compiler =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, loader);
            Class<?> progress =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.CompilationProgress", true, loader);
            Method compile =
                    compiler.getMethod(
                            "compile",
                            String[].class,
                            PrintWriter.class,
                            PrintWriter.class,
                            progress);
            String[] arguments = {
                "-1.8",
                "-proc:none",
                "-bootclasspath",
                platformFile.getAbsolutePath(),
                "-classpath",
                platformFile.getAbsolutePath(),
                "-d",
                classes.getAbsolutePath(),
                sourceFile.getAbsolutePath()
            };
            compiled = (Boolean) compile.invoke(null, arguments, output, output, null);
        }

        assertTrue(diagnostics.toString(), compiled);
        assertTrue(
                new File(classes, "dev/smallwright/generated/counter/MainActivity.class").isFile());
    }

    private static File findToolchainFile(String path) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File candidate = new File(directory, path);
            if (candidate.isFile()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IllegalStateException("Toolchain input is missing: " + path);
    }
}
