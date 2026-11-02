package dev.smallwright.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectSourceManyFilesTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void firstRequestListsEveryProjectFileWithoutSendingItsContents() throws Exception {
        ProjectIdentity project =
                new ProjectRegistry(new File(temporaryFolder.newFolder("files"), "projects"))
                        .create("Many files");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String screen = "java/" + project.packageName().replace('.', '/') + "/ui/ListScreen.java";
        new ProjectStore(new File(project.directory(), "revision-store"), project.packageName())
                .applyPatch(
                        0,
                        Map.of(
                                screen,
                                "class ListScreen { int SCREEN_MARKER; }"
                                        .getBytes(StandardCharsets.UTF_8),
                                "assets/data/notes.txt",
                                "notes".getBytes(StandardCharsets.UTF_8)));
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String first = gate.approve(gate.preview("Add a list", 1), "Add a list", 1);
        assertTrue(first, first.contains("{\"path\":\"" + screen + "\",\"bytes\":39}"));
        assertTrue(first, first.contains("{\"path\":\"assets/data/notes.txt\",\"bytes\":5}"));
        assertFalse(first, first.contains("SCREEN_MARKER"));
    }
}
