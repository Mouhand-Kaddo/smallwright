package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.SystemClock;
import com.android.apksig.ApkVerifier;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Observe real production HTTPS cancellation; never reads credentials or changes transport. */
final class ProviderCancellationProbe {
    private static final String PREFS = "provider-cancellation-fixture-v2";
    private static final String TITLE = "A33 live provider cancellation fixture";
    private static final String REQUEST =
            "Add a native Reset button that sets the saved counter to zero and immediately refreshes "
                    + "the same visible Counter label. Preserve Increment and stored count behavior. "
                    + "Use only existing Android framework APIs and no new permissions or dependencies.";
    private static String stage = "GUARD";
    // Predeclared evidence freshness cap, not a provider/UI performance measurement.
    private static final long MAX_OBSERVATION_TO_STOP_NANOS = 5_000_000;

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        stage = "GUARD";
        if (!java.util.List.of(
                        "prepare",
                        "preview",
                        "send",
                        "stopped",
                        "recover",
                        "recoverAfterVisibilityFailure",
                        "ready",
                        "inspect",
                        "restoreInspectedSelection",
                        "restoreSelection")
                .contains(phase)) throw new AssertionError("Unknown provider cancellation phase");
        if (!"cohere/north-mini-code:free".equals(ModelClient.MODEL))
            throw new AssertionError("Model policy changed");
        Context target = inst.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("reserved"))
                throw new AssertionError("One-shot fixture already reserved");
            save(
                    pins.edit()
                            .putBoolean("reserved", true)
                            .putString(
                                    "previousProject",
                                    target.getSharedPreferences(
                                                    "MainActivity", Context.MODE_PRIVATE)
                                            .getString("active-project", null)));
        } else if (!pins.contains("project")) throw new AssertionError("No owned fixture");
        if ("send".equals(phase)
                && (!freeApproved
                        || pins.contains("attempted")
                        || approvedHash == null
                        || !approvedHash.matches("[0-9a-f]{64}")
                        || !approvedHash.equals(pins.getString("previewSha256", ""))))
            throw new AssertionError("Exact free-only one-shot approval required");
        if ("restoreSelection".equals(phase)) return restoreSelection(target, pins);
        if ("restoreInspectedSelection".equals(phase)) {
            if (!pins.contains("inspection"))
                throw new AssertionError("No preserved original outcome");
            RunJournal.Snapshot original =
                    new RunJournal(target, pins.getString("project", ""))
                            .load(pins.getString("run", ""));
            if (original.isActive() && original.state != RunJournal.State.REPAIR_NEEDED)
                throw new AssertionError("Original run active");
            if (!pins.getString("project", "")
                    .equals(
                            target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                                    .getString("active-project", null)))
                throw new AssertionError("Selection changed");
            String previous = pins.getString("previousProject", null);
            if (previous != null)
                new ProjectRegistry(
                                new File(target.getFilesDir(), "projects"), target.getFilesDir())
                        .get(previous);
            save(
                    target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                            .edit()
                            .putString("active-project", previous));
            return "Provider cancellation original failed-test selection restored; project/run/source/artifact retained\n";
        }
        Activity ui =
                inst.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui);
            if ("prepare".equals(phase)) {
                stage = "NEW_PROJECT";
                CompilerMeasurementProbe.createProject(inst, ui, TITLE);
                ProjectIdentity project =
                        mainValue(inst, () -> (ProjectIdentity) field(ui, "activeProject"));
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!TITLE.equals(project.title())
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot()
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError("Pristine fixture differs");
                String chat =
                        mainValue(inst, () -> ((ChatStore.Chat) field(ui, "selectedChat")).id);
                String sha = snapshotHash(source.readRecoverySources(0));
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString("title", TITLE)
                                .putString("chat", chat)
                                .putString("sourceSha256", sha));
                return "Provider cancellation prepare PASS "
                        + new JSONObject()
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("sourceRevision", 0)
                                .put("snapshotSha256", sha)
                                .put("modelRequestMade", false)
                        + "\n";
            }
            stage = "OWN_PROJECT";
            ProjectIdentity project = mainValue(inst, () -> requireOwn(ui, pins));
            ProjectSourceStore source = new ProjectSourceStore(project);
            RunJournal journal = new RunJournal(target, project.id());
            if ("inspect".equals(phase)) {
                stage = "INSPECT_ORIGINAL_SAVED_RUN";
                if (!pins.getBoolean("attempted", false) || pins.contains("inspection"))
                    throw new AssertionError("No uninspected original attempt");
                requireIdle(ui);
                RunJournal.Snapshot original = journal.load(pins.getString("run", ""));
                if (original.isActive() && original.state != RunJournal.State.REPAIR_NEEDED)
                    throw new AssertionError("Original run active");
                JSONObject inspection =
                        new JSONObject()
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("run", original.runId)
                                .put("journalState", original.state.name())
                                .put("journalSequence", original.sequence)
                                .put("sourceRevision", source.revision())
                                .put(
                                        "snapshotSha256",
                                        snapshotHash(source.readRecoverySources(source.revision())))
                                .put(
                                        "journalVersion",
                                        original.versionCode == null
                                                ? JSONObject.NULL
                                                : original.versionCode)
                                .put(
                                        "providerReportedTokens",
                                        original.reportedTokens == null
                                                ? JSONObject.NULL
                                                : original.reportedTokens)
                                .put("providerReportedCost", JSONObject.NULL)
                                .put("additionalModelRequest", false)
                                .put("originalCancellationTestPassed", false);
                if (original.state == RunJournal.State.READY_TO_TEST) {
                    SignedArtifactReceipt.Record receipt =
                            verifiedRecord(target, project, source.revision());
                    receipt.artifact.verifyUnchanged();
                    ArtifactValidator.validateUnsigned(
                            target, receipt.apk, receipt.versionCode, project.packageName());
                    if (!new ApkVerifier.Builder(receipt.apk).build().verify().isVerified()
                            || !receipt.signerFingerprint.equals(
                                    new ProjectSigner(project).verifyExisting(target, receipt.apk)))
                        throw new AssertionError("Original signed artifact invalid");
                    inspection
                            .put("apkSha256", receipt.sha256)
                            .put("signerSha256", receipt.signerFingerprint)
                            .put("apkPath", receipt.apk.getAbsolutePath())
                            .put("apkBytes", receipt.apk.length());
                }
                save(pins.edit().putString("inspection", inspection.toString()));
                return "Provider cancellation original failed-test inspection " + inspection + "\n";
            }
            if ("ready".equals(phase)) {
                if (!pins.getBoolean("recovered", false)) throw new AssertionError("No recovery");
                SignedArtifactReceipt.Record receipt =
                        verifyReady(inst, target, ui, project, source, pins);
                return "Provider cancellation ready PASS "
                        + artifactResult(project, receipt).put("additionalModelRequest", false)
                        + "\n";
            }
            if ("stopped".equals(phase)
                    || "recover".equals(phase)
                    || "recoverAfterVisibilityFailure".equals(phase)) {
                if (!pins.getBoolean("stopped", false)
                        || pins.getBoolean("recovered", false)
                        || "recover".equals(phase) && pins.getBoolean("recoveryAttempted", false)
                        || "recoverAfterVisibilityFailure".equals(phase)
                                && (!pins.getBoolean("recoveryAttempted", false)
                                        || pins.getBoolean("recoveryClickAttempted", false)))
                    throw new AssertionError(
                            "No exact stopped fixture or recovery already attempted");
                requireStopped(inst, ui, project, source, journal, pins);
                if ("stopped".equals(phase))
                    return "Provider cancellation stopped reopen PASS exact source0/cancelled run; no automatic request/build/readiness\n";
                stage = "VISIBLE_FRESH_BUILD";
                save(pins.edit().putBoolean("recoveryAttempted", true));
                ActiveWorkerStopProbe.showTools(inst, ui);
                onMain(inst, () -> reveal(button(ui, Labels.BUILD_SOURCE)));
                inst.waitForIdleSync();
                onMain(
                        inst,
                        () -> {
                            Control build = button(ui, Labels.BUILD_SOURCE);
                            save(pins.edit().putBoolean("recoveryClickAttempted", true));
                            visibleClick(build);
                            if (!(boolean) field(ui, "buildInProgress"))
                                throw new AssertionError("Visible fresh Build did not start");
                        });
                settle(inst, ui);
                requireSnapshot(source, 1, pins.getString("sourceSha256", ""));
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
                verifyIdentity(inst, target, ui, project, receipt);
                RunJournal.Snapshot cancelled = journal.load(pins.getString("run", ""));
                if (cancelled.state != RunJournal.State.CANCELLED
                        || cancelled.sequence != pins.getInt("sequence", -1))
                    throw new AssertionError("Cancelled run changed during fresh local build");
                save(
                        pins.edit()
                                .putBoolean("recovered", true)
                                .putString("apkSha256", receipt.sha256)
                                .putString("signerSha256", receipt.signerFingerprint));
                return "Provider cancellation recovery PASS "
                        + artifactResult(project, receipt).put("additionalModelRequest", false)
                        + "\n";
            }
            stage = "EXACT_PREVIEW";
            requireSnapshot(source, 0, pins.getString("sourceSha256", ""));
            if (pins.contains("attempted") || journal.hasAnyAttempt())
                throw new AssertionError("Attempt already exists");
            ContextPreviewGate.Preview preview =
                    mainValue(
                            inst,
                            () -> {
                                requireIdle(ui);
                                if (!source.readSource()
                                                .contentEquals(
                                                        ((TextBox) field(ui, "source")).getText())
                                        || (int) field(ui, "editorRevision") != 0
                                        || field(ui, "signedArtifact") != null
                                        || field(ui, "selectedCrashReport") != null
                                        || field(ui, "selectedScreenshot") != null)
                                    throw new AssertionError("Visible context changed");
                                ((TextBox) field(ui, "modelRequest")).setText(REQUEST);
                                return ((ContextPreviewGate) field(ui, "contextGate"))
                                        .previewChat(pins.getString("chat", ""), REQUEST, 0);
                            });
            String sha = sha256(preview.payload().getBytes(StandardCharsets.UTF_8));
            if ("send".equals(phase) && !sha.equals(approvedHash))
                throw new AssertionError("Approved context changed");
            onMain(inst, () -> reveal(button(ui, Labels.GENERATE)));
            inst.waitForIdleSync();
            onMain(inst, () -> visibleClick(button(ui, Labels.GENERATE)));
            waitNode(inst, target, "android.widget.Button", "Send & generate");
            if (!hasPayload(inst.getUiAutomation().getRootInActiveWindow(), preview.payload()))
                throw new AssertionError("Displayed payload differs");
            if ("preview".equals(phase)) {
                click(inst, target, "Cancel");
                requireSnapshot(source, 0, pins.getString("sourceSha256", ""));
                if (journal.hasAnyAttempt()) throw new AssertionError("Preview created attempt");
                save(pins.edit().putString("previewSha256", sha));
                return "Provider cancellation preview PASS "
                        + new JSONObject()
                                .put("contextSha256", sha)
                                .put(
                                        "contextBytes",
                                        preview.payload().getBytes(StandardCharsets.UTF_8).length)
                                .put("previewCancelled", true)
                                .put("modelRequestMade", false)
                        + "\n";
            }
            stage = "SEND_ONCE";
            save(pins.edit().putBoolean("attempted", true));
            click(inst, target, "Send & generate");
            Object token = mainValue(inst, () -> field(ui, "activeStop"));
            if (token == null) throw new AssertionError("No production run token");
            String runId = (String) tokenField(token, "runId");
            if (runId == null || !runId.matches("run_[0-9a-f]{32}"))
                throw new AssertionError("No production run identity");
            save(pins.edit().putString("run", runId));
            ModelClient.Cancellation cancellation =
                    (ModelClient.Cancellation) tokenField(token, "model");
            onMain(inst, () -> reveal(button(ui, Labels.STOP)));
            stage = "OBSERVE_REAL_RESPONSE_WAIT";
            Thread network = awaitNetworkWait(cancellation);
            HttpURLConnection connection = connection(cancellation);
            if (connection == null || !ModelClient.ENDPOINT.equals(connection.getURL().toString()))
                throw new AssertionError("Unexpected production endpoint");
            long[] clock = new long[4];
            JSONArray stack = new JSONArray();
            StackTraceElement[][] finalStack = new StackTraceElement[1][];
            String transportClass = connection.getClass().getName();
            stage = "VISIBLE_STOP_AT_NETWORK_WAIT";
            onMain(
                    inst,
                    () -> {
                        if (field(ui, "activeStop") != token
                                || connection(cancellation) != connection
                                || (boolean) tokenField(token, "requested")
                                || (boolean) cancellationField(cancellation, "cancelled"))
                            throw new AssertionError("Live call changed before Stop");
                        stage = "VERIFY_MODEL_WAIT_STATE";
                        RunJournal.Snapshot planning = journal.load(runId);
                        if (planning.state != RunJournal.State.EDITING
                                || planning.sourceRevision != 0
                                || source.revision() != 0)
                            throw new AssertionError("Provider already completed");
                        stage = "VERIFY_RESPONSE_READ_BEFORE_STOP";
                        Control stop = button(ui, Labels.STOP);
                        StackTraceElement[] observed = network.getStackTrace();
                        if (!responseWait(observed))
                            throw new AssertionError("No real response read at Stop entry");
                        for (StackTraceElement frame : observed)
                            stack.put(frame.getClassName() + "." + frame.getMethodName());
                        if (field(ui, "activeStop") != token
                                || connection(cancellation) != connection
                                || (boolean) tokenField(token, "requested")
                                || (boolean) cancellationField(cancellation, "cancelled"))
                            throw new AssertionError("Live call changed before final observation");
                        clock[0] = SystemClock.elapsedRealtimeNanos();
                        finalStack[0] = network.getStackTrace();
                        clock[1] = SystemClock.elapsedRealtimeNanos();
                        if (!responseWait(finalStack[0]))
                            throw new AssertionError("No response read immediately before Stop");
                        stage = "ACTUAL_VISIBLE_STOP";
                        clock[2] = SystemClock.elapsedRealtimeNanos();
                        visibleClick(stop);
                        clock[3] = SystemClock.elapsedRealtimeNanos();
                    });
            JSONArray lastStack = new JSONArray();
            for (StackTraceElement frame : finalStack[0])
                lastStack.put(frame.getClassName() + "." + frame.getMethodName());
            JSONObject observation =
                    new JSONObject()
                            .put("run", runId)
                            .put("endpointHost", "openrouter.ai")
                            .put("transportClass", transportClass)
                            .put("networkThreadId", network.getId())
                            .put("observedStack", stack)
                            .put("finalPreStopStack", lastStack)
                            .put("maximumObservationToStopGapNanos", MAX_OBSERVATION_TO_STOP_NANOS)
                            .put("observationStartNanos", clock[0])
                            .put("observationEndNanos", clock[1])
                            .put("stopStartNanos", clock[2])
                            .put("stopEndNanos", clock[3])
                            .put("observationToStopGapNanos", clock[2] - clock[1]);
            android.os.Bundle marker = new android.os.Bundle();
            marker.putString(
                    "stream",
                    "Provider cancellation actual Stop observation " + observation + "\n");
            inst.sendStatus(0, marker);
            save(pins.edit().putString("observation", observation.toString()));
            if (clock[2] - clock[1] > MAX_OBSERVATION_TO_STOP_NANOS)
                throw new AssertionError("Response observation too old; no retry");
            if (!(boolean) tokenField(token, "requested")
                    || !(boolean) cancellationField(cancellation, "cancelled"))
                throw new AssertionError("Actual Stop did not request cancellation");
            stage = "ORIGINAL_EXECUTOR_SETTLE";
            settle(inst, ui);
            ((ExecutorService) field(ui, "executor")).submit(() -> {}).get(10, TimeUnit.SECONDS);
            inst.waitForIdleSync();
            RunJournal.Snapshot cancelled = journal.load(runId);
            if (cancelled.state != RunJournal.State.CANCELLED
                    || cancelled.sourceRevision != 0
                    || cancelled.repairs != 0
                    || cancelled.versionCode != null)
                throw new AssertionError("Live cancellation outcome differs");
            if (connection(cancellation) != null || field(ui, "activeModelCall") != null)
                throw new AssertionError("Original network call still active");
            save(pins.edit().putInt("sequence", cancelled.sequence));
            requireStopped(inst, ui, project, source, journal, pins);
            save(pins.edit().putBoolean("stopped", true));
            return "Provider cancellation live Stop PASS "
                    + observation
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("contextSha256", sha)
                            .put("sourceRevision", 0)
                            .put("snapshotSha256", pins.getString("sourceSha256", ""))
                            .put("journalState", cancelled.state.name())
                            .put("journalSequence", cancelled.sequence)
                            .put(
                                    "providerReportedTokens",
                                    cancelled.reportedTokens == null
                                            ? JSONObject.NULL
                                            : cancelled.reportedTokens)
                            .put("providerReportedCost", JSONObject.NULL)
                            .put("model", ModelClient.MODEL)
                            .put("freeOnly", true)
                            .put("localCancellationOnly", true)
                    + "\n";
        } finally {
            settle(inst, ui);
            onMain(inst, ui::finish);
        }
    }

    private static Thread awaitNetworkWait(ModelClient.Cancellation cancellation) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 35000;
        while (SystemClock.elapsedRealtime() < deadline) {
            HttpURLConnection current = connection(cancellation);
            if (current != null && ModelClient.ENDPOINT.equals(current.getURL().toString())) {
                Thread found = null;
                for (Map.Entry<Thread, StackTraceElement[]> item :
                        Thread.getAllStackTraces().entrySet()) {
                    if (responseWait(item.getValue())) {
                        if (found != null) throw new AssertionError("Ambiguous model threads");
                        found = item.getKey();
                    }
                }
                if (found != null) return found;
            }
            Thread.sleep(2);
        }
        throw new AssertionError("Real response read not observed; no automatic retry");
    }

    private static boolean responseWait(StackTraceElement[] frames) {
        boolean model = false, response = false, read = false, handshake = false;
        for (StackTraceElement frame : frames) {
            String owner = frame.getClassName(), method = frame.getMethodName();
            model |= owner.equals(ModelClient.class.getName()) && method.equals("completeWithKey");
            response |=
                    (owner.startsWith("com.android.okhttp.") && method.equals("getResponseCode"))
                            || (owner.equals("dev.smallwright.agent.ReplyStream")
                                    && method.equals("nextLine"));
            read |=
                    (owner.contains("NativeCrypto") && method.equals("SSL_read"))
                            || (owner.contains("SocketInputStream")
                                    && method.toLowerCase(java.util.Locale.ROOT).contains("read"))
                            || (owner.contains("ConscryptEngineSocket") && method.equals("read"));
            handshake |= method.contains("do_handshake") || method.equals("startHandshake");
        }
        return model && response && read && !handshake;
    }

    private static Object tokenField(Object token, String name) throws Exception {
        Field f = token.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(token);
    }

    private static Object cancellationField(ModelClient.Cancellation cancellation, String name)
            throws Exception {
        if (!java.util.List.of("connection", "cancelled").contains(name))
            throw new AssertionError("Unapproved cancellation metadata");
        Field f = ModelClient.Cancellation.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(cancellation);
    }

    private static HttpURLConnection connection(ModelClient.Cancellation cancellation)
            throws Exception {
        return (HttpURLConnection) cancellationField(cancellation, "connection");
    }

    private static void settle(Instrumentation inst, Activity ui) throws Exception {
        while (!awaitIdle(inst, ui, 30)) {
            android.os.Bundle pending = new android.os.Bundle();
            pending.putString(
                    "stream",
                    "Provider cancellation original app work pending; wait same handle, no retry\n");
            inst.sendStatus(0, pending);
        }
    }

    private static void requireStopped(
            Instrumentation inst,
            Activity ui,
            ProjectIdentity project,
            ProjectSourceStore source,
            RunJournal journal,
            SharedPreferences pins)
            throws Exception {
        requireSnapshot(source, 0, pins.getString("sourceSha256", ""));
        RunJournal.Snapshot run = journal.load(pins.getString("run", ""));
        if (run.state != RunJournal.State.CANCELLED
                || run.sequence != pins.getInt("sequence", -1)
                || run.sourceRevision != 0
                || run.buildId != null
                || run.versionCode != null
                || journal.maximumVersionCode() != 0)
            throw new AssertionError("Cancelled saved run changed");
        if (new File(project.directory(), "artifacts/verified-artifact.properties").exists())
            throw new AssertionError("Cancelled provider produced receipt");
        File artifacts = new File(project.directory(), "artifacts");
        String[] files = artifacts.list();
        if (files != null && files.length != 0)
            throw new AssertionError("Cancelled provider produced artifacts");
        onMain(
                inst,
                () -> {
                    requireIdle(ui);
                    requireOwn(ui, pins);
                    if (field(ui, "signedArtifact") != null
                            || field(ui, "pendingRecovery") != null
                            || (int) field(ui, "editorRevision") != 0
                            || !source.readSource()
                                    .contentEquals(((TextBox) field(ui, "source")).getText()))
                        throw new AssertionError("Cancelled source/readiness differs");
                    Control install = Ui.button(ui, Labels.INSTALL);
                    Control export = Ui.button(ui, Labels.EXPORT_APK);
                    if (install == null
                            || install.isEnabled()
                            || export == null
                            || export.isEnabled())
                        throw new AssertionError("Cancelled install/export available");
                    button(ui, Labels.BUILD_SOURCE);
                });
    }

    private static SignedArtifactReceipt.Record verifyReady(
            Instrumentation inst,
            Context target,
            Activity ui,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences pins)
            throws Exception {
        requireSnapshot(source, 1, pins.getString("sourceSha256", ""));
        SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
        verifyIdentity(inst, target, ui, project, receipt);
        if (!receipt.sha256.equals(pins.getString("apkSha256", ""))
                || !receipt.signerFingerprint.equals(pins.getString("signerSha256", "")))
            throw new AssertionError("Recovery artifact changed");
        return receipt;
    }

    private static void verifyIdentity(
            Instrumentation inst,
            Context target,
            Activity ui,
            ProjectIdentity project,
            SignedArtifactReceipt.Record receipt)
            throws Exception {
        receipt.artifact.verifyUnchanged();
        ArtifactValidator.validateUnsigned(target, receipt.apk, 1, project.packageName());
        if (receipt.versionCode != 1
                || !new ApkVerifier.Builder(receipt.apk).build().verify().isVerified()
                || !receipt.signerFingerprint.equals(
                        new ProjectSigner(project).verifyExisting(target, receipt.apk)))
            throw new AssertionError("Recovery identity/signature differs");
        awaitReady(inst, ui, receipt.sha256);
    }

    private static JSONObject artifactResult(
            ProjectIdentity project, SignedArtifactReceipt.Record receipt) throws Exception {
        return new JSONObject()
                .put("project", project.id())
                .put("package", project.packageName())
                .put("sourceRevision", 1)
                .put("version", 1)
                .put("apkSha256", receipt.sha256)
                .put("signerSha256", receipt.signerFingerprint)
                .put("apkPath", receipt.apk.getAbsolutePath())
                .put("apkBytes", receipt.apk.length());
    }

    private static String restoreSelection(Context target, SharedPreferences pins)
            throws Exception {
        if (!pins.getBoolean("recovered", false)
                || !pins.getString("project", "")
                        .equals(
                                target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                                        .getString("active-project", null)))
            throw new AssertionError("Selection/recovery changed");
        String previous = pins.getString("previousProject", null);
        if (previous != null)
            new ProjectRegistry(new File(target.getFilesDir(), "projects"), target.getFilesDir())
                    .get(previous);
        save(
                target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                        .edit()
                        .putString("active-project", previous));
        return "Provider cancellation selection restored PASS; project/source/receipt/data retained\n";
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Pins not committed");
    }
}
