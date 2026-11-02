package dev.smallwright.skills;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SkillStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    static GitHubSkills.Found found(String name, String description, String... extra)
            throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "SKILL.md",
                ("---\nname: " + name + "\ndescription: " + description + "\n---\nBody of " + name)
                        .getBytes(StandardCharsets.UTF_8));
        for (int index = 0; index < extra.length; index += 2)
            files.put(extra[index], extra[index + 1].getBytes(StandardCharsets.UTF_8));
        return new GitHubSkills.Found(SkillFile.parse(files.get("SKILL.md")), files);
    }

    @Test
    public void aSkillForOneProjectKeepsItsProjectAcrossStoresAndToggles() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("https://github.com/o/r", List.of(found("everywhere", "All.")));
        store.add("https://github.com/o/r", List.of(found("here", "One.")), "app1", "Habits");
        store.toggle("here");
        List<SkillStore.Installed> listed = new SkillStore(folder).list();
        SkillStore.Installed everywhere = listed.get(0);
        SkillStore.Installed here = listed.get(1);
        assertNull(everywhere.project);
        assertTrue(everywhere.isFor("app2"));
        assertEquals("app1", here.project);
        assertEquals("Habits", here.projectTitle);
        assertFalse(here.on);
        assertTrue(here.isFor("app1"));
        assertFalse(here.isFor("app2"));
    }

    @Test
    public void replacingAProjectsSkillFromALinkKeepsItInThatProject() throws Exception {
        SkillStore store = new SkillStore(temporary.newFolder());
        store.add("https://github.com/o/r", List.of(found("here", "One.")), "app1", "Habits");
        store.add("https://github.com/o/r2", List.of(found("here", "Newer.")));
        SkillStore.Installed here = store.list().get(0);
        assertEquals("Newer.", here.description);
        assertEquals("app1", here.project);
        assertEquals("Habits", here.projectTitle);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.add("https://github.com/o/r", List.of(found("x", "X.")), "app1", null));
    }

    @Test
    public void addsSkillsTurnedOnAndKeepsThemAcrossStores() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        assertTrue(store.list().isEmpty());
        store.add(
                "https://github.com/o/r",
                List.of(
                        found("tdd", "Test first.", "tests.md", "Good tests."),
                        found("grilling", "Ask.")));

        List<SkillStore.Installed> listed = new SkillStore(folder).list();
        assertEquals(2, listed.size());
        assertEquals("grilling", listed.get(0).name);
        SkillStore.Installed tdd = listed.get(1);
        assertEquals("Test first.", tdd.description);
        assertTrue(tdd.on);
        assertTrue(tdd.modelInvocable);
        assertEquals("https://github.com/o/r", tdd.source);
        assertEquals(List.of("SKILL.md", "tests.md"), List.copyOf(tdd.files.keySet()));
        assertArrayEquals(
                "Good tests.".getBytes(StandardCharsets.UTF_8), store.read(tdd, "tests.md"));
    }

    @Test
    public void turnsSkillsOffAndOnAndRemovesThem() throws Exception {
        SkillStore store = new SkillStore(temporary.newFolder());
        store.add("src", List.of(found("one", "One."), found("two", "Two.")));
        store.setOn("one", false);
        assertFalse(store.list().get(0).on);
        assertTrue(store.list().get(1).on);
        store.setOn("one", true);
        assertTrue(store.list().get(0).on);

        SkillStore.Installed removed = store.list().get(1);
        store.remove("two");
        assertEquals(1, store.list().size());
        assertNull(store.read(removed, "SKILL.md"));
        assertThrows(IOException.class, () -> store.setOn("two", true));
    }

    @Test
    public void addingTheSameNameReplacesTheSkillAndItsFiles() throws Exception {
        SkillStore store = new SkillStore(temporary.newFolder());
        store.add("first", List.of(found("tdd", "Old.", "old.md", "Old file.")));
        store.setOn("tdd", false);
        SkillStore.Installed old = store.list().get(0);

        store.add("second", List.of(found("tdd", "New.")));

        SkillStore.Installed replaced = store.list().get(0);
        assertEquals(1, store.list().size());
        assertEquals("New.", replaced.description);
        assertEquals("second", replaced.source);
        assertTrue(replaced.on);
        assertEquals(List.of("SKILL.md"), List.copyOf(replaced.files.keySet()));
        assertNull(
                "the old copy is gone, so a run that listed it cannot read it",
                store.read(old, "SKILL.md"));
    }

    @Test
    public void readsOnlyTheFilesTheSkillHolds() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("src", List.of(found("tdd", "Test.", "tests.md", "Tests.")));
        Files.write(
                new File(folder, "secret.md").toPath(), "secret".getBytes(StandardCharsets.UTF_8));
        SkillStore.Installed tdd = store.list().get(0);
        for (String outside :
                new String[] {
                    "../secret.md", "/etc/passwd", "scripts/run.sh", "", "tests.md/..", "TESTS.MD"
                }) assertNull(outside, store.read(tdd, outside));
    }

    @Test
    public void aDamagedListIsReportedNotIgnored() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("src", List.of(found("tdd", "Test.")));
        Files.write(
                new File(folder, "skills.json").toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, store::list);
    }

    @Test
    public void toggleFlipsASkillInOneStep() throws Exception {
        SkillStore store = new SkillStore(temporary.newFolder());
        store.add("src", List.of(found("one", "One.")));
        store.toggle("one");
        assertFalse(store.list().get(0).on);
        store.toggle("one");
        assertTrue(store.list().get(0).on);
        assertThrows(IOException.class, () -> store.toggle("none"));
    }

    @Test
    public void removeAllClearsADamagedList() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("src", List.of(found("tdd", "Test.")));
        Files.write(
                new File(folder, "skills.json").toPath(),
                "{not json".getBytes(StandardCharsets.UTF_8));
        store.removeAll();
        assertTrue(store.list().isEmpty());
        store.add("src", List.of(found("tdd", "Test.")));
        assertEquals(1, store.list().size());
    }

    @Test
    public void aFileWhoseSizeChangedIsNotRead() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("src", List.of(found("tdd", "Test.")));
        SkillStore.Installed tdd = store.list().get(0);
        Files.write(new File(new File(folder, tdd.id), "SKILL.md").toPath(), new byte[0]);
        assertNull(store.read(tdd, "SKILL.md"));
    }

    @Test
    public void savingKeepsSkillsStillBeingWritten() throws Exception {
        File folder = temporary.newFolder();
        SkillStore store = new SkillStore(folder);
        store.add("src", List.of(found("one", "One.")));
        File staging = new File(folder, "0123456789abcdef0123456789abcdef.tmp");
        assertTrue(staging.mkdirs());
        store.toggle("one");
        assertTrue(staging.isDirectory());
    }
}
