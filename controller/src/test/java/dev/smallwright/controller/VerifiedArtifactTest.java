package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class VerifiedArtifactTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rejectsReplacementAfterValidationEvenWhenLengthMatches() throws Exception {
        File apk = temporary.newFile("generated.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        Files.write(apk.toPath(), "tampered".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> artifact.copyTo(new ByteArrayOutputStream()));
    }

    @Test
    public void transfersExactlyTheValidatedBytes() throws Exception {
        File apk = temporary.newFile("generated.apk");
        byte[] approved = "abc".getBytes(StandardCharsets.UTF_8);
        Files.write(apk.toPath(), approved);
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        artifact.verifyUnchanged();
        artifact.copyTo(destination);

        assertArrayEquals(approved, destination.toByteArray());
        assertEquals(3, artifact.length());
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                artifact.sha256());
    }

    @Test
    public void rejectsMutationWhileCopyingToTheInstaller() throws Exception {
        File apk = temporary.newFile("generated.apk");
        Files.write(apk.toPath(), new byte[20_000]);
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        OutputStream destination =
                new OutputStream() {
                    private boolean changed;

                    @Override
                    public void write(int value) {}

                    @Override
                    public void write(byte[] bytes, int offset, int length) throws IOException {
                        if (!changed) {
                            // Change bytes not yet read after the first chunk has reached the
                            // destination.
                            try (RandomAccessFile replacement = new RandomAccessFile(apk, "rw")) {
                                replacement.seek(19_999);
                                replacement.write(1);
                            }
                            changed = true;
                        }
                    }
                };

        assertThrows(IOException.class, () -> artifact.copyTo(destination));
    }

    @Test
    public void rejectsTruncatedArtifact() throws Exception {
        File apk = temporary.newFile("generated.apk");
        Files.write(apk.toPath(), new byte[100]);
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        Files.write(apk.toPath(), new byte[99]);

        assertThrows(IOException.class, () -> artifact.copyTo(new ByteArrayOutputStream()));
    }

    @Test
    public void rejectsGrowthWithoutWritingBeyondValidatedLength() throws Exception {
        File apk = temporary.newFile("generated.apk");
        Files.write(apk.toPath(), new byte[100]);
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        Files.write(apk.toPath(), new byte[101]);
        ByteArrayOutputStream destination = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> artifact.copyTo(destination));
        assertEquals(0, destination.size());
    }
}
