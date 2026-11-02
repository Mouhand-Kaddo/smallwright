package dev.smallwright.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Android-provider check using dummy bytes, never a project identity. */
final class RecoveryEnvelopeProbe {
    private RecoveryEnvelopeProbe() {}

    static String run() throws Exception {
        byte[] dummy = "test-only recovery payload".getBytes(StandardCharsets.UTF_8);
        char[] passphrase = "disposable café recovery phrase".toCharArray();
        try {
            byte[] envelope = RecoveryEnvelope.seal(dummy, passphrase);
            if (Arrays.equals(dummy, envelope)
                    || !Arrays.equals(dummy, RecoveryEnvelope.open(envelope, passphrase))) {
                throw new AssertionError("Android recovery envelope round trip failed");
            }
            byte[] damaged = envelope.clone();
            damaged[damaged.length - 1] ^= 1;
            try {
                RecoveryEnvelope.open(damaged, passphrase);
                throw new AssertionError("Damaged recovery envelope opened");
            } catch (GeneralSecurityException expected) {
                // Authenticated encryption must reject changed ciphertext.
            }
            try {
                RecoveryEnvelope.open(envelope, "different disposable phrase".toCharArray());
                throw new AssertionError("Wrong passphrase opened recovery envelope");
            } catch (GeneralSecurityException expected) {
                // Password derivation must be required to authenticate the bundle.
            }
            return "Recovery envelope: dummy Unicode round trip and rejection checks passed\n";
        } finally {
            Arrays.fill(passphrase, '\0');
        }
    }
}
