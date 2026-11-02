package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Stages a validated APK before any document provider receives bytes. */
final class ArtifactExport {
    private ArtifactExport() {}

    static byte[] prepare(VerifiedArtifact artifact) throws IOException {
        if (artifact == null) throw new IllegalArgumentException("No verified artifact");
        artifact.verifyUnchanged();
        ByteArrayOutputStream staged = new ByteArrayOutputStream((int) artifact.length());
        artifact.copyTo(staged);
        return staged.toByteArray();
    }
}
