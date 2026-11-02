package dev.smallwright.worker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The project libraries the controller gave for the next build, kept unchanged in worker-private
 * storage by SHA-256. They are compile and dex inputs only and are never class-loaded. The
 * controller gives them before every build; the build takes the selection once.
 */
final class LibraryInputs {
    static final int MAX_LIBRARIES = 300;
    static final long MAX_LIBRARY_BYTES = 16L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final String SELECTED = "selected";

    /** Opens the transferred bytes of library number {@code index}. */
    interface Opener {
        InputStream open(int index) throws IOException;
    }

    private final File directory;

    LibraryInputs(File directory) {
        this.directory = directory;
    }

    /**
     * Checks each library's bytes against its SHA-256 and the size limits, keeps a copy, and
     * selects them for the next build. Anything wrong refuses the whole set and selects nothing.
     */
    void accept(List<String> sha256, Opener opener) throws IOException {
        Files.createDirectories(directory.toPath());
        Files.deleteIfExists(new File(directory, SELECTED).toPath());
        if (sha256.size() > MAX_LIBRARIES)
            throw new IOException("More libraries were given to the compiler than it accepts");
        if (new HashSet<>(sha256).size() != sha256.size())
            throw new IOException("A library was given to the compiler twice");
        for (String hash : sha256)
            if (hash == null || !hash.matches("[0-9a-f]{64}"))
                throw new IOException("A library was given to the compiler without a valid hash");
        long total = 0;
        for (int index = 0; index < sha256.size(); index++) {
            try (InputStream input = opener.open(index)) {
                total += keep(input, sha256.get(index), MAX_TOTAL_BYTES - total);
            }
        }
        File partial = new File(directory, SELECTED + ".partial");
        Files.write(
                partial.toPath(), String.join("\n", sha256).getBytes(StandardCharsets.US_ASCII));
        Files.move(
                partial.toPath(),
                new File(directory, SELECTED).toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** The selected libraries, each checked again, and clears the selection. */
    List<File> takeSelected() throws IOException {
        File selected = new File(directory, SELECTED);
        List<File> libraries = new ArrayList<>();
        if (!selected.isFile()) return libraries;
        String text = new String(Files.readAllBytes(selected.toPath()), StandardCharsets.US_ASCII);
        Files.delete(selected.toPath());
        for (String hash : text.split("\n")) {
            if (hash.isEmpty()) continue;
            File jar = jar(hash);
            if (!hash.matches("[0-9a-f]{64}") || !jar.isFile() || !hash.equals(sha256(jar)))
                throw new IOException("The compiler's copy of a library changed");
            libraries.add(jar);
        }
        return libraries;
    }

    private long keep(InputStream input, String hash, long remaining) throws IOException {
        File partial = new File(directory, hash + ".partial");
        try {
            MessageDigest digest = digest();
            long size = 0;
            try (OutputStream copy = Files.newOutputStream(partial.toPath())) {
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = input.read(buffer)) != -1; ) {
                    size += read;
                    if (size > MAX_LIBRARY_BYTES || size > remaining)
                        throw new IOException(
                                "A library given to the compiler is larger than it accepts");
                    digest.update(buffer, 0, read);
                    copy.write(buffer, 0, read);
                }
            }
            if (!hash.equals(hex(digest.digest())))
                throw new IOException(
                        "A library's bytes do not match the hash the controller gave");
            File jar = jar(hash);
            if (!jar.isFile() || !hash.equals(sha256(jar)))
                Files.move(
                        partial.toPath(),
                        jar.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            return size;
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private File jar(String hash) {
        return new File(directory, hash + ".jar");
    }

    static String sha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
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
