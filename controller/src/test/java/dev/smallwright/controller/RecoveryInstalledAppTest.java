package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.apksig.SigningCertificateLineage;
import java.security.GeneralSecurityException;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

public final class RecoveryInstalledAppTest {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";
    private static PortableSigningIdentity oldSigner;
    private static PortableSigningIdentity currentSigner;
    private static PortableSigningIdentity unrelatedSigner;

    @BeforeClass
    public static void signers() throws Exception {
        oldSigner = PortableSigningIdentity.create();
        currentSigner = PortableSigningIdentity.create();
        unrelatedSigner = PortableSigningIdentity.create();
    }

    @Test
    public void absentAndExactSignerAdvanceVersion() throws Exception {
        RecoveryBundle bundle = bundle(7, null);
        assertEquals(8, RecoveryInstalledApp.nextVersionCode(bundle, null, 0));
        assertEquals(
                8, RecoveryInstalledApp.nextVersionCode(bundle, certificate(currentSigner), 3));
        assertEquals(
                13, RecoveryInstalledApp.nextVersionCode(bundle, certificate(currentSigner), 12));
        assertEquals(
                13,
                RecoveryInstalledApp.nextVersionCode(
                        7, certificate(currentSigner), null, certificate(currentSigner), 12));
    }

    @Test
    public void earlierSignerRequiresInstalledDataCapability() throws Exception {
        byte[] permitted = lineage(true);
        byte[] forbidden = lineage(false);
        assertEquals(
                11,
                RecoveryInstalledApp.nextVersionCode(
                        bundle(7, permitted), certificate(oldSigner), 10));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                bundle(7, forbidden), certificate(oldSigner), 10));
    }

    @Test
    public void unrelatedOrInvalidCertificatesFailClosed() throws Exception {
        byte[] permitted = lineage(true);
        RecoveryBundle bundle = bundle(7, permitted);
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                bundle, certificate(unrelatedSigner), 8));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(bundle, new byte[] {1, 2, 3}, 8));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(bundle, new byte[0], 8));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(bundle, null, 8));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(bundle, certificate(currentSigner), 0));
    }

    @Test
    public void malformedOrWrongEndLineageFailsEvenWithExactSigner() throws Exception {
        byte[] current = certificate(currentSigner);
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                7, current, new byte[] {1, 2, 3}, current, 7));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                7, certificate(unrelatedSigner), lineage(true), current, 7));
    }

    @Test
    public void unusableVersionNumbersFail() throws Exception {
        byte[] current = certificate(currentSigner);
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(0, current, null, null, 0));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                Integer.MAX_VALUE - 1, current, null, null, 0));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                1, current, null, current, Integer.MAX_VALUE - 1L));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryInstalledApp.nextVersionCode(
                                1, current, null, current, Long.MAX_VALUE));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryInstalledApp.nextVersionCode(1, current, null, current, -1));
    }

    private static RecoveryBundle bundle(int version, byte[] lineage) throws Exception {
        return RecoveryBundle.create(
                1,
                version,
                Map.of(JAVA, new byte[] {1}, XML, new byte[] {2}),
                currentSigner,
                lineage);
    }

    private static byte[] certificate(PortableSigningIdentity signer) throws Exception {
        return signer.certificate().getEncoded();
    }

    private static byte[] lineage(boolean installedData) throws Exception {
        SigningCertificateLineage.SignerConfig before =
                new SigningCertificateLineage.SignerConfig.Builder(
                                oldSigner.privateKey(), oldSigner.certificate())
                        .build();
        SigningCertificateLineage.SignerConfig after =
                new SigningCertificateLineage.SignerConfig.Builder(
                                currentSigner.privateKey(), currentSigner.certificate())
                        .build();
        return new SigningCertificateLineage.Builder(before, after)
                .setMinSdkVersion(28)
                .setOriginalCapabilities(
                        new SigningCertificateLineage.SignerCapabilities.Builder()
                                .setInstalledData(installedData)
                                .build())
                .build()
                .getBytes();
    }
}
