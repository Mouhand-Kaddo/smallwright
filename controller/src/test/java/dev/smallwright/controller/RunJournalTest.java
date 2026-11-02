package dev.smallwright.controller;

import static org.junit.Assert.*;

import org.junit.Test;

public final class RunJournalTest {
    @Test
    public void aRepairRecordedBeforeStepRunsIsClosedWhenItsProjectOpens() {
        RunJournal.Snapshot repair =
                RunJournal.Snapshot.start("durable_repair", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null)
                        .advance(RunJournal.State.APPLYING, 0, null, null)
                        .advance(RunJournal.State.BUILDING, 1, "durable_build", 17, 1)
                        .advance(RunJournal.State.REPAIR_NEEDED, 1, null, null);
        assertEquals(RunJournal.State.FAILED, repair.recover(1).state);
        assertEquals(1, repair.recover(1).sourceRevision);
        assertEquals(RunJournal.State.FAILED, repair.recover(2).state);
        assertEquals(1, repair.recover(2).sourceRevision);
        RunJournal.Snapshot cancelled = repair.advance(RunJournal.State.CANCELLED, 1, null, null);
        assertSame(cancelled, cancelled.recover(1));
    }

    @Test
    public void rejectsInvalidProjectIdentityBeforeOpeningStorage() {
        assertThrows(IllegalArgumentException.class, () -> new RunJournal(null, null));
        assertThrows(IllegalArgumentException.class, () -> new RunJournal(null, ""));
        assertThrows(IllegalArgumentException.class, () -> new RunJournal(null, "../other"));
        assertThrows(IllegalArgumentException.class, () -> new RunJournal(null, "a/b"));
        assertThrows(IllegalArgumentException.class, () -> new RunJournal(null, "x".repeat(65)));
    }

    @Test
    public void recordsOnlyBoundedAttemptMetadata() {
        RunJournal.Snapshot draft = RunJournal.Snapshot.start("run_1", 7);
        assertNull(draft.reportedTokens);
        RunJournal.Snapshot planning = draft.advance(RunJournal.State.PLANNING, 7, null, null);
        assertNull(planning.reportedTokens);
        RunJournal.Snapshot editing = planning.advance(RunJournal.State.EDITING, 7, null, 18);
        RunJournal.Snapshot applying = editing.advance(RunJournal.State.APPLYING, 7, null, 18);
        RunJournal.Snapshot building =
                applying.advance(RunJournal.State.BUILDING, 8, "build_1", 18);
        RunJournal.Snapshot ready = building.advance(RunJournal.State.READY_TO_TEST, 8, null, null);
        assertEquals("run_1", ready.runId);
        assertEquals(8, ready.sourceRevision);
        assertEquals("build_1", ready.buildId);
        assertEquals(Integer.valueOf(18), ready.reportedTokens);
        assertEquals(5, ready.sequence);
    }

    @Test
    public void rejectsUnsupportedClaimsAndInconsistentMetadata() {
        RunJournal.Snapshot draft = RunJournal.Snapshot.start("run_1", 0);
        assertThrows(
                IllegalStateException.class,
                () -> draft.advance(RunJournal.State.READY_TO_TEST, 0, null, 0));
        RunJournal.Snapshot editing =
                draft.advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, 10);
        RunJournal.Snapshot applying = editing.advance(RunJournal.State.APPLYING, 0, null, 10);
        assertThrows(
                IllegalArgumentException.class,
                () -> applying.advance(RunJournal.State.BUILDING, 0, "b", 10));
        assertEquals(2, applying.advance(RunJournal.State.BUILDING, 2, "b", 10).sourceRevision);
        assertThrows(
                IllegalArgumentException.class,
                () -> applying.advance(RunJournal.State.BUILDING, 1, null, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> applying.advance(RunJournal.State.BUILDING, 1, "b", 9));
        assertEquals(1, applying.advance(RunJournal.State.FAILED, 1, null, null).sourceRevision);
        assertEquals(0, applying.advance(RunJournal.State.CANCELLED, 0, null, null).sourceRevision);
        RunJournal.Snapshot cancelled = editing.advance(RunJournal.State.CANCELLED, 0, null, null);
        assertThrows(
                IllegalStateException.class,
                () -> cancelled.advance(RunJournal.State.BUILDING, 1, "b", null));
        assertThrows(
                IllegalArgumentException.class, () -> RunJournal.Snapshot.start("../secret", 0));
    }

    @Test
    public void answeredEndsTheRunAndIsNotReachedBeforeEditing() {
        RunJournal.Snapshot editing =
                RunJournal.Snapshot.start("run_a", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null);
        RunJournal.Snapshot answered = editing.advance(RunJournal.State.ANSWERED, 0, null, 4);
        assertFalse(answered.isActive());
        assertThrows(
                IllegalStateException.class,
                () -> answered.advance(RunJournal.State.EDITING, 0, null, null));
        assertThrows(
                IllegalStateException.class,
                () -> answered.advance(RunJournal.State.FAILED, 0, null, null));
        assertThrows(
                IllegalStateException.class,
                () ->
                        RunJournal.Snapshot.start("run_b", 0)
                                .advance(RunJournal.State.PLANNING, 0, null, null)
                                .advance(RunJournal.State.ANSWERED, 0, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> editing.advance(RunJournal.State.ANSWERED, 1, null, null));
    }

    @Test
    public void aRunBuildsAndEditsAgainWithoutALimitAndNeverReusesABuildIdentity() {
        RunJournal.Snapshot state =
                RunJournal.Snapshot.start("run_2", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null);
        for (int build = 1; build <= 10; build++) {
            state =
                    state.advance(RunJournal.State.APPLYING, build - 1, null, null)
                            .advance(RunJournal.State.BUILDING, build, "b" + build, null)
                            .advance(RunJournal.State.EDITING, build, null, null);
        }
        RunJournal.Snapshot edited = state;
        assertEquals(10, edited.sourceRevision);
        assertEquals("b10", edited.buildId);
        assertThrows(
                IllegalArgumentException.class,
                () -> edited.advance(RunJournal.State.BUILDING, 10, "b1", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> edited.advance(RunJournal.State.BUILDING, 11, "b11", null));
        RunJournal.Snapshot rebuilt =
                edited.advance(RunJournal.State.BUILDING, 10, "b11", null)
                        .advance(RunJournal.State.EDITING, 10, null, null);
        RunJournal.Snapshot ready = rebuilt.advance(RunJournal.State.READY_TO_TEST, 10, null, null);
        assertEquals("b11", ready.buildId);
        assertFalse(ready.isActive());
        RunJournal.Snapshot refused =
                rebuilt.advance(RunJournal.State.APPLYING, 10, null, null)
                        .advance(RunJournal.State.EDITING, 10, null, null);
        assertEquals(10, refused.sourceRevision);
        RunJournal.Snapshot saved =
                refused.advance(RunJournal.State.APPLYING, 10, null, null)
                        .advance(RunJournal.State.ANSWERED, 11, null, null);
        assertEquals(11, saved.sourceRevision);
        assertThrows(
                IllegalStateException.class,
                () ->
                        RunJournal.Snapshot.start("run_unbuilt", 0)
                                .advance(RunJournal.State.PLANNING, 0, null, null)
                                .advance(RunJournal.State.EDITING, 0, null, null)
                                .advance(RunJournal.State.READY_TO_TEST, 0, null, null));
    }

    @Test
    public void earlierRepairChainsStillReplay() {
        RunJournal.Snapshot repaired =
                RunJournal.Snapshot.start("run_legacy", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null)
                        .advance(RunJournal.State.APPLYING, 0, null, null)
                        .advance(RunJournal.State.BUILDING, 1, "b1", null)
                        .advance(RunJournal.State.REPAIR_NEEDED, 1, null, null)
                        .advance(RunJournal.State.EDITING, 1, null, null);
        assertEquals(1, repaired.repairs);
        assertEquals(RunJournal.State.FAILED, repaired.recover(1).state);
    }

    @Test
    public void rejectsReuseOfAnyEarlierBuildIdentity() {
        RunJournal.Snapshot repairing =
                RunJournal.Snapshot.start("run_3", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null)
                        .advance(RunJournal.State.APPLYING, 0, null, null)
                        .advance(RunJournal.State.BUILDING, 1, "b1", null)
                        .advance(RunJournal.State.REPAIR_NEEDED, 1, null, null)
                        .advance(RunJournal.State.EDITING, 1, null, null)
                        .advance(RunJournal.State.APPLYING, 1, null, null)
                        .advance(RunJournal.State.BUILDING, 2, "b2", null)
                        .advance(RunJournal.State.REPAIR_NEEDED, 2, null, null)
                        .advance(RunJournal.State.EDITING, 2, null, null);
        RunJournal.Snapshot applying = repairing.advance(RunJournal.State.APPLYING, 2, null, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> applying.advance(RunJournal.State.BUILDING, 3, "b1", null));
    }

    @Test
    public void interruptedAttemptCanOnlyFailAtActualSourceRevision() {
        RunJournal.Snapshot applying =
                RunJournal.Snapshot.start("interrupted", 4)
                        .advance(RunJournal.State.PLANNING, 4, null, null)
                        .advance(RunJournal.State.EDITING, 4, null, 7)
                        .advance(RunJournal.State.APPLYING, 4, null, null);
        assertEquals(4, applying.recover(4).sourceRevision);
        assertEquals(5, applying.recover(5).sourceRevision);
        assertEquals(RunJournal.State.FAILED, applying.recover(5).state);
        assertEquals(Integer.valueOf(7), applying.recover(5).reportedTokens);
        assertEquals(6, applying.recover(6).sourceRevision);
        assertEquals(RunJournal.State.FAILED, applying.recover(6).state);
        assertThrows(IllegalStateException.class, () -> applying.recover(3));
        assertEquals(RunJournal.State.FAILED, applying.recover(4).recover(4).state);
        RunJournal.Snapshot building =
                applying.advance(RunJournal.State.BUILDING, 5, "build_1", null);
        assertEquals("build_1", building.recover(5).buildId);
        assertThrows(IllegalStateException.class, () -> building.recover(6));
        RunJournal.Snapshot awaitingRepair =
                building.advance(RunJournal.State.REPAIR_NEEDED, 5, null, null);
        assertEquals(RunJournal.State.FAILED, awaitingRepair.recover(5).state);
        RunJournal.Snapshot supersededRepair = awaitingRepair.recover(30);
        assertEquals(RunJournal.State.FAILED, supersededRepair.state);
        assertEquals(5, supersededRepair.sourceRevision);
        assertEquals(awaitingRepair.sequence + 1, supersededRepair.sequence);
        assertThrows(IllegalStateException.class, () -> awaitingRepair.recover(4));
    }

    @Test
    public void committedBuildKeepsPinnedVersionForProcessDeathRecovery() {
        RunJournal.Snapshot building =
                RunJournal.Snapshot.start("restart", 2)
                        .advance(RunJournal.State.PLANNING, 2, null, null)
                        .advance(RunJournal.State.EDITING, 2, null, 9)
                        .advance(RunJournal.State.APPLYING, 2, null, null)
                        .advance(RunJournal.State.BUILDING, 3, "build_restart", null, 17);
        assertEquals(Integer.valueOf(17), building.versionCode);
        assertSame(building, building.recover(3));
        assertThrows(IllegalStateException.class, () -> building.recover(4));
        assertEquals(
                RunJournal.State.FAILED,
                building.advance(RunJournal.State.FAILED, 3, null, null).state);
        RunJournal.Snapshot legacy =
                RunJournal.Snapshot.start("legacy", 2)
                        .advance(RunJournal.State.PLANNING, 2, null, null)
                        .advance(RunJournal.State.EDITING, 2, null, null)
                        .advance(RunJournal.State.APPLYING, 2, null, null)
                        .advance(RunJournal.State.BUILDING, 3, "old_build", null);
        assertEquals(RunJournal.State.FAILED, legacy.recover(3).state);
    }

    @Test
    public void buildStopUsesSequenceAndPreventsRestartReplay() {
        RunJournal.Snapshot building =
                RunJournal.Snapshot.start("stop_build", 2)
                        .advance(RunJournal.State.PLANNING, 2, null, null)
                        .advance(RunJournal.State.EDITING, 2, null, 9)
                        .advance(RunJournal.State.APPLYING, 2, null, null)
                        .advance(RunJournal.State.BUILDING, 3, "build_stop", null, 17);
        RunJournal.BuildCancellation stopped =
                RunJournal.decideBuildCancellation(building, building.sequence);
        assertEquals(RunJournal.StopBuildOutcome.CANCELLED, stopped.outcome);
        assertEquals(RunJournal.State.CANCELLED, stopped.snapshot.state);
        assertEquals(building.sequence + 1, stopped.snapshot.sequence);
        assertEquals(3, stopped.snapshot.sourceRevision);
        assertEquals("build_stop", stopped.snapshot.buildId);
        assertEquals(Integer.valueOf(17), stopped.snapshot.versionCode);
        assertSame(stopped.snapshot, stopped.snapshot.recover(3));
        assertThrows(
                IllegalStateException.class,
                () -> stopped.snapshot.advance(RunJournal.State.READY_TO_TEST, 3, null, null));
    }

    @Test
    public void buildStopReportsWinnerAndNeverCancelsApplying() {
        RunJournal.Snapshot applying =
                RunJournal.Snapshot.start("stop_race", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null)
                        .advance(RunJournal.State.APPLYING, 0, null, null);
        RunJournal.BuildCancellation deferred =
                RunJournal.decideBuildCancellation(applying, applying.sequence);
        assertEquals(RunJournal.StopBuildOutcome.NOT_BUILDING, deferred.outcome);
        assertSame(applying, deferred.snapshot);
        RunJournal.Snapshot building =
                applying.advance(RunJournal.State.BUILDING, 1, "build_race", null, 2);
        RunJournal.BuildCancellation stale =
                RunJournal.decideBuildCancellation(building, applying.sequence);
        assertEquals(RunJournal.StopBuildOutcome.STALE, stale.outcome);
        assertSame(building, stale.snapshot);
        RunJournal.Snapshot ready = building.advance(RunJournal.State.READY_TO_TEST, 1, null, null);
        assertEquals(
                RunJournal.StopBuildOutcome.ALREADY_READY,
                RunJournal.decideBuildCancellation(ready, building.sequence).outcome);
        RunJournal.Snapshot cancelled =
                RunJournal.decideBuildCancellation(building, building.sequence).snapshot;
        assertEquals(
                RunJournal.StopBuildOutcome.ALREADY_TERMINAL,
                RunJournal.decideBuildCancellation(cancelled, building.sequence).outcome);
    }
}
