package dev.smallwright.controller;

import java.security.Signature;
import java.util.Arrays;

/** Checks software certificate generation in ART with a disposable identity. */
final class PortableSigningIdentityProbe {
    private PortableSigningIdentityProbe() {}

    static String run() throws Exception {
        PortableSigningIdentity original = PortableSigningIdentity.create();
        byte[] secret = original.exportPkcs8();
        try {
            PortableSigningIdentity restored =
                    PortableSigningIdentity.restore(secret, original.certificate().getEncoded());
            byte[] challenge =
                    "disposable portable signer probe"
                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(restored.privateKey());
            signer.update(challenge);
            byte[] proof = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(original.certificate().getPublicKey());
            verifier.update(challenge);
            if (!verifier.verify(proof)) throw new AssertionError("Restored signer did not match");
            return "Portable signer: disposable RSA key and certificate restored on Android\n";
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }
}
