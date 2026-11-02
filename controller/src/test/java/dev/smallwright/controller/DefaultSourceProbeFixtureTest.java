package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;

/** Keeps the worker's disposable compile fixture byte-for-byte equal to the default project. */
public final class DefaultSourceProbeFixtureTest {
    @Test
    public void workerProbeUsesExactDefaultSource() throws Exception {
        Method method = ProjectSourceStore.class.getDeclaredMethod("defaultSource");
        method.setAccessible(true);
        byte[] actual = ((String) method.invoke(null)).getBytes(StandardCharsets.UTF_8);
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File fixture =
                    new File(
                            directory,
                            "worker/src/androidTest/assets/default-generated-MainActivity.java");
            if (fixture.isFile()) {
                assertArrayEquals(actual, Files.readAllBytes(fixture.toPath()));
                return;
            }
            directory = directory.getParentFile();
        }
        throw new AssertionError("Worker default-source probe fixture is missing");
    }
}
