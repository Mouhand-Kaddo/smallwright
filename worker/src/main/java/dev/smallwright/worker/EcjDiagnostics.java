package dev.smallwright.worker;

import dev.smallwright.protocol.CompilerDiagnostics;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.batch.Main;

/**
 * Captures numeric ECJ errors for the admitted project sources, without retaining compiler prose. A
 * problem names its source by index into the admitted paths in sorted order.
 */
final class EcjDiagnostics {
    private static final int MAX_PROBLEMS = CompilerDiagnostics.MAX_PROBLEMS;
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;

    static final class Problem {
        final int file;
        final int id;
        final int category;
        final int line;

        private Problem(int file, int id, int category, int line) {
            this.file = file;
            this.id = id;
            this.category = category;
            this.line = line;
        }
    }

    static final class Report {
        final String sourceSha256;
        final List<Problem> problems;
        final boolean truncated;

        private Report(String sourceSha256, List<Problem> problems, boolean truncated) {
            this.sourceSha256 = sourceSha256;
            this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
            this.truncated = truncated;
        }
    }

    static final class Result {
        final boolean success;
        final Report diagnostics;

        private Result(boolean success, Report diagnostics) {
            this.success = success;
            this.diagnostics = diagnostics;
        }
    }

    /** {@code admitted} maps each project path, such as java/p/MainActivity.java, to its file. */
    static Result compile(String[] arguments, PrintWriter writer, SortedMap<String, File> admitted)
            throws IOException {
        if (admitted.isEmpty() || admitted.size() > CompilerDiagnostics.MAX_SOURCES)
            throw new IOException("Admitted compiler sources exceed limit");
        SortedMap<String, byte[]> bytes = new TreeMap<>();
        Map<String, Integer> indexes = new HashMap<>();
        List<Integer> lineCounts = new ArrayList<>();
        for (Map.Entry<String, File> source : admitted.entrySet()) {
            File file = source.getValue();
            if (Files.size(file.toPath()) > MAX_SOURCE_BYTES)
                throw new IOException("Admitted compiler source exceeds limit");
            byte[] content = Files.readAllBytes(file.toPath());
            if (content.length > MAX_SOURCE_BYTES)
                throw new IOException("Admitted compiler source exceeds limit");
            bytes.put(source.getKey(), content);
            indexes.put(file.getCanonicalPath(), lineCounts.size());
            lineCounts.add(lineCount(content));
        }
        Collector compiler = new Collector(writer, indexes, lineCounts);
        boolean success = compiler.compile(arguments);
        Report report =
                success || compiler.problems.isEmpty()
                        ? null
                        : new Report(
                                CompilerDiagnostics.sourceSetSha256(bytes),
                                compiler.problems,
                                compiler.truncated);
        return new Result(success, report);
    }

    private static int lineCount(byte[] bytes) {
        int lines = 1;
        for (int index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n'
                    || (bytes[index] == '\r'
                            && (index + 1 == bytes.length || bytes[index + 1] != '\n'))) lines++;
        }
        return lines;
    }

    private static final class Collector extends Main {
        private final Map<String, Integer> indexes;
        private final List<Integer> lineCounts;
        private final List<Problem> problems = new ArrayList<>();
        private boolean truncated;

        Collector(PrintWriter writer, Map<String, Integer> indexes, List<Integer> lineCounts) {
            super(writer, writer, false);
            // ECJ canonicalizes its compilation-unit filenames, including Android data aliases.
            this.indexes = indexes;
            this.lineCounts = lineCounts;
        }

        @Override
        public ICompilerRequestor getBatchRequestor() {
            ICompilerRequestor delegate = super.getBatchRequestor();
            return result -> {
                Integer file = admittedIndex(result.getFileName());
                if (file != null) {
                    CategorizedProblem[] reported = result.getProblems();
                    if (reported != null)
                        for (CategorizedProblem problem : reported) {
                            int category = problem.getCategoryID();
                            int line = problem.getSourceLineNumber();
                            // ECJ 4.12 categories are 0..170 in increments of ten. No messages or
                            // arguments.
                            if (!problem.isError()
                                    || problem.getID() == 0
                                    || category < 0
                                    || category > 170
                                    || category % 10 != 0
                                    || line < 1
                                    || line > lineCounts.get(file)) continue;
                            if (problems.size() == MAX_PROBLEMS) truncated = true;
                            else problems.add(new Problem(file, problem.getID(), category, line));
                        }
                }
                // ECJ's requestor owns error counts, logging and class output as well as
                // diagnostics.
                delegate.acceptResult(result);
            };
        }

        private Integer admittedIndex(char[] filename) {
            try {
                return indexes.get(new File(new String(filename)).getCanonicalPath());
            } catch (IOException unavailable) {
                // Fail closed for diagnostics without bypassing ECJ's real requestor.
                return null;
            }
        }
    }
}
