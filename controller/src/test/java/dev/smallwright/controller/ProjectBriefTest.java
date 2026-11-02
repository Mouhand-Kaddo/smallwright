package dev.smallwright.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.security.ContextFilter;
import java.io.File;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectBriefTest {
    private static final String BRIEF =
            "**For**: me, to count the glasses of water I drink.\n"
                    + "- One screen with a big plus button\n"
                    + "- Remembers each day's count\n"
                    + "- Ruled out: sign-in, reminders";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void aProjectWithoutABriefReadsAsEmpty() throws Exception {
        assertEquals("", new ProjectBrief(temporary.newFolder()).read());
    }

    @Test
    public void theBriefWrittenIsTheBriefReadAndEachWriteReplacesItWhole() throws Exception {
        File project = temporary.newFolder();
        new ProjectBrief(project).write(BRIEF);
        assertEquals(BRIEF, new ProjectBrief(project).read());
        new ProjectBrief(project).write("Dark colours.\r\nNo pictures.\n\n");
        assertEquals("Dark colours.\nNo pictures.", new ProjectBrief(project).read());
    }

    @Test
    public void refusesABlankOversizedOrControlCharacterBrief() throws Exception {
        File project = temporary.newFolder();
        String largest = "é".repeat(ContextFilter.MAX_BRIEF_BYTES / 2);
        assertNull(ProjectBrief.refusal(largest));
        assertNull(ProjectBrief.refusal("Tabs\tand\nlines are fine"));
        for (String bad :
                new String[] {null, "", "  \n ", largest + "a", "Bell \u0007", "Nul \u0000"}) {
            assertNotNull(ProjectBrief.refusal(bad));
            assertThrows(
                    IllegalArgumentException.class, () -> new ProjectBrief(project).write(bad));
        }
        assertEquals("", new ProjectBrief(project).read());
    }

    @Test
    public void aSavedBriefThatIsTooLargeOrNotTextReadsAsEmpty() throws Exception {
        File project = temporary.newFolder();
        File saved = new File(project, ProjectBrief.FILE);
        Files.write(saved.toPath(), "a".repeat(ContextFilter.MAX_BRIEF_BYTES + 1).getBytes(UTF_8));
        assertEquals("", new ProjectBrief(project).read());
        Files.write(saved.toPath(), new byte[] {'o', 'k', (byte) 0xff});
        assertEquals("", new ProjectBrief(project).read());
    }

    @Test
    public void aBriefPathThatIsALinkIsNeitherReadNorWritten() throws Exception {
        File project = temporary.newFolder();
        File elsewhere = temporary.newFile();
        Files.write(elsewhere.toPath(), "Not the brief".getBytes(UTF_8));
        Files.createSymbolicLink(new File(project, ProjectBrief.FILE).toPath(), elsewhere.toPath());
        assertEquals("", new ProjectBrief(project).read());
        assertThrows(java.io.IOException.class, () -> new ProjectBrief(project).write(BRIEF));
        assertEquals("Not the brief", new String(Files.readAllBytes(elsewhere.toPath()), UTF_8));
    }

    @Test
    public void aWriteThatFailsLeavesNoPartialFileBehind() throws Exception {
        File project = temporary.newFolder();
        File inTheWay = new File(project, ProjectBrief.FILE);
        if (!inTheWay.mkdir() || !new File(inTheWay, "keep").createNewFile())
            throw new AssertionError("Could not block the brief path");

        assertThrows(java.io.IOException.class, () -> new ProjectBrief(project).write(BRIEF));

        assertFalse(new File(project, ProjectBrief.FILE + ".partial").exists());
    }

    @Test
    public void theBriefIsNoProjectFileAndNeverReachesTheBuildSnapshot() throws Exception {
        File files = temporary.newFolder("files");
        ProjectIdentity project = new ProjectRegistry(new File(files, "projects")).create("Water");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        Map<String, byte[]> before = source.readRecoverySources(0);

        source.brief().write(BRIEF);

        assertEquals(BRIEF, source.brief().read());
        assertEquals(0, source.revision());
        assertEquals(before.keySet(), source.readRecoverySources(0).keySet());
        File archive = temporary.newFile("snapshot.zip");
        try (dev.smallwright.project.ProjectStore.BuildLease ignored =
                        source.beginBuild(0, archive, 1);
                ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String text = new String(zip.getInputStream(entry).readAllBytes(), UTF_8);
                assertFalse(entry.getName(), text.contains("glasses of water"));
                assertFalse(entry.getName(), entry.getName().contains("brief"));
            }
        }
    }
}
