package dev.smallwright.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Stores one model credential in controller-private, non-backed-up storage, under its own Android
 * Keystore key: the OpenRouter key, or the key of the OpenAI-compatible server.
 */
public final class CredentialStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private final AtomicFile file;
    private final String keyAlias;

    private CredentialStore(Context context, String fileName, String keyAlias) {
        File directory =
                new File(context.getApplicationContext().getNoBackupFilesDir(), "credentials");
        file = new AtomicFile(new File(directory, fileName));
        this.keyAlias = keyAlias;
    }

    public static CredentialStore openRouter(Context context) {
        return new CredentialStore(
                context, "model-credential.bin", "dev.smallwright.controller.model_credential.v1");
    }

    public static CredentialStore server(Context context) {
        return new CredentialStore(
                context,
                "server-credential.bin",
                "dev.smallwright.controller.server_credential.v1");
    }

    /** True when a key is saved, including AtomicFile's backup copy. Reads no key bytes. */
    public synchronized boolean exists() {
        File base = file.getBaseFile();
        return base.isFile() || new File(base.getPath() + ".bak").isFile();
    }

    /** The caller owns the input and must clear it when no longer needed. */
    public synchronized void save(byte[] credential) throws IOException, GeneralSecurityException {
        if (credential == null || credential.length == 0 || credential.length > 8192) {
            throw new IllegalArgumentException("Invalid credential length");
        }
        byte[] envelope = CredentialEnvelope.encrypt(getOrCreateKey(), credential);
        File directory = file.getBaseFile().getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create credential directory");
        }
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(envelope);
            file.finishWrite(stream);
        } catch (IOException | RuntimeException e) {
            if (stream != null) file.failWrite(stream);
            throw e;
        }
    }

    /** Returns a caller-owned byte array, or null when no credential has been saved. */
    public synchronized byte[] load() throws IOException, GeneralSecurityException {
        byte[] envelope;
        try (FileInputStream input = file.openRead()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > CredentialEnvelope.MAX_ENVELOPE_BYTES) {
                    throw new IOException("Credential envelope too large");
                }
                output.write(buffer, 0, count);
            }
            envelope = output.toByteArray();
        } catch (FileNotFoundException e) {
            return null;
        }
        SecretKey key = findKey();
        if (key == null) throw new GeneralSecurityException("Credential key unavailable");
        return CredentialEnvelope.decrypt(key, envelope);
    }

    public synchronized void clear() throws GeneralSecurityException, IOException {
        file.delete();
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        store.deleteEntry(keyAlias);
    }

    private SecretKey findKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        return (SecretKey) store.getKey(keyAlias, null);
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        SecretKey existing = findKey();
        if (existing != null) return existing;
        KeyGenerator generator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(
                new KeyGenParameterSpec.Builder(
                                keyAlias,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
        return generator.generateKey();
    }
}
