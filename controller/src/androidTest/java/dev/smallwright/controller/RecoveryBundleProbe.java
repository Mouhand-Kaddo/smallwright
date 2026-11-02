package dev.smallwright.controller;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

/** Exercises the combined recovery bundle and signer restore on Android ART. */
final class RecoveryBundleProbe {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";

    private RecoveryBundleProbe() {}

    static String run() throws Exception {
        char[] passphrase = "disposable Unicode recovery phrase café".toCharArray();
        char[] wrongPassphrase = "different disposable recovery phrase".toCharArray();
        try {
            PortableSigningIdentity signer = PortableSigningIdentity.create();
            byte[] java = "class MainActivity {}".getBytes(StandardCharsets.UTF_8);
            byte[] xml = "<View/>".getBytes(StandardCharsets.UTF_8);
            RecoveryBundle original =
                    RecoveryBundle.create(5, 8, Map.of(JAVA, java, XML, xml), signer, null);
            byte[] sealed = original.seal(passphrase);
            RecoveryBundle restored = RecoveryBundle.open(sealed, passphrase);
            if (restored.revision() != 5
                    || restored.versionCode() != 8
                    || !ProjectSourceStore.PACKAGE_NAME.equals(restored.packageName())
                    || !Arrays.equals(java, restored.sources().get(JAVA))
                    || !Arrays.equals(xml, restored.sources().get(XML))
                    || !Arrays.equals(
                            signer.certificate().getEncoded(),
                            restored.signer().certificate().getEncoded())) {
                throw new AssertionError("Recovery bundle round trip changed project identity");
            }
            expectFailure(() -> RecoveryBundle.open(sealed, wrongPassphrase));
            byte[] tampered = sealed.clone();
            tampered[tampered.length - 1] ^= 1;
            expectFailure(() -> RecoveryBundle.open(tampered, passphrase));
            return "Recovery bundle: disposable signer and fixed sources restored on Android\n";
        } finally {
            Arrays.fill(passphrase, '\0');
            Arrays.fill(wrongPassphrase, '\0');
        }
    }

    private static void expectFailure(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (GeneralSecurityException expected) {
            return;
        }
        throw new AssertionError("Invalid recovery bundle accepted");
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
