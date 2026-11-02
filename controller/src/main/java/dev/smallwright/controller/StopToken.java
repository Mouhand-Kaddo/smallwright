package dev.smallwright.controller;

import dev.smallwright.agent.ModelClient;

/** What Stop ends: one model request, build or run, and the journal entry it may cancel. */
final class StopToken {
    enum Result {
        REQUESTED,
        HISTORY_UNCERTAIN,
        ALREADY_FINISHED
    }

    volatile boolean requested;
    final StepRun.Control completion = new StepRun.Control();
    final ModelClient.Cancellation model;
    final RunJournal journal;
    final String runId;

    StopToken(ModelClient.Cancellation model) {
        this(model, null, null);
    }

    StopToken(ModelClient.Cancellation model, RunJournal journal, String runId) {
        this.model = model;
        this.journal = journal;
        this.runId = runId;
    }

    void request() {
        requested = true;
        if (model != null) model.cancel();
    }

    void ensureActive() {
        if (requested) throw new IllegalStateException("Run stopped");
    }

    Result stop() {
        synchronized (completion) {
            if (requested) return Result.REQUESTED;
            if (completion.stop() == StepRun.Control.StopOutcome.ALREADY_FINISHED)
                return Result.ALREADY_FINISHED;
            boolean historyCertain = true;
            if (journal != null) {
                try {
                    for (int attempt = 0; attempt < 3; attempt++) {
                        RunJournal.Snapshot current = journal.load(runId);
                        if (!current.isActive()) return Result.ALREADY_FINISHED;
                        if (current.state == RunJournal.State.BUILDING) {
                            RunJournal.StopBuildOutcome result =
                                    journal.cancelActiveBuild(runId, current.sequence);
                            if (result == RunJournal.StopBuildOutcome.CANCELLED) break;
                            if (result == RunJournal.StopBuildOutcome.ALREADY_READY
                                    || result == RunJournal.StopBuildOutcome.ALREADY_TERMINAL)
                                return Result.ALREADY_FINISHED;
                            if (attempt == 2) historyCertain = false;
                            continue;
                        }
                        break;
                    }
                } catch (Exception failure) {
                    historyCertain = false;
                }
            }
            request();
            return historyCertain ? Result.REQUESTED : Result.HISTORY_UNCERTAIN;
        }
    }
}
