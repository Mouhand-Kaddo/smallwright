package dev.smallwright.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Project-private self-report; it never establishes installation or functional correctness. */
final class AppTestStatus {
    enum State {
        READY_TO_INSTALL,
        INSTALLED_UNTESTED,
        USER_ACCEPTED
    }

    private final Path record;
    private final Path base;

    AppTestStatus(File project, File trustedPrivateDirectory) {
        base = trustedPrivateDirectory.toPath().toAbsolutePath().normalize();
        record = project.toPath().toAbsolutePath().normalize().resolve("test-acceptance.txt");
        if (!record.startsWith(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Test status outside private storage");
    }

    State observe(String verifiedSha256, String installedSha256) throws IOException {
        requireHash(verifiedSha256);
        if (installedSha256 != null) requireHash(installedSha256);
        if (!verifiedSha256.equals(installedSha256)) return State.READY_TO_INSTALL;
        return verifiedSha256.equals(acceptedHash())
                ? State.USER_ACCEPTED
                : State.INSTALLED_UNTESTED;
    }

    void accept(String verifiedSha256, String installedSha256) throws IOException {
        if (observe(verifiedSha256, installedSha256) == State.READY_TO_INSTALL)
            throw new IllegalStateException("The verified build is not installed");
        Path partial = record.resolveSibling("test-acceptance.txt.partial");
        checkPath(record);
        checkPath(partial);
        try {
            try (FileOutputStream output = new FileOutputStream(partial.toFile())) {
                output.write(("1\n" + verifiedSha256 + "\n").getBytes(StandardCharsets.US_ASCII));
                output.getFD().sync();
            }
            Files.move(
                    partial,
                    record,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private String acceptedHash() throws IOException {
        checkPath(record);
        if (!Files.exists(record, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS) || Files.size(record) != 67)
            throw new IOException("Invalid test acceptance record");
        String value = new String(Files.readAllBytes(record), StandardCharsets.US_ASCII);
        if (!value.matches("1\\n[0-9a-f]{64}\\n"))
            throw new IOException("Invalid test acceptance record");
        return value.substring(2, 66);
    }

    private void checkPath(Path path) throws IOException {
        if (!path.startsWith(base)) throw new IOException("Test status outside private storage");
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Linked test acceptance path");
            if (current.equals(base)) return;
        }
        throw new IOException("Test status outside private storage");
    }

    private static void requireHash(String sha) {
        if (sha == null || !sha.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid artifact hash");
    }
}
