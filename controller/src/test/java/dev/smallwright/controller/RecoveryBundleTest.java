package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.android.apksig.SigningCertificateLineage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public final class RecoveryBundleTest {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";
    private static final char[] PHRASE = "disposable recovery phrase 42".toCharArray();

    @Test
    public void roundTripCarriesOnlyFixedSourcesAndMatchingSigner() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        byte[] java = "class MainActivity {}".getBytes(StandardCharsets.UTF_8);
        byte[] xml = "<View/>".getBytes(StandardCharsets.UTF_8);
        byte[] lineage = validLineage(PortableSigningIdentity.create(), signer);
        RecoveryBundle bundle =
                RecoveryBundle.create(7, 12, Map.of(JAVA, java, XML, xml), signer, lineage);
        byte[] sealed = bundle.seal(PHRASE);
        assertFalse(new String(sealed, StandardCharsets.ISO_8859_1).contains("MainActivity"));
        RecoveryBundle opened = RecoveryBundle.open(sealed, PHRASE);
        assertEquals(ProjectSourceStore.PACKAGE_NAME, opened.packageName());
        assertEquals(7, opened.revision());
        assertEquals(12, opened.versionCode());
        assertEquals(2, opened.sources().size());
        assertArrayEquals(java, opened.sources().get(JAVA));
        assertArrayEquals(xml, opened.sources().get(XML));
        assertArrayEquals(lineage, opened.publicLineage());
        assertArrayEquals(
                signer.certificate().getEncoded(), opened.signer().certificate().getEncoded());
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.open(sealed, "wrong recovery phrase".toCharArray()));
    }

    @Test
    public void rejectsExtraOrMissingSourcesAndOversizedFields() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        Map<String, byte[]> sources = Map.of(JAVA, new byte[] {1}, XML, new byte[] {2});
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, Map.of(JAVA, new byte[] {1}), signer, null));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.create(
                                0,
                                1,
                                Map.of(
                                        JAVA,
                                        new byte[] {1},
                                        XML,
                                        new byte[] {2},
                                        "other",
                                        new byte[] {3}),
                                signer,
                                null));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(-1, 1, sources, signer, null));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 0, sources, signer, null));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.create(
                                0,
                                1,
                                Map.of(JAVA, new byte[64 * 1024 + 1], XML, new byte[] {2}),
                                signer,
                                null));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, sources, signer, new byte[64 * 1024 + 1]));
    }

    @Test
    public void roundTripCarriesCompleteBoundedSnapshotAndCopiesInputs() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        Map<String, byte[]> files = new HashMap<>();
        files.put(JAVA, new byte[] {1});
        files.put(XML, new byte[] {2});
        files.put("java/dev/smallwright/generated/counter/Helper.java", new byte[] {3});
        files.put("java/dev/smallwright/generated/counter/Widget.java", new byte[] {4});
        files.put("res/layout/main.xml", new byte[] {5});
        files.put("res/values/colors.xml", new byte[] {6});
        files.put("res/values/strings.xml", new byte[] {7});
        RecoveryBundle bundle = RecoveryBundle.create(4, 9, files, signer, null);
        files.get(JAVA)[0] = 99;
        assertEquals(7, bundle.sources().size());
        assertArrayEquals(new byte[] {1}, bundle.sources().get(JAVA));
        RecoveryBundle opened = RecoveryBundle.open(bundle.seal(PHRASE), PHRASE);
        assertEquals(7, opened.sources().size());
        for (int i = 1; i <= 7; i++) {
            String path =
                    i == 1
                            ? JAVA
                            : i == 2
                                    ? XML
                                    : i == 3
                                            ? "java/dev/smallwright/generated/counter/Helper.java"
                                            : i == 4
                                                    ? "java/dev/smallwright/generated/counter/Widget.java"
                                                    : i == 5
                                                            ? "res/layout/main.xml"
                                                            : i == 6
                                                                    ? "res/values/colors.xml"
                                                                    : "res/values/strings.xml";
            assertArrayEquals(new byte[] {(byte) i}, opened.sources().get(path));
        }
        opened.sources().get(JAVA)[0] = 55;
        assertArrayEquals(new byte[] {1}, opened.sources().get(JAVA));
    }

    @Test
    public void rejectsInvalidSnapshotPathsCountsAndAggregateSize() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        Map<String, byte[]> files = new HashMap<>();
        files.put(JAVA, new byte[] {1});
        files.put(XML, new byte[] {2});
        files.put("res/values/strings.xml", new byte[] {3});
        RecoveryBundle.create(0, 1, files, signer, null);
        files.put("res/values/strings.xml", new byte[64 * 1024 + 1]);
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, null));
        files.put("res/values/strings.xml", new byte[] {3});
        files.put("res/values/../secrets.xml", new byte[] {4});
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, null));
        files.remove("res/values/../secrets.xml");
        files.put("java/dev/smallwright/generated/counter/A.java", new byte[64 * 1024]);
        files.put("java/dev/smallwright/generated/counter/B.java", new byte[64 * 1024]);
        files.put("java/dev/smallwright/generated/counter/C.java", new byte[64 * 1024]);
        files.put("java/dev/smallwright/generated/counter/D.java", new byte[64 * 1024]);
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, null));
        files.put("java/dev/smallwright/generated/counter/E.java", new byte[] {5});
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, null));
    }

    @Test
    public void roundTripsAllowedLongSourceFilename() throws Exception {
        String longPath = "java/dev/smallwright/generated/counter/A" + "b".repeat(180) + ".java";
        RecoveryBundle bundle =
                RecoveryBundle.create(
                        0,
                        1,
                        Map.of(JAVA, new byte[] {1}, XML, new byte[] {2}, longPath, new byte[] {3}),
                        PortableSigningIdentity.create(),
                        null);
        assertArrayEquals(
                new byte[] {3},
                RecoveryBundle.open(bundle.seal(PHRASE), PHRASE).sources().get(longPath));
    }

    @Test
    public void rejectsMalformedPlaintextEvenInsideValidEnvelope() throws Exception {
        PortableSigningIdentity first = PortableSigningIdentity.create();
        PortableSigningIdentity second = PortableSigningIdentity.create();
        byte[] valid =
                payload(
                        1,
                        2,
                        ProjectSourceStore.PACKAGE_NAME,
                        JAVA,
                        XML,
                        first.exportPkcs8(),
                        first.certificate().getEncoded(),
                        new byte[0]);
        byte[] unknownVersion = valid.clone();
        unknownVersion[4] = 3;
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.open(RecoveryEnvelope.seal(unknownVersion, PHRASE), PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        Arrays.copyOf(valid, valid.length - 1), PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        Arrays.copyOf(valid, valid.length + 1), PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payload(
                                                1,
                                                2,
                                                "other.package",
                                                JAVA,
                                                XML,
                                                first.exportPkcs8(),
                                                first.certificate().getEncoded(),
                                                null),
                                        PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payload(
                                                1,
                                                2,
                                                ProjectSourceStore.PACKAGE_NAME,
                                                "../bad",
                                                XML,
                                                first.exportPkcs8(),
                                                first.certificate().getEncoded(),
                                                null),
                                        PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payload(
                                                1,
                                                2,
                                                ProjectSourceStore.PACKAGE_NAME,
                                                JAVA,
                                                XML,
                                                first.exportPkcs8(),
                                                second.certificate().getEncoded(),
                                                null),
                                        PHRASE),
                                PHRASE));
        byte[] negativeLength = valid.clone();
        // First source path length follows magic, format version, revision, version and package.
        int sourcePathLengthOffset =
                4
                        + 1
                        + 4
                        + 4
                        + 4
                        + ProjectSourceStore.PACKAGE_NAME.getBytes(StandardCharsets.UTF_8).length;
        Arrays.fill(
                negativeLength, sourcePathLengthOffset, sourcePathLengthOffset + 4, (byte) 0xff);
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.open(RecoveryEnvelope.seal(negativeLength, PHRASE), PHRASE));
    }

    @Test
    public void v2EncodingIsCanonicalAndDecoderRejectsDuplicateOrUnsortedPaths() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        Map<String, byte[]> first = new java.util.LinkedHashMap<>();
        first.put(XML, new byte[] {2});
        first.put("res/values/strings.xml", new byte[] {3});
        first.put(JAVA, new byte[] {1});
        Map<String, byte[]> second = new java.util.LinkedHashMap<>();
        second.put(JAVA, new byte[] {1});
        second.put(XML, new byte[] {2});
        second.put("res/values/strings.xml", new byte[] {3});
        byte[] one =
                RecoveryEnvelope.open(
                        RecoveryBundle.create(1, 2, first, signer, null).seal(PHRASE), PHRASE);
        byte[] two =
                RecoveryEnvelope.open(
                        RecoveryBundle.create(1, 2, second, signer, null).seal(PHRASE), PHRASE);
        assertArrayEquals(one, two);
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(signer, new String[] {JAVA, JAVA, XML}), PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(signer, new String[] {XML, JAVA}), PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(
                                                signer,
                                                new String[] {JAVA, "res/values/../bad.xml", XML}),
                                        PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(payloadV2(signer, new String[0]), PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(
                                                signer,
                                                new String[] {
                                                    JAVA, XML, XML, XML, XML, XML, XML, XML
                                                }),
                                        PHRASE),
                                PHRASE));
    }

    @Test
    public void opensExistingTwoFileFormat() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        byte[] legacy =
                payload(
                        1,
                        2,
                        ProjectSourceStore.PACKAGE_NAME,
                        JAVA,
                        XML,
                        signer.exportPkcs8(),
                        signer.certificate().getEncoded(),
                        new byte[0]);
        RecoveryBundle opened = RecoveryBundle.open(RecoveryEnvelope.seal(legacy, PHRASE), PHRASE);
        assertEquals(2, opened.sources().size());
        assertArrayEquals(new byte[] {1}, opened.sources().get(JAVA));
        assertArrayEquals(new byte[] {2}, opened.sources().get(XML));
    }

    @Test
    public void rejectsMalformedOrMismatchedLineageAndAcceptsMatchingLineage() throws Exception {
        PortableSigningIdentity oldSigner = PortableSigningIdentity.create();
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        PortableSigningIdentity differentSigner = PortableSigningIdentity.create();
        Map<String, byte[]> files = Map.of(JAVA, new byte[] {1}, XML, new byte[] {2});
        byte[] valid = validLineage(oldSigner, signer);
        byte[] wrongEnd = validLineage(oldSigner, differentSigner);
        assertArrayEquals(
                valid,
                RecoveryBundle.open(
                                RecoveryBundle.create(0, 1, files, signer, valid).seal(PHRASE),
                                PHRASE)
                        .publicLineage());
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, new byte[] {1, 2, 3}));
        assertThrows(
                GeneralSecurityException.class,
                () -> RecoveryBundle.create(0, 1, files, signer, wrongEnd));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(
                                                signer,
                                                new String[] {JAVA, XML},
                                                new byte[] {1, 2, 3}),
                                        PHRASE),
                                PHRASE));
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryBundle.open(
                                RecoveryEnvelope.seal(
                                        payloadV2(signer, new String[] {JAVA, XML}, wrongEnd),
                                        PHRASE),
                                PHRASE));
    }

    private static byte[] validLineage(
            PortableSigningIdentity oldSigner, PortableSigningIdentity signer) throws Exception {
        SigningCertificateLineage.SignerConfig before =
                new SigningCertificateLineage.SignerConfig.Builder(
                                oldSigner.privateKey(), oldSigner.certificate())
                        .build();
        SigningCertificateLineage.SignerConfig after =
                new SigningCertificateLineage.SignerConfig.Builder(
                                signer.privateKey(), signer.certificate())
                        .build();
        return new SigningCertificateLineage.Builder(before, after)
                .setMinSdkVersion(28)
                .build()
                .getBytes();
    }

    private static byte[] payloadV2(PortableSigningIdentity signer, String[] paths)
            throws Exception {
        return payloadV2(signer, paths, new byte[0]);
    }

    private static byte[] payloadV2(PortableSigningIdentity signer, String[] paths, byte[] lineage)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {'P', 'L', 'B', 'B'});
        out.writeByte(2);
        out.writeInt(1);
        out.writeInt(2);
        field(out, ProjectSourceStore.PACKAGE_NAME.getBytes(StandardCharsets.UTF_8));
        out.writeInt(paths.length);
        for (String path : paths) {
            field(out, path.getBytes(StandardCharsets.UTF_8));
            field(out, new byte[] {1});
        }
        field(out, signer.exportPkcs8());
        field(out, signer.certificate().getEncoded());
        field(out, lineage);
        return bytes.toByteArray();
    }

    private static byte[] payload(
            int revision,
            int version,
            String pkg,
            String javaPath,
            String xmlPath,
            byte[] key,
            byte[] certificate,
            byte[] lineage)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {'P', 'L', 'B', 'B'});
        out.writeByte(1);
        out.writeInt(revision);
        out.writeInt(version);
        field(out, pkg.getBytes(StandardCharsets.UTF_8));
        field(out, javaPath.getBytes(StandardCharsets.UTF_8));
        field(out, new byte[] {1});
        field(out, xmlPath.getBytes(StandardCharsets.UTF_8));
        field(out, new byte[] {2});
        field(out, key);
        field(out, certificate);
        field(out, lineage == null ? new byte[0] : lineage);
        return bytes.toByteArray();
    }

    private static void field(DataOutputStream out, byte[] value) throws Exception {
        out.writeInt(value.length);
        out.write(value);
    }
}
