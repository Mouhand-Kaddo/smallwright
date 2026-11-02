package dev.smallwright.worker;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Keeps compiler diagnostics and Binder error responses within one fixed byte budget. */
final class BoundedDiagnostics extends OutputStream {
    static final int MAX_BYTES = 16_384;
    private static final String TRUNCATED = "\n[diagnostics truncated]";
    private static final byte[] MARKER = TRUNCATED.getBytes(StandardCharsets.UTF_8);
    // Leave room if the last captured byte cuts through one UTF-8 code point.
    private static final int CAPTURE_BYTES = MAX_BYTES - MARKER.length - 4;

    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private boolean truncated;

    @Override
    public void write(int value) {
        if (captured.size() < CAPTURE_BYTES) {
            captured.write(value);
        } else {
            truncated = true;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException();
        }
        int remaining = CAPTURE_BYTES - captured.size();
        int accepted = Math.min(length, Math.max(0, remaining));
        captured.write(bytes, offset, accepted);
        if (accepted < length) truncated = true;
    }

    String text() {
        return new String(captured.toByteArray(), StandardCharsets.UTF_8)
                + (truncated ? TRUNCATED : "");
    }

    static String limit(String message) {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) return message;
        return new String(bytes, 0, CAPTURE_BYTES, StandardCharsets.UTF_8) + TRUNCATED;
    }
}
