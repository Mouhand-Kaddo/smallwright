package dev.smallwright.controller;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.android.apksig.SigningCertificateLineage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Durable controller-private storage for a newly generated, exportable project signer. */
final class PortableSignerStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS_PREFIX = "dev.smallwright.controller.portable_signer.v1.";
    private static final int MAGIC = 0x50534931; // PSI1
    private static final int MAX_DER = 16 * 1024;
    private static final int MAX_LINEAGE = 64 * 1024;
    private static final int MAX_PLAINTEXT = 4 + 3 * 4 + 2 * MAX_DER + MAX_LINEAGE;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_ENVELOPE = 4 + IV_LENGTH + MAX_PLAINTEXT + TAG_BITS / 8;
    private static final Object SAVE_LOCK = new Object();

    private final AtomicFile file;
    private final String alias;

    PortableSignerStore(Context context, String projectId) {
        String id = validProjectId(projectId);
        File directory =
                new File(
                        new File(
                                context.getApplicationContext().getNoBackupFilesDir(),
                                "portable-signers"),
                        id);
        this.file = new AtomicFile(new File(directory, "identity.bin"));
        this.alias = ALIAS_PREFIX + id;
    }

    /** Package-private injection is reserved for disposable Android instrumentation. */
    PortableSignerStore(File directory, String testAlias) {
        if (directory == null
                || testAlias == null
                || !testAlias.startsWith("dev.smallwright.controller.test.portable.")) {
            throw new IllegalArgumentException("Invalid test store location");
        }
        this.file = new AtomicFile(new File(directory, "identity.bin"));
        this.alias = testAlias;
    }

    private static String validProjectId(String projectId) {
        if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid project ID");
        }
        return projectId;
    }

    /**
     * Stores a caller-created identity once. Existing or orphaned Keystore state is never replaced.
     */
    void saveNew(PortableSigningIdentity identity, byte[] publicLineage) throws Exception {
        if (identity == null) throw new IllegalArgumentException("Missing portable signer");
        byte[] lineage = checkedLineage(publicLineage);
        validateLineage(identity, lineage);
        synchronized (SAVE_LOCK) {
            if (file.getBaseFile().exists() || findKey() != null) {
                throw new GeneralSecurityException("Portable signer already exists");
            }
            writeIdentity(identity, lineage, createKey());
        }
    }

    /** Completes a marker-bound recovery after a crash left only its wrapping key. */
    void saveForPendingRecovery(PortableSigningIdentity identity, byte[] publicLineage)
            throws Exception {
        if (identity == null) throw new IllegalArgumentException("Missing portable signer");
        byte[] lineage = checkedLineage(publicLineage);
        validateLineage(identity, lineage);
        synchronized (SAVE_LOCK) {
            // openRead restores a committed .bak file. Neither it nor a damaged base may be
            // replaced.
            try (FileInputStream ignored = file.openRead()) {
                throw new GeneralSecurityException("Portable signer already exists");
            } catch (FileNotFoundException absent) {
                File base = file.getBaseFile();
                if (base.exists() || new File(base.getPath() + ".bak").exists()) {
                    throw new GeneralSecurityException("Portable signer file unavailable", absent);
                }
            }
            SecretKey key = findKey();
            if (key == null) {
                throw new GeneralSecurityException("Orphaned portable signer key unavailable");
            }
            writeIdentity(identity, lineage, key);
        }
    }

    private void writeIdentity(PortableSigningIdentity identity, byte[] lineage, SecretKey key)
            throws Exception {
        byte[] pkcs8 = identity.exportPkcs8();
        byte[] plaintext = null;
        try {
            byte[] certificate = identity.certificate().getEncoded();
            plaintext = encode(pkcs8, certificate, lineage);
            byte[] encrypted = encrypt(key, plaintext);
            File directory = file.getBaseFile().getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Could not create portable signer directory");
            }
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                output.write(encrypted);
                file.finishWrite(output);
            } catch (IOException | RuntimeException failure) {
                if (output != null) file.failWrite(output);
                throw failure;
            }
        } finally {
            Arrays.fill(pkcs8, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Returns null only for a never-created store; corrupt or unwrappable state fails closed. */
    StoredIdentity load() throws IOException, GeneralSecurityException {
        byte[] envelope;
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_ENVELOPE) {
                    throw new GeneralSecurityException("Portable signer envelope too large");
                }
                output.write(buffer, 0, count);
            }
            envelope = output.toByteArray();
        } catch (FileNotFoundException absent) {
            if (findKey() != null) {
                throw new OrphanedWrappingKeyException(absent);
            }
            return null;
        }
        SecretKey key = findKey();
        if (key == null)
            throw new GeneralSecurityException("Portable signer wrapping key unavailable");
        byte[] plaintext = decrypt(key, envelope);
        try {
            return decode(plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static byte[] checkedLineage(byte[] value) {
        if (value != null && value.length > MAX_LINEAGE) {
            throw new IllegalArgumentException("Public lineage too large");
        }
        return value == null ? new byte[0] : value.clone();
    }

    private static byte[] encode(byte[] key, byte[] certificate, byte[] lineage)
            throws GeneralSecurityException {
        if (key.length == 0
                || key.length > MAX_DER
                || certificate.length == 0
                || certificate.length > MAX_DER) {
            throw new GeneralSecurityException("Invalid portable identity size");
        }
        byte[] plaintext = new byte[4 + 3 * 4 + key.length + certificate.length + lineage.length];
        java.nio.ByteBuffer.wrap(plaintext)
                .putInt(MAGIC)
                .putInt(key.length)
                .putInt(certificate.length)
                .putInt(lineage.length)
                .put(key)
                .put(certificate)
                .put(lineage);
        return plaintext;
    }

    private static StoredIdentity decode(byte[] plaintext) throws GeneralSecurityException {
        try {
            DataInputStream input =
                    new DataInputStream(new java.io.ByteArrayInputStream(plaintext));
            if (input.readInt() != MAGIC)
                throw new GeneralSecurityException("Invalid signer format");
            int keyLength = input.readInt();
            int certificateLength = input.readInt();
            int lineageLength = input.readInt();
            if (keyLength < 1
                    || keyLength > MAX_DER
                    || certificateLength < 1
                    || certificateLength > MAX_DER
                    || lineageLength < 0
                    || lineageLength > MAX_LINEAGE
                    || input.available() != keyLength + certificateLength + lineageLength) {
                throw new GeneralSecurityException("Invalid signer fields");
            }
            byte[] key = new byte[keyLength];
            byte[] certificate = new byte[certificateLength];
            byte[] lineage = new byte[lineageLength];
            try {
                input.readFully(key);
                input.readFully(certificate);
                input.readFully(lineage);
                PortableSigningIdentity identity =
                        PortableSigningIdentity.restore(key, certificate);
                validateLineage(identity, lineage);
                return new StoredIdentity(identity, lineage);
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        } catch (IOException malformed) {
            throw new GeneralSecurityException("Malformed signer data", malformed);
        }
    }

    private static void validateLineage(PortableSigningIdentity identity, byte[] lineage)
            throws GeneralSecurityException {
        if (lineage.length == 0) return;
        try {
            if (!SigningCertificateLineage.readFromBytes(lineage)
                    .isCertificateLatestInLineage(identity.certificate())) {
                throw new GeneralSecurityException("Lineage does not end with portable signer");
            }
        } catch (IOException | RuntimeException malformed) {
            throw new GeneralSecurityException("Invalid public signing lineage", malformed);
        }
    }

    private byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(alias.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        if (iv.length != IV_LENGTH) throw new GeneralSecurityException("Invalid GCM IV");
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] envelope = new byte[4 + IV_LENGTH + ciphertext.length];
        java.nio.ByteBuffer.wrap(envelope).putInt(MAGIC).put(iv).put(ciphertext);
        return envelope;
    }

    private byte[] decrypt(SecretKey key, byte[] envelope) throws GeneralSecurityException {
        if (envelope.length < 4 + IV_LENGTH + TAG_BITS / 8 || envelope.length > MAX_ENVELOPE) {
            throw new GeneralSecurityException("Invalid signer envelope size");
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(envelope);
        if (buffer.getInt() != MAGIC) throw new GeneralSecurityException("Invalid signer envelope");
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(alias.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(envelope, 4 + IV_LENGTH, envelope.length - 4 - IV_LENGTH);
    }

    void delete() throws GeneralSecurityException, IOException {
        file.delete();
        File directory = file.getBaseFile().getParentFile();
        if (directory != null && directory.isDirectory()) directory.delete();
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(alias)) store.deleteEntry(alias);
    }

    private SecretKey findKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        return (SecretKey) store.getKey(alias, null);
    }

    private SecretKey createKey() throws GeneralSecurityException {
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(
                new KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
        return generator.generateKey();
    }

    static final class OrphanedWrappingKeyException extends GeneralSecurityException {
        OrphanedWrappingKeyException(FileNotFoundException cause) {
            super("Portable signer file unavailable", cause);
        }
    }

    static final class StoredIdentity {
        private final PortableSigningIdentity signer;
        private final byte[] publicLineage;

        private StoredIdentity(PortableSigningIdentity signer, byte[] publicLineage) {
            this.signer = signer;
            this.publicLineage = publicLineage.clone();
        }

        PortableSigningIdentity signer() {
            return signer;
        }

        byte[] publicLineage() {
            return publicLineage.clone();
        }
    }
}
