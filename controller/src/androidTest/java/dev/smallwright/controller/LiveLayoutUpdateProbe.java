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
import java.util.List;
import org.json.JSONObject;

/** One real source3/version3 XML update; keeps every source/APK byte on the phone. */
final class LiveLayoutUpdateProbe {
    private static final String PREFS = "live-layout-update-v1";
    private static final String REQUEST =
            "Change only res/layout/empty.xml: rename the existing Add resource button to XML add one. Preserve all resource IDs, the Resource count label, layout structure and every other attribute. Return the entire XML file with only that button text changed. Do not change MainActivity.java, the package, permissions, dependencies, saved-count key or increment behavior.";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation in, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        stage = "GUARD";
        if (!List.of("preview", "send", "ready").contains(phase))
            throw new AssertionError("Unknown phase");
        if (!"cohere/north-mini-code:free".equals(ModelClient.MODEL))
            throw new AssertionError("Wrong model");
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences baseline =
                target.getSharedPreferences("layout-edit-acceptance-v1", Context.MODE_PRIVATE);
        if (!baseline.getBoolean("verified", false)
                || baseline.getInt("revision", -1) != 3
                || baseline.getInt("version", -1) != 3)
            throw new AssertionError("Accepted XML baseline missing");
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
                requireSnapshot(source, 4, pins.getString("finalSnapshot", ""));
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 4);
                if (receipt.versionCode != 4
                        || !receipt.sha256.equals(pins.getString("apkSha256", ""))
                        || !receipt.signerFingerprint.equals(baseline.getString("signer", "")))
                    throw new AssertionError("Ready identity changed");
                awaitReady(in, ui, receipt.sha256);
                return "Live layout ready PASS "
                        + new JSONObject(pins.getString("outcome", ""))
                        + "\n";
            }
            stage = "PIN_INPUT";
            if (source.revision() != 3) throw new AssertionError("Source baseline changed");
            SignedArtifactReceipt.Record old = verifiedRecord(target, project, 3);
            if (old.versionCode != 3
                    || !old.sha256.equals(baseline.getString("sha256", ""))
                    || !old.signerFingerprint.equals(baseline.getString("signer", "")))
                throw new AssertionError("Baseline artifact changed");
            String mainPath =
                    "java/" + project.packageName().replace('.', '/') + "/MainActivity.java";
            java.util.Map<String, byte[]> files = source.readRecoverySources(3);
            String before = snapshotHash(files),
                    javaHash = sha256(files.get(mainPath)),
                    layoutHash = sha256(files.get("res/layout/empty.xml"));
            if (pins.contains("snapshot")
                    && (!before.equals(pins.getString("snapshot", ""))
                            || !javaHash.equals(pins.getString("javaSha256", ""))
                            || !layoutHash.equals(pins.getString("layoutSha256", ""))))
                throw new AssertionError("Pinned input changed");
            save(
                    pins.edit()
                            .putString("snapshot", before)
                            .putString("javaSha256", javaHash)
                            .putString("layoutSha256", layoutHash));
            String previewPayload =
                    mainValue(
                            in,
                            () -> {
                                requireIdle(ui);
                                ((TextBox) field(ui, "modelRequest")).setText(REQUEST);
                                return ((ContextPreviewGate) field(ui, "contextGate"))
                                        .previewChat(baseline.getString("chat", ""), REQUEST, 3)
                                        .payload();
                            });
            String contextHash = sha256(previewPayload.getBytes(StandardCharsets.UTF_8));
            if ("send".equals(phase) && !approvedHash.equals(contextHash))
                throw new AssertionError("Approved context changed");
            onMain(in, () -> reveal(button(ui, Labels.GENERATE)));
            in.waitForIdleSync();
            onMain(in, () -> visibleClick(button(ui, Labels.GENERATE)));
            waitNode(in, target, "android.widget.Button", "Send & generate");
            dialog = true;
            stage = "CONSENT_SCOPE";
            AccessibilityNodeInfo root = in.getUiAutomation().getRootInActiveWindow();
            if (!hasPayload(root, previewPayload)
                    || !contains(root, "Java source or its existing XML layout"))
                throw new AssertionError("Displayed edit scope/context incorrect");
            if ("preview".equals(phase)) {
                click(in, target, "Cancel");
                dialog = false;
                requireSnapshot(source, 3, before);
                save(pins.edit().putString("contextSha256", contextHash));
                return "Live layout preview PASS "
                        + new JSONObject()
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("contextSha256", contextHash)
                                .put(
                                        "contextBytes",
                                        previewPayload.getBytes(StandardCharsets.UTF_8).length)
                                .put("sourceRevision", 3)
                                .put("javaSha256", javaHash)
                                .put("layoutSha256", layoutHash)
                                .put("previewCancelled", true)
                                .put("modelRequestMade", false)
                        + "\n";
            }
            stage = "SEND_ONCE";
            requireSnapshot(source, 3, before);
            save(pins.edit().putBoolean("attempted", true));
            click(in, target, "Send & generate");
            dialog = false;
            while (!awaitIdle(in, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString("stream", "Live layout original app work pending; no retry\n");
                in.sendStatus(0, pending);
            }
            stage = "REAL_RESPONSE_OUTCOME";
            String runId = onlyLiveRun(target, project);
            RunJournal.Snapshot run = new RunJournal(target, project.id()).load(runId);
            JSONObject observed =
                    new JSONObject()
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("run", runId)
                            .put("state", run.state.name())
                            .put("sourceRevision", source.revision())
                            .put("model", ModelClient.MODEL)
                            .put(
                                    "providerReportedTokens",
                                    run.reportedTokens == null
                                            ? JSONObject.NULL
                                            : run.reportedTokens)
                            .put("providerReportedCost", JSONObject.NULL);
            save(pins.edit().putString("observedOutcome", observed.toString()));
            android.os.Bundle marker = new android.os.Bundle();
            marker.putString("stream", "Live layout observed outcome " + observed + "\n");
            in.sendStatus(0, marker);
            if (run.state != RunJournal.State.READY_TO_TEST
                    || source.revision() != 4
                    || run.sourceRevision != 4
                    || run.versionCode == null
                    || run.versionCode != 4)
                throw new AssertionError("Live request did not create exact ready update");
            java.util.Map<String, byte[]> after = source.readRecoverySources(4);
            if (!javaHash.equals(sha256(after.get(mainPath)))
                    || layoutHash.equals(sha256(after.get("res/layout/empty.xml"))))
                throw new AssertionError("Model changed wrong file or no layout");
            SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 4);
            if (receipt.versionCode != 4
                    || !receipt.signerFingerprint.equals(old.signerFingerprint))
                throw new AssertionError("Update lineage changed");
            awaitReady(in, ui, receipt.sha256);
            String finalSnapshot = snapshotHash(after);
            observed.put("version", receipt.versionCode)
                    .put("apkPath", receipt.apk.getAbsolutePath())
                    .put("apkSha256", receipt.sha256)
                    .put("apkBytes", receipt.apk.length())
                    .put("signerSha256", receipt.signerFingerprint)
                    .put("javaUnchanged", true)
                    .put("layoutChanged", true)
                    .put("contextSha256", contextHash)
                    .put("finalSnapshotSha256", finalSnapshot);
            save(
                    pins.edit()
                            .putBoolean("completed", true)
                            .putString("finalSnapshot", finalSnapshot)
                            .putString("apkSha256", receipt.sha256)
                            .putString("outcome", observed.toString()));
            return "Live layout update PASS " + observed + "\n";
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

    private static String onlyLiveRun(Context context, ProjectIdentity project) {
        File file = new File(context.getNoBackupFilesDir(), "run-journal.sqlite");
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor ids =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=? AND substr(run_id,1,4)='run_' LIMIT 2",
                                new String[] {project.id()})) {
            if (!ids.moveToFirst()) throw new AssertionError("Live run missing");
            String id = ids.getString(0);
            if (ids.moveToNext() || !id.matches("run_[0-9a-f]{32}"))
                throw new AssertionError("Ambiguous live run");
            return id;
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectProject(Instrumentation in, Activity ui, String id) throws Exception {
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
    private static void selectChat(Instrumentation in, Activity ui, String id) throws Exception {
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
