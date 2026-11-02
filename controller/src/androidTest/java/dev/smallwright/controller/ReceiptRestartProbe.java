package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runs in separate instrumentation invocations around external controller force-stops. */
final class ReceiptRestartProbe {
    private static final String RECEIPT = "verified-artifact.properties";
    private static final String BACKUP = "verified-artifact.properties.receipt-probe-backup";
    private static final String MARKER = "receipt-restart-probe-id";

    private ReceiptRestartProbe() {}

    static String prepare(Context target) throws Exception {
        File project = project(target);
        File artifacts = new File(project, "artifacts");
        if (!artifacts.mkdirs() && !artifacts.isDirectory())
            throw new AssertionError("Artifact directory unavailable");
        File backup = new File(artifacts, BACKUP);
        File marker = new File(target.getNoBackupFilesDir(), MARKER);
        if (backup.exists() || marker.exists())
            throw new AssertionError("Receipt probe already prepared");
        String id = "receipt_probe_" + UUID.randomUUID().toString().replace("-", "");
        ProjectSourceStore current = new ProjectSourceStore(target);
        int revision = current.revision();
        File isolated =
                Files.createTempDirectory(target.getNoBackupFilesDir().toPath(), "receipt-source-")
                        .toFile();
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> worker = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        worker.set(IBuildService.Stub.asInterface(service));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        worker.set(null);
                    }
                };
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(target, source)
                            .buildCommittedRevision(0, 1, id, worker.get());
            File receipt = new File(artifacts, RECEIPT);
            if (receipt.exists()) Files.copy(receipt.toPath(), backup.toPath());
            try {
                SignedArtifactReceipt.publish(
                        target,
                        project,
                        "counter-alpha",
                        ProjectSourceStore.PACKAGE_NAME,
                        revision,
                        1,
                        artifact);
                Files.write(marker.toPath(), id.getBytes(StandardCharsets.US_ASCII));
            } catch (Exception failure) {
                if (backup.exists())
                    Files.move(
                            backup.toPath(),
                            receipt.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                else Files.deleteIfExists(receipt.toPath());
                throw failure;
            }
            return "Real worker built and signed receipt probe APK; restart ID " + id;
        } finally {
            target.unbindService(connection);
        }
    }

    static String verifyAndTamper(Instrumentation instrumentation) throws Exception {
        Context target = instrumentation.getTargetContext();
        String id = readId(target);
        assertControls(instrumentation, true);
        Path receipt = new File(new File(project(target), "artifacts"), RECEIPT).toPath();
        String before = new String(Files.readAllBytes(receipt), StandardCharsets.ISO_8859_1);
        String after = before.replaceFirst("sha256=[0-9a-f]{64}", "sha256=" + "0".repeat(64));
        if (before.equals(after)) throw new AssertionError("Receipt SHA field unavailable");
        Files.write(receipt, after.getBytes(StandardCharsets.ISO_8859_1));
        return "Restart restored Install and Export; modified receipt digest for negative restart "
                + id;
    }

    static String verifyTamperedAndCleanup(Instrumentation instrumentation) throws Exception {
        Context target = instrumentation.getTargetContext();
        String id = readId(target);
        assertControls(instrumentation, false);
        File artifacts = new File(project(target), "artifacts");
        Path receipt = new File(artifacts, RECEIPT).toPath();
        Path backup = new File(artifacts, BACKUP).toPath();
        if (Files.exists(backup))
            Files.move(backup, receipt, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        else Files.delete(receipt);
        Files.delete(new File(target.getNoBackupFilesDir(), MARKER).toPath());
        File built = new File(new File(artifacts, id), "app.apk");
        Files.delete(built.toPath());
        Files.delete(built.getParentFile().toPath());
        return "Tampered receipt left Install and Export disabled after restart; prior receipt restored";
    }

    private static void assertControls(Instrumentation instrumentation, boolean enabled)
            throws Exception {
        Activity activity =
                instrumentation.startActivitySync(
                        new Intent(instrumentation.getTargetContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(instrumentation, activity);
            java.lang.reflect.Field executorField = MainActivity.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            ((ExecutorService) executorField.get(activity))
                    .submit(() -> {})
                    .get(25, TimeUnit.SECONDS);
            instrumentation.waitForIdleSync();
            long deadline = System.currentTimeMillis() + 20000;
            while (true) {
                AtomicReference<Boolean> ready = new AtomicReference<>(false);
                instrumentation.runOnMainSync(
                        () -> {
                            Control install = control(activity, Labels.INSTALL);
                            Control export = control(activity, Labels.EXPORT_APK);
                            ready.set(
                                    install.isEnabled() == enabled
                                            && export.isEnabled() == enabled);
                        });
                if (ready.get()) return;
                if (System.currentTimeMillis() >= deadline)
                    throw new AssertionError(
                            "Install/Export controls did not reach expected state");
                Thread.sleep(250);
            }
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static Control control(Activity activity, String label) {
        try {
            return Ui.button(activity, label);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String readId(Context target) throws Exception {
        String id =
                new String(
                        Files.readAllBytes(new File(target.getNoBackupFilesDir(), MARKER).toPath()),
                        StandardCharsets.US_ASCII);
        if (!id.matches("receipt_probe_[0-9a-f]{32}"))
            throw new AssertionError("Invalid receipt probe marker");
        return id;
    }

    private static File project(Context target) {
        return new File(target.getFilesDir(), "projects/counter-alpha");
    }
}
