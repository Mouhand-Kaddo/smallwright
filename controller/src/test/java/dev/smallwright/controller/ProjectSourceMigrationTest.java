package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectSourceMigrationTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void existingEditedSourceBecomesRevisionZeroWithoutBeingOverwritten() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File legacy =
                new File(
                        files,
                        "projects/counter-alpha/source/java/dev/smallwright/generated/counter/MainActivity.java");
        Files.createDirectories(legacy.toPath().getParent());
        Files.write(legacy.toPath(), "// saved user edit\n".getBytes(StandardCharsets.UTF_8));
        File layout = new File(files, "projects/counter-alpha/source/res/layout/empty.xml");
        Files.createDirectories(layout.toPath().getParent());
        Files.write(layout.toPath(), "<View/>".getBytes(StandardCharsets.UTF_8));

        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();

        assertEquals(0, source.revision());
        assertEquals("// saved user edit\n", source.readSource());
        assertEquals(
                "// saved user edit\n",
                new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8));
        assertEquals(1, source.writeSource(0, "// next edit\n"));
        assertEquals("// next edit\n", source.readSource());
    }

    @Test
    public void legacySourceThroughSymlinkedDirectoryIsNotImported() throws Exception {
        File files = temporaryFolder.newFolder("files");
        File outside = temporaryFolder.newFolder("outside");
        File externalSource =
                new File(outside, "dev/smallwright/generated/counter/MainActivity.java");
        Files.createDirectories(externalSource.toPath().getParent());
        Files.write(externalSource.toPath(), "// outside\n".getBytes(StandardCharsets.UTF_8));
        File legacyJava = new File(files, "projects/counter-alpha/source/java");
        Files.createDirectories(legacyJava.toPath().getParent());
        Files.createSymbolicLink(legacyJava.toPath(), outside.toPath());

        ProjectSourceStore source = new ProjectSourceStore(files);

        assertThrows(SecurityException.class, source::initialize);
        assertEquals(
                "// outside\n",
                new String(Files.readAllBytes(externalSource.toPath()), StandardCharsets.UTF_8));
    }
}
