package dev.smallwright.protocol;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Test;

public final class CompilerDiagnosticsTest {
    private static SortedMap<String, byte[]> sources() {
        SortedMap<String, byte[]> sources = new TreeMap<>();
        sources.put("java/p/Main.java", "abc".getBytes(StandardCharsets.UTF_8));
        sources.put("java/p/ui/List.java", "one\ntwo\nthree\n".getBytes(StandardCharsets.UTF_8));
        return sources;
    }

    private static final String DIGEST = CompilerDiagnostics.sourceSetSha256(sources());

    @Test
    public void sourceSetDigestCoversEveryPathAndItsBytes() {
        assertTrue(DIGEST.matches("[0-9a-f]{64}"));
        SortedMap<String, byte[]> renamed = new TreeMap<>(sources());
        renamed.put("java/p/Other.java", renamed.remove("java/p/Main.java"));
        assertNotEquals(DIGEST, CompilerDiagnostics.sourceSetSha256(renamed));
        SortedMap<String, byte[]> edited = sources();
        edited.put("java/p/Main.java", "abd".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(DIGEST, CompilerDiagnostics.sourceSetSha256(edited));
    }

    @Test
    public void decodesProblemsInAnyAdmittedFileAgainstTheExactSources() throws Exception {
        CompilerDiagnostics.Report report =
                CompilerDiagnostics.create(
                        DIGEST,
                        List.of(
                                new CompilerDiagnostics.Problem(0, 16777218, 40, 1),
                                new CompilerDiagnostics.Problem(1, 67108964, 50, 4)),
                        false);
        byte[] wire = report.toBytes();
        assertEquals(39 + 32, wire.length);
        CompilerDiagnostics.Report decoded = CompilerDiagnostics.decode(wire, sources());
        assertEquals(List.of("java/p/Main.java", "java/p/ui/List.java"), decoded.paths);
        assertEquals(1, decoded.problems.get(1).file);
        assertEquals(67108964, decoded.problems.get(1).id);
        assertEquals(4, decoded.problems.get(1).line);
        assertArrayEquals(wire, decoded.toBytes());
        assertThrows(UnsupportedOperationException.class, () -> decoded.problems.clear());
    }

    @Test
    public void exactWireRecordWithoutProse() throws Exception {
        byte[] wire =
                HexFormat.of()
                        .parseHex(
                                "504c444702"
                                        + DIGEST
                                        + "0001"
                                        + "00000001010000020000002800000003");
        CompilerDiagnostics.Report report = CompilerDiagnostics.decode(wire, sources());
        assertEquals(DIGEST, report.sourceSha256);
        assertFalse(report.truncated);
        assertEquals(1, report.problems.get(0).file);
        assertEquals(16777218, report.problems.get(0).id);
        assertEquals(40, report.problems.get(0).category);
        assertEquals(3, report.problems.get(0).line);
    }

    @Test
    public void malformedOrWrongSourceReportsNeverBecomeAcceptedDiagnostics() throws Exception {
        byte[] original =
                HexFormat.of()
                        .parseHex(
                                "504c444702"
                                        + DIGEST
                                        + "0001"
                                        + "00000001010000020000002800000003");
        for (int[] mutation :
                new int[][] {
                    {0, 0}, {4, 1}, {5, -1}, {37, 2}, {38, 0}, {38, 9}, {42, 2}, {50, 171},
                    {50, 41}, {54, 9}, {54, 0}
                }) {
            byte[] wire = original.clone();
            wire[mutation[0]] = mutation[1] < 0 ? (byte) ~wire[mutation[0]] : (byte) mutation[1];
            assertThrows(
                    java.io.IOException.class, () -> CompilerDiagnostics.decode(wire, sources()));
        }
        byte[] zeroId = original.clone();
        java.util.Arrays.fill(zeroId, 43, 47, (byte) 0);
        assertThrows(
                java.io.IOException.class, () -> CompilerDiagnostics.decode(zeroId, sources()));
        for (int size : new int[] {0, 54, 56, CompilerDiagnostics.MAX_BYTES + 1}) {
            byte[] wire = java.util.Arrays.copyOf(original, size);
            assertThrows(
                    java.io.IOException.class, () -> CompilerDiagnostics.decode(wire, sources()));
        }
        SortedMap<String, byte[]> other = sources();
        other.put("java/p/Extra.java", new byte[0]);
        assertThrows(java.io.IOException.class, () -> CompilerDiagnostics.decode(original, other));
        SortedMap<String, byte[]> huge = new TreeMap<>();
        huge.put("java/p/Main.java", new byte[1024 * 1024 + 1]);
        assertThrows(java.io.IOException.class, () -> CompilerDiagnostics.decode(original, huge));
        assertThrows(java.io.IOException.class, () -> CompilerDiagnostics.decode(null, sources()));
        assertThrows(java.io.IOException.class, () -> CompilerDiagnostics.decode(original, null));
        assertThrows(
                java.io.IOException.class,
                () -> CompilerDiagnostics.decode(original, new TreeMap<>()));
    }

    @Test
    public void realSignedEcjProblemIdsAndFullTruncationRemainRepresentable() throws Exception {
        CompilerDiagnostics.Problem problem =
                new CompilerDiagnostics.Problem(0, -1610612274, 70, 1);
        CompilerDiagnostics.Report report =
                CompilerDiagnostics.create(
                        DIGEST,
                        java.util.Collections.nCopies(CompilerDiagnostics.MAX_PROBLEMS, problem),
                        true);
        assertEquals(CompilerDiagnostics.MAX_BYTES, report.toBytes().length);
        CompilerDiagnostics.Report decoded =
                CompilerDiagnostics.decode(report.toBytes(), sources());
        assertTrue(decoded.truncated);
        assertEquals(CompilerDiagnostics.MAX_PROBLEMS, decoded.problems.size());
        assertEquals(-1610612274, decoded.problems.get(0).id);
    }
}
