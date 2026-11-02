package dev.smallwright.controller;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disposable project in an isolated registry, built by the real worker with the downloaded Android
 * platform files; no model request is made. The other cases check refusals.
 */
final class PlatformBuildProbe {
    private PlatformBuildProbe() {}

    static String run(Instrumentation in, String kind) throws Exception {
        if (!Arrays.asList("app", "wrongInput", "workerMissing", "controllerMissing")
                .contains(kind)) throw new IllegalArgumentException("Unknown case " + kind);
        Context target = in.getTargetContext();
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "platform-build-")
                        .toFile();
        try {
            return runIn(in, target, root, kind);
        } finally {
            deleteTree(root);
        }
    }

    private static String runIn(Instrumentation in, Context target, File root, String kind)
            throws Exception {
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Probe cache unavailable");
        Context emptyApplication = isolated(target, root, cache, null);
        Context isolated =
                isolated(
                        target,
                        root,
                        cache,
                        kind.equals("controllerMissing") ? emptyApplication : null);
        ProjectIdentity project =
                new ProjectRegistry(new File(root, "projects"), root).create("Platform files");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String authored;
        try (InputStream asset =
                in.getContext().getAssets().open("platform-fixtures/MainActivity.java")) {
            authored =
                    new String(asset.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("$PACKAGE", project.packageName());
        }
        int revision = source.writeSource(0, authored);
        return withWorker(
                target,
                worker -> {
                    switch (kind) {
                        case "app":
                            return buildApp(target, isolated, source, project, revision, worker);
                        case "wrongInput":
                            return wrongInput(target, cache, worker);
                        case "workerMissing":
                            return workerMissing(source, cache, revision, worker);
                        default:
                            return controllerMissing(isolated, source, project, revision, worker);
                    }
                });
    }

    private static String buildApp(
            Context target,
            Context isolated,
            ProjectSourceStore source,
            ProjectIdentity project,
            int revision,
            IBuildService worker)
            throws Exception {
        long start = SystemClock.elapsedRealtime();
        VerifiedArtifact artifact =
                new LocalBuildCoordinator(isolated, source, project)
                        .buildCommittedRevision(revision, 1, worker);
        long buildMillis = SystemClock.elapsedRealtime() - start;
        artifact.verifyUnchanged();
        String toolchain = worker.toolchainProbe();
        if (!toolchain.startsWith("ECJ valid class and invalid-source diagnostic"))
            throw new AssertionError("Unexpected toolchain probe answer: " + toolchain);
        File apk = new File(project.directory(), "artifacts/app-v1.apk");
        ArtifactValidator.validateUnsigned(target, apk, 1, project.packageName());
        String builderLink = builderLink(target, apk, project.packageName());
        File kept =
                new File(target.getFilesDir(), "platform-build-" + project.packageName() + ".apk");
        Files.copy(apk.toPath(), kept.toPath());
        return "PlatformBuild case=app package="
                + project.packageName()
                + " buildMs="
                + buildMillis
                + " apkBytes="
                + apk.length()
                + " apkSha256="
                + artifact.sha256()
                + " toolchainProbe="
                + toolchain
                + " builderLink="
                + builderLink
                + " apkPath="
                + kept.getAbsolutePath()
                + "\n";
    }

    /** The builder's components the built APK declares (ADR 0008). */
    private static String builderLink(Context target, File apk, String packageName) {
        android.content.pm.PackageInfo info =
                target.getPackageManager()
                        .getPackageArchiveInfo(
                                apk.getAbsolutePath(),
                                android.content.pm.PackageManager.GET_ACTIVITIES
                                        | android.content.pm.PackageManager.GET_PROVIDERS);
        if (info == null || info.activities == null || info.providers == null)
            throw new AssertionError("Built APK declares no builder components");
        boolean look = false;
        boolean crash = false;
        boolean starter = false;
        for (android.content.pm.ActivityInfo activity : info.activities) {
            if (activity.name.equals(AppLinkSources.component(packageName, AppLinkSources.LOOK)))
                look = activity.exported && packageName.equals(activity.taskAffinity);
            if (activity.name.equals(AppLinkSources.component(packageName, AppLinkSources.CRASH)))
                crash =
                        !activity.exported
                                && activity.processName.endsWith(":builderlink")
                                && (packageName + ".builderlink").equals(activity.taskAffinity);
        }
        for (android.content.pm.ProviderInfo provider : info.providers)
            if (provider.name.equals(AppLinkSources.component(packageName, AppLinkSources.STARTER)))
                starter =
                        !provider.exported
                                && provider.authority.equals(packageName + ".builderlink");
        if (!look || !crash || !starter)
            throw new AssertionError(
                    "Builder components look=" + look + " crash=" + crash + " starter=" + starter);
        return "look,crash,starter";
    }

    private static String wrongInput(Context target, File cache, IBuildService worker)
            throws Exception {
        File google = googleJar(target);
        byte[] changed = Files.readAllBytes(google.toPath());
        changed[changed.length / 2] ^= 1;
        File sameSize = new File(cache, "changed-android.jar");
        Files.write(sameSize.toPath(), changed);
        File shorter = new File(cache, "short-android.jar");
        Files.write(shorter.toPath(), Arrays.copyOf(changed, 1024));
        String changedResult = usePlatform(worker, sameSize);
        String shorterResult = usePlatform(worker, shorter);
        String nullResult = worker.usePlatform(null);
        String googleResult = usePlatform(worker, google);
        String mismatch =
                "ERROR: The Android platform files given to the compiler do not match the pinned"
                        + " API 36 file";
        if (!mismatch.equals(changedResult)
                || !mismatch.equals(shorterResult)
                || !"ERROR: The Android platform files were not given to the compiler"
                        .equals(nullResult)
                || !"OK".equals(googleResult))
            throw new AssertionError(
                    "Unexpected worker answers: "
                            + changedResult
                            + " | "
                            + shorterResult
                            + " | "
                            + nullResult
                            + " | "
                            + googleResult);
        return "PlatformBuild case=wrongInput sameSizeChanged=["
                + changedResult
                + "] shorter=["
                + shorterResult
                + "] none=["
                + nullResult
                + "] google=["
                + googleResult
                + "]\n";
    }

    private static String workerMissing(
            ProjectSourceStore source, File cache, int revision, IBuildService worker)
            throws Exception {
        File snapshot = new File(cache, "snapshot.zip");
        File apk = new File(cache, "unsigned.apk");
        File report = new File(cache, "report.bin");
        Files.createFile(apk.toPath());
        Files.createFile(report.toPath());
        String result;
        try (ProjectStore.BuildLease lease = source.beginBuild(revision, snapshot, 1);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_WRITE);
                ParcelFileDescriptor diagnostics =
                        ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_WRITE)) {
            result = worker.buildWithDiagnostics(input, output, diagnostics);
        }
        if (!"ERROR: The compiler does not have the Android platform files".equals(result)
                || apk.length() != 0)
            throw new AssertionError("Worker built without platform files: " + result);
        return "PlatformBuild case=workerMissing result=[" + result + "]\n";
    }

    private static String controllerMissing(
            Context isolated,
            ProjectSourceStore source,
            ProjectIdentity project,
            int revision,
            IBuildService worker)
            throws Exception {
        try {
            new LocalBuildCoordinator(isolated, source, project)
                    .buildCommittedRevision(revision, 1, worker);
        } catch (IOException refused) {
            if (!refused.getMessage().contains("Android platform files"))
                throw new AssertionError("Unclear refusal: " + refused.getMessage());
            if (new File(project.directory(), "artifacts/app-v1.apk").exists())
                throw new AssertionError("Refused build left an APK");
            return "PlatformBuild case=controllerMissing refused=[" + refused.getMessage() + "]\n";
        }
        throw new AssertionError("Built without the Android platform files");
    }

    private static String usePlatform(IBuildService worker, File file) throws Exception {
        try (ParcelFileDescriptor input =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return worker.usePlatform(input);
        }
    }

    private static File googleJar(Context target) throws IOException {
        return new PlatformFiles(
                        new File(target.getNoBackupFilesDir(), "platform"),
                        PlatformFiles.GOOGLE_36_R02)
                .verifiedJar();
    }

    private static Context isolated(
            Context target, File root, File cache, Context applicationContext) {
        return new ContextWrapper(target) {
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

            @Override
            public Context getApplicationContext() {
                return applicationContext == null
                        ? super.getApplicationContext()
                        : applicationContext;
            }
        };
    }

    interface WorkerCase {
        String run(IBuildService worker) throws Exception;
    }

    private static String withWorker(Context target, WorkerCase work) throws Exception {
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
        Intent bind =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(bind, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            return work.run(worker.get());
        } finally {
            target.unbindService(connection);
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
