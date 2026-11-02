package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One fixed alpha attempt through the real UI. Never reads credentials or installs an APK. */
final class PhoneAlphaCounterProbe {
    private static final int LEGACY_MAX_REPAIRS = 3;

    static final String RUN_ID = "s23_counter01_20261002_02";
    private static final String PREFS = "phone-alpha-counter-" + RUN_ID;
    private static final String FEEDBACK_RUN_ID = RUN_ID + "_feedback1";
    private static final String FEEDBACK_PREFS = PREFS + "-feedback1";
    private static final String FEEDBACK2_RUN_ID = RUN_ID + "_feedback2";
    private static final String FEEDBACK2_PREFS = PREFS + "-feedback2";
    private static final String FEEDBACK =
            "The installed app has two Counter labels. Increment changes only the first; Decrement does not lower that value and overwrites the saved count. Use one shared counter and one Counter label. Place Increment and Decrement beside each other in a horizontal row. Decrement must subtract exactly one, including from zero to minus one. Both buttons must save the same count and restore it after closing the app. Preserve existing saved data and crash-report behavior.";
    private static final String FEEDBACK2 =
            "The updated app still shows two Counter labels. Starting at 1, Decrement changes the first label to 0 but leaves the second at 1; both show 0 only after relaunch. The shared count, negative subtraction and horizontal buttons now work. Remove the redundant Counter TextView so the screen contains exactly one Counter label. Both Increment and Decrement must update that same label immediately, save the same count and restore it on relaunch. Preserve the horizontal button row, existing saved data and crash-report behavior.";
    // Exact counter_01 request from benchmarks/prompts.jsonl; no extra instructions.
    private static final String REQUEST =
            "Add a Decrement button beside Increment. It should subtract one and persist the count after closing the app.";

    static final class GuardFailure extends AssertionError {
        GuardFailure() {
            super("alpha approval or attempt guard refused");
        }
    }

    private static SharedPreferences feedbackPins(
            Context target, SharedPreferences first, boolean initialize, int number) {
        if (number < 1 || number > 2) throw new GuardFailure();
        if (!"app492b70d4a41d4f558c116070a6083775".equals(first.getString("project", ""))
                || !"39d7c43fced54c958ed26a3092ff54a8".equals(first.getString("chat", ""))
                || !"verified".equals(first.getString("compileResult", ""))
                || !first.getBoolean("settled", false)
                || first.getInt("finalRevision", -1) != 1
                || !"6723ec46278cc816fdddb63c4af422f31271d29f8c6adef160d1826fd8b13be7"
                        .equals(first.getString("finalSourceSha256", ""))) throw new GuardFailure();
        SharedPreferences prior =
                number == 1
                        ? first
                        : target.getSharedPreferences(FEEDBACK_PREFS, Context.MODE_PRIVATE);
        if (number == 2) {
            for (String key : new String[] {"project", "package", "title", "chat"})
                if (!prior.getString(key, "").equals(first.getString(key, "")))
                    throw new GuardFailure();
            if (!prior.getBoolean("feedback", false)
                    || !prior.getBoolean("settled", false)
                    || !first.getString("productionRun", "")
                            .equals(prior.getString("previousProductionRun", ""))
                    || !"verified".equals(prior.getString("compileResult", ""))
                    || prior.getInt("finalRevision", -1) != 2
                    || prior.getInt("version", -1) != 2
                    || !"c7b42bdc50c7342d1750c032d42d497ee0ad81cb8a46e106f32a02ce754d930a"
                            .equals(prior.getString("finalSourceSha256", ""))
                    || !"9120f964d1b4e4bdbf0e4bd56ccced7971900bd0567187e790840022dae18290"
                            .equals(prior.getString("artifactSha256", ""))
                    || !"a1e8aca58fe6f360707f3036bd552b4a93625cd8d0c0908c22d4ccd79f704cb7"
                            .equals(prior.getString("signerSha256", ""))) throw new GuardFailure();
            RunJournal.Snapshot initial =
                    new RunJournal(target, first.getString("project", ""))
                            .load(first.getString("productionRun", ""));
            if (initial.state != RunJournal.State.READY_TO_TEST
                    || initial.sourceRevision != 1
                    || initial.runId.equals(prior.getString("productionRun", "")))
                throw new GuardFailure();
        }
        RunJournal.Snapshot previous =
                new RunJournal(target, first.getString("project", ""))
                        .load(prior.getString("productionRun", ""));
        if (previous.state != RunJournal.State.READY_TO_TEST
                || previous.sourceRevision != number
                || previous.versionCode == null
                || previous.versionCode != prior.getInt("version", -1)) throw new GuardFailure();
        SharedPreferences record =
                target.getSharedPreferences(
                        number == 1 ? FEEDBACK_PREFS : FEEDBACK2_PREFS, Context.MODE_PRIVATE);
        if (record.contains("attempted")) throw new GuardFailure();
        if (!record.contains("project")) {
            if (!initialize) throw new GuardFailure();
            SharedPreferences.Editor prepared =
                    record.edit()
                            .putBoolean("feedback", true)
                            .putInt("feedbackNumber", number)
                            .putString("project", first.getString("project", ""))
                            .putString("package", first.getString("package", ""))
                            .putString("title", first.getString("title", ""))
                            .putString("chat", first.getString("chat", ""))
                            .putString("previousProductionRun", previous.runId)
                            .putInt("previousSequence", previous.sequence)
                            .putInt("preparedRevision", number)
                            .putString("preparedSha256", prior.getString("finalSourceSha256", ""))
                            .putString(
                                    "previousArtifactSha256", prior.getString("artifactSha256", ""))
                            .putString("previousSignerSha256", prior.getString("signerSha256", ""))
                            .putInt("previousVersion", prior.getInt("version", -1));
            if (number == 2)
                prepared.putString("earlierProductionRun", first.getString("productionRun", ""));
            save(prepared);
        }
        for (String key : new String[] {"project", "package", "title", "chat"})
            if (!record.getString(key, "").equals(first.getString(key, "")))
                throw new GuardFailure();
        if (!record.getBoolean("feedback", false)
                || record.getInt("feedbackNumber", 1) != number
                || !previous.runId.equals(record.getString("previousProductionRun", ""))
                || previous.sequence != record.getInt("previousSequence", -1)
                || record.getInt("preparedRevision", -1) != number
                || !record.getString("preparedSha256", "")
                        .equals(prior.getString("finalSourceSha256", ""))
                || !record.getString("previousArtifactSha256", "")
                        .equals(prior.getString("artifactSha256", ""))
                || !record.getString("previousSignerSha256", "")
                        .equals(prior.getString("signerSha256", ""))
                || record.getInt("previousVersion", -1) != prior.getInt("version", -1)
                || number == 2
                        && !record.getString("earlierProductionRun", "")
                                .equals(first.getString("productionRun", "")))
            throw new GuardFailure();
        return record;
    }

    private static void requireFeedbackOrigin(
            Activity activity, Context target, ProjectSourceStore source, SharedPreferences pins)
            throws Exception {
        ProjectIdentity project = requireOwn(activity, pins);
        requireSnapshot(
                source, pins.getInt("preparedRevision", -1), pins.getString("preparedSha256", ""));
        RunJournal.Snapshot previous =
                new RunJournal(target, project.id())
                        .load(pins.getString("previousProductionRun", ""));
        SignedArtifactReceipt.Record artifact = verifiedRecord(target, project, source.revision());
        if (previous.state != RunJournal.State.READY_TO_TEST
                || previous.sequence != pins.getInt("previousSequence", -1)
                || previous.sourceRevision != source.revision()
                || previous.versionCode == null
                || previous.versionCode != artifact.versionCode
                || !previous.runId.equals(
                        ownProductionRun(
                                target, project, pins.getString("earlierProductionRun", null)))
                || artifact.versionCode != pins.getInt("previousVersion", -1)
                || !artifact.sha256.equals(pins.getString("previousArtifactSha256", ""))
                || !artifact.signerFingerprint.equals(pins.getString("previousSignerSha256", ""))
                || field(activity, "selectedCrashReport") != null
                || field(activity, "selectedScreenshot") != null
                || !feedbackObservation(pins)
                        .contentEquals(((TextBox) field(activity, "feedback")).getText()))
            throw new GuardFailure();
        android.content.pm.PackageInfo installed =
                target.getPackageManager()
                        .getPackageInfo(
                                project.packageName(),
                                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
        if (installed.getLongVersionCode() != artifact.versionCode
                || installed.signingInfo == null
                || installed.signingInfo.getApkContentsSigners().length != 1
                || !sha256(installed.signingInfo.getApkContentsSigners()[0].toByteArray())
                        .equals(artifact.signerFingerprint)) throw new GuardFailure();
    }

    private static ContextPreviewGate.Preview preview(
            Activity activity, SharedPreferences pins, boolean repair) throws Exception {
        ContextPreviewGate gate = (ContextPreviewGate) field(activity, "contextGate");
        int revision = pins.getInt("preparedRevision", -1);
        if (repair) throw new GuardFailure();
        return gate.previewChat(
                pins.getString("chat", ""),
                pins.getBoolean("feedback", false)
                        ? FeedbackRequest.compose(feedbackObservation(pins))
                        : REQUEST,
                revision);
    }

    private static void requireInput(
            ProjectSourceStore source, SharedPreferences pins, boolean existingSource)
            throws Exception {
        if (existingSource)
            requireSnapshot(
                    source,
                    pins.getInt("preparedRevision", -1),
                    pins.getString("preparedSha256", ""));
        else requirePristine(source, pins);
    }

    private static void recordOutcome(
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
        int revision = source.revision();
        String hash = snapshotHash(source.readRecoverySources(revision));
        if (!pins.contains("productionRun")) {
            String id =
                    ownProductionRun(
                            target,
                            project,
                            pins.getString("previousProductionRun", null),
                            pins.getString("earlierProductionRun", null));
            if (id != null) save(pins.edit().putString("productionRun", id));
        }
        RunJournal.Snapshot run =
                pins.contains("productionRun")
                        ? new RunJournal(target, project.id())
                                .load(pins.getString("productionRun", ""))
                        : null;
        String result = settled ? "failed" : "unknown";
        String category = timeout ? "deadline_stop_requested" : "generation_failed";
        boolean started = run != null && run.sequence > pins.getInt("preparedSequence", -1);
        if (run != null && !started) category = "repair_did_not_start";
        else if (run != null && run.state == RunJournal.State.REPAIR_NEEDED) {
            result = "compile_error";
            category = "java_compile";
        } else if (run != null && run.state == RunJournal.State.CANCELLED) {
            result = "cancelled";
            category = "cancelled";
        } else if (settled) {
            // Only known public status categories are inspected; no status/body is emitted.
            String status =
                    mainValue(
                            inst, () -> ((TextBox) field(activity, "status")).getText().toString());
            for (String line : status.split("\n")) {
                if (started
                        && line.equals(
                                "Java compilation still failed after "
                                        + LEGACY_MAX_REPAIRS
                                        + " repairs. Source remains saved. No APK is ready.")) {
                    result = "compile_error";
                    category = "java_compile_cap";
                    break;
                } else if (line.equals(
                                "Model proposal could not be applied to the current source revision. No new APK is ready.")
                        || line.startsWith(
                                "Generation stopped at model response: Invalid model response")) {
                    result = "policy_rejected";
                    category = "proposal_policy";
                    break;
                }
            }
        }
        SharedPreferences.Editor record =
                pins.edit()
                        .putInt("finalRevision", revision)
                        .putString("finalSourceSha256", hash)
                        .putBoolean("settled", settled);
        if (settled) record.putLong("generationEndElapsedMs", SystemClock.elapsedRealtime());
        else record.remove("generationEndElapsedMs");
        if (run != null && run.reportedTokens != null)
            record.putInt("providerReportedTokens", run.reportedTokens);
        if (settled && run != null && run.state == RunJournal.State.READY_TO_TEST) {
            SignedArtifactReceipt.Record artifact = verifiedRecord(target, project, revision);
            if (pins.getBoolean("feedback", false)
                    && (revision <= pins.getInt("preparedRevision", -1)
                            || artifact.versionCode <= pins.getInt("previousVersion", -1)
                            || !artifact.signerFingerprint.equals(
                                    pins.getString("previousSignerSha256", "")))) {
                result = "failed";
                category = "update_identity";
            } else {
                awaitReady(inst, activity, artifact.sha256);
                result = "verified";
                category = "signed_receipt_verified";
                record.putString("artifactSha256", artifact.sha256)
                        .putString("signerSha256", artifact.signerFingerprint)
                        .putInt("version", artifact.versionCode)
                        .putString("artifactPath", artifact.apk.getAbsolutePath());
            }
        }
        save(record.putString("compileResult", result).putString("outcomeCategory", category));
    }

    private static String ownProductionRun(Context target, ProjectIdentity project) {
        return ownProductionRun(target, project, null);
    }

    private static String ownProductionRun(
            Context target, ProjectIdentity project, String excludedRun) {
        return ownProductionRun(target, project, excludedRun, null);
    }

    private static String ownProductionRun(
            Context target, ProjectIdentity project, String excludedRun, String earlierRun) {
        if (excludedRun != null && !excludedRun.matches("run_[0-9a-f]{32}")
                || earlierRun != null
                        && (!earlierRun.matches("run_[0-9a-f]{32}")
                                || earlierRun.equals(excludedRun))) throw new GuardFailure();
        // The UI chooses its run ID. Exclude only the pinned prior attempts from this
        // project's public IDs, then validate the chain with RunJournal.load. No bodies.
        java.io.File file = new java.io.File(target.getNoBackupFilesDir(), "run-journal.sqlite");
        if (!file.isFile()) return null;
        try (SQLiteDatabase db =
                        SQLiteDatabase.openDatabase(
                                file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor ids =
                        db.rawQuery(
                                "SELECT DISTINCT run_id FROM attempt_events WHERE project_id=?"
                                        + (excludedRun == null ? "" : " AND run_id<>?")
                                        + (earlierRun == null ? "" : " AND run_id<>?")
                                        + " LIMIT 2",
                                earlierRun == null
                                        ? excludedRun == null
                                                ? new String[] {project.id()}
                                                : new String[] {project.id(), excludedRun}
                                        : excludedRun == null
                                                ? new String[] {project.id(), earlierRun}
                                                : new String[] {
                                                    project.id(), excludedRun, earlierRun
                                                })) {
            if (!ids.moveToFirst()) return null;
            String id = ids.getString(0);
            if (ids.moveToNext() || !id.matches("run_[0-9a-f]{32}")) throw new GuardFailure();
            return id;
        }
    }

    private static String summary(SharedPreferences pins) {
        long start = pins.getLong("generationStartElapsedMs", -1),
                end = pins.getLong("generationEndElapsedMs", -1);
        return "Alpha run="
                + runId(pins)
                + " prompt=counter_01 project="
                + pins.getString("project", "")
                + " package="
                + pins.getString("package", "")
                + " chat="
                + pins.getString("chat", "")
                + " repairNumber="
                + pins.getInt("repairNumber", 0)
                + " feedbackNumber="
                + (pins.getBoolean("feedback", false) ? pins.getInt("feedbackNumber", 1) : 0)
                + " origin="
                + (pins.getBoolean("feedback", false)
                        ? "observed_feedback"
                        : pins.getInt("repairNumber", 0) > 0 ? "production_restored" : "initial_ui")
                + " revisionBefore="
                + pins.getInt("preparedRevision", -1)
                + " revisionAfter="
                + pins.getInt("finalRevision", -1)
                + " sourceBeforeSha256="
                + pins.getString("preparedSha256", "")
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
                + (start >= 0 ? Long.toString(start) : "null")
                + " generationEndElapsedMs="
                + (end >= 0 ? Long.toString(end) : "null")
                + " totalGenerationDurationMs="
                + (end >= start && start >= 0 ? Long.toString(end - start) : "null")
                + " phone_build_elapsed_ms=null providerReportedTokensCumulative="
                + (pins.contains("providerReportedTokens")
                        ? pins.getInt("providerReportedTokens", 0)
                        : "null")
                + " providerReportedCostUsd=null settled="
                + pins.getBoolean("settled", false)
                + " workerUid="
                + (pins.contains("workerUid") ? pins.getInt("workerUid", -1) : "null")
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

    private static String base(ProjectIdentity project, int revision, String hash) {
        return base(project, revision, hash, RUN_ID);
    }

    private static String runId(SharedPreferences pins) {
        return pins.getBoolean("feedback", false)
                ? pins.getInt("feedbackNumber", 1) == 2 ? FEEDBACK2_RUN_ID : FEEDBACK_RUN_ID
                : RUN_ID;
    }

    private static String feedbackObservation(SharedPreferences pins) {
        int number = pins.getInt("feedbackNumber", 1);
        if (number < 1 || number > 2) throw new GuardFailure();
        return number == 2 ? FEEDBACK2 : FEEDBACK;
    }

    private static String base(ProjectIdentity project, int revision, String hash, String runId) {
        return "Alpha run="
                + runId
                + " prompt=counter_01 project="
                + project.id()
                + " package="
                + project.packageName()
                + " revision="
                + revision
                + " sourceSha256="
                + hash;
    }

    static ProjectIdentity requireOwn(Activity activity, SharedPreferences pins) throws Exception {
        ProjectIdentity project = (ProjectIdentity) field(activity, "activeProject");
        ChatStore.Chat chat = (ChatStore.Chat) field(activity, "selectedChat");
        if (project == null
                || chat == null
                || !project.id().equals(pins.getString("project", ""))
                || !project.packageName().equals(pins.getString("package", ""))
                || !project.title().equals(pins.getString("title", ""))
                || !chat.id.equals(pins.getString("chat", ""))) throw new GuardFailure();
        return project;
    }

    private static void requirePristine(ProjectSourceStore source, SharedPreferences pins)
            throws Exception {
        requireSnapshot(
                source, pins.getInt("preparedRevision", -1), pins.getString("preparedSha256", ""));
        if (!source.isPristineDefaultSnapshot()) throw new GuardFailure();
    }

    static void requireSnapshot(ProjectSourceStore source, int revision, String hash)
            throws Exception {
        if (revision < 0
                || source.revision() != revision
                || !snapshotHash(source.readRecoverySources(revision)).equals(hash))
            throw new GuardFailure();
    }

    static String snapshotHash(Map<String, byte[]> files) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(files).entrySet()) {
            byte[] path = entry.getKey().getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(path.length).array());
            hash.update(path);
            hash.update(ByteBuffer.allocate(8).putLong(entry.getValue().length).array());
            hash.update(entry.getValue());
        }
        return hex(hash.digest());
    }

    static String sha256(byte[] value) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item));
        return result.toString();
    }

    static SignedArtifactReceipt.Record verifiedRecord(
            Context target, ProjectIdentity project, int revision) throws Exception {
        VerifiedArtifact artifact = SignedArtifactReceipt.restore(target, project, revision);
        SignedArtifactReceipt.Record record =
                SignedArtifactReceipt.read(
                        project.directory(), project.id(), project.packageName(), revision);
        if (artifact == null || !artifact.sha256().equals(record.sha256)) throw new GuardFailure();
        return record;
    }

    static void awaitReady(Instrumentation inst, Activity activity, String hash) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (mainValue(
                    inst,
                    () -> {
                        Control install = Ui.button(activity, Labels.INSTALL);
                        VerifiedArtifact shown =
                                (VerifiedArtifact) field(activity, "signedArtifact");
                        return install.isEnabled() && shown != null && shown.sha256().equals(hash);
                    })) return; // Observe only: never click Install or an installer confirmation.
            Thread.sleep(100);
        }
        throw new GuardFailure();
    }

    static boolean busy(Activity activity) throws Exception {
        return (boolean) field(activity, "projectBindingInProgress")
                || (boolean) field(activity, "generationInProgress")
                || (boolean) field(activity, "buildInProgress")
                || (boolean) field(activity, "exportInProgress")
                || (boolean) field(activity, "recoveryImportInProgress");
    }

    static void requireIdle(Activity activity) throws Exception {
        if (busy(activity)) throw new GuardFailure();
    }

    static boolean awaitIdle(Instrumentation inst, Activity activity, int seconds)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (!mainValue(inst, () -> busy(activity))) return true;
            Thread.sleep(100);
        }
        return false;
    }

    static void stop(Instrumentation inst, Activity activity, SharedPreferences pins)
            throws Exception {
        onMain(inst, () -> requireOwn(activity, pins));
        if (mainValue(inst, () -> Ui.button(activity, Labels.STOP).isEnabled()))
            Ui.tap(inst, activity, Labels.STOP);
    }

    private static ProjectIdentity awaitCreated(
            Instrumentation inst, Activity activity, String title) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            ProjectIdentity project =
                    mainValue(inst, () -> (ProjectIdentity) field(activity, "activeProject"));
            if (project != null && title.equals(project.title())) {
                ControllerUi.awaitProjectBinding(inst, activity);
                if (project.equals(
                        mainValue(inst, () -> (ProjectIdentity) field(activity, "activeProject"))))
                    return project;
            }
            Thread.sleep(25);
        }
        throw new GuardFailure();
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new GuardFailure();
    }

    static Control button(Activity activity, String text) throws Exception {
        Control result = Ui.button(activity, text);
        if (!result.isEnabled()) throw new GuardFailure();
        return result;
    }

    /** Compose lays the screen out itself; the tap helper scrolls the control on screen. */
    static void reveal(Control button) {}

    /** Runs the control's action as its tap would. Use Ui.tap for a tap through the screen. */
    static void visibleClick(Control button) {
        if (!button.isShown() || !Ui.click(button)) throw new GuardFailure();
    }

    static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    interface Action {
        void run() throws Exception;
    }

    interface Value<T> {
        T get() throws Exception;
    }

    static void onMain(Instrumentation inst, Action action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        inst.runOnMainSync(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable error) {
                        failure.set(error);
                    }
                });
        if (failure.get() instanceof Exception error) throw error;
        if (failure.get() instanceof Error error) throw error;
    }

    static <T> T mainValue(Instrumentation inst, Value<T> value) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        onMain(inst, () -> result.set(value.get()));
        return result.get();
    }

    static void click(Instrumentation inst, Context target, String text) throws Exception {
        if (!waitNode(inst, target, "android.widget.Button", text)
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new GuardFailure();
    }

    static AccessibilityNodeInfo waitNode(
            Instrumentation inst, Context target, String type, String text)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
            if (root != null
                    && target.getPackageName().equals(String.valueOf(root.getPackageName()))) {
                AccessibilityNodeInfo found = find(root, type, text);
                if (found != null) return found;
            }
            Thread.sleep(100);
        }
        throw new GuardFailure();
    }

    private static AccessibilityNodeInfo find(
            AccessibilityNodeInfo node, String type, String text) {
        AccessibilityNodeInfo here = findHere(node, type, text);
        return here != null || text == null ? here : Ui.find(node, type, text);
    }

    private static AccessibilityNodeInfo findHere(
            AccessibilityNodeInfo node, String type, String text) {
        if (node == null) return null;
        if (type.equals(String.valueOf(node.getClassName()))
                && node.isVisibleToUser()
                && node.isEnabled()
                && (text == null
                        ? "Project name".equals(String.valueOf(node.getHintText()))
                        : text.equalsIgnoreCase(String.valueOf(node.getText())))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findHere(node.getChild(i), type, text);
            if (found != null) return found;
        }
        return null;
    }

    static boolean hasPayload(AccessibilityNodeInfo node, String payload) {
        if (node == null) return false;
        if ("android.widget.TextView".equals(String.valueOf(node.getClassName()))
                && String.valueOf(node.getText()).endsWith("\n\n" + payload)) return true;
        for (int i = 0; i < node.getChildCount(); i++)
            if (hasPayload(node.getChild(i), payload)) return true;
        return false;
    }
}
