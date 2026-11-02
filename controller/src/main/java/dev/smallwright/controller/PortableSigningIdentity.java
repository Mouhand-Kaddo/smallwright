package dev.smallwright.controller;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** A newly generated software signer; no current project key is read here. */
final class PortableSigningIdentity {
    private static final int KEY_BITS = 3072;
    private static final int MAX_DER_BYTES = 16 * 1024;
    private static final byte[] PROOF =
            "smallwright-portable-signer-proof"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    private PortableSigningIdentity(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    static PortableSigningIdentity create() throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_BITS, random);
        KeyPair pair = generator.generateKeyPair();
        X500Principal subject = new X500Principal("CN=Smallwright Generated Counter Recovery");
        byte[] serialBytes = new byte[16];
        random.nextBytes(serialBytes);
        BigInteger serial = new BigInteger(1, serialBytes).setBit(127);
        long now = System.currentTimeMillis();
        long year = 365L * 24 * 60 * 60 * 1000;
        byte[] der =
                new JcaX509v3CertificateBuilder(
                                subject,
                                serial,
                                new Date(now - year),
                                new Date(now + 30 * year),
                                subject,
                                pair.getPublic())
                        .build(
                                new JcaContentSignerBuilder("SHA256withRSA")
                                        .build(pair.getPrivate()))
                        .getEncoded();
        X509Certificate certificate =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509")
                                .generateCertificate(new ByteArrayInputStream(der));
        PortableSigningIdentity identity =
                new PortableSigningIdentity(pair.getPrivate(), certificate);
        identity.validate();
        return identity;
    }

    static PortableSigningIdentity restore(byte[] pkcs8, byte[] certificateDer)
            throws GeneralSecurityException {
        if (pkcs8 == null
                || pkcs8.length == 0
                || pkcs8.length > MAX_DER_BYTES
                || certificateDer == null
                || certificateDer.length == 0
                || certificateDer.length > MAX_DER_BYTES) {
            throw new GeneralSecurityException("Invalid portable identity size");
        }
        PrivateKey key =
                KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        X509Certificate certificate =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509")
                                .generateCertificate(new ByteArrayInputStream(certificateDer));
        if (!Arrays.equals(certificateDer, certificate.getEncoded())) {
            throw new GeneralSecurityException("Invalid portable certificate encoding");
        }
        PortableSigningIdentity identity = new PortableSigningIdentity(key, certificate);
        identity.validate();
        return identity;
    }

    PrivateKey privateKey() {
        return privateKey;
    }

    X509Certificate certificate() {
        return certificate;
    }

    byte[] exportPkcs8() throws GeneralSecurityException {
        byte[] encoded = privateKey.getEncoded();
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_DER_BYTES) {
            throw new GeneralSecurityException("Portable private key encoding unavailable");
        }
        return encoded;
    }

    private void validate() throws GeneralSecurityException {
        if (!(certificate.getPublicKey() instanceof RSAPublicKey)
                || ((RSAPublicKey) certificate.getPublicKey()).getModulus().bitLength() < KEY_BITS
                || !"RSA".equals(privateKey.getAlgorithm())
                || !certificate
                        .getSubjectX500Principal()
                        .equals(certificate.getIssuerX500Principal())) {
            throw new GeneralSecurityException("Invalid portable signer certificate");
        }
        certificate.checkValidity();
        certificate.verify(certificate.getPublicKey());
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(PROOF);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(PROOF);
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("Portable signer key does not match certificate");
        }
    }
}
