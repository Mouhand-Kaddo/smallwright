package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.Test;

public final class JavaCompileFailureTest {
    @Test
    public void snapshotTimeoutIsActionableFailureWithoutAutomaticJavaRepair() {
        IOException failure =
                assertThrows(
                        IOException.class,
                        () -> LocalBuildCoordinator.requireWorkerSuccess("SNAPSHOT_TIMEOUT"));
        assertEquals("Worker snapshot transfer timed out. Retry the build.", failure.getMessage());
        org.junit.Assert.assertFalse(failure instanceof JavaCompileFailure);
    }

    @Test
    public void mapsOnlyExactWorkerSentinelToTypedFailure() {
        JavaCompileFailure failure =
                assertThrows(
                        JavaCompileFailure.class,
                        () -> LocalBuildCoordinator.requireWorkerSuccess("COMPILE_ERROR"));
        assertEquals("Java source compilation failed", failure.getMessage());
    }

    @Test
    public void leavesEveryOtherWorkerFailureGeneric() {
        IOException failure =
                assertThrows(
                        IOException.class,
                        () ->
                                LocalBuildCoordinator.requireWorkerSuccess(
                                        "ERROR: COMPILE_ERROR: /private/source.java"));
        assertEquals("ERROR: COMPILE_ERROR: /private/source.java", failure.getMessage());
        assertThrows(IOException.class, () -> LocalBuildCoordinator.requireWorkerSuccess("OTHER"));
    }
}
