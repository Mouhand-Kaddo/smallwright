package dev.smallwright.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Fixed-size numeric ECJ records for a build's Java sources; never accepts diagnostic prose,
 * identifiers or paths. A problem names its file by index into the sources sorted by path.
 */
public final class CompilerDiagnostics {
    private static final int MAGIC = 0x504c4447;
    private static final int VERSION = 2;
    public static final int MAX_PROBLEMS = 16;
    public static final int MAX_SOURCES = 500;
    public static final int MAX_BYTES = 39 + 16 * MAX_PROBLEMS;
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;

    private CompilerDiagnostics() {}

    public static final class Problem {
        public final int file, id, category, line;

        public Problem(int file, int id, int category, int line) {
            if (file < 0
                    || file >= MAX_SOURCES
                    || id == 0
                    || category < 0
                    || category > 170
                    || category % 10 != 0
                    || line < 1
                    || line > MAX_SOURCE_BYTES + 1) {
                throw new IllegalArgumentException("Invalid numeric compiler problem");
            }
            this.file = file;
            this.id = id;
            this.category = category;
            this.line = line;
        }
    }

    public static final class Report {
        public final String sourceSha256;
        public final List<Problem> problems;
        public final boolean truncated;

        /** The checked sources' paths, sorted, when decoded against them; otherwise empty. */
        public final List<String> paths;

        private final byte[] sourceDigest;

        private Report(
                byte[] digest, List<Problem> problems, boolean truncated, List<String> paths) {
            if (digest.length != 32
                    || problems == null
                    || problems.isEmpty()
                    || problems.size() > MAX_PROBLEMS
                    || truncated && problems.size() != MAX_PROBLEMS) {
                throw new IllegalArgumentException("Invalid compiler report");
            }
            for (Problem problem : problems) {
                if (problem == null) throw new IllegalArgumentException("Invalid compiler problem");
            }
            this.sourceDigest = digest.clone();
            this.sourceSha256 = hex(digest);
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
            this.truncated = truncated;
            this.paths = List.copyOf(paths);
        }

        public byte[] toBytes() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(MAX_BYTES);
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    out.writeInt(MAGIC);
                    out.writeByte(VERSION);
                    out.write(sourceDigest);
                    out.writeByte(truncated ? 1 : 0);
                    out.writeByte(problems.size());
                    for (Problem problem : problems) {
                        out.writeInt(problem.file);
                        out.writeInt(problem.id);
                        out.writeInt(problem.category);
                        out.writeInt(problem.line);
                    }
                }
                return bytes.toByteArray();
            } catch (IOException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }

    /**
     * The digest that binds a report to these exact sources: SHA-256 over each path in order, a
     * zero byte, and the SHA-256 of its bytes.
     */
    public static String sourceSetSha256(SortedMap<String, byte[]> sources) {
        if (sources == null || sources.isEmpty() || sources.size() > MAX_SOURCES)
            throw new IllegalArgumentException("Invalid compiler sources");
        ByteArrayOutputStream set = new ByteArrayOutputStream();
        for (Map.Entry<String, byte[]> source : sources.entrySet()) {
            if (source.getValue() == null || source.getValue().length > MAX_SOURCE_BYTES)
                throw new IllegalArgumentException("Invalid compiler source");
            byte[] path = source.getKey().getBytes(StandardCharsets.UTF_8);
            set.write(path, 0, path.length);
            set.write(0);
            set.write(sha256(source.getValue()), 0, 32);
        }
        return hex(sha256(set.toByteArray()));
    }

    public static Report create(String sourceSha256, List<Problem> problems, boolean truncated) {
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid compiler source digest");
        }
        byte[] digest = new byte[32];
        for (int index = 0; index < digest.length; index++) {
            digest[index] =
                    (byte) Integer.parseInt(sourceSha256.substring(index * 2, index * 2 + 2), 16);
        }
        return new Report(digest, problems, truncated, List.of());
    }

    /** Caller must supply the actual admitted sources from its still-held build lease. */
    public static Report decode(byte[] wire, SortedMap<String, byte[]> admittedSources)
            throws IOException {
        if (wire == null || wire.length < 55 || wire.length > MAX_BYTES)
            throw new IOException("Invalid compiler report bounds");
        String expected;
        try {
            expected = sourceSetSha256(admittedSources);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid compiler report sources", invalid);
        }
        List<String> paths = new ArrayList<>(admittedSources.keySet());
        List<Integer> lines = new ArrayList<>(paths.size());
        for (byte[] source : admittedSources.values()) lines.add(lineCount(source));
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(wire))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != VERSION) {
                throw new IOException("Unsupported compiler report");
            }
            byte[] digest = new byte[32];
            in.readFully(digest);
            if (!hex(digest).equals(expected)) {
                throw new IOException("Compiler report source mismatch");
            }
            int flag = in.readUnsignedByte();
            int count = in.readUnsignedByte();
            if (flag > 1 || count < 1 || count > MAX_PROBLEMS || wire.length != 39 + count * 16) {
                throw new IOException("Invalid compiler report framing");
            }
            List<Problem> problems = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Problem problem =
                        new Problem(in.readInt(), in.readInt(), in.readInt(), in.readInt());
                if (problem.file >= paths.size() || problem.line > lines.get(problem.file))
                    throw new IOException("Compiler report line exceeds source");
                problems.add(problem);
            }
            return new Report(digest, problems, flag == 1, paths);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid numeric compiler report", invalid);
        }
    }

    private static int lineCount(byte[] source) {
        int lines = 1;
        for (int index = 0; index < source.length; index++) {
            if (source[index] == '\n'
                    || source[index] == '\r'
                            && (index + 1 == source.length || source[index + 1] != '\n')) lines++;
        }
        return lines;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String hex(byte[] digest) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) result.append(hex[(value & 255) >>> 4]).append(hex[value & 15]);
        return result.toString();
    }
}
