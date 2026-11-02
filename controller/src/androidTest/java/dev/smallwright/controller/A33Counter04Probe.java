package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.CompilerDiagnostics;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Fixed counter_04 evaluation using a probe-authored test fixture entered through the ordinary
 * editor and saved only by the normal Build action; no credential reads or direct source-store
 * seed.
 */
final class A33Counter04Probe {
    private static final String RUN = "a33_counter04_20261017_01";
    private static final String PREFS = "a33-suite-counter-" + RUN;
    private static final String DIAGNOSTIC_PREFS = PREFS + "-baseline-diagnostic-v1";
    private static final String TITLE = "Alpha A33 counter_04 initial 20261017";
    private static final int MAX_SOURCE_FILES = 7;
    private static final int MAX_SOURCE_FILE_BYTES = 64 * 1024;
    private static final int MAX_SOURCE_TOTAL_BYTES = 252 * 1024;
    private static final String BASELINE_FIXTURE =
            "package $PACKAGE;\n"
                    + "\n"
                    + "import android.app.Activity;\n"
                    + "import android.os.Bundle;\n"
                    + "import android.widget.Button;\n"
                    + "import android.widget.LinearLayout;\n"
                    + "import android.widget.TextView;\n"
                    + "\n"
                    + "public final class MainActivity extends Activity {\n"
                    + "    private int mainCount;\n"
                    + "    private TextView value;\n"
                    + "\n"
                    + "    private void showCount() { value.setText(\"Counter: \" + mainCount); }\n"
                    + "\n"
                    + "    @Override public void onCreate(Bundle state) {\n"
                    + "        super.onCreate(state);\n"
                    + "        mainCount = getPreferences(0).getInt(\"mainCount\", 0);\n"
                    + "        LinearLayout box = new LinearLayout(this);\n"
                    + "        box.setOrientation(LinearLayout.VERTICAL);\n"
                    + "        value = new TextView(this);\n"
                    + "        box.addView(value);\n"
                    + "        Button increment = new Button(this);\n"
                    + "        increment.setText(\"Increment\");\n"
                    + "        increment.setOnClickListener(v -> {\n"
                    + "            mainCount++;\n"
                    + "            getPreferences(0).edit().putInt(\"mainCount\", mainCount).apply();\n"
                    + "            showCount();\n"
                    + "        });\n"
                    + "        box.addView(increment);\n"
                    + "        Button reset = new Button(this);\n"
                    + "        reset.setText(\"Reset\");\n"
                    + "        reset.setOnClickListener(v -> {\n"
                    + "            mainCount = 0;\n"
                    + "            getPreferences(0).edit().putInt(\"mainCount\", mainCount).apply();\n"
                    + "            showCount();\n"
                    + "        });\n"
                    + "        box.addView(reset);\n"
                    + "        setContentView(box);\n"
                    + "        showCount();\n"
                    + "    }\n"
                    + "}\n";
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
                                "diagnoseBaselineBuild",
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
                in.getContext().getAssets().open("alpha/counter_04.json")) {
            contract = new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (!"counter_04".equals(contract.getString("id"))
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
        if ("preview".equals(phase)
                && (pins.getBoolean("attempted", false) || pins.contains("contextSha256")))
            throw new AssertionError("Preview is one-shot and must precede the request");
        if ("restoreSelection".equals(phase)
                && (!pins.contains("outcome")
                        || pins.getBoolean("selectionRestoreAttempted", false)
                        || pins.getBoolean("selectionRestored", false)))
            throw new AssertionError("No eligible one-shot settled selection restore");
        if ("verifySelection".equals(phase)
                && (!pins.getBoolean("selectionRestoreAttempted", false)
                        || !pins.getBoolean("selectionRestored", false)
                        || pins.getBoolean("selectionVerifyAttempted", false)))
            throw new AssertionError("No one-shot completed selection restore to verify");
        if ("send".equals(phase)
                && (!freeApproved
                        || pins.getBoolean("attempted", false)
                        || approvedHash == null
                        || !approvedHash.matches("[0-9a-f]{64}")
                        || !approvedHash.equals(pins.getString("contextSha256", ""))))
            throw new AssertionError("Exact approval required");
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean dialog = false;
        try {
            stage = "OPEN";
            ControllerUi.awaitProjectBinding(in, ui);
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            if ("restoreSelection".equals(phase)) {
                stage = "VERIFY_SETTLED_OUTCOME_BEFORE_RESTORE";
                ProjectIdentity owned =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat ownedChat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                String activeProjectPreference =
                        target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                                .getString("active-project", "");
                if (owned == null
                        || !owned.id().equals(pins.getString("project", ""))
                        || ownedChat == null
                        || !ownedChat.id.equals(pins.getString("chat", ""))
                        || !owned.id().equals(activeProjectPreference))
                    throw new AssertionError("Exact owned project/chat selection required");
                JSONObject outcome = new JSONObject(pins.getString("outcome", ""));
                ProjectSourceStore ownedSource = new ProjectSourceStore(owned);
                requirePinnedBaseline(target, owned, ownedSource, pins, false, false);
                requireSnapshot(
                        ownedSource,
                        outcome.getInt("sourceRevisionAfter"),
                        outcome.getString("finalSnapshotSha256"));
                RunJournal journal = new RunJournal(target, owned.id());
                RunJournal.Snapshot settled = journal.load(outcome.getString("productionRun"));
                if (!owned.packageName().equals(outcome.getString("package"))
                        || !owned.id().equals(outcome.getString("project"))
                        || !RUN.equals(outcome.getString("runId"))
                        || !"counter_04".equals(outcome.getString("promptId"))
                        || !pins.getString("contextSha256", "")
                                .equals(outcome.getString("contextSha256"))
                        || !settled.state.name().equals(outcome.getString("journalState"))
                        || settled.sequence != outcome.getInt("journalSequence")
                        || settled.sourceRevision != outcome.getInt("sourceRevisionAfter"))
                    throw new AssertionError("Settled outcome journal changed");
                requireOutcomeArtifactForRestore(target, owned, outcome);
                if ("verified".equals(outcome.getString("compileResult")))
                    awaitReady(in, ui, outcome.getString("apkSha256"));
                new ProjectRegistry(
                                new File(target.getFilesDir(), "projects"), target.getFilesDir())
                        .get(pins.getString("previousProject", ""));
                save(pins.edit().putBoolean("selectionRestoreAttempted", true));
                stage = "RESTORE_ORIGINAL_SELECTION";
                A33CounterFeedbackProbe.selectProject(
                        in, ui, pins.getString("previousProject", ""));
                A33CounterFeedbackProbe.selectChat(in, ui, pins.getString("previousChat", ""));
                ActiveWorkerStopProbe.awaitWorker(in, ui);
                verifySelectionUiAndPreference(in, ui, target, pins);
                save(pins.edit().putBoolean("selectionRestored", true));
                return marker(
                        phase,
                        new JSONObject()
                                .put("restored", true)
                                .put("additionalModelRequest", false));
            }
            if ("verifySelection".equals(phase)) {
                save(pins.edit().putBoolean("selectionVerifyAttempted", true));
                verifySelectionUiAndPreference(in, ui, target, pins);
                return marker(
                        phase,
                        new JSONObject()
                                .put("restored", true)
                                .put("additionalModelRequest", false));
            }
            if ("prepare".equals(phase)) {
                stage = "CREATE_FRESH_PROJECT";
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
                                .putString("pristineSnapshotSha256", hash)
                                .putString(
                                        "requestSha256",
                                        sha256(request.getBytes(StandardCharsets.UTF_8))));
                return marker(
                        phase,
                        new JSONObject()
                                .put("runId", RUN)
                                .put("promptId", "counter_04")
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("sourceRevision", 0)
                                .put("pristineSnapshotSha256", hash)
                                .put("awaitingIndependentEditorAuthoring", true)
                                .put("requestSha256", pins.getString("requestSha256", ""))
                                .put("developerSourceEdits", false)
                                .put("modelRequestMade", false));
            }
            stage = "OWN_PROJECT";
            ProjectIdentity project = mainValue(in, () -> requireOwn(ui, pins));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("diagnoseBaselineBuild".equals(phase)) {
                stage = "DIAGNOSTIC_BASELINE_GUARD";
                JSONObject diagnostic = diagnoseBaselineBuild(in, target, project, source, pins);
                return marker(phase, diagnostic);
            }
            if ("baselineBuild".equals(phase)) {
                stage = "AUTHORED_BASELINE_R0_GUARD";
                if (!pins.getBoolean("reserved", false)
                        || pins.getBoolean("baselineBuildAttempted", false)
                        || pins.getBoolean("baselineBuildComplete", false)
                        || pins.getBoolean("attempted", false)
                        || pins.contains("baselineBuildId")
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot()
                        || !snapshotHash(source.readRecoverySources(0))
                                .equals(pins.getString("pristineSnapshotSha256", ""))
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError("Fresh R0 and unused baseline build required");
                ChatStore.Chat selected =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                String authored = BASELINE_FIXTURE.replace("$PACKAGE", project.packageName());
                if (!pins.getString("chat", "").equals(selected.id)
                        || mainValue(in, () -> (int) field(ui, "editorRevision")) != 0
                        || !source.isPristineDefaultSnapshot()
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError(
                            "Fresh baseline project required before test authoring");
                mainValue(
                        in,
                        () -> {
                            ((TextBox) field(ui, "source")).setText(authored);
                            return null;
                        });
                String editorText =
                        mainValue(in, () -> ((TextBox) field(ui, "source")).getText().toString());
                if (!authored.equals(editorText)
                        || !authored.contains("package " + project.packageName() + ";")
                        || !authored.contains("\"Increment\"")
                        || !authored.contains("\"Reset\""))
                    throw new AssertionError(
                            "Probe-authored test fixture was not staged in editor");
                String authoredSha = sha256(authored.getBytes(StandardCharsets.UTF_8));
                onMain(in, () -> requireIdle(ui));
                save(
                        pins.edit()
                                .putBoolean("baselineBuildAttempted", true)
                                .putString("authoredSourceSha256", authoredSha));
                ActiveWorkerStopProbe.showTools(in, ui);
                Control build = mainValue(in, () -> button(ui, Labels.BUILD_SOURCE));
                onMain(in, () -> reveal(build));
                in.waitForIdleSync();
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
                                "A33 counter04 normal baseline Build pending; no Stop/retry\n");
                        in.sendStatus(0, pending);
                        nextPending += 30000;
                    }
                    Thread.sleep(25);
                }
                in.waitForIdleSync();
                stage = "VERIFY_AUTHORED_BASELINE_R1_READY";
                if (source.revision() != 1
                        || !authored.equals(source.readSource())
                        || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError(
                            "Normal baseline Build changed authored source or made attempt");
                String authoredSnapshotSha = snapshotHash(source.readRecoverySources(1));
                save(pins.edit().putString("initialSnapshotSha256", authoredSnapshotSha));
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
                save(
                        pins.edit()
                                .putBoolean("baselineBuildComplete", true)
                                .putString("baselineBuildId", origin.buildId));
                return marker(
                        phase,
                        new JSONObject()
                                .put("promptId", "counter_04")
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("chat", origin.chatId)
                                .put("sourceRevision", 1)
                                .put("sourceSha256", authoredSha)
                                .put("snapshotSha256", authoredSnapshotSha)
                                .put("authoredSourceSha256", authoredSha)
                                .put("baselineBuildId", origin.buildId)
                                .put("version", receipt.versionCode)
                                .put("apkPath", receipt.apk.getAbsolutePath())
                                .put("apkSha256", receipt.sha256)
                                .put("apkBytes", receipt.apk.length())
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("manifestPolicyVerified", true)
                                .put("verifiedReadyObserved", true)
                                .put(
                                        "buildClickToReadyMs",
                                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                                readyAt - clickedAt))
                                .put("modelRequestMade", false)
                                .put("testFixtureAuthoredByProbe", true)
                                .put("sourceStoreSeededByProbe", false)
                                .put("testAssisted", true));
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
                                .put("promptId", "counter_04")
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
                requirePinnedBaseline(target, project, source, pins, false, true);
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
            requirePinnedBaseline(target, project, source, pins, true, true);
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
            Object beforeStorage = safeStorage(target);
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
                    pending.putString("stream", "A33 counter04 request pending; no retry/kill\n");
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
                            .put("promptId", "counter_04")
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
                            .put("finalSnapshotSha256", safeCurrentSnapshotHash(source))
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
                            .put("controllerStorageAfter", safeStorage(target))
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
            save(pins.edit().putString("outcome", result.toString()));
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
                        "stream", "A33 counter04 request pending before finish; no retry/kill\n");
                in.sendStatus(0, pending);
            }
            onMain(in, ui::finish);
        }
    }

    private static Object safeCurrentSnapshotHash(ProjectSourceStore source) {
        try {
            return snapshotHash(source.readRecoverySources(source.revision()));
        } catch (Exception unavailableOrChanged) {
            return JSONObject.NULL;
        }
    }

    private static Object safeStorage(Context target) {
        try {
            return dev.smallwright.testing.OwnPackageStorage.read(target);
        } catch (Exception unavailable) {
            return JSONObject.NULL;
        }
    }

    private static Object safeOwnMemory() {
        try {
            return ownMemory();
        } catch (Exception unavailable) {
            return JSONObject.NULL;
        }
    }

    private static void requireOutcomeArtifactForRestore(
            Context target, ProjectIdentity project, JSONObject outcome) throws Exception {
        if ("verified".equals(outcome.getString("compileResult"))) {
            SignedArtifactReceipt.Record receipt =
                    verifiedRecord(target, project, outcome.getInt("sourceRevisionAfter"));
            receipt.artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(
                    target, receipt.apk, receipt.versionCode, project.packageName());
            if (!receipt.sha256.equals(outcome.getString("apkSha256"))
                    || !receipt.signerFingerprint.equals(outcome.getString("signerSha256"))
                    || receipt.versionCode != outcome.getInt("version"))
                throw new AssertionError("Settled generated artifact changed");
            requireInstalledReceipt(target, project, receipt);
        } else {
            requireInstalledV1(target, project, outcome);
        }
    }

    private static void requireInstalledV1(
            Context target, ProjectIdentity project, JSONObject outcome) throws Exception {
        requireInstalledV1Baseline(
                target, project, target.getSharedPreferences(PREFS, Context.MODE_PRIVATE), outcome);
    }

    private static void requireInstalledV1Baseline(
            Context target, ProjectIdentity project, SharedPreferences pins) throws Exception {
        requireInstalledV1Baseline(target, project, pins, null);
    }

    private static void requireInstalledV1Baseline(
            Context target, ProjectIdentity project, SharedPreferences pins, JSONObject outcome)
            throws Exception {
        File apk = pinnedBaselineFile(project, pins);
        VerifiedArtifact baseline = VerifiedArtifact.capture(apk);
        baseline.verifyUnchanged();
        PackageInfo installed =
                target.getPackageManager()
                        .getPackageInfo(
                                project.packageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        String installedPath =
                target.getPackageManager().getApplicationInfo(project.packageName(), 0).sourceDir;
        String signer = new ProjectSigner(project).verifyExisting(target, apk);
        if ((outcome != null && !baseline.sha256().equals(outcome.getString("baselineApkSha256")))
                || !baseline.sha256()
                        .equals(sha256(Files.readAllBytes(new File(installedPath).toPath())))
                || installed.getLongVersionCode() != 1
                || installed.signingInfo == null
                || installed.signingInfo.hasMultipleSigners()
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !signer.equals(
                        target.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .getString("baselineSignerSha256", ""))
                || !signer.equals(
                        sha256(installed.signingInfo.getApkContentsSigners()[0].toByteArray())))
            throw new AssertionError("Failed outcome must retain installed immutable v1 baseline");
    }

    private static void requireInstalledReceipt(
            Context target, ProjectIdentity project, SignedArtifactReceipt.Record receipt)
            throws Exception {
        PackageInfo installed =
                target.getPackageManager()
                        .getPackageInfo(
                                project.packageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        String installedPath =
                target.getPackageManager().getApplicationInfo(project.packageName(), 0).sourceDir;
        if (installed.getLongVersionCode() != receipt.versionCode
                || !receipt.sha256.equals(
                        sha256(Files.readAllBytes(new File(installedPath).toPath())))
                || installed.signingInfo == null
                || installed.signingInfo.hasMultipleSigners()
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !receipt.signerFingerprint.equals(
                        sha256(installed.signingInfo.getApkContentsSigners()[0].toByteArray())))
            throw new AssertionError("Installed settled artifact differs from verified receipt");
    }

    private static void verifySelectionUiAndPreference(
            Instrumentation in, Activity ui, Context target, SharedPreferences pins)
            throws Exception {
        ProjectIdentity selected =
                mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
        ChatStore.Chat selectedChat =
                mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
        String activeProject =
                target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                        .getString("active-project", "");
        if (selected == null
                || selectedChat == null
                || !pins.getString("previousProject", "").equals(selected.id())
                || !pins.getString("previousChat", "").equals(selectedChat.id)
                || !pins.getString("previousProject", "").equals(activeProject))
            throw new AssertionError(
                    "Original project/chat UI and preference selection not restored");
    }

    /** One-shot, unsigned worker replay of the already committed R1 baseline snapshot. */
    private static JSONObject diagnoseBaselineBuild(
            Instrumentation in,
            Context target,
            ProjectIdentity project,
            ProjectSourceStore source,
            SharedPreferences baselinePins)
            throws Exception {
        SharedPreferences diagnosticPins =
                target.getSharedPreferences(DIAGNOSTIC_PREFS, Context.MODE_PRIVATE);
        Map<String, ?> originalBaselinePins = new java.util.HashMap<>(baselinePins.getAll());
        if (diagnosticPins.getBoolean("reserved", false)
                || diagnosticPins.getBoolean("attempted", false)
                || diagnosticPins.contains("result"))
            throw new AssertionError("Baseline diagnostic is one-shot");
        if (!RUN.equals(baselinePins.getString("runId", RUN))
                || !baselinePins.getBoolean("baselineBuildAttempted", false)
                || baselinePins.getBoolean("baselineBuildComplete", false)
                || baselinePins.getBoolean("attempted", false)
                || baselinePins.contains("baselineBuildId")
                || source.revision() != 1
                || new RunJournal(target, project.id()).hasAnyAttempt())
            throw new AssertionError("Failed immutable R1 baseline required");

        String authored = BASELINE_FIXTURE.replace("$PACKAGE", project.packageName());
        String authoredSha = sha256(authored.getBytes(StandardCharsets.UTF_8));
        String pinnedAuthoredSha = baselinePins.getString("authoredSourceSha256", "");
        String pinnedSnapshotSha = baselinePins.getString("initialSnapshotSha256", "");
        if (!authoredSha.matches("[0-9a-f]{64}")
                || !authoredSha.equals(pinnedAuthoredSha)
                || !pinnedSnapshotSha.matches("[0-9a-f]{64}")
                || !authored.equals(source.readSource())
                || !authoredSha.equals(sha256(source.readSource().getBytes(StandardCharsets.UTF_8)))
                || !pinnedSnapshotSha.equals(snapshotHash(source.readRecoverySources(1))))
            throw new AssertionError("Exact failed authored R1 snapshot required");
        requireNoPublishedBaselineArtifact(project);

        save(
                diagnosticPins
                        .edit()
                        .putBoolean("reserved", true)
                        .putString("project", project.id())
                        .putInt("revision", 1)
                        .putString("authoredSourceSha256", authoredSha)
                        .putString("snapshotSha256", pinnedSnapshotSha));

        File scratch =
                Files.createTempDirectory(target.getCacheDir().toPath(), "counter04-r1-diagnostic-")
                        .toFile();
        File snapshot = new File(scratch, "snapshot.zip");
        File unsigned = new File(scratch, "unsigned.apk");
        File report = new File(scratch, "numeric.report");
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> remote = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        boolean bound = false;
        FutureTask<String> call = null;
        JSONObject result = null;
        try {
            stage = "DIAGNOSTIC_BIND_WORKER";
            bound =
                    target.bindService(
                            new Intent()
                                    .setComponent(
                                            new ComponentName(
                                                    "dev.smallwright.worker",
                                                    "dev.smallwright.worker.BuildService")),
                            connection,
                            Context.BIND_AUTO_CREATE);
            if (!bound || !connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Diagnostic worker bind unavailable");
            int workerUid =
                    target.getPackageManager().getApplicationInfo("dev.smallwright.worker", 0).uid;
            if (workerUid == target.getApplicationInfo().uid)
                throw new AssertionError("Diagnostic worker UID is not isolated");
            IBuildService worker = remote.get();
            stage = "DIAGNOSTIC_WORKER_CAPABILITY";
            if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                    .equals(worker.handshake(2)))
                throw new AssertionError("Numeric worker diagnostics unavailable");

            result =
                    new JSONObject()
                            .put("project", project.id())
                            .put("sourceRevision", 1)
                            .put("authoredSourceSha256", authoredSha)
                            .put("snapshotSha256", pinnedSnapshotSha)
                            .put("workerUid", workerUid)
                            .put("modelRequestMade", false)
                            .put("sourceStoreSeededByProbe", false)
                            .put("signedArtifactPublished", false)
                            .put("generatedInstallationAttempted", false);
            stage = "DIAGNOSTIC_BUILD_SNAPSHOT";
            String workerResult;
            long nextPending = SystemClock.elapsedRealtime() + 30000;
            try (ProjectStore.BuildLease lease = source.beginBuild(1, snapshot, 1)) {
                if (lease.revision() != 1) throw new AssertionError("Diagnostic revision changed");
                Files.createFile(unsigned.toPath());
                Files.createFile(report.toPath());
                save(diagnosticPins.edit().putBoolean("attempted", true));
                call =
                        new FutureTask<>(
                                () -> {
                                    try (ParcelFileDescriptor input =
                                                    ParcelFileDescriptor.open(
                                                            snapshot,
                                                            ParcelFileDescriptor.MODE_READ_ONLY);
                                            ParcelFileDescriptor output =
                                                    ParcelFileDescriptor.open(
                                                            unsigned,
                                                            ParcelFileDescriptor.MODE_READ_WRITE
                                                                    | ParcelFileDescriptor
                                                                            .MODE_TRUNCATE);
                                            ParcelFileDescriptor diagnostics =
                                                    ParcelFileDescriptor.open(
                                                            report,
                                                            ParcelFileDescriptor.MODE_READ_WRITE
                                                                    | ParcelFileDescriptor
                                                                            .MODE_TRUNCATE)) {
                                        return worker.buildWithDiagnostics(
                                                input, output, diagnostics);
                                    }
                                });
                new Thread(call, "counter04-r1-diagnostic-worker-call").start();
                workerResult = awaitDiagnostic(in, call, nextPending);
                result.put("workerResultCategory", safeWorkerResultCategory(workerResult));
                result.put("temporaryOutputBytes", unsigned.length());
                stage = "DIAGNOSTIC_NUMERIC_REPORT";
                JSONObject diagnostics =
                        readSafeNumericDiagnostics(
                                report, workerResult, authored, project.packageName());
                result.put("numericDiagnostics", diagnostics);
            }

            stage = "DIAGNOSTIC_VERIFY_UNCHANGED_STATE";
            if (source.revision() != 1
                    || !authored.equals(source.readSource())
                    || !pinnedSnapshotSha.equals(snapshotHash(source.readRecoverySources(1)))
                    || new RunJournal(target, project.id()).hasAnyAttempt())
                throw new AssertionError("Diagnostic replay changed project source or journal");
            requireNoPublishedBaselineArtifact(project);
            result.put("sourceUnchanged", true)
                    .put("snapshotUnchanged", true)
                    .put("journalAttemptCreated", false)
                    .put("receiptPublished", false)
                    .put("modelRequestMade", false)
                    .put("rawWorkerTextPersisted", false);
            return result;
        } finally {
            try {
                if (call != null && !call.isDone()) awaitDiagnostic(in, call, 0);
            } finally {
                try {
                    if (bound) target.unbindService(connection);
                } finally {
                    Files.deleteIfExists(report.toPath());
                    Files.deleteIfExists(unsigned.toPath());
                    Files.deleteIfExists(snapshot.toPath());
                    Files.deleteIfExists(scratch.toPath());
                    if (Files.exists(report.toPath())
                            || Files.exists(unsigned.toPath())
                            || Files.exists(snapshot.toPath())
                            || Files.exists(scratch.toPath()))
                        throw new AssertionError("Diagnostic temporary files remain");
                    if (source.revision() != 1
                            || !authored.equals(source.readSource())
                            || !pinnedSnapshotSha.equals(
                                    snapshotHash(source.readRecoverySources(1))))
                        throw new AssertionError("R1 changed during diagnostic cleanup");
                    if (!originalBaselinePins.equals(baselinePins.getAll()))
                        throw new AssertionError("Original baseline flags changed");
                    requireNoPublishedBaselineArtifact(project);
                    if (result != null) {
                        result.put(
                                "workerResultCategory",
                                result.optString("workerResultCategory", "NO_WORKER_RESULT"));
                        result.put("temporaryFilesCleaned", true)
                                .put("originalBaselinePinsUnchanged", true);
                        save(
                                diagnosticPins
                                        .edit()
                                        .putBoolean("complete", true)
                                        .putString(
                                                "outcomeCategory",
                                                result.getString("workerResultCategory"))
                                        .putString("result", result.toString()));
                    }
                }
            }
        }
    }

    private static String awaitDiagnostic(
            Instrumentation in, FutureTask<String> call, long nextPending) throws Exception {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return call.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException pending) {
                    if (nextPending == 0 || SystemClock.elapsedRealtime() >= nextPending) {
                        android.os.Bundle status = new android.os.Bundle();
                        status.putString(
                                "stream",
                                "A33 counter04 R1 diagnostic pending; no retry or publication\n");
                        in.sendStatus(0, status);
                        nextPending = SystemClock.elapsedRealtime() + 30000;
                    }
                } catch (InterruptedException pending) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static JSONObject readSafeNumericDiagnostics(
            File report, String workerResult, String authored, String packageName)
            throws Exception {
        if (!Files.isRegularFile(report.toPath(), LinkOption.NOFOLLOW_LINKS)
                || Files.size(report.toPath()) > CompilerDiagnostics.MAX_BYTES)
            return new JSONObject().put("status", "invalid_or_oversized");
        byte[] wire;
        try (InputStream input = Files.newInputStream(report.toPath())) {
            byte[] bounded = new byte[CompilerDiagnostics.MAX_BYTES + 1];
            int used = 0;
            while (used < bounded.length) {
                int count = input.read(bounded, used, bounded.length - used);
                if (count < 0) break;
                if (count == 0) continue;
                used += count;
            }
            if (used > CompilerDiagnostics.MAX_BYTES)
                return new JSONObject().put("status", "invalid_or_oversized");
            wire = java.util.Arrays.copyOf(bounded, used);
        }
        if (!"COMPILE_ERROR".equals(workerResult))
            return new JSONObject()
                    .put("status", wire.length == 0 ? "absent" : "unexpected_for_result");
        if (wire.length == 0) return new JSONObject().put("status", "absent");
        try {
            CompilerDiagnostics.Report decoded =
                    CompilerDiagnostics.decode(
                            wire,
                            new java.util.TreeMap<>(
                                    java.util.Map.of(
                                            "java/"
                                                    + packageName.replace('.', '/')
                                                    + "/MainActivity.java",
                                            authored.getBytes(StandardCharsets.UTF_8))));
            JSONArray problems = new JSONArray();
            for (CompilerDiagnostics.Problem problem : decoded.problems)
                problems.put(
                        new JSONObject()
                                .put("id", problem.id)
                                .put("category", problem.category)
                                .put("line", problem.line));
            return new JSONObject()
                    .put("status", "validated")
                    .put("sourceSha256", decoded.sourceSha256)
                    .put("problems", problems)
                    .put("truncated", decoded.truncated);
        } catch (IOException rejected) {
            return new JSONObject()
                    .put("status", "rejected")
                    .put("exceptionCategory", rejected.getClass().getSimpleName());
        }
    }

    private static String safeWorkerResultCategory(String result) {
        if ("OK".equals(result)) return "OK_UNVERIFIED";
        if ("COMPILE_ERROR".equals(result)) return "COMPILE_ERROR";
        if ("SNAPSHOT_TIMEOUT".equals(result)) return "SNAPSHOT_TIMEOUT";
        if ("ERROR: Worker busy".equals(result)) return "WORKER_BUSY";
        if ("ERROR: Packaged AAPT2 timed out".equals(result)) return "AAPT2_TIMEOUT";
        if (result != null && result.startsWith("ERROR: Packaged AAPT2 failed: "))
            return "AAPT2_FAILURE";
        return "OTHER_WORKER_ERROR";
    }

    private static void requireNoPublishedBaselineArtifact(ProjectIdentity project)
            throws IOException {
        Path artifacts = new File(project.directory(), "artifacts").toPath();
        if (Files.isSymbolicLink(artifacts))
            throw new SecurityException("Baseline artifacts path is a link");
        if (!Files.exists(artifacts, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(artifacts, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Baseline artifacts path is not a directory");
        try (java.util.stream.Stream<Path> entries = Files.list(artifacts)) {
            if (entries.findAny().isPresent())
                throw new AssertionError("Baseline artifact directory is not empty");
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
            boolean requireCurrentRevisionOne,
            boolean requireInstalledV1Baseline)
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
        Map<String, byte[]> historical = readHistoricalSnapshot(target, source, 1);
        String mainPath = "java/" + project.packageName().replace('.', '/') + "/MainActivity.java";
        if (!snapshotHash(historical).equals(pins.getString("initialSnapshotSha256", ""))
                || !sha256(historical.get(mainPath))
                        .equals(pins.getString("authoredSourceSha256", ""))
                || !java.security.MessageDigest.isEqual(
                        origin.sourceDigest, SourceSnapshotDigest.compute(historical)))
            throw new AssertionError("Pinned baseline source history/origin digest changed");
        if (requireCurrentRevisionOne
                && (source.revision() != 1
                        || !snapshotHash(source.readRecoverySources(1))
                                .equals(pins.getString("initialSnapshotSha256", ""))))
            throw new AssertionError("Expected exact authored bytes at source R1");
        if (requireInstalledV1Baseline) requireInstalledV1Baseline(target, project, pins);
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

    /**
     * Reads the pinned baseline without asking ProjectStore to move or reinterpret current HEAD.
     */
    private static Map<String, byte[]> readHistoricalSnapshot(
            Context target, ProjectSourceStore source, int revision) throws Exception {
        if (revision != 1) throw new AssertionError("Only the pinned R1 baseline is in scope");
        ProjectIdentity project =
                new ProjectRegistry(
                                new File(target.getFilesDir(), "projects"), target.getFilesDir())
                        .get(source.projectId());
        if (!project.packageName().equals(source.packageName()))
            throw new AssertionError("Historical source project identity changed");
        Path projectPath = project.directory().toPath().toAbsolutePath().normalize();
        Path revisionPath =
                projectPath.resolve("revision-store/revisions").resolve(Integer.toString(revision));
        Path trustedBase = target.getFilesDir().toPath().toAbsolutePath().normalize();
        Path canonicalBase = trustedBase.toRealPath();
        rejectSymlinksWithin(trustedBase, projectPath);
        rejectSymlinksWithin(trustedBase, revisionPath);
        if (!projectPath.startsWith(trustedBase)
                || !projectPath.toRealPath().startsWith(canonicalBase)
                || !Files.isDirectory(revisionPath, LinkOption.NOFOLLOW_LINKS)
                || !revisionPath.toRealPath().startsWith(projectPath.toRealPath()))
            throw new AssertionError("Pinned historical revision directory is unavailable");

        ProjectStore historyStore =
                new ProjectStore(
                        new File(project.directory(), "revision-store"), project.packageName());
        Map<String, byte[]> snapshot = new TreeMap<>();
        long[] total = {0};
        int[] count = {0};
        String packageDirectory = "java/" + project.packageName().replace('.', '/');
        Files.walkFileTree(
                revisionPath,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attrs) throws IOException {
                        rejectSymlinksWithin(trustedBase, directory);
                        if (attrs.isSymbolicLink()
                                || !attrs.isDirectory()
                                || !directory.equals(revisionPath)
                                        && !isAllowedSourceDirectory(
                                                revisionPath
                                                        .relativize(directory)
                                                        .toString()
                                                        .replace('\\', '/'),
                                                packageDirectory))
                            throw new SecurityException(
                                    "Historical source has a foreign directory");
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        rejectSymlinksWithin(trustedBase, file);
                        String relative =
                                revisionPath.relativize(file).toString().replace('\\', '/');
                        if (attrs.isSymbolicLink()
                                || !attrs.isRegularFile()
                                || !isAllowedSourcePath(relative, packageDirectory)
                                || attrs.size() > MAX_SOURCE_FILE_BYTES
                                || ++count[0] > MAX_SOURCE_FILES)
                            throw new SecurityException("Historical source inventory is invalid");
                        total[0] += attrs.size();
                        if (total[0] > MAX_SOURCE_TOTAL_BYTES)
                            throw new SecurityException("Historical source exceeds snapshot limit");
                        byte[] contents = historyStore.readFile(revision, relative);
                        if (contents.length != attrs.size()
                                || contents.length > MAX_SOURCE_FILE_BYTES)
                            throw new SecurityException("Historical source changed while reading");
                        snapshot.put(relative, contents);
                        return FileVisitResult.CONTINUE;
                    }
                });
        if (snapshot.isEmpty()) throw new AssertionError("Pinned historical source is empty");
        return snapshot;
    }

    private static boolean isAllowedSourcePath(String path, String packageDirectory) {
        return path.matches(
                        java.util.regex.Pattern.quote(packageDirectory)
                                + "/[A-Za-z][A-Za-z0-9_]*\\.java")
                || path.matches("res/(layout|values)/[a-z][a-z0-9_]*\\.xml");
    }

    private static boolean isAllowedSourceDirectory(String path, String packageDirectory) {
        if ("res".equals(path)
                || "res/layout".equals(path)
                || "res/values".equals(path)
                || "java".equals(path)) return true;
        return packageDirectory.startsWith(path + "/") || packageDirectory.equals(path);
    }

    private static void rejectSymlinksWithin(Path trustedBase, Path path) throws IOException {
        Path base = trustedBase.toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(base) || Files.isSymbolicLink(base))
            throw new SecurityException("Historical source escaped app-private storage");
        Path current = base;
        for (Path part : base.relativize(absolute)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current))
                throw new SecurityException("Historical source path contains a symlink");
        }
    }

    private static String marker(String phase, JSONObject value) {
        return "A33 suite counter04 " + phase + " " + value + "\n";
    }

    private static void save(SharedPreferences.Editor value) {
        if (!value.commit()) throw new AssertionError("Pin persistence failed");
    }
}
