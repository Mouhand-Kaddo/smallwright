package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import com.android.apksig.ApkVerifier;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable emulator proof that distinct projects build and retain distinct signers. */
final class SecondProjectBuildProbe {
    static String run(Context target) throws Exception {
        File root =
                Files.createTempDirectory(target.getFilesDir().toPath(), "second-project-build-")
                        .toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Probe cache unavailable");
        Context isolated =
                new ContextWrapper(target) {
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
        ProjectRegistry registry = new ProjectRegistry(new File(root, "projects"), root);
        ProjectIdentity legacy = registry.ensureLegacyProject();
        ProjectIdentity second = registry.create("Second project probe");
        ProjectSourceStore oldSource = new ProjectSourceStore(legacy);
        ProjectSourceStore newSource = new ProjectSourceStore(second);
        oldSource.initialize();
        newSource.initialize();
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
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            VerifiedArtifact original =
                    new LocalBuildCoordinator(isolated, oldSource, legacy)
                            .buildCommittedRevision(0, 1, remote.get());
            VerifiedArtifact first =
                    new LocalBuildCoordinator(isolated, newSource, second)
                            .buildCommittedRevision(0, 1, remote.get());
            VerifiedArtifact update =
                    new LocalBuildCoordinator(isolated, newSource, second)
                            .buildCommittedRevision(0, 2, remote.get());
            original.verifyUnchanged();
            first.verifyUnchanged();
            update.verifyUnchanged();
            File originalFile = new File(legacy.directory(), "artifacts/counter-v1.apk");
            File secondFile = new File(second.directory(), "artifacts/app-v1.apk");
            File updateFile = new File(second.directory(), "artifacts/app-v2.apk");
            requireArtifact(original, originalFile);
            requireArtifact(first, secondFile);
            requireArtifact(update, updateFile);
            first.verifyPackage(isolated, second.packageName());
            try {
                first.verifyPackage(isolated, legacy.packageName());
                throw new AssertionError("Artifact accepted another project's package");
            } catch (java.io.IOException expected) {
                /* install package binding */
            }
            requirePackage(isolated, originalFile, legacy.packageName(), 1);
            requirePackage(isolated, secondFile, second.packageName(), 1);
            requirePackage(isolated, updateFile, second.packageName(), 2);
            try {
                ArtifactValidator.validateUnsigned(isolated, secondFile, 1, legacy.packageName());
                throw new AssertionError("Another project's artifact was accepted");
            } catch (java.io.IOException expected) {
                /* package binding */
            }
            try {
                new LocalBuildCoordinator(isolated, oldSource, second);
                throw new AssertionError("Source/project mismatch was accepted");
            } catch (IllegalArgumentException expected) {
                /* source binding */
            }
            byte[] originalCert = certificate(originalFile);
            byte[] newCert = certificate(secondFile);
            if (Arrays.equals(originalCert, newCert)
                    || !Arrays.equals(newCert, certificate(updateFile))) {
                throw new AssertionError("Project signing identities were reused or changed");
            }
            if (oldSource.revision() != 0 || newSource.revision() != 0)
                throw new AssertionError("Build altered a source revision");
            return "Second project: distinct packages and signers; same signer across v1/v2; "
                    + "phone-local worker build and controller validation passed\n";
        } finally {
            target.unbindService(connection);
        }
    }

    private static void requireArtifact(VerifiedArtifact artifact, File file) throws Exception {
        if (!artifact.sha256().equals(VerifiedArtifact.capture(file).sha256()))
            throw new AssertionError("Built artifact path does not match verified result");
    }

    private static void requirePackage(Context context, File file, String packageName, int version)
            throws Exception {
        android.content.pm.PackageInfo info =
                context.getPackageManager()
                        .getPackageArchiveInfo(
                                file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        if (info == null
                || !packageName.equals(info.packageName)
                || info.getLongVersionCode() != version) {
            throw new AssertionError("Generated package/version does not match project");
        }
    }

    private static byte[] certificate(File file) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(file).build().verify();
        if (!result.isVerified() || result.getSignerCertificates().size() != 1)
            throw new AssertionError("Generated APK signature is invalid");
        return result.getSignerCertificates().get(0).getEncoded();
    }
}
