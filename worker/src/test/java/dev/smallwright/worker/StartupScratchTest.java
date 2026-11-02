package dev.smallwright.worker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class StartupScratchTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void clearsOnlyWorkerScratchAfterAProcessRestart() throws Exception {
        File cache = temporary.newFolder("cache");
        File active = new File(cache, "active-build");
        File probe = new File(cache, "toolchain-probe");
        File unrelated = new File(cache, "unrelated-file");
        assertTrue(active.mkdirs());
        assertTrue(probe.mkdirs());
        Files.write(new File(active, "partial.apk").toPath(), new byte[] {1});
        Files.write(new File(probe, "partial.class").toPath(), new byte[] {2});
        Files.write(unrelated.toPath(), new byte[] {3});

        BuildService.clearStaleScratch(cache);

        assertFalse(active.exists());
        assertFalse(probe.exists());
        assertTrue(unrelated.isFile());
    }
}
