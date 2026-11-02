package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Actual recorded build interrupted externally during worker input; synthetic proposal, no model.
 */
final class ActiveBuildInterruptionProbe {
    private static final String PREFS = "active-build-interruption-fixture-v2";
    private static final String REQUEST = "Change the fixture label to AfterControllerTermination";
    private static String stage = "GUARD";
    private static String observedState = "";

    static String failureStage() {
        return stage + observedState;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        observedState = "";
        if (!java.util.List.of("prepare", "recover", "ready").contains(phase))
            throw new IllegalArgumentException("Unknown interruption phase");
        SharedPreferences pins =
                inst.getTargetContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase) ? pins.contains("root") : !pins.getBoolean("armed", false))
            throw new AssertionError("Interruption fixture unavailable or already reserved");
        if ("prepare".equals(phase)) return prepare(inst, pins);
        if ("ready".equals(phase) && !pins.getBoolean("recovered", false))
            throw new AssertionError("No recovered fixture");
        stage = "OPEN_RECOVERY_UI";
        String rootId = pins.getString("root", "");
        if (!rootId.matches("[0-9a-f]{32}")) throw new AssertionError("Invalid fixture root");
        Activity activity =
                inst.startActivitySync(
                        new Intent(inst.getTargetContext(), ProjectBindingProbeActivity.class)
                                .putExtra("bindingProbeId", rootId)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, activity);
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(activity, "activeProject"));
            if (!pins.getString("project", "").equals(project.id())
                    || !pins.getString("package", "").equals(project.packageName()))
                throw new AssertionError("Recovery selected another project");
            RunJournal journal = new RunJournal(activity, project.id());
            String runId = pins.getString("run", "");
            stage = "AWAIT_RECOVERED_READY";
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (true) {
                RunJournal.Snapshot run = journal.load(runId);
                observedState = " state=" + run.state.name();
                boolean ready =
                        PhoneAlphaCounterProbe.mainValue(
                                inst,
                                () ->
                                        field(activity, "signedArtifact") != null
                                                && !(boolean) field(activity, "buildInProgress"));
                if (ready) break;
                if (run.state != RunJournal.State.BUILDING
                        && run.state != RunJournal.State.READY_TO_TEST)
                    throw new AssertionError("Recovery closed the committed active build");
                if (System.nanoTime() >= end)
                    throw new AssertionError("Recovery did not reach readiness");
                Thread.sleep(25);
            }
            stage = "VERIFY_RECOVERY";
            RunJournal.Snapshot run = journal.load(runId);
            if (run.state != RunJournal.State.READY_TO_TEST
                    || run.sourceRevision != 1
                    || run.sequence != pins.getInt("buildingSequence", -1) + 1
                    || !pins.getString("build", "").equals(run.buildId)
                    || !Integer.valueOf(1).equals(run.versionCode)
                    || pins.getInt("proposalCalls", -1) != 1)
                throw new AssertionError(
                        "Recovery changed the recorded run identity or replayed a proposal");
            ProjectSourceStore source = new ProjectSourceStore(project);
            if (source.revision() != 1
                    || !pins.getString("sourceSha256", "").equals(hash(source.readSource()))
                    || !source.readSource().contains("AfterControllerTermination"))
                throw new AssertionError("Recovery changed or lost the committed source");
            VerifiedArtifact artifact =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (VerifiedArtifact) field(activity, "signedArtifact"));
            artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(activity, artifact.file(), 1, project.packageName());
            if (!new ApkVerifier.Builder(artifact.file()).build().verify().isVerified())
                throw new AssertionError("Recovered APK signature is invalid");
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), 1);
            File directory =
                    RunRecovery.directory(new File(project.directory(), "artifacts"), run.buildId);
            if (!new File(directory, "app.apk").equals(artifact.file())
                    || receipt.versionCode != 1
                    || !receipt.sha256.equals(artifact.sha256()))
                throw new AssertionError("Recovery receipt differs from the pinned build artifact");
            for (String name :
                    new String[] {
                        "snapshot.partial.zip", "unsigned.partial.apk", "signed.partial.apk"
                    })
                if (new File(directory, name).exists())
                    throw new AssertionError("Recovered build retained partial output");
            Control install = Ui.button(activity, Labels.INSTALL);
            PhoneAlphaCounterProbe.onMain(inst, () -> PhoneAlphaCounterProbe.reveal(install));
            inst.waitForIdleSync();
            if (!PhoneAlphaCounterProbe.mainValue(
                    inst,
                    () -> {
                        return install.isEnabled() && install.isShown();
                    })) throw new AssertionError("Recovered Install readiness was not visible");
            if ("ready".equals(phase)) {
                if (!pins.getString("readySha256", "").equals(artifact.sha256()))
                    throw new AssertionError("Ready restart rebuilt or changed the artifact");
                return "Active interruption ready restart: exact revision1/build/version1 receipt restored; visible Install enabled; no model, rebuild or install\n";
            }
            if (!pins.edit()
                    .putBoolean("recovered", true)
                    .putString("readySha256", artifact.sha256())
                    .commit()) throw new AssertionError("Recovery observation was not saved");
            return "Active interruption recovery: real UI rebuilt exact committed revision1/build/version1 after controller termination; one synthetic proposal, no replay; actual native APK/signature/receipt; partials discarded; visible Install enabled, no installation. sha256="
                    + artifact.sha256()
                    + "\n";
        } finally {
            PhoneAlphaCounterProbe.onMain(inst, activity::finish);
        }
    }

    private static String prepare(Instrumentation inst, SharedPreferences pins) throws Exception {
        String rootId = UUID.randomUUID().toString().replace("-", "");
        if (!pins.edit().putString("root", rootId).commit())
            throw new AssertionError("Fixture reservation failed");
        Context context = isolated(inst.getTargetContext(), rootId);
        ProjectIdentity project =
                new ProjectRegistry(
                                new File(context.getFilesDir(), "projects"), context.getFilesDir())
                        .create("Active interruption fixture");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
            throw new AssertionError("Fixture source was not pristine");
        java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias("generated-" + project.id()))
            throw new AssertionError("Fixture signer was already present");
        String content = source.readSource().replace("Increment", "AfterControllerTermination");
        StringBuilder padding = new StringBuilder(content).append("\n/*\n");
        java.util.Random random = new java.util.Random(7271);
        for (int i = 0; i < 24000; i++) {
            padding.append((char) ('A' + random.nextInt(26)));
            if (i % 80 == 79) padding.append('\n');
        }
        content = padding.append("*/\n").toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > 64 * 1024)
            throw new AssertionError("Fixture source exceeds admitted project bound");
        String expectedContent = content;
        String sha = hash(content);
        String runId = "interruption_" + UUID.randomUUID().toString().replace("-", "");
        RunJournal journal = new RunJournal(context, project.id());
        journal.create(runId, 0);
        if (!pins.edit()
                .putString("project", project.id())
                .putString("run", runId)
                .putString("sourceSha256", sha)
                .commit()) throw new AssertionError("Fixture identity not saved");
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
        try {
            stage = "BIND_WORKER";
            if (!connected.await(10, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            IBuildService real = remote.get();
            if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                    .equals(real.handshake(2)))
                throw new AssertionError("Worker negotiation failed");
            HeldInput held = new HeldInput(real, project.packageName(), sha);
            FutureTask<VerifiedArtifact> build =
                    new FutureTask<>(
                            () -> {
                                if (pins.getInt("proposalCalls", 0) != 0
                                        || !pins.edit().putInt("proposalCalls", 1).commit())
                                    throw new AssertionError(
                                            "Fixture change replayed or metadata failed");
                                for (RunJournal.State next :
                                        new RunJournal.State[] {
                                            RunJournal.State.PLANNING,
                                            RunJournal.State.EDITING,
                                            RunJournal.State.APPLYING
                                        }) {
                                    RunJournal.Snapshot current = journal.load(runId);
                                    journal.advance(runId, current.sequence, next, 0, null, null);
                                }
                                int revision =
                                        source.commit(
                                                0,
                                                java.util.Map.of(
                                                        source.mainPath(),
                                                        expectedContent.getBytes(
                                                                StandardCharsets.UTF_8)),
                                                java.util.Set.of());
                                String buildId =
                                        "build_" + UUID.randomUUID().toString().replace("-", "");
                                RunJournal.Snapshot applying = journal.load(runId);
                                journal.advance(
                                        runId,
                                        applying.sequence,
                                        RunJournal.State.BUILDING,
                                        revision,
                                        buildId,
                                        null,
                                        1);
                                return new LocalBuildCoordinator(context, source, project)
                                        .buildCommittedRevision(revision, 1, buildId, held);
                            });
            Thread thread = new Thread(build, "active-interruption-recorded-build");
            thread.setDaemon(true);
            thread.start();
            stage = "AWAIT_ADMISSION";
            if (!held.forwarded.await(4, TimeUnit.SECONDS)
                    || build.isDone()
                    || held.finished.getCount() == 0) {
                observedState =
                        " state="
                                + journal.load(runId).state.name()
                                + " workerFinished="
                                + (held.finished.getCount() == 0);
                throw new AssertionError("Actual build was not held during worker input");
            }
            try {
                real.toolchainProbe();
                throw new AssertionError("Worker admission was not held");
            } catch (IllegalStateException busy) {
                if (!"ERROR: Worker busy".equals(busy.getMessage())) throw busy;
            }
            RunJournal.Snapshot current = journal.load(runId);
            File directory =
                    RunRecovery.directory(
                            new File(project.directory(), "artifacts"), current.buildId);
            if (current.state != RunJournal.State.BUILDING
                    || current.sourceRevision != 1
                    || !Integer.valueOf(1).equals(current.versionCode)
                    || !new File(directory, "snapshot.partial.zip").isFile()
                    || !new File(directory, "unsigned.partial.apk").isFile()
                    || new File(directory, "app.apk").exists()
                    || new File(project.directory(), "artifacts/verified-artifact.properties")
                            .exists())
                throw new AssertionError(
                        "Interrupted build did not persist exact source/build/partial boundary");
            if (!pins.edit()
                    .putString("project", project.id())
                    .putString("package", project.packageName())
                    .putString("run", runId)
                    .putString("build", current.buildId)
                    .putInt("buildingSequence", current.sequence)
                    .putString("sourceSha256", sha)
                    .putBoolean("armed", true)
                    .commit()) throw new AssertionError("Armed fixture not saved");
            stage = "ARMED";
            android.os.Bundle ready = new android.os.Bundle();
            ready.putString(
                    "stream",
                    "ACTIVE_INTERRUPTION_ARMED controllerPid="
                            + android.os.Process.myPid()
                            + " project="
                            + project.id()
                            + " build="
                            + current.buildId
                            + " sourceSha256="
                            + sha
                            + "\n");
            inst.sendStatus(0, ready);
            // The host must terminate this exact process, not release an injected exception.
            build.get(12, TimeUnit.SECONDS);
            throw new AssertionError("Controller termination did not occur");
        } finally {
            context.unbindService(connection);
        }
    }

    static Context isolated(Context target, String id) throws Exception {
        File root = new File(target.getFilesDir(), "binding-ui-probe-" + id),
                cache = new File(root, "cache"),
                noBackup = new File(root, "no-backup");
        if (!cache.mkdirs() || !noBackup.mkdirs())
            throw new AssertionError("Fixture root was reused");
        return new ContextWrapper(target) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("binding-ui-probe-" + id + "-" + name, mode);
            }

            @Override
            public File getFilesDir() {
                return root;
            }

            @Override
            public File getCacheDir() {
                return cache;
            }

            @Override
            public File getNoBackupFilesDir() {
                return noBackup;
            }
        };
    }

    static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    static String hash(String source) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b :
                MessageDigest.getInstance("SHA-256")
                        .digest(source.getBytes(StandardCharsets.UTF_8)))
            out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }

    /** Forward exact source bytes on phone, hold EOF until external death or bounded failure. */
    private static final class HeldInput extends IBuildService.Stub {
        final IBuildService real;
        final String packageName, sha;
        final CountDownLatch forwarded = new CountDownLatch(1), finished = new CountDownLatch(1);

        HeldInput(IBuildService real, String packageName, String sha) {
            this.real = real;
            this.packageName = packageName;
            this.sha = sha;
        }

        @Override
        public String handshake(int version) throws RemoteException {
            return real.handshake(version);
        }

        @Override
        public String aapt2Version() throws RemoteException {
            return real.aapt2Version();
        }

        @Override
        public String toolchainProbe() throws RemoteException {
            return real.toolchainProbe();
        }

        @Override
        public String usePlatform(ParcelFileDescriptor platform) throws RemoteException {
            return real.usePlatform(platform);
        }

        @Override
        public String useLibraries(ParcelFileDescriptor[] libraries, String[] sha256)
                throws RemoteException {
            return real.useLibraries(libraries, sha256);
        }

        @Override
        public String build(ParcelFileDescriptor source, ParcelFileDescriptor apk) {
            throw new AssertionError("Unexpected legacy dispatch");
        }

        @Override
        public String buildWithDiagnostics(
                ParcelFileDescriptor input, ParcelFileDescriptor apk, ParcelFileDescriptor report) {
            ParcelFileDescriptor[] pipe = null;
            FutureTask<String> task = null;
            try {
                byte[] bytes;
                try (InputStream in =
                        new ParcelFileDescriptor.AutoCloseInputStream(
                                ParcelFileDescriptor.dup(input.getFileDescriptor()))) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = in.read(chunk)) != -1) {
                        if (out.size() + read > 512 * 1024)
                            throw new AssertionError("Fixture ZIP exceeds bound");
                        out.write(chunk, 0, read);
                    }
                    bytes = out.toByteArray();
                }
                int matches = 0;
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!entry.getName()
                                .equals(
                                        "java/"
                                                + packageName.replace('.', '/')
                                                + "/MainActivity.java")) continue;
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int read;
                        while ((read = zip.read(chunk)) != -1) {
                            if (out.size() + read > 64 * 1024)
                                throw new AssertionError("Fixture source exceeds bound");
                            out.write(chunk, 0, read);
                        }
                        if (!sha.equals(
                                hash(new String(out.toByteArray(), StandardCharsets.UTF_8))))
                            throw new AssertionError("Leased source changed");
                        matches++;
                    }
                }
                if (matches != 1) throw new AssertionError("Leased source entry is not unique");
                pipe = ParcelFileDescriptor.createPipe();
                if (Os.fcntlInt(pipe[1].getFileDescriptor(), 1031, 4096) != 4096)
                    throw new AssertionError(
                            "Disposable fixture pipe capacity was not established");
                Os.fcntlInt(
                        pipe[1].getFileDescriptor(),
                        OsConstants.F_SETFL,
                        Os.fcntlInt(pipe[1].getFileDescriptor(), OsConstants.F_GETFL, 0)
                                | OsConstants.O_NONBLOCK);
                int sent = 0;
                boolean full = false;
                while (sent < bytes.length) {
                    try {
                        sent +=
                                Os.write(
                                        pipe[1].getFileDescriptor(),
                                        bytes,
                                        sent,
                                        Math.min(8192, bytes.length - sent));
                    } catch (ErrnoException busy) {
                        if (busy.errno != OsConstants.EAGAIN) throw busy;
                        full = true;
                        break;
                    }
                }
                if (!full) throw new AssertionError("Fixture did not prefill its private pipe");
                ParcelFileDescriptor reader = pipe[0];
                task = new FutureTask<>(() -> real.buildWithDiagnostics(reader, apk, report));
                FutureTask<String> worker = task;
                Thread caller =
                        new Thread(
                                () -> {
                                    try {
                                        worker.run();
                                    } finally {
                                        finished.countDown();
                                    }
                                },
                                "interruption-real-worker");
                caller.setDaemon(true);
                caller.start();
                boolean drained = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (sent < bytes.length) {
                    if (worker.isDone() || System.nanoTime() >= deadline)
                        throw new AssertionError("Real worker input did not accept fixture");
                    try {
                        int written =
                                Os.write(
                                        pipe[1].getFileDescriptor(),
                                        bytes,
                                        sent,
                                        Math.min(8192, bytes.length - sent));
                        sent += written;
                        if (written > 0) drained = true;
                    } catch (ErrnoException busy) {
                        if (busy.errno != OsConstants.EAGAIN) throw busy;
                        Thread.sleep(5);
                    }
                }
                if (!drained || worker.isDone())
                    throw new AssertionError("No active real worker input progress");
                forwarded.countDown();
                if (!new CountDownLatch(1).await(7, TimeUnit.SECONDS))
                    throw new IllegalStateException(
                            "External controller termination did not arrive");
                throw new AssertionError("Impossible fixture release");
            } catch (Exception failure) {
                throw new IllegalStateException("Bounded interruption relay failed", failure);
            } finally {
                if (pipe != null)
                    for (ParcelFileDescriptor fd : pipe)
                        try {
                            fd.close();
                        } catch (Exception ignored) {
                        }
                if (task != null && !task.isDone())
                    try {
                        task.get(15, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        task.cancel(true);
                    }
            }
        }
    }
}
