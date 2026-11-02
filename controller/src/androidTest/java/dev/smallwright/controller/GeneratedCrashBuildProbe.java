package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable emulator candidate with a deliberate Java crash on counter tap. */
final class GeneratedCrashBuildProbe {
    private GeneratedCrashBuildProbe() {}

    static String run(Context controller) throws Exception {
        File root =
                Files.createTempDirectory(
                                controller.getFilesDir().toPath(), "generated-crash-probe-")
                        .toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Probe cache unavailable");
        Context isolated =
                new ContextWrapper(controller) {
                    @Override
                    public File getFilesDir() {
                        return root;
                    }

                    @Override
                    public File getCacheDir() {
                        return cache;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        String original = source.readSource();
        String changed =
                original.replace(
                        "count++; getPreferences(0)",
                        "count++; if (System.nanoTime() != 0) throw new IllegalStateException(\"TEST_SECRET_SENTINEL\"); getPreferences(0)");
        if (changed.equals(original)) throw new AssertionError("Crash insertion point changed");
        int revision = source.writeSource(0, changed);
        int version = 1;
        try {
            long installed =
                    controller
                            .getPackageManager()
                            .getPackageInfo(ProjectSourceStore.PACKAGE_NAME, 0)
                            .getLongVersionCode();
            if (installed < 0 || installed >= Integer.MAX_VALUE - 1)
                throw new AssertionError("No safe test version is available");
            version = (int) installed + 1;
        } catch (PackageManager.NameNotFoundException missing) {
            // Fresh emulator: version 1 is available.
        }

        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> worker = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        worker.set(IBuildService.Stub.asInterface(binder));
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
        if (!controller.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(isolated, source)
                            .buildCommittedRevision(revision, version, worker.get());
            artifact.verifyUnchanged();
            File apk =
                    new File(root, "projects/counter-alpha/artifacts/counter-v" + version + ".apk");
            return "Generated crash candidate: "
                    + apk.getAbsolutePath()
                    + " SHA-256 "
                    + artifact.sha256()
                    + "; not installed\n";
        } finally {
            controller.unbindService(connection);
        }
    }
}
