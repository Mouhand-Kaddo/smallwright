package dev.smallwright.controller;

import com.android.apksig.SigningCertificateLineage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/** Checks whether a recovered signer can update the currently installed generated app. */
final class RecoveryInstalledApp {
    private RecoveryInstalledApp() {}

    static int nextVersionCode(
            RecoveryBundle bundle, byte[] installedCurrentCertificateDer, long installedVersionCode)
            throws GeneralSecurityException {
        if (bundle == null) throw new GeneralSecurityException("Missing recovery bundle");
        return nextVersionCode(
                bundle.versionCode(),
                certificateBytes(bundle.signer()),
                bundle.publicLineage(),
                installedCurrentCertificateDer,
                installedVersionCode);
    }

    /** A null installed certificate and zero version mean the package is absent. */
    static int nextVersionCode(
            int savedVersionCode,
            byte[] recoveredCertificateDer,
            byte[] publicLineage,
            byte[] installedCurrentCertificateDer,
            long installedVersionCode)
            throws GeneralSecurityException {
        if (savedVersionCode < 1 || savedVersionCode >= Integer.MAX_VALUE - 1) {
            throw new GeneralSecurityException("Recovery version cannot advance");
        }
        X509Certificate recovered = certificate(recoveredCertificateDer);
        SigningCertificateLineage lineage = null;
        if (publicLineage != null && publicLineage.length > 64 * 1024) {
            throw new GeneralSecurityException("Recovery signing lineage too large");
        }
        if (publicLineage != null && publicLineage.length != 0) {
            try {
                lineage = SigningCertificateLineage.readFromBytes(publicLineage);
                if (!lineage.isCertificateLatestInLineage(recovered)) {
                    throw new GeneralSecurityException("Recovery lineage ends with another signer");
                }
            } catch (IOException | RuntimeException invalid) {
                throw new GeneralSecurityException("Invalid recovery signing lineage", invalid);
            }
        }
        if (installedCurrentCertificateDer == null) {
            if (installedVersionCode != 0) {
                throw new GeneralSecurityException("Installed app metadata is inconsistent");
            }
        } else {
            if (installedVersionCode < 1 || installedVersionCode >= Integer.MAX_VALUE - 1L) {
                throw new GeneralSecurityException("Installed app version cannot advance");
            }
            X509Certificate installed = certificate(installedCurrentCertificateDer);
            if (!Arrays.equals(installedCurrentCertificateDer, recoveredCertificateDer)) {
                if (lineage == null
                        || !lineage.isCertificateInLineage(installed)
                        || !lineage.getSignerCapabilities(installed).hasInstalledData()) {
                    throw new GeneralSecurityException("Installed app signer is incompatible");
                }
            }
        }
        long next = Math.max((long) savedVersionCode, installedVersionCode) + 1;
        if (next >= Integer.MAX_VALUE) {
            throw new GeneralSecurityException("Recovery version cannot advance");
        }
        return (int) next;
    }

    private static X509Certificate certificate(byte[] der) throws GeneralSecurityException {
        if (der == null || der.length == 0 || der.length > 16 * 1024) {
            throw new GeneralSecurityException("Invalid signer certificate size");
        }
        ByteArrayInputStream input = new ByteArrayInputStream(der);
        X509Certificate parsed =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509").generateCertificate(input);
        if (input.available() != 0 || !Arrays.equals(der, parsed.getEncoded())) {
            throw new GeneralSecurityException("Invalid signer certificate encoding");
        }
        return parsed;
    }

    private static byte[] certificateBytes(PortableSigningIdentity identity)
            throws GeneralSecurityException {
        if (identity == null) throw new GeneralSecurityException("Missing recovered signer");
        return identity.certificate().getEncoded();
    }
}
