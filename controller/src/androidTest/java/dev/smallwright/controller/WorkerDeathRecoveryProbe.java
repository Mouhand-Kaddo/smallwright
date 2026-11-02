package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.widget.EditText;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Actual admitted worker death, real native Build UI and retained-source retry; no model. */
final class WorkerDeathRecoveryProbe {
    private static final String PREFS = "worker-death-recovery-fixture-v2";
    private static final String TITLE = "Worker Binder death fixture";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of("prepare", "restart", "recover", "ready").contains(phase))
            throw new IllegalArgumentException("Unknown worker death phase");
        SharedPreferences pins =
                inst.getTargetContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("root")) throw new AssertionError("Fixture already reserved");
            save(pins.edit().putString("root", UUID.randomUUID().toString().replace("-", "")));
        } else if (!pins.getBoolean("dead", false))
            throw new AssertionError("No verified death fixture");
        if ("recover".equals(phase)
                && (!pins.getBoolean("restarted", false)
                        || pins.getBoolean("retryAttempted", false)))
            throw new AssertionError("Retry unavailable");
        if ("restart".equals(phase) && pins.getBoolean("retryAttempted", false))
            throw new AssertionError("Failed-state restart unavailable after retry");
        if ("ready".equals(phase) && !pins.getBoolean("ready", false))
            throw new AssertionError("No ready artifact");
        stage = "OPEN";
        Activity ui =
                inst.startActivitySync(
                        new Intent(inst.getTargetContext(), ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ActiveWorkerStopProbe.PausedTransfer relay = null;
        IBinder binder = null;
        CountDownLatch died = new CountDownLatch(1);
        IBinder.DeathRecipient death = died::countDown;
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            IBuildService worker = ActiveWorkerStopProbe.awaitWorker(inst, ui);
            if ("prepare".equals(phase)) {
                stage = "CREATE_PROJECT";
                Ui.openMenu(inst, ui);
                ActiveWorkerStopProbe.visible(inst, ui, Labels.NEW_PROJECT);
                android.view.accessibility.AccessibilityNodeInfo input =
                        PhoneAlphaCounterProbe.waitNode(inst, ui, "android.widget.EditText", null);
                Bundle title = new Bundle();
                title.putCharSequence(
                        android.view.accessibility.AccessibilityNodeInfo
                                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        TITLE);
                if (!input.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, title))
                    throw new AssertionError("Could not enter project title");
                PhoneAlphaCounterProbe.click(inst, ui, "Create");
                ActiveWorkerStopProbe.awaitProject(inst, ui, TITLE);
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () ->
                                    (ProjectIdentity)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "activeProject"));
            if (project == null
                    || !project.id().matches("app[0-9a-f]{32}")
                    || !TITLE.equals(project.title())
                    || (!"prepare".equals(phase)
                            && !project.id().equals(pins.getString("project", ""))))
                throw new AssertionError("Selected project differs from fixture");
            ProjectSourceStore source = new ProjectSourceStore(project);
            PhoneAlphaCounterProbe.onMain(
                    inst,
                    () -> {
                        TextBox editor = (TextBox) ActiveBuildInterruptionProbe.field(ui, "source");
                        // Test display assistance only; full synthetic source remains unchanged.
                    });
            if ("prepare".equals(phase)) {
                stage = "PREPARE_EXACT_SOURCE";
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Project source was not pristine");
                StringBuilder edit =
                        new StringBuilder(
                                        source.readSource()
                                                .replace("Increment", "AfterWorkerDeathRecovery"))
                                .append("\n/*\n");
                Random random = new Random(8181);
                for (int i = 0; i < 24000; i++) {
                    edit.append((char) ('A' + random.nextInt(26)));
                    if (i % 80 == 79) edit.append('\n');
                }
                String expected = edit.append("*/\n").toString();
                if (expected.getBytes(StandardCharsets.UTF_8).length > 64 * 1024)
                    throw new AssertionError("Fixture exceeds admitted source bound");
                String sha = ActiveBuildInterruptionProbe.hash(expected);
                save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
                binder = worker.asBinder();
                binder.linkToDeath(death, 0);
                relay =
                        new ActiveWorkerStopProbe.PausedTransfer(
                                worker, project.packageName(), sha, 15);
                ActiveWorkerStopProbe.PausedTransfer held = relay;
                Field target = MainActivity.class.getDeclaredField("worker");
                target.setAccessible(true);
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            target.set(ui, held);
                            ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source"))
                                    .setText(expected);
                        });
                ActiveWorkerStopProbe.showTools(inst, ui);
                stage = "START_AND_OBSERVE_ADMISSION";
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                stage = "AWAIT_EXACT_DRAIN";
                if (!held.draining.await(5, TimeUnit.SECONDS)
                        || held.finished.getCount() == 0
                        || held.calls != 1
                        || !sha.equals(held.admittedMainSha256))
                    throw new AssertionError("No exact active worker read progress");
                stage = "REQUIRE_ACTUAL_BUSY";
                try {
                    worker.toolchainProbe();
                    throw new AssertionError("Real worker is not busy");
                } catch (IllegalStateException busy) {
                    if (!"ERROR: Worker busy".equals(busy.getMessage())) throw busy;
                }
                stage = "VERIFY_ADMITTED_EDITOR";
                // The live build lease intentionally rejects other ProjectStore reads.
                // Exact leased source was checked by the relay; disk identity is checked after
                // death.
                if (!PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                (int) ActiveBuildInterruptionProbe.field(ui, "editorRevision") == 1
                                        && sha.equals(
                                                ActiveBuildInterruptionProbe.hash(
                                                        ((EditText)
                                                                        ActiveBuildInterruptionProbe
                                                                                .field(
                                                                                        ui,
                                                                                        "source"))
                                                                .getText()
                                                                .toString()))))
                    throw new AssertionError("Admitted snapshot source changed");
                stage = "ARMED_WORKER_TERMINATION";
                Bundle marker = new Bundle();
                marker.putString(
                        "stream",
                        "WORKER_DEATH_ARMED root="
                                + pins.getString("root", "")
                                + " project="
                                + project.id()
                                + " sourceSha256="
                                + sha
                                + " controllerPid="
                                + android.os.Process.myPid()
                                + "\n");
                inst.sendStatus(0, marker);
                if (!died.await(10, TimeUnit.SECONDS)
                        || binder.isBinderAlive()
                        || !held.finished.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("No actual old Binder death during held request");
                stage = "SETTLE_GENUINE_DEAD_OBJECT_FAILURE";
                held.release.countDown();
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
                Throwable cause = held.failure;
                boolean deadObject = false;
                for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause())
                    if (cause instanceof DeadObjectException) deadObject = true;
                if (!deadObject || held.calls != 1 || held.result != null)
                    throw new AssertionError(
                            "Held Binder call did not fail with actual DeadObjectException");
                requireFailed(inst, ui, project, source, sha);
                save(pins.edit().putBoolean("dead", true));
                return summary(pins)
                        + " phase=prepare: actual admitted input/old Binder death/DeadObjectException; controller stayed alive; source1 retained, partials removed, no APK/receipt/Install/export; editor usable; no model/installation\n";
            }
            String sha = pins.getString("sourceSha256", "");
            if ("restart".equals(phase) || "recover".equals(phase)) {
                stage = "VERIFY_FAILED_RESTART";
                requireFailed(inst, ui, project, source, sha);
                if ("restart".equals(phase)) {
                    save(pins.edit().putBoolean("restarted", true));
                    return summary(pins)
                            + " phase=restart: source1 retained; newly bound worker available; no automatic build, receipt or readiness; no model/installation\n";
                }
                save(pins.edit().putBoolean("retryAttempted", true));
                stage = "VISIBLE_FRESH_BUILD";
                ActiveWorkerStopProbe.showTools(inst, ui);
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
            }
            stage = "VERIFY_NATIVE_READY";
            // Wait for startup receipt restoration, serialized on the actual controller executor.
            inst.waitForIdleSync();
            ((java.util.concurrent.ExecutorService)
                            ActiveBuildInterruptionProbe.field(ui, "executor"))
                    .submit(() -> {})
                    .get(10, TimeUnit.SECONDS);
            inst.waitForIdleSync();
            VerifiedArtifact artifact =
                    ActiveWorkerStopProbe.requireReady(inst, ui, project, source, sha);
            String signer = new ProjectSigner(project).verifyExisting(ui, artifact.file());
            Control install = Ui.button(ui, Labels.INSTALL);
            PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
            inst.waitForIdleSync();
            if (!PhoneAlphaCounterProbe.mainValue(
                    inst,
                    () -> {
                        Rect visible = new Rect();
                        return install.isEnabled() && install.isShown();
                    })) throw new AssertionError("Install readiness is not fully visible");
            if ("ready".equals(phase)) {
                if (!artifact.sha256().equals(pins.getString("readySha256", ""))
                        || !signer.equals(pins.getString("signerSha256", "")))
                    throw new AssertionError("Ready restart changed exact APK/signer");
            } else
                save(
                        pins.edit()
                                .putBoolean("ready", true)
                                .putString("readySha256", artifact.sha256())
                                .putString("signerSha256", signer));
            return summary(pins)
                    + " phase="
                    + phase
                    + ": source2/version1 real native APK/signature/receipt; exact saved content; visible Install, no installation/model; sha256="
                    + artifact.sha256()
                    + " signerSha256="
                    + signer
                    + "\n";
        } finally {
            // Failure cleanup must cancel completion before permitting a still-live compiler EOF.
            // This is cleanup assistance, not a visible Stop test result.
            if (relay != null)
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            Control stop = (Control) ActiveBuildInterruptionProbe.field(ui, "stop");
                            if (stop.isEnabled()) Ui.click(stop);
                        });
            if (relay != null) relay.release.countDown();
            if (binder != null) binder.unlinkToDeath(death, 0);
            PhoneAlphaCounterProbe.onMain(inst, ui::finish);
        }
    }

    private static void requireFailed(
            Instrumentation inst,
            Activity ui,
            ProjectIdentity project,
            ProjectSourceStore source,
            String sha)
            throws Exception {
        ActiveWorkerStopProbe.requireStopped(inst, ui, project, source, 1, sha);
        File artifacts = new File(project.directory(), "artifacts");
        if (artifacts.exists())
            for (File file : artifacts.listFiles())
                if (file.getName().endsWith(".apk")
                        || file.getName().startsWith("unsigned-")
                        || file.getName().endsWith(".partial"))
                    throw new AssertionError("Worker death left an APK/partial");
        for (File file : ui.getCacheDir().listFiles())
            if (file.getName().startsWith("snapshot-"))
                throw new AssertionError("Worker death left a snapshot temporary");
        if (!PhoneAlphaCounterProbe.mainValue(
                inst,
                () ->
                        ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source")).isEnabled()
                                && ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source"))
                                        .getText()
                                        .toString()
                                        .equals(source.readSource())
                                && !((Control) ActiveBuildInterruptionProbe.field(ui, "exportApk"))
                                        .isEnabled()))
            throw new AssertionError("Worker death left unusable editor or export readiness");
    }

    private static String summary(SharedPreferences pins) {
        return "Worker death recovery root="
                + pins.getString("root", "")
                + " project="
                + pins.getString("project", "")
                + " sourceSha256="
                + pins.getString("sourceSha256", "");
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Fixture metadata could not be saved");
    }
}
