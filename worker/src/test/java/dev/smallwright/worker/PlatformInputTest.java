package dev.smallwright.worker;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PlatformInputTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File googleJar;
    private PlatformInput platform;

    @Before
    public void setUp() throws IOException {
        googleJar = PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
        platform = new PlatformInput(temporaryFolder.newFolder("platform"));
    }

    @Test
    public void keepsTheGoogleJarUnchangedForBuilds() throws IOException {
        try (InputStream input = Files.newInputStream(googleJar.toPath())) {
            platform.accept(input);
        }

        assertArrayEquals(
                Files.readAllBytes(googleJar.toPath()),
                Files.readAllBytes(platform.verified().toPath()));
    }

    @Test
    public void buildsAreRefusedWhenNoPlatformFileWasGiven() {
        assertRefused(() -> platform.verified());
    }

    @Test
    public void refusesDifferentBytesOfTheSameSize() throws IOException {
        byte[] wrong = new byte[(int) googleJar.length()];
        Arrays.fill(wrong, (byte) 7);

        assertRefused(() -> platform.accept(new ByteArrayInputStream(wrong)));
        assertRefused(() -> platform.verified());
    }

    @Test
    public void refusesALongerFile() throws IOException {
        InputStream longer =
                new SequenceInputStream(
                        Files.newInputStream(googleJar.toPath()),
                        new ByteArrayInputStream(new byte[] {0}));

        assertRefused(() -> platform.accept(longer));
        assertRefused(() -> platform.verified());
    }

    @Test
    public void refusesAShorterFile() throws IOException {
        byte[] start = Arrays.copyOf(Files.readAllBytes(googleJar.toPath()), 1024);

        assertRefused(() -> platform.accept(new ByteArrayInputStream(start)));
    }

    @Test
    public void refusesAWrongFileEvenWhenTheRightOneIsKept() throws IOException {
        try (InputStream input = Files.newInputStream(googleJar.toPath())) {
            platform.accept(input);
        }

        assertRefused(() -> platform.accept(new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertTrue(platform.verified().isFile());
    }

    @Test
    public void aChangedKeptCopyIsRefusedThenReplaced() throws IOException {
        try (InputStream input = Files.newInputStream(googleJar.toPath())) {
            platform.accept(input);
        }
        File kept = platform.verified();
        byte[] changed = Files.readAllBytes(kept.toPath());
        changed[100] ^= 1;
        Files.write(kept.toPath(), changed);

        assertRefused(() -> platform.verified());
        try (InputStream input = Files.newInputStream(googleJar.toPath())) {
            platform.accept(input);
        }
        assertArrayEquals(
                Files.readAllBytes(googleJar.toPath()),
                Files.readAllBytes(platform.verified().toPath()));
    }

    private static void assertRefused(org.junit.function.ThrowingRunnable action) {
        IOException refused = assertThrows(IOException.class, action);
        assertTrue(refused.getMessage(), refused.getMessage().contains("Android platform files"));
    }
}
