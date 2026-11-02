package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;

/** Measures two normal native Build UI runs; acknowledgments occur outside timed work. */
final class CompilerMeasurementProbe {
    private static final String PREFS = "compiler-measurement-fixture-v9";
    private static final String TITLE = "Compiler measurement fixture";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of("measure", "ready").contains(phase))
            throw new IllegalArgumentException("Unknown measurement phase");
        Context context = inst.getTargetContext();
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required before measurement UI");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("measure".equals(phase)) {
            if (pins.contains("root")) throw new AssertionError("Measurement already reserved");
            save(pins.edit().putString("root", UUID.randomUUID().toString().replace("-", "")));
        } else if (!pins.getBoolean("measured", false))
            throw new AssertionError("No complete measured fixture");
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui); // Handshake only, no compiler probe.
            if ("measure".equals(phase)) {
                stage = "CREATE_PROJECT";
                marker(
                        inst,
                        "COMPILER_CREATE_BASELINE "
                                + PhoneAlphaCounterProbe.mainValue(
                                        inst,
                                        () ->
                                                "binding="
                                                        + field(ui, "projectBindingInProgress")
                                                        + " blocked="
                                                        + field(ui, "startupBlocked")
                                                        + " pendingRecovery="
                                                        + (field(ui, "pendingRecovery") != null)
                                                        + " editorRevision="
                                                        + field(ui, "editorRevision")));
                createProject(inst, ui, TITLE);
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            if (project == null
                    || !project.id().matches("app[0-9a-f]{32}")
                    || !TITLE.equals(project.title())
                    || !"measure".equals(phase)
                            && !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Selected project differs from fixture");
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("ready".equals(phase)) {
                ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                        .submit(() -> {})
                        .get(10, TimeUnit.SECONDS);
                inst.waitForIdleSync();
                SignedArtifactReceipt.Record receipt =
                        requireReady(
                                inst, ui, project, source, 2, pins.getString("sourceSha256", ""));
                if (!receipt.sha256.equals(pins.getString("readySha256", ""))
                        || !receipt.signerFingerprint.equals(pins.getString("signerSha256", "")))
                    throw new AssertionError("Ready restart changed exact artifact/signer");
                return "Compiler measurement ready: exact source2/version2 receipt/APK/signer restored; no rebuild/model/installation\n";
            }
            stage = "PREPARE_SOURCE";
            if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                throw new AssertionError("Measurement source was not pristine");
            String before = source.readSource();
            if (before.indexOf("\"Increment\"") < 0
                    || before.indexOf("\"Increment\"") != before.lastIndexOf("\"Increment\""))
                throw new AssertionError("Default label differs");
            String expected = before.replace("\"Increment\"", "\"Measured add one\"");
            String sha = ActiveBuildInterruptionProbe.hash(expected);
            save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
            ActiveWorkerStopProbe.showTools(inst, ui);
            PhoneAlphaCounterProbe.onMain(
                    inst, () -> ((TextBox) field(ui, "source")).setText(expected));
            inst.waitForIdleSync();
            JSONArray builds = new JSONArray();
            String signer = null;
            for (int version = 1; version <= 2; version++) {
                stage = "BEFORE_BUILD_" + version;
                gate(inst, ui, pins, "before" + version);
                AtomicLong start = new AtomicLong(), end = new AtomicLong();
                Control build = Ui.button(ui, Labels.BUILD_SOURCE);
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(build));
                inst.waitForIdleSync();
                stage = "REAL_BUILD_" + version;
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            requireVisible(build);
                            start.set(SystemClock.elapsedRealtimeNanos());
                            Ui.click(build);
                            if (!(boolean) field(ui, "buildInProgress"))
                                throw new AssertionError("Visible Build did not start");
                        });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
                while (PhoneAlphaCounterProbe.mainValue(
                        inst, () -> (boolean) field(ui, "buildInProgress"))) {
                    if (System.nanoTime() >= deadline)
                        throw new AssertionError("Build observation deadline");
                    Thread.sleep(10);
                }
                Control install = Ui.button(ui, Labels.INSTALL);
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
                inst.waitForIdleSync();
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            requireVisible(install);
                            if (field(ui, "signedArtifact") == null)
                                throw new AssertionError("No verified readiness");
                            end.set(SystemClock.elapsedRealtimeNanos());
                        });
                stage = "INDEPENDENT_VERIFY_" + version;
                SignedArtifactReceipt.Record receipt =
                        requireReady(inst, ui, project, source, version, sha);
                if (signer != null && !signer.equals(receipt.signerFingerprint))
                    throw new AssertionError("Second build changed project signer");
                signer = receipt.signerFingerprint;
                long sourceBytes = 0;
                for (byte[] bytes : source.readRecoverySources(version).values())
                    sourceBytes += bytes.length;
                long artifactBytes = 0;
                for (File file : new File(project.directory(), "artifacts").listFiles())
                    artifactBytes += file.length();
                JSONObject result =
                        new JSONObject()
                                .put("version", version)
                                .put("sourceRevision", version)
                                .put("startElapsedRealtimeNanos", start.get())
                                .put("endElapsedRealtimeNanos", end.get())
                                .put(
                                        "observedClickToVisibleReadyMs",
                                        TimeUnit.NANOSECONDS.toMillis(end.get() - start.get()))
                                .put("sourceSha256", sha)
                                .put("apkSha256", receipt.sha256)
                                .put("signerSha256", receipt.signerFingerprint)
                                .put("apkBytes", receipt.apk.length())
                                .put("sourceLogicalBytes", sourceBytes)
                                .put("artifactDirectoryLogicalBytes", artifactBytes)
                                .put(
                                        "filesystemAvailableBytes",
                                        new StatFs(ui.getFilesDir().getAbsolutePath())
                                                .getAvailableBytes())
                                .put(
                                        "thermalStatus",
                                        ui.getSystemService(PowerManager.class)
                                                .getCurrentThermalStatus());
                builds.put(result);
                marker(inst, "COMPILER_BUILD_RESULT " + result);
                stage = "AFTER_BUILD_" + version;
                gate(inst, ui, pins, "after" + version);
            }
            JSONObject output =
                    new JSONObject()
                            .put("root", pins.getString("root", ""))
                            .put("project", project.id())
                            .put("package", project.packageName())
                            .put("controllerUid", android.os.Process.myUid())
                            .put(
                                    "workerUid",
                                    context.getPackageManager()
                                            .getApplicationInfo("dev.smallwright.worker", 0)
                                            .uid)
                            .put("builds", builds);
            if (output.getInt("workerUid") == output.getInt("controllerUid"))
                throw new AssertionError("Worker shares controller UID");
            JSONObject finalBuild = builds.getJSONObject(1);
            save(
                    pins.edit()
                            .putBoolean("measured", true)
                            .putString("readySha256", finalBuild.getString("apkSha256"))
                            .putString("signerSha256", finalBuild.getString("signerSha256")));
            return "Compiler measurement PASS " + output + "\n";
        } finally {
            PhoneAlphaCounterProbe.onMain(
                    inst,
                    () -> {
                        Control stop = (Control) field(ui, "stop");
                        if (stop.isEnabled()) Ui.click(stop);
                        ui.finish();
                    });
        }
    }

    static void createProject(Instrumentation inst, Activity ui, String title) throws Exception {
        Ui.openMenu(inst, ui);
        Ui.tap(inst, ui, Labels.NEW_PROJECT);
        stage = "TITLE_INPUT";
        marker(inst, "COMPILER_CREATE_STAGE TITLE_INPUT");
        android.view.View dialog = awaitOwnedProjectDialog(inst, ui);
        PhoneAlphaCounterProbe.onMain(
                inst,
                () -> {
                    if (!dialog.hasWindowFocus()) throw new AssertionError("Dialog lost focus");
                    EditText input = projectNameInput(dialog, ui);
                    requireVisibleView(input);
                    input.setText(title);
                });
        stage = "CREATE_CLICK";
        PhoneAlphaCounterProbe.onMain(
                inst,
                () -> {
                    if (!dialog.hasWindowFocus()) throw new AssertionError("Dialog lost focus");
                    EditText input = projectNameInput(dialog, ui);
                    requireVisibleView(input);
                    if (!title.contentEquals(input.getText()))
                        throw new AssertionError("Title differs");
                    Button create = findButton(dialog, "Create");
                    requireVisible(create);
                    if (!create.performClick())
                        throw new AssertionError("Owned Create action failed");
                });
        stage = "AWAIT_CREATED_PROJECT";
        marker(inst, "COMPILER_CREATE_STAGE AWAIT_CREATED_PROJECT");
        ActiveWorkerStopProbe.awaitProject(inst, ui, title);
    }

    static SignedArtifactReceipt.Record requireReady(
            Instrumentation inst,
            Activity ui,
            ProjectIdentity project,
            ProjectSourceStore source,
            int version,
            String sha)
            throws Exception {
        if (source.revision() != version
                || !sha.equals(ActiveBuildInterruptionProbe.hash(source.readSource())))
            throw new AssertionError("Source differs from measured build");
        VerifiedArtifact artifact =
                PhoneAlphaCounterProbe.mainValue(
                        inst, () -> (VerifiedArtifact) field(ui, "signedArtifact"));
        if (artifact == null) throw new AssertionError("No actual signed artifact");
        artifact.verifyUnchanged();
        ArtifactValidator.validateUnsigned(ui, artifact.file(), version, project.packageName());
        if (!new ApkVerifier.Builder(artifact.file()).build().verify().isVerified())
            throw new AssertionError("APK signature invalid");
        SignedArtifactReceipt.Record receipt =
                SignedArtifactReceipt.read(
                        project.directory(), project.id(), project.packageName(), version);
        if (receipt.versionCode != version
                || !receipt.sha256.equals(artifact.sha256())
                || !receipt.signerFingerprint.equals(
                        new ProjectSigner(project).verifyExisting(ui, artifact.file())))
            throw new AssertionError("Receipt differs from actual native artifact");
        PhoneAlphaCounterProbe.onMain(
                inst,
                () -> {
                    Control install = Ui.button(ui, Labels.INSTALL);
                    PhoneAlphaCounterProbe.reveal(install);
                    requireVisible(install);
                });
        return receipt;
    }

    private static void gate(
            Instrumentation inst, Activity ui, SharedPreferences pins, String label)
            throws Exception {
        File acknowledgment = new File(ui.getCacheDir(), "compiler-measure-" + label + ".ack");
        if (acknowledgment.exists())
            throw new AssertionError("Measurement gate was previously used");
        marker(
                inst,
                "COMPILER_MEASURE_GATE root="
                        + pins.getString("root", "")
                        + " phase="
                        + label
                        + " controllerPid="
                        + android.os.Process.myPid());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!acknowledgment.isFile()) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Host observation gate timed out");
            Thread.sleep(20);
        }
    }

    static void requireVisible(Control control) {
        if (!control.isShown() || !control.isEnabled())
            throw new AssertionError("Action not shown: " + control.getLabel());
    }

    static void requireVisible(Button button) {
        Rect bounds = new Rect();
        if (button == null
                || !button.isEnabled()
                || !button.isShown()
                || !button.getGlobalVisibleRect(bounds)
                || bounds.width() != button.getWidth()
                || bounds.height() != button.getHeight())
            throw new AssertionError("Expected visible enabled control");
    }

    private static android.view.View awaitOwnedProjectDialog(Instrumentation inst, Activity ui)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            android.view.View found =
                    PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () -> {
                                android.view.View selected = null;
                                for (android.view.View root :
                                        android.view.inspector.WindowInspector
                                                .getGlobalWindowViews()) {
                                    if (!root.hasWindowFocus()
                                            || projectNameInput(root, ui) == null) continue;
                                    if (selected != null)
                                        throw new AssertionError("Multiple fixture dialogs");
                                    selected = root;
                                }
                                return selected;
                            });
            if (found != null) return found;
            Thread.sleep(25);
        }
        marker(
                inst,
                "COMPILER_DIALOG_FOCUS_MISSING locked="
                        + ui.getSystemService(KeyguardManager.class).isKeyguardLocked()
                        + " interactive="
                        + ui.getSystemService(PowerManager.class).isInteractive());
        throw new AssertionError("No focused owned project dialog");
    }

    private static EditText projectNameInput(android.view.View view, Activity ui) {
        if (view instanceof EditText input
                && input.getContext() == ui
                && "Project name".equals(String.valueOf(input.getHint()))
                && input.isShown()
                && input.isEnabled()) return input;
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = projectNameInput(group.getChildAt(i), ui);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void requireVisibleView(android.view.View view) {
        Rect bounds = new Rect();
        if (view == null
                || !view.isEnabled()
                || !view.isShown()
                || !view.getGlobalVisibleRect(bounds)
                || bounds.width() != view.getWidth()
                || bounds.height() != view.getHeight())
            throw new AssertionError("Expected visible enabled input");
    }

    private static Object field(Activity ui, String name) throws Exception {
        return ActiveBuildInterruptionProbe.field(ui, name);
    }

    private static void save(SharedPreferences.Editor pins) {
        if (!pins.commit()) throw new AssertionError("Fixture pins not saved");
    }

    private static void marker(Instrumentation inst, String text) {
        Bundle result = new Bundle();
        result.putString("stream", text + "\n");
        inst.sendStatus(0, result);
    }

    private static Button findButton(View root, String text) {
        if (root instanceof Button button && text.contentEquals(button.getText())) return button;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        return null;
    }
}
