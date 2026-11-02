package dev.smallwright.security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.junit.Test;

public final class CredentialEnvelopeTest {
    private static final byte[] DUMMY = "dummy-test-credential".getBytes(StandardCharsets.UTF_8);

    @Test
    public void roundTripUsesFreshNonceAndDoesNotStorePlaintext() throws Exception {
        SecretKey key = newKey();
        byte[] first = CredentialEnvelope.encrypt(key, DUMMY);
        byte[] second = CredentialEnvelope.encrypt(key, DUMMY);

        assertArrayEquals(DUMMY, CredentialEnvelope.decrypt(key, first));
        assertArrayEquals(DUMMY, CredentialEnvelope.decrypt(key, second));
        assertFalse(Arrays.equals(first, second));
        assertFalse(
                new String(first, StandardCharsets.ISO_8859_1).contains("dummy-test-credential"));
    }

    @Test
    public void wrongKeyAndTamperingFailClosed() throws Exception {
        SecretKey key = newKey();
        byte[] envelope = CredentialEnvelope.encrypt(key, DUMMY);
        assertThrows(
                GeneralSecurityException.class,
                () -> CredentialEnvelope.decrypt(newKey(), envelope));

        envelope[envelope.length - 1] ^= 1;
        assertThrows(
                GeneralSecurityException.class, () -> CredentialEnvelope.decrypt(key, envelope));
    }

    @Test
    public void malformedAndOversizedInputsAreRejected() throws Exception {
        SecretKey key = newKey();
        byte[] envelope = CredentialEnvelope.encrypt(key, DUMMY);
        envelope[0] ^= 1;
        assertThrows(IOException.class, () -> CredentialEnvelope.decrypt(key, envelope));
        assertThrows(IOException.class, () -> CredentialEnvelope.decrypt(key, new byte[8]));
        assertThrows(
                IllegalArgumentException.class, () -> CredentialEnvelope.encrypt(key, new byte[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> CredentialEnvelope.encrypt(key, new byte[8193]));
        assertThrows(
                IOException.class,
                () ->
                        CredentialEnvelope.decrypt(
                                key, new byte[CredentialEnvelope.MAX_ENVELOPE_BYTES + 1]));
    }

    private static SecretKey newKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        return generator.generateKey();
    }
}
