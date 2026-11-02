package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Native build plus real filesystem receipt failure; bounded obstruction, never disk filling. */
final class ReceiptWriteRecoveryProbe {
    private static final String PREFS = "native-receipt-failure-fixture-v2";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of(
                        "prepare", "restart", "recover", "ready", "armFailureStop", "armReadyStop")
                .contains(phase))
            throw new IllegalArgumentException("Unknown receipt recovery phase");
        SharedPreferences pins =
                inst.getTargetContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("root")) throw new AssertionError("Fixture already reserved");
            save(pins.edit().putString("root", UUID.randomUUID().toString().replace("-", "")));
        } else if (!pins.getBoolean("failed", false)) {
            throw new AssertionError("No completed failed fixture");
        }
        if ("recover".equals(phase)
                && (!pins.getBoolean("restarted", false)
                        || pins.getBoolean("recoveryAttempted", false)))
            throw new AssertionError("Recovery unavailable or already attempted");
        if ("restart".equals(phase) && pins.getBoolean("recoveryAttempted", false))
            throw new AssertionError("Failure restart unavailable after retry");
        if (("ready".equals(phase) || "armReadyStop".equals(phase))
                && !pins.getBoolean("ready", false)) throw new AssertionError("No ready fixture");
        if ("armFailureStop".equals(phase) && pins.getBoolean("recoveryAttempted", false))
            throw new AssertionError("Failure arm unavailable after retry");

        stage = "OPEN";
        Activity ui =
                inst.startActivitySync(
                        new Intent(inst.getTargetContext(), ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", pins.getString("root", ""))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            awaitWorker(inst, ui);
            if ("prepare".equals(phase)) {
                stage = "CREATE_PROJECT";
                Ui.openMenu(inst, ui);
                visible(inst, ui, Labels.NEW_PROJECT);
                android.view.accessibility.AccessibilityNodeInfo input =
                        PhoneAlphaCounterProbe.waitNode(inst, ui, "android.widget.EditText", null);
                Bundle title = new Bundle();
                title.putCharSequence(
                        android.view.accessibility.AccessibilityNodeInfo
                                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "Receipt write recovery fixture");
                if (!input.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, title))
                    throw new AssertionError("Project title could not be entered");
                PhoneAlphaCounterProbe.click(inst, ui, "Create");
                awaitProject(inst, ui);
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
                    || !"Receipt write recovery fixture".equals(project.title()))
                throw new AssertionError("Unexpected selected project");
            if (!"prepare".equals(phase) && !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Project identity changed");
            ProjectSourceStore source = new ProjectSourceStore(project);
            File artifacts = new File(project.directory(), "artifacts");
            File obstruction = new File(artifacts, "verified-artifact.properties");
            File preserved = new File(artifacts, "receipt-obstruction-evidence");
            File marker = new File(obstruction, "fixture-marker");
            if ("prepare".equals(phase)) {
                stage = "PREPARE_BOUNDED_OBSTRUCTION";
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Project was previously used");
                String edited =
                        source.readSource().replace("Increment", "AfterReceiptFailureRecovery");
                String sha = ActiveBuildInterruptionProbe.hash(edited);
                save(pins.edit().putString("project", project.id()).putString("sourceSha256", sha));
                Files.createDirectories(artifacts.toPath());
                Files.createDirectory(obstruction.toPath());
                Files.write(
                        marker.toPath(),
                        "disposable receipt obstruction\n".getBytes(StandardCharsets.UTF_8));
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () ->
                                ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source"))
                                        .setText(edited));
                showTools(inst, ui);
                stage = "BUILD_WITH_RECEIPT_OBSTRUCTION";
                visible(inst, ui, Labels.BUILD_SOURCE);
                awaitBuild(inst, ui);
                if (!PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                ((TextBox) ActiveBuildInterruptionProbe.field(ui, "status"))
                                        .getText()
                                        .toString()
                                        .startsWith("Smallwright\nLocal build failed.")))
                    throw new AssertionError("Receipt failure was not reported by the real UI");
                stage = "VERIFY_REAL_PUBLICATION_FAILURE";
                requireSource(inst, ui, source, pins, 1);
                VerifiedArtifact failed = verifyNative(ui, project, 1);
                String fingerprint = new ProjectSigner(project).verifyExisting(ui, failed.file());
                try {
                    SignedArtifactReceipt.publish(ui, project, 1, 1, failed);
                    throw new AssertionError(
                            "Bounded obstruction did not reject actual receipt IO");
                } catch (IOException expected) {
                    /* Real move/write failure, not an injected exception. */
                }
                requireNoReadiness(inst, ui, project, 1);
                if (!marker.isFile() || !obstruction.isDirectory())
                    throw new AssertionError("Failure obstruction was not retained");
                requireNoPartials(ui, artifacts);
                save(
                        pins.edit()
                                .putString("failedApkSha256", failed.sha256())
                                .putString("signerSha256", fingerprint)
                                .putBoolean("failed", true));
                return summary(pins)
                        + " phase=prepare: real native v1 compilation/signature; actual receipt IO failure; source1 retained; Install/export disabled; usable editor/Build; bounded obstruction preserved, no model/installation\n";
            }

            stage = "VERIFY_PINNED_SOURCE";
            boolean readyRestart = "ready".equals(phase) || "armReadyStop".equals(phase);
            requireSource(inst, ui, source, pins, readyRestart ? 2 : 1);
            VerifiedArtifact failed = verifyNative(ui, project, 1);
            if (!failed.sha256().equals(pins.getString("failedApkSha256", ""))
                    || !pins.getString("signerSha256", "")
                            .equals(new ProjectSigner(project).verifyExisting(ui, failed.file())))
                throw new AssertionError("Failed native artifact or signer changed");
            if ("restart".equals(phase)
                    || "recover".equals(phase)
                    || "armFailureStop".equals(phase)) {
                stage = "VERIFY_FAILED_RESTART";
                requireNoReadiness(inst, ui, project, 1);
                requireNoPartials(ui, artifacts);
                if (!marker.isFile() || preserved.exists())
                    throw new AssertionError("Failure obstruction changed before recovery");
                if ("armFailureStop".equals(phase)) armForExternalStop(inst, pins, phase);
                if ("restart".equals(phase)) {
                    save(pins.edit().putBoolean("restarted", true));
                    return summary(pins)
                            + " phase=restart: source1/failed-v1 preserved after external force-stop; no usable receipt or false readiness; Build/editor usable; no rebuild/model/installation\n";
                }
                save(pins.edit().putBoolean("recoveryAttempted", true));
                stage = "PRESERVE_OBSTRUCTION_AND_RETRY";
                Files.move(
                        obstruction.toPath(), preserved.toPath(), StandardCopyOption.ATOMIC_MOVE);
                showTools(inst, ui);
                visible(inst, ui, Labels.BUILD_SOURCE);
                awaitBuild(inst, ui);
                requireSource(inst, ui, source, pins, 2);
            }
            stage = "VERIFY_EXACT_READY";
            settle(inst, ui);
            VerifiedArtifact ready =
                    PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () ->
                                    (VerifiedArtifact)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "signedArtifact"));
            if (ready == null) throw new AssertionError("No recovered verified artifact");
            VerifiedArtifact nativeV2 = verifyNative(ui, project, 2);
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), 2);
            if (!ready.file().equals(nativeV2.file())
                    || !ready.sha256().equals(nativeV2.sha256())
                    || !receipt.sha256.equals(ready.sha256())
                    || receipt.versionCode != 2
                    || !receipt.signerFingerprint.equals(pins.getString("signerSha256", ""))
                    || !pins.getString("signerSha256", "")
                            .equals(new ProjectSigner(project).verifyExisting(ui, ready.file()))
                    || !new File(preserved, "fixture-marker").isFile()
                    || !failed.sha256().equals(pins.getString("failedApkSha256", "")))
                throw new AssertionError(
                        "Recovery changed artifact, source, version or signer identity");
            ready.verifyUnchanged();
            failed.verifyUnchanged();
            requireNoPartials(ui, artifacts);
            Control install = button(ui, Labels.INSTALL);
            PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
            inst.waitForIdleSync();
            if (!PhoneAlphaCounterProbe.mainValue(
                    inst,
                    () -> {
                        Rect visible = new Rect();
                        return install.isEnabled() && install.isShown();
                    })) throw new AssertionError("Install readiness is not fully visible");
            if (readyRestart) {
                if (!ready.sha256().equals(pins.getString("readyApkSha256", "")))
                    throw new AssertionError("Restart rebuilt or changed the saved APK");
                if ("armReadyStop".equals(phase)) armForExternalStop(inst, pins, phase);
            } else
                save(
                        pins.edit()
                                .putString("readyApkSha256", ready.sha256())
                                .putBoolean("ready", true));
            return summary(pins)
                    + " phase="
                    + phase
                    + ": source2/version2 exact native artifact/signature/receipt; same project signer; failed-v1/obstruction retained; visible Install, no installation/model; sha256="
                    + ready.sha256()
                    + "\n";
        } finally {
            PhoneAlphaCounterProbe.onMain(inst, ui::finish);
        }
    }

    private static String summary(SharedPreferences pins) {
        return "Receipt recovery root="
                + pins.getString("root", "")
                + " project="
                + pins.getString("project", "")
                + " sourceSha256="
                + pins.getString("sourceSha256", "")
                + " failedApkSha256="
                + pins.getString("failedApkSha256", "")
                + " signerSha256="
                + pins.getString("signerSha256", "");
    }

    private static void armForExternalStop(
            Instrumentation inst, SharedPreferences pins, String phase) throws Exception {
        stage = "ARMED_EXTERNAL_STOP";
        Bundle marker = new Bundle();
        marker.putString(
                "stream",
                "RECEIPT_RECOVERY_ARMED phase="
                        + phase
                        + " "
                        + summary(pins)
                        + " pid="
                        + android.os.Process.myPid()
                        + "\n");
        inst.sendStatus(0, marker);
        // Test-only hold: no build, model request or source mutation occurs while armed.
        new java.util.concurrent.CountDownLatch(1).await(15, TimeUnit.SECONDS);
        throw new AssertionError("Expected external controller force-stop did not occur");
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Fixture evidence could not be saved");
    }

    private static void awaitProject(Instrumentation inst, Activity ui) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () ->
                                    (ProjectIdentity)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "activeProject"));
            if (project != null && "Receipt write recovery fixture".equals(project.title())) {
                ControllerUi.awaitProjectBinding(inst, ui);
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("New fixture project was not selected");
    }

    private static void awaitWorker(Instrumentation inst, Activity ui) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () -> (IBuildService) ActiveBuildInterruptionProbe.field(ui, "worker"))
                    != null) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Worker unavailable");
    }

    private static void awaitBuild(Instrumentation inst, Activity ui) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (PhoneAlphaCounterProbe.mainValue(
                inst, () -> (boolean) ActiveBuildInterruptionProbe.field(ui, "buildInProgress"))) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Build did not settle");
            Thread.sleep(25);
        }
        settle(inst, ui);
    }

    private static void settle(Instrumentation inst, Activity ui) throws Exception {
        inst.waitForIdleSync();
        ExecutorService executor =
                (ExecutorService) ActiveBuildInterruptionProbe.field(ui, "executor");
        executor.submit(() -> {}).get(10, TimeUnit.SECONDS);
        inst.waitForIdleSync();
    }

    private static void requireSource(
            Instrumentation inst,
            Activity ui,
            ProjectSourceStore source,
            SharedPreferences pins,
            int revision)
            throws Exception {
        if (source.revision() != revision
                || !ActiveBuildInterruptionProbe.hash(source.readSource())
                        .equals(pins.getString("sourceSha256", ""))
                || !PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source"))
                                                .getText()
                                                .toString()
                                                .equals(source.readSource())
                                        && (int)
                                                        ActiveBuildInterruptionProbe.field(
                                                                ui, "editorRevision")
                                                == revision))
            throw new AssertionError("Saved source/editor identity changed");
    }

    private static VerifiedArtifact verifyNative(Context ui, ProjectIdentity project, int version)
            throws Exception {
        VerifiedArtifact artifact =
                VerifiedArtifact.capture(
                        new File(project.directory(), "artifacts/app-v" + version + ".apk"));
        ArtifactValidator.validateUnsigned(ui, artifact.file(), version, project.packageName());
        if (!new ApkVerifier.Builder(artifact.file()).build().verify().isVerified())
            throw new AssertionError("Native artifact signature invalid");
        artifact.verifyUnchanged();
        return artifact;
    }

    private static void requireNoReadiness(
            Instrumentation inst, Activity ui, ProjectIdentity project, int revision)
            throws Exception {
        settle(inst, ui);
        try {
            SignedArtifactReceipt.read(
                    project.directory(), project.id(), project.packageName(), revision);
            throw new AssertionError("Failed publication authorized an artifact");
        } catch (IOException expected) {
            /* No usable receipt. */
        }
        showTools(inst, ui);
        if (!PhoneAlphaCounterProbe.mainValue(
                inst,
                () ->
                        ActiveBuildInterruptionProbe.field(ui, "signedArtifact") == null
                                && !button(ui, Labels.INSTALL).isEnabled()
                                && !button(ui, Labels.EXPORT_APK).isEnabled()
                                && button(ui, Labels.BUILD_SOURCE).isEnabled()
                                && ((TextBox) ActiveBuildInterruptionProbe.field(ui, "source"))
                                        .isEnabled()))
            throw new AssertionError("Failure left false readiness or unusable Build/editor");
    }

    private static void requireNoPartials(Context ui, File artifacts) throws Exception {
        for (File file : artifacts.listFiles())
            if (file.getName().startsWith("unsigned-") || file.getName().endsWith(".partial"))
                throw new AssertionError("Failure/recovery left a partial artifact");
        for (File file : ui.getCacheDir().listFiles())
            if (file.getName().startsWith("snapshot-"))
                throw new AssertionError("Failure/recovery left a temporary snapshot");
    }

    private static Control button(Activity ui, String label) throws Exception {
        return Ui.button(ui, label);
    }

    private static void visible(Instrumentation inst, Activity ui, String label) throws Exception {
        Control button = button(ui, label);
        PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(button));
        inst.waitForIdleSync();
        PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.visibleClick(button));
        inst.waitForIdleSync();
    }

    private static void showTools(Instrumentation inst, Activity ui) throws Exception {
        if (!PhoneAlphaCounterProbe.mainValue(
                inst, () -> button(ui, Labels.BUILD_SOURCE).isShown())) Ui.showTools(inst, ui, ui);
    }
}
