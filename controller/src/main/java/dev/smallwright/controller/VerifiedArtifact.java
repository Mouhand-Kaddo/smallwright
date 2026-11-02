package dev.smallwright.controller;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Retains artifact identity across validation and the user's installation decision. */
final class VerifiedArtifact {
    private final File file;
    private final long length;
    private final byte[] digest;

    private VerifiedArtifact(File file, long length, byte[] digest) {
        this.file = file;
        this.length = length;
        this.digest = digest;
    }

    // Call before validation, then check unchanged after validation succeeds.
    static VerifiedArtifact capture(File file) throws IOException {
        long length = file.length();
        if (!file.isFile() || length == 0 || length > 32 * 1024 * 1024) {
            throw new IOException("Invalid artifact size");
        }
        return new VerifiedArtifact(file, length, transfer(file, length, null));
    }

    long length() {
        return length;
    }

    File file() {
        return file;
    }

    String sha256() {
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    void verifyUnchanged() throws IOException {
        copyTo(null);
    }

    void verifyPackage(Context context, String expectedPackage) throws IOException {
        verifyUnchanged();
        PackageInfo info =
                context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
        if (info == null || expectedPackage == null || !expectedPackage.equals(info.packageName)) {
            throw new IOException("Verified artifact belongs to another project");
        }
        verifyUnchanged();
    }

    /** The caller must abandon the destination session if this method throws. */
    void copyTo(OutputStream destination) throws IOException {
        if (!MessageDigest.isEqual(digest, transfer(file, length, destination))) {
            throw new IOException("Artifact changed after verification");
        }
    }

    private static byte[] transfer(File file, long expectedLength, OutputStream destination)
            throws IOException {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            long copied = 0;
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    copied += read;
                    if (copied > expectedLength) {
                        throw new IOException("Artifact size changed after verification");
                    }
                    hash.update(buffer, 0, read);
                    if (destination != null) destination.write(buffer, 0, read);
                }
            }
            if (copied != expectedLength) {
                throw new IOException("Artifact size changed after verification");
            }
            return hash.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
