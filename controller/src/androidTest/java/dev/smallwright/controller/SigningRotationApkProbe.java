package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Produces phone-compiled rotation APKs for a preflight-clean, disposable emulator. */
final class SigningRotationApkProbe {
    private static final String INSTALLED_STATE = "rotation-installed-state.txt";

    /** Phase one ends before installation so phase two can migrate an installed v1 signer. */
    static String prepareInstalledProductV1(Context controller) throws Exception {
        File state = new File(controller.getNoBackupFilesDir(), INSTALLED_STATE);
        if (state.exists()) throw new AssertionError("Installed migration already started");
        try {
            controller.getPackageManager().getPackageInfo(ProjectSourceStore.PACKAGE_NAME, 0);
            throw new AssertionError("Generated package already installed");
        } catch (PackageManager.NameNotFoundException expected) {
            // The runner independently checks both installed and retained package settings.
        }
        String nonce = UUID.randomUUID().toString();
        File root = new File(controller.getNoBackupFilesDir(), "rotation-installed-" + nonce);
        File cache = new File(root, "cache");
        if (!cache.mkdirs()) throw new AssertionError("Probe cache unavailable");
        Context isolated = installedContext(controller, root, cache);
        File published = installedPublishedDirectory(controller, root);
        String oldAlias = installedOldAlias(nonce);
        String wrappingAlias = installedWrappingAlias(nonce);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        boolean ready = false;
        try {
            ProjectSourceStore source = new ProjectSourceStore(isolated);
            source.initialize();
            if (source.revision() != 0 || !source.readSource().contains("Counter: ")) {
                throw new AssertionError("Unexpected initial source");
            }
            File unsigned = buildUnsignedBound(controller, root, source, 0, 1);
            File signed = new File(published, "app-v1.apk");
            new ProjectSigner(
                            oldAlias,
                            new PortableSignerStore(
                                    new File(root, "portable-test-identity"), wrappingAlias))
                    .sign(isolated, unsigned, signed);
            verify(controller, signed, 1, false);
            if (keys.getCertificate(oldAlias) == null) {
                throw new AssertionError("Old product signer missing");
            }
            Files.write(state.toPath(), nonce.getBytes(StandardCharsets.US_ASCII));
            ready = true;
            return "Installed migration v1: worker-built APK signed by ProjectSigner; phone paths "
                    + published.getAbsolutePath()
                    + "\n";
        } finally {
            if (!ready) {
                for (String alias : new String[] {oldAlias, wrappingAlias}) {
                    if (keys.containsAlias(alias)) keys.deleteEntry(alias);
                }
            }
        }
    }

    /** Phase two proves that activation accepts exactly the installed v1 certificate. */
    static String prepareInstalledProductV2(Context controller) throws Exception {
        return prepareInstalledProductV2(controller, false);
    }

    /** Opt-in variant retains only the disposable fixture identity for a post-install export. */
    static String prepareInstalledProductV2ForRecovery(Context controller) throws Exception {
        return prepareInstalledProductV2(controller, true);
    }

    private static String prepareInstalledProductV2(Context controller, boolean retainForRecovery)
            throws Exception {
        File state = new File(controller.getNoBackupFilesDir(), INSTALLED_STATE);
        String nonce = new String(Files.readAllBytes(state.toPath()), StandardCharsets.US_ASCII);
        if (!nonce.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new AssertionError("Invalid installed migration state");
        }
        PackageInfo installed =
                controller
                        .getPackageManager()
                        .getPackageInfo(
                                ProjectSourceStore.PACKAGE_NAME,
                                PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] current =
                installed.signingInfo == null
                        ? null
                        : installed.signingInfo.getApkContentsSigners();
        if (installed.getLongVersionCode() != 1 || current == null || current.length != 1) {
            throw new AssertionError("Expected installed v1 with one signer");
        }
        File root = new File(controller.getNoBackupFilesDir(), "rotation-installed-" + nonce);
        File cache = new File(root, "cache");
        if (!cache.isDirectory()) throw new AssertionError("Probe source unavailable");
        Context isolated = installedContext(controller, root, cache);
        File external = controller.getExternalFilesDir(null);
        if (external == null) throw new AssertionError("Emulator external files unavailable");
        File published = new File(external, root.getName());
        if (!published.isDirectory()) throw new AssertionError("Probe APK directory unavailable");
        String oldAlias = installedOldAlias(nonce);
        String wrappingAlias = installedWrappingAlias(nonce);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        X509Certificate oldCertificate = (X509Certificate) keys.getCertificate(oldAlias);
        if (oldCertificate == null
                || !Arrays.equals(oldCertificate.getEncoded(), current[0].toByteArray())) {
            throw new AssertionError("Installed v1 signer differs from disposable project alias");
        }
        File identityDirectory = new File(root, "portable-test-identity");
        PortableSignerStore portableStore =
                new PortableSignerStore(identityDirectory, wrappingAlias);
        ProjectSigner signer = new ProjectSigner(oldAlias, portableStore);
        signer.activatePortable(isolated);
        PortableSignerStore.StoredIdentity saved = portableStore.load();
        if (saved == null
                || !SigningCertificateLineage.readFromBytes(saved.publicLineage())
                        .isCertificateInLineage(oldCertificate)) {
            throw new AssertionError("Installed signer missing from persisted lineage");
        }
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        String original = source.readSource();
        int revision = source.writeSource(0, original.replace("Counter: ", "Rotated: "));
        File unsigned = buildUnsignedBound(controller, root, source, revision, 2);
        File signed = new File(published, "app-v2.apk");
        new ProjectSigner(oldAlias, new PortableSignerStore(identityDirectory, wrappingAlias))
                .sign(isolated, unsigned, signed);
        verify(controller, signed, 2, true);
        keys.deleteEntry(oldAlias);
        if (retainForRecovery) {
            Files.write(
                    state.toPath(),
                    (nonce + "\nrecovery-ready").getBytes(StandardCharsets.US_ASCII));
        } else {
            keys.deleteEntry(wrappingAlias);
            new android.util.AtomicFile(new File(identityDirectory, "identity.bin")).delete();
            Files.delete(state.toPath());
        }
        return "Installed migration v2: activation accepted the installed v1 signer and "
                + "worker-built v2 used persisted portable identity; phone paths "
                + published.getAbsolutePath()
                + "\n";
    }

    /** Exports the disposable v2 snapshot while installed v1 can still prove lineage migration. */
    static String exportInstalledRecovery(Context controller) throws Exception {
        File state = new File(controller.getNoBackupFilesDir(), INSTALLED_STATE);
        String marker = new String(Files.readAllBytes(state.toPath()), StandardCharsets.US_ASCII);
        if (!marker.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\nrecovery-ready")) {
            throw new AssertionError("Disposable installed recovery fixture unavailable");
        }
        String nonce = marker.substring(0, 36);
        File root = new File(controller.getNoBackupFilesDir(), "rotation-installed-" + nonce);
        File cache = new File(root, "cache");
        File identityDirectory = new File(root, "portable-test-identity");
        if (!cache.isDirectory() || !new File(identityDirectory, "identity.bin").isFile()) {
            throw new AssertionError("Disposable installed recovery state unavailable");
        }
        Context isolated = installedContext(controller, root, cache);
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        int revision = source.revision();
        if (revision != 1 || !source.readSource().contains("Rotated: ")) {
            throw new AssertionError("Disposable installed source is not the v2 snapshot");
        }
        PackageInfo installed =
                controller
                        .getPackageManager()
                        .getPackageInfo(
                                ProjectSourceStore.PACKAGE_NAME,
                                PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] current =
                installed.signingInfo == null
                        ? null
                        : installed.signingInfo.getApkContentsSigners();
        if (installed.getLongVersionCode() != 1 || current == null || current.length != 1) {
            throw new AssertionError("Installed generated app is not the original v1 fixture");
        }
        PortableSignerStore store =
                new PortableSignerStore(identityDirectory, installedWrappingAlias(nonce));
        PortableSignerStore.StoredIdentity saved = store.load();
        if (saved == null) {
            throw new AssertionError("Disposable portable recovery identity unavailable");
        }
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        X509Certificate old =
                (X509Certificate)
                        certificates.generateCertificate(
                                new java.io.ByteArrayInputStream(current[0].toByteArray()));
        SigningCertificateLineage lineage =
                SigningCertificateLineage.readFromBytes(saved.publicLineage());
        if (lineage.size() != 2
                || !lineage.isCertificateInLineage(old)
                || !lineage.getSignerCapabilities(old).hasInstalledData()
                || !lineage.isCertificateLatestInLineage(saved.signer().certificate())) {
            throw new AssertionError("Installed signer history differs from disposable lineage");
        }
        File external = controller.getExternalFilesDir(null);
        if (external == null) throw new AssertionError("Disposable APK directory unavailable");
        File v2 = new File(new File(external, root.getName()), "app-v2.apk");
        verify(controller, v2, 2, true);
        ApkVerifier.Result verifiedV2 = new ApkVerifier.Builder(v2).build().verify();
        if (!verifiedV2.isVerified()
                || verifiedV2.getSignerCertificates().size() != 1
                || !Arrays.equals(
                        verifiedV2.getSignerCertificates().get(0).getEncoded(),
                        saved.signer().certificate().getEncoded())) {
            throw new AssertionError("Disposable v2 APK signer differs from recovery identity");
        }
        char[] phrase = "disposable installed recovery phrase".toCharArray();
        byte[] sealed = null;
        Uri document = null;
        String title =
                "smallwright-installed-recovery-" + nonce + "-" + UUID.randomUUID() + ".plbr";
        try {
            sealed =
                    RecoveryExport.prepare(
                            isolated,
                            source,
                            new ProjectSigner(installedOldAlias(nonce), store),
                            store,
                            revision,
                            1,
                            phrase);
            RecoveryBundle opened = RecoveryBundle.open(sealed, phrase);
            Map<String, byte[]> exact = source.readRecoverySources(revision);
            Map<String, byte[]> recovered = opened.sources();
            try {
                if (opened.revision() != revision
                        || opened.versionCode() != 1
                        || !Arrays.equals(
                                opened.signer().certificate().getEncoded(),
                                saved.signer().certificate().getEncoded())
                        || !Arrays.equals(opened.publicLineage(), saved.publicLineage())
                        || !exact.keySet().equals(recovered.keySet())) {
                    throw new AssertionError("Sealed installed recovery metadata differs");
                }
                for (String path : exact.keySet()) {
                    if (!Arrays.equals(exact.get(path), recovered.get(path))) {
                        throw new AssertionError("Sealed installed recovery source differs");
                    }
                }
            } finally {
                for (byte[] value : exact.values()) Arrays.fill(value, (byte) 0);
                for (byte[] value : recovered.values()) Arrays.fill(value, (byte) 0);
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, title);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            document =
                    controller
                            .getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (document == null) throw new IllegalStateException("Downloads document unavailable");
            try (OutputStream output =
                    controller.getContentResolver().openOutputStream(document, "w")) {
                if (output == null) throw new IllegalStateException("Downloads stream unavailable");
                output.write(sealed);
            }
            ContentValues visible = new ContentValues();
            visible.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (controller.getContentResolver().update(document, visible, null, null) != 1) {
                throw new IllegalStateException("Recovery document was not published");
            }
            document = null;
            return "Installed recovery fixture: Downloads/"
                    + title
                    + "; package "
                    + ProjectSourceStore.PACKAGE_NAME
                    + "; revision "
                    + revision
                    + "; recovery last installed version 1; v2 source; installed v1 signer "
                    + certificateSha256(current[0])
                    + "\n";
        } finally {
            if (document != null) controller.getContentResolver().delete(document, null, null);
            Arrays.fill(phrase, '\0');
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
        }
    }

    private static String installedOldAlias(String nonce) {
        return "rotation-apk-old-installed-" + nonce;
    }

    private static String installedWrappingAlias(String nonce) {
        return "dev.smallwright.controller.test.portable.installed." + nonce;
    }

    private static Context installedContext(Context controller, File root, File cache) {
        return new ContextWrapper(controller) {
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
    }

    private static File installedPublishedDirectory(Context controller, File root) {
        File external = controller.getExternalFilesDir(null);
        if (external == null) throw new AssertionError("Emulator external files unavailable");
        File published = new File(external, root.getName());
        if (!published.mkdir()) throw new AssertionError("Probe APK directory unavailable");
        return published;
    }

    private static File buildUnsignedBound(
            Context controller, File root, ProjectSourceStore source, int revision, int version)
            throws Exception {
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
            throw new AssertionError("Worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null) {
                throw new AssertionError("Worker unavailable");
            }
            return buildUnsigned(root, source, worker.get(), revision, version);
        } finally {
            controller.unbindService(connection);
        }
    }

    static String installed(Context controller, int expectedVersion) throws Exception {
        if (expectedVersion < 1 || expectedVersion > 3) {
            throw new IllegalArgumentException("Expected rotation version 1, 2 or 3");
        }
        PackageInfo info =
                controller
                        .getPackageManager()
                        .getPackageInfo(
                                ProjectSourceStore.PACKAGE_NAME,
                                PackageManager.GET_SIGNING_CERTIFICATES);
        Signature[] history =
                info.signingInfo == null ? null : info.signingInfo.getSigningCertificateHistory();
        Signature[] current =
                info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners();
        if (info.getLongVersionCode() != expectedVersion
                || history == null
                || current == null
                || current.length != 1) {
            throw new AssertionError("Installed APK has wrong version or signer information");
        }
        if (expectedVersion == 1) {
            if (history.length != 1 || !history[0].equals(current[0])) {
                throw new AssertionError("Initial APK has unexpected signer history");
            }
            String original = certificateSha256(current[0]);
            return "Installed rotation: version 1 old signer "
                    + original
                    + " current signer "
                    + original
                    + "\n";
        }
        if (history.length != 2
                || history[0].equals(history[1])
                || !history[1].equals(current[0])) {
            throw new AssertionError("Installed rotated APK has wrong version or signer history");
        }
        return "Installed rotation: version "
                + expectedVersion
                + " has old-to-new signer history; old signer "
                + certificateSha256(history[0])
                + " current signer "
                + certificateSha256(current[0])
                + "\n";
    }

    private static String certificateSha256(Signature certificate) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray());
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(digits[(value >>> 4) & 15]).append(digits[value & 15]);
        }
        return result.toString();
    }

    static String prepare(Context controller) throws Exception {
        return prepare(controller, false);
    }

    static String preparePortable(Context controller) throws Exception {
        return prepare(controller, true);
    }

    /** Builds through the real ProjectSigner path with disposable old/wrapping aliases. */
    static String prepareProduct(Context controller) throws Exception {
        try {
            controller.getPackageManager().getPackageInfo(ProjectSourceStore.PACKAGE_NAME, 0);
            throw new AssertionError("Generated package already installed; refusing rotation test");
        } catch (PackageManager.NameNotFoundException expected) {
            // The runner also requires a fresh dedicated emulator.
        }
        File root =
                Files.createTempDirectory(
                                controller.getNoBackupFilesDir().toPath(), "rotation-apk-probe-")
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

                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        String original = source.readSource();
        if (!original.contains("Counter: ") || !original.contains("Increment")) {
            throw new AssertionError("Counter template changed");
        }
        File external = controller.getExternalFilesDir(null);
        if (external == null) throw new AssertionError("Emulator external files unavailable");
        File published = new File(external, root.getName());
        if (!published.mkdir()) throw new AssertionError("Probe APK directory unavailable");

        String nonce = UUID.randomUUID().toString();
        String oldAlias = "rotation-apk-old-" + nonce;
        String wrappingAlias = "dev.smallwright.controller.test.portable." + nonce;
        File identityDirectory = new File(root, "portable-test-identity");
        PortableSignerStore portableStore =
                new PortableSignerStore(identityDirectory, wrappingAlias);
        ProjectSigner signer = new ProjectSigner(oldAlias, portableStore);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
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
            throw new AssertionError("Worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null) {
                throw new AssertionError("Worker unavailable");
            }
            File unsigned1 = buildUnsigned(root, source, worker.get(), 0, 1);
            File signed1 = new File(published, "app-v1.apk");
            signer.sign(isolated, unsigned1, signed1);
            verify(controller, signed1, 1, false);
            X509Certificate oldCertificate = (X509Certificate) keys.getCertificate(oldAlias);
            if (oldCertificate == null)
                throw new AssertionError("Old product probe signer missing");

            signer.activatePortable(isolated);
            PortableSignerStore.StoredIdentity saved = portableStore.load();
            if (saved == null) throw new AssertionError("Portable product signer not persisted");
            SigningCertificateLineage lineage =
                    SigningCertificateLineage.readFromBytes(saved.publicLineage());
            if (lineage.size() != 2
                    || !lineage.isCertificateInLineage(oldCertificate)
                    || !lineage.isCertificateLatestInLineage(saved.signer().certificate())) {
                throw new AssertionError("Product signer lineage differs from the old APK signer");
            }

            int revision2 = source.writeSource(0, original.replace("Counter: ", "Rotated: "));
            File unsigned2 = buildUnsigned(root, source, worker.get(), revision2, 2);
            File signed2 = new File(published, "app-v2.apk");
            signer.sign(isolated, unsigned2, signed2);
            verify(controller, signed2, 2, true);

            keys.deleteEntry(oldAlias);
            if (keys.containsAlias(oldAlias))
                throw new AssertionError("Old product signer still exists");
            int revision3 =
                    source.writeSource(revision2, original.replace("Counter: ", "Recovered: "));
            File unsigned3 = buildUnsigned(root, source, worker.get(), revision3, 3);
            File signed3 = new File(published, "app-v3.apk");
            new ProjectSigner(oldAlias, new PortableSignerStore(identityDirectory, wrappingAlias))
                    .sign(isolated, unsigned3, signed3);
            verify(controller, signed3, 3, true);
            return "Product migration APKs: three real worker builds signed by ProjectSigner; "
                    + "v3 signed from encrypted portable store after old alias removal; phone paths "
                    + published.getAbsolutePath()
                    + "\n";
        } finally {
            Exception cleanupFailure = null;
            try {
                controller.unbindService(connection);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            for (String alias : new String[] {oldAlias, wrappingAlias}) {
                try {
                    if (keys.containsAlias(alias)) keys.deleteEntry(alias);
                } catch (Exception failure) {
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                }
            }
            new android.util.AtomicFile(new File(identityDirectory, "identity.bin")).delete();
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }

    /** An installed app may not be migrated from an absent or unrelated old signer. */
    static String rejectUnsafeProductActivation(Context controller) throws Exception {
        controller
                .getPackageManager()
                .getPackageInfo(
                        ProjectSourceStore.PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES);
        String nonce = UUID.randomUUID().toString();
        String oldAlias = "rotation-apk-old-" + nonce;
        String wrappingAlias = "dev.smallwright.controller.test.portable.preflight." + nonce;
        File directory = new File(controller.getNoBackupFilesDir(), "rotation-preflight-" + nonce);
        PortableSignerStore portableStore = new PortableSignerStore(directory, wrappingAlias);
        ProjectSigner signer = new ProjectSigner(oldAlias, portableStore);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        try {
            expectActivationRejected(signer, controller);
            if (keys.containsAlias(oldAlias)) {
                throw new AssertionError("Missing signer was silently replaced");
            }
            SigningLineageProbe.createKey(oldAlias, BigInteger.valueOf(777));
            expectActivationRejected(signer, controller);
            if (keys.containsAlias(wrappingAlias) || new File(directory, "identity.bin").exists()) {
                throw new AssertionError("Rejected migration persisted a portable signer");
            }
            return "Product migration preflight: absent and wrong old signers rejected\n";
        } finally {
            for (String alias : new String[] {oldAlias, wrappingAlias}) {
                if (keys.containsAlias(alias)) keys.deleteEntry(alias);
            }
            new android.util.AtomicFile(new File(directory, "identity.bin")).delete();
        }
    }

    /** A build must reject an installed-app signer conflict before opening unsigned input. */
    static String rejectUnsafeProductSigning(Context controller) throws Exception {
        controller
                .getPackageManager()
                .getPackageInfo(
                        ProjectSourceStore.PACKAGE_NAME, PackageManager.GET_SIGNING_CERTIFICATES);
        String nonce = UUID.randomUUID().toString();
        File root = new File(controller.getNoBackupFilesDir(), "rotation-sign-preflight-" + nonce);
        String missingOld = "rotation-apk-old-missing-" + nonce;
        String portableOld = "rotation-apk-old-portable-" + nonce;
        String emptyWrap = "dev.smallwright.controller.test.portable.empty." + nonce;
        String portableWrap = "dev.smallwright.controller.test.portable.other." + nonce;
        File emptyDir = new File(root, "empty");
        File portableDir = new File(root, "portable");
        PortableSignerStore empty = new PortableSignerStore(emptyDir, emptyWrap);
        PortableSignerStore unrelated = new PortableSignerStore(portableDir, portableWrap);
        File unsigned = new File(root, "missing-unsigned.apk");
        File signed = new File(root, "rejected.apk");
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        try {
            expectSigningRejected(
                    new ProjectSigner(missingOld, empty), controller, unsigned, signed);
            if (keys.containsAlias(missingOld)) {
                throw new AssertionError("Missing old signer was silently replaced during signing");
            }
            unrelated.saveNew(PortableSigningIdentity.create(), null);
            expectSigningRejected(
                    new ProjectSigner(portableOld, unrelated), controller, unsigned, signed);
            if (signed.exists()) throw new AssertionError("Conflicting signer produced an APK");
            return "Product signing preflight: absent and unrelated installed signers rejected\n";
        } finally {
            for (String alias : new String[] {missingOld, portableOld, emptyWrap, portableWrap}) {
                if (keys.containsAlias(alias)) keys.deleteEntry(alias);
            }
            new android.util.AtomicFile(new File(emptyDir, "identity.bin")).delete();
            new android.util.AtomicFile(new File(portableDir, "identity.bin")).delete();
        }
    }

    private static void expectSigningRejected(
            ProjectSigner signer, Context controller, File unsigned, File signed) throws Exception {
        try {
            signer.sign(controller, unsigned, signed);
        } catch (SecurityException expected) {
            return;
        }
        throw new AssertionError("Unsafe installed-app signing was accepted");
    }

    private static void expectActivationRejected(ProjectSigner signer, Context controller)
            throws Exception {
        try {
            signer.activatePortable(controller);
        } catch (SecurityException expected) {
            return;
        }
        throw new AssertionError("Unsafe installed-app signer migration was accepted");
    }

    private static String prepare(Context controller, boolean portable) throws Exception {
        try {
            controller.getPackageManager().getPackageInfo(ProjectSourceStore.PACKAGE_NAME, 0);
            throw new AssertionError("Generated package already installed; refusing rotation test");
        } catch (PackageManager.NameNotFoundException expected) {
            // The runner must check this too, before any test APK is installed.
        }
        File root =
                Files.createTempDirectory(
                                controller.getNoBackupFilesDir().toPath(), "rotation-apk-probe-")
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

                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        String original = source.readSource();
        if (!original.contains("Counter: ") || !original.contains("Increment")) {
            throw new AssertionError("Counter template changed");
        }
        File external = controller.getExternalFilesDir(null);
        if (external == null) throw new AssertionError("Emulator external files unavailable");
        File published = new File(external, root.getName());
        if (!published.mkdir()) throw new AssertionError("Probe APK directory unavailable");

        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String oldAlias = "rotation-apk-old-" + UUID.randomUUID();
        String newAlias = "rotation-apk-new-" + UUID.randomUUID();
        char[] recoveryPassphrase =
                portable ? "disposable portable recovery phrase".toCharArray() : null;
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
            throw new AssertionError("Worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null) {
                throw new AssertionError("Worker unavailable");
            }
            SigningLineageProbe.createKey(oldAlias, BigInteger.valueOf(201));
            PortableSigningIdentity software = null;
            if (portable) software = PortableSigningIdentity.create();
            else SigningLineageProbe.createKey(newAlias, BigInteger.valueOf(202));
            ApkSigner.SignerConfig oldApkSigner = apkSigner(store, oldAlias);
            ApkSigner.SignerConfig newApkSigner =
                    portable ? apkSigner(software) : apkSigner(store, newAlias);
            SigningCertificateLineage.SignerConfig oldLineageSigner =
                    lineageSigner(store, oldAlias);
            SigningCertificateLineage.SignerConfig newLineageSigner =
                    portable ? lineageSigner(software) : lineageSigner(store, newAlias);
            SigningCertificateLineage lineage =
                    new SigningCertificateLineage.Builder(oldLineageSigner, newLineageSigner)
                            .setMinSdkVersion(28)
                            .setOriginalCapabilities(
                                    new SigningCertificateLineage.SignerCapabilities.Builder()
                                            .setInstalledData(true)
                                            .setRollback(false)
                                            .build())
                            .build();

            File unsigned1 = buildUnsigned(root, source, worker.get(), 0, 1);
            File signed1 = new File(published, "app-v1.apk");
            sign(unsigned1, signed1, oldApkSigner, null);
            verify(controller, signed1, 1, false);

            int revision2 = source.writeSource(0, original.replace("Counter: ", "Rotated: "));
            File unsigned2 = buildUnsigned(root, source, worker.get(), revision2, 2);
            File signed2 = new File(published, "app-v2.apk");
            sign(unsigned2, signed2, newApkSigner, lineage);
            verify(controller, signed2, 2, true);

            // Export the new test key before the old signer is removed.
            byte[] savedLineage =
                    lineage.getBytes(); // Public certificate proof; no private-key bytes.
            byte[] sealedKey = null;
            byte[] portableCertificate = null;
            if (portable) {
                byte[] pkcs8 = software.exportPkcs8();
                try {
                    sealedKey = RecoveryEnvelope.seal(pkcs8, recoveryPassphrase);
                    portableCertificate = software.certificate().getEncoded();
                } finally {
                    Arrays.fill(pkcs8, (byte) 0);
                }
            }
            store.deleteEntry(oldAlias);
            if (store.containsAlias(oldAlias))
                throw new AssertionError("Old probe signer still exists");
            SigningCertificateLineage restored =
                    SigningCertificateLineage.readFromBytes(savedLineage);
            ApkSigner.SignerConfig afterRestoreSigner = newApkSigner;
            if (portable) {
                byte[] opened = RecoveryEnvelope.open(sealedKey, recoveryPassphrase);
                try {
                    afterRestoreSigner =
                            apkSigner(PortableSigningIdentity.restore(opened, portableCertificate));
                } finally {
                    Arrays.fill(opened, (byte) 0);
                }
            }
            int revision3 =
                    source.writeSource(revision2, original.replace("Counter: ", "Recovered: "));
            File unsigned3 = buildUnsigned(root, source, worker.get(), revision3, 3);
            File signed3 = new File(published, "app-v3.apk");
            sign(unsigned3, signed3, afterRestoreSigner, restored);
            verify(controller, signed3, 3, true);
            if (portable) {
                return "Portable rotation APKs: three real worker builds; v3 signed "
                        + "with decrypted/restored test key after old alias removal; phone paths "
                        + published.getAbsolutePath()
                        + "\n";
            }
            return "Rotation APKs: three real worker builds signed with disposable old/new "
                    + "identities; v3 signed after old alias removal; phone paths "
                    + published.getAbsolutePath()
                    + "\n";
        } finally {
            Exception cleanupFailure = null;
            if (recoveryPassphrase != null) Arrays.fill(recoveryPassphrase, '\0');
            try {
                controller.unbindService(connection);
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            for (String alias : new String[] {oldAlias, newAlias}) {
                try {
                    if (store.containsAlias(alias)) store.deleteEntry(alias);
                } catch (Exception failure) {
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                }
            }
            if (cleanupFailure != null) throw cleanupFailure;
        }
    }

    private static File buildUnsigned(
            File root, ProjectSourceStore source, IBuildService worker, int revision, int version)
            throws Exception {
        File snapshot = new File(root, "snapshot-v" + version + ".zip");
        File unsigned = new File(root, "unsigned-v" + version + ".apk");
        try (ProjectStore.BuildLease lease = source.beginBuild(revision, snapshot, version);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                unsigned,
                                ParcelFileDescriptor.MODE_READ_WRITE
                                        | ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_TRUNCATE)) {
            LocalBuildCoordinator.requireWorkerSuccess(worker.build(input, output));
        }
        return unsigned;
    }

    private static ApkSigner.SignerConfig apkSigner(KeyStore store, String alias) throws Exception {
        return new ApkSigner.SignerConfig.Builder(
                        "rotation-probe",
                        (PrivateKey) store.getKey(alias, null),
                        Collections.singletonList((X509Certificate) store.getCertificate(alias)))
                .build();
    }

    private static ApkSigner.SignerConfig apkSigner(PortableSigningIdentity identity) {
        return new ApkSigner.SignerConfig.Builder(
                        "portable-rotation-probe",
                        identity.privateKey(),
                        Collections.singletonList(identity.certificate()))
                .build();
    }

    private static SigningCertificateLineage.SignerConfig lineageSigner(
            KeyStore store, String alias) throws Exception {
        return new SigningCertificateLineage.SignerConfig.Builder(
                        (PrivateKey) store.getKey(alias, null),
                        (X509Certificate) store.getCertificate(alias))
                .build();
    }

    private static SigningCertificateLineage.SignerConfig lineageSigner(
            PortableSigningIdentity identity) {
        return new SigningCertificateLineage.SignerConfig.Builder(
                        identity.privateKey(), identity.certificate())
                .build();
    }

    private static void sign(
            File unsigned,
            File signed,
            ApkSigner.SignerConfig signer,
            SigningCertificateLineage lineage)
            throws Exception {
        ApkSigner.Builder builder =
                new ApkSigner.Builder(Collections.singletonList(signer))
                        .setInputApk(unsigned)
                        .setOutputApk(signed)
                        .setMinSdkVersion(31)
                        .setV1SigningEnabled(false)
                        .setV2SigningEnabled(lineage == null)
                        .setV3SigningEnabled(true);
        if (lineage != null) {
            builder.setSigningCertificateLineage(lineage).setMinSdkVersionForRotation(28);
        }
        builder.build().sign();
    }

    private static void verify(Context controller, File apk, int version, boolean rotated)
            throws Exception {
        for (int api : new int[] {31, 36}) {
            ApkVerifier.Result result =
                    new ApkVerifier.Builder(apk)
                            .setMinCheckedPlatformVersion(api)
                            .setMaxCheckedPlatformVersion(api)
                            .build()
                            .verify();
            if (!result.isVerified()
                    || result.getSignerCertificates().size() != 1
                    || rotated && result.getSigningCertificateLineage() == null) {
                throw new AssertionError("Rotation APK did not verify for API " + api);
            }
        }
        android.content.pm.PackageInfo info =
                controller.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null
                || !ProjectSourceStore.PACKAGE_NAME.equals(info.packageName)
                || info.getLongVersionCode() != version) {
            throw new AssertionError("Rotation APK package/version mismatch");
        }
    }
}
