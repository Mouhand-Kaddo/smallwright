package dev.smallwright.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Versioned AES-GCM envelope. The key is never serialized with the ciphertext. */
final class CredentialEnvelope {
    private static final byte[] MAGIC = {'P', 'L', 'C', 1};
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_CREDENTIAL_BYTES = 8192;
    static final int MAX_ENVELOPE_BYTES =
            MAGIC.length + NONCE_BYTES + MAX_CREDENTIAL_BYTES + TAG_BITS / 8;

    private CredentialEnvelope() {}

    static byte[] encrypt(SecretKey key, byte[] plaintext) throws GeneralSecurityException {
        if (plaintext == null || plaintext.length == 0 || plaintext.length > MAX_CREDENTIAL_BYTES) {
            throw new IllegalArgumentException("Invalid credential length");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] nonce = cipher.getIV();
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new GeneralSecurityException("Unsupported GCM nonce size");
        }
        cipher.updateAAD(MAGIC);
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] result = new byte[MAGIC.length + NONCE_BYTES + encrypted.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        System.arraycopy(nonce, 0, result, MAGIC.length, NONCE_BYTES);
        System.arraycopy(encrypted, 0, result, MAGIC.length + NONCE_BYTES, encrypted.length);
        return result;
    }

    static byte[] decrypt(SecretKey key, byte[] envelope)
            throws IOException, GeneralSecurityException {
        if (envelope == null
                || envelope.length < MAGIC.length + NONCE_BYTES + TAG_BITS / 8
                || envelope.length > MAX_ENVELOPE_BYTES
                || !Arrays.equals(MAGIC, Arrays.copyOf(envelope, MAGIC.length))) {
            throw new IOException("Invalid credential envelope");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, envelope, MAGIC.length, NONCE_BYTES));
        cipher.updateAAD(MAGIC);
        return cipher.doFinal(
                envelope, MAGIC.length + NONCE_BYTES, envelope.length - MAGIC.length - NONCE_BYTES);
    }
}
