package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.PowerManager;
import android.os.SystemClock;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fixed counter_03 evaluation with normal-UI baseline build and preserved data; no credential reads
 * or developer edits.
 */
final class A33Counter03Probe {
    private static final String RUN = "a33_counter03_20261012_01";
    private static final String PREFS = "a33-suite-counter-" + RUN;
    private static final String TITLE = "Alpha A33 counter_03 initial 20261012";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation in, String phase, boolean freeApproved, String approvedHash)
            throws Exception {
        stage = "GUARD";
        if (!java.util.List.of(
                                "prepare",
                                "baselineBuild",
                                "pinBaseline",
                                "preview",
                                "send",
                                "ready",
                                "failed",
                                "restoreSelection",
                                "verifySelection")
                        .contains(phase)
                || !"cohere/north-mini-code:free".equals(ModelClient.MODEL))
            throw new AssertionError("Fixed suite policy");
        JSONObject contract;
        try (java.io.InputStream stream =
                in.getContext().getAssets().open("alpha/counter_03.json")) {
            contract = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (!"counter_03".equals(contract.getString("id"))
                || !"supported".equals(contract.getString("kind")))
            throw new AssertionError("Fixed request asset");
        String request = contract.getString("request");
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("reserved")) throw new AssertionError("One-shot project reserved");
            save(pins.edit().putBoolean("reserved", true));
        } else if (!pins.contains("project")) throw new AssertionError("Owned case missing");
        if ("send".equals(phase)
                && (!freeApproved
                        || pins.getBoolean("attempted", false)
                        || approvedHash == null
                        || !approvedHash.matches("[0-9a-f]{64}")
                        || !approvedHash.equals(pins.getString("contextSha256", ""))))
            throw new AssertionError("Exact approval required");
        if ("restoreSelection".equals(phase)) {
            if (!pins.contains("outcome")
                    || !pins.getString("project", "")
                            .equals(
                                    target.getSharedPreferences(
                                                    "MainActivity", Context.MODE_PRIVATE)
                                            .getString("active-project", "")))
                throw new AssertionError("Original settled selection required");
            new ProjectRegistry(new File(target.getFilesDir(), "projects"), target.getFilesDir())
                    .get(pins.getString("previousProject", ""));
            save(
                    target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                            .edit()
                            .putString("active-project", pins.getString("previousProject", "")));
            return marker(
                    phase,
                    new JSONObject().put("restored", true).put("additionalModelRequest", false));
        }
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean dialog = false;
        try {
            stage = "OPEN";
            ControllerUi.awaitProjectBinding(in, ui);
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            if ("verifySelection".equals(phase)) {
                ProjectIdentity previous =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat chat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                if (!previous.id().equals(pins.getString("previousProject", ""))
                        || !chat.id.equals(pins.getString("previousChat", "")))
                    throw new AssertionError("Prior project/chat not restored");
                return marker(
                        phase,
                        new JSONObject()
                                .put("restored", true)
                                .put("additionalModelRequest", false));
            }
            if ("prepare".equals(phase)) {
                stage = "CREATE_UNTOUCHED_PROJECT";
                ProjectIdentity previous =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat previousChat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                save(
                        pins.edit()
                                .putString("previousProject", previous.id())
                                .putString("previousChat", previousChat.id));
                CompilerMeasurementProbe.createProject(in, ui, TITLE);
                ProjectIdentity project =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat chat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!TITLE.equals(project.title())
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot()
                        || new RunJournal(target, project.id()).hasAnyAttempt()
                        || !new ChatStore(
                                        new File(target.getFilesDir(), "chats"),
                                        target.getFilesDir())
                                .listMessages(project.id(), chat.id)
                                .isEmpty()) throw new AssertionError("Untouched baseline required");
                String hash = snapshotHash(source.readRecoverySources(0));
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString("title", TITLE)
                                .putString("chat", chat.id)
                                .putInt("initialRevision", 0)
                                .putString("initialSnapshotSha256", hash)
                                .putString(
                                        "requestSha256",
                                        sha256(request.getBytes(StandardCharsets.UTF_8))));
                return marker(
                        phase,
                        new JSONObject()
                                .put("runId", RUN)
                                .put("promptId", "counter_03")
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("sourceRevision", 0)
                                .put("initialSnapshotSha256", hash)
                                .put("requestSha256", pins.getString("requestSha256", ""))
                                .put("developerSourceEdits", false)
                                .put("modelRequestMade", false));
            }
            stage = "OWN_PROJECT";
            ProjectIdentity project = mainValue(in, () -> requireOwn(ui, pins));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("baselineBuild".equals(phase)) {
                stage = "NORMAL_BASELINE_R0_GUARD";
                if (!pins.getBoolean("reserved", false)
                        || pins.getBoolean("baselineBuildAttempted", false)
                        || pins.getBoolean("baselineBuildComplete", false)
                        || pins.getBoolean("attempted", false)
                        || pins.contains("baselineBuildId")
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot()
                        || !snapshotHash(source.readRecoverySources(0))
                                .equals(pins.getString("initialSnapshotSha256", ""))
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError("Untouched R0 and unused baseline build required");
                ChatStore.Chat selected =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                if (!pins.getString("chat", "").equals(selected.id)
                        || !source.readSource()
                                .contentEquals(
                                        mainValue(
                                                in,
                                                () -> ((TextBox) field(ui, "source")).getText()))
                        || mainValue(in, () -> (int) field(ui, "editorRevision")) != 0)
                    throw new AssertionError("Prepared source R0/chat changed");
                onMain(in, () -> requireIdle(ui));
                save(pins.edit().putBoolean("baselineBuildAttempted", true));
                ActiveWorkerStopProbe.showTools(in, ui);
                Control build = mainValue(in, () -> button(ui, Labels.BUILD_SOURCE));
                onMain(in, () -> reveal(build));
                in.waitForIdleSync();
                PowerManager power = target.getSystemService(PowerManager.class);
                Object storageBefore = safeControllerStorage(target);
                int thermalBefore = power.getCurrentThermalStatus();
                int workerUid =
                        target.getPackageManager()
                                .getApplicationInfo("dev.smallwright.worker", 0)
                                .uid;
                long clickedAt = SystemClock.elapsedRealtimeNanos();
                stage = "NORMAL_BASELINE_BUILD_CLICKED";
                onMain(in, () -> visibleClick(build));
                in.waitForIdleSync();
                long nextPending = SystemClock.elapsedRealtime() + 30000;
                while (mainValue(in, () -> busy(ui))) {
                    if (SystemClock.elapsedRealtime() >= nextPending) {
                        android.os.Bundle pending = new android.os.Bundle();
                        pending.putString(
                                "stream",
                                "A33 counter03 normal baseline Build pending; no Stop/retry\n");
                        in.sendStatus(0, pending);
                        nextPending += 30000;
                    }
                    Thread.sleep(25);
                }
                in.waitForIdleSync();
                stage = "VERIFY_NORMAL_BASELINE_R1_READY";
                if (source.revision() != 1
                        || !snapshotHash(source.readRecoverySources(1))
                                .equals(pins.getString("initialSnapshotSha256", ""))
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError(
                            "Normal baseline Build changed source or made attempt");
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
                receipt.artifact.verifyUnchanged();
                ArtifactValidator.validateUnsigned(target, receipt.apk, 1, project.packageName());
                ChatStore chats =
                        new ChatStore(
                                new File(target.getFilesDir(), "chats"), target.getFilesDir());
                ManualBuildOriginStore.Origin origin = source.verifiedManualBuildOrigin(1, chats);
                if (origin == null
                        || !origin.projectId.equals(project.id())
                        || !origin.chatId.equals(pins.getString("chat", ""))
                        || origin.sourceRevision != 1
                        || origin.versionCode != 1
                        || !origin.buildId.matches("manual_[0-9a-f]{32}")
                        || receipt.versionCode != 1
                        || !receipt.sha256.equals(origin.artifactSha256)
                        || receipt.apk.length() == 0
                        || !receipt.signerFingerprint.matches("[0-9a-f]{64}")
                        || !receipt.signerFingerprint.equals(
                                new ProjectSigner(project).verifyExisting(target, receipt.apk)))
                    throw new AssertionError("Normal baseline receipt/origin invalid");
                awaitReady(in, ui, receipt.sha256);
                long readyAt = SystemClock.elapsedRealtimeNanos();
                Object storageAfter = safeControllerStorage(target);
                Object memoryAtReady = safeOwnMemory();
                int thermalAfter = power.getCurrentThermalStatus();
                save(
                        pins.edit()
                                .putBoolean("baselineBuildComplete", true)
                                .putString("baselineBuildId", origin.buildId));
                return marker(
                        phase,
                        new JSONObject()
                                .put("promptId", "counter_03")
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("chat", origin.chatId)
                                .put("sourceRevision", 1)
                                .put("sourceSha256", pins.getString("initialSnapshotSha256", ""))
                                .put("baselineBuildId", origin.buildId)
                                .put("version", receipt.versionCode)
                                .put("apkPath", receipt.apk.getAbsolutePath())
                                .put("apkSha256", receipt.sha256)
                                .put("apkBytes", receipt.apk.length())
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("manifestPolicyVerified", true)
                                .put("verifiedReadyObserved", true)
                                .put("controllerPid", android.os.Process.myPid())
                                .put("controllerUid", android.os.Process.myUid())
                                .put("workerUid", workerUid)
                                .put("workerProcessIdentity", JSONObject.NULL)
                                .put("workerProcessIdentityStatus", "unknown_no_existing_probe")
                                .put(
                                        "workerColdWarmClassification",
                                        "unknown_without_worker_process_start_identity")
                                .put("controllerStorageBefore", storageBefore)
                                .put("controllerStorageAfter", storageAfter)
                                .put(
                                        "combinedControllerWorkerStorageStatus",
                                        "unknown_worker_snapshot_not_available_from_existing_probe")
                                .put("controllerMemoryAtReady", memoryAtReady)
                                .put("workerReportedRss", JSONObject.NULL)
                                .put("largestWaitedChildReportedRss", JSONObject.NULL)
                                .put(
                                        "reportedRssEnvelopeStatus",
                                        "unknown_worker_and_child_counters_not_available_from_existing_probe")
                                .put("thermalStatusBefore", thermalBefore)
                                .put("thermalStatusAfter", thermalAfter)
                                .put("firstBuildLimitMs", 10000)
                                .put("warmBuildLimitMs", 5000)
                                .put("reportedRssEnvelopeLimitKiB", 1048576)
                                .put("combinedControllerWorkerStorageLimitBytes", 268435456)
                                .put(
                                        "buildClickToReadyMs",
                                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                readyAt - clickedAt))
                                .put("modelRequestMade", false));
            }
            if ("pinBaseline".equals(phase)) {
                stage = "PIN_BASELINE_BUILD";
                if (!pins.getBoolean("baselineBuildAttempted", false)
                        || !pins.getBoolean("baselineBuildComplete", false)
                        || pins.contains("baselineApkSha256")
                        || pins.getBoolean("attempted", false))
                    throw new AssertionError(
                            "Completed baseline Build and unused request required");
                if (source.revision() != 1
                        || !snapshotHash(source.readRecoverySources(1))
                                .equals(pins.getString("initialSnapshotSha256", "")))
                    throw new AssertionError("Normal Build did not save unchanged source as R1");
                if (new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError("Model attempt exists before fixed request");
                ChatStore chats =
                        new ChatStore(
                                new File(target.getFilesDir(), "chats"), target.getFilesDir());
                ManualBuildOriginStore.Origin origin = source.verifiedManualBuildOrigin(1, chats);
                if (origin == null
                        || !origin.projectId.equals(project.id())
                        || !origin.chatId.equals(pins.getString("chat", ""))
                        || !origin.buildId.equals(pins.getString("baselineBuildId", ""))
                        || origin.sourceRevision != 1
                        || origin.versionCode != 1
                        || !origin.buildId.matches("manual_[0-9a-f]{32}"))
                    throw new AssertionError("Normal manual Build origin is not pinned R1/chat");
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
                if (receipt.versionCode != 1
                        || !receipt.sha256.equals(origin.artifactSha256)
                        || !receipt.apk.isFile()
                        || receipt.apk.length() == 0
                        || !receipt.signerFingerprint.matches("[0-9a-f]{64}"))
                    throw new AssertionError("Baseline artifact receipt invalid");
                receipt.artifact.verifyUnchanged();
                ArtifactValidator.validateUnsigned(target, receipt.apk, 1, project.packageName());
                String installedPath =
                        target.getPackageManager()
                                .getApplicationInfo(project.packageName(), 0)
                                .sourceDir;
                String installedHash = sha256(Files.readAllBytes(new File(installedPath).toPath()));
                if (!receipt.sha256.equals(installedHash))
                    throw new AssertionError("Installed baseline bytes differ from receipt");
                PackageInfo installed =
                        target.getPackageManager()
                                .getPackageInfo(
                                        project.packageName(),
                                        PackageManager.GET_SIGNING_CERTIFICATES);
                if (installed.getLongVersionCode() != 1
                        || installed.signingInfo == null
                        || installed.signingInfo.hasMultipleSigners()
                        || installed.signingInfo.getApkContentsSigners().length != 1
                        || !receipt.signerFingerprint.equals(
                                sha256(
                                        installed.signingInfo.getApkContentsSigners()[0]
                                                .toByteArray())))
                    throw new AssertionError("Installed baseline version or signer mismatch");
                save(
                        pins.edit()
                                .putString("baselineBuildId", origin.buildId)
                                .putString("baselineApkSha256", receipt.sha256)
                                .putString("baselineSignerSha256", receipt.signerFingerprint)
                                .putString("baselinePath", receipt.apk.getAbsolutePath())
                                .putLong("baselineApkBytes", receipt.artifact.length())
                                .putInt("baselineVersion", receipt.versionCode)
                                .putInt("baselineRevision", 1));
                return marker(
                        phase,
                        new JSONObject()
                                .put("promptId", "counter_03")
                                .put("baselineBuildId", origin.buildId)
                                .put("sourceRevision", 1)
                                .put("version", 1)
                                .put("package", project.packageName())
                                .put("artifactSha256", receipt.sha256)
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("installedBytesVerified", true)
                                .put("manifestPolicyVerified", true)
                                .put("nativePreseedReopenEvidenceMustBeRecordedSeparately", true)
                                .put("modelRequestMade", false));
            }
            if ("ready".equals(phase) || "failed".equals(phase)) {
                if (!pins.contains("outcome")) throw new AssertionError("No settled outcome");
                JSONObject result = new JSONObject(pins.getString("outcome", ""));
                if ("ready".equals(phase) != "verified".equals(result.getString("compileResult")))
                    throw new AssertionError("Outcome does not match requested phase");
                if (!pins.contains("baselineBuildId"))
                    throw new AssertionError("Baseline receipt was not pinned");
                requirePinnedBaseline(target, project, source, pins, false);
                requireSnapshot(
                        source,
                        result.getInt("sourceRevisionAfter"),
                        result.getString("finalSnapshotSha256"));
                RunJournal.Snapshot run =
                        new RunJournal(target, project.id())
                                .load(result.getString("productionRun"));
                if (!run.state.name().equals(result.getString("journalState"))
                        || run.sequence != result.getInt("journalSequence"))
                    throw new AssertionError("Saved result changed");
                if ("verified".equals(result.getString("compileResult"))) {
                    SignedArtifactReceipt.Record receipt =
                            verifiedRecord(target, project, source.revision());
                    if (result.getInt("sourceRevisionBefore") != 1
                            || result.getInt("sourceRevisionAfter") <= 1
                            || !receipt.sha256.equals(result.getString("apkSha256"))
                            || !receipt.signerFingerprint.equals(result.getString("signerSha256"))
                            || !receipt.signerFingerprint.equals(
                                    pins.getString("baselineSignerSha256", ""))
                            || receipt.versionCode != result.getInt("version")
                            || receipt.versionCode <= pins.getInt("baselineVersion", -1)
                            || !project.packageName().equals(pins.getString("package", ""))
                            || !project.packageName().equals(result.optString("package", "")))
                        throw new AssertionError("Ready identity changed");
                    awaitReady(in, ui, receipt.sha256);
                } else if (mainValue(in, () -> field(ui, "signedArtifact")) != null)
                    throw new AssertionError("Failure gained artifact");
                return marker(phase, result);
            }
            stage = "EXACT_BASELINE";
            if (!pins.contains("baselineBuildId") || pins.getBoolean("attempted", false))
                throw new AssertionError("Pinned baseline and unused request required");
            requirePinnedBaseline(target, project, source, pins, true);
            requireSnapshot(source, 1, pins.getString("initialSnapshotSha256", ""));
            if (source.revision() != 1)
                throw new AssertionError("Expected unchanged authored source at R1");
            ContextPreviewGate.Preview preview =
                    mainValue(
                            in,
                            () -> {
                                requireIdle(ui);
                                requireOwn(ui, pins);
                                if (!source.readSource()
                                                .contentEquals(
                                                        ((TextBox) field(ui, "source")).getText())
                                        || (int) field(ui, "editorRevision") != 1)
                                    throw new AssertionError("Source UI changed");
                                ((TextBox) field(ui, "modelRequest")).setText(request);
                                return ((ContextPreviewGate) field(ui, "contextGate"))
                                        .previewChat(pins.getString("chat", ""), request, 1);
                            });
            String hash = sha256(preview.payload().getBytes(StandardCharsets.UTF_8));
            if ("send".equals(phase) && !hash.equals(approvedHash))
                throw new AssertionError("Approved context changed");
            onMain(in, () -> reveal(button(ui, Labels.GENERATE)));
            in.waitForIdleSync();
            onMain(in, () -> visibleClick(button(ui, Labels.GENERATE)));
            waitNode(in, target, "android.widget.Button", "Send & generate");
            dialog = true;
            if (!hasPayload(in.getUiAutomation().getRootInActiveWindow(), preview.payload()))
                throw new AssertionError("Approval UI payload changed");
            if ("preview".equals(phase)) {
                click(in, target, "Cancel");
                dialog = false;
                in.waitForIdleSync();
                requireSnapshot(source, 1, pins.getString("initialSnapshotSha256", ""));
                save(pins.edit().putString("contextSha256", hash));
                return marker(
                        phase,
                        new JSONObject()
                                .put("contextSha256", hash)
                                .put(
                                        "contextBytes",
                                        preview.payload().getBytes(StandardCharsets.UTF_8).length)
                                .put("model", ModelClient.MODEL)
                                .put("freeOnly", true)
                                .put("previewCancelled", true)
                                .put("modelRequestMade", false));
            }
            stage = "SEND_ONCE";
            JSONObject beforeStorage = dev.smallwright.testing.OwnPackageStorage.read(target);
            long started = SystemClock.elapsedRealtimeNanos();
            save(pins.edit().putBoolean("attempted", true));
            click(in, target, "Send & generate");
            dialog = false;
            long lastPreBuild = 0,
                    firstBuildObserved = 0,
                    bracketStart = 0,
                    maximumPollGap = 0,
                    priorQuery = started;
            String productionRun = null, priorState = null;
            JSONArray states = new JSONArray();
            long nextPending = SystemClock.elapsedRealtime() + 30000;
            do {
                long queryStarted = SystemClock.elapsedRealtimeNanos();
                maximumPollGap = Math.max(maximumPollGap, queryStarted - priorQuery);
                priorQuery = queryStarted;
                if (productionRun == null) productionRun = onlyRun(target, project.id());
                if (productionRun != null) {
                    RunJournal.Snapshot run =
                            new RunJournal(target, project.id()).load(productionRun);
                    if (run.state == RunJournal.State.BUILDING && firstBuildObserved == 0) {
                        firstBuildObserved = queryStarted;
                        bracketStart = lastPreBuild;
                    } else if (firstBuildObserved == 0) lastPreBuild = queryStarted;
                    if (!run.state.name().equals(priorState)) {
                        priorState = run.state.name();
                        if (states.length() >= 32)
                            throw new AssertionError("Bounded state observations exceeded");
                        states.put(
                                new JSONObject()
                                        .put("state", priorState)
                                        .put("queryStartedNanos", queryStarted)
                                        .put(
                                                "queryEndedNanos",
                                                SystemClock.elapsedRealtimeNanos()));
                    }
                }
                if (!mainValue(in, () -> busy(ui))) break;
                if (SystemClock.elapsedRealtime() >= nextPending) {
                    android.os.Bundle pending = new android.os.Bundle();
                    pending.putString("stream", "A33 counter03 request pending; no retry/kill\n");
                    in.sendStatus(0, pending);
                    nextPending += 30000;
                }
                Thread.sleep(25);
            } while (true);
            stage = "VERIFY_SETTLED_OUTCOME";
            if (productionRun == null) productionRun = onlyRun(target, project.id());
            if (productionRun == null) throw new AssertionError("Production attempt missing");
            RunJournal.Snapshot run = new RunJournal(target, project.id()).load(productionRun);
            if (run.isActive() && run.state != RunJournal.State.REPAIR_NEEDED)
                throw new AssertionError("Original run not settled");
            JSONObject result =
                    new JSONObject()
                            .put("runId", RUN)
                            .put("promptId", "counter_03")
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("productionRun", productionRun)
                            .put("sourceRevisionBefore", 1)
                            .put("baselineBuildId", pins.getString("baselineBuildId", ""))
                            .put("baselineRevision", pins.getInt("baselineRevision", -1))
                            .put("baselineVersion", pins.getInt("baselineVersion", -1))
                            .put("baselineApkSha256", pins.getString("baselineApkSha256", ""))
                            .put("sourceRevisionAfter", source.revision())
                            .put(
                                    "initialSnapshotSha256",
                                    pins.getString("initialSnapshotSha256", ""))
                            .put(
                                    "finalSnapshotSha256",
                                    snapshotHash(source.readRecoverySources(source.revision())))
                            .put("model", ModelClient.MODEL)
                            .put("freeOnly", true)
                            .put("contextSha256", hash)
                            .put("requestSha256", pins.getString("requestSha256", ""))
                            .put("journalState", run.state.name())
                            .put("journalSequence", run.sequence)
                            .put("repairs", run.repairs)
                            .put("repairRequestsSentByThisProbe", 0)
                            .put(
                                    "repairDecision",
                                    run.state == RunJournal.State.REPAIR_NEEDED
                                            ? "pending_next_vertical_slice"
                                            : "not_needed_or_terminal")
                            .put(
                                    "providerReportedTokens",
                                    run.reportedTokens == null
                                            ? JSONObject.NULL
                                            : run.reportedTokens)
                            .put("providerReportedCost", JSONObject.NULL)
                            .put(
                                    "workerUid",
                                    target.getPackageManager()
                                            .getApplicationInfo("dev.smallwright.worker", 0)
                                            .uid)
                            .put("controllerStorageBefore", beforeStorage)
                            .put(
                                    "controllerStorageAfter",
                                    dev.smallwright.testing.OwnPackageStorage.read(target))
                            .put("stateObservations", states)
                            .put("maximumStatePollGapNanos", maximumPollGap)
                            .put("developerSourceEdits", false);
            String compile =
                    run.state == RunJournal.State.READY_TO_TEST
                            ? "verified"
                            : run.state == RunJournal.State.REPAIR_NEEDED
                                    ? "compile_error"
                                    : run.state == RunJournal.State.CANCELLED
                                            ? "cancelled"
                                            : "failed";
            result.put("compileResult", compile);
            if ("verified".equals(compile)) {
                SignedArtifactReceipt.Record receipt =
                        verifiedRecord(target, project, source.revision());
                receipt.artifact.verifyUnchanged();
                ArtifactValidator.validateUnsigned(
                        target, receipt.apk, receipt.versionCode, project.packageName());
                awaitReady(in, ui, receipt.sha256);
                onMain(in, () -> reveal(button(ui, Labels.INSTALL)));
                in.waitForIdleSync();
                onMain(
                        in,
                        () -> CompilerMeasurementProbe.requireVisible(button(ui, Labels.INSTALL)));
                long ready = SystemClock.elapsedRealtimeNanos();
                result.put("version", receipt.versionCode)
                        .put("apkPath", receipt.apk.getAbsolutePath())
                        .put("apkSha256", receipt.sha256)
                        .put("apkBytes", receipt.apk.length())
                        .put("signerSha256", receipt.signerFingerprint)
                        .put(
                                "buildBracketStartNanos",
                                bracketStart > 0 ? bracketStart : JSONObject.NULL)
                        .put(
                                "firstBuildingObservedNanos",
                                firstBuildObserved > 0 ? firstBuildObserved : JSONObject.NULL)
                        .put("visibleVerifiedReadyNanos", ready)
                        .put(
                                "buildToReadyUpperBoundMs",
                                bracketStart > 0
                                        ? java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                ready - bracketStart)
                                        : JSONObject.NULL)
                        .put(
                                "buildMeasurementScope",
                                "last pre-BUILDING query start to verified visible readiness; conservative bracket may include unsampled model tail/setup; not exact compiler latency");
                android.content.pm.PackageInfo info =
                        target.getPackageManager()
                                .getPackageArchiveInfo(
                                        receipt.apk.getAbsolutePath(),
                                        android.content.pm.PackageManager.GET_PERMISSIONS);
                if (info == null) throw new AssertionError("Archive permissions unavailable");
                JSONArray permissions = new JSONArray();
                if (info.requestedPermissions != null)
                    for (String permission : info.requestedPermissions) permissions.put(permission);
                result.put("permissions", permissions)
                        .put("signatureVerified", true)
                        .put("installResult", "not_attempted")
                        .put("functionalResult", "untested");
            } else {
                if (mainValue(in, () -> field(ui, "signedArtifact")) != null)
                    throw new AssertionError("Failed generation gained readiness");
                String status =
                        mainValue(in, () -> ((TextBox) field(ui, "status")).getText().toString());
                String category =
                        "compile_error".equals(compile)
                                ? "java_compile"
                                : status.contains("Model request timed out")
                                        ? "model_timeout"
                                        : status.contains("OpenRouter authentication failed")
                                                ? "model_authentication"
                                                : status.contains("OpenRouter rate limit")
                                                        ? "model_rate_limit"
                                                        : status.contains("Invalid model response")
                                                                ? "proposal_policy"
                                                                : "generation_failed";
                result.put("outcomeCategory", category);
            }
            result.put(
                    "observedSendToSettledMs",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                            SystemClock.elapsedRealtimeNanos() - started));
            result.put("controllerReportedMemoryAtOutcome", safeOwnMemory())
                    .put("controllerMemoryReadNanos", SystemClock.elapsedRealtimeNanos());
            save(pins.edit().putString("outcome", result.toString()));
            return marker(phase, result);
        } finally {
            if (dialog) click(in, target, "Cancel");
            while (!awaitIdle(in, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString(
                        "stream", "A33 counter03 request pending before finish; no retry/kill\n");
                in.sendStatus(0, pending);
            }
            onMain(in, ui::finish);
        }
    }

    private static JSONObject ownMemory() throws Exception {
        String value =
                new String(
                        Files.readAllBytes(new File("/proc/self/status").toPath()),
                        StandardCharsets.US_ASCII);
        JSONObject result = new JSONObject();
        for (String field : java.util.List.of("VmHWM", "VmRSS")) {
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("(?m)^" + field + ":\\s+(\\d+) kB$")
                            .matcher(value);
            if (!matcher.find()) throw new AssertionError("Own memory counter absent");
            result.put(field + "KiB", Long.parseLong(matcher.group(1)));
        }
        return result;
    }

    private static Object safeOwnMemory() {
        try {
            return ownMemory();
        } catch (Exception | AssertionError unavailable) {
            return JSONObject.NULL;
        }
    }

    private static Object safeControllerStorage(Context target) {
        try {
            return dev.smallwright.testing.OwnPackageStorage.read(target);
        } catch (Exception unavailable) {
            return JSONObject.NULL;
        }
    }

    private static String onlyRun(Context target, String project) throws Exception {
        File path = new File(target.getNoBackupFilesDir(), "run-journal.sqlite");
        if (!path.isFile()) return null;
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                path.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor ids =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=? LIMIT 2",
                                new String[] {project})) {
            if (!ids.moveToFirst()) return null;
            String id = ids.getString(0);
            if (ids.moveToNext() || !id.matches("run_[0-9a-f]{32}"))
                throw new AssertionError("Initial production run ambiguity");
            return id;
        }
    }

    private static void requirePinnedBaseline(
            Context target,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences pins,
            boolean requireCurrentRevisionOne)
            throws Exception {
        ChatStore chats =
                new ChatStore(new File(target.getFilesDir(), "chats"), target.getFilesDir());
        ManualBuildOriginStore.Origin origin =
                requireCurrentRevisionOne
                        ? source.verifiedManualBuildOrigin(1, chats)
                        : new ManualBuildOriginStore(project.directory(), project.id()).read(1);
        File baselineApk;
        String baselineSha;
        String baselineSigner;
        long baselineBytes;
        if (requireCurrentRevisionOne) {
            SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 1);
            baselineApk = receipt.apk;
            baselineSha = receipt.sha256;
            baselineSigner = receipt.signerFingerprint;
            baselineBytes = receipt.artifact.length();
        } else {
            baselineApk = pinnedBaselineFile(project, pins);
            VerifiedArtifact artifact = VerifiedArtifact.capture(baselineApk);
            artifact.verifyUnchanged();
            baselineSha = artifact.sha256();
            baselineBytes = artifact.length();
            ArtifactValidator.validateUnsigned(target, baselineApk, 1, project.packageName());
            baselineSigner = new ProjectSigner(project).verifyExisting(target, baselineApk);
        }
        if (pins.getInt("baselineVersion", -1) != 1
                || pins.getInt("baselineRevision", -1) != 1
                || !pins.getString("baselineApkSha256", "").matches("[0-9a-f]{64}")
                || !pins.getString("baselineSignerSha256", "").matches("[0-9a-f]{64}")
                || !pins.getString("baselineBuildId", "").matches("manual_[0-9a-f]{32}")
                || origin == null
                || !origin.buildId.equals(pins.getString("baselineBuildId", ""))
                || !origin.chatId.equals(pins.getString("chat", ""))
                || origin.sourceRevision != 1
                || origin.versionCode != 1
                || !baselineSha.equals(origin.artifactSha256)
                || !baselineSha.equals(pins.getString("baselineApkSha256", ""))
                || !baselineSigner.equals(pins.getString("baselineSignerSha256", ""))
                || baselineBytes != pins.getLong("baselineApkBytes", -1)
                || !baselineApk.getAbsolutePath().equals(pins.getString("baselinePath", "")))
            throw new AssertionError("Pinned source or baseline identity changed");
        if (requireCurrentRevisionOne
                && (source.revision() != 1
                        || !snapshotHash(source.readRecoverySources(1))
                                .equals(pins.getString("initialSnapshotSha256", ""))))
            throw new AssertionError("Expected exact authored bytes at source R1");
        String installedPath =
                target.getPackageManager().getApplicationInfo(project.packageName(), 0).sourceDir;
        PackageInfo installed =
                target.getPackageManager()
                        .getPackageInfo(
                                project.packageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (!baselineSha.equals(sha256(Files.readAllBytes(new File(installedPath).toPath())))
                || installed.getLongVersionCode() != 1
                || installed.signingInfo == null
                || installed.signingInfo.hasMultipleSigners()
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !baselineSigner.equals(
                        sha256(installed.signingInfo.getApkContentsSigners()[0].toByteArray())))
            throw new AssertionError("Installed baseline package changed");
    }

    private static File pinnedBaselineFile(ProjectIdentity project, SharedPreferences pins)
            throws Exception {
        if (!project.id().matches("app[0-9a-f]{32}")
                || !project.packageName().equals("dev.smallwright.generated." + project.id()))
            throw new AssertionError("Expected the fixed case's fresh application project");
        File projectDirectory = project.directory();
        File artifacts = new File(projectDirectory, "artifacts");
        File apk = new File(artifacts, "app-v1.apk");
        java.nio.file.Path projectPath = projectDirectory.toPath().toAbsolutePath().normalize();
        java.nio.file.Path artifactsPath = artifacts.toPath().toAbsolutePath().normalize();
        java.nio.file.Path apkPath = apk.toPath().toAbsolutePath().normalize();
        if (!apk.getAbsolutePath().equals(pins.getString("baselinePath", ""))
                || !apkPath.startsWith(artifactsPath)
                || java.nio.file.Files.isSymbolicLink(projectPath)
                || java.nio.file.Files.isSymbolicLink(artifactsPath)
                || java.nio.file.Files.isSymbolicLink(apkPath)
                || !java.nio.file.Files.isRegularFile(
                        apkPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !artifactsPath.toRealPath().startsWith(projectPath.toRealPath())
                || !apkPath.toRealPath().startsWith(artifactsPath.toRealPath()))
            throw new AssertionError("Pinned baseline path is missing or unsafe");
        for (java.nio.file.Path current = artifactsPath.getParent();
                current != null && current.startsWith(projectPath);
                current = current.getParent()) {
            if (java.nio.file.Files.isSymbolicLink(current))
                throw new AssertionError("Pinned baseline parent path is a link");
        }
        return apk;
    }

    private static String marker(String phase, JSONObject value) {
        return "A33 suite counter03 " + phase + " " + value + "\n";
    }

    private static void save(SharedPreferences.Editor value) {
        if (!value.commit()) throw new AssertionError("Pin persistence failed");
    }
}
