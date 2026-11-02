package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;

/** Fixed A33 counter feedback and frozen Reset evaluation; no developer source edits. */
final class A33CounterFeedbackProbe {
    private static final String PREFS = "a33-counter01-feedback1-20261012";
    private static final String OBSERVATION =
            "Automated observation of the installed native app for counter_01: Decrement is above Increment in a vertical stack, but the request says beside Increment. Increment from 0 displays 1; Decrement from 1 displays 0. Decrement from 0 still displays 0, but subtracting one should display -1. Closing and reopening preserved the observed 0. Fix the button placement and exact subtraction, including negative counts; preserve Increment and saved-count persistence, package, permissions and existing data.";
    private static final String LAYOUT_OBSERVATION =
            "Automated native observation after the first feedback update: Decrement is still directly above Increment in the same vertical column. The Counter label is to the left of that column and Preview crash report is to the right. The request requires Decrement beside Increment: put those two buttons next to each other on one horizontal row. Preserve the visible Counter label, the existing Increment behavior, exact subtraction including negative counts, saved-count persistence and existing data, package and permissions. The installed update retained count3; subsequent native1 to2 to1 to0 to-1 and closing/reopening at-1 passed. Fix only the remaining placement problem.";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String runReset(
            Instrumentation in, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        return runFixed(in, phase, freeApproved, approvedHash, 3);
    }

    static String runLayout(
            Instrumentation in, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        return runFixed(in, phase, freeApproved, approvedHash, 2);
    }

    static String run(Instrumentation in, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        return runFixed(in, phase, freeApproved, approvedHash, 1);
    }

    private static String runFixed(
            Instrumentation in,
            String phase,
            boolean freeApproved,
            String approvedHash,
            int attempt)
            throws Exception {
        boolean layout = attempt == 2;
        boolean reset = attempt == 3;
        if (attempt < 1 || attempt > 3) throw new AssertionError("Fixed case only");
        int beforeRevision = attempt;
        int afterRevision = beforeRevision + 1;
        String observation = layout ? LAYOUT_OBSERVATION : OBSERVATION;
        String request;
        if (reset) {
            JSONObject contract;
            try (java.io.InputStream input =
                    in.getContext().getAssets().open("alpha/counter_02.json")) {
                contract = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (!"counter_02".equals(contract.getString("id"))
                    || !"supported".equals(contract.getString("kind")))
                throw new AssertionError("Frozen Reset asset");
            request = contract.getString("request");
        } else request = FeedbackRequest.compose(observation);
        String markerPrefix = reset ? "A33 suite reset " : "A33 counter feedback ";
        String action = reset ? Labels.GENERATE : "Send my feedback";
        String positive = reset ? "Send & generate" : "Send & update";
        stage = "GUARD";
        if (!List.of("preview", "send", "ready").contains(phase))
            throw new AssertionError("Unknown phase");
        if (!"cohere/north-mini-code:free".equals(ModelClient.MODEL))
            throw new AssertionError("Wrong model");
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins =
                target.getSharedPreferences(
                        reset
                                ? "a33-counter02-initial-20261012"
                                : layout ? "a33-counter01-feedback2-20261012" : PREFS,
                        Context.MODE_PRIVATE);
        SharedPreferences baseline =
                target.getSharedPreferences(
                        "a33-suite-counter-a33_counter01_20261012_01", Context.MODE_PRIVATE);
        JSONObject initial = new JSONObject(baseline.getString("outcome", ""));
        JSONObject original =
                reset
                        ? new JSONObject(
                                target.getSharedPreferences(
                                                "a33-counter01-feedback2-20261012",
                                                Context.MODE_PRIVATE)
                                        .getString("outcome", ""))
                        : layout
                                ? new JSONObject(
                                        target.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                                .getString("outcome", ""))
                                : initial;
        String priorRun = original.getString(attempt > 1 ? "run" : "productionRun");
        String earlierRun = attempt > 1 ? initial.getString("productionRun") : null;
        String middleRun =
                reset
                        ? new JSONObject(
                                        target.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                                .getString("outcome", ""))
                                .getString("run")
                        : null;
        if (!"verified".equals(original.getString("compileResult"))
                || original.getInt(attempt > 1 ? "sourceRevision" : "sourceRevisionAfter")
                        != beforeRevision
                || original.getInt("version") != beforeRevision)
            throw new AssertionError("Failed functional baseline missing");
        if ("send".equals(phase)
                && (!freeApproved
                        || pins.getBoolean("attempted", false)
                        || approvedHash == null
                        || !approvedHash.matches("[0-9a-f]{64}")
                        || !approvedHash.equals(pins.getString("contextSha256", ""))))
            throw new AssertionError("Exact free-only approval required");
        if ("preview".equals(phase) && pins.getBoolean("attempted", false))
            throw new AssertionError("Original attempt reserved");
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerUi.awaitProjectBinding(in, ui);
        String previousProject =
                mainValue(in, () -> ((ProjectIdentity) field(ui, "activeProject")).id());
        String previousChat = mainValue(in, () -> ((ChatStore.Chat) field(ui, "selectedChat")).id);
        boolean dialog = false;
        try {
            stage = "SELECT_BASELINE";
            selectProject(in, ui, baseline.getString("project", ""));
            selectChat(in, ui, baseline.getString("chat", ""));
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            ProjectIdentity project =
                    mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("ready".equals(phase)) {
                if (!pins.getBoolean("completed", false))
                    throw new AssertionError("No completed live attempt");
                requireSnapshot(
                        source,
                        pins.getInt("finalRevision", -1),
                        pins.getString("finalSnapshot", ""));
                SignedArtifactReceipt.Record receipt =
                        verifiedRecord(target, project, source.revision());
                if (receipt.versionCode != pins.getInt("finalVersion", -1)
                        || !receipt.sha256.equals(pins.getString("apkSha256", ""))
                        || !receipt.signerFingerprint.equals(original.getString("signerSha256")))
                    throw new AssertionError("Ready identity changed");
                awaitReady(in, ui, receipt.sha256);
                return markerPrefix
                        + "ready "
                        + new JSONObject(pins.getString("outcome", ""))
                        + "\n";
            }
            stage = "PIN_INPUT";
            if (source.revision() != beforeRevision)
                throw new AssertionError("Source baseline changed");
            SignedArtifactReceipt.Record old = verifiedRecord(target, project, beforeRevision);
            if (old.versionCode != beforeRevision
                    || !old.sha256.equals(original.getString("apkSha256"))
                    || !old.signerFingerprint.equals(original.getString("signerSha256")))
                throw new AssertionError("Baseline artifact changed");
            RunJournal.Snapshot prior = new RunJournal(target, project.id()).load(priorRun);
            if (prior.state != RunJournal.State.READY_TO_TEST
                    || (attempt == 1 && prior.sequence != original.getInt("journalSequence"))
                    || (attempt > 1
                            && pins.contains("priorSequence")
                            && prior.sequence != pins.getInt("priorSequence", -1))
                    || prior.sourceRevision != beforeRevision
                    || onlyLiveRun(target, project, priorRun, earlierRun, middleRun) != null)
                throw new AssertionError("Original journal changed or another attempt exists");
            if (attempt > 1 && !pins.contains("priorSequence")) {
                if (!"preview".equals(phase))
                    throw new AssertionError("Preview must pin prior sequence");
                save(pins.edit().putInt("priorSequence", prior.sequence));
            }
            String before = snapshotHash(source.readRecoverySources(beforeRevision));
            if (!before.equals(original.getString("finalSnapshotSha256")))
                throw new AssertionError("Original rejected source changed");
            String installedPath =
                    target.getPackageManager()
                            .getApplicationInfo(project.packageName(), 0)
                            .sourceDir;
            if (!sha256(Files.readAllBytes(new File(installedPath).toPath())).equals(old.sha256))
                throw new AssertionError("Installed baseline changed");
            if (pins.contains("snapshot") && !before.equals(pins.getString("snapshot", "")))
                throw new AssertionError("Pinned input changed");
            save(pins.edit().putString("snapshot", before));
            String previewPayload =
                    mainValue(
                            in,
                            () -> {
                                requireIdle(ui);
                                if (field(ui, "selectedCrashReport") != null
                                        || field(ui, "selectedScreenshot") != null
                                        || !source.readSource()
                                                .contentEquals(
                                                        ((TextBox) field(ui, "source")).getText())
                                        || (int) field(ui, "editorRevision") != beforeRevision)
                                    throw new AssertionError(
                                            "Unchanged source and observation-only context required");
                                ((TextBox) field(ui, reset ? "modelRequest" : "feedback"))
                                        .setText(reset ? request : observation);
                                return ((ContextPreviewGate) field(ui, "contextGate"))
                                        .previewChat(
                                                baseline.getString("chat", ""),
                                                request,
                                                beforeRevision)
                                        .payload();
                            });
            String contextHash = sha256(previewPayload.getBytes(StandardCharsets.UTF_8));
            if ("send".equals(phase) && !approvedHash.equals(contextHash))
                throw new AssertionError("Approved context changed");
            if (!reset) {
                onMain(
                        in,
                        () -> {
                            if (!button(ui, action).isShown()) Ui.state(ui).setToolsOpen(true);
                        });
                in.waitForIdleSync();
                onMain(
                        in,
                        () -> {
                            if (!button(ui, action).isShown()) Ui.state(ui).setToolsOpen(true);
                        });
                in.waitForIdleSync();
            }
            onMain(in, () -> reveal(button(ui, action)));
            in.waitForIdleSync();
            onMain(in, () -> visibleClick(button(ui, action)));
            waitNode(in, target, "android.widget.Button", positive);
            dialog = true;
            stage = "CONSENT_SCOPE";
            AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
            if (!hasPayload(root, previewPayload)
                    || !contains(root, "Java source or its existing XML layout"))
                throw new AssertionError("Displayed edit scope/context incorrect");
            if ("preview".equals(phase)) {
                click(in, target, "Cancel");
                dialog = false;
                requireSnapshot(source, beforeRevision, before);
                save(pins.edit().putString("contextSha256", contextHash));
                return markerPrefix
                        + "preview "
                        + new JSONObject()
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("contextSha256", contextHash)
                                .put(
                                        "contextBytes",
                                        previewPayload.getBytes(StandardCharsets.UTF_8).length)
                                .put("sourceRevision", beforeRevision)
                                .put("previewCancelled", true)
                                .put("modelRequestMade", false)
                        + "\n";
            }
            stage = "SEND_ONCE";
            long started = android.os.SystemClock.elapsedRealtime();
            requireSnapshot(source, beforeRevision, before);
            save(pins.edit().putBoolean("attempted", true));
            click(in, target, positive);
            dialog = false;
            while (!awaitIdle(in, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString("stream", "A33 feedback original app work pending; no retry\n");
                in.sendStatus(0, pending);
            }
            stage = "REAL_RESPONSE_OUTCOME";
            String runId = onlyLiveRun(target, project, priorRun, earlierRun, middleRun);
            if (runId == null) throw new AssertionError("Feedback run missing");
            RunJournal.Snapshot run = new RunJournal(target, project.id()).load(runId);
            JSONObject observed =
                    new JSONObject()
                            .put(
                                    "runId",
                                    reset
                                            ? "a33_counter02_20261012_01"
                                            : layout
                                                    ? "a33_counter01_20261012_01_feedback2"
                                                    : "a33_counter01_20261012_01_feedback1")
                            .put("promptId", reset ? "counter_02" : "counter_01")
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("run", runId)
                            .put("state", run.state.name())
                            .put("sourceRevision", source.revision())
                            .put("model", ModelClient.MODEL)
                            .put("freeOnly", true)
                            .put("repairs", run.repairs)
                            .put("sourceRevisionBefore", beforeRevision)
                            .put(
                                    "observedSendToSettledMs",
                                    android.os.SystemClock.elapsedRealtime() - started)
                            .put("contextSha256", contextHash)
                            .put(
                                    "finalSnapshotSha256",
                                    snapshotHash(source.readRecoverySources(source.revision())))
                            .put("developerSourceEdits", false)
                            .put(
                                    "providerReportedTokens",
                                    run.reportedTokens == null
                                            ? JSONObject.NULL
                                            : run.reportedTokens)
                            .put("providerReportedCost", JSONObject.NULL);
            save(pins.edit().putString("observedOutcome", observed.toString()));
            android.os.Bundle marker = new android.os.Bundle();
            marker.putString("stream", markerPrefix + "observed outcome " + observed + "\n");
            in.sendStatus(0, marker);
            if (run.state != RunJournal.State.READY_TO_TEST) {
                if (mainValue(in, () -> field(ui, "signedArtifact")) != null)
                    throw new AssertionError("Failed update gained readiness");
                observed.put(
                        "compileResult",
                        run.state == RunJournal.State.REPAIR_NEEDED ? "compile_error" : "failed");
                save(
                        pins.edit()
                                .putBoolean("settled", true)
                                .putString("outcome", observed.toString()));
                return markerPrefix + "send " + observed + "\n";
            }
            if (source.revision() != afterRevision
                    || run.sourceRevision != afterRevision
                    || run.versionCode == null
                    || run.versionCode != afterRevision)
                throw new AssertionError("Ready update revision changed");
            SignedArtifactReceipt.Record receipt =
                    verifiedRecord(target, project, source.revision());
            if (receipt.versionCode != afterRevision
                    || !receipt.signerFingerprint.equals(old.signerFingerprint))
                throw new AssertionError("Update lineage changed");
            awaitReady(in, ui, receipt.sha256);
            String finalSnapshot = snapshotHash(source.readRecoverySources(afterRevision));
            observed.put("version", receipt.versionCode)
                    .put("apkPath", receipt.apk.getAbsolutePath())
                    .put("apkSha256", receipt.sha256)
                    .put("apkBytes", receipt.apk.length())
                    .put("signerSha256", receipt.signerFingerprint)
                    .put("compileResult", "verified")
                    .put("contextSha256", contextHash)
                    .put("finalSnapshotSha256", finalSnapshot);
            save(
                    pins.edit()
                            .putBoolean("completed", true)
                            .putBoolean("settled", true)
                            .putInt("finalRevision", afterRevision)
                            .putInt("finalVersion", afterRevision)
                            .putString("finalSnapshot", finalSnapshot)
                            .putString("apkSha256", receipt.sha256)
                            .putString("outcome", observed.toString()));
            return markerPrefix + "send " + observed + "\n";
        } finally {
            if (dialog) click(in, target, "Cancel");
            while (!awaitIdle(in, ui, 30)) {
                android.os.Bundle waiting = new android.os.Bundle();
                waiting.putString(
                        "stream", "Original work still pending; waiting before restoration\n");
                in.sendStatus(0, waiting);
            }
            selectProject(in, ui, previousProject);
            selectChat(in, ui, previousChat);
            save(pins.edit().putBoolean("selectionRestored", true));
            onMain(in, ui::finish);
        }
    }

    private static boolean contains(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().contains(text)) return true;
        for (int i = 0; i < node.getChildCount(); i++)
            if (contains(node.getChild(i), text)) return true;
        return false;
    }

    private static String onlyLiveRun(
            Context context,
            ProjectIdentity project,
            String excluded,
            String earlier,
            String middle) {
        File file = new File(context.getNoBackupFilesDir(), "run-journal.sqlite");
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor ids =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=? AND run_id<>?"
                                        + (earlier == null ? "" : " AND run_id<>?")
                                        + (middle == null ? "" : " AND run_id<>?")
                                        + " LIMIT 2",
                                earlier == null
                                        ? new String[] {project.id(), excluded}
                                        : middle == null
                                                ? new String[] {project.id(), excluded, earlier}
                                                : new String[] {
                                                    project.id(), excluded, earlier, middle
                                                })) {
            if (!ids.moveToFirst()) return null;
            String id = ids.getString(0);
            if (ids.moveToNext() || !id.matches("run_[0-9a-f]{32}"))
                throw new AssertionError("Ambiguous live run");
            return id;
        }
    }

    @SuppressWarnings("unchecked")
    static void selectProject(Instrumentation in, Activity ui, String id) throws Exception {
        List<ProjectIdentity> projects =
                mainValue(in, () -> (List<ProjectIdentity>) field(ui, "projectList"));
        for (int n = 0; n < projects.size(); n++)
            if (id.equals(projects.get(n).id())) {
                int index = n;
                onMain(in, () -> ((Picker) field(ui, "projectPicker")).select(index));
                in.waitForIdleSync();
                ControllerUi.awaitProjectBinding(in, ui);
                return;
            }
        throw new AssertionError("Project missing");
    }

    @SuppressWarnings("unchecked")
    static void selectChat(Instrumentation in, Activity ui, String id) throws Exception {
        List<ChatStore.Chat> chats = mainValue(in, () -> (List<ChatStore.Chat>) field(ui, "chats"));
        for (int n = 0; n < chats.size(); n++)
            if (id.equals(chats.get(n).id)) {
                int index = n;
                onMain(in, () -> ((Picker) field(ui, "chatPicker")).select(index));
                in.waitForIdleSync();
                return;
            }
        throw new AssertionError("Chat missing");
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Pin persistence failed");
    }
}
