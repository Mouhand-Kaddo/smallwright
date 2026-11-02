package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Builds only its own disposable UI-created project; never invokes a provider or installer. */
final class PhoneOfflineBuildProbe {
    private static final String PREFS = "phone-offline-build-probe";
    private static final String ORIGINAL = "\"Increment\"";
    private static final String REPLACEMENT = "\"Offline add one\"";

    static final class OfflineGuardFailure extends AssertionError {
        OfflineGuardFailure(String message) {
            super(message);
        }
    }

    static String run(Instrumentation instrumentation, String phase, boolean confirmed)
            throws Exception {
        Context target = instrumentation.getTargetContext();
        if (!"prepare".equals(phase) && !"build".equals(phase) && !"restore".equals(phase))
            throw new AssertionError("Unknown offline probe phase");
        // Refuse before starting an Activity, touching prefs, or changing source/UI.
        if (!"prepare".equals(phase)) requireOffline(target, confirmed);
        SharedPreferences pins = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase) && pins.contains("project"))
            throw new AssertionError("Offline probe already has a prepared project");
        Activity activity =
                instrumentation.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        try {
            if ("prepare".equals(phase)) {
                String title = "Phone offline " + UUID.randomUUID();
                onMain(
                        instrumentation,
                        () -> {
                            if (field(activity, "pendingRecovery") != null)
                                throw new AssertionError(
                                        "Finish existing recovery before preparing the offline project");
                            Control create = button(activity, Labels.NEW_PROJECT);
                            PhoneAlphaCounterProbe.reveal(create);
                        });
                instrumentation.waitForIdleSync();
                Ui.openMenu(instrumentation, activity);
                waitNode(instrumentation, target, "android.widget.Button", Labels.NEW_PROJECT);
                onMain(
                        instrumentation,
                        () -> {
                            if (field(activity, "pendingRecovery") != null)
                                throw new AssertionError(
                                        "Finish existing recovery before preparing the offline project");
                            Control create = button(activity, Labels.NEW_PROJECT);
                            requireVisible(create);
                            Ui.click(create);
                        });
                AccessibilityNodeInfo input =
                        waitNode(instrumentation, target, "android.widget.EditText", null);
                if (!"Project name".equals(String.valueOf(input.getHintText())))
                    throw new AssertionError("Offline project title dialog is unavailable");
                Bundle text = new Bundle();
                text.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
                if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                    throw new AssertionError("Offline project title could not be entered");
                AccessibilityNodeInfo create =
                        waitNode(instrumentation, target, "android.widget.Button", "Create");
                if (!create.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    throw new AssertionError("Offline project could not be created");
                instrumentation.waitForIdleSync();
                ProjectIdentity project = awaitCreatedProject(instrumentation, activity, title);
                if (!title.equals(project.title()) || !project.id().matches("app[0-9a-f]{32}"))
                    throw new AssertionError(
                            "Only the new disposable offline project may be pinned");
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!source.isPristineDefaultSnapshot())
                    throw new AssertionError("Offline project source was not freshly bound");
                int revision = source.revision();
                String hash = snapshotHash(source.readRecoverySources(revision));
                if (!pins.edit()
                        .putString("project", project.id())
                        .putString("package", project.packageName())
                        .putString("title", title)
                        .putInt("preparedRevision", revision)
                        .putString("preparedSha256", hash)
                        .commit()) throw new AssertionError("Could not pin the offline project");
                return "Offline probe prepared: project="
                        + project.id()
                        + " revision="
                        + revision
                        + " sourceSha256="
                        + hash
                        + "\n";
            }

            ProjectIdentity project = mainValue(instrumentation, () -> requireOwn(activity, pins));
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("restore".equals(phase)) {
                long observedMs = pins.getLong("observedStartToReadyMs", -1);
                if (!pins.contains("artifactSha256") || observedMs < 0)
                    throw new AssertionError("No successful offline build is pinned");
                requireSnapshot(
                        source,
                        pins.getInt("builtRevision", -1),
                        pins.getString("builtSourceSha256", ""));
                awaitInstall(instrumentation, activity, target, confirmed);
                SignedArtifactReceipt.Record record =
                        verifiedRecord(target, project, source.revision());
                if (!record.sha256.equals(pins.getString("artifactSha256", ""))
                        || record.versionCode != pins.getInt("version", -1)
                        || !record.signerFingerprint.equals(pins.getString("signerSha256", "")))
                    throw new AssertionError(
                            "Restart did not restore the exact offline build receipt");
                requireReady(instrumentation, activity, pins, record);
                requireOffline(target, confirmed);
                return metadata(
                        "Offline receipt restored", project, source.revision(), record, observedMs);
            }

            int revision = pins.getInt("preparedRevision", -1);
            String preparedHash = pins.getString("preparedSha256", "");
            requireSnapshot(source, revision, preparedHash);
            String original = source.readSource();
            int first = original.indexOf(ORIGINAL);
            if (first < 0 || original.indexOf(ORIGINAL, first + ORIGINAL.length()) >= 0)
                throw new AssertionError(
                        "Expected exactly one Increment label in the offline project");
            String changed = original.replace(ORIGINAL, REPLACEMENT);
            Map<String, byte[]> expected = new TreeMap<>(source.readRecoverySources(revision));
            expected.put(
                    "java/" + project.packageName().replace('.', '/') + "/MainActivity.java",
                    changed.getBytes(StandardCharsets.UTF_8));
            String expectedHash = snapshotHash(expected);
            AtomicLong buildStarted = new AtomicLong();
            onMain(
                    instrumentation,
                    () -> {
                        requireOffline(target, confirmed);
                        requireOwn(activity, pins);
                        requireSnapshot(source, revision, preparedHash);
                        TextBox editor = (TextBox) field(activity, "source");
                        if (!original.contentEquals(editor.getText())
                                || (int) field(activity, "editorRevision") != revision)
                            throw new AssertionError(
                                    "Offline source editor differs from the pinned snapshot");
                        Ui.state(activity).setToolsOpen(true);
                        editor.setText(changed);
                    });
            instrumentation.waitForIdleSync();
            onMain(
                    instrumentation,
                    () -> {
                        requireOffline(target, confirmed);
                        requireOwn(activity, pins);
                        Control build = button(activity, Labels.BUILD_SOURCE);
                        PhoneAlphaCounterProbe.reveal(build);
                    });
            instrumentation.waitForIdleSync();
            onMain(
                    instrumentation,
                    () -> {
                        requireOffline(target, confirmed);
                        requireOwn(activity, pins);
                        requireSnapshot(source, revision, preparedHash);
                        if (!changed.contentEquals(((TextBox) field(activity, "source")).getText()))
                            throw new AssertionError("Offline label edit changed before Build");
                        Control build = button(activity, Labels.BUILD_SOURCE);
                        requireVisible(build);
                        buildStarted.set(SystemClock.elapsedRealtime());
                        Ui.click(build);
                        if (!(boolean) field(activity, "buildInProgress"))
                            throw new AssertionError("Visible offline Build did not start");
                    });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
            while (mainValue(instrumentation, () -> (boolean) field(activity, "buildInProgress"))) {
                requireOffline(target, confirmed);
                if (System.nanoTime() >= deadline)
                    throw new AssertionError("Offline build deadline exceeded");
                Thread.sleep(100);
            }
            long observedMs = SystemClock.elapsedRealtime() - buildStarted.get();
            requireOffline(target, confirmed);
            requireSnapshot(source, revision + 1, expectedHash);
            SignedArtifactReceipt.Record record = verifiedRecord(target, project, revision + 1);
            requireReady(instrumentation, activity, pins, record);
            requireOffline(target, confirmed);
            if (!pins.edit()
                    .putInt("builtRevision", revision + 1)
                    .putString("builtSourceSha256", expectedHash)
                    .putString("artifactSha256", record.sha256)
                    .putInt("version", record.versionCode)
                    .putString("signerSha256", record.signerFingerprint)
                    .putLong("observedStartToReadyMs", observedMs)
                    .commit())
                throw new AssertionError("Could not pin the successful offline build");
            return metadata(
                    "Offline signed build ready", project, revision + 1, record, observedMs);
        } finally {
            onMain(instrumentation, activity::finish);
        }
    }

    private static void requireOffline(Context context, boolean confirmed) {
        if (!confirmed) throw new OfflineGuardFailure("explicit offlineConfirmed=true required");
        if (Settings.Global.getInt(
                        context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                != 1)
            throw new OfflineGuardFailure(
                    "airplane mode must be on before offline build or restore");
    }

    private static ProjectIdentity requireOwn(Activity activity, SharedPreferences pins)
            throws Exception {
        ProjectIdentity project = project(activity);
        if (project == null
                || !project.id().equals(pins.getString("project", ""))
                || !project.packageName().equals(pins.getString("package", ""))
                || !project.title().equals(pins.getString("title", "")))
            throw new AssertionError("Only the pinned disposable offline project may be tested");
        return project;
    }

    private static void requireSnapshot(ProjectSourceStore source, int revision, String expected)
            throws Exception {
        if (revision < 0
                || source.revision() != revision
                || !snapshotHash(source.readRecoverySources(revision)).equals(expected))
            throw new AssertionError("Offline project revision or snapshot differs from its pin");
    }

    private static String snapshotHash(Map<String, byte[]> snapshot) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(snapshot).entrySet()) {
            byte[] path = entry.getKey().getBytes(StandardCharsets.UTF_8);
            hash.update(ByteBuffer.allocate(4).putInt(path.length).array());
            hash.update(path);
            hash.update(ByteBuffer.allocate(8).putLong(entry.getValue().length).array());
            hash.update(entry.getValue());
        }
        StringBuilder result = new StringBuilder();
        for (byte value : hash.digest())
            result.append(String.format(java.util.Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private static SignedArtifactReceipt.Record verifiedRecord(
            Context context, ProjectIdentity project, int revision) throws Exception {
        VerifiedArtifact artifact = SignedArtifactReceipt.restore(context, project, revision);
        SignedArtifactReceipt.Record record =
                SignedArtifactReceipt.read(
                        project.directory(), project.id(), project.packageName(), revision);
        if (artifact == null || !artifact.sha256().equals(record.sha256))
            throw new AssertionError("Offline signed receipt is unavailable");
        return record;
    }

    private static String metadata(
            String label,
            ProjectIdentity project,
            int revision,
            SignedArtifactReceipt.Record record,
            long observedMs) {
        return label
                + ": project="
                + project.id()
                + " revision="
                + revision
                + " version="
                + record.versionCode
                + " sha256="
                + record.sha256
                + " signerSha256="
                + record.signerFingerprint
                + " observedStartToReadyMs="
                + observedMs
                + "\n";
    }

    private static ProjectIdentity awaitCreatedProject(
            Instrumentation instrumentation, Activity activity, String title) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            ProjectIdentity current = mainValue(instrumentation, () -> project(activity));
            if (current != null && title.equals(current.title())) {
                ControllerUi.awaitProjectBinding(instrumentation, activity);
                ProjectIdentity bound = mainValue(instrumentation, () -> project(activity));
                if (current.equals(bound)) return bound;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("New offline project did not bind before the deadline");
    }

    private static void requireVisible(Control control) {
        CompilerMeasurementProbe.requireVisible(control);
    }

    private static void requireVisible(Button button) {
        Rect visible = new Rect();
        if (!button.isShown()
                || !button.getGlobalVisibleRect(visible)
                || visible.height() != button.getHeight()
                || visible.width() != button.getWidth())
            throw new AssertionError("Offline probe action is not fully visible");
    }

    private static void requireReady(
            Instrumentation instrumentation,
            Activity activity,
            SharedPreferences pins,
            SignedArtifactReceipt.Record record)
            throws Exception {
        onMain(
                instrumentation,
                () -> {
                    requireOwn(activity, pins);
                    button(activity, Labels.INSTALL);
                    VerifiedArtifact shown = (VerifiedArtifact) field(activity, "signedArtifact");
                    if (shown == null || !shown.sha256().equals(record.sha256))
                        throw new AssertionError(
                                "Visible Install action differs from the offline receipt");
                });
    }

    private static void awaitInstall(
            Instrumentation instrumentation, Activity activity, Context context, boolean confirmed)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!mainValue(
                instrumentation,
                () -> {
                    Control install = Ui.button(activity, Labels.INSTALL);
                    return install != null && install.isEnabled();
                })) {
            requireOffline(context, confirmed);
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Offline Install action did not recover");
            Thread.sleep(100);
        }
        requireOffline(context, confirmed);
    }

    private static Control button(Activity activity, String text) throws Exception {
        return Ui.button(activity, text);
    }

    private static ProjectIdentity project(Activity activity) throws Exception {
        return (ProjectIdentity) field(activity, "activeProject");
    }

    private static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    private interface Action {
        void run() throws Exception;
    }

    private interface Value<T> {
        T get() throws Exception;
    }

    private static void onMain(Instrumentation instrumentation, Action action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable error) {
                        failure.set(error);
                    }
                });
        if (failure.get() instanceof Exception exception) throw exception;
        if (failure.get() instanceof Error error) throw error;
    }

    private static <T> T mainValue(Instrumentation instrumentation, Value<T> value)
            throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        onMain(instrumentation, () -> result.set(value.get()));
        return result.get();
    }

    private static AccessibilityNodeInfo waitNode(
            Instrumentation instrumentation, Context target, String type, String text)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null
                    && target.getPackageName().equals(String.valueOf(root.getPackageName()))) {
                AccessibilityNodeInfo found = findNode(root, type, text);
                if (found != null) return found;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Offline project dialog is not visible");
    }

    private static AccessibilityNodeInfo findNode(
            AccessibilityNodeInfo node, String type, String text) {
        AccessibilityNodeInfo here = findNodeHere(node, type, text);
        return here != null || text == null ? here : Ui.find(node, type, text);
    }

    private static AccessibilityNodeInfo findNodeHere(
            AccessibilityNodeInfo node, String type, String text) {
        if (node == null) return null;
        if (type.equals(String.valueOf(node.getClassName()))
                && node.isVisibleToUser()
                && node.isEnabled()
                && (text == null
                        ? "Project name".equals(String.valueOf(node.getHintText()))
                        : text.equalsIgnoreCase(String.valueOf(node.getText())))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNodeHere(node.getChild(i), type, text);
            if (found != null) return found;
        }
        return null;
    }
}
