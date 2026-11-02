package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Arrays;
import org.junit.Test;

public final class PortableSigningIdentityTest {
    @Test
    public void freshIdentityCanBeRestoredWithTheExactCertificate() throws Exception {
        PortableSigningIdentity fresh = PortableSigningIdentity.create();
        byte[] key = fresh.exportPkcs8();
        byte[] certificate = fresh.certificate().getEncoded();
        assertNotNull(key);
        assertTrue(key.length > 0);
        assertTrue(fresh.certificate().getPublicKey().getAlgorithm().equals("RSA"));
        assertFalse(Arrays.equals(key, certificate));

        PortableSigningIdentity restored = PortableSigningIdentity.restore(key, certificate);
        assertArrayEquals(certificate, restored.certificate().getEncoded());
        byte[] challenge =
                "disposable signer challenge".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(restored.privateKey());
        signer.update(challenge);
        byte[] proof = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(fresh.certificate().getPublicKey());
        verifier.update(challenge);
        assertTrue(verifier.verify(proof));
    }

    @Test
    public void refusesMismatchedOrDamagedIdentity() throws Exception {
        PortableSigningIdentity first = PortableSigningIdentity.create();
        PortableSigningIdentity second = PortableSigningIdentity.create();
        byte[] key = first.exportPkcs8();
        byte[] certificate = first.certificate().getEncoded();
        assertThrows(
                GeneralSecurityException.class,
                () -> PortableSigningIdentity.restore(key, second.certificate().getEncoded()));
        certificate[certificate.length - 1] ^= 1;
        assertThrows(
                GeneralSecurityException.class,
                () -> PortableSigningIdentity.restore(key, certificate));
        assertThrows(
                GeneralSecurityException.class,
                () -> PortableSigningIdentity.restore(new byte[20_000], new byte[20_000]));
    }
}
