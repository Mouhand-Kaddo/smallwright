package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** One frozen Reset request on the accepted counter project, through the real approval UI. */
final class PhoneAlphaResetProbe {
    private static final String RUN_ID = "s23_counter02_20261002_01";
    private static final String PREFS = "phone-alpha-reset-" + RUN_ID;
    private static final String FEEDBACK_RUN_ID = RUN_ID + "_feedback1";
    private static final String FEEDBACK_PREFS = PREFS + "-feedback1";
    private static final String FEEDBACK =
            "The Reset confirmation saves zero, but leaves the visible Counter label stale: starting at 2, Reset then Yes still shows Counter: 2; relaunch shows Counter: 0. Cancel correctly keeps 2. Fix the confirmed Reset so the visible counter immediately shows 0 and Increment/Decrement continue from that same saved count. Keep a single counter label and the working confirmation/cancel behavior.";
    private static final String REQUEST =
            "Add a Reset button that asks for confirmation before setting the counter to zero.";
    private static final String PROJECT = "app492b70d4a41d4f558c116070a6083775";
    private static final String CHAT = "39d7c43fced54c958ed26a3092ff54a8";
    private static final String SOURCE_HASH =
            "f4ba7822958a61e3049cc4abb2d46338b6d0e56724ffeee16f2a54308feef812";
    private static final String APK_HASH =
            "3a43a5715c07d6b44fcef6459e165a18e2bf32efce48f96276ee09b1860a85bf";
    private static final String SOURCE4_HASH =
            "a43b0de86f716df27656240aef3fb26ca7b0c2b90aefdb102c3211da476dd711";
    private static final String APK4_HASH =
            "6596678935a5408ce9fcd673f2f982e40286caec2d36b53cbd0962811b8302f8";
    private static final String SIGNER =
            "a1e8aca58fe6f360707f3036bd552b4a93625cd8d0c0908c22d4ccd79f704cb7";

    static String run(Instrumentation inst, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        boolean feedback = "feedbackPreview".equals(phase) || "feedback".equals(phase);
        boolean restore = "restore".equals(phase) || "feedbackRestore".equals(phase);
        boolean send = "send".equals(phase) || "feedback".equals(phase);
        if (!"prepare".equals(phase) && !"preview".equals(phase) && !send && !restore && !feedback)
            throw new PhoneAlphaCounterProbe.GuardFailure();
        Context target = inst.getTargetContext();
        SharedPreferences initial = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences pins =
                feedback
                        ? feedbackPins(target, initial, "feedbackPreview".equals(phase))
                        : "feedbackRestore".equals(phase)
                                ? target.getSharedPreferences(FEEDBACK_PREFS, Context.MODE_PRIVATE)
                                : initial;
        int before = pins.getBoolean("feedback", false) ? 4 : 3;
        String beforeHash = before == 4 ? SOURCE4_HASH : SOURCE_HASH;
        if ("prepare".equals(phase) ? pins.contains("reserved") : !pins.contains("project")) fail();
        if (send
                && (!freeApproved
                        || pins.contains("attempted")
                        || approvedHash == null
                        || !approvedHash.matches("[0-9a-f]{64}")
                        || !approvedHash.equals(pins.getString("previewSha256", "")))) fail();
        if ("prepare".equals(phase)) save(pins.edit().putBoolean("reserved", true));
        Activity activity =
                inst.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        inst.waitForIdleSync();
        ControllerUi.awaitProjectBinding(inst, activity);
        try {
            ProjectIdentity project =
                    mainValue(
                            inst,
                            () -> {
                                ProjectIdentity own =
                                        (ProjectIdentity) field(activity, "activeProject");
                                ChatStore.Chat selected =
                                        (ChatStore.Chat) field(activity, "selectedChat");
                                if (own == null
                                        || selected == null
                                        || !PROJECT.equals(own.id())
                                        || !CHAT.equals(selected.id)) fail();
                                requireIdle(activity);
                                return own;
                            });
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("prepare".equals(phase)) {
                baseline(target, project, source, 3);
                Map<Integer, String> prior = PhoneAlphaRunHistory.priorRuns(target, project, 3);
                SharedPreferences.Editor prepared =
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString("title", project.title())
                                .putString("chat", CHAT)
                                .putInt(
                                        "workerUid",
                                        target.getPackageManager()
                                                .getApplicationInfo("dev.smallwright.worker", 0)
                                                .uid);
                for (Map.Entry<Integer, String> run : prior.entrySet())
                    prepared.putString("priorRun" + run.getKey(), run.getValue());
                save(prepared);
                return "Reset run="
                        + RUN_ID
                        + " prompt=counter_02 project="
                        + project.id()
                        + " phase=prepare revisionBefore=3 sourceBeforeSha256="
                        + SOURCE_HASH
                        + " baselineVersion=3\n";
            }
            onMain(inst, () -> requireOwn(activity, pins));
            if (restore) {
                if (!pins.contains("compileResult")) fail();
                requireSnapshot(
                        source,
                        pins.getInt("finalRevision", -1),
                        pins.getString("finalSourceSha256", ""));
                if ("verified".equals(pins.getString("compileResult", ""))) {
                    SignedArtifactReceipt.Record artifact =
                            verifiedRecord(target, project, source.revision());
                    if (!artifact.sha256.equals(pins.getString("artifactSha256", ""))
                            || artifact.versionCode != pins.getInt("version", -1)
                            || !artifact.signerFingerprint.equals(
                                    pins.getString("signerSha256", ""))
                            || !artifact.apk
                                    .getAbsolutePath()
                                    .equals(pins.getString("artifactPath", ""))) fail();
                    awaitReady(inst, activity, artifact.sha256);
                }
                return summary(pins) + " phase=" + phase + "\n";
            }
            if (pins.contains("attempted")) fail();
            baseline(target, project, source, before);
            PhoneAlphaRunHistory.verifyPriorRuns(
                    target, project, pins, pins.getBoolean("feedback", false) ? 4 : 3);
            ContextPreviewGate.Preview preview =
                    mainValue(
                            inst,
                            () -> {
                                requireOwn(activity, pins);
                                requireIdle(activity);
                                requireSnapshot(source, before, beforeHash);
                                if ((int) field(activity, "editorRevision") != before
                                        || !source.readSource()
                                                .contentEquals(
                                                        ((TextBox) field(activity, "source"))
                                                                .getText())
                                        || field(activity, "pendingRecovery") != null) fail();
                                if (feedback) {
                                    if (field(activity, "selectedCrashReport") != null
                                            || field(activity, "selectedScreenshot") != null)
                                        fail();
                                    ((TextBox) field(activity, "feedback")).setText(FEEDBACK);
                                } else ((TextBox) field(activity, "modelRequest")).setText(REQUEST);
                                return ((ContextPreviewGate) field(activity, "contextGate"))
                                        .previewChat(
                                                CHAT,
                                                feedback
                                                        ? FeedbackRequest.compose(FEEDBACK)
                                                        : REQUEST,
                                                before);
                            });
            String hash = hash(preview.payload());
            if (send && !hash.equals(approvedHash)) fail();
            String action = feedback ? "Send my feedback" : Labels.GENERATE;
            String positive = feedback ? "Send & update" : "Send & generate";
            if (feedback) {
                onMain(
                        inst,
                        () -> {
                            requireOwn(activity, pins);
                            requireIdle(activity);
                            if (!button(activity, action).isShown())
                                Ui.state(activity).setToolsOpen(true);
                        });
                inst.waitForIdleSync();
                onMain(
                        inst,
                        () -> {
                            requireOwn(activity, pins);
                            requireIdle(activity);
                            if (!button(activity, action).isShown())
                                Ui.state(activity).setToolsOpen(true);
                        });
                inst.waitForIdleSync();
            }
            onMain(inst, () -> reveal(button(activity, action)));
            inst.waitForIdleSync();
            onMain(inst, () -> visibleClick(button(activity, action)));
            waitNode(inst, target, "android.widget.Button", positive);
            if (!hasPayload(inst.getUiAutomation().getRootInActiveWindow(), preview.payload()))
                fail();
            if (!send) {
                click(inst, target, "Cancel");
                inst.waitForIdleSync();
                baseline(target, project, source, before);
                PhoneAlphaRunHistory.verifyPriorRuns(
                        target, project, pins, pins.getBoolean("feedback", false) ? 4 : 3);
                save(pins.edit().putString("previewSha256", hash));
                return "Reset run="
                        + (feedback ? FEEDBACK_RUN_ID : RUN_ID)
                        + " prompt=counter_02 project="
                        + project.id()
                        + " phase="
                        + phase
                        + " revisionBefore="
                        + before
                        + " contextSha256="
                        + hash
                        + " model="
                        + ModelClient.MODEL
                        + " freeOnly=true destination=OpenRouter_and_selected_free_provider previewCancelled=true\n";
            }
            baseline(target, project, source, before);
            PhoneAlphaRunHistory.verifyPriorRuns(
                    target, project, pins, pins.getBoolean("feedback", false) ? 4 : 3);
            onMain(
                    inst,
                    () -> {
                        requireOwn(activity, pins);
                        requireIdle(activity);
                        if (feedback
                                ? !FEEDBACK.contentEquals(
                                                ((TextBox) field(activity, "feedback")).getText())
                                        || field(activity, "selectedCrashReport") != null
                                        || field(activity, "selectedScreenshot") != null
                                : !REQUEST.contentEquals(
                                        ((TextBox) field(activity, "modelRequest")).getText()))
                            fail();
                        if (!hash.equals(
                                hash(
                                        ((ContextPreviewGate) field(activity, "contextGate"))
                                                .previewChat(
                                                        CHAT,
                                                        feedback
                                                                ? FeedbackRequest.compose(FEEDBACK)
                                                                : REQUEST,
                                                        before)
                                                .payload()))) fail();
                    });
            save(
                    pins.edit()
                            .putBoolean("attempted", true)
                            .putLong("startElapsedMs", SystemClock.elapsedRealtime()));
            click(inst, target, positive);
            inst.waitForIdleSync();
            boolean timeout = !awaitIdle(inst, activity, 150);
            if (timeout) stop(inst, activity, pins);
            outcome(
                    inst,
                    activity,
                    target,
                    project,
                    source,
                    pins,
                    timeout,
                    awaitIdle(inst, activity, 15));
            return summary(pins) + " phase=" + phase + "\n";
        } finally {
            if (pins.contains("attempted")) {
                onMain(inst, () -> requireOwn(activity, pins));
                if (mainValue(inst, () -> busy(activity))) stop(inst, activity, pins);
                if (!pins.contains("compileResult")) {
                    ProjectIdentity own = mainValue(inst, () -> requireOwn(activity, pins));
                    outcome(
                            inst,
                            activity,
                            target,
                            own,
                            new ProjectSourceStore(own),
                            pins,
                            false,
                            !mainValue(inst, () -> busy(activity)));
                }
            }
            onMain(inst, activity::finish);
        }
    }

    private static SharedPreferences feedbackPins(
            Context target, SharedPreferences initial, boolean initialize) {
        if (!PROJECT.equals(initial.getString("project", ""))
                || !CHAT.equals(initial.getString("chat", ""))
                || !initial.getBoolean("settled", false)
                || !"verified".equals(initial.getString("compileResult", ""))
                || initial.getInt("finalRevision", -1) != 4
                || initial.getInt("version", -1) != 4
                || !SOURCE4_HASH.equals(initial.getString("finalSourceSha256", ""))
                || !APK4_HASH.equals(initial.getString("artifactSha256", ""))
                || !SIGNER.equals(initial.getString("signerSha256", ""))) fail();
        RunJournal.Snapshot previous =
                new RunJournal(target, PROJECT).load(initial.getString("productionRun", ""));
        if (previous.state != RunJournal.State.READY_TO_TEST
                || previous.sourceRevision != 4
                || previous.versionCode == null
                || previous.versionCode != 4) fail();
        SharedPreferences record =
                target.getSharedPreferences(FEEDBACK_PREFS, Context.MODE_PRIVATE);
        if (record.contains("attempted")) fail();
        if (!record.contains("project")) {
            if (!initialize) fail();
            SharedPreferences.Editor prepared =
                    record.edit()
                            .putBoolean("feedback", true)
                            .putString("project", PROJECT)
                            .putString("package", initial.getString("package", ""))
                            .putString("title", initial.getString("title", ""))
                            .putString("chat", CHAT)
                            .putInt("workerUid", initial.getInt("workerUid", -1))
                            .putString("priorRun4", previous.runId);
            for (int i = 1; i <= 3; i++)
                prepared.putString("priorRun" + i, initial.getString("priorRun" + i, ""));
            save(prepared);
        }
        for (String key : new String[] {"project", "package", "title", "chat"})
            if (!record.getString(key, "").equals(initial.getString(key, ""))) fail();
        if (!record.getBoolean("feedback", false)
                || !previous.runId.equals(record.getString("priorRun4", ""))) fail();
        for (int i = 1; i <= 3; i++)
            if (!record.getString("priorRun" + i, "").equals(initial.getString("priorRun" + i, "")))
                fail();
        return record;
    }

    private static void baseline(
            Context target, ProjectIdentity project, ProjectSourceStore source, int before)
            throws Exception {
        if (before != 3 && before != 4) fail();
        requireSnapshot(source, before, before == 4 ? SOURCE4_HASH : SOURCE_HASH);
        SignedArtifactReceipt.Record artifact = verifiedRecord(target, project, before);
        if (artifact.versionCode != before
                || !(before == 4 ? APK4_HASH : APK_HASH).equals(artifact.sha256)
                || !SIGNER.equals(artifact.signerFingerprint)) fail();
        android.content.pm.PackageInfo installed =
                target.getPackageManager()
                        .getPackageInfo(
                                project.packageName(),
                                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
        if (installed.getLongVersionCode() != before
                || installed.signingInfo == null
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !SIGNER.equals(
                        sha256(installed.signingInfo.getApkContentsSigners()[0].toByteArray())))
            fail();
    }

    private static void outcome(
            Instrumentation inst,
            Activity activity,
            Context target,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences pins,
            boolean timeout,
            boolean settled)
            throws Exception {
        onMain(inst, () -> requireOwn(activity, pins));
        String id =
                PhoneAlphaRunHistory.newRun(
                        target, project, pins, pins.getBoolean("feedback", false) ? 4 : 3);
        RunJournal.Snapshot run = id == null ? null : new RunJournal(target, project.id()).load(id);
        int revision = source.revision();
        String result = settled ? "failed" : "unknown",
                category = timeout ? "deadline_stop_requested" : "generation_failed";
        if (run != null && run.state == RunJournal.State.REPAIR_NEEDED) {
            result = "compile_error";
            category = "java_compile";
        } else if (run != null && run.state == RunJournal.State.CANCELLED) {
            result = "cancelled";
            category = "cancelled";
        } else if (settled) {
            String status =
                    mainValue(
                            inst, () -> ((TextBox) field(activity, "status")).getText().toString());
            for (String line : status.split("\n"))
                if (line.equals(
                                "Model proposal could not be applied to the current source revision. No new APK is ready.")
                        || line.startsWith(
                                "Generation stopped at model response: Invalid model response")) {
                    result = "policy_rejected";
                    category = "proposal_policy";
                    break;
                }
        }
        SharedPreferences.Editor recorded =
                pins.edit()
                        .putInt("finalRevision", revision)
                        .putString(
                                "finalSourceSha256",
                                snapshotHash(source.readRecoverySources(revision)))
                        .putBoolean("settled", settled);
        if (settled) recorded.putLong("endElapsedMs", SystemClock.elapsedRealtime());
        if (id != null) recorded.putString("productionRun", id);
        if (run != null && run.reportedTokens != null)
            recorded.putInt("reportedTokens", run.reportedTokens);
        if (settled && run != null && run.state == RunJournal.State.READY_TO_TEST) {
            SignedArtifactReceipt.Record artifact = verifiedRecord(target, project, revision);
            int before = pins.getBoolean("feedback", false) ? 4 : 3;
            if (revision <= before
                    || artifact.versionCode <= before
                    || !SIGNER.equals(artifact.signerFingerprint)) {
                result = "failed";
                category = "update_identity";
            } else {
                awaitReady(inst, activity, artifact.sha256);
                result = "verified";
                category = "signed_receipt_verified";
                recorded.putString("artifactSha256", artifact.sha256)
                        .putString("signerSha256", artifact.signerFingerprint)
                        .putInt("version", artifact.versionCode)
                        .putString("artifactPath", artifact.apk.getAbsolutePath());
            }
        }
        save(recorded.putString("compileResult", result).putString("outcomeCategory", category));
    }

    private static String summary(SharedPreferences pins) {
        long start = pins.getLong("startElapsedMs", -1), end = pins.getLong("endElapsedMs", -1);
        boolean feedback = pins.getBoolean("feedback", false);
        return "Reset run="
                + (feedback ? FEEDBACK_RUN_ID : RUN_ID)
                + " prompt=counter_02 project="
                + PROJECT
                + " package="
                + pins.getString("package", "")
                + " chat="
                + CHAT
                + " origin="
                + (feedback ? "observed_feedback" : "frozen_request")
                + " revisionBefore="
                + (feedback ? 4 : 3)
                + " sourceBeforeSha256="
                + (feedback ? SOURCE4_HASH : SOURCE_HASH)
                + " revisionAfter="
                + pins.getInt("finalRevision", -1)
                + " sourceSha256="
                + pins.getString("finalSourceSha256", "")
                + " model="
                + ModelClient.MODEL
                + " freeOnly=true contextSha256="
                + pins.getString("previewSha256", "")
                + " compile_result="
                + pins.getString("compileResult", "unknown")
                + " outcome="
                + pins.getString("outcomeCategory", "unknown")
                + " generationStartElapsedMs="
                + start
                + " generationEndElapsedMs="
                + (end >= 0 ? Long.toString(end) : "null")
                + " totalGenerationDurationMs="
                + (end >= start && start >= 0 ? Long.toString(end - start) : "null")
                + " phone_build_elapsed_ms=null providerReportedTokensCumulative="
                + (pins.contains("reportedTokens") ? pins.getInt("reportedTokens", 0) : "null")
                + " providerReportedCostUsd=null settled="
                + pins.getBoolean("settled", false)
                + " workerUid="
                + pins.getInt("workerUid", -1)
                + " artifactSha256="
                + pins.getString("artifactSha256", "null")
                + " signerSha256="
                + pins.getString("signerSha256", "null")
                + " version="
                + (pins.contains("version") ? pins.getInt("version", -1) : "null")
                + " apk="
                + pins.getString("artifactPath", "null")
                + " install_result=not_attempted functional_result=untested";
    }

    private static String hash(String value) throws Exception {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) fail();
    }

    private static void fail() {
        throw new PhoneAlphaCounterProbe.GuardFailure();
    }
}
