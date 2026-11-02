package dev.smallwright.controller;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import dev.smallwright.project.ChatStore;
import java.io.File;
import java.nio.file.Files;

/** Exercises actual SQLite transactions in controller-private storage. */
final class RunJournalProbe {
    static void run(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "run-journal-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        RunJournal journal = new RunJournal(isolated);
        if (journal.hasAnyAttempt()) throw new AssertionError("Fresh journal reported saved work");
        journal.create("probe_1", 5);
        if (!new RunJournal(isolated).hasAnyAttempt())
            throw new AssertionError("Saved attempt was hidden from recovery preflight");
        RunJournal.Snapshot draft = new RunJournal(isolated).load("probe_1");
        if (draft.state != RunJournal.State.DRAFT
                || draft.sequence != 0
                || draft.reportedTokens != null)
            throw new AssertionError("Draft did not survive reopen");
        RunJournal.Snapshot planning =
                journal.advance("probe_1", 0, RunJournal.State.PLANNING, 5, null, null);
        try {
            journal.advance("probe_1", 0, RunJournal.State.EDITING, 5, null, 12);
            throw new AssertionError("Stale transition accepted");
        } catch (IllegalStateException expected) {
            /* stale sequence */
        }
        RunJournal.Snapshot editing =
                journal.advance(
                        "probe_1", planning.sequence, RunJournal.State.EDITING, 5, null, 12);
        RunJournal.Snapshot applying =
                journal.advance(
                        "probe_1", editing.sequence, RunJournal.State.APPLYING, 5, null, null);
        RunJournal.Snapshot building =
                journal.advance(
                        "probe_1", applying.sequence, RunJournal.State.BUILDING, 6, "build_1", 12);
        RunJournal.Snapshot ready =
                journal.advance(
                        "probe_1",
                        building.sequence,
                        RunJournal.State.READY_TO_TEST,
                        6,
                        null,
                        null);
        RunJournal.Snapshot reopened = new RunJournal(isolated).load("probe_1");
        if (reopened.state != RunJournal.State.READY_TO_TEST
                || reopened.sequence != ready.sequence
                || reopened.sourceRevision != 6
                || !"build_1".equals(reopened.buildId)
                || !Integer.valueOf(12).equals(reopened.reportedTokens))
            throw new AssertionError("Journal did not persist attempt metadata");
        try {
            journal.advance("probe_1", reopened.sequence, RunJournal.State.EDITING, 6, null, null);
            throw new AssertionError("Terminal run resumed automatically");
        } catch (IllegalStateException expected) {
            /* explicit new run required */
        }
        journal.create("probe_2", 9);
        RunJournal.Snapshot interrupted =
                journal.advance("probe_2", 0, RunJournal.State.PLANNING, 9, null, null);
        interrupted =
                journal.advance(
                        "probe_2", interrupted.sequence, RunJournal.State.EDITING, 9, null, 21);
        interrupted =
                journal.advance(
                        "probe_2", interrupted.sequence, RunJournal.State.APPLYING, 9, null, null);
        RunJournal.Snapshot pending = new RunJournal(isolated).load("probe_2");
        RunJournal.Snapshot pendingAgain = new RunJournal(isolated).load("probe_2");
        if (pending.state != RunJournal.State.APPLYING
                || pending.sequence != interrupted.sequence
                || pendingAgain.sequence != pending.sequence
                || pending.buildId != null
                || !Integer.valueOf(21).equals(pending.reportedTokens))
            throw new AssertionError("Reopening interrupted attempt changed its state");
        File path = new File(root, "run-journal.sqlite");
        try (SQLiteDatabase db =
                SQLiteDatabase.openDatabase(
                        path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL(
                    "UPDATE attempt_events SET reported_tokens=11 WHERE run_id='probe_1' AND seq=4");
        }
        try {
            journal.load("probe_1");
            throw new AssertionError("Corrupted event chain accepted");
        } catch (IllegalStateException expected) {
            /* semantic corruption rejected */
        }
        try (SQLiteDatabase db =
                SQLiteDatabase.openDatabase(
                        path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL(
                    "UPDATE attempt_events SET reported_tokens=NULL WHERE run_id='probe_1' AND seq=4");
        }
        try {
            journal.load("probe_1");
            throw new AssertionError("Missing persisted usage accepted");
        } catch (IllegalStateException expected) {
            /* inherited usage must match the persisted row */
        }
        recoveryUsesCommittedSourceAndRejectsCorruption(target);
        stoppedBuildIsNotReplayed(target);
        staleRepairDoesNotBlockStartup(target);
        projectsAreIsolatedAndSerialized(target);
        interruptedRunTellsItsChat(target);
        legacyRowsMigrateWithoutLoss(target);
        legacyVersionCodesMigrateWithoutLoss(target);
    }

    private static void interruptedRunTellsItsChat(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "interrupted-run-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        File chatRoot = new File(root, "chats");
        if (!chatRoot.mkdir()) throw new AssertionError("Probe chat root unavailable");
        ChatStore chats = new ChatStore(chatRoot, root);
        String project = "app" + "b".repeat(32);
        String chat = chats.createChat(project, "App ideas").id;
        RunJournal journal = new RunJournal(isolated, project);
        RunJournal other = new RunJournal(isolated, "app" + "c".repeat(32));
        journal.create(
                4,
                new RunJournal.Progress("killed_run", chat, "Add a list", 0, "failed\tThinking\t"));
        RunJournal.Snapshot run = journal.load("killed_run");
        run = journal.advance(run.runId, run.sequence, RunJournal.State.PLANNING, 4, null, null);
        run = journal.advance(run.runId, run.sequence, RunJournal.State.EDITING, 4, null, null);
        run = journal.advance(run.runId, run.sequence, RunJournal.State.APPLYING, 4, null, null);
        run =
                journal.advance(
                        run.runId,
                        run.sequence,
                        RunJournal.State.BUILDING,
                        5,
                        "killed_build",
                        null,
                        3);
        run = journal.advance(run.runId, run.sequence, RunJournal.State.EDITING, 5, null, null);
        journal.showed("killed_run", "done\tBuilt on this phone in 2.0 s\t\nfailed\tThinking\t");
        RunJournal reopened = new RunJournal(isolated, project);
        if (reopened.unsaved().size() != 1
                || !other.unsaved().isEmpty()
                || !reopened.unsaved().get(0).steps.startsWith("done\tBuilt on this phone"))
            throw new AssertionError("Run progress did not persist for its own project only");
        if (InterruptedRuns.reopen(reopened, chats, project, 5) != 1)
            throw new AssertionError("Interrupted run was not closed");
        java.util.List<ChatStore.Message> messages = chats.listMessages(project, chat);
        if (reopened.load("killed_run").state != RunJournal.State.FAILED
                || reopened.load("killed_run").sourceRevision != 5
                || messages.size() != 3
                || !"Add a list".equals(messages.get(0).text)
                || !"tool".equals(messages.get(1).role)
                || !InterruptedRuns.ENDED.equals(messages.get(2).text)
                || messages.get(2).sourceRevision != 5
                || !reopened.unsaved().isEmpty())
            throw new AssertionError("Interrupted run was not told in its chat");
        if (InterruptedRuns.reopen(reopened, chats, project, 5) != 0
                || chats.listMessages(project, chat).size() != 3)
            throw new AssertionError("Reopening again changed the chat");
        reopened.create(
                5, new RunJournal.Progress("next_run", chat, "Next", 3, "failed\tThinking\t"));
        reopened.saved("next_run");
        if (!reopened.unsaved().isEmpty()) throw new AssertionError("Saved run kept its progress");
    }

    private static void projectsAreIsolatedAndSerialized(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "project-journal-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        RunJournal alpha = new RunJournal(isolated, "project_alpha");
        RunJournal beta = new RunJournal(isolated, "project_beta");
        for (String bad : new String[] {"", "../escape", "a/b"}) {
            try {
                new RunJournal(isolated, bad);
                throw new AssertionError("Invalid project ID accepted");
            } catch (IllegalArgumentException expected) {
                /* bounded identity */
            }
        }
        alpha.create("shared_run", 2);
        if (!alpha.hasAnyAttempt() || beta.hasAnyAttempt())
            throw new AssertionError("Attempts leaked between projects");
        try {
            beta.create("shared_run", 99);
            throw new AssertionError("Another project started over an active run");
        } catch (IllegalStateException expected) {
            /* global one-active-run rule */
        }
        RunJournal.Snapshot a = alpha.load("shared_run");
        a = alpha.advance("shared_run", a.sequence, RunJournal.State.PLANNING, 2, null, null);
        a = alpha.advance("shared_run", a.sequence, RunJournal.State.EDITING, 2, null, null);
        a = alpha.advance("shared_run", a.sequence, RunJournal.State.APPLYING, 2, null, null);
        a =
                alpha.advance(
                        "shared_run",
                        a.sequence,
                        RunJournal.State.BUILDING,
                        3,
                        "alpha_build",
                        null,
                        8);
        if (beta.recover(99) != 0
                || beta.pendingBuild(99) != null
                || beta.maximumVersionCode() != 0)
            throw new AssertionError("Another project saw the active build");
        if (alpha.pendingBuild(3) == null || alpha.maximumVersionCode() != 8)
            throw new AssertionError("Owner lost its active build");
        try {
            beta.load("shared_run");
            throw new AssertionError("Other project loaded run");
        } catch (IllegalStateException expected) {
            /* project-scoped lookup */
        }
        try {
            beta.advance("shared_run", a.sequence, RunJournal.State.READY_TO_TEST, 3, null, null);
            throw new AssertionError("Other project advanced run");
        } catch (IllegalStateException expected) {
            /* project-scoped CAS */
        }
        alpha.advance("shared_run", a.sequence, RunJournal.State.READY_TO_TEST, 3, null, null);
        beta.create("shared_run", 99);
        if (!beta.hasAnyAttempt()
                || beta.load("shared_run").sourceRevision != 99
                || alpha.load("shared_run").sourceRevision != 3)
            throw new AssertionError("Equal run IDs collided across projects");
        if (alpha.recover(3) != 0
                || beta.recover(99) != 1
                || beta.load("shared_run").state != RunJournal.State.FAILED)
            throw new AssertionError("Recovery crossed project boundary");
    }

    private static void legacyRowsMigrateWithoutLoss(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "legacy-journal-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        File path = new File(root, "run-journal.sqlite");
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null)) {
            db.execSQL(
                    "CREATE TABLE attempt_events (run_id TEXT NOT NULL, seq INTEGER NOT NULL,"
                            + " state TEXT NOT NULL, source_revision INTEGER NOT NULL, build_id TEXT,"
                            + " reported_tokens INTEGER, repairs INTEGER NOT NULL, PRIMARY KEY(run_id, seq))");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('legacy_run', 0, 'DRAFT', 4, NULL, NULL, 0)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('legacy_run', 1, 'PLANNING', 4, NULL, NULL, 0)");
        }
        RunJournal legacy = new RunJournal(isolated);
        RunJournal.Snapshot before = legacy.load("legacy_run");
        RunJournal other = new RunJournal(isolated, "new_project");
        if (before.sequence != 1 || before.sourceRevision != 4 || other.hasAnyAttempt())
            throw new AssertionError("Legacy rows did not migrate to counter-alpha");
        try {
            other.create("legacy_run", 0);
            throw new AssertionError("Migrated active run did not serialize projects");
        } catch (IllegalStateException expected) {
            /* old run is active globally */
        }
        if (legacy.recover(4) != 1 || legacy.load("legacy_run").state != RunJournal.State.FAILED)
            throw new AssertionError("Migrated event chain could not recover");
        other.create("legacy_run", 0);
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                android.database.Cursor rows =
                        db.rawQuery(
                                "SELECT project_id, seq, state FROM attempt_events WHERE run_id='legacy_run' ORDER BY project_id, seq",
                                null)) {
            if (rows.getCount() != 4) throw new AssertionError("Migration lost or duplicated rows");
            while (rows.moveToNext()) {
                if (rows.getInt(1) <= 2 && "counter-alpha".equals(rows.getString(0))) continue;
                if (rows.getInt(1) == 0 && "new_project".equals(rows.getString(0))) continue;
                throw new AssertionError("Migrated row had incorrect project key");
            }
        }
    }

    private static void legacyVersionCodesMigrateWithoutLoss(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "versioned-journal-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        File path = new File(root, "run-journal.sqlite");
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null)) {
            db.execSQL(
                    "CREATE TABLE attempt_events (run_id TEXT NOT NULL, seq INTEGER NOT NULL,"
                            + " state TEXT NOT NULL, source_revision INTEGER NOT NULL, build_id TEXT,"
                            + " reported_tokens INTEGER, repairs INTEGER NOT NULL, version_code INTEGER,"
                            + " PRIMARY KEY(run_id, seq))");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 0, 'DRAFT', 4,"
                            + " NULL, NULL, 0, NULL)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 1, 'PLANNING', 4,"
                            + " NULL, NULL, 0, NULL)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 2, 'EDITING', 4,"
                            + " NULL, NULL, 0, NULL)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 3, 'APPLYING', 4,"
                            + " NULL, NULL, 0, NULL)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 4, 'BUILDING', 5,"
                            + " 'versioned_build', NULL, 0, 17)");
            db.execSQL(
                    "INSERT INTO attempt_events VALUES ('versioned_run', 5, 'READY_TO_TEST', 5,"
                            + " 'versioned_build', NULL, 0, 17)");
        }
        RunJournal journal = new RunJournal(isolated);
        RunJournal.Snapshot restored = journal.load("versioned_run");
        if (restored.state != RunJournal.State.READY_TO_TEST
                || restored.versionCode != 17
                || journal.maximumVersionCode() != 17)
            throw new AssertionError("Legacy build version was lost during migration");
    }

    private static void staleRepairDoesNotBlockStartup(Context target) throws Exception {
        File root =
                Files.createTempDirectory(
                                target.getNoBackupFilesDir().toPath(), "stale-repair-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        RunJournal journal = new RunJournal(isolated);
        journal.create("old_repair", 7);
        RunJournal.Snapshot state =
                journal.advance("old_repair", 0, RunJournal.State.PLANNING, 7, null, null);
        state =
                journal.advance(
                        "old_repair", state.sequence, RunJournal.State.EDITING, 7, null, null);
        state =
                journal.advance(
                        "old_repair", state.sequence, RunJournal.State.APPLYING, 7, null, null);
        state =
                journal.advance(
                        "old_repair",
                        state.sequence,
                        RunJournal.State.BUILDING,
                        8,
                        "old_build",
                        null);
        state =
                journal.advance(
                        "old_repair",
                        state.sequence,
                        RunJournal.State.REPAIR_NEEDED,
                        8,
                        null,
                        null);
        RunJournal restarted = new RunJournal(isolated);
        if (restarted.recover(30) != 1) throw new AssertionError("Stale repair blocked startup");
        RunJournal.Snapshot closed = restarted.load("old_repair");
        if (closed.state != RunJournal.State.FAILED
                || closed.sourceRevision != 8
                || closed.sequence != state.sequence + 1
                || restarted.recover(30) != 0)
            throw new AssertionError("Stale repair recovery did not persist safely");
        restarted.create("after_restart", 30);
        if (restarted.load("after_restart").state != RunJournal.State.DRAFT)
            throw new AssertionError("New generation stayed blocked after recovery");
    }

    private static void stoppedBuildIsNotReplayed(Context target) throws Exception {
        File root =
                Files.createTempDirectory(target.getNoBackupFilesDir().toPath(), "stop-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        RunJournal journal = new RunJournal(isolated);
        journal.create("stopped", 2);
        RunJournal.Snapshot state =
                journal.advance("stopped", 0, RunJournal.State.PLANNING, 2, null, null);
        state = journal.advance("stopped", state.sequence, RunJournal.State.EDITING, 2, null, 7);
        state =
                journal.advance(
                        "stopped", state.sequence, RunJournal.State.APPLYING, 2, null, null);
        state =
                journal.advance(
                        "stopped",
                        state.sequence,
                        RunJournal.State.BUILDING,
                        3,
                        "build_stopped",
                        null,
                        4);
        if (journal.cancelActiveBuild("stopped", state.sequence)
                != RunJournal.StopBuildOutcome.CANCELLED)
            throw new AssertionError("Active build was not cancelled");
        RunJournal reopened = new RunJournal(isolated);
        RunJournal.Snapshot ended = reopened.load("stopped");
        if (ended.state != RunJournal.State.CANCELLED
                || ended.sourceRevision != 3
                || ended.versionCode != 4
                || ended.sequence != state.sequence + 1
                || reopened.pendingBuild(3) != null
                || reopened.recover(3) != 0
                || reopened.cancelActiveBuild("stopped", state.sequence)
                        != RunJournal.StopBuildOutcome.ALREADY_TERMINAL)
            throw new AssertionError("Stopped build was replayed or lost its committed revision");
        reopened.create("next", 3);
    }

    private static void recoveryUsesCommittedSourceAndRejectsCorruption(Context target)
            throws Exception {
        File root =
                Files.createTempDirectory(target.getNoBackupFilesDir().toPath(), "recovery-probe-")
                        .toFile();
        Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectSourceStore sources = new ProjectSourceStore(root);
        sources.initialize();
        int before = sources.revision();
        String original = sources.readSource();
        RunJournal journal = new RunJournal(isolated);
        journal.create("abandoned", before);
        RunJournal.Snapshot current =
                journal.advance("abandoned", 0, RunJournal.State.PLANNING, before, null, null);
        current =
                journal.advance(
                        "abandoned",
                        current.sequence,
                        RunJournal.State.EDITING,
                        before,
                        null,
                        null);
        current =
                journal.advance(
                        "abandoned",
                        current.sequence,
                        RunJournal.State.APPLYING,
                        before,
                        null,
                        null);
        try {
            journal.create("another", before);
            throw new AssertionError("New run started over abandoned attempt");
        } catch (IllegalStateException expected) {
            /* must recover first */
        }
        int committed = sources.writeSource(before, original + "\n// recovery probe\n");
        if (journal.recover(sources.revision()) != 1)
            throw new AssertionError("Attempt was not recovered");
        RunJournal.Snapshot ended = new RunJournal(isolated).load("abandoned");
        if (ended.state != RunJournal.State.FAILED
                || ended.sourceRevision != committed
                || ended.sequence != current.sequence + 1
                || journal.recover(sources.revision()) != 0
                || sources.revision() != committed
                || !sources.readSource().equals(original + "\n// recovery probe\n"))
            throw new AssertionError("Recovery altered source or did not preserve actual revision");
        journal.create("another", committed);
        File path = new File(root, "run-journal.sqlite");
        try (SQLiteDatabase db =
                SQLiteDatabase.openDatabase(
                        path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL(
                    "UPDATE attempt_events SET source_revision=-1 WHERE run_id='abandoned' AND seq=0");
        }
        try {
            journal.recover(sources.revision());
            throw new AssertionError("Recovery accepted corrupted event chain");
        } catch (IllegalStateException expected) {
            /* no attempt may be changed */
        }
        if (journal.load("another").state != RunJournal.State.DRAFT)
            throw new AssertionError("Corruption partially changed another attempt");
    }
}
