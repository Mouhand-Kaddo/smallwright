package dev.smallwright.controller;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.android.apksig.SigningCertificateLineage;
import java.io.File;
import java.io.FileOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.KeyGenerator;

/** Exercises disposable storage and Keystore state on a real Android runtime. */
final class PortableSignerStoreProbe {
    private PortableSignerStoreProbe() {}

    static String run(Context context) throws Exception {
        String nonce = UUID.randomUUID().toString();
        String alias = "dev.smallwright.controller.test.portable." + nonce;
        File directory = new File(context.getNoBackupFilesDir(), "portable-store-test-" + nonce);
        PortableSignerStore store = new PortableSignerStore(directory, alias);
        String lineageAlias = alias + ".lineage";
        File lineageDirectory =
                new File(context.getNoBackupFilesDir(), "portable-lineage-test-" + nonce);
        PortableSignerStore lineageStore = new PortableSignerStore(lineageDirectory, lineageAlias);
        String orphanAlias = alias + ".orphan";
        File orphanDirectory =
                new File(context.getNoBackupFilesDir(), "portable-orphan-test-" + nonce);
        PortableSignerStore orphanStore = new PortableSignerStore(orphanDirectory, orphanAlias);
        byte[] lineage = new byte[0];
        try {
            if (store.load() != null) throw new AssertionError("Fresh store is not empty");
            PortableSigningIdentity created = PortableSigningIdentity.create();
            expectFailure(() -> store.saveNew(created, new byte[] {1, 2, 3, 4}));
            store.saveNew(created, lineage);
            PortableSignerStore.StoredIdentity restored = store.load();
            if (restored == null
                    || !Arrays.equals(lineage, restored.publicLineage())
                    || !Arrays.equals(
                            created.certificate().getEncoded(),
                            restored.signer().certificate().getEncoded())) {
                throw new AssertionError("Portable identity did not round trip");
            }
            expectFailure(() -> store.saveNew(PortableSigningIdentity.create(), null));
            expectFailure(
                    () -> store.saveForPendingRecovery(PortableSigningIdentity.create(), null));
            File file = new File(directory, "identity.bin");
            byte[] encrypted = java.nio.file.Files.readAllBytes(file.toPath());
            byte[] originalEnvelope = encrypted.clone();
            encrypted[encrypted.length - 1] ^= 1;
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(encrypted);
            }
            expectFailure(store::load);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(originalEnvelope);
            }
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            keys.deleteEntry(alias);
            expectFailure(store::load);

            PortableSigningIdentity oldSigner = PortableSigningIdentity.create();
            PortableSigningIdentity newSigner = PortableSigningIdentity.create();
            SigningCertificateLineage.SignerConfig oldConfig = lineageSigner(oldSigner);
            SigningCertificateLineage.SignerConfig newConfig = lineageSigner(newSigner);
            byte[] validLineage =
                    new SigningCertificateLineage.Builder(oldConfig, newConfig)
                            .setMinSdkVersion(28)
                            .build()
                            .getBytes();
            expectFailure(() -> lineageStore.saveNew(oldSigner, validLineage));
            lineageStore.saveNew(newSigner, validLineage);
            PortableSignerStore.StoredIdentity withLineage = lineageStore.load();
            if (withLineage == null
                    || !Arrays.equals(validLineage, withLineage.publicLineage())
                    || !Arrays.equals(
                            newSigner.certificate().getEncoded(),
                            withLineage.signer().certificate().getEncoded())) {
                throw new AssertionError("Valid lineage did not round trip");
            }

            KeyGenerator generator =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(
                    new KeyGenParameterSpec.Builder(
                                    orphanAlias,
                                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build());
            generator.generateKey(); // Simulate interruption before identity.bin was written.
            try {
                orphanStore.load();
                throw new AssertionError("Orphaned key was accepted as an empty store");
            } catch (PortableSignerStore.OrphanedWrappingKeyException expected) {
                // Recovery adoption can identify this exact interruption window.
            }
            expectFailure(() -> orphanStore.saveNew(PortableSigningIdentity.create(), null));
            PortableSigningIdentity recovered = PortableSigningIdentity.create();
            orphanStore.saveForPendingRecovery(recovered, null);
            PortableSignerStore.StoredIdentity resumed = orphanStore.load();
            if (resumed == null
                    || !Arrays.equals(
                            recovered.certificate().getEncoded(),
                            resumed.signer().certificate().getEncoded()))
                throw new AssertionError("Orphaned wrapping key was not recovered");
            expectFailure(
                    () ->
                            orphanStore.saveForPendingRecovery(
                                    PortableSigningIdentity.create(), null));
            if (!Arrays.equals(
                    recovered.certificate().getEncoded(),
                    orphanStore.load().signer().certificate().getEncoded()))
                throw new AssertionError("Recovery replaced an existing identity");
            return "Portable signer store: round trip, tamper, missing key, lineage, orphan recovery passed\n";
        } finally {
            deleteDisposable(alias, directory);
            deleteDisposable(lineageAlias, lineageDirectory);
            deleteDisposable(orphanAlias, orphanDirectory);
        }
    }

    private static SigningCertificateLineage.SignerConfig lineageSigner(
            PortableSigningIdentity signer) {
        return new SigningCertificateLineage.SignerConfig.Builder(
                        signer.privateKey(), signer.certificate())
                .build();
    }

    private static void deleteDisposable(String alias, File directory) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        keys.deleteEntry(alias);
        new android.util.AtomicFile(new File(directory, "identity.bin")).delete();
        directory.delete();
    }

    private static void expectFailure(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (GeneralSecurityException | java.io.IOException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected store failure");
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
