package dev.smallwright.worker;

import static org.junit.Assert.*;

import dev.smallwright.protocol.CompilerDiagnostics;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Actual ECJ execution at the approved Java-compile seam, never diagnostic prose parsing. */
public final class CompileDiagnosticsTest {
    private static final String MAIN = "java/p/MainActivity.java";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static SortedMap<String, File> admitted(String path, File file) {
        return new TreeMap<>(Map.of(path, file));
    }

    @Test
    public void malformedMainReturnsSourceBoundNumericErrorsAndSamePrivateSentinel()
            throws Exception {
        File source = temporary.newFile("MainActivity.java");
        byte[] bytes =
                ("final class MainActivity {\n"
                                + "  void run() { int PRIVATE_SOURCE_MARKER = ; }\n}\n")
                        .getBytes(StandardCharsets.UTF_8);
        Files.write(source.toPath(), bytes);
        File classes = temporary.newFolder("classes");
        File boot = PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
        List<File> sources = new ArrayList<>(List.of(source));
        BuildService.JavaCompileFailure failure =
                assertThrows(
                        BuildService.JavaCompileFailure.class,
                        () ->
                                BuildService.compileJavaOrThrow(
                                        sources, classes, boot, admitted(MAIN, source)));

        assertEquals("COMPILE_ERROR", failure.getMessage());
        assertEquals("COMPILE_ERROR", BuildService.failureResult(failure));
        assertNotNull(failure.diagnostics);
        assertEquals(
                CompilerDiagnostics.sourceSetSha256(new TreeMap<>(Map.of(MAIN, bytes))),
                failure.diagnostics.sourceSha256);
        assertFalse(failure.diagnostics.problems.isEmpty());
        assertTrue(
                failure.diagnostics.problems.stream()
                        .anyMatch(
                                problem ->
                                        problem.file == 0
                                                && problem.category == 20
                                                && problem.line == 2
                                                && problem.id != 0));
        assertFalse(failure.diagnostics.truncated);
    }

    @Test
    public void realUnresolvedTypeHasPinnedEcjIdAndNoMutableReport() throws Exception {
        Compilation fixture =
                fixture("final class MainActivity {\n  PRIVATE_MISSING_TYPE value;\n}\n");
        BuildService.JavaCompileFailure failure = fixture.failure();
        assertNotNull(failure.diagnostics);
        assertTrue(
                failure.diagnostics.problems.stream()
                        .anyMatch(
                                problem ->
                                        problem.id == 16777218
                                                && problem.category == 40
                                                && problem.line == 2));
        assertThrows(
                UnsupportedOperationException.class, () -> failure.diagnostics.problems.clear());
        assertEquals("COMPILE_ERROR", BuildService.failureResult(failure));
    }

    @Test
    public void sourceThroughDirectoryAliasRetainsNumericDiagnostics() throws Exception {
        Compilation fixture =
                fixture("final class MainActivity {\n  PRIVATE_MISSING_TYPE value;\n}\n");
        File alias = new File(temporary.getRoot(), "source-alias");
        Files.createSymbolicLink(alias.toPath(), fixture.main.getParentFile().toPath());
        File aliasedMain = new File(alias, fixture.main.getName());
        fixture.sources.remove(fixture.main);
        fixture.sources.add(aliasedMain);
        BuildService.JavaCompileFailure failure =
                assertThrows(
                        BuildService.JavaCompileFailure.class,
                        () ->
                                BuildService.compileJavaOrThrow(
                                        fixture.sources,
                                        fixture.classes,
                                        fixture.boot,
                                        admitted(MAIN, aliasedMain)));
        assertNotNull(failure.diagnostics);
        assertTrue(
                failure.diagnostics.problems.stream()
                        .anyMatch(
                                problem ->
                                        problem.id == 16777218
                                                && problem.category == 40
                                                && problem.line == 2));
    }

    @Test
    public void errorsInAnotherAdmittedSourceNameThatSourceByIndex() throws Exception {
        Compilation fixture = fixture("final class MainActivity { int value() { return 1; } }\n");
        File helper = new File(fixture.main.getParentFile(), "Helper.java");
        Files.write(
                helper.toPath(),
                "class Helper {\n  HELPER_MISSING_TYPE value;\n}\n"
                        .getBytes(StandardCharsets.UTF_8));
        fixture.sources.add(helper);
        fixture.admitted.put("java/p/ui/Helper.java", helper);
        EcjDiagnostics.Report report = fixture.failure().diagnostics;
        assertNotNull(report);
        assertEquals(1, report.problems.size());
        assertEquals(1, report.problems.get(0).file);
        assertEquals(16777218, report.problems.get(0).id);
        assertEquals(2, report.problems.get(0).line);
    }

    @Test
    public void errorsInSourcesThatAreNotAdmittedAreNotReported() throws Exception {
        Compilation fixture = fixture("final class MainActivity { int value() { return 1; } }\n");
        File foreign = new File(fixture.main.getParentFile(), "Helper.java");
        Files.write(
                foreign.toPath(),
                "class Helper { FOREIGN_PRIVATE_TYPE value; }\n".getBytes(StandardCharsets.UTF_8));
        fixture.sources.add(foreign);
        assertNull(fixture.failure().diagnostics);
    }

    @Test
    public void errorsBeyondTheLimitAreBoundedAndExplicitlyTruncated() throws Exception {
        StringBuilder source = new StringBuilder("final class MainActivity {\n");
        for (int index = 0; index < 20; index++) {
            source.append("  PRIVATE_MISSING_")
                    .append(index)
                    .append(" field")
                    .append(index)
                    .append(";\n");
        }
        source.append("}\n");
        EcjDiagnostics.Report report = fixture(source.toString()).failure().diagnostics;
        assertNotNull(report);
        assertEquals(CompilerDiagnostics.MAX_PROBLEMS, report.problems.size());
        assertTrue(report.truncated);
        for (EcjDiagnostics.Problem problem : report.problems) {
            assertEquals(16777218, problem.id);
            assertEquals(40, problem.category);
            assertTrue(problem.line >= 2 && problem.line <= 21);
        }
    }

    @Test
    public void delegatedRequestorEmitsRealClassesForValidSourceWithWarnings() throws Exception {
        Compilation fixture =
                fixture(
                        "final class MainActivity {\n"
                                + "  int value() { int unused = 2; return 1; }\n}\n");
        BuildService.compileJavaOrThrow(
                fixture.sources, fixture.classes, fixture.boot, fixture.admitted);
        assertTrue(new File(fixture.classes, "MainActivity.class").isFile());
        assertFalse(new File(fixture.classes, "java/lang/Object.class").exists());
    }

    @Test
    public void carriageReturnLineSeparatorsUseActualCompilerLineNumbers() throws Exception {
        EcjDiagnostics.Report report =
                fixture("final class MainActivity {\r" + "  PRIVATE_MISSING_TYPE value;\r}\r")
                        .failure()
                        .diagnostics;
        assertNotNull(report);
        assertTrue(report.problems.stream().anyMatch(problem -> problem.line == 2));
    }

    @Test
    public void noAdmittedSourcePreservesCompilationAndReportsNothing() throws Exception {
        Compilation fixture = fixture("class MainActivity { PRIVATE_MISSING_TYPE value; }\n");
        fixture.admitted.clear();
        BuildService.JavaCompileFailure failure = fixture.failure();
        assertNull(failure.diagnostics);
        assertEquals("COMPILE_ERROR", BuildService.failureResult(failure));
    }

    private Compilation fixture(String java) throws Exception {
        File scratch = temporary.newFolder();
        File main = new File(scratch, "MainActivity.java");
        Files.write(main.toPath(), java.getBytes(StandardCharsets.UTF_8));
        File classes = new File(scratch, "classes");
        assertTrue(classes.mkdir());
        File boot = PlatformCompileTest.repositoryFile("toolchain/input/android/android-36.jar");
        List<File> sources = new ArrayList<>(List.of(main));
        return new Compilation(main, classes, boot, sources, admitted(MAIN, main));
    }

    private static final class Compilation {
        final File main;
        final File classes;
        final File boot;
        final List<File> sources;
        final SortedMap<String, File> admitted;

        Compilation(
                File main,
                File classes,
                File boot,
                List<File> sources,
                SortedMap<String, File> admitted) {
            this.main = main;
            this.classes = classes;
            this.boot = boot;
            this.sources = sources;
            this.admitted = admitted;
        }

        BuildService.JavaCompileFailure failure() {
            return assertThrows(
                    BuildService.JavaCompileFailure.class,
                    () -> BuildService.compileJavaOrThrow(sources, classes, boot, admitted));
        }
    }
}
