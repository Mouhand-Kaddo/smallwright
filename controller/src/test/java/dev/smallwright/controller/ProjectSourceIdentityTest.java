package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectSourceIdentityTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void newProjectsKeepSeparateSourcesAndRevisions() throws Exception {
        File files = temporaryFolder.newFolder("files");
        ProjectRegistry registry = new ProjectRegistry(new File(files, "projects"));
        ProjectIdentity first = registry.create("First");
        ProjectIdentity second = registry.create("Second");
        ProjectSourceStore one = new ProjectSourceStore(first);
        ProjectSourceStore two = new ProjectSourceStore(second);
        assertEquals(first.id(), one.projectId());
        assertEquals(first.packageName(), one.packageName());
        assertEquals(second.id(), two.projectId());
        one.initialize();
        two.initialize();

        String firstPath = "java/" + first.packageName().replace('.', '/') + "/MainActivity.java";
        String secondPath = "java/" + second.packageName().replace('.', '/') + "/MainActivity.java";
        assertTrue(first.packageName().matches("dev\\.smallwright\\.generated\\.app[0-9a-f]{32}"));
        assertTrue(second.packageName().matches("dev\\.smallwright\\.generated\\.app[0-9a-f]{32}"));
        assertFalse(first.id().equals(second.id()));
        assertTrue(
                one.readCurrentFiles(0)
                        .get(firstPath)
                        .startsWith("package " + first.packageName() + ";"));
        assertTrue(
                two.readCurrentFiles(0)
                        .get(secondPath)
                        .startsWith("package " + second.packageName() + ";"));
        assertFalse(one.readCurrentFiles(0).containsKey(secondPath));
        assertTrue(
                Files.isRegularFile(
                        first.directory()
                                .toPath()
                                .resolve("revision-store/revisions/0")
                                .resolve(firstPath)));
        assertTrue(
                Files.isRegularFile(
                        second.directory()
                                .toPath()
                                .resolve("revision-store/revisions/0")
                                .resolve(secondPath)));

        assertEquals(1, one.writeSource(0, "package " + first.packageName() + ";\n"));
        assertEquals(1, one.revision());
        assertEquals(0, two.revision());
        assertTrue(two.readSource().contains("public final class MainActivity"));
    }

    @Test
    public void generatedManifestUsesProjectPackageAndPathsCannotEscape() throws Exception {
        File files = temporaryFolder.newFolder("files");
        ProjectIdentity project = new ProjectRegistry(new File(files, "projects")).create("Sample");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String main = "java/" + project.packageName().replace('.', '/') + "/MainActivity.java";
        File archive = temporaryFolder.newFile("snapshot.zip");
        try (dev.smallwright.project.ProjectStore.BuildLease ignored =
                        source.beginBuild(0, archive, 1);
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive)) {
            String manifest =
                    new String(
                            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes(),
                            StandardCharsets.UTF_8);
            assertTrue(manifest.contains("package=\"" + project.packageName() + "\""));
            assertTrue(manifest.contains("dev.smallwright.generated.PROJECT_APP"));
            assertTrue(manifest.contains("android:label=\"Smallwright App\""));
            assertTrue(zip.getEntry(main) != null);
        }
        dev.smallwright.project.ProjectStore store =
                new dev.smallwright.project.ProjectStore(
                        new File(project.directory(), "revision-store"), project.packageName());
        assertThrows(SecurityException.class, () -> store.readFile("../escape.java"));
        assertThrows(
                SecurityException.class,
                () ->
                        store.applyPatch(
                                0,
                                Map.of("../escape.java", "bad".getBytes(StandardCharsets.UTF_8))));
        assertThrows(
                SecurityException.class,
                () ->
                        store.applyPatch(
                                0,
                                Map.of(
                                        "res/layout/../../AndroidManifest.xml",
                                        "bad".getBytes(StandardCharsets.UTF_8))));
        assertThrows(
                SecurityException.class,
                () ->
                        store.applyPatch(
                                0,
                                Map.of(
                                        "java/dev/smallwright/generated/counter/MainActivity.java",
                                        "bad".getBytes(StandardCharsets.UTF_8))));
        assertEquals(0, source.revision());
        assertTrue(source.readCurrentFiles(0).containsKey(main));
    }
}
