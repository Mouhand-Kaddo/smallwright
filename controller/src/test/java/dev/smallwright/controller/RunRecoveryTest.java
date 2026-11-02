package dev.smallwright.controller;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class RunRecoveryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void discardsOnlySelectedBuildPartialsAndPreservesCompleteArtifacts() throws Exception {
        File artifacts = temporary.newFolder("artifacts");
        File selected = new File(artifacts, "build_1");
        assertTrue(selected.mkdir());
        File other = new File(artifacts, "build_2");
        assertTrue(other.mkdir());
        File partial = new File(selected, "signed.partial.apk");
        File complete = new File(selected, "app.apk");
        File otherPartial = new File(other, "signed.partial.apk");
        Files.write(partial.toPath(), "partial".getBytes(StandardCharsets.UTF_8));
        Files.write(complete.toPath(), "complete".getBytes(StandardCharsets.UTF_8));
        Files.write(otherPartial.toPath(), "other".getBytes(StandardCharsets.UTF_8));
        RunRecovery.discardPartials(artifacts, "build_1");
        assertFalse(partial.exists());
        assertEquals(
                "complete",
                new String(Files.readAllBytes(complete.toPath()), StandardCharsets.UTF_8));
        assertEquals(
                "other",
                new String(Files.readAllBytes(otherPartial.toPath()), StandardCharsets.UTF_8));
        assertThrows(
                IllegalArgumentException.class,
                () -> RunRecovery.discardPartials(artifacts, "../build_2"));
    }

    @Test
    public void rejectsSymlinkedBuildDirectory() throws Exception {
        File artifacts = temporary.newFolder("artifacts");
        File elsewhere = temporary.newFolder("elsewhere");
        Files.createSymbolicLink(new File(artifacts, "build_1").toPath(), elsewhere.toPath());
        assertThrows(
                SecurityException.class, () -> RunRecovery.discardPartials(artifacts, "build_1"));
    }

    @Test
    public void reservesVersionAboveInstalledAndPreviouslyVerifiedBuilds() {
        assertEquals(12, RunRecovery.nextVersionCode(3, 11, 8));
        assertEquals(15, RunRecovery.nextVersionCode(15, 8, 12));
        assertEquals(23, RunRecovery.nextVersionCode(2, 7, 22));
        assertThrows(
                IllegalStateException.class,
                () -> RunRecovery.nextVersionCode(1, Integer.MAX_VALUE, 0));
        assertThrows(
                IllegalStateException.class,
                () -> RunRecovery.nextVersionCode(Integer.MAX_VALUE, 1, 0));
        assertThrows(
                IllegalStateException.class,
                () -> RunRecovery.nextVersionCode(1, Integer.MAX_VALUE - 1, 0));
    }
}
