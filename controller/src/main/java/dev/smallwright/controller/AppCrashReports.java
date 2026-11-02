package dev.smallwright.controller;

import dev.smallwright.security.ContextFilter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The latest crash report a project's app sent with "Tell the builder" (ADR 0008). Every report
 * passes {@link CrashReportImport#read} before it is saved and again when it is read.
 */
final class AppCrashReports {
    static final String FILE = "builder-crash-report.txt";
    static final String NONE = "No crash report has been sent from this app.";
    private static final int MAX_REPORT_BYTES = 16 * 1024;
    private static final int MAX_FILE_BYTES = MAX_REPORT_BYTES + 64;

    /** A saved report and the version of the app that sent it. */
    static final class Report {
        final String text;
        final long versionCode;

        private Report(String text, long versionCode) {
            this.text = text;
            this.versionCode = versionCode;
        }
    }

    private final File projectDirectory;

    AppCrashReports(File projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    /** Checks the bytes an app sent and saves them as the latest report. */
    void receive(byte[] sent, long versionCode) throws IOException {
        if (sent == null || sent.length == 0 || sent.length > MAX_REPORT_BYTES)
            throw new IOException("Crash report missing or too large");
        if (versionCode < 1) throw new IOException("Unknown app version");
        String report = CrashReportImport.read(new ByteArrayInputStream(sent));
        Path destination = new File(projectDirectory, FILE).toPath();
        Path partial = new File(projectDirectory, FILE + ".partial").toPath();
        if (Files.isSymbolicLink(destination) || Files.isSymbolicLink(partial))
            throw new IOException("Crash report path is a link");
        Files.write(
                partial,
                ("version " + versionCode + "\n\n" + report).getBytes(StandardCharsets.UTF_8));
        Files.move(
                partial,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** The latest report, or null when none was sent or the saved one no longer passes. */
    Report latest() throws IOException {
        Path saved = new File(projectDirectory, FILE).toPath();
        byte[] bytes;
        try {
            if (Files.isSymbolicLink(saved) || Files.size(saved) > MAX_FILE_BYTES) return null;
            bytes = Files.readAllBytes(saved);
        } catch (NoSuchFileException none) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        int split = text.indexOf("\n\n");
        if (split < 0 || !text.startsWith("version ")) return null;
        try {
            long version = Long.parseLong(text.substring("version ".length(), split));
            String report =
                    CrashReportImport.read(
                            new ByteArrayInputStream(
                                    text.substring(split + 2).getBytes(StandardCharsets.UTF_8)));
            return version < 1 ? null : new Report(report, version);
        } catch (IOException | NumberFormatException unusable) {
            return null;
        }
    }

    /** What read_crash_report gives the model. */
    String forModel() throws IOException {
        Report report = latest();
        if (report == null) return NONE;
        return "The person sent this crash report from version "
                + report.versionCode
                + " of the app:\n"
                + ContextFilter.redact(report.text);
    }
}
