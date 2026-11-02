package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

/** Builds a changed native APK under the installed worker's network-denied UID. */
final class LocalityProofProbe {
    private static final String WORKER_PACKAGE = "dev.smallwright.worker";
    private static final String INTERNET = "android.permission.INTERNET";

    static String run(Context controller) throws Exception {
        PackageManager packages = controller.getPackageManager();
        PackageInfo workerInfo =
                packages.getPackageInfo(WORKER_PACKAGE, PackageManager.GET_PERMISSIONS);
        if (workerInfo.applicationInfo.uid == controller.getApplicationInfo().uid) {
            throw new AssertionError("Compiler worker shares the controller UID");
        }
        if (packages.checkPermission(INTERNET, WORKER_PACKAGE) != PackageManager.PERMISSION_DENIED
                || contains(workerInfo.requestedPermissions, INTERNET)) {
            throw new AssertionError("Compiler worker has or requests Internet permission");
        }

        File root =
                Files.createTempDirectory(controller.getFilesDir().toPath(), "locality-proof-")
                        .toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Locality probe cache unavailable");
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

                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        String marker = "LocalityProofButton";
        String original = source.readSource();
        if (!original.contains("Increment")) throw new AssertionError("Default source changed");
        String changed = original.replace("Increment", marker);
        int revision = source.writeSource(0, changed);
        if (revision != 1) throw new AssertionError("Unexpected source revision");

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
                                        WORKER_PACKAGE, "dev.smallwright.worker.BuildService"));
        if (!controller.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw new AssertionError("Compiler worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null) {
                throw new AssertionError("Compiler worker unavailable");
            }
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(isolated, source)
                            .buildCommittedRevision(revision, 1, worker.get());
            artifact.verifyUnchanged();
            File apk = new File(root, "projects/counter-alpha/artifacts/counter-v1.apk");
            ArtifactValidator.validateUnsigned(isolated, apk, 1);
            PackageInfo generated =
                    packages.getPackageArchiveInfo(
                            apk.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
            if (generated == null
                    || !ProjectSourceStore.PACKAGE_NAME.equals(generated.packageName)
                    || generated.requestedPermissions != null
                            && generated.requestedPermissions.length != 0
                    || generated.permissions != null && generated.permissions.length != 0) {
                throw new AssertionError("Generated APK requests or declares permissions");
            }
            try (ZipFile zip = new ZipFile(apk)) {
                String dex =
                        new String(
                                zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes(),
                                StandardCharsets.ISO_8859_1);
                if (!dex.contains(marker))
                    throw new AssertionError("Changed source missing from compiled DEX");
            }
            return "Locality probe: controller UID "
                    + controller.getApplicationInfo().uid
                    + ", real worker build under UID "
                    + workerInfo.applicationInfo.uid
                    + " with INTERNET denied; changed revision "
                    + revision
                    + "; signed APK requests no permissions; SHA-256 "
                    + artifact.sha256()
                    + "; private APK "
                    + apk.getAbsolutePath()
                    + "\n";
        } finally {
            controller.unbindService(connection);
        }
    }

    private static boolean contains(String[] values, String wanted) {
        if (values == null) return false;
        for (String value : values) if (wanted.equals(value)) return true;
        return false;
    }
}
