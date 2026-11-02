package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.protocol.IBuildService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Real UI/Binder/compiler; test transport holds EOF during actual worker input, never a model. */
final class ActiveWorkerStopProbe {
    private static final String PREFS = "active-worker-stop-fixture-v5";
    private static volatile String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    private static void mark(Instrumentation inst, String name) {
        stage = name;
        android.os.Bundle result = new android.os.Bundle();
        result.putString("stream", "Active Stop stage=" + name + "\n");
        inst.sendStatus(0, result);
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        mark(inst, "GUARD");
        if (!java.util.List.of("prepare", "restart", "recover", "ready").contains(phase))
            throw new IllegalArgumentException("Unknown active worker Stop phase");
        SharedPreferences pins =
                inst.getTargetContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase) ? pins.contains("root") : !pins.getBoolean("stopped", false))
            throw new AssertionError("Fixture unavailable or already reserved");
        if ("prepare".equals(phase)
                && !pins.edit()
                        .putString("root", UUID.randomUUID().toString().replace("-", ""))
                        .commit()) throw new AssertionError("Fixture reservation failed");
        Intent intent =
                new Intent(inst.getTargetContext(), ProjectBindingProbeActivity.class)
                        .putExtra("bindingProbeId", pins.getString("root", ""))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mark(inst, "OPEN");
        Activity activity = inst.startActivitySync(intent);
        PausedTransfer relay = null;
        PhoneAlphaCounterProbe.onMain(
                inst,
                () -> {
                    TextBox editor = (TextBox) field(activity, "source");
                    // Test assistance: bound display of the large synthetic comment, not source
                    // bytes.
                });
        try {
            ControllerUi.awaitProjectBinding(inst, activity);
            IBuildService real = awaitWorker(inst, activity);
            if ("prepare".equals(phase)) {
                mark(inst, "CREATE_PROJECT");
                String title = "Active worker Stop fixture";
                Ui.openMenu(inst, activity);
                visible(inst, activity, Labels.NEW_PROJECT);
                android.view.accessibility.AccessibilityNodeInfo input =
                        PhoneAlphaCounterProbe.waitNode(
                                inst, activity, "android.widget.EditText", null);
                android.os.Bundle text = new android.os.Bundle();
                text.putCharSequence(
                        android.view.accessibility.AccessibilityNodeInfo
                                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        title);
                if (!input.performAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                    throw new AssertionError("Project title could not be entered");
                PhoneAlphaCounterProbe.click(inst, activity, "Create");
                ProjectIdentity project = awaitProject(inst, activity, title);
                if (!project.id().matches("app[0-9a-f]{32}"))
                    throw new AssertionError("Fixture is not a new project");
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (!source.isPristineDefaultSnapshot() || source.revision() != 0)
                    throw new AssertionError("Fixture project was previously used");
                java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
                keys.load(null);
                if (keys.containsAlias("generated-" + project.id()))
                    throw new AssertionError("Fixture signer existed before test");
                StringBuilder edited = new StringBuilder(source.readSource()).append("\n/*");
                Random random = new Random(6141);
                // A valid incompressible comment fills the pipe so reader consumption is
                // observable.
                for (int i = 0; i < 24000; i++) edited.append((char) ('A' + random.nextInt(26)));
                edited.append("*/\n");
                String expected = edited.toString();
                if (expected.getBytes(StandardCharsets.UTF_8).length > 64 * 1024)
                    throw new AssertionError("Fixture exceeds admitted source bound");
                relay = new PausedTransfer(real, project.packageName(), hash(expected));
                PausedTransfer installed = relay;
                mark(inst, "START_BUILD");
                PhoneAlphaCounterProbe.onMain(
                        inst,
                        () -> {
                            setField(activity, "worker", installed);
                            ((TextBox) field(activity, "source")).setText(expected);
                        });
                mark(inst, "SHOW_TOOLS");
                showTools(inst, activity);
                mark(inst, "CLICK_BUILD");
                visible(inst, activity, Labels.BUILD_SOURCE);
                mark(inst, "OBSERVE_ACTIVE_WORKER");
                if (!relay.draining.await(5, TimeUnit.SECONDS) || relay.finished.getCount() == 0)
                    throw new AssertionError(
                            "Real worker read progress was not observed while request remained active");
                try {
                    real.toolchainProbe();
                    throw new AssertionError("Real worker admission was not held");
                } catch (IllegalStateException busy) {
                    if (!"ERROR: Worker busy".equals(busy.getMessage())) throw busy;
                }
                if (!hash(expected).equals(relay.admittedMainSha256))
                    throw new AssertionError(
                            "Worker did not receive the exact leased fixture source");
                mark(inst, "STOP_ACTIVE_WORKER");
                visible(inst, activity, Labels.STOP);
                if (PhoneAlphaCounterProbe.mainValue(
                        inst, () -> ((Control) field(activity, "stop")).isEnabled()))
                    throw new AssertionError("Stop remained enabled after cancellation");
                relay.release.countDown();
                mark(inst, "SETTLE_LATE_BUILD");
                awaitIdle(inst, activity);
                if (!"OK".equals(relay.result) || relay.calls != 1)
                    throw new AssertionError(
                            "Actual stopped request did not finish one native build: "
                                    + relay.result);
                File late = new File(project.directory(), "artifacts/app-v1.apk");
                validateApk(activity, project, late);
                requireStopped(inst, activity, project, source, 1, hash(expected));
                if (!pins.edit()
                        .putString("project", project.id())
                        .putString("package", project.packageName())
                        .putString("sourceSha256", hash(expected))
                        .putString("lateSha256", VerifiedArtifact.capture(late).sha256())
                        .putBoolean("stopped", true)
                        .commit()) throw new AssertionError("Stopped fixture metadata not saved");
                return "Active Stop: exact leased snapshot forwarded; admitted real worker observed draining private pipe; actual visible Stop then EOF release; one native build/sign completed late; committed revision1 preserved, no receipt/Install readiness. Controlled transfer, not ECJ-phase cancellation. project="
                        + project.id()
                        + " sourceSha256="
                        + hash(expected)
                        + " lateApkSha256="
                        + VerifiedArtifact.capture(late).sha256()
                        + "\n";
            }
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(activity, "activeProject"));
            if (!pins.getString("project", "").equals(project.id())
                    || !pins.getString("package", "").equals(project.packageName()))
                throw new AssertionError("Restart restored another project");
            ProjectSourceStore source = new ProjectSourceStore(project);
            if ("restart".equals(phase)) {
                mark(inst, "VERIFY_STOPPED_RESTART");
                requireStopped(
                        inst, activity, project, source, 1, pins.getString("sourceSha256", ""));
                File late = new File(project.directory(), "artifacts/app-v1.apk");
                validateApk(activity, project, late);
                if (!pins.getString("lateSha256", "")
                        .equals(VerifiedArtifact.capture(late).sha256()))
                    throw new AssertionError("Late artifact changed during restart");
                return "Active Stop restart: saved revision1 and late signed APK retained; no receipt, automatic build or Install readiness resurrected\n";
            }
            if ("recover".equals(phase)) {
                if (pins.getBoolean("recovered", false))
                    throw new AssertionError("Recovery already attempted");
                requireStopped(
                        inst, activity, project, source, 1, pins.getString("sourceSha256", ""));
                mark(inst, "RECOVER_FRESH_BUILD");
                showTools(inst, activity);
                visible(inst, activity, Labels.BUILD_SOURCE);
                awaitIdle(inst, activity);
                VerifiedArtifact artifact =
                        requireReady(
                                inst,
                                activity,
                                project,
                                source,
                                pins.getString("sourceSha256", ""));
                if (!pins.edit()
                        .putBoolean("recovered", true)
                        .putString("readySha256", artifact.sha256())
                        .commit()) throw new AssertionError("Recovery metadata not saved");
                return "Active Stop recovery: separately clicked fresh local Build committed revision2, compiled/signed/verified actual APK, persisted receipt and enabled Install; no installation. sha256="
                        + artifact.sha256()
                        + "\n";
            }
            mark(inst, "VERIFY_READY_RESTART");
            if (!pins.getBoolean("recovered", false))
                throw new AssertionError("No successful recovery was recorded");
            VerifiedArtifact ready =
                    requireReady(
                            inst, activity, project, source, pins.getString("sourceSha256", ""));
            if (!pins.getString("readySha256", "").equals(ready.sha256()))
                throw new AssertionError("Recovered artifact changed on restart");
            return "Active Stop ready restart: exact revision2 receipt/APK restored with verified Install readiness; no automatic rebuild or installation\n";
        } finally {
            if (relay != null) relay.release.countDown();
            PhoneAlphaCounterProbe.onMain(inst, activity::finish);
        }
    }

    static void requireStopped(
            Instrumentation inst,
            Activity activity,
            ProjectIdentity project,
            ProjectSourceStore source,
            int revision,
            String expectedHash)
            throws Exception {
        if (source.revision() != revision
                || !expectedHash.equals(hash(source.readSource()))
                || Files.exists(
                        new File(project.directory(), "artifacts/verified-artifact.properties")
                                .toPath())
                || PhoneAlphaCounterProbe.mainValue(
                        inst,
                        () ->
                                field(activity, "signedArtifact") != null
                                        || ((Control) field(activity, "install")).isEnabled()
                                        || (boolean) field(activity, "buildInProgress")))
            throw new AssertionError(
                    "Stopped build lost source or published/resumed late artifact");
        if (new RunJournal(activity, project.id()).hasAnyAttempt())
            throw new AssertionError("Manual fixture unexpectedly acquired automatic run history");
    }

    static VerifiedArtifact requireReady(
            Instrumentation inst,
            Activity activity,
            ProjectIdentity project,
            ProjectSourceStore source,
            String expectedHash)
            throws Exception {
        if (source.revision() != 2 || !expectedHash.equals(hash(source.readSource())))
            throw new AssertionError("Fresh retry changed the saved source unexpectedly");
        VerifiedArtifact artifact =
                PhoneAlphaCounterProbe.mainValue(
                        inst, () -> (VerifiedArtifact) field(activity, "signedArtifact"));
        if (artifact == null
                || !PhoneAlphaCounterProbe.mainValue(
                        inst, () -> ((Control) field(activity, "install")).isEnabled()))
            throw new AssertionError("Fresh verified retry did not enable installation");
        artifact.verifyUnchanged();
        validateApk(activity, project, artifact.file());
        SignedArtifactReceipt.Record receipt =
                SignedArtifactReceipt.read(
                        project.directory(), project.id(), project.packageName(), 2);
        if (!artifact.sha256().equals(receipt.sha256))
            throw new AssertionError("Fresh retry did not persist its exact receipt");
        return artifact;
    }

    private static void validateApk(Context context, ProjectIdentity project, File apk)
            throws Exception {
        ArtifactValidator.validateUnsigned(context, apk, 1, project.packageName());
        if (!new ApkVerifier.Builder(apk).build().verify().isVerified())
            throw new AssertionError("Fixture APK signature is invalid");
    }

    static void visible(Instrumentation inst, Activity activity, String label) throws Exception {
        Ui.tap(inst, activity, label);
    }

    static void showTools(Instrumentation inst, Activity activity) throws Exception {
        Ui.showTools(inst, activity, activity);
    }

    static void awaitIdle(Instrumentation inst, Activity activity) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (PhoneAlphaCounterProbe.mainValue(
                inst, () -> (boolean) field(activity, "buildInProgress"))) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Local build did not settle");
            Thread.sleep(25);
        }
        inst.waitForIdleSync();
    }

    static IBuildService awaitWorker(Instrumentation inst, Activity activity) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            IBuildService worker =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (IBuildService) field(activity, "worker"));
            if (worker != null) return worker;
            Thread.sleep(25);
        }
        throw new AssertionError("Worker unavailable");
    }

    static ProjectIdentity awaitProject(Instrumentation inst, Activity activity, String title)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            ProjectIdentity project =
                    PhoneAlphaCounterProbe.mainValue(
                            inst, () -> (ProjectIdentity) field(activity, "activeProject"));
            if (project != null && title.equals(project.title())) {
                ControllerUi.awaitProjectBinding(inst, activity);
                return project;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("New project was not selected");
    }

    private static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    private static void setField(Activity activity, String name, Object value) throws Exception {
        Ui.setField(activity, name, value);
    }

    private static String hash(String source) throws Exception {
        StringBuilder text = new StringBuilder();
        for (byte value :
                MessageDigest.getInstance("SHA-256")
                        .digest(source.getBytes(StandardCharsets.UTF_8)))
            text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }

    /** Exact real snapshot relay: draining a prefilled pipe proves active remote input progress. */
    static final class PausedTransfer extends IBuildService.Stub {
        final IBuildService real;
        final CountDownLatch draining = new CountDownLatch(1),
                release = new CountDownLatch(1),
                finished = new CountDownLatch(1);
        volatile String result;
        volatile Throwable failure;
        volatile int calls;
        volatile String admittedMainSha256;
        final String packageName, expectedMainSha256;
        final int holdSeconds;

        PausedTransfer(IBuildService real, String packageName, String expectedMainSha256) {
            this(real, packageName, expectedMainSha256, 3);
        }

        PausedTransfer(
                IBuildService real,
                String packageName,
                String expectedMainSha256,
                int holdSeconds) {
            if (holdSeconds < 1 || holdSeconds > 15)
                throw new IllegalArgumentException("Invalid fixture hold");
            this.real = real;
            this.packageName = packageName;
            this.expectedMainSha256 = expectedMainSha256;
            this.holdSeconds = holdSeconds;
        }

        @Override
        public String handshake(int protocol) throws RemoteException {
            return real.handshake(protocol);
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
        public String build(ParcelFileDescriptor input, ParcelFileDescriptor apk) {
            throw new AssertionError("Unexpected legacy dispatch");
        }

        @Override
        public String buildWithDiagnostics(
                ParcelFileDescriptor input, ParcelFileDescriptor apk, ParcelFileDescriptor report) {
            calls++;
            ParcelFileDescriptor[] pipe = null;
            FutureTask<String> remote = null;
            try {
                byte[] bytes;
                try (InputStream source =
                        new ParcelFileDescriptor.AutoCloseInputStream(
                                ParcelFileDescriptor.dup(input.getFileDescriptor()))) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = source.read(chunk)) != -1) {
                        if (buffer.size() + count > 512 * 1024)
                            throw new AssertionError("Fixture archive exceeds relay bound");
                        buffer.write(chunk, 0, count);
                    }
                    bytes = buffer.toByteArray();
                }
                String mainPath = "java/" + packageName.replace('.', '/') + "/MainActivity.java";
                int matches = 0;
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!mainPath.equals(entry.getName())) continue;
                        ByteArrayOutputStream source = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int count;
                        while ((count = zip.read(chunk)) != -1) {
                            if (source.size() + count > 256 * 1024)
                                throw new AssertionError("Leased source exceeds bound");
                            source.write(chunk, 0, count);
                        }
                        admittedMainSha256 =
                                hash(new String(source.toByteArray(), StandardCharsets.UTF_8));
                        matches++;
                    }
                }
                if (matches != 1 || !expectedMainSha256.equals(admittedMainSha256))
                    throw new AssertionError(
                            "Leased snapshot does not contain the exact fixture Main source");
                pipe = ParcelFileDescriptor.createPipe();
                // Linux F_SETPIPE_SZ (1024 + 7): shrink only this disposable pipe.
                // An admitted <64KiB source can then exercise bounded transport independently of
                // scheduling.
                if (Os.fcntlInt(pipe[1].getFileDescriptor(), 1031, 4096) != 4096)
                    throw new AssertionError("Fixture pipe capacity was not established");
                int flags = Os.fcntlInt(pipe[1].getFileDescriptor(), OsConstants.F_GETFL, 0);
                Os.fcntlInt(
                        pipe[1].getFileDescriptor(),
                        OsConstants.F_SETFL,
                        flags | OsConstants.O_NONBLOCK);
                int sent = 0;
                boolean full = false;
                // Fill before the remote reader exists: observation cannot depend on scheduler
                // speed.
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
                if (!full)
                    throw new AssertionError("Fixture archive did not fill its private pipe");
                ParcelFileDescriptor reader = pipe[0];
                remote = new FutureTask<>(() -> real.buildWithDiagnostics(reader, apk, report));
                FutureTask<String> task = remote;
                Thread caller =
                        new Thread(
                                () -> {
                                    try {
                                        task.run();
                                    } finally {
                                        finished.countDown();
                                    }
                                },
                                "actual-worker-stop-relay");
                caller.setDaemon(true);
                caller.start();
                boolean drained = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (sent < bytes.length) {
                    if (remote.isDone() || System.nanoTime() >= deadline)
                        throw new AssertionError(
                                "Worker did not consume exact snapshot within relay deadline");
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
                if (!drained || remote.isDone())
                    throw new AssertionError(
                            "No prefilled-pipe drain was observed for active remote worker");
                draining.countDown();
                if (!release.await(holdSeconds, TimeUnit.SECONDS))
                    throw new AssertionError("Stop did not release bounded fixture transfer");
                pipe[1].close(); // EOF now permits the actual compiler, which may finish after
                // Stop.
                result = remote.get(20, TimeUnit.SECONDS);
                return result;
            } catch (Exception failure) {
                this.failure = failure;
                throw new IllegalStateException("Controlled actual worker relay failed", failure);
            } finally {
                if (pipe != null)
                    for (ParcelFileDescriptor fd : pipe)
                        try {
                            fd.close();
                        } catch (Exception ignored) {
                        }
                if (remote != null && !remote.isDone()) {
                    try {
                        remote.get(15, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        remote.cancel(true);
                    }
                }
            }
        }
    }
}
