package dev.smallwright.controller;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Controller-private, append-only metadata for explicitly started runs, and what each working run
 * has shown so far, so process death can end it honestly when its project opens again.
 */
final class RunJournal {
    /**
     * A run's states. {@code REPAIR_NEEDED} appears only in runs recorded before runs worked in
     * steps; opening the project closes such a run.
     */
    enum State {
        DRAFT,
        PLANNING,
        EDITING,
        APPLYING,
        BUILDING,
        REPAIR_NEEDED,
        READY_TO_TEST,
        ANSWERED,
        CANCELLED,
        FAILED
    }

    enum StopBuildOutcome {
        CANCELLED,
        ALREADY_READY,
        ALREADY_TERMINAL,
        NOT_BUILDING,
        STALE
    }

    static final class BuildCancellation {
        final StopBuildOutcome outcome;
        final Snapshot snapshot;

        private BuildCancellation(StopBuildOutcome outcome, Snapshot snapshot) {
            this.outcome = outcome;
            this.snapshot = snapshot;
        }
    }

    /** A working run's chat, the person's message and the builder steps shown so far. */
    static final class Progress {
        final String runId;
        final String chatId;
        final String request;

        /** How many messages the chat held when the run started. */
        final int chatSize;

        /** The steps so far, as the chat keeps them. */
        final String steps;

        Progress(String runId, String chatId, String request, int chatSize, String steps) {
            validId(runId);
            if (chatId == null || request == null || steps == null || chatSize < 0)
                throw new IllegalArgumentException("Invalid run progress");
            this.runId = runId;
            this.chatId = chatId;
            this.request = request;
            this.chatSize = chatSize;
            this.steps = steps;
        }
    }

    private static final String TABLE = "attempt_events";
    private static final String PROGRESS = "run_progress";
    private static final String LEGACY_PROJECT_ID = "counter-alpha";
    private final File database;
    private final String projectId;

    RunJournal(Context context) {
        this(context, LEGACY_PROJECT_ID);
    }

    RunJournal(Context context, String projectId) {
        validId(projectId);
        database = new File(context.getNoBackupFilesDir(), "run-journal.sqlite");
        this.projectId = projectId;
    }

    boolean hasActiveAttempt() {
        try (SQLiteDatabase db = open()) {
            for (Snapshot snapshot : projectLatest(db, projectId)) {
                if (snapshot.isActive()) return true;
            }
            return false;
        }
    }

    static final class Snapshot {
        final String runId;
        final State state;
        final int sourceRevision;
        final String buildId;
        final Integer versionCode;
        final Integer reportedTokens;
        final int sequence;
        final int repairs;
        private final Set<String> usedBuildIds;

        private Snapshot(
                String runId,
                State state,
                int sourceRevision,
                String buildId,
                Integer versionCode,
                Integer reportedTokens,
                int sequence,
                int repairs,
                Set<String> usedBuildIds) {
            this.runId = runId;
            this.state = state;
            this.sourceRevision = sourceRevision;
            this.buildId = buildId;
            this.versionCode = versionCode;
            this.reportedTokens = reportedTokens;
            this.sequence = sequence;
            this.repairs = repairs;
            this.usedBuildIds = Set.copyOf(usedBuildIds);
        }

        static Snapshot start(String runId, int revision) {
            validId(runId);
            if (revision < 0) throw new IllegalArgumentException("Invalid source revision");
            return new Snapshot(runId, State.DRAFT, revision, null, null, null, 0, 0, Set.of());
        }

        Snapshot advance(State next, int revision, String nextBuildId, Integer tokens) {
            return advance(next, revision, nextBuildId, tokens, null);
        }

        Snapshot advance(
                State next,
                int revision,
                String nextBuildId,
                Integer tokens,
                Integer nextVersionCode) {
            if (next == null) throw new IllegalArgumentException("Missing state");
            boolean allowed =
                    switch (state) {
                        case DRAFT -> next == State.PLANNING;
                        case PLANNING -> next == State.EDITING;
                        case EDITING ->
                                next == State.APPLYING
                                        || next == State.ANSWERED
                                        || next == State.BUILDING
                                        || next == State.READY_TO_TEST;
                        case APPLYING ->
                                next == State.BUILDING
                                        || next == State.ANSWERED
                                        || next == State.EDITING;
                        case BUILDING ->
                                next == State.READY_TO_TEST
                                        || next == State.EDITING
                                        || next == State.REPAIR_NEEDED;
                        case REPAIR_NEEDED -> next == State.EDITING;
                        default -> false;
                    };
            if (!allowed
                    && !((next == State.CANCELLED || next == State.FAILED)
                            && (state == State.DRAFT
                                    || state == State.PLANNING
                                    || state == State.EDITING
                                    || state == State.APPLYING
                                    || state == State.BUILDING
                                    || state == State.REPAIR_NEEDED)))
                throw new IllegalStateException("Invalid run transition");
            boolean committed =
                    state == State.APPLYING && (next == State.BUILDING || next == State.ANSWERED);
            boolean terminalAfterApply =
                    state == State.APPLYING && (next == State.FAILED || next == State.CANCELLED);
            boolean revisionHolds =
                    committed
                            ? revision > sourceRevision
                            : terminalAfterApply
                                    ? revision >= sourceRevision
                                    : revision == sourceRevision;
            if (!revisionHolds) throw new IllegalArgumentException("Invalid source revision");
            Integer total = tokens == null ? reportedTokens : tokens;
            if (total != null && (total < 0 || reportedTokens != null && total < reportedTokens))
                throw new IllegalArgumentException("Invalid reported usage");
            String build = buildId;
            Integer version = versionCode;
            Set<String> buildIds = usedBuildIds;
            if (next == State.BUILDING) {
                validId(nextBuildId);
                if (nextVersionCode != null && nextVersionCode < 1)
                    throw new IllegalArgumentException("Invalid build version");
                if (usedBuildIds.contains(nextBuildId))
                    throw new IllegalArgumentException("Build identity reused");
                build = nextBuildId;
                version = nextVersionCode;
                HashSet<String> updated = new HashSet<>(usedBuildIds);
                updated.add(nextBuildId);
                buildIds = updated;
            } else if (nextBuildId != null || nextVersionCode != null) {
                throw new IllegalArgumentException(
                        "Build identity or version may change only when building");
            }
            if (next == State.READY_TO_TEST && build == null)
                throw new IllegalStateException("No verified build identity");
            return new Snapshot(
                    runId,
                    next,
                    revision,
                    build,
                    version,
                    total,
                    sequence + 1,
                    repairs + (next == State.REPAIR_NEEDED ? 1 : 0),
                    buildIds);
        }

        boolean isActive() {
            return state == State.DRAFT
                    || state == State.PLANNING
                    || state == State.EDITING
                    || state == State.APPLYING
                    || state == State.BUILDING
                    || state == State.REPAIR_NEEDED;
        }

        /**
         * Ends an interrupted run at the project's revision. A committed build keeps BUILDING, so
         * it is built again; nothing is ever sent to the model again.
         */
        Snapshot recover(int actualRevision) {
            if (!isActive()) return this;
            if (state == State.REPAIR_NEEDED && actualRevision > sourceRevision)
                return advance(State.FAILED, sourceRevision, null, null);
            if (actualRevision != sourceRevision
                    && !(state == State.APPLYING && actualRevision > sourceRevision))
                throw new IllegalStateException("Source revision does not match interrupted run");
            if (state == State.BUILDING && versionCode != null) return this;
            return advance(State.FAILED, actualRevision, null, null);
        }
    }

    void create(String runId, int sourceRevision) {
        create(runId, sourceRevision, null);
    }

    /** Starts a run and keeps what it shows until {@link #saved} says its chat has it. */
    void create(int sourceRevision, Progress started) {
        create(started.runId, sourceRevision, started);
    }

    private void create(String runId, int sourceRevision, Progress started) {
        Snapshot initial = Snapshot.start(runId, sourceRevision);
        try (SQLiteDatabase db = open()) {
            db.beginTransaction();
            try {
                if (latest(db, projectId, runId) != null)
                    throw new IllegalStateException("Run already exists");
                for (Snapshot snapshot : allLatest(db)) {
                    if (snapshot.isActive())
                        throw new IllegalStateException("Recover interrupted run first");
                }
                insert(db, projectId, initial);
                if (started != null) {
                    ContentValues row = new ContentValues();
                    row.put("project_id", projectId);
                    row.put("run_id", started.runId);
                    row.put("chat_id", started.chatId);
                    row.put("request", started.request);
                    row.put("chat_size", started.chatSize);
                    row.put("steps", started.steps);
                    db.insertOrThrow(PROGRESS, null, row);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    /** Closes abandoned work after validating every event chain; no work is replayed. */
    int recover(int actualRevision) {
        if (actualRevision < 0) throw new IllegalArgumentException("Invalid source revision");
        try (SQLiteDatabase db = open()) {
            db.beginTransaction();
            try {
                List<Snapshot> updates = new ArrayList<>();
                for (Snapshot snapshot : projectLatest(db, projectId)) {
                    if (snapshot.isActive()) {
                        Snapshot recovered = snapshot.recover(actualRevision);
                        if (recovered != snapshot) updates.add(recovered);
                    }
                }
                for (Snapshot update : updates) insert(db, projectId, update);
                db.setTransactionSuccessful();
                return updates.size();
            } finally {
                db.endTransaction();
            }
        }
    }

    Snapshot load(String runId) {
        validId(runId);
        try (SQLiteDatabase db = open()) {
            Snapshot snapshot = latest(db, projectId, runId);
            if (snapshot == null) throw new IllegalStateException("Unknown run");
            return snapshot; // Reading never replays a provider request or installation.
        }
    }

    /** Atomically closes a known active build so a process restart cannot replay it. */
    StopBuildOutcome cancelActiveBuild(String runId, int expectedSequence) {
        validId(runId);
        try (SQLiteDatabase db = open()) {
            db.beginTransaction();
            try {
                Snapshot current = latest(db, projectId, runId);
                if (current == null) throw new IllegalStateException("Unknown run");
                BuildCancellation decision = decideBuildCancellation(current, expectedSequence);
                if (decision.outcome == StopBuildOutcome.CANCELLED)
                    insert(db, projectId, decision.snapshot);
                db.setTransactionSuccessful();
                return decision.outcome;
            } finally {
                db.endTransaction();
            }
        }
    }

    /** Pure half of the journal CAS, shared by the database path and host tests. */
    static BuildCancellation decideBuildCancellation(Snapshot current, int expectedSequence) {
        if (current == null) throw new IllegalArgumentException("Missing run");
        if (current.state == State.READY_TO_TEST)
            return new BuildCancellation(StopBuildOutcome.ALREADY_READY, current);
        if (current.state == State.CANCELLED || current.state == State.FAILED)
            return new BuildCancellation(StopBuildOutcome.ALREADY_TERMINAL, current);
        if (current.sequence != expectedSequence)
            return new BuildCancellation(StopBuildOutcome.STALE, current);
        if (current.state != State.BUILDING)
            return new BuildCancellation(StopBuildOutcome.NOT_BUILDING, current);
        return new BuildCancellation(
                StopBuildOutcome.CANCELLED,
                current.advance(State.CANCELLED, current.sourceRevision, null, null));
    }

    Snapshot pendingBuild(int actualRevision) {
        try (SQLiteDatabase db = open()) {
            Snapshot pending = null;
            for (Snapshot snapshot : projectLatest(db, projectId)) {
                if (snapshot.state != State.BUILDING || snapshot.versionCode == null) continue;
                if (snapshot.sourceRevision != actualRevision || pending != null)
                    throw new IllegalStateException("Ambiguous interrupted build");
                pending = snapshot;
            }
            return pending;
        }
    }

    int maximumVersionCode() {
        try (SQLiteDatabase db = open()) {
            int maximum = 0;
            for (Snapshot snapshot : projectLatest(db, projectId)) {
                if (snapshot.versionCode != null) maximum = Math.max(maximum, snapshot.versionCode);
            }
            return maximum;
        }
    }

    /** Recovery import may begin only before any local generation attempt was recorded. */
    boolean hasAnyAttempt() {
        try (SQLiteDatabase db = open()) {
            return !projectLatest(db, projectId).isEmpty();
        }
    }

    Snapshot advance(
            String runId,
            int expectedSequence,
            State next,
            int revision,
            String buildId,
            Integer reportedTokens) {
        return advance(runId, expectedSequence, next, revision, buildId, reportedTokens, null);
    }

    Snapshot advance(
            String runId,
            int expectedSequence,
            State next,
            int revision,
            String buildId,
            Integer reportedTokens,
            Integer versionCode) {
        validId(runId);
        try (SQLiteDatabase db = open()) {
            db.beginTransaction();
            try {
                Snapshot previous = latest(db, projectId, runId);
                if (previous == null || previous.sequence != expectedSequence)
                    throw new IllegalStateException("Stale or unknown run");
                Snapshot updated =
                        previous.advance(next, revision, buildId, reportedTokens, versionCode);
                insert(db, projectId, updated);
                db.setTransactionSuccessful();
                return updated;
            } finally {
                db.endTransaction();
            }
        }
    }

    /** Records the steps a working run has shown so far. */
    void showed(String runId, String steps) {
        validId(runId);
        if (steps == null) throw new IllegalArgumentException("Missing steps");
        ContentValues row = new ContentValues();
        row.put("steps", steps);
        try (SQLiteDatabase db = open()) {
            db.update(PROGRESS, row, "project_id=? AND run_id=?", new String[] {projectId, runId});
        }
    }

    /** The chat holds this run's messages now. */
    void saved(String runId) {
        validId(runId);
        try (SQLiteDatabase db = open()) {
            db.delete(PROGRESS, "project_id=? AND run_id=?", new String[] {projectId, runId});
        }
    }

    /** This project's runs whose chat may not hold their messages yet. */
    List<Progress> unsaved() {
        List<Progress> runs = new ArrayList<>();
        try (SQLiteDatabase db = open();
                Cursor rows =
                        db.query(
                                PROGRESS,
                                new String[] {"run_id", "chat_id", "request", "chat_size", "steps"},
                                "project_id=?",
                                new String[] {projectId},
                                null,
                                null,
                                "run_id")) {
            while (rows.moveToNext()) {
                try {
                    runs.add(
                            new Progress(
                                    rows.getString(0),
                                    rows.getString(1),
                                    rows.getString(2),
                                    rows.getInt(3),
                                    rows.getString(4)));
                } catch (IllegalArgumentException malformed) {
                    throw new IllegalStateException("Run journal corrupted", malformed);
                }
            }
        }
        return runs;
    }

    private SQLiteDatabase open() {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(database, null);
        try {
            try (Cursor check = db.rawQuery("PRAGMA quick_check(1)", null)) {
                if (!check.moveToFirst() || !"ok".equals(check.getString(0)))
                    throw new IllegalStateException("Run journal corrupted");
            }
            db.beginTransaction();
            try {
                boolean hasTable = false;
                boolean hasProject = false;
                boolean hasVersion = false;
                try (Cursor columns = db.rawQuery("PRAGMA table_info(" + TABLE + ")", null)) {
                    while (columns.moveToNext()) {
                        hasTable = true;
                        if ("project_id".equals(columns.getString(1))) hasProject = true;
                        if ("version_code".equals(columns.getString(1))) hasVersion = true;
                    }
                }
                if (!hasTable) {
                    createTable(db, TABLE);
                } else if (!hasProject) {
                    createTable(db, "attempt_events_migrating");
                    db.execSQL(
                            "INSERT INTO attempt_events_migrating"
                                    + " (project_id, run_id, seq, state, source_revision, build_id,"
                                    + " reported_tokens, repairs, version_code) SELECT '"
                                    + LEGACY_PROJECT_ID
                                    + "',"
                                    + " run_id, seq, state, source_revision, build_id, reported_tokens, repairs, "
                                    + (hasVersion ? "version_code" : "NULL")
                                    + " FROM "
                                    + TABLE);
                    db.execSQL("DROP TABLE " + TABLE);
                    db.execSQL("ALTER TABLE attempt_events_migrating RENAME TO " + TABLE);
                } else if (!hasVersion) {
                    db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN version_code INTEGER");
                }
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS "
                                + PROGRESS
                                + " (project_id TEXT NOT NULL, run_id TEXT NOT NULL,"
                                + " chat_id TEXT NOT NULL, request TEXT NOT NULL,"
                                + " chat_size INTEGER NOT NULL, steps TEXT NOT NULL,"
                                + " PRIMARY KEY(project_id, run_id))");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return db;
        } catch (RuntimeException failure) {
            db.close();
            throw failure;
        }
    }

    private static void createTable(SQLiteDatabase db, String name) {
        db.execSQL(
                "CREATE TABLE "
                        + name
                        + " (project_id TEXT NOT NULL, run_id TEXT NOT NULL,"
                        + " seq INTEGER NOT NULL, state TEXT NOT NULL, source_revision INTEGER NOT NULL,"
                        + " build_id TEXT, reported_tokens INTEGER, repairs INTEGER NOT NULL,"
                        + " version_code INTEGER, PRIMARY KEY(project_id, run_id, seq))");
    }

    private static Snapshot latest(SQLiteDatabase db, String projectId, String runId) {
        Snapshot previous = null;
        try (Cursor rows =
                db.query(
                        TABLE,
                        new String[] {
                            "seq",
                            "state",
                            "source_revision",
                            "build_id",
                            "reported_tokens",
                            "repairs",
                            "version_code"
                        },
                        "project_id=? AND run_id=?",
                        new String[] {projectId, runId},
                        null,
                        null,
                        "seq ASC")) {
            while (rows.moveToNext()) {
                int seq = rows.getInt(0);
                State state;
                try {
                    state = State.valueOf(rows.getString(1));
                } catch (RuntimeException malformed) {
                    throw new IllegalStateException("Run journal corrupted");
                }
                int revision = rows.getInt(2);
                String build = rows.isNull(3) ? null : rows.getString(3);
                Integer tokens = rows.isNull(4) ? null : rows.getInt(4);
                int repairs = rows.getInt(5);
                Integer version = rows.isNull(6) ? null : rows.getInt(6);
                if (previous == null) {
                    if (seq != 0
                            || state != State.DRAFT
                            || revision < 0
                            || build != null
                            || tokens != null
                            || repairs != 0
                            || version != null)
                        throw new IllegalStateException("Run journal corrupted");
                    previous = Snapshot.start(runId, revision);
                } else {
                    try {
                        Snapshot candidate =
                                previous.advance(
                                        state,
                                        revision,
                                        state == State.BUILDING ? build : null,
                                        tokens,
                                        state == State.BUILDING ? version : null);
                        if (candidate.sequence != seq
                                || candidate.repairs != repairs
                                || !Objects.equals(candidate.buildId, build)
                                || !Objects.equals(candidate.versionCode, version)
                                || !Objects.equals(candidate.reportedTokens, tokens))
                            throw new IllegalStateException("Run journal corrupted");
                        previous = candidate;
                    } catch (IllegalArgumentException | IllegalStateException bad) {
                        throw new IllegalStateException("Run journal corrupted", bad);
                    }
                }
            }
        }
        return previous;
    }

    private static List<Snapshot> allLatest(SQLiteDatabase db) {
        List<Snapshot> snapshots = new ArrayList<>();
        try (Cursor rows =
                db.rawQuery(
                        "SELECT DISTINCT project_id, run_id FROM "
                                + TABLE
                                + " ORDER BY project_id, run_id",
                        null)) {
            while (rows.moveToNext()) {
                String owner = rows.getString(0);
                String runId = rows.getString(1);
                try {
                    validId(owner);
                    validId(runId);
                } catch (IllegalArgumentException malformed) {
                    throw new IllegalStateException("Run journal corrupted", malformed);
                }
                snapshots.add(latest(db, owner, runId));
            }
        }
        return snapshots;
    }

    private static List<Snapshot> projectLatest(SQLiteDatabase db, String projectId) {
        List<Snapshot> snapshots = new ArrayList<>();
        try (Cursor rows =
                db.query(
                        true,
                        TABLE,
                        new String[] {"run_id"},
                        "project_id=?",
                        new String[] {projectId},
                        null,
                        null,
                        "run_id",
                        null)) {
            while (rows.moveToNext()) {
                String runId = rows.getString(0);
                try {
                    validId(runId);
                } catch (IllegalArgumentException malformed) {
                    throw new IllegalStateException("Run journal corrupted", malformed);
                }
                snapshots.add(latest(db, projectId, runId));
            }
        }
        return snapshots;
    }

    private static void insert(SQLiteDatabase db, String projectId, Snapshot state) {
        ContentValues row = new ContentValues();
        row.put("project_id", projectId);
        row.put("run_id", state.runId);
        row.put("seq", state.sequence);
        row.put("state", state.state.name());
        row.put("source_revision", state.sourceRevision);
        row.put("build_id", state.buildId);
        row.put("reported_tokens", state.reportedTokens);
        row.put("version_code", state.versionCode);
        row.put("repairs", state.repairs);
        if (db.insertOrThrow(TABLE, null, row) < 0)
            throw new IllegalStateException("Run journal write failed");
    }

    private static void validId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid run or build identity");
    }
}
