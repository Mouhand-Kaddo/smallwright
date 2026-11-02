package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Physical keyguard/noninteractive transition during real admitted input; no model. */
final class ScreenLockRecoveryProbe {
    private static final String PREFS = "screen-lock-recovery-fixture-v1";
    private static final String TITLE = "Screen lock recovery fixture";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of("prepare", "restart", "recover", "ready").contains(phase))
            throw new IllegalArgumentException("Unknown screen lock phase");
        Context context = inst.getTargetContext();
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        if (keyguard.isKeyguardLocked() || !power.isInteractive())
            throw new AssertionError("User unlock required before test UI");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("root")) throw new AssertionError("Fixture already reserved");
            save(pins.edit().putString("root", UUID.randomUUID().toString().replace("-", "")));
        } else if (!pins.getBoolean("stopped", false))
            throw new AssertionError("No verified lock fixture");
        if ("restart".equals(phase) && pins.getBoolean("retryAttempted", false)
                || "recover".equals(phase)
                        && (!pins.getBoolean("restarted", false)
                                || pins.getBoolean("retryAttempted", false))
                || "ready".equals(phase) && !pins.getBoolean("ready", false))
            throw new AssertionError("Phase unavailable");
        stage = "OPEN";
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ActiveWorkerStopProbe.PausedTransfer relay = null;
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
                    throw new AssertionError("Title input failed");
                PhoneAlphaCounterProbe.click(inst, ui, "Create");
                ActiveWorkerStopProbe.awaitProject(inst, ui, TITLE);
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            if (project == null
                    || !project.id().matches("app[0-9a-f]{32}")
                    || !TITLE.equals(project.title())
                    || !"prepare".equals(phase)
                            && !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Selected project differs from fixture");
            ProjectSourceStore source = new ProjectSourceStore(project);
            PhoneAlphaCounterProbe.onMain(
                    inst,
                    () -> {
                        TextBox editor =
                                (TextBox)
                                        field(ui, "source"); // Display assistance only, full source
                        // preserved.
                    });
            if ("prepare".equals(phase)) {
                stage = "EXACT_SOURCE";
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Fixture is not pristine");
                StringBuilder text =
                        new StringBuilder(
                                        source.readSource()
                                                .replace("Increment", "AfterScreenLockRecovery"))
                                .append("\n/*\n");
                Random random = new Random(9191);
                for (int i = 0; i < 24000; i++) {
                    text.append((char) ('A' + random.nextInt(26)));
                    if (i % 80 == 79) text.append('\n');
                }
                String expected = text.append("*/\n").toString();
                if (expected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64 * 1024)
                    throw new AssertionError("Source exceeds bound");
                String sha = ActiveBuildInterruptionProbe.hash(expected);
                save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
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
                            ((TextBox) field(ui, "source")).setText(expected);
                        });
                ActiveWorkerStopProbe.showTools(inst, ui);
                stage = "ADMISSION";
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                if (!held.draining.await(5, TimeUnit.SECONDS)
                        || held.finished.getCount() == 0
                        || held.calls != 1
                        || !sha.equals(held.admittedMainSha256))
                    throw new AssertionError("No exact unfinished admitted input");
                try {
                    worker.toolchainProbe();
                    throw new AssertionError("Real worker is not busy");
                } catch (IllegalStateException busy) {
                    if (!"ERROR: Worker busy".equals(busy.getMessage())) throw busy;
                }
                Object token =
                        PhoneAlphaCounterProbe.mainValue(inst, () -> field(ui, "activeStop"));
                Field requested = token.getClass().getDeclaredField("requested");
                requested.setAccessible(true);
                if (requested.getBoolean(token)
                        || keyguard.isKeyguardLocked()
                        || !power.isInteractive())
                    throw new AssertionError("Lock baseline changed before ARMED");
                stage = "AWAIT_REAL_LOCK_AND_LIFECYCLE_STOP";
                marker(
                        inst,
                        "SCREEN_LOCK_ARMED root="
                                + pins.getString("root", "")
                                + " project="
                                + project.id()
                                + " sourceSha256="
                                + sha
                                + " controllerPid="
                                + android.os.Process.myPid());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                while (!(keyguard.isKeyguardLocked()
                        && !power.isInteractive()
                        && requested.getBoolean(token))) {
                    if (System.nanoTime() >= deadline || held.finished.getCount() == 0)
                        throw new AssertionError("Real lock did not cancel the held build");
                    Thread.sleep(20);
                }
                marker(inst, "SCREEN_LOCK_OBSERVED keyguard=true interactive=false stopped=true");
                stage = "SETTLE_CANCELLED_NATIVE_RESULT";
                held.release.countDown();
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
                if (!"OK".equals(held.result) || held.calls != 1 || held.failure != null)
                    throw new AssertionError("Late actual native build did not settle");
                File late = new File(project.directory(), "artifacts/app-v1.apk");
                ArtifactValidator.validateUnsigned(ui, late, 1, project.packageName());
                if (!new ApkVerifier.Builder(late).build().verify().isVerified())
                    throw new AssertionError("Late APK signature invalid");
                requireStopped(inst, ui, project, source, sha);
                if (!PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                ((TextBox) field(ui, "status"))
                                        .getText()
                                        .toString()
                                        .contains("no new APK is ready")))
                    throw new AssertionError("Locked cancellation status missing");
                if (!keyguard.isKeyguardLocked() || power.isInteractive())
                    throw new AssertionError("Phone unlocked before locked-state outcome");
                save(
                        pins.edit()
                                .putBoolean("stopped", true)
                                .putString("lateSha256", VerifiedArtifact.capture(late).sha256()));
                return "Screen lock recovery phase=prepare: actual keyguard/noninteractive/lifecycle Stop; late native build/sign settled while locked; source1 retained, no receipt/Install/export; no model/installation\n";
            }
            String sha = pins.getString("sourceSha256", "");
            if (!"ready".equals(phase)) {
                stage = "VERIFY_UNLOCKED_FAILED_STATE";
                requireStopped(inst, ui, project, source, sha);
                File late = new File(project.directory(), "artifacts/app-v1.apk");
                if (!VerifiedArtifact.capture(late)
                        .sha256()
                        .equals(pins.getString("lateSha256", "")))
                    throw new AssertionError("Late artifact changed on reopen");
                if ("restart".equals(phase)) {
                    save(pins.edit().putBoolean("restarted", true));
                    return "Screen lock recovery phase=restart: user-unlocked fresh process; source1/late artifact retained, no automatic build or receipt/Install/export\n";
                }
                save(pins.edit().putBoolean("retryAttempted", true));
                stage = "VISIBLE_FRESH_BUILD";
                ActiveWorkerStopProbe.showTools(inst, ui);
                ActiveWorkerStopProbe.visible(inst, ui, Labels.BUILD_SOURCE);
                ActiveWorkerStopProbe.awaitIdle(inst, ui);
            }
            stage = "VERIFY_READY";
            ((java.util.concurrent.ExecutorService) field(ui, "executor"))
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
                    inst, () -> install.isShown() && install.isEnabled()))
                throw new AssertionError("Install is not shown");
            if ("ready".equals(phase)) {
                if (!artifact.sha256().equals(pins.getString("readySha256", ""))
                        || !signer.equals(pins.getString("signerSha256", "")))
                    throw new AssertionError("Ready APK/signer changed");
            } else
                save(
                        pins.edit()
                                .putBoolean("ready", true)
                                .putString("readySha256", artifact.sha256())
                                .putString("signerSha256", signer));
            return "Screen lock recovery phase="
                    + phase
                    + ": unchanged content/source2/version1 actual native signature/receipt/visible Install; sha256="
                    + artifact.sha256()
                    + " signerSha256="
                    + signer
                    + "; no model/installation\n";
        } finally {
            if (relay != null)
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            Control stop = (Control) field(ui, "stop");
                            if (stop.isEnabled())
                                Ui.click(stop); // Failure cleanup before EOF only.
                        });
            if (relay != null) relay.release.countDown();
            PhoneAlphaCounterProbe.onMain(inst, ui::finish);
        }
    }

    private static void requireStopped(
            Instrumentation inst,
            Activity ui,
            ProjectIdentity project,
            ProjectSourceStore source,
            String sha)
            throws Exception {
        ActiveWorkerStopProbe.requireStopped(inst, ui, project, source, 1, sha);
        if (!PhoneAlphaCounterProbe.mainValue(
                inst,
                () ->
                        ((TextBox) field(ui, "source")).isEnabled()
                                && !((Control) field(ui, "exportApk")).isEnabled()
                                && ((TextBox) field(ui, "source"))
                                        .getText()
                                        .toString()
                                        .equals(source.readSource())))
            throw new AssertionError("Editor/export invalid after cancellation");
        for (File file : ui.getCacheDir().listFiles())
            if (file.getName().startsWith("snapshot-"))
                throw new AssertionError("Snapshot partial remains");
        for (File file : new File(project.directory(), "artifacts").listFiles())
            if (file.getName().startsWith("unsigned-") || file.getName().endsWith(".partial"))
                throw new AssertionError("Artifact partial remains");
    }

    private static Object field(Activity ui, String name) throws Exception {
        return ActiveBuildInterruptionProbe.field(ui, name);
    }

    private static void marker(Instrumentation inst, String text) {
        Bundle result = new Bundle();
        result.putString("stream", text + "\n");
        inst.sendStatus(0, result);
    }

    private static void save(SharedPreferences.Editor pins) {
        if (!pins.commit()) throw new AssertionError("Could not save fixture pins");
    }
}
