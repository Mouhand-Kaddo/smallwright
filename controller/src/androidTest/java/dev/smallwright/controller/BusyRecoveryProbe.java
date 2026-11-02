package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Actual busy Binder admission and native UI recovery; injected pending-run preparation, no model.
 */
final class BusyRecoveryProbe {
    private static String stage = "PREPARE", state = "";

    static String failureStage() {
        return stage + state;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        if (!java.util.List.of("true", "retry", "stop", "stopAfterWait").contains(phase))
            throw new IllegalArgumentException("Unknown busy recovery phase");
        stage = "PREPARE";
        state = "";
        String rootId = UUID.randomUUID().toString().replace("-", "");
        Context context = ActiveBuildInterruptionProbe.isolated(inst.getTargetContext(), rootId);
        ProjectIdentity project =
                new ProjectRegistry(
                                new File(context.getFilesDir(), "projects"), context.getFilesDir())
                        .create("Busy recovery fixture");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String replacement = source.readSource().replace("Increment", "AfterWorkerBusyRecovery");
        String expectedSha = ActiveBuildInterruptionProbe.hash(replacement);
        String runId = "busy_" + UUID.randomUUID().toString().replace("-", "");
        RunJournal journal = new RunJournal(context, project.id());
        journal.create(runId, 0);
        for (RunJournal.State next :
                new RunJournal.State[] {
                    RunJournal.State.PLANNING, RunJournal.State.EDITING, RunJournal.State.APPLYING
                }) {
            RunJournal.Snapshot current = journal.load(runId);
            journal.advance(runId, current.sequence, next, 0, null, null);
        }
        int committed =
                source.commit(
                        0,
                        java.util.Map.of(
                                source.mainPath(),
                                replacement.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        java.util.Set.of());
        RunJournal.Snapshot applying = journal.load(runId);
        journal.advance(
                runId,
                applying.sequence,
                RunJournal.State.BUILDING,
                committed,
                "build_" + UUID.randomUUID().toString().replace("-", ""),
                null,
                1);
        RunJournal.Snapshot pending = journal.load(runId);
        if (pending.state != RunJournal.State.BUILDING
                || pending.sourceRevision != 1
                || !Integer.valueOf(1).equals(pending.versionCode)
                || source.revision() != 1)
            throw new AssertionError("No durable exact pending build");
        android.os.Bundle marker = new android.os.Bundle();
        marker.putString(
                "stream",
                "BUSY_RECOVERY_FIXTURE root="
                        + rootId
                        + " project="
                        + project.id()
                        + " run="
                        + runId
                        + " build="
                        + pending.buildId
                        + "\n");
        inst.sendStatus(0, marker);

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
        boolean bound =
                context.bindService(
                        new Intent()
                                .setComponent(
                                        new ComponentName(
                                                "dev.smallwright.worker",
                                                "dev.smallwright.worker.BuildService")),
                        connection,
                        Context.BIND_AUTO_CREATE);
        if (!bound) throw new AssertionError("Worker bind refused");
        ParcelFileDescriptor[] input = null;
        ParcelFileDescriptor output = null, report = null;
        FutureTask<String> blocker = null;
        Activity activity = null;
        try {
            stage = "ADMIT_BUSY_WORKER";
            if (!connected.await(10, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            IBuildService real = remote.get();
            if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                    .equals(real.handshake(2)))
                throw new AssertionError("Worker negotiation differs");
            input = ParcelFileDescriptor.createPipe();
            Os.fcntlInt(
                    input[1].getFileDescriptor(),
                    OsConstants.F_SETFL,
                    Os.fcntlInt(input[1].getFileDescriptor(), OsConstants.F_GETFL, 0)
                            | OsConstants.O_NONBLOCK);
            if (Os.fcntlInt(input[1].getFileDescriptor(), 1031, 4096) != 4096)
                throw new AssertionError("Disposable pipe capacity differs");
            if (Os.write(input[1].getFileDescriptor(), new byte[4096], 0, 4096) != 4096)
                throw new AssertionError("Disposable pipe was not prefilled");
            try {
                Os.write(input[1].getFileDescriptor(), new byte[1], 0, 1);
                throw new AssertionError("Disposable pipe was not full before dispatch");
            } catch (ErrnoException expected) {
                if (expected.errno != OsConstants.EAGAIN) throw expected;
            }
            File unsigned = new File(context.getCacheDir(), "blocked-unsigned.apk");
            File diagnostics = new File(context.getCacheDir(), "blocked-report");
            Files.createFile(unsigned.toPath());
            Files.createFile(diagnostics.toPath());
            output = ParcelFileDescriptor.open(unsigned, ParcelFileDescriptor.MODE_READ_WRITE);
            report = ParcelFileDescriptor.open(diagnostics, ParcelFileDescriptor.MODE_READ_WRITE);
            ParcelFileDescriptor reader = input[0], apk = output, wire = report;
            blocker = new FutureTask<>(() -> real.buildWithDiagnostics(reader, apk, wire));
            Thread caller = new Thread(blocker, "busy-recovery-blocker");
            caller.setDaemon(true);
            caller.start();
            awaitDrain(input[1], blocker);
            requireBusy(real);

            stage = "OPEN_RECOVERY_UI";
            activity =
                    inst.startActivitySync(
                            new Intent(inst.getTargetContext(), ProjectBindingProbeActivity.class)
                                    .putExtra("bindingProbeId", rootId)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity ui = activity;
            ControllerUi.awaitProjectBinding(inst, ui);
            stage = "AWAIT_BUSY_RECOVERY";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                RunJournal.Snapshot run = journal.load(runId);
                state = " state=" + run.state.name();
                if (run.state != RunJournal.State.BUILDING)
                    throw new AssertionError("Busy admission permanently closed the pending run");
                boolean waiting =
                        PhoneAlphaCounterProbe.mainValue(
                                inst,
                                () -> {
                                    TextBox status =
                                            (TextBox)
                                                    ActiveBuildInterruptionProbe.field(
                                                            ui, "status");
                                    return status.getText()
                                                    .toString()
                                                    .startsWith(
                                                            "Smallwright\nWaiting for the compiler")
                                            && ActiveBuildInterruptionProbe.field(
                                                            ui, "signedArtifact")
                                                    == null;
                                });
                if (waiting) break;
                if (blocker.isDone() || System.nanoTime() >= deadline)
                    throw new AssertionError("No visible bounded recovery wait");
                Thread.sleep(20);
            }
            requireBusy(real);
            Control stop = Ui.button(ui, Labels.STOP);
            if (!PhoneAlphaCounterProbe.mainValue(inst, stop::isEnabled))
                throw new AssertionError("Busy recovery lost Stop");
            Control retry = null;
            if ("retry".equals(phase) || "stopAfterWait".equals(phase)) {
                stage = "AWAIT_BOUNDED_WAIT_END";
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                while (PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                (boolean)
                                        ActiveBuildInterruptionProbe.field(
                                                ui, "buildInProgress"))) {
                    if (journal.load(runId).state != RunJournal.State.BUILDING
                            || blocker.isDone()) {
                        stage = "BUDGET_RUN_OR_BLOCKER_ENDED";
                        throw new AssertionError(
                                "Busy wait lost the pending run or blocker ended early");
                    }
                    if (System.nanoTime() >= deadline) {
                        stage = "BUDGET_WAIT_DEADLINE";
                        throw new AssertionError("Busy recovery wait was not bounded");
                    }
                    Thread.sleep(20);
                }
                stage = "VERIFY_BUDGET_WORKER_BUSY";
                requireBusy(real);
                stage = "VERIFY_BUDGET_PENDING";
                if (journal.load(runId).state != RunJournal.State.BUILDING
                        || journal.load(runId).sequence != pending.sequence
                        || !PhoneAlphaCounterProbe.mainValue(
                                inst,
                                () ->
                                        ActiveBuildInterruptionProbe.field(ui, "pendingRecovery")
                                                        != null
                                                && ActiveBuildInterruptionProbe.field(
                                                                ui, "signedArtifact")
                                                        == null
                                                && !((TextBox)
                                                                ActiveBuildInterruptionProbe.field(
                                                                        ui, "source"))
                                                        .isEnabled()
                                                && stop.isEnabled()))
                    throw new AssertionError(
                            "Bounded wait discarded the exact pending run or Stop");
                if ("retry".equals(phase)) {
                    stage = "VERIFY_RETRY_BUTTON";
                    retry = Ui.button(ui, Labels.RETRY_SAVED_BUILD);
                    Ui.showTools(inst, ui, ui);
                    Control visibleRetry = retry;
                    PhoneAlphaCounterProbe.onMain(
                            inst, () -> PhoneAlphaCounterProbe.reveal(visibleRetry));
                    inst.waitForIdleSync();
                    if (!PhoneAlphaCounterProbe.mainValue(
                            inst, () -> visibleRetry.isEnabled() && visibleRetry.isShown()))
                        throw new AssertionError("Saved-build retry is not enabled and shown");
                }
            }
            boolean stopped = "stop".equals(phase) || "stopAfterWait".equals(phase);
            if (stopped) {
                stage = "STOP_PENDING_RECOVERY";
                PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(stop));
                inst.waitForIdleSync();
                PhoneAlphaCounterProbe.onMain(
                        inst, () -> PhoneAlphaCounterProbe.visibleClick(stop));
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                (boolean) ActiveBuildInterruptionProbe.field(ui, "buildInProgress")
                                        || (boolean)
                                                ActiveBuildInterruptionProbe.field(
                                                        ui, "recoveryQueued"))) {
                    if (System.nanoTime() >= deadline)
                        throw new AssertionError("Stopped recovery did not settle");
                    Thread.sleep(20);
                }
                if (journal.load(runId).state != RunJournal.State.CANCELLED
                        || journal.load(runId).sequence != pending.sequence + 1
                        || !PhoneAlphaCounterProbe.mainValue(
                                inst,
                                () ->
                                        ActiveBuildInterruptionProbe.field(ui, "pendingRecovery")
                                                        == null
                                                && ActiveBuildInterruptionProbe.field(
                                                                ui, "signedArtifact")
                                                        == null
                                                && ((TextBox)
                                                                ActiveBuildInterruptionProbe.field(
                                                                        ui, "source"))
                                                        .isEnabled()))
                    throw new AssertionError("Stop retained cancelled recovery or false readiness");
            }
            stage = "RELEASE_WORKER";
            input[1].close();
            if (!"ERROR: Snapshot archive is malformed".equals(blocker.get(15, TimeUnit.SECONDS)))
                throw new AssertionError("Explicit malformed blocker did not release normally");
            if (stopped) {
                stage = "VERIFY_STOP_AFTER_RELEASE";
                inst.waitForIdleSync();
                File directory =
                        RunRecovery.directory(
                                new File(project.directory(), "artifacts"), pending.buildId);
                if (journal.load(runId).state != RunJournal.State.CANCELLED
                        || source.revision() != 1
                        || !expectedSha.equals(
                                ActiveBuildInterruptionProbe.hash(source.readSource()))
                        || new File(project.directory(), "artifacts/verified-artifact.properties")
                                .exists()
                        || new File(directory, "app.apk").exists()
                        || !PhoneAlphaCounterProbe.mainValue(
                                inst,
                                () ->
                                        ActiveBuildInterruptionProbe.field(ui, "pendingRecovery")
                                                        == null
                                                && ActiveBuildInterruptionProbe.field(
                                                                ui, "signedArtifact")
                                                        == null))
                    throw new AssertionError(
                            "Cancelled recovery rebuilt or published after worker release");
                for (String name :
                        new String[] {
                            "snapshot.partial.zip", "unsigned.partial.apk", "signed.partial.apk"
                        })
                    if (new File(directory, name).exists())
                        throw new AssertionError("Stopped recovery retained partials");
                return "Busy recovery phase="
                        + phase
                        + ": actual visible Stop cancelled the saved run; pending recovery cleared, source1 retained; after blocker EOF no retry, APK, receipt or readiness; no model/installation\n";
            }
            if (retry != null) {
                Control visibleRetry = retry;
                PhoneAlphaCounterProbe.onMain(
                        inst, () -> PhoneAlphaCounterProbe.visibleClick(visibleRetry));
            }
            stage = "AWAIT_READY";
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!PhoneAlphaCounterProbe.mainValue(
                    inst,
                    () ->
                            ActiveBuildInterruptionProbe.field(ui, "signedArtifact") != null
                                    && !(boolean)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "buildInProgress"))) {
                state = " state=" + journal.load(runId).state.name();
                if (journal.load(runId).state != RunJournal.State.BUILDING
                        && journal.load(runId).state != RunJournal.State.READY_TO_TEST)
                    throw new AssertionError("Released worker did not recover the saved build");
                if (System.nanoTime() >= deadline)
                    throw new AssertionError("Recovery deadline expired");
                Thread.sleep(25);
            }
            stage = "VERIFY_EXACT_READY";
            RunJournal.Snapshot ready = journal.load(runId);
            VerifiedArtifact artifact =
                    PhoneAlphaCounterProbe.mainValue(
                            inst,
                            () ->
                                    (VerifiedArtifact)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "signedArtifact"));
            if (ready.state != RunJournal.State.READY_TO_TEST
                    || ready.sequence != pending.sequence + 1
                    || ready.sourceRevision != 1
                    || !Integer.valueOf(1).equals(ready.versionCode)
                    || !pending.buildId.equals(ready.buildId)
                    || source.revision() != 1
                    || !expectedSha.equals(ActiveBuildInterruptionProbe.hash(source.readSource())))
                throw new AssertionError("Recovery changed the exact saved run");
            artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(ui, artifact.file(), 1, project.packageName());
            if (!new ApkVerifier.Builder(artifact.file()).build().verify().isVerified())
                throw new AssertionError("Recovered APK signature is invalid");
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), 1);
            File directory =
                    RunRecovery.directory(
                            new File(project.directory(), "artifacts"), pending.buildId);
            if (!new File(directory, "app.apk").equals(artifact.file())
                    || receipt.versionCode != 1
                    || !artifact.sha256().equals(receipt.sha256))
                throw new AssertionError("Receipt differs from saved build");
            for (String name :
                    new String[] {
                        "snapshot.partial.zip", "unsigned.partial.apk", "signed.partial.apk"
                    })
                if (new File(directory, name).exists())
                    throw new AssertionError("Recovery retained a partial");
            Control install = Ui.button(ui, Labels.INSTALL);
            PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
            inst.waitForIdleSync();
            if (!PhoneAlphaCounterProbe.mainValue(
                    inst,
                    () -> {
                        Rect visible = new Rect();
                        return install.isEnabled() && install.isShown();
                    })) throw new AssertionError("Install readiness is not visible");
            return "Busy recovery phase="
                    + phase
                    + ": actual busy worker retained BUILDING with visible wait/Stop; pipe released; exact source1/build/version1 recovered through real native compilation/signature/receipt; visible Install, no installation/model; sha256="
                    + artifact.sha256()
                    + "\n";
        } finally {
            if (input != null) {
                close(input[1]);
            }
            if (blocker != null) {
                try {
                    blocker.get(20, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
            if (input != null) close(input[0]);
            close(output);
            close(report);
            if (activity != null) {
                Activity ui = activity;
                PhoneAlphaCounterProbe.onMain(inst, ui::finish);
            }
            context.unbindService(connection);
        }
    }

    private static void requireBusy(IBuildService worker) throws Exception {
        try {
            worker.toolchainProbe();
            throw new AssertionError("Actual worker is not busy");
        } catch (IllegalStateException expected) {
            if (!"ERROR: Worker busy".equals(expected.getMessage())) throw expected;
        }
    }

    private static void awaitDrain(ParcelFileDescriptor writer, FutureTask<String> blocker)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (blocker.isDone()) throw new AssertionError("Blocker finished before admission");
            try {
                if (Os.write(writer.getFileDescriptor(), new byte[1], 0, 1) == 1) return;
            } catch (ErrnoException failure) {
                if (failure.errno != OsConstants.EAGAIN) throw failure;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("No actual worker input progress");
    }

    private static void close(ParcelFileDescriptor fd) {
        if (fd != null)
            try {
                fd.close();
            } catch (Exception ignored) {
            }
    }
}
