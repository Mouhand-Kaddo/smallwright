package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;

import dev.smallwright.controller.ui.WorkerState;
import org.junit.Test;

public final class BuildWorkerTest {
    @Test
    public void aWorkerSignedByAnotherKeyCannotBeUsedOrReplaced() {
        assertEquals(WorkerState.OTHER_KEY, BuildWorker.state(false, 1, 1));
        assertEquals(WorkerState.OTHER_KEY, BuildWorker.state(false, 0, 1));
    }

    @Test
    public void aWorkerOlderThanThisAppNeedsAnUpdate() {
        assertEquals(WorkerState.OUTDATED, BuildWorker.state(true, 1, 2));
    }

    @Test
    public void aWorkerAtOrAboveThisAppsVersionIsReady() {
        assertEquals(WorkerState.READY, BuildWorker.state(true, 2, 2));
        assertEquals(WorkerState.READY, BuildWorker.state(true, 3, 2));
    }
}
