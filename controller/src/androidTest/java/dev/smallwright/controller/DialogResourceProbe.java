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
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in fresh authored project; normal visible phone Build; never touches frozen suite source. */
final class DialogResourceProbe {
    private static final String PREFS = "dialog-resource-api28-20261012";
    private static final String TRACE_PREFS = "dialog-resource-trace28-20261012";
    private static final String FRESH_PREFS = "dialog-resource-api28-native2-20261012";
    private static final String ORIGINAL_OBSERVATION = "aa9749b8334440728785f381f9733324";
    private static final String GREEN_WORKER_SHA =
            "c1daca5782eb3fb751b0356c7a985f30024420afaaaf5b383ab0113db004f2bb";
    private static final String HARNESS_SIGNER_SHA =
            "19a7407f08bee5ef8be1809749b60970ddc141a22b34423b0d89a7aaa5d4bd25";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    /** New physical GREEN case anchored to the preserved independent host compiler RED. */
    static String runFresh(
            Instrumentation in,
            String phase,
            String observation,
            boolean approved,
            String expected,
            String workerHash)
            throws Exception {
        SharedPreferences original = in.getTargetContext().getSharedPreferences(PREFS, 0);
        Map<String, ?> unchanged = original.getAll();
        try {
            return runFreshOwned(in, phase, observation, approved, expected, workerHash);
        } finally {
            if (!original.getAll().equals(unchanged))
                throw new AssertionError("Original dialog observation pins changed");
        }
    }

    private static String runFreshOwned(
            Instrumentation in,
            String phase,
            String observation,
            boolean approved,
            String expected,
            String workerHash)
            throws Exception {
        stage = "GUARD";
        Context target = in.getTargetContext();
        SharedPreferences pins = target.getSharedPreferences(FRESH_PREFS, 0);
        if (!approved
                || observation == null
                || !observation.matches("[0-9a-f]{32}")
                || ORIGINAL_OBSERVATION.equals(observation)
                || !List.of(
                                "prepare",
                                "inspect",
                                "compile",
                                "ready",
                                "restoreSelection",
                                "verifySelection")
                        .contains(phase)
                || "red".equals(expected)
                || !GREEN_WORKER_SHA.equals(workerHash)
                || target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Approved independent native dialog GREEN case required");
        if ("prepare".equals(phase)) {
            if (pins.contains("reserved"))
                throw new AssertionError("Independent case already reserved");
        } else if (!observation.equals(pins.getString("observation", ""))
                || !pins.contains("project"))
            throw new AssertionError("Exact independent case ownership required");
        if ("compile".equals(phase)
                && (!"green".equals(expected)
                        || pins.getBoolean("greenAttempted", false)
                        || pins.getBoolean("greenVerified", false)))
            throw new AssertionError("Once-only independent normal GREEN required");
        android.content.pm.PackageInfo worker =
                target.getPackageManager()
                        .getPackageInfo(
                                "dev.smallwright.worker", PackageManager.GET_SIGNING_CERTIFICATES);
        if (worker.signingInfo == null
                || worker.signingInfo.hasMultipleSigners()
                || worker.signingInfo.getApkContentsSigners().length != 1
                || !HARNESS_SIGNER_SHA.equals(
                        sha256(worker.signingInfo.getApkContentsSigners()[0].toByteArray()))
                || !GREEN_WORKER_SHA.equals(
                        sha256(
                                Files.readAllBytes(
                                        new File(
                                                        target.getPackageManager()
                                                                .getApplicationInfo(
                                                                        "dev.smallwright.worker", 0)
                                                                .sourceDir)
                                                .toPath()))))
            throw new AssertionError("Reviewed normal worker identity changed");
        if ("restoreSelection".equals(phase)) {
            stage = "RESTORE_SETTLED_SELECTION";
            if (!pins.getBoolean("greenVerified", false)
                    || pins.getBoolean("selectionRestored", false)
                    || !pins.getString("project", "")
                            .equals(
                                    target.getSharedPreferences("MainActivity", 0)
                                            .getString("active-project", "")))
                throw new AssertionError("Settled owned selection required once");
            ProjectRegistry registry =
                    new ProjectRegistry(
                            new File(target.getFilesDir(), "projects"), target.getFilesDir());
            ProjectIdentity owned = registry.get(pins.getString("project", ""));
            requireSnapshot(new ProjectSourceStore(owned), 2, pins.getString("snapshotSha256", ""));
            SignedArtifactReceipt.Record current = verifiedRecord(target, owned, 2);
            if (!current.sha256.equals(pins.getString("apkSha256", ""))
                    || !current.signerFingerprint.equals(pins.getString("signerSha256", "")))
                throw new AssertionError("Settled retained receipt changed");
            registry.get(pins.getString("previousProject", ""));
            save(
                    target.getSharedPreferences("MainActivity", 0)
                            .edit()
                            .putString("active-project", pins.getString("previousProject", "")));
            save(pins.edit().putBoolean("selectionRestored", true));
            return "Dialog native2 restoreSelection {\"restored\":true,\"modelRequestMade\":false}\n";
        }
        if ("prepare".equals(phase))
            save(pins.edit().putBoolean("reserved", true).putString("observation", observation));
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            stage = "FRESH_OPEN";
            ControllerUi.awaitProjectBinding(in, ui);
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            if ("verifySelection".equals(phase)) {
                if (!pins.getBoolean("selectionRestored", false)
                        || pins.getBoolean("selectionVerified", false)
                        || !pins.getString("previousProject", "")
                                .equals(
                                        mainValue(
                                                in,
                                                () ->
                                                        ((ProjectIdentity)
                                                                        field(ui, "activeProject"))
                                                                .id()))
                        || !pins.getString("previousChat", "")
                                .equals(
                                        mainValue(
                                                in,
                                                () ->
                                                        ((ChatStore.Chat) field(ui, "selectedChat"))
                                                                .id)))
                    throw new AssertionError("Previous project/chat restoration required once");
                save(pins.edit().putBoolean("selectionVerified", true));
                return "Dialog native2 verifySelection {\"restored\":true,\"modelRequestMade\":false}\n";
            }
            if ("prepare".equals(phase)) {
                stage = "FRESH_PROJECT_CREATE";
                onMain(in, () -> requireIdle(ui));
                save(
                        pins.edit()
                                .putString(
                                        "previousProject",
                                        mainValue(
                                                in,
                                                () ->
                                                        ((ProjectIdentity)
                                                                        field(ui, "activeProject"))
                                                                .id()))
                                .putString(
                                        "previousChat",
                                        mainValue(
                                                in,
                                                () ->
                                                        ((ChatStore.Chat) field(ui, "selectedChat"))
                                                                .id)));
                String title = "Dialog native2 " + observation;
                CompilerMeasurementProbe.createProject(in, ui, title);
                ProjectIdentity project =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ChatStore.Chat chat =
                        mainValue(in, () -> (ChatStore.Chat) field(ui, "selectedChat"));
                ProjectSourceStore source = new ProjectSourceStore(project);
                ChatStore chats = mainValue(in, () -> (ChatStore) field(ui, "chatStore"));
                if (!project.id().matches("app[0-9a-f]{32}")
                        || !title.equals(project.title())
                        || source.revision() != 0
                        || !source.isPristineDefaultSnapshot()
                        || new RunJournal(target, project.id()).hasAnyAttempt()
                        || !chats.listMessages(project.id(), chat.id).isEmpty())
                    throw new AssertionError("Untouched new authored fixture required");
                try {
                    target.getPackageManager().getPackageInfo(project.packageName(), 0);
                    throw new AssertionError("Fresh generated package already installed");
                } catch (PackageManager.NameNotFoundException absent) {
                    /* No prior app data. */
                }
                Map<String, byte[]> files = fixture(in, project);
                int staged = source.stageRecoverySources(0, files);
                int revision = source.promoteRecoverySources(0, staged);
                if (revision != 1) throw new AssertionError("Fresh authored revision1 required");
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString("title", title)
                                .putString("chat", chat.id)
                                .putInt("revision", 1)
                                .putString("snapshotSha256", snapshotHash(files)));
                // Finish this stale editor without selection restoration. Next process binds R1.
                return "Dialog native2 prepare " + describeFresh(project, source, pins) + "\n";
            }
            stage = "FRESH_OWN_PROJECT";
            ProjectIdentity project =
                    mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if (!project.id().equals(pins.getString("project", ""))
                    || !project.title().equals(pins.getString("title", ""))
                    || !project.packageName().equals(pins.getString("package", ""))
                    || !pins.getString("chat", "")
                            .equals(
                                    mainValue(
                                            in,
                                            () -> ((ChatStore.Chat) field(ui, "selectedChat")).id)))
                throw new AssertionError("Exact owned project/chat selection required");
            requireSnapshot(
                    source, pins.getInt("revision", -1), pins.getString("snapshotSha256", ""));
            if (!snapshotHash(fixture(in, project)).equals(pins.getString("snapshotSha256", "")))
                throw new AssertionError("Authored source bytes changed");
            if ("compile".equals(phase)) {
                stage = "FRESH_NORMAL_BUILD_GUARD";
                if (source.revision() != 1 || new RunJournal(target, project.id()).hasAnyAttempt())
                    throw new AssertionError("Unbuilt exact authored R1 required");
                requireNotReady(in, ui, target, project, 1);
                onMain(
                        in,
                        () -> {
                            requireIdle(ui);
                            if ((int) field(ui, "editorRevision") != 1
                                    || !source.readSource()
                                            .contentEquals(
                                                    ((TextBox) field(ui, "source")).getText()))
                                throw new AssertionError("Fresh bound saved editor required");
                        });
                save(pins.edit().putBoolean("greenAttempted", true));
                stage = "FRESH_NORMAL_BUILD_CLICKED";
                ActiveWorkerStopProbe.showTools(in, ui);
                ActiveWorkerStopProbe.visible(in, ui, Labels.BUILD_SOURCE);
                awaitSettled(in, ui);
                requireSnapshot(source, 2, pins.getString("snapshotSha256", ""));
                save(pins.edit().putInt("revision", 2));
                stage = "FRESH_VERIFIED_PHONE_GREEN";
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 2);
                ChatStore chats = mainValue(in, () -> (ChatStore) field(ui, "chatStore"));
                ManualBuildOriginStore.Origin origin = source.verifiedManualBuildOrigin(2, chats);
                if (origin == null
                        || origin.sourceRevision != 2
                        || origin.versionCode != 1
                        || !origin.chatId.equals(pins.getString("chat", ""))
                        || !origin.artifactSha256.equals(receipt.sha256)
                        || receipt.versionCode != 1)
                    throw new AssertionError("Exact new normal Build receipt/origin required");
                awaitReady(in, ui, receipt.sha256);
                save(
                        pins.edit()
                                .putInt("revision", 2)
                                .putBoolean("greenVerified", true)
                                .putString("apkSha256", receipt.sha256)
                                .putString("signerSha256", receipt.signerFingerprint)
                                .putInt("version", 1)
                                .putString("apkPath", receipt.apk.getAbsolutePath()));
            }
            if ("ready".equals(phase) || "compile".equals(phase)) {
                if (!pins.getBoolean("greenVerified", false))
                    throw new AssertionError("No independent verified GREEN");
                SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 2);
                ManualBuildOriginStore.Origin retained =
                        source.verifiedManualBuildOrigin(
                                2, mainValue(in, () -> (ChatStore) field(ui, "chatStore")));
                if (retained == null
                        || retained.sourceRevision != 2
                        || retained.versionCode != 1
                        || !retained.chatId.equals(pins.getString("chat", ""))
                        || !retained.artifactSha256.equals(receipt.sha256))
                    throw new AssertionError("Fresh retained source/chat origin changed");
                if (!receipt.sha256.equals(pins.getString("apkSha256", ""))
                        || !receipt.signerFingerprint.equals(pins.getString("signerSha256", ""))
                        || receipt.versionCode != pins.getInt("version", -1))
                    throw new AssertionError("Fresh retained receipt changed");
                awaitReady(in, ui, receipt.sha256);
                return "Dialog native2 "
                        + phase
                        + " "
                        + describeFresh(project, source, pins)
                                .put("compileResult", "verified")
                                .put("apkSha256", receipt.sha256)
                                .put("apkPath", receipt.apk.getAbsolutePath())
                                .put("apkBytes", receipt.apk.length())
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("version", receipt.versionCode)
                        + "\n";
            }
            if (!pins.getBoolean("greenVerified", false))
                requireNotReady(in, ui, target, project, source.revision());
            return "Dialog native2 inspect " + describeFresh(project, source, pins) + "\n";
        } finally {
            awaitSettled(in, ui);
            onMain(in, ui::finish);
        }
    }

    private static JSONObject describeFresh(
            ProjectIdentity project, ProjectSourceStore source, SharedPreferences pins)
            throws Exception {
        int revision = source.revision();
        Map<String, byte[]> actual = source.readRecoverySources(revision);
        if (source.revision() != revision
                || !snapshotHash(actual).equals(pins.getString("snapshotSha256", "")))
            throw new AssertionError("Fresh described snapshot changed");
        return describe(project, source, pins)
                .put("sourceRevision", revision)
                .put("snapshotSha256", snapshotHash(actual))
                .put("sourceFileCount", actual.size());
    }

    static String run(
            Instrumentation in,
            String phase,
            String observation,
            boolean approved,
            String expected,
            String workerHash)
            throws Exception {
        stage = "GUARD";
        Context target = in.getTargetContext();
        SharedPreferences pins = target.getSharedPreferences(PREFS, 0);
        if (!approved
                || observation == null
                || !observation.matches("[0-9a-f]{32}")
                || !List.of("prepare", "diagnose", "trace", "inspect", "compile", "ready")
                        .contains(phase)
                || target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Approved owned unlocked dialog fixture required");
        if ("diagnose".equals(phase)) {
            if (!observation.equals(pins.getString("observation", "")))
                throw new AssertionError("Reserved case ownership required");
            return diagnose(target, observation);
        }
        if ("trace".equals(phase)) return trace(in, target, pins, observation, workerHash);
        if ("prepare".equals(phase)) {
            if (pins.contains("observation")) throw new AssertionError("Never reseed owned case");
            save(pins.edit().putString("observation", observation));
        } else if (!observation.equals(pins.getString("observation", ""))
                || !pins.contains("project")) throw new AssertionError("Case ownership required");
        if ("compile".equals(phase)) {
            if (!List.of("red", "green").contains(expected)
                    || pins.getBoolean(expected + "Attempted", false)
                    || "green".equals(expected) && !pins.getBoolean("redVerified", false)
                    || workerHash == null
                    || !workerHash.matches("[0-9a-f]{64}"))
                throw new AssertionError("Exact once-only worker transition required");
            File workerApk =
                    new File(
                            target.getPackageManager()
                                    .getApplicationInfo("dev.smallwright.worker", 0)
                                    .sourceDir);
            if (!workerHash.equals(sha256(Files.readAllBytes(workerApk.toPath()))))
                throw new AssertionError("Installed worker differs from approved public APK pin");
            if ("green".equals(expected)
                    && workerHash.equals(pins.getString("redWorkerSha256", "")))
                throw new AssertionError("New worker required after actual RED");
        }
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerUi.awaitProjectBinding(in, ui);
        String previousProject =
                mainValue(in, () -> ((ProjectIdentity) field(ui, "activeProject")).id());
        String previousChat = mainValue(in, () -> ((ChatStore.Chat) field(ui, "selectedChat")).id);
        Throwable original = null;
        try {
            if ("prepare".equals(phase)) {
                stage = "NEW_PROJECT";
                onMain(in, () -> requireIdle(ui));
                CompilerMeasurementProbe.createProject(in, ui, "Dialog resource " + observation);
                ProjectIdentity project =
                        mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!project.id().matches("app[0-9a-f]{32}") || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Fresh isolated project required");
                try {
                    target.getPackageManager().getPackageInfo(project.packageName(), 0);
                    throw new AssertionError("Fresh package already installed");
                } catch (PackageManager.NameNotFoundException absent) {
                    /* No existing app data. */
                }
                Map<String, byte[]> files = fixture(in, project);
                int staged = source.stageRecoverySources(0, files);
                int revision = source.promoteRecoverySources(0, staged);
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString(
                                        "chat",
                                        mainValue(
                                                in,
                                                () ->
                                                        ((ChatStore.Chat) field(ui, "selectedChat"))
                                                                .id))
                                .putInt("revision", revision)
                                .putString("snapshotSha256", snapshotHash(files)));
                return "Dialog resource prepare " + describe(project, source, pins) + "\n";
            }
            stage = "SELECT_OWN_PROJECT";
            A33CounterFeedbackProbe.selectProject(in, ui, pins.getString("project", ""));
            A33CounterFeedbackProbe.selectChat(in, ui, pins.getString("chat", ""));
            ActiveWorkerStopProbe.awaitWorker(in, ui);
            ProjectIdentity project =
                    mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if (!project.id().equals(pins.getString("project", ""))
                    || !project.packageName().equals(pins.getString("package", "")))
                throw new AssertionError("Owned fresh project changed");
            requireSnapshot(
                    source, pins.getInt("revision", -1), pins.getString("snapshotSha256", ""));
            if (!snapshotHash(fixture(in, project)).equals(pins.getString("snapshotSha256", "")))
                throw new AssertionError("Authored fixture changed; never replace saved source");
            if ("compile".equals(phase)) {
                stage = "NORMAL_BUILD_" + expected;
                int before = source.revision();
                onMain(
                        in,
                        () -> {
                            requireIdle(ui);
                            if ((int) field(ui, "editorRevision") != before
                                    || !source.readSource()
                                            .contentEquals(
                                                    ((TextBox) field(ui, "source")).getText()))
                                throw new AssertionError("Exact saved editor required");
                        });
                save(
                        pins.edit()
                                .putBoolean(expected + "Attempted", true)
                                .putString(expected + "WorkerSha256", workerHash));
                ActiveWorkerStopProbe.showTools(in, ui);
                ActiveWorkerStopProbe.visible(in, ui, Labels.BUILD_SOURCE);
                awaitSettled(in, ui);
                if (source.revision() != before + 1
                        || !pins.getString("snapshotSha256", "")
                                .equals(
                                        snapshotHash(
                                                source.readRecoverySources(source.revision()))))
                    throw new AssertionError("Normal Build changed authored source bytes");
                save(pins.edit().putInt("revision", source.revision()));
                if ("red".equals(expected)) {
                    stage = "ACTUAL_JAVA_COMPILE_RED";
                    String status =
                            mainValue(
                                    in, () -> ((TextBox) field(ui, "status")).getText().toString());
                    if (!status.equals(
                            "Java source did not compile. Saved source remains; edit it and Build again. No new APK is ready."))
                        throw new AssertionError("Expected actual JavaCompileFailure status");
                    requireNotReady(in, ui, target, project, source.revision());
                    save(pins.edit().putBoolean("redVerified", true));
                    return "Dialog resource compile red "
                            + describe(project, source, pins)
                                    .put("compileResult", "compile_error")
                                    .put("verifiedReceipt", false)
                            + "\n";
                }
                stage = "ACTUAL_PHONE_COMPILE_GREEN";
                SignedArtifactReceipt.Record receipt =
                        verifiedRecord(target, project, source.revision());
                ChatStore chats = mainValue(in, () -> (ChatStore) field(ui, "chatStore"));
                ManualBuildOriginStore.Origin origin =
                        source.verifiedManualBuildOrigin(source.revision(), chats);
                if (origin == null
                        || !origin.chatId.equals(pins.getString("chat", ""))
                        || !origin.artifactSha256.equals(receipt.sha256)
                        || origin.versionCode != receipt.versionCode)
                    throw new AssertionError("Exact normal Build origin absent");
                awaitReady(in, ui, receipt.sha256);
                save(
                        pins.edit()
                                .putBoolean("greenVerified", true)
                                .putString("apkSha256", receipt.sha256)
                                .putString("signerSha256", receipt.signerFingerprint)
                                .putInt("version", receipt.versionCode)
                                .putString("apkPath", receipt.apk.getAbsolutePath()));
            }
            if ("ready".equals(phase) || "green".equals(expected) && "compile".equals(phase)) {
                if (!pins.getBoolean("greenVerified", false))
                    throw new AssertionError("No actual verified GREEN");
                SignedArtifactReceipt.Record receipt =
                        verifiedRecord(target, project, source.revision());
                if (!receipt.sha256.equals(pins.getString("apkSha256", ""))
                        || !receipt.signerFingerprint.equals(pins.getString("signerSha256", ""))
                        || receipt.versionCode != pins.getInt("version", -1))
                    throw new AssertionError("Retained receipt changed");
                awaitReady(in, ui, receipt.sha256);
                return "Dialog resource "
                        + phase
                        + " "
                        + describe(project, source, pins)
                                .put("compileResult", "verified")
                                .put("apkSha256", receipt.sha256)
                                .put("apkPath", receipt.apk.getAbsolutePath())
                                .put("apkBytes", receipt.apk.length())
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("version", receipt.versionCode)
                        + "\n";
            }
            if (!pins.getBoolean("greenVerified", false))
                requireNotReady(in, ui, target, project, source.revision());
            return "Dialog resource inspect " + describe(project, source, pins) + "\n";
        } catch (Exception | AssertionError failure) {
            original = failure;
            throw failure;
        } finally {
            try {
                awaitSettled(in, ui);
                A33CounterFeedbackProbe.selectProject(in, ui, previousProject);
                A33CounterFeedbackProbe.selectChat(in, ui, previousChat);
            } catch (Exception | AssertionError restoration) {
                if (original != null) original.addSuppressed(restoration);
                else throw restoration;
            } finally {
                onMain(in, ui::finish);
            }
        }
    }

    /**
     * Independent once-only Binder observation; no UI, source promotion or artifact publication.
     */
    private static String trace(
            Instrumentation in,
            Context target,
            SharedPreferences pins,
            String observation,
            String workerHash)
            throws Exception {
        if (!observation.equals(pins.getString("observation", ""))
                || !pins.contains("project")
                || pins.getInt("revision", -1) != 2
                || !pins.getBoolean("redAttempted", false)
                || pins.getBoolean("redVerified", false)
                || pins.getBoolean("greenAttempted", false)
                || workerHash == null
                || !workerHash.matches("[0-9a-f]{64}")
                || !workerHash.equals(pins.getString("redWorkerSha256", "")))
            throw new AssertionError("Exact retained failed observer required");
        File workerApk =
                new File(
                        target.getPackageManager()
                                .getApplicationInfo("dev.smallwright.worker", 0)
                                .sourceDir);
        if (!workerHash.equals(sha256(Files.readAllBytes(workerApk.toPath()))))
            throw new AssertionError("Trace worker differs from original public APK pin");
        SharedPreferences tracePins = target.getSharedPreferences(TRACE_PREFS, 0);
        if (tracePins.contains("observation")) throw new AssertionError("Trace already reserved");
        Map<String, ?> originalPins = pins.getAll();
        ProjectIdentity project =
                new ProjectRegistry(
                                new File(target.getFilesDir(), "projects"), target.getFilesDir())
                        .get(pins.getString("project", ""));
        ProjectSourceStore source = new ProjectSourceStore(project);
        String snapshotHash = pins.getString("snapshotSha256", "");
        requireSnapshot(source, 2, snapshotHash);
        if (!project.title().equals("Dialog resource " + observation)
                || !project.packageName().equals(pins.getString("package", ""))
                || !snapshotHash(fixture(in, project)).equals(snapshotHash)
                || Files.exists(
                        new File(project.directory(), "artifacts/verified-artifact.properties")
                                .toPath(),
                        java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new AssertionError("Exact unverified authored source required");
        ChatStore chats =
                new ChatStore(new File(target.getFilesDir(), "chats"), target.getFilesDir());
        ManualBuildOriginStore.Origin origin = source.manualBuildOrigin(2, chats);
        if (origin == null
                || origin.artifactSha256 != null
                || !origin.chatId.equals(pins.getString("chat", ""))
                || origin.versionCode != 1)
            throw new AssertionError("Exact failed manual Build origin required");
        byte[] admitted =
                source.readRecoverySources(2)
                        .get(
                                "java/"
                                        + project.packageName().replace('.', '/')
                                        + "/MainActivity.java");
        save(
                tracePins
                        .edit()
                        .putString("observation", observation)
                        .putString("project", project.id())
                        .putInt("revision", 2)
                        .putString("snapshotSha256", snapshotHash)
                        .putString("workerSha256", workerHash));
        JSONObject result =
                new JSONObject()
                        .put("case", observation)
                        .put("project", project.id())
                        .put("sourceRevision", 2)
                        .put("version", origin.versionCode)
                        .put("snapshotSha256", snapshotHash)
                        .put("workerSha256", workerHash)
                        .put("binderEntered", false)
                        .put("binderReturned", false)
                        .put("generatedInstallationAttempted", false)
                        .put("generatedSourceExported", false)
                        .put("signedArtifactPublished", false)
                        .put("modelRequestMade", false);
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
        stage = "TRACE_BIND";
        boolean bound =
                target.bindService(
                        new Intent()
                                .setComponent(
                                        new ComponentName(
                                                "dev.smallwright.worker",
                                                "dev.smallwright.worker.BuildService")),
                        connection,
                        Context.BIND_AUTO_CREATE);
        FutureTask<JSONObject> call = null;
        try {
            if (!bound || !connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                return "Dialog resource trace " + result.put("outcome", "BIND_UNAVAILABLE") + "\n";
            IBuildService worker = remote.get();
            call =
                    new FutureTask<>(
                            () ->
                                    traceCall(
                                            in,
                                            target,
                                            source,
                                            origin.versionCode,
                                            worker,
                                            admitted,
                                            result));
            new Thread(call, "owned-dialog-source-trace").start();
            return "Dialog resource trace " + awaitTrace(in, call) + "\n";
        } finally {
            if (call != null && !call.isDone()) awaitTrace(in, call);
            if (bound) target.unbindService(connection);
            requireSnapshot(source, 2, snapshotHash);
            if (!pins.getAll().equals(originalPins))
                throw new AssertionError("Original observer pins changed during trace");
        }
    }

    private static JSONObject traceCall(
            Instrumentation in,
            Context target,
            ProjectSourceStore source,
            int version,
            IBuildService worker,
            byte[] admitted,
            JSONObject result)
            throws Exception {
        File scratch =
                Files.createTempDirectory(target.getCacheDir().toPath(), "dialog-trace-").toFile();
        File snapshot = new File(scratch, "snapshot.zip");
        File apk = new File(scratch, "unsigned.apk");
        File report = new File(scratch, "numeric.report");
        try {
            stage = "TRACE_SNAPSHOT";
            try (ProjectStore.BuildLease lease = source.beginBuild(2, snapshot, version)) {
                result.put("snapshotAssembled", true);
                traceMarker(in, "SNAPSHOT_ASSEMBLED");
                stage = "TRACE_HANDSHAKE";
                if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                        .equals(worker.handshake(2)))
                    return result.put("outcome", "UNEXPECTED_CAPABILITY");
                result.put("numericCapability", true);
                traceMarker(in, "NUMERIC_CAPABILITY");
                Files.createFile(apk.toPath());
                Files.createFile(report.toPath());
                String outcome;
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        apk, ParcelFileDescriptor.MODE_READ_WRITE);
                        ParcelFileDescriptor diagnostics =
                                ParcelFileDescriptor.open(
                                        report, ParcelFileDescriptor.MODE_READ_WRITE)) {
                    stage = "TRACE_BINDER";
                    result.put("binderEntered", true);
                    traceMarker(in, "BINDER_ENTERED");
                    outcome = worker.buildWithDiagnostics(input, output, diagnostics);
                    result.put("binderReturned", true);
                }
                result.put("outcome", traceOutcome(outcome))
                        .put("unsignedOutputBytes", apk.length());
                traceMarker(in, "BINDER_RETURNED");
                stage = "TRACE_REPORT";
                try {
                    if (!Files.isRegularFile(
                            report.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        throw new java.io.IOException("Invalid numeric file");
                    byte[] wire = new byte[CompilerDiagnostics.MAX_BYTES + 1];
                    int used = 0;
                    try (java.io.InputStream input = Files.newInputStream(report.toPath())) {
                        while (used < wire.length) {
                            int count = input.read(wire, used, wire.length - used);
                            if (count < 0) break;
                            if (count == 0) continue;
                            used += count;
                        }
                    }
                    result.put("reportBytes", used);
                    if (used > CompilerDiagnostics.MAX_BYTES)
                        throw new java.io.IOException("Oversized numeric report");
                    if (!"COMPILE_ERROR".equals(outcome) && used != 0)
                        throw new java.io.IOException("Unexpected numeric report");
                    if (used == 0) result.put("numericReport", "absent");
                    else {
                        CompilerDiagnostics.Report decoded =
                                CompilerDiagnostics.decode(
                                        java.util.Arrays.copyOf(wire, used),
                                        new java.util.TreeMap<>(
                                                java.util.Map.of(source.mainPath(), admitted)));
                        JSONArray problems = new JSONArray();
                        for (CompilerDiagnostics.Problem problem : decoded.problems)
                            problems.put(
                                    new JSONObject()
                                            .put("id", problem.id)
                                            .put("category", problem.category)
                                            .put("line", problem.line));
                        result.put("numericReport", "validated")
                                .put("sourceSha256", decoded.sourceSha256)
                                .put("problems", problems)
                                .put("truncated", decoded.truncated);
                    }
                } catch (Exception rejected) {
                    result.put("numericReport", "rejected")
                            .put("reportExceptionCategory", rejected.getClass().getSimpleName());
                }
            }
            return result;
        } catch (Exception failed) {
            return result.put("outcome", "TRACE_EXCEPTION")
                    .put("exceptionStage", stage)
                    .put("exceptionCategory", failed.getClass().getSimpleName());
        } finally {
            Files.deleteIfExists(report.toPath());
            Files.deleteIfExists(apk.toPath());
            Files.deleteIfExists(snapshot.toPath());
            Files.deleteIfExists(scratch.toPath());
        }
    }

    private static String traceOutcome(String result) {
        if ("OK".equals(result)) return "OK_UNVERIFIED";
        if ("COMPILE_ERROR".equals(result)) return "COMPILE_ERROR";
        if ("SNAPSHOT_TIMEOUT".equals(result)) return "SNAPSHOT_TIMEOUT";
        if ("ERROR: Worker busy".equals(result)) return "WORKER_BUSY";
        if (result != null && result.startsWith("ERROR: Packaged AAPT2 failed: "))
            return "AAPT2_FAILURE";
        if ("ERROR: Packaged AAPT2 timed out".equals(result)) return "AAPT2_TIMEOUT";
        return "OTHER_WORKER_ERROR";
    }

    private static JSONObject awaitTrace(Instrumentation in, FutureTask<JSONObject> call)
            throws Exception {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return call.get(30, TimeUnit.SECONDS);
                } catch (TimeoutException pending) {
                    try {
                        traceMarker(in, "PENDING_NO_STOP_OR_RETRY");
                    } catch (RuntimeException unavailable) {
                        // A status transport failure must not abandon the actual Binder call.
                    }
                } catch (InterruptedException pending) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void traceMarker(Instrumentation in, String marker) {
        android.os.Bundle status = new android.os.Bundle();
        status.putString("stream", "DIALOG_SOURCE_TRACE " + marker + "\n");
        in.sendStatus(0, status);
    }

    /** Reads only the reserved title's existing catalog entry and committed snapshot metadata. */
    private static String diagnose(Context target, String observation) throws Exception {
        stage = "DIAGNOSE_REGISTRY";
        File root = new File(target.getFilesDir(), "projects");
        if (Files.isSymbolicLink(root.toPath()))
            throw new SecurityException("Diagnostic registry is a symlink");
        if (!Files.isDirectory(root.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            return "Dialog resource diagnose "
                    + new JSONObject().put("result", "missing").put("matchCount", 0)
                    + "\n";
        String title = "Dialog resource " + observation;
        List<ProjectIdentity> matches = new ArrayList<>();
        for (ProjectIdentity project : new ProjectRegistry(root, target.getFilesDir()).list())
            if (title.equals(project.title())) matches.add(project);
        JSONObject result = new JSONObject().put("matchCount", matches.size());
        if (matches.size() != 1)
            return "Dialog resource diagnose "
                    + result.put("result", matches.isEmpty() ? "missing" : "ambiguous")
                    + "\n";
        ProjectIdentity project = matches.get(0);
        result.put("result", "unique").put("id", project.id()).put("title", project.title());
        stage = "DIAGNOSE_SNAPSHOT";
        java.nio.file.Path head = new File(project.directory(), "revision-store/HEAD").toPath();
        if (Files.isSymbolicLink(head))
            throw new SecurityException("Diagnostic revision is a symlink");
        if (!Files.isRegularFile(head, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            return "Dialog resource diagnose "
                    + result.put("revision", JSONObject.NULL)
                            .put("isPristine", JSONObject.NULL)
                            .put("fileCount", JSONObject.NULL)
                            .put("fileHashes", JSONObject.NULL)
                    + "\n";
        ProjectSourceStore source = new ProjectSourceStore(project);
        int revision = source.revision();
        Map<String, byte[]> files = source.readRecoverySources(revision);
        JSONObject hashes = new JSONObject();
        for (Map.Entry<String, byte[]> file : new TreeMap<>(files).entrySet())
            hashes.put(file.getKey(), sha256(file.getValue()));
        boolean pristine = source.isPristineDefaultSnapshot();
        if (source.revision() != revision)
            throw new AssertionError("Diagnostic snapshot changed while reading");
        return "Dialog resource diagnose "
                + result.put("revision", revision)
                        .put("isPristine", pristine)
                        .put("fileCount", files.size())
                        .put("fileHashes", hashes)
                + "\n";
    }

    private static Map<String, byte[]> fixture(Instrumentation in, ProjectIdentity project)
            throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        String java =
                asset(in, "MainActivity.java.txt").replace("@PACKAGE@", project.packageName());
        files.put(
                "java/" + project.packageName().replace('.', '/') + "/MainActivity.java",
                java.getBytes(StandardCharsets.UTF_8));
        files.put("res/layout/empty.xml", asset(in, "empty.xml").getBytes(StandardCharsets.UTF_8));
        files.put(
                "res/values/dialog_labels.xml",
                asset(in, "dialog_labels.xml").getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private static String asset(Instrumentation in, String name) throws Exception {
        try (java.io.InputStream input =
                in.getContext().getAssets().open("dialog-resource/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JSONObject describe(
            ProjectIdentity project, ProjectSourceStore source, SharedPreferences pins)
            throws Exception {
        return new JSONObject()
                .put("case", pins.getString("observation", ""))
                .put("project", project.id())
                .put("package", project.packageName())
                .put("chat", pins.getString("chat", ""))
                .put("sourceRevision", source.revision())
                .put("snapshotSha256", pins.getString("snapshotSha256", ""))
                .put("modelRequestMade", false)
                .put("generatedSourceExported", false)
                .put("generatedInstallationAttempted", false)
                .put("nativeRuntimeTested", false);
    }

    private static void requireNotReady(
            Instrumentation in, Activity ui, Context target, ProjectIdentity project, int revision)
            throws Exception {
        if (Files.exists(
                        new File(project.directory(), "artifacts/verified-artifact.properties")
                                .toPath(),
                        java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || mainValue(in, () -> field(ui, "signedArtifact")) != null
                || mainValue(in, () -> ((Control) field(ui, "install")).isEnabled()))
            throw new AssertionError("Failed/unbuilt revision exposed readiness");
    }

    private static void awaitSettled(Instrumentation in, Activity ui) throws Exception {
        while (!awaitIdle(in, ui, 30)) {
            android.os.Bundle waiting = new android.os.Bundle();
            waiting.putString(
                    "stream",
                    "Owned dialog Build pending; no Stop/retry or selection restoration\n");
            in.sendStatus(0, waiting);
        }
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Owned fixture pin persistence failed");
    }
}
