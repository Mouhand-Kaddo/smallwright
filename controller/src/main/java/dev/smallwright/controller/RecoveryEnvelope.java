package dev.smallwright.controller;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Versioned encryption boundary for explicit project recovery export. */
final class RecoveryEnvelope {
    private static final byte[] MAGIC = {'P', 'L', 'B', 'R'};
    private static final byte VERSION = 1;
    private static final byte[] PROJECT =
            "dev.smallwright.generated.counter".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int HEADER_BYTES = MAGIC.length + 1 + SALT_BYTES + NONCE_BYTES + 4;
    private static final int MAX_PAYLOAD_BYTES = 3 * 1024 * 1024;
    // A future change to the derivation parameters must use a new envelope version.
    private static final int PBKDF2_ITERATIONS = 600_000;

    private RecoveryEnvelope() {}

    static byte[] seal(byte[] payload, char[] passphrase) throws GeneralSecurityException {
        requirePassphrase(passphrase);
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid recovery payload size");
        }
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(salt);
        random.nextBytes(nonce);
        byte[] envelope =
                ByteBuffer.allocate(HEADER_BYTES + payload.length + TAG_BYTES)
                        .put(MAGIC)
                        .put(VERSION)
                        .put(salt)
                        .put(nonce)
                        .putInt(payload.length + TAG_BYTES)
                        .array();
        byte[] key = derive(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(envelope, 0, HEADER_BYTES);
            cipher.updateAAD(PROJECT);
            byte[] ciphertext = cipher.doFinal(payload);
            System.arraycopy(ciphertext, 0, envelope, HEADER_BYTES, ciphertext.length);
            return envelope;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    static byte[] open(byte[] envelope, char[] passphrase) throws GeneralSecurityException {
        if (envelope == null
                || envelope.length < HEADER_BYTES + TAG_BYTES + 1
                || envelope.length > HEADER_BYTES + MAX_PAYLOAD_BYTES + TAG_BYTES) {
            throw new GeneralSecurityException("Invalid recovery envelope");
        }
        ByteBuffer header = ByteBuffer.wrap(envelope);
        byte[] magic = new byte[MAGIC.length];
        header.get(magic);
        if (!Arrays.equals(magic, MAGIC) || header.get() != VERSION) {
            throw new GeneralSecurityException("Unsupported recovery envelope");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        header.get(salt);
        header.get(nonce);
        int ciphertextLength = header.getInt();
        if (ciphertextLength < TAG_BYTES + 1
                || ciphertextLength > MAX_PAYLOAD_BYTES + TAG_BYTES
                || HEADER_BYTES + ciphertextLength != envelope.length) {
            throw new GeneralSecurityException("Invalid recovery envelope");
        }
        requirePassphrase(passphrase);
        byte[] key = derive(passphrase, salt);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(envelope, 0, HEADER_BYTES);
            cipher.updateAAD(PROJECT);
            return cipher.doFinal(envelope, HEADER_BYTES, ciphertextLength);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length < 12 || passphrase.length > 1024) {
            throw new IllegalArgumentException("Recovery passphrase must be 12-1024 characters");
        }
    }

    private static byte[] derive(char[] passphrase, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();
        } finally {
            specification.clearPassword();
        }
    }
}
