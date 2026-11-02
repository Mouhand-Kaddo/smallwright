package dev.smallwright.worker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Google's Android platform file for API 36, kept unchanged in worker-private storage. It is a
 * compile-only input for ECJ and AAPT2, checked against the pinned size and SHA-256 on every use
 * and never class-loaded.
 */
final class PlatformInput {
    static final String SHA256 = "d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a";
    static final long SIZE = 27_768_026L;

    private final File directory;

    PlatformInput(File directory) {
        this.directory = directory;
    }

    /**
     * Checks the controller's file against the pins. A copy is written only when the kept one is
     * missing or changed; a wrong file is refused either way.
     */
    void accept(InputStream input) throws IOException {
        boolean kept = matchesPins(jar());
        Files.createDirectories(directory.toPath());
        File partial = new File(directory, SHA256 + ".partial");
        try {
            MessageDigest digest = sha256();
            long total = 0;
            try (OutputStream copy = kept ? null : Files.newOutputStream(partial.toPath())) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) != -1; ) {
                    total += read;
                    if (total > SIZE)
                        throw new IOException(
                                "The Android platform files given to the compiler are larger"
                                        + " than the pinned API 36 file");
                    digest.update(buffer, 0, read);
                    if (copy != null) copy.write(buffer, 0, read);
                }
            }
            if (total != SIZE || !SHA256.equals(hex(digest.digest())))
                throw new IOException(
                        "The Android platform files given to the compiler do not match the pinned"
                                + " API 36 file");
            if (!kept)
                Files.move(
                        partial.toPath(),
                        jar().toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    /** The kept file, after checking its size and SHA-256 again. */
    File verified() throws IOException {
        if (!jar().isFile())
            throw new IOException("The compiler does not have the Android platform files");
        if (!matchesPins(jar()))
            throw new IOException("The compiler's copy of the Android platform files changed");
        return jar();
    }

    private File jar() {
        return new File(directory, SHA256 + ".jar");
    }

    private static boolean matchesPins(File file) throws IOException {
        if (!file.isFile() || file.length() != SIZE) return false;
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        return SHA256.equals(hex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            text.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return text.toString();
    }
}
