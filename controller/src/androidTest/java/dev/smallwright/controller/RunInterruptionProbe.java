package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.mainValue;
import static dev.smallwright.controller.PhoneAlphaCounterProbe.onMain;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.controller.ui.Turn;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Kills the controller in the middle of a real run, through the normal screen, then reopens it.
 * {@code start} creates a disposable project, sends one message with the saved model (DeepSeek V4.1
 * Flash or a free model only) and kills its own process once the run has built at least once and is
 * working again. {@code reopen} starts the controller fresh and checks the run ended at the last
 * committed revision, its chat tells what happened, and nothing was sent again. {@code cleanup}
 * deletes the disposable project and selects the person's previous project, or the one named.
 */
final class RunInterruptionProbe {
    private static final String TITLE = "Run interruption test 44";
    private static final String REQUEST =
            "Make this a tip calculator in two stages. Stage 1: in MainActivity add a field for"
                    + " the bill amount and a Calculate button that shows a 15% tip, then build."
                    + " Stage 2: after that build succeeds, move the tip calculation into a new"
                    + " class TipMath.java, use it from MainActivity, and build again. Then tell me"
                    + " in one sentence what you did.";

    private RunInterruptionProbe() {}

    static String run(Instrumentation in, String phase, String selectAfter) throws Exception {
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins =
                target.getSharedPreferences("run-interruption-44", Context.MODE_PRIVATE);
        switch (phase) {
            case "start":
                return start(in, target, pins);
            case "reopen":
                return reopen(in, target, pins);
            case "cleanup":
                return cleanup(in, target, pins, selectAfter);
            default:
                throw new IllegalArgumentException("Unknown phase " + phase);
        }
    }

    private static Activity open(Instrumentation in, Context target) throws Exception {
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerUi.awaitProjectBinding(in, ui);
        ActiveWorkerStopProbe.awaitWorker(in, ui);
        return ui;
    }

    private static ProjectIdentity active(Instrumentation in, Activity ui) throws Exception {
        return mainValue(in, () -> (ProjectIdentity) Ui.field(ui, "activeProject"));
    }

    private static String start(Instrumentation in, Context target, SharedPreferences pins)
            throws Exception {
        if (pins.contains("project")) throw new AssertionError("Earlier probe not cleaned up");
        String previous =
                target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                        .getString("active-project", "");
        Activity ui = open(in, target);
        ModelChoice model = mainValue(in, () -> Ui.state(ui).getModel());
        if (!model.isFree() && !"deepseek/deepseek-v4.1-flash".equals(model.id))
            throw new AssertionError("Only DeepSeek V4.1 Flash or a free model: " + model.id);
        CompilerMeasurementProbe.createProject(in, ui, TITLE);
        ProjectIdentity project = active(in, ui);
        if (!TITLE.equals(project.title())) throw new AssertionError("Not the disposable project");
        ProjectSourceStore source = new ProjectSourceStore(project);
        RunJournal journal = new RunJournal(target, project.id());
        if (source.revision() != 0 || journal.hasAnyAttempt())
            throw new AssertionError("Disposable project is not new");
        commit(
                pins.edit()
                        .putString("previous", previous)
                        .putString("project", project.id())
                        .putString("model", model.id));
        onMain(in, () -> Ui.text(ui, "modelRequest").setText(REQUEST));
        Ui.tap(in, target, Labels.GENERATE);

        String runId = null;
        long deadline = SystemClock.elapsedRealtime() + 600_000;
        long workingAgainSince = 0;
        while (true) {
            if (SystemClock.elapsedRealtime() > deadline)
                throw new AssertionError("The run did not build within ten minutes");
            if (runId == null) runId = onlyRun(target, project.id());
            RunJournal.Snapshot run = runId == null ? null : journal.load(runId);
            if (run != null && !run.isActive())
                throw new AssertionError("The run ended before it could be killed: " + run.state);
            LiveRun live = mainValue(in, () -> (LiveRun) Ui.field(ui, "liveSteps"));
            boolean workingAgain =
                    run != null && run.state == RunJournal.State.EDITING && run.sourceRevision >= 1;
            if (workingAgain && workingAgainSince == 0)
                workingAgainSince = SystemClock.elapsedRealtime();
            if (workingAgain
                    && live != null
                    && (editedSinceLastBuild(live.getSteps())
                            || SystemClock.elapsedRealtime() - workingAgainSince > 90_000)) {
                int revision = source.revision();
                if (revision != run.sourceRevision)
                    throw new AssertionError("Journal and project disagree before the kill");
                screenshot(in, target, "01-before-kill");
                Bundle status = new Bundle();
                status.putString(
                        "stream",
                        "RUN_INTERRUPTION start project="
                                + project.id()
                                + " run="
                                + runId
                                + " revision="
                                + revision
                                + " state="
                                + run.state
                                + " uncommittedEdits="
                                + editedSinceLastBuild(live.getSteps())
                                + " steps="
                                + live.getSteps().size()
                                + " killing pid "
                                + android.os.Process.myPid()
                                + "\n");
                in.sendStatus(0, status);
                SystemClock.sleep(300);
                RunJournal.Snapshot killed = journal.load(runId);
                commit(
                        pins.edit()
                                .putString("run", runId)
                                .putInt("revision", killed.sourceRevision)
                                .putInt("sequence", killed.sequence)
                                .putString("state", killed.state.name())
                                .putBoolean(
                                        "uncommittedEdits", editedSinceLastBuild(live.getSteps()))
                                .putString("steps", journal.unsaved().get(0).steps)
                                .putString("chat", live.getChatId()));
                android.os.Process.killProcess(android.os.Process.myPid());
                throw new AssertionError("Still alive after the kill");
            }
            SystemClock.sleep(100);
        }
    }

    private static boolean editedSinceLastBuild(List<Step> steps) {
        boolean edited = false;
        for (Step step : steps) {
            String text = step.getText();
            if (text.startsWith("Built on this phone")
                    || text.startsWith("The build failed")
                    || text.startsWith("Building on this phone")) edited = false;
            else if (text.startsWith("Edited ")
                    || text.startsWith("Wrote ")
                    || text.startsWith("Created ")
                    || text.startsWith("Deleted ")) edited = true;
        }
        return edited;
    }

    private static String reopen(Instrumentation in, Context target, SharedPreferences pins)
            throws Exception {
        String projectId = pins.getString("project", null);
        String runId = pins.getString("run", null);
        if (projectId == null || runId == null) throw new AssertionError("Nothing was killed");
        RunJournal journal = new RunJournal(target, projectId);
        RunJournal.Snapshot atStart = journal.load(runId);
        if (!atStart.isActive() || atStart.sequence != pins.getInt("sequence", -1))
            throw new AssertionError("The journal moved after the kill: " + atStart.state);
        Activity ui = open(in, target);
        ProjectIdentity project = active(in, ui);
        if (!projectId.equals(project.id()))
            throw new AssertionError("The interrupted run's project did not open");
        SystemClock.sleep(5000);
        RunJournal.Snapshot reopened = journal.load(runId);
        int revision = new ProjectSourceStore(project).revision();
        int killedAt = pins.getInt("revision", -1);
        if (reopened.isActive()) throw new AssertionError("Still active: " + reopened.state);
        if (reopened.sourceRevision != killedAt || revision != killedAt)
            throw new AssertionError(
                    "Reopened at revision "
                            + revision
                            + " / journal "
                            + reopened.sourceRevision
                            + ", killed at "
                            + killedAt);
        if (!java.util.Objects.equals(reopened.reportedTokens, atStart.reportedTokens)
                || !List.of(runId).equals(runs(target, projectId)))
            throw new AssertionError("A model request was made after the kill");
        if (mainValue(in, () -> ((RunCoordinator) Ui.field(ui, "runs")).isBusy()))
            throw new AssertionError("Work started again after reopening");
        if (!journal.unsaved().isEmpty()) throw new AssertionError("Run progress was kept");
        ChatStore chats =
                new ChatStore(new File(target.getFilesDir(), "chats"), target.getFilesDir());
        List<ChatStore.Message> messages =
                chats.listMessages(projectId, pins.getString("chat", ""));
        int size = messages.size();
        if (size < 3
                || !REQUEST.equals(messages.get(size - 3).text)
                || !"tool".equals(messages.get(size - 2).role)
                || !pins.getString("steps", "").equals(messages.get(size - 2).text)
                || !InterruptedRuns.ENDED.equals(messages.get(size - 1).text)
                || messages.get(size - 1).sourceRevision != killedAt)
            throw new AssertionError("The chat does not tell what happened");
        List<Turn> transcript = mainValue(in, () -> Ui.state(ui).getTranscript());
        if (transcript.isEmpty()
                || !InterruptedRuns.ENDED.equals(transcript.get(transcript.size() - 1).getText()))
            throw new AssertionError("The reopened chat does not show what happened");
        String status = mainValue(in, () -> Ui.state(ui).getStatus().getText());
        screenshot(in, target, "02-reopened");
        commit(pins.edit().putBoolean("reopened", true));
        return "RUN_INTERRUPTION reopen run="
                + runId
                + " state="
                + reopened.state
                + " revision="
                + revision
                + " killedAt="
                + killedAt
                + " uncommittedEdits="
                + pins.getBoolean("uncommittedEdits", false)
                + " messages="
                + size
                + " steps="
                + StepLog.decode(messages.get(size - 2).text).size()
                + " status="
                + status.replace('\n', '|')
                + "\n";
    }

    private static String cleanup(
            Instrumentation in, Context target, SharedPreferences pins, String selectAfter)
            throws Exception {
        String projectId = pins.getString("project", null);
        String previous = selectAfter != null ? selectAfter : pins.getString("previous", "");
        if (projectId == null) throw new AssertionError("No disposable project recorded");
        Activity ui = open(in, target);
        if (!projectId.equals(active(in, ui).id())) select(in, ui, projectId);
        ProjectIdentity doomed = active(in, ui);
        if (!projectId.equals(doomed.id()) || !TITLE.equals(doomed.title()))
            throw new AssertionError("Refusing to delete a project the probe did not make");
        List<Turn> shown = mainValue(in, () -> Ui.state(ui).getTranscript());
        String lastShown = shown.isEmpty() ? "" : shown.get(shown.size() - 1).getText();
        screenshot(in, target, "00-before-delete");
        Ui.showTools(in, target, ui);
        Ui.tap(in, target, Labels.DELETE_PROJECT);
        Ui.tap(in, target, "Delete project");
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        while (projectId.equals(active(in, ui).id())
                || mainValue(in, () -> (boolean) Ui.field(ui, "projectBindingInProgress"))) {
            if (SystemClock.elapsedRealtime() > deadline)
                throw new AssertionError("The disposable project was not deleted");
            SystemClock.sleep(100);
        }
        if (!previous.isEmpty() && !previous.equals(active(in, ui).id())) select(in, ui, previous);
        String selected = active(in, ui).id();
        if (!previous.isEmpty() && !previous.equals(selected))
            throw new AssertionError("The previous project was not selected again");
        List<String> left = new ArrayList<>();
        for (ProjectIdentity project :
                new dev.smallwright.project.ProjectRegistry(
                                new File(target.getFilesDir(), "projects"), target.getFilesDir())
                        .list()) {
            if (project.id().equals(projectId)) throw new AssertionError("Project still listed");
            left.add(project.title());
        }
        screenshot(in, target, "03-after-cleanup");
        commit(pins.edit().clear());
        return "RUN_INTERRUPTION cleanup deleted="
                + projectId
                + " lastShown="
                + lastShown.replace('\n', '|')
                + " selected="
                + selected
                + " projects="
                + left
                + "\n";
    }

    private static void select(Instrumentation in, Activity ui, String projectId) throws Exception {
        onMain(
                in,
                () -> {
                    @SuppressWarnings("unchecked")
                    List<ProjectIdentity> listed =
                            (List<ProjectIdentity>) Ui.field(ui, "projectList");
                    for (int index = 0; index < listed.size(); index++)
                        if (listed.get(index).id().equals(projectId))
                            ((Picker) Ui.field(ui, "projectPicker")).select(index);
                });
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        while (!projectId.equals(active(in, ui).id())
                || mainValue(in, () -> (boolean) Ui.field(ui, "projectBindingInProgress"))) {
            if (SystemClock.elapsedRealtime() > deadline)
                throw new AssertionError("Project " + projectId + " was not selected");
            SystemClock.sleep(100);
        }
        in.waitForIdleSync();
    }

    private static String onlyRun(Context target, String projectId) {
        List<String> runs = runs(target, projectId);
        if (runs.size() > 1) throw new AssertionError("More than one run in the project");
        return runs.isEmpty() ? null : runs.get(0);
    }

    private static List<String> runs(Context target, String projectId) {
        List<String> runs = new ArrayList<>();
        File database = new File(target.getNoBackupFilesDir(), "run-journal.sqlite");
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor rows =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=?",
                                new String[] {projectId})) {
            while (rows.moveToNext()) runs.add(rows.getString(0));
        }
        return runs;
    }

    private static void screenshot(Instrumentation in, Context target, String name)
            throws Exception {
        in.waitForIdleSync();
        SystemClock.sleep(700);
        Bitmap shot = in.getUiAutomation().takeScreenshot();
        if (shot == null) throw new AssertionError("No screenshot");
        File directory = new File(target.getFilesDir(), "probe-44");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new AssertionError("No screenshot directory");
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            shot.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private static void commit(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Could not save probe pins");
    }
}
