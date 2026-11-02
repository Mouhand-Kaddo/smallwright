package dev.smallwright.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shared journal guards for the frozen Reset and Step trials' three/four/five prior runs. */
final class PhoneAlphaRunHistory {
    private PhoneAlphaRunHistory() {}

    static Map<Integer, String> priorRuns(Context target, ProjectIdentity project, int count) {
        requireCount(count);
        List<String> ids = new ArrayList<>();
        try (SQLiteDatabase db = journal(target);
                Cursor rows =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=? LIMIT "
                                        + (count + 1),
                                new String[] {project.id()})) {
            while (rows.moveToNext()) {
                String id = rows.getString(0);
                requireId(id);
                ids.add(id);
            }
        }
        if (ids.size() != count) fail();
        // Finish the read-only query before production validation opens its database.
        Map<Integer, String> result = new TreeMap<>();
        for (String id : ids) {
            RunJournal.Snapshot run = new RunJournal(target, project.id()).load(id);
            if (!id.equals(run.runId)
                    || run.state != RunJournal.State.READY_TO_TEST
                    || run.sourceRevision < 1
                    || run.sourceRevision > count
                    || run.versionCode == null
                    || run.versionCode != run.sourceRevision
                    || result.put(run.sourceRevision, id) != null) fail();
        }
        return result;
    }

    static void verifyPriorRuns(
            Context target, ProjectIdentity project, SharedPreferences pins, int count) {
        for (Map.Entry<Integer, String> run : priorRuns(target, project, count).entrySet()) {
            if (!run.getValue().equals(pins.getString("priorRun" + run.getKey(), ""))) fail();
        }
    }

    static String newRun(
            Context target, ProjectIdentity project, SharedPreferences pins, int count) {
        requireCount(count);
        String[] arguments = new String[count + 1];
        arguments[0] = project.id();
        for (int index = 1; index <= count; index++) {
            arguments[index] = pins.getString("priorRun" + index, "");
            requireId(arguments[index]);
            for (int previous = 1; previous < index; previous++) {
                if (arguments[index].equals(arguments[previous])) fail();
            }
        }
        String placeholders = String.join(",", Collections.nCopies(count, "?"));
        try (SQLiteDatabase db = journal(target);
                Cursor rows =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=? "
                                        + "AND run_id NOT IN ("
                                        + placeholders
                                        + ") LIMIT 2",
                                arguments)) {
            if (!rows.moveToFirst()) return null;
            String id = rows.getString(0);
            requireId(id);
            if (rows.moveToNext()) fail();
            return id;
        }
    }

    private static SQLiteDatabase journal(Context target) {
        return SQLiteDatabase.openDatabase(
                new File(target.getNoBackupFilesDir(), "run-journal.sqlite").getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY);
    }

    private static void requireCount(int count) {
        if (count < 3 || count > 5) fail();
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("run_[0-9a-f]{32}")) fail();
    }

    private static void fail() {
        throw new PhoneAlphaCounterProbe.GuardFailure();
    }
}
