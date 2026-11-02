package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Accepts only the bounded, message-free report format produced by generated apps. Class and method
 * names may use any letters and digits, and the dashes Kotlin puts in mangled names.
 */
final class CrashReportImport {
    static final int MAX_BYTES = 16 * 1024;
    private static final String NAME = "[\\p{L}\\p{N}_$<>-]";
    private static final Pattern CAUSE =
            Pattern.compile("Cause [0-3]: " + NAME + "[\\p{L}\\p{N}_$<>.-]*");
    private static final Pattern FRAME =
            Pattern.compile("  at " + NAME + "[\\p{L}\\p{N}_$<>.-]*\\." + NAME + "+:-?[0-9]+");

    private CrashReportImport() {}

    static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Crash report unavailable");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int length;
        while ((length = input.read(buffer)) != -1) {
            if (bytes.size() + length > MAX_BYTES) throw new IOException("Crash report too large");
            bytes.write(buffer, 0, length);
        }
        final String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes.toByteArray()))
                            .toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Crash report is not UTF-8", invalid);
        }
        String[] lines = text.split("\n", -1);
        if (lines.length < 4
                || !"Generated app crash".equals(lines[0])
                || !"Exception messages and values omitted.".equals(lines[1])
                || !text.endsWith("\n")) throw new IOException("Unknown crash report format");
        for (int i = 2; i < lines.length - 1; i++) {
            if (!CAUSE.matcher(lines[i]).matches() && !FRAME.matcher(lines[i]).matches())
                throw new IOException("Unknown crash report line");
        }
        if (!CAUSE.matcher(lines[2]).matches()) throw new IOException("Missing crash cause");
        return text;
    }

    static String composeFeedback(String feedback, String report) {
        if (report == null || report.isEmpty())
            throw new IllegalArgumentException("Select a crash report first");
        String combined =
                "User feedback:\n"
                        + (feedback == null ? "" : feedback)
                        + "\n\nApproved generated app crash report:\n"
                        + report;
        return FeedbackRequest.compose(combined);
    }
}
