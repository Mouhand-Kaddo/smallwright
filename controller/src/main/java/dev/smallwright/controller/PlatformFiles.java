package dev.smallwright.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Google's Android platform files for one Android version: downloaded once from Google, checked
 * against pinned sizes and checksums, and kept unchanged in controller-private storage.
 */
final class PlatformFiles {
    interface Progress {
        void bytes(long done, long total);
    }

    /** The download did not match what Google published. What arrived has been thrown away. */
    static final class Refused extends IOException {
        Refused(String message) {
            super(message);
        }
    }

    record Acceptance(String revision, String acceptedOn, String termsDate) {}

    record Package(
            String revision,
            String url,
            long size,
            String sha1,
            String sha256,
            String entry,
            long entrySize,
            String entrySha256) {}

    static final String TERMS_VERSION = "page dated April 28, 2026, fetched 2026-10-18";

    static final Package GOOGLE_36_R02 =
            new Package(
                    "platform-36_r02",
                    "https://dl.google.com/android/repository/platform-36_r02.zip",
                    65_878_410L,
                    "2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576",
                    "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e",
                    "android-36/android.jar",
                    27_768_026L,
                    "d9eb9da824d9e247a352f570f01e1169e725b2954bca9e283a71786c59b59f9a");

    private static final int BUFFER = 64 * 1024;
    private static final int TIMEOUT_MILLIS = 30_000;

    private final File directory;
    private final Package platform;

    PlatformFiles(File directory, Package platform) {
        this.directory = directory;
        this.platform = platform;
    }

    /** A quick check for screens: the kept jar has the pinned size. */
    boolean isPresent() {
        return jarFile().length() == platform.entrySize();
    }

    /**
     * The kept jar, after checking its size and SHA-256 again; this is what the worker is given.
     */
    File verifiedJar() throws IOException {
        if (!isPresent() || !checksums(jarFile())[1].equals(platform.entrySha256()))
            throw new IOException("The Android platform files are missing or changed");
        return jarFile();
    }

    void recordAcceptance(String acceptedOn, String termsDate) throws IOException {
        if (acceptance() != null) return;
        Files.createDirectories(directory.toPath());
        Properties record = new Properties();
        record.setProperty("revision", platform.revision());
        record.setProperty("acceptedOn", acceptedOn);
        record.setProperty("termsDate", termsDate);
        try (OutputStream out = Files.newOutputStream(acceptanceFile().toPath())) {
            record.store(out, null);
        }
    }

    Acceptance acceptance() throws IOException {
        if (!acceptanceFile().isFile()) return null;
        Properties record = new Properties();
        try (InputStream in = Files.newInputStream(acceptanceFile().toPath())) {
            record.load(in);
        }
        if (!platform.revision().equals(record.getProperty("revision"))) return null;
        return new Acceptance(
                record.getProperty("revision"),
                record.getProperty("acceptedOn"),
                record.getProperty("termsDate"));
    }

    private File acceptanceFile() {
        return new File(directory, "acceptance.properties");
    }

    void download(Progress progress) throws IOException {
        Files.createDirectories(directory.toPath());
        File part = new File(directory, "platform.zip.part");
        if (part.length() > platform.size()) Files.delete(part.toPath());
        if (part.length() < platform.size()) fetch(part, progress);
        if (part.length() < platform.size())
            throw new IOException("The download stopped at " + part.length() + " bytes");
        String[] checksums = checksums(part);
        if (!checksums[0].equals(platform.sha1()) || !checksums[1].equals(platform.sha256()))
            throw refuse(part, "The checksum does not match the one Google published");
        extract(part);
        Files.delete(part.toPath());
    }

    private void fetch(File part, Progress progress) throws IOException {
        long kept = part.length();
        HttpURLConnection connection = (HttpURLConnection) new URL(platform.url()).openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        if (kept > 0) connection.setRequestProperty("Range", "bytes=" + kept + "-");
        try {
            boolean resumed = connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL;
            if (resumed && !startsAt(connection, kept))
                throw new IOException("Google answered a different part of the file");
            long done = resumed ? kept : 0;
            if (done + connection.getContentLengthLong() > platform.size())
                throw refuse(part, "The file is larger than published");
            try (InputStream in = connection.getInputStream();
                    OutputStream out =
                            resumed
                                    ? Files.newOutputStream(
                                            part.toPath(), StandardOpenOption.APPEND)
                                    : Files.newOutputStream(part.toPath())) {
                byte[] buffer = new byte[BUFFER];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    done += read;
                    if (done > platform.size())
                        throw refuse(part, "The file is larger than published");
                    out.write(buffer, 0, read);
                    progress.bytes(done, platform.size());
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean startsAt(HttpURLConnection connection, long offset) {
        String range = connection.getHeaderField("Content-Range");
        return range != null && range.startsWith("bytes " + offset + "-");
    }

    private void extract(File zip) throws IOException {
        File target = jarFile();
        File temporary = new File(target.getPath() + ".tmp");
        Files.createDirectories(target.getParentFile().toPath());
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (!entry.getName().equals(platform.entry())) continue;
                try (OutputStream out = Files.newOutputStream(temporary.toPath())) {
                    byte[] buffer = new byte[BUFFER];
                    long written = 0;
                    for (int read; (read = in.read(buffer)) > 0; ) {
                        written += read;
                        if (written > platform.entrySize()) break;
                        out.write(buffer, 0, read);
                    }
                }
                if (temporary.length() != platform.entrySize()
                        || !checksums(temporary)[1].equals(platform.entrySha256())) {
                    Files.deleteIfExists(temporary.toPath());
                    throw refuse(zip, "The android.jar inside does not match the pinned file");
                }
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        throw refuse(zip, "The download does not contain " + platform.entry());
    }

    private Refused refuse(File part, String reason) throws IOException {
        Files.deleteIfExists(part.toPath());
        return new Refused(reason);
    }

    private static String[] checksums(File file) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[BUFFER];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    sha1.update(buffer, 0, read);
                    sha256.update(buffer, 0, read);
                }
            }
            return new String[] {hex(sha1.digest()), hex(sha256.digest())};
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

    private File jarFile() {
        return new File(directory, platform.entry());
    }
}
