package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** Canonical source identity shared by manual-build and compiler-repair provenance. */
final class SourceSnapshotDigest {
    private SourceSnapshotDigest() {}

    static byte[] compute(Map<String, byte[]> source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(source).entrySet()) {
                byte[] path = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeInt(path.length);
                out.write(path);
                out.writeInt(entry.getValue().length);
                out.write(entry.getValue());
            }
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
