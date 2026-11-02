package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ArtifactExportTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void stagesOnlyTheVerifiedApkBytes() throws Exception {
        File apk = temporary.newFile("signed.apk");
        byte[] original = "signed artifact".getBytes(StandardCharsets.UTF_8);
        Files.write(apk.toPath(), original);
        VerifiedArtifact verified = VerifiedArtifact.capture(apk);

        byte[] staged = ArtifactExport.prepare(verified);
        assertArrayEquals(original, staged);
        Files.write(apk.toPath(), "later mutation".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(original, staged);
    }

    @Test
    public void rejectsMutationBeforeDestinationCanBeOpened() throws Exception {
        File apk = temporary.newFile("signed.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        VerifiedArtifact verified = VerifiedArtifact.capture(apk);
        Files.write(apk.toPath(), "tampered".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> ArtifactExport.prepare(verified));
    }

    @Test
    public void rejectsMissingArtifact() {
        assertThrows(IllegalArgumentException.class, () -> ArtifactExport.prepare(null));
    }
}
