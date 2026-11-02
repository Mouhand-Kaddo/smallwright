package dev.smallwright.controller;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import com.android.apksig.SigningCertificateLineage;
import dev.smallwright.project.ProjectIdentity;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import javax.security.auth.x500.X500Principal;

final class ProjectSigner {
    private static final String ALIAS = "generated-counter-alpha";
    private static final String PROJECT_ID = "counter-alpha";
    private final String oldAlias;
    private final String projectId;
    private final String packageName;
    private final PortableSignerStore injectedPortableStore;

    ProjectSigner() {
        oldAlias = ALIAS;
        projectId = PROJECT_ID;
        packageName = ProjectSourceStore.PACKAGE_NAME;
        injectedPortableStore = null;
    }

    ProjectSigner(ProjectIdentity identity) {
        if (identity == null
                || !identity.packageName()
                        .matches("dev\\.smallwright\\.generated\\.(?:counter|app[0-9a-f]{32})")) {
            throw new IllegalArgumentException("Invalid project signer identity");
        }
        projectId = identity.id();
        packageName = identity.packageName();
        oldAlias = keystoreAlias(projectId);
        injectedPortableStore = null;
    }

    /** Test-only injection uses a disposable alias and private test store. */
    ProjectSigner(String disposableOldAlias, PortableSignerStore disposableStore) {
        if (disposableOldAlias == null
                || !disposableOldAlias.startsWith("rotation-apk-old-")
                || disposableStore == null) {
            throw new IllegalArgumentException("Invalid disposable signer setup");
        }
        oldAlias = disposableOldAlias;
        projectId = PROJECT_ID;
        packageName = ProjectSourceStore.PACKAGE_NAME;
        injectedPortableStore = disposableStore;
    }

    /** Explicit transition to a recoverable identity; no existing Keystore key is exported. */
    void activatePortable(Context context) throws Exception {
        PortableSignerStore portableStore = portableStore(context);
        if (portableStore.load() != null) {
            throw new IllegalStateException("Portable project signer is already active");
        }
        KeyStore oldStore = keyStore();
        X509Certificate oldCertificate = (X509Certificate) oldStore.getCertificate(oldAlias);
        if (oldCertificate == null) {
            requireInstalledSigner(context, null);
            createOldKey(oldAlias, projectId);
            oldCertificate = (X509Certificate) oldStore.getCertificate(oldAlias);
        } else {
            requireInstalledSigner(context, oldCertificate);
        }
        PrivateKey oldKey = (PrivateKey) oldStore.getKey(oldAlias, null);
        if (oldKey == null || oldCertificate == null) {
            throw new IllegalStateException("Original project signer unavailable");
        }
        PortableSigningIdentity portableIdentity = PortableSigningIdentity.create();
        SigningCertificateLineage.SignerConfig before =
                new SigningCertificateLineage.SignerConfig.Builder(oldKey, oldCertificate).build();
        SigningCertificateLineage.SignerConfig after =
                new SigningCertificateLineage.SignerConfig.Builder(
                                portableIdentity.privateKey(), portableIdentity.certificate())
                        .build();
        SigningCertificateLineage lineage =
                new SigningCertificateLineage.Builder(before, after)
                        .setMinSdkVersion(28)
                        .setOriginalCapabilities(
                                new SigningCertificateLineage.SignerCapabilities.Builder()
                                        .setInstalledData(true)
                                        .setRollback(false)
                                        .build())
                        .build();
        portableStore.saveNew(portableIdentity, lineage.getBytes());
    }

    VerifiedArtifact sign(Context context, File unsigned, File signed) throws Exception {
        PortableSignerStore.StoredIdentity portable = portableStore(context).load();
        PrivateKey privateKey;
        X509Certificate certificate;
        SigningCertificateLineage lineage = null;
        if (portable != null) {
            privateKey = portable.signer().privateKey();
            certificate = portable.signer().certificate();
            byte[] bytes = portable.publicLineage();
            if (bytes.length != 0) lineage = SigningCertificateLineage.readFromBytes(bytes);
            requireCompatibleInstalledSigner(context, certificate, lineage);
        } else {
            KeyStore oldStore = keyStore();
            X509Certificate oldCertificate = (X509Certificate) oldStore.getCertificate(oldAlias);
            requireCompatibleInstalledSigner(context, oldCertificate, null);
            if (oldCertificate == null) createOldKey(oldAlias, projectId);
            privateKey = (PrivateKey) oldStore.getKey(oldAlias, null);
            certificate = (X509Certificate) oldStore.getCertificate(oldAlias);
        }
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("Project signer unavailable");
        }
        ApkSigner.SignerConfig config =
                new ApkSigner.SignerConfig.Builder(
                                projectId, privateKey, Collections.singletonList(certificate))
                        .build();
        ApkSigner.Builder builder =
                new ApkSigner.Builder(Collections.singletonList(config))
                        .setInputApk(unsigned)
                        .setOutputApk(signed)
                        .setV1SigningEnabled(lineage == null)
                        .setV2SigningEnabled(lineage == null)
                        .setV3SigningEnabled(true);
        if (lineage != null) {
            builder.setMinSdkVersion(31)
                    .setSigningCertificateLineage(lineage)
                    .setMinSdkVersionForRotation(28);
        }
        builder.build().sign();
        VerifiedArtifact artifact = VerifiedArtifact.capture(signed);
        ApkVerifier.Result verification = new ApkVerifier.Builder(signed).build().verify();
        if (!verification.isVerified()
                || verification.getSignerCertificates().size() != 1
                || !Arrays.equals(
                        verification.getSignerCertificates().get(0).getEncoded(),
                        certificate.getEncoded())
                || lineage != null && verification.getSigningCertificateLineage() == null) {
            throw new SecurityException("Signed APK verification failed");
        }
        artifact.verifyUnchanged();
        return artifact;
    }

    /**
     * The encoded certificate the project signs with now, or null before it has a signer. Creates
     * no key.
     */
    byte[] currentCertificate(Context context) throws Exception {
        PortableSignerStore.StoredIdentity portable = portableStore(context).load();
        X509Certificate certificate =
                portable != null
                        ? portable.signer().certificate()
                        : (X509Certificate) keyStore().getCertificate(oldAlias);
        return certificate == null ? null : certificate.getEncoded();
    }

    /** Checks an existing APK against the project's current identity without creating a key. */
    String verifyExisting(Context context, File apk) throws Exception {
        PortableSignerStore.StoredIdentity portable = portableStore(context).load();
        X509Certificate certificate;
        SigningCertificateLineage lineage = null;
        if (portable != null) {
            certificate = portable.signer().certificate();
            byte[] bytes = portable.publicLineage();
            if (bytes.length != 0) lineage = SigningCertificateLineage.readFromBytes(bytes);
        } else {
            certificate = (X509Certificate) keyStore().getCertificate(oldAlias);
        }
        if (certificate == null) throw new SecurityException("Project signer unavailable");
        ApkVerifier.Result verification = new ApkVerifier.Builder(apk).build().verify();
        if (!verification.isVerified()
                || verification.getSignerCertificates().size() != 1
                || !Arrays.equals(
                        verification.getSignerCertificates().get(0).getEncoded(),
                        certificate.getEncoded()))
            throw new SecurityException("APK signer differs from project signer");
        SigningCertificateLineage actual = verification.getSigningCertificateLineage();
        if (lineage == null && actual != null
                || lineage != null
                        && (actual == null
                                || !Arrays.equals(lineage.getBytes(), actual.getBytes())))
            throw new SecurityException("APK signing lineage differs from project lineage");
        requireCompatibleInstalledSigner(context, certificate, lineage);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    /**
     * Deletes both signing keys of a generated project, so its installed app can no longer be
     * updated; repeating it is harmless.
     */
    static void deleteKeys(Context context, String projectId) throws Exception {
        if (projectId == null || !projectId.matches("app[0-9a-f]{32}"))
            throw new IllegalArgumentException("This signer cannot be deleted");
        new PortableSignerStore(context, projectId).delete();
        KeyStore store = keyStore();
        String alias = keystoreAlias(projectId);
        if (store.containsAlias(alias)) store.deleteEntry(alias);
    }

    private static String keystoreAlias(String projectId) {
        return "generated-" + projectId;
    }

    private PortableSignerStore portableStore(Context context) {
        return injectedPortableStore == null
                ? new PortableSignerStore(context, projectId)
                : injectedPortableStore;
    }

    private static KeyStore keyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    private void requireInstalledSigner(Context context, X509Certificate oldCertificate)
            throws Exception {
        Signature current = installedSigner(context);
        if (current == null) return;
        if (oldCertificate == null
                || !Arrays.equals(oldCertificate.getEncoded(), current.toByteArray())) {
            throw new SecurityException(
                    "Installed app signer differs from the original project signer");
        }
    }

    private void requireCompatibleInstalledSigner(
            Context context, X509Certificate certificate, SigningCertificateLineage lineage)
            throws Exception {
        Signature current = installedSigner(context);
        if (current == null) return;
        if (certificate != null && Arrays.equals(certificate.getEncoded(), current.toByteArray())) {
            return;
        }
        if (lineage != null) {
            for (X509Certificate earlier : lineage.getCertificatesInLineage()) {
                if (Arrays.equals(earlier.getEncoded(), current.toByteArray())
                        && lineage.getSignerCapabilities(earlier).hasInstalledData()) {
                    return;
                }
            }
        }
        throw new SecurityException(
                "Installed app signer is incompatible with this project signer");
    }

    private Signature installedSigner(Context context) throws Exception {
        PackageInfo installed;
        try {
            installed =
                    context.getPackageManager()
                            .getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        } catch (PackageManager.NameNotFoundException absent) {
            return null;
        }
        Signature[] current =
                installed.signingInfo == null
                        ? null
                        : installed.signingInfo.getApkContentsSigners();
        if (current == null || current.length != 1)
            throw new SecurityException("Installed app signer is unavailable");
        return current[0];
    }

    private static void createOldKey(String alias, String projectId) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(
                                alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(
                                new X500Principal(
                                        "counter-alpha".equals(projectId)
                                                ? "CN=Smallwright Generated Counter"
                                                : "CN=Smallwright Generated App"))
                        .setCertificateSerialNumber(BigInteger.ONE)
                        .setCertificateNotBefore(new java.util.Date())
                        .setCertificateNotAfter(
                                new java.util.Date(System.currentTimeMillis() + 315360000000L))
                        .build());
        generator.generateKeyPair();
    }
}
