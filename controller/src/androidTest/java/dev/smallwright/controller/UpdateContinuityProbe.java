package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import com.android.apksig.ApkVerifier;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.json.JSONObject;

/** Builds two revisions through the real worker; does not install either generated APK. */
final class UpdateContinuityProbe {
    static String run(Context controller) throws Exception {
        File root =
                Files.createTempDirectory(controller.getFilesDir().toPath(), "update-continuity-")
                        .toFile();
        File cache = new File(root, "cache");
        if (!cache.mkdir()) throw new AssertionError("Update probe cache unavailable");
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
        String original = source.readSource();
        if (!original.contains("Increment")) throw new AssertionError("Default source changed");

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
        if (!controller.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw new AssertionError("Compiler worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null) {
                throw new AssertionError("Compiler worker unavailable");
            }
            LocalBuildCoordinator coordinator = new LocalBuildCoordinator(isolated, source);
            int firstRevision =
                    source.writeSource(0, original.replace("Increment", "FirstRevisionButton"));
            if (firstRevision != 1) throw new AssertionError("First revision was not committed");
            VerifiedArtifact firstArtifact =
                    coordinator.buildCommittedRevision(firstRevision, 1, worker.get());
            firstArtifact.verifyUnchanged();
            requireNoUnsignedApks(root);
            File firstApk = new File(root, "projects/counter-alpha/artifacts/counter-v1.apk");
            PackageInfo firstPackage = inspectApk(controller, firstApk, "FirstRevisionButton");
            List<X509Certificate> firstSigners = verifiedSigners(firstApk);

            int secondRevision =
                    source.writeSource(
                            firstRevision, original.replace("Increment", "SecondRevisionButton"));
            if (secondRevision != 2) throw new AssertionError("Second revision was not committed");
            VerifiedArtifact secondArtifact =
                    coordinator.buildCommittedRevision(secondRevision, 2, worker.get());
            secondArtifact.verifyUnchanged();
            requireNoUnsignedApks(root);
            File secondApk = new File(root, "projects/counter-alpha/artifacts/counter-v2.apk");
            PackageInfo secondPackage = inspectApk(controller, secondApk, "SecondRevisionButton");
            List<X509Certificate> secondSigners = verifiedSigners(secondApk);

            IBuildService failedWorker =
                    new IBuildService.Stub() {
                        @Override
                        public String handshake(int version) {
                            throw new IllegalArgumentException("Simulated legacy worker");
                        }

                        @Override
                        public String aapt2Version() {
                            throw new AssertionError("Unexpected AAPT2 probe");
                        }

                        @Override
                        public String toolchainProbe() {
                            throw new AssertionError("Unexpected toolchain probe");
                        }

                        @Override
                        public String usePlatform(ParcelFileDescriptor platform) {
                            return "OK";
                        }

                        @Override
                        public String useLibraries(
                                ParcelFileDescriptor[] libraries, String[] sha256) {
                            return "OK";
                        }

                        @Override
                        public String build(
                                ParcelFileDescriptor snapshot, ParcelFileDescriptor artifact) {
                            return "ERROR: simulated worker failure";
                        }

                        @Override
                        public String buildWithDiagnostics(
                                ParcelFileDescriptor snapshot,
                                ParcelFileDescriptor artifact,
                                ParcelFileDescriptor diagnostics) {
                            throw new AssertionError("Legacy worker received a detailed build");
                        }
                    };
            try {
                coordinator.buildCommittedRevision(secondRevision, 3, failedWorker);
                throw new AssertionError("Simulated worker failure produced an APK");
            } catch (java.io.IOException expected) {
                requireNoUnsignedApks(root);
            }

            if (!ProjectSourceStore.PACKAGE_NAME.equals(firstPackage.packageName)
                    || !firstPackage.packageName.equals(secondPackage.packageName)) {
                throw new AssertionError("Generated package identity changed across revisions");
            }
            if (firstPackage.getLongVersionCode() != 1
                    || secondPackage.getLongVersionCode() != 2
                    || secondPackage.getLongVersionCode() <= firstPackage.getLongVersionCode()) {
                throw new AssertionError("Generated version did not increase from 1 to 2");
            }
            if (firstSigners.size() != 1
                    || secondSigners.size() != 1
                    || !MessageDigest.isEqual(
                            firstSigners.get(0).getEncoded(), secondSigners.get(0).getEncoded())) {
                throw new AssertionError("Generated signing certificate changed across revisions");
            }
            return "Update continuity: two real worker builds from revisions 1 and 2; "
                    + "stable package, versions 1 to 2, same verified signing certificate; "
                    + "generated APKs were not installed\n"
                    + "Verified first artifact: "
                    + identity(firstArtifact, firstPackage, firstSigners)
                    + "\nVerified second artifact: "
                    + identity(secondArtifact, secondPackage, secondSigners)
                    + "\n";
        } finally {
            controller.unbindService(connection);
        }
    }

    private static String identity(
            VerifiedArtifact artifact, PackageInfo info, List<X509Certificate> signers)
            throws Exception {
        artifact.verifyUnchanged();
        StringBuilder certificate = new StringBuilder(64);
        for (byte value :
                MessageDigest.getInstance("SHA-256").digest(signers.get(0).getEncoded())) {
            certificate.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        }
        return new JSONObject()
                .put("path", artifact.file().getAbsolutePath())
                .put("sha256", artifact.sha256())
                .put("bytes", artifact.length())
                .put("package", info.packageName)
                .put("version", info.getLongVersionCode())
                .put("certificateSha256", certificate.toString())
                .toString();
    }

    private static void requireNoUnsignedApks(File root) {
        File artifacts = new File(root, "projects/counter-alpha/artifacts");
        File[] unsigned =
                artifacts.listFiles(
                        (directory, name) -> name.startsWith("unsigned-") && name.endsWith(".apk"));
        if (unsigned == null || unsigned.length != 0)
            throw new AssertionError("Temporary unsigned APK remained after build");
    }

    private static PackageInfo inspectApk(Context context, File apk, String marker)
            throws Exception {
        PackageInfo info =
                context.getPackageManager()
                        .getPackageArchiveInfo(
                                apk.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
        if (info == null) throw new AssertionError("Generated APK could not be parsed");
        if (info.requestedPermissions != null && info.requestedPermissions.length != 0
                || info.permissions != null && info.permissions.length != 0) {
            throw new AssertionError("Generated counter declares or requests permissions");
        }
        try (ZipFile zip = new ZipFile(apk)) {
            if (zip.getEntry("classes.dex") == null)
                throw new AssertionError("Generated APK lacks DEX");
            String dex =
                    new String(
                            zip.getInputStream(zip.getEntry("classes.dex")).readAllBytes(),
                            StandardCharsets.ISO_8859_1);
            if (!dex.contains(marker))
                throw new AssertionError("Generated APK lacks committed revision");
        }
        return info;
    }

    private static List<X509Certificate> verifiedSigners(File apk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk).build().verify();
        if (!result.isVerified() || result.getSignerCertificates().isEmpty()) {
            throw new AssertionError("Generated APK signature did not verify");
        }
        return result.getSignerCertificates();
    }
}
