package dev.smallwright.controller;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.android.apksig.SigningCertificateLineage;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.UUID;
import javax.security.auth.x500.X500Principal;

/** Checks apksig lineage creation using only disposable Android Keystore aliases. */
final class SigningLineageProbe {
    static String run() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String oldAlias = "lineage-probe-old-" + UUID.randomUUID();
        String newAlias = "lineage-probe-new-" + UUID.randomUUID();
        try {
            createKey(oldAlias, BigInteger.valueOf(101));
            createKey(newAlias, BigInteger.valueOf(102));
            SigningCertificateLineage.SignerConfig oldSigner =
                    new SigningCertificateLineage.SignerConfig.Builder(
                                    (PrivateKey) store.getKey(oldAlias, null),
                                    (X509Certificate) store.getCertificate(oldAlias))
                            .build();
            SigningCertificateLineage.SignerConfig newSigner =
                    new SigningCertificateLineage.SignerConfig.Builder(
                                    (PrivateKey) store.getKey(newAlias, null),
                                    (X509Certificate) store.getCertificate(newAlias))
                            .build();
            SigningCertificateLineage.SignerCapabilities oldCapabilities =
                    new SigningCertificateLineage.SignerCapabilities.Builder()
                            .setInstalledData(true)
                            .setRollback(false)
                            .build();
            SigningCertificateLineage lineage =
                    new SigningCertificateLineage.Builder(oldSigner, newSigner)
                            .setMinSdkVersion(28)
                            .setOriginalCapabilities(oldCapabilities)
                            .build();
            SigningCertificateLineage restored =
                    SigningCertificateLineage.readFromBytes(lineage.getBytes());
            if (restored.size() != 2
                    || !restored.isSignerInLineage(oldSigner)
                    || !restored.isSignerInLineage(newSigner)
                    || !restored.getSignerCapabilities(oldSigner).hasInstalledData()
                    || restored.getSignerCapabilities(oldSigner).hasRollback()) {
                throw new AssertionError(
                        "Disposable signing lineage has wrong members or capabilities");
            }
            return "Signing lineage: disposable Android Keystore signer created "
                    + "a restorable old-to-new proof with installed-data capability; "
                    + "no project key used\n";
        } finally {
            Exception cleanupFailure = null;
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

    static void createKey(String alias, BigInteger serial) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        long now = System.currentTimeMillis();
        generator.initialize(
                new KeyGenParameterSpec.Builder(
                                alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(2048)
                        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setCertificateSubject(new X500Principal("CN=Disposable Lineage Probe"))
                        .setCertificateSerialNumber(serial)
                        .setCertificateNotBefore(new Date(now - 60_000))
                        .setCertificateNotAfter(new Date(now + 31_536_000_000L))
                        .build());
        generator.generateKeyPair();
    }
}
