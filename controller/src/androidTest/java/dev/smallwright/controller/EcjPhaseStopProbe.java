package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Real native UI and worker, with a host signal from an independent actual ECJ observer. */
final class EcjPhaseStopProbe {
    private static final String PREFS = "ecj-phase-stop-fixture-v2";
    private static final String TITLE = "Actual ECJ Stop fixture";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase, String observationId) throws Exception {
        stage = "GUARD";
        Context context = inst.getTargetContext();
        if (!java.util.List.of("prepare", "restart", "recover", "ready").contains(phase))
            throw new AssertionError("Unknown phase");
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("root")
                    || observationId == null
                    || !observationId.matches("[0-9a-f]{32}"))
                throw new AssertionError("One-shot observation guard");
            save(
                    pins.edit()
                            .putString("root", UUID.randomUUID().toString().replace("-", ""))
                            .putString("observationId", observationId));
        } else if (!pins.getBoolean("stopped", false))
            throw new AssertionError("No complete stopped fixture");
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            stage = "OPEN";
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui);
            if ("prepare".equals(phase)) {
                stage = "CREATE_PROJECT";
                CompilerMeasurementProbe.createProject(inst, ui, TITLE);
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            if (project == null
                    || !TITLE.equals(project.title())
                    || !project.id().matches("app[0-9a-f]{32}")
                    || !"prepare".equals(phase)
                            && !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Wrong fixture project");
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("prepare".equals(phase)) {
                stage = "SOURCE";
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Not a pristine project");
                String before = source.readSource();
                int closing = before.lastIndexOf('}');
                if (closing < 0
                        || before.indexOf("\"Increment\"") < 0
                        || before.indexOf("\"Increment\"") != before.lastIndexOf("\"Increment\""))
                    throw new AssertionError("Default source differs");
                StringBuilder methods = new StringBuilder("\n");
                for (int n = 0; n < 1000; n++)
                    methods.append("private static int f")
                            .append(n)
                            .append("(int x){return x+")
                            .append(n)
                            .append(";}\n");
                String expected =
                        (before.substring(0, closing) + methods + before.substring(closing))
                                .replace("\"Increment\"", "\"ECJ phase add one\"");
                int bytes = expected.getBytes(StandardCharsets.UTF_8).length;
                if (bytes >= 64 * 1024) throw new AssertionError("Source exceeds controller bound");
                String sha = ActiveBuildInterruptionProbe.hash(expected);
                save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
                ActiveWorkerStopProbe.showTools(inst, ui);
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            TextBox editor = (TextBox) field(ui, "source");
                            editor.setText(expected);
                        });
                File signal = new File(ui.getCacheDir(), "ecj-observed.ack");
                if (signal.exists()) throw new AssertionError("Signal already exists");
                marker(
                        inst,
                        "ECJ_STOP_CONTROLLER_READY root="
                                + pins.getString("root", "")
                                + " observationId="
                                + observationId
                                + " controllerPid="
                                + android.os.Process.myPid());
                stage = "BUILD";
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                Object token = field(ui, "activeStop");
                Control stop = (Control) field(ui, "stop");
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(stop));
                inst.waitForIdleSync();
                stage = "WAIT_ECJ_SIGNAL";
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!signal.isFile()) {
                    if (!PhoneAlphaCounterProbe.mainValue(
                                    inst, () -> (boolean) field(ui, "buildInProgress"))
                            || System.nanoTime() >= deadline)
                        throw new AssertionError("Actual ECJ Stop timing missed");
                    Thread.sleep(2);
                }
                long[] stopClock = new long[2];
                stage = "VISIBLE_STOP";
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            if (field(ui, "activeStop") != token)
                                throw new AssertionError("Stop token changed");
                            stopClock[0] = SystemClock.elapsedRealtimeNanos();
                            PhoneAlphaCounterProbe.visibleClick(stop);
                            stopClock[1] = SystemClock.elapsedRealtimeNanos();
                        });
                java.lang.reflect.Field requested = token.getClass().getDeclaredField("requested");
                requested.setAccessible(true);
                if (!requested.getBoolean(token))
                    throw new AssertionError("Actual Stop was not requested");
                marker(
                        inst,
                        "ECJ_STOP_APPLIED "
                                + new JSONObject()
                                        .put("observationId", observationId)
                                        .put("stopStartNanos", stopClock[0])
                                        .put("stopEndNanos", stopClock[1]));
                stage = "LATE_SETTLE";
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
                ActiveWorkerStopProbe.requireStopped(inst, ui, project, source, 1, sha);
                requireNoExport(inst, ui);
                File late = new File(project.directory(), "artifacts/app-v1.apk");
                ArtifactValidator.validateUnsigned(ui, late, 1, project.packageName());
                if (!new ApkVerifier.Builder(late).build().verify().isVerified())
                    throw new AssertionError("Late actual APK invalid");
                String signer = new ProjectSigner(project).verifyExisting(ui, late);
                String lateSha = VerifiedArtifact.capture(late).sha256();
                save(pins.edit().putBoolean("stopped", true).putString("signerSha256", signer));
                return "ECJ Stop prepare PASS "
                        + new JSONObject()
                                .put("root", pins.getString("root", ""))
                                .put("project", project.id())
                                .put("package", project.packageName())
                                .put("sourceSha256", sha)
                                .put("sourceBytes", bytes)
                                .put("lateApkSha256", lateSha)
                                .put("signerSha256", signer)
                                .put("stopStartNanos", stopClock[0])
                                .put("stopEndNanos", stopClock[1])
                        + "\n";
            }
            ((java.util.concurrent.ExecutorService) field(ui, "executor"))
                    .submit(() -> {})
                    .get(10, TimeUnit.SECONDS);
            inst.waitForIdleSync();
            String sha = pins.getString("sourceSha256", "");
            if ("restart".equals(phase) || "recover".equals(phase)) {
                stage = "RESTORED_STOPPED";
                ActiveWorkerStopProbe.requireStopped(inst, ui, project, source, 1, sha);
                requireNoExport(inst, ui);
                if ("restart".equals(phase))
                    return "ECJ Stop restart PASS exact source1; no auto-build/receipt/Install/export\n";
                stage = "FRESH_BUILD";
                ActiveWorkerStopProbe.showTools(inst, ui);
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
            } else if (!pins.getBoolean("recovered", false))
                throw new AssertionError("No completed recovery");
            stage = "READY_VERIFY";
            VerifiedArtifact ready =
                    ActiveWorkerStopProbe.requireReady(inst, ui, project, source, sha);
            String signer = new ProjectSigner(project).verifyExisting(ui, ready.file());
            if (!signer.equals(pins.getString("signerSha256", "")))
                throw new AssertionError("Project signer changed");
            if ("ready".equals(phase) && !ready.sha256().equals(pins.getString("readySha256", "")))
                throw new AssertionError("Ready artifact changed");
            if ("recover".equals(phase))
                save(
                        pins.edit()
                                .putBoolean("recovered", true)
                                .putString("readySha256", ready.sha256()));
            return "ECJ Stop "
                    + phase
                    + " PASS exact source2/version1 receipt/APK/signer; sha256="
                    + ready.sha256()
                    + "; no model/install\n";
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

    private static void requireNoExport(Instrumentation inst, Activity ui) throws Exception {
        if (PhoneAlphaCounterProbe.mainValue(
                inst, () -> ((Control) field(ui, "exportApk")).isEnabled()))
            throw new AssertionError("Export readiness escaped Stop");
    }

    private static Object field(Activity ui, String name) throws Exception {
        return ActiveBuildInterruptionProbe.field(ui, name);
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Pins not persisted");
    }

    private static void marker(Instrumentation inst, String value) {
        Bundle result = new Bundle();
        result.putString("stream", value + "\n");
        inst.sendStatus(0, result);
    }
}
