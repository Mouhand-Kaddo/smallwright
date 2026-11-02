package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Normal MainActivity controls and actual phone compilation; only a newly owned project is edited.
 */
final class ManualBuildOriginProbe {
    private static final String PREFS = "manual-build-origin-probe-v1";
    private static String stage = "GUARD";
    private static String evidence = "";

    static String failureStage() {
        return stage;
    }

    static String failureEvidence() {
        return evidence;
    }

    static String run(
            Instrumentation instrumentation, String observation, String phase, boolean approved)
            throws Exception {
        stage = "GUARD";
        evidence = "";
        Context context = instrumentation.getTargetContext();
        if (!approved
                || observation == null
                || !observation.matches("[0-9a-f]{32}")
                || !("red".equals(phase) || "green".equals(phase) || "verify".equals(phase))
                || context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Explicit local phase approval and unlocked UI required");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("red".equals(phase)) {
            if (pins.contains("observation"))
                throw new AssertionError("RED already reserved; never reseed");
            reserve(
                    pins.edit()
                            .putString("observation", observation)
                            .putBoolean("redReserved", true));
        } else if (!observation.equals(pins.getString("observation", null))
                || !pins.getBoolean("redRecorded", false)
                || "green".equals(phase) && pins.getBoolean("greenReserved", false)
                || "verify".equals(phase) && !pins.getBoolean("greenVerified", false)) {
            throw new AssertionError("Phase does not own the retained observation");
        }
        if ("green".equals(phase)) reserve(pins.edit().putBoolean("greenReserved", true));
        Intent launch =
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(launch);
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        if (value(instrumentation, activity, "pendingRecovery", Object.class) != null
                || value(instrumentation, activity, "startupBlocked", Boolean.class)
                || value(instrumentation, activity, "buildInProgress", Boolean.class)) {
            Activity blocked = activity;
            instrumentation.runOnMainSync(blocked::finish);
            throw new AssertionError("Existing controller work prevents owned probe");
        }
        String previousProject =
                value(instrumentation, activity, "activeProject", ProjectIdentity.class).id();
        String previousChat =
                value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
        Throwable original = null;
        try {
            ProjectIdentity project;
            if ("red".equals(phase)) {
                stage = "NEW_PROJECT_UI";
                CompilerMeasurementProbe.createProject(
                        instrumentation, activity, "Manual origin " + observation);
                project = value(instrumentation, activity, "activeProject", ProjectIdentity.class);
                String captured =
                        value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
                stage = "NEW_CHAT_UI";
                Ui.openMenu(instrumentation, activity);
                ActiveWorkerStopProbe.visible(instrumentation, activity, Labels.NEW_CHAT);
                String browsed =
                        value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
                if (captured.equals(browsed))
                    throw new AssertionError("Normal New chat did not create separate chat");
                reserve(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("capturedChat", captured)
                                .putString("browsedChat", browsed));
            } else {
                stage = "SELECT_RETAINED_PROJECT_UI";
                selectProject(instrumentation, activity, pins.getString("project", ""));
                project = value(instrumentation, activity, "activeProject", ProjectIdentity.class);
            }
            ProjectSourceStore source = new ProjectSourceStore(project);
            ChatStore chats = value(instrumentation, activity, "chatStore", ChatStore.class);
            String captured = pins.getString("capturedChat", "");
            String browsed = pins.getString("browsedChat", "");
            if (!"red".equals(phase)) {
                stage = "LEGACY_ORIGIN_REMAINS_UNKNOWN";
                int legacyRevision = pins.getInt("legacyRevision", -1);
                if (source.manualBuildOrigin(legacyRevision, chats) != null)
                    throw new AssertionError(
                            "Pre-feature artifact was assigned a fabricated origin");
                VerifiedArtifact legacy =
                        VerifiedArtifact.capture(new File(pins.getString("legacyApk", "")));
                if (!legacy.sha256().equals(pins.getString("legacySha256", "")))
                    throw new AssertionError("Pre-feature artifact bytes changed");
                legacy.verifyUnchanged();
            }
            if (!"verify".equals(phase)) {
                stage = "NORMAL_BUILD_WITH_ASSISTED_CHAT_SWITCH_" + phase;
                int before = source.revision();
                selectChat(instrumentation, activity, captured);
                SignedArtifactReceipt.Record built =
                        buildWhileBrowsing(
                                instrumentation,
                                activity,
                                browsed,
                                "manual-origin-" + observation + "-" + phase);
                int revision = source.revision();
                if (revision != before + 1)
                    throw new AssertionError("Build did not commit exactly one source revision");
                evidence = describe(project, source, revision, built, captured, browsed, phase);
                if ("red".equals(phase)) {
                    reserve(
                            pins.edit()
                                    .putInt("legacyRevision", revision)
                                    .putInt("legacyVersion", built.versionCode)
                                    .putString("legacySigner", built.signerFingerprint)
                                    .putString("legacyApk", built.apk.getAbsolutePath())
                                    .putString("legacySha256", built.sha256)
                                    .putString("redEvidence", evidence)
                                    .putBoolean("redRecorded", true));
                } else {
                    if (built.versionCode != pins.getInt("legacyVersion", -1) + 1
                            || !built.signerFingerprint.equals(pins.getString("legacySigner", "")))
                        throw new AssertionError(
                                "Manual origin changed existing build/signer lineage");
                    reserve(
                            pins.edit()
                                    .putInt("greenRevision", revision)
                                    .putString("greenSha256", built.sha256)
                                    .putString("greenEvidence", evidence));
                }
                stage = "EXPECT_DURABLE_CAPTURED_ORIGIN_" + phase;
                ManualBuildOriginStore.Origin origin =
                        source.verifiedManualBuildOrigin(revision, chats);
                if (origin == null)
                    throw new AssertionError(
                            "Normal direct Build has no durable captured-chat origin");
                assertOrigin(origin, project, revision, captured, built.sha256, built.versionCode);
                reserve(pins.edit().putString("greenBuildId", origin.buildId));
                stage = "REOPEN_NORMAL_CONTROLLER";
                Activity first = activity;
                instrumentation.runOnMainSync(first::finish);
                instrumentation.waitForIdleSync();
                activity = instrumentation.startActivitySync(launch);
                ControllerUi.awaitProjectBinding(instrumentation, activity);
                if (!project.id()
                                .equals(
                                        value(
                                                        instrumentation,
                                                        activity,
                                                        "activeProject",
                                                        ProjectIdentity.class)
                                                .id())
                        || !browsed.equals(
                                value(
                                                instrumentation,
                                                activity,
                                                "selectedChat",
                                                ChatStore.Chat.class)
                                        .id))
                    throw new AssertionError("Reopen lost the built project/browsed chat");
            }
            stage = "VERIFY_REOPENED_ORIGIN_AND_RECEIPT";
            int revision = pins.getInt("greenRevision", -1);
            ProjectSourceStore reopened = new ProjectSourceStore(project);
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), revision);
            ManualBuildOriginStore.Origin restored =
                    reopened.verifiedManualBuildOrigin(revision, chats);
            assertOrigin(
                    restored,
                    project,
                    revision,
                    captured,
                    pins.getString("greenSha256", ""),
                    receipt.versionCode);
            if (!restored.buildId.equals(pins.getString("greenBuildId", "")))
                throw new AssertionError("Reopen relabeled build identity");
            SignedArtifactReceipt.restore(activity, project, revision).verifyUnchanged();
            VerifiedArtifact offered =
                    value(instrumentation, activity, "signedArtifact", VerifiedArtifact.class);
            if (offered == null || !offered.sha256().equals(receipt.sha256))
                throw new AssertionError(
                        "Reopened normal controller did not offer exact verified artifact");
            if (reopened.manualBuildOrigin(pins.getInt("legacyRevision", -1), chats) != null)
                throw new AssertionError("Old origin fabricated after restart");
            evidence =
                    describe(project, reopened, revision, receipt, captured, browsed, phase)
                            + "buildId="
                            + restored.buildId
                            + " sourceSnapshotSha256="
                            + hex(restored.sourceDigest)
                            + "\n";
            reserve(
                    pins.edit()
                            .putBoolean("greenVerified", true)
                            .putString("verifiedEvidence", evidence));
            stage = "VERIFIED";
            return "Manual Build origin: normal MainActivity New project/New chat/Build; executor queue parked only to switch chats deterministically; actual phone compiler/signature/receipt; captured chat survives activity/store reopen; old origin remains unknown. No model call or installation.\n"
                    + evidence;
        } catch (Exception | AssertionError failure) {
            original = failure;
            throw failure;
        } finally {
            try {
                if (!PhoneAlphaCounterProbe.awaitIdle(instrumentation, activity, 60))
                    throw new AssertionError(
                            "Owned build still active; selection cannot safely restore");
                selectProject(instrumentation, activity, previousProject);
                selectChat(instrumentation, activity, previousChat);
                reserve(pins.edit().putBoolean(phase + "SelectionRestored", true));
            } catch (Exception | AssertionError restoration) {
                if (original != null) original.addSuppressed(restoration);
                else throw restoration;
            } finally {
                Activity closing = activity;
                instrumentation.runOnMainSync(closing::finish);
            }
        }
    }

    private static SignedArtifactReceipt.Record buildWhileBrowsing(
            Instrumentation in, Activity activity, String browsed, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (value(in, activity, "worker", Object.class) == null) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Worker not connected");
            Thread.sleep(50);
        }
        ProjectIdentity project = value(in, activity, "activeProject", ProjectIdentity.class);
        ProjectSourceStore source = new ProjectSourceStore(project);
        String edited = source.readSource() + "\n// " + marker + "\n";
        ActiveWorkerStopProbe.showTools(in, activity);
        in.runOnMainSync(() -> field(activity, "source", TextBox.class).setText(edited));
        CountDownLatch parked = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = value(in, activity, "executor", ExecutorService.class);
        executor.execute(
                () -> {
                    parked.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
        try {
            if (!parked.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Executor queue not parked");
            ActiveWorkerStopProbe.visible(in, activity, Labels.BUILD_SOURCE);
            if (!value(in, activity, "buildInProgress", Boolean.class))
                throw new AssertionError("Normal Build did not queue");
            selectChat(in, activity, browsed);
            if (!browsed.equals(value(in, activity, "selectedChat", ChatStore.Chat.class).id)
                    || release.getCount() != 1)
                throw new AssertionError(
                        "Chat switch was not completed before queued work started");
        } finally {
            release.countDown();
        }
        if (!PhoneAlphaCounterProbe.awaitIdle(in, activity, 60))
            throw new AssertionError("Phone build did not settle");
        if (value(in, activity, "signedArtifact", VerifiedArtifact.class) == null)
            throw new AssertionError("Actual compiler did not produce verified signed artifact");
        SignedArtifactReceipt.restore(activity, project, source.revision()).verifyUnchanged();
        return SignedArtifactReceipt.read(
                project.directory(), project.id(), project.packageName(), source.revision());
    }

    private static void assertOrigin(
            ManualBuildOriginStore.Origin origin,
            ProjectIdentity project,
            int revision,
            String chat,
            String sha256,
            int version) {
        if (origin == null
                || !project.id().equals(origin.projectId)
                || origin.sourceRevision != revision
                || !chat.equals(origin.chatId)
                || !sha256.equals(origin.artifactSha256)
                || origin.versionCode != version
                || origin.buildId == null)
            throw new AssertionError("Origin differs from captured chat/exact receipt");
    }

    private static String describe(
            ProjectIdentity project,
            ProjectSourceStore source,
            int revision,
            SignedArtifactReceipt.Record receipt,
            String captured,
            String browsed,
            String phase)
            throws Exception {
        return "phase="
                + phase
                + " project="
                + project.id()
                + " package="
                + project.packageName()
                + " capturedChat="
                + captured
                + " browsedChat="
                + browsed
                + " source="
                + revision
                + " sourceSha256="
                + hex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(source.readSource().getBytes(StandardCharsets.UTF_8)))
                + " version="
                + receipt.versionCode
                + " signer="
                + receipt.signerFingerprint
                + " apk="
                + receipt.apk.getAbsolutePath()
                + " apkSha256="
                + receipt.sha256
                + "\n";
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value));
        return text.toString();
    }

    private static void reserve(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Probe evidence reservation failed");
    }

    private static void selectProject(Instrumentation in, Activity activity, String id)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<ProjectIdentity> projects = value(in, activity, "projectList", List.class);
        for (int i = 0; i < projects.size(); i++)
            if (id.equals(projects.get(i).id())) {
                select(in, activity, "projectPicker", i);
                ControllerUi.awaitProjectBinding(in, activity);
                return;
            }
        throw new AssertionError("Owned project missing from normal picker");
    }

    private static void selectChat(Instrumentation in, Activity activity, String id) {
        @SuppressWarnings("unchecked")
        List<ChatStore.Chat> chats = value(in, activity, "chats", List.class);
        for (int i = 0; i < chats.size(); i++)
            if (id.equals(chats.get(i).id)) {
                select(in, activity, "chatPicker", i);
                return;
            }
        throw new AssertionError("Owned chat missing from normal picker");
    }

    private static void select(Instrumentation in, Activity activity, String name, int index) {
        in.runOnMainSync(
                () -> {
                    Picker picker = field(activity, name, Picker.class);
                    if (!picker.isEnabled()) throw new AssertionError("Normal picker disabled");
                    picker.select(index);
                });
        in.waitForIdleSync();
    }

    private static <T> T value(Instrumentation in, Activity activity, String name, Class<T> type) {
        AtomicReference<T> result = new AtomicReference<>();
        in.runOnMainSync(() -> result.set(field(activity, name, type)));
        return result.get();
    }

    private static <T> T field(Activity activity, String name, Class<T> type) {
        try {
            return type.cast(Ui.field(activity, name));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
