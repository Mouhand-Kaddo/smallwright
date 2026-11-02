package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectSourceRecoveryTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void onlyUntouchedDefaultProjectCanReceiveRecovery() throws Exception {
        ProjectSourceStore sources = new ProjectSourceStore(temp.newFolder("controller"));
        sources.initialize();
        assertTrue(sources.isPristineDefaultSnapshot());
        int staged =
                sources.stageRecoverySources(
                        0,
                        Map.of(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "package dev.smallwright.generated.counter; class MainActivity {}"
                                        .getBytes(StandardCharsets.UTF_8),
                                "res/layout/empty.xml",
                                "<View/>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(sources.isPristineDefaultSnapshot());
        assertArrayEquals(
                RecoveryAdoption.sourceDigest(
                        Map.of(
                                "java/dev/smallwright/generated/counter/MainActivity.java",
                                "package dev.smallwright.generated.counter; class MainActivity {}"
                                        .getBytes(StandardCharsets.UTF_8),
                                "res/layout/empty.xml",
                                "<View/>".getBytes(StandardCharsets.UTF_8))),
                sources.validatedStagedRecoveryDigest(0, staged));
        sources.promoteRecoverySources(0, staged);
        assertFalse(sources.isPristineDefaultSnapshot());
    }

    @Test
    public void savedEditAndLegacyProjectAreNotPristine() throws Exception {
        java.io.File controller = temp.newFolder("edited");
        ProjectSourceStore sources = new ProjectSourceStore(controller);
        sources.initialize();
        sources.writeSource(0, sources.readSource() + "\n// saved edit\n");
        assertFalse(sources.isPristineDefaultSnapshot());

        java.io.File legacyController = temp.newFolder("legacy");
        java.nio.file.Path legacy =
                legacyController.toPath().resolve("projects/counter-alpha/source");
        Files.createDirectories(legacy);
        ProjectSourceStore legacySources = new ProjectSourceStore(legacyController);
        legacySources.initialize();
        assertFalse(legacySources.isPristineDefaultSnapshot());
    }
}
