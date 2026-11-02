package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.junit.Test;

public final class RecoveryEnvelopeTest {
    private static final char[] PASSPHRASE = "disposable recovery phrase 42".toCharArray();

    @Test
    public void sealsAndOpensWithoutExposingDummyPayload() throws Exception {
        byte[] payload = "dummy portable identity and project".getBytes(StandardCharsets.UTF_8);
        byte[] first = RecoveryEnvelope.seal(payload, PASSPHRASE);
        byte[] second = RecoveryEnvelope.seal(payload, PASSPHRASE);
        assertFalse(Arrays.equals(first, second));
        assertFalse(new String(first, StandardCharsets.ISO_8859_1).contains("dummy portable"));
        assertArrayEquals(payload, RecoveryEnvelope.open(first, PASSPHRASE));
        assertArrayEquals(payload, RecoveryEnvelope.open(second, PASSPHRASE));
    }

    @Test
    public void wrongPassphraseAndMutatedEnvelopeFailClosed() throws Exception {
        byte[] sealed = RecoveryEnvelope.seal(new byte[] {1, 2, 3}, PASSPHRASE);
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryEnvelope.open(sealed, "different recovery phrase".toCharArray()));
        for (int offset : new int[] {0, 4, 5, 21, 33, sealed.length - 1}) {
            byte[] changed = sealed.clone();
            changed[offset] ^= 1;
            assertThrows(
                    "offset " + offset,
                    GeneralSecurityException.class,
                    () -> RecoveryEnvelope.open(changed, PASSPHRASE));
        }
        byte[] extended = Arrays.copyOf(sealed, sealed.length + 1);
        assertThrows(
                GeneralSecurityException.class, () -> RecoveryEnvelope.open(extended, PASSPHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryEnvelope.open(Arrays.copyOf(sealed, sealed.length - 1), PASSPHRASE));
    }

    @Test
    public void rejectsWeakOrOversizedInputsBeforeKeyDerivation() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecoveryEnvelope.seal(new byte[] {1}, "short".toCharArray()));
        assertThrows(
                IllegalArgumentException.class,
                () -> RecoveryEnvelope.seal(new byte[0], PASSPHRASE));
        assertThrows(
                IllegalArgumentException.class,
                () -> RecoveryEnvelope.seal(new byte[3 * 1024 * 1024 + 1], PASSPHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryEnvelope.open(new byte[3 * 1024 * 1024 + 100], PASSPHRASE));
    }
}
