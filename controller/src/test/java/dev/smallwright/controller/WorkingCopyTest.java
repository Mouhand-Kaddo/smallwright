package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A run's edits stay within what a build of the project, with the builder's own file, can hold. */
public final class WorkingCopyTest {
    private static final int MEGABYTE = 1024 * 1024;

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectIdentity project;
    private ProjectSourceStore source;
    private long builderBytes;
    private int builderFiles;

    @Before
    public void createProject() throws Exception {
        project = new ProjectRegistry(temporary.newFolder("projects")).create("Big");
        source = new ProjectSourceStore(project);
        source.initialize();
        Map<String, byte[]> builder = AppLinkSources.files(project.packageName(), new byte[32]);
        builderFiles = builder.size();
        for (byte[] file : builder.values()) builderBytes += file.length;
    }

    private long projectBytes(WorkingCopy files) {
        long total = 0;
        for (byte[] content : files.files().values()) total += content.length;
        return total;
    }

    private static String asset(int index) {
        return "assets/data/" + index + ".bin";
    }

    /** Fills the project with whole-megabyte assets and one asset of the rest, to this total. */
    private Map<String, byte[]> filledTo(WorkingCopy files, long total) {
        Map<String, byte[]> assets = new TreeMap<>();
        long left = total - projectBytes(files);
        for (int index = 0; left > 0; index++) {
            int size = (int) Math.min(MEGABYTE, left);
            assets.put(asset(index), new byte[size]);
            left -= size;
        }
        return assets;
    }

    @Test
    public void aWriteThatLeavesNoRoomForTheBuildersFileIsRefused() throws Exception {
        WorkingCopy files = new WorkingCopy(source, source.revision());
        long room = ProjectStore.MAX_PROJECT_BYTES - builderBytes;

        files.write(filledTo(files, room));
        IOException refused =
                assertThrows(
                        IOException.class, () -> files.write("assets/one-more.bin", new byte[1]));

        assertEquals(room, projectBytes(files));
        assertTrue(refused.getMessage(), refused.getMessage().contains("at most"));
    }

    @Test
    public void aProjectAlreadyPastTheLimitMayShrinkButNotGrow() throws Exception {
        WorkingCopy filling = new WorkingCopy(source, source.revision());
        Map<String, byte[]> past =
                filledTo(filling, ProjectStore.MAX_PROJECT_BYTES - builderBytes / 2);
        new ProjectStore(
                        new java.io.File(project.directory(), "revision-store"),
                        project.packageName())
                .applyPatch(source.revision(), past);
        WorkingCopy files = new WorkingCopy(source, source.revision());

        files.write(asset(0), new byte[MEGABYTE - 10]);
        assertArrayEquals(new byte[MEGABYTE - 10], files.read(asset(0)));

        assertThrows(IOException.class, () -> files.write(asset(0), new byte[MEGABYTE]));
        assertThrows(IOException.class, () -> files.write("assets/new.bin", new byte[1]));
    }

    @Test
    public void theFileCountLeavesRoomForTheBuildersFile() throws Exception {
        WorkingCopy files = new WorkingCopy(source, source.revision());
        Map<String, byte[]> many = new TreeMap<>();
        for (int index = files.files().size();
                index < ProjectStore.MAX_PROJECT_FILES - builderFiles;
                index++) many.put(asset(index), new byte[1]);

        files.write(many);

        assertEquals(ProjectStore.MAX_PROJECT_FILES - builderFiles, files.files().size());
        assertThrows(IOException.class, () -> files.write("assets/last.bin", new byte[1]));
    }
}
