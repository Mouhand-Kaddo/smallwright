package dev.smallwright.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectRegistryTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void legacyProjectKeepsItsPathAndInstalledPackage() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        Files.createDirectory(root.toPath().resolve("counter-alpha"));
        ProjectRegistry registry = new ProjectRegistry(root);

        ProjectIdentity legacy = registry.ensureLegacyProject();

        assertEquals("counter-alpha", legacy.id());
        assertEquals("dev.smallwright.generated.counter", legacy.packageName());
        assertEquals(root.toPath().resolve("counter-alpha"), legacy.directory().toPath());
        assertEquals(legacy, new ProjectRegistry(root).get("counter-alpha"));
        assertEquals(1, new ProjectRegistry(root).list().size());
    }

    @Test
    public void createdProjectsRemainDistinctAcrossReopen() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        ProjectRegistry registry = new ProjectRegistry(root);
        ProjectIdentity first = registry.create("Shopping list");
        ProjectIdentity second = registry.create("Garden notes");

        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.packageName(), second.packageName());
        assertNotEquals(first.directory(), second.directory());
        assertTrue(first.packageName().startsWith("dev.smallwright.generated."));
        assertEquals("Shopping list", first.title());
        assertEquals(first, new ProjectRegistry(root).get(first.id()));
        assertEquals(second, new ProjectRegistry(root).get(second.id()));
        assertEquals(2, new ProjectRegistry(root).list().size());
    }

    @Test
    public void titleAndProjectCountAreBounded() throws Exception {
        ProjectRegistry registry = new ProjectRegistry(temporaryFolder.newFolder("projects"));
        assertThrows(IllegalArgumentException.class, () -> registry.create(""));
        assertThrows(IllegalArgumentException.class, () -> registry.create("x".repeat(257)));
        for (int i = 0; i < ProjectRegistry.MAX_PROJECTS; i++) registry.create("App " + i);
        assertEquals(ProjectRegistry.MAX_PROJECTS, registry.list().size());
        assertThrows(java.io.IOException.class, () -> registry.create("One more"));
    }

    @Test
    public void deletedProjectLeavesTheCatalogAndFreesItsSlot() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        ProjectRegistry registry = new ProjectRegistry(root);
        ProjectIdentity kept = registry.create("Kept");
        for (int i = 1; i < ProjectRegistry.MAX_PROJECTS; i++) registry.create("App " + i);
        ProjectIdentity doomed =
                registry.list().stream()
                        .filter(project -> !project.equals(kept))
                        .findFirst()
                        .orElseThrow();
        Files.createDirectories(doomed.directory().toPath().resolve("revision-store/revisions/1"));
        Files.write(
                doomed.directory().toPath().resolve("revision-store/revisions/1/Main.java"),
                "class Main {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        registry.unpublish(doomed.id());
        registry.purge(doomed.id());

        assertFalse(Files.exists(doomed.directory().toPath()));
        assertThrows(IllegalArgumentException.class, () -> registry.get(doomed.id()));
        assertFalse(new ProjectRegistry(root).list().contains(doomed));
        assertEquals(kept, registry.get(kept.id()));
        assertEquals(ProjectRegistry.MAX_PROJECTS - 1, registry.list().size());
        registry.create("Fits again");
        assertThrows(IllegalArgumentException.class, () -> registry.unpublish(doomed.id()));
    }

    @Test
    public void interruptedDeleteIsUnknownAndStaysListedForCleanup() throws Exception {
        ProjectRegistry registry = new ProjectRegistry(temporaryFolder.newFolder("projects"));
        ProjectIdentity legacy = registry.ensureLegacyProject();
        ProjectIdentity kept = registry.create("Kept");
        ProjectIdentity doomed = registry.create("Doomed");
        Files.createDirectories(doomed.directory().toPath().resolve("artifacts"));

        registry.unpublish(doomed.id());

        assertThrows(IllegalArgumentException.class, () -> registry.get(doomed.id()));
        assertTrue(registry.list().containsAll(List.of(legacy, kept)));
        assertEquals(2, registry.list().size());
        assertEquals(List.of(doomed.id()), registry.unpublished());
        registry.purge(doomed.id());
        registry.purge(doomed.id());
        assertEquals(List.of(), registry.unpublished());
        assertFalse(Files.exists(doomed.directory().toPath()));
        assertThrows(IllegalStateException.class, () -> registry.purge(kept.id()));
    }

    @Test
    public void legacyProjectCannotBeDeleted() throws Exception {
        ProjectRegistry registry = new ProjectRegistry(temporaryFolder.newFolder("projects"));
        ProjectIdentity legacy = registry.ensureLegacyProject();

        assertThrows(IllegalArgumentException.class, () -> registry.unpublish(legacy.id()));
        assertThrows(IllegalArgumentException.class, () -> registry.purge(legacy.id()));
        assertEquals(legacy, registry.get(legacy.id()));
    }

    @Test
    public void existingLegacyDirectoryReservesAProjectSlot() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        Files.createDirectory(root.toPath().resolve("counter-alpha"));
        ProjectRegistry registry = new ProjectRegistry(root);
        for (int i = 1; i < ProjectRegistry.MAX_PROJECTS; i++) registry.create("App " + i);

        assertThrows(java.io.IOException.class, () -> registry.create("One more"));
        assertEquals(ProjectRegistry.MAX_PROJECTS, registry.list().size() + 1);
        assertEquals("counter-alpha", registry.ensureLegacyProject().id());
        assertEquals(ProjectRegistry.MAX_PROJECTS, registry.list().size());
    }

    @Test
    public void forgedIdsAndSymlinksDoNotEscapeRoot() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        ProjectRegistry registry = new ProjectRegistry(root);
        assertThrows(IllegalArgumentException.class, () -> registry.get("../outside"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("Uppercase"));
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(
                root.toPath().resolve("app00000000000000000000000000000000"), outside);
        assertThrows(SecurityException.class, registry::list);
        assertFalse(Files.exists(outside.resolve("project.bin")));
    }

    @Test
    public void symlinkedAncestorAndCorruptMetadataFailClosed() throws Exception {
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Path link = temporaryFolder.getRoot().toPath().resolve("link");
        Files.createSymbolicLink(link, outside);
        ProjectRegistry redirected = new ProjectRegistry(link.resolve("projects").toFile());
        assertThrows(SecurityException.class, () -> redirected.create("Escaped"));
        assertFalse(Files.exists(outside.resolve("projects")));

        File root = temporaryFolder.newFolder("projects");
        ProjectIdentity created = new ProjectRegistry(root).create("Valid");
        Files.write(created.directory().toPath().resolve("project.bin"), new byte[] {1, 2, 3});
        assertThrows(SecurityException.class, () -> new ProjectRegistry(root).get(created.id()));
    }

    @Test
    public void privateBaseAcceptsSafeProjectsAndRejectsRedirectsBelowIt() throws Exception {
        File base = temporaryFolder.newFolder("private-files");
        ProjectRegistry registry = new ProjectRegistry(new File(base, "projects"), base);
        ProjectIdentity created = registry.create("Phone notes");
        assertEquals(
                created, new ProjectRegistry(new File(base, "projects"), base).get(created.id()));

        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(
                new File(base, "projects/app00000000000000000000000000000000").toPath(), outside);
        assertThrows(SecurityException.class, registry::list);
        assertThrows(
                SecurityException.class,
                () -> new ProjectRegistry(outside.resolve("projects").toFile(), base));
    }

    @Test
    public void strayIncompleteWriteDoesNotBecomeAProject() throws Exception {
        File root = temporaryFolder.newFolder("projects");
        ProjectIdentity created = new ProjectRegistry(root).create("Stable");
        Files.write(root.toPath().resolve("write-123.tmp"), new byte[] {1});
        Files.write(root.toPath().resolve("write--123.tmp"), new byte[] {1});
        List<ProjectIdentity> projects = new ProjectRegistry(root).list();
        assertEquals(List.of(created), projects);
    }
}
