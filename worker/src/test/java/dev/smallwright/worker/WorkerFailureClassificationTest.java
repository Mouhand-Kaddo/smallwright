package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkerFailureClassificationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void snapshotDeadlineReturnsFixedCategoryWithoutCompilerRepairSentinel() {
        assertEquals(
                "SNAPSHOT_TIMEOUT", BuildService.failureResult(new SnapshotInputStream.Timeout()));
    }

    @Test
    public void realEcjMalformedJavaMapsToFixedSentinelWithoutDiagnosticText() throws Exception {
        File scratch = temporary.newFolder("compile-failure");
        File source = new File(scratch, "java/dev/smallwright/generated/counter/MainActivity.java");
        assertTrue(source.getParentFile().mkdirs());
        Files.write(
                source.toPath(),
                "package dev.smallwright.generated.counter; class MainActivity { broken }"
                        .getBytes(StandardCharsets.UTF_8));
        File classes = new File(scratch, "classes");
        assertTrue(classes.mkdir());
        File platform = new File("../toolchain/input/android/android-36.jar");
        assertTrue(platform.isFile());
        BuildService.JavaCompileFailure thrown =
                assertThrows(
                        BuildService.JavaCompileFailure.class,
                        () -> BuildService.compileJavaOrThrow(List.of(source), classes, platform));
        assertEquals("COMPILE_ERROR", BuildService.failureResult(thrown));
    }

    @Test
    public void onlyEcjSourceFailureReturnsFixedSentinel() {
        assertEquals(
                "COMPILE_ERROR", BuildService.failureResult(new BuildService.JavaCompileFailure()));
        assertEquals(
                "ERROR: AAPT2 rejected resource",
                BuildService.failureResult(new IllegalStateException("AAPT2 rejected resource")));
        assertEquals(
                "ERROR: worker policy rejected snapshot",
                BuildService.failureResult(
                        new IllegalArgumentException("worker policy rejected snapshot")));
    }

    @Test
    public void validJavaStillCompilesWithoutFailureSentinel() throws Exception {
        File scratch = temporary.newFolder("valid-java");
        File source = new File(scratch, "Valid.java");
        File classes = new File(scratch, "classes");
        assertTrue(classes.mkdir());
        Files.write(
                source.toPath(),
                "final class Valid { int value() { return 1; } }".getBytes(StandardCharsets.UTF_8));
        BuildService.compileJavaOrThrow(
                List.of(source), classes, new File("../toolchain/input/android/android-36.jar"));
        assertTrue(new File(classes, "Valid.class").isFile());
    }
}
