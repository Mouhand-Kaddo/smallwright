package dev.smallwright.controller;

import com.android.apksig.SigningCertificateLineage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Fixed-project recovery data. Its serialized form is always sealed as one envelope. */
final class RecoveryBundle {
    private static final byte[] MAGIC = {'P', 'L', 'B', 'B'};
    private static final int FORMAT_VERSION = 2;
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";
    private static final int MAX_SOURCE_BYTES = 64 * 1024;
    private static final int MAX_TOTAL_SOURCE_BYTES = 252 * 1024;
    private static final int MAX_FILES = 7;
    private static final int MAX_PATH_BYTES = 512;
    private static final int MAX_IDENTITY_BYTES = 16 * 1024;
    private static final int MAX_LINEAGE_BYTES = 64 * 1024;
    private static final int MAX_PAYLOAD_BYTES =
            MAX_TOTAL_SOURCE_BYTES
                    + 2 * MAX_IDENTITY_BYTES
                    + MAX_LINEAGE_BYTES
                    + MAX_FILES * (MAX_PATH_BYTES + 8)
                    + 1024;

    private final int revision;
    private final int versionCode;
    private final Map<String, byte[]> sources;
    private final PortableSigningIdentity signer;
    private final byte[] publicLineage;

    private RecoveryBundle(
            int revision,
            int versionCode,
            Map<String, byte[]> sources,
            PortableSigningIdentity signer,
            byte[] publicLineage) {
        this.revision = revision;
        this.versionCode = versionCode;
        this.sources = copySources(sources);
        this.signer = signer;
        this.publicLineage = publicLineage.clone();
    }

    static RecoveryBundle create(
            int revision,
            int versionCode,
            Map<String, byte[]> sources,
            PortableSigningIdentity signer,
            byte[] publicLineage)
            throws GeneralSecurityException {
        if (revision < 0
                || versionCode < 1
                || sources == null
                || sources.size() < 2
                || sources.size() > MAX_FILES
                || !sources.containsKey(JAVA)
                || !sources.containsKey(XML)
                || signer == null) {
            throw new GeneralSecurityException("Invalid recovery bundle metadata");
        }
        byte[] lineage = publicLineage == null ? new byte[0] : publicLineage;
        long total = 0;
        for (Map.Entry<String, byte[]> entry : sources.entrySet()) {
            requirePath(entry.getKey());
            requireSize(entry.getValue(), MAX_SOURCE_BYTES, false);
            total += entry.getValue().length;
            if (total > MAX_TOTAL_SOURCE_BYTES) {
                throw new GeneralSecurityException("Recovery snapshot too large");
            }
        }
        requireSize(lineage, MAX_LINEAGE_BYTES, false);
        validateLineage(signer, lineage);
        return new RecoveryBundle(revision, versionCode, sources, signer, lineage);
    }

    byte[] seal(char[] passphrase) throws GeneralSecurityException {
        byte[] key = signer.exportPkcs8();
        byte[] plaintext = null;
        WipingByteArrayOutputStream bytes = new WipingByteArrayOutputStream();
        try {
            byte[] certificate = signer.certificate().getEncoded();
            requireSize(key, MAX_IDENTITY_BYTES, true);
            requireSize(certificate, MAX_IDENTITY_BYTES, true);
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(MAGIC);
            out.writeByte(FORMAT_VERSION);
            out.writeInt(revision);
            out.writeInt(versionCode);
            field(out, ProjectSourceStore.PACKAGE_NAME.getBytes(StandardCharsets.UTF_8));
            out.writeInt(sources.size());
            for (Map.Entry<String, byte[]> source : new TreeMap<>(sources).entrySet()) {
                field(out, source.getKey().getBytes(StandardCharsets.UTF_8));
                field(out, source.getValue());
            }
            field(out, key);
            field(out, certificate);
            field(out, publicLineage);
            plaintext = bytes.toByteArray();
            return RecoveryEnvelope.seal(plaintext, passphrase);
        } catch (IOException impossible) {
            throw new GeneralSecurityException("Could not encode recovery bundle", impossible);
        } finally {
            Arrays.fill(key, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            bytes.erase();
        }
    }

    static RecoveryBundle open(byte[] sealed, char[] passphrase) throws GeneralSecurityException {
        byte[] plaintext = RecoveryEnvelope.open(sealed, passphrase);
        byte[] key = null;
        try {
            if (plaintext.length > MAX_PAYLOAD_BYTES) {
                throw new GeneralSecurityException("Recovery bundle too large");
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(plaintext));
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            int format = in.readUnsignedByte();
            if (!Arrays.equals(magic, MAGIC) || (format != 1 && format != FORMAT_VERSION)) {
                throw new GeneralSecurityException("Unsupported recovery bundle");
            }
            int revision = in.readInt();
            int versionCode = in.readInt();
            if (revision < 0 || versionCode < 1) {
                throw new GeneralSecurityException("Invalid recovery bundle metadata");
            }
            exactField(in, ProjectSourceStore.PACKAGE_NAME);
            Map<String, byte[]> sources = new HashMap<>();
            if (format == 1) {
                exactField(in, JAVA);
                sources.put(JAVA, readField(in, MAX_SOURCE_BYTES, true));
                exactField(in, XML);
                sources.put(XML, readField(in, MAX_SOURCE_BYTES, true));
            } else {
                int count = in.readInt();
                if (count < 2 || count > MAX_FILES) {
                    throw new GeneralSecurityException("Invalid recovery source count");
                }
                String previous = null;
                long total = 0;
                for (int i = 0; i < count; i++) {
                    String path =
                            new String(readField(in, MAX_PATH_BYTES, true), StandardCharsets.UTF_8);
                    requirePath(path);
                    if (previous != null && previous.compareTo(path) >= 0) {
                        throw new GeneralSecurityException("Recovery paths are not ordered");
                    }
                    byte[] content = readField(in, MAX_SOURCE_BYTES, false);
                    total += content.length;
                    if (total > MAX_TOTAL_SOURCE_BYTES) {
                        throw new GeneralSecurityException("Recovery snapshot too large");
                    }
                    sources.put(path, content);
                    previous = path;
                }
                if (!sources.containsKey(JAVA) || !sources.containsKey(XML)) {
                    throw new GeneralSecurityException("Recovery snapshot lacks required sources");
                }
            }
            key = readField(in, MAX_IDENTITY_BYTES, true);
            byte[] certificate = readField(in, MAX_IDENTITY_BYTES, true);
            byte[] lineage = readField(in, MAX_LINEAGE_BYTES, false);
            if (in.available() != 0) {
                throw new GeneralSecurityException("Trailing recovery bundle data");
            }
            PortableSigningIdentity signer = PortableSigningIdentity.restore(key, certificate);
            validateLineage(signer, lineage);
            return new RecoveryBundle(revision, versionCode, sources, signer, lineage);
        } catch (IOException malformed) {
            throw new GeneralSecurityException("Invalid recovery bundle", malformed);
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    String packageName() {
        return ProjectSourceStore.PACKAGE_NAME;
    }

    int revision() {
        return revision;
    }

    int versionCode() {
        return versionCode;
    }

    Map<String, byte[]> sources() {
        return copySources(sources);
    }

    PortableSigningIdentity signer() {
        return signer;
    }

    byte[] publicLineage() {
        return publicLineage.clone();
    }

    private static void field(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length);
        out.write(value);
    }

    private static void exactField(DataInputStream in, String expected)
            throws IOException, GeneralSecurityException {
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, readField(in, bytes.length, true))) {
            throw new GeneralSecurityException("Unexpected recovery bundle path or package");
        }
    }

    private static byte[] readField(DataInputStream in, int maximum, boolean required)
            throws IOException, GeneralSecurityException {
        int length = in.readInt();
        if (length < (required ? 1 : 0) || length > maximum || length > in.available()) {
            throw new GeneralSecurityException("Invalid recovery bundle field length");
        }
        byte[] value = new byte[length];
        in.readFully(value);
        return value;
    }

    private static void requireSize(byte[] value, int maximum, boolean required)
            throws GeneralSecurityException {
        if (value == null || value.length < (required ? 1 : 0) || value.length > maximum) {
            throw new GeneralSecurityException("Invalid recovery bundle field size");
        }
    }

    private static void requirePath(String path) throws GeneralSecurityException {
        if (path == null
                || path.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES
                || !(path.matches(
                                "java/dev/smallwright/generated/counter/[A-Za-z][A-Za-z0-9_]*\\.java")
                        || path.matches("res/(layout|values)/[a-z][a-z0-9_]*\\.xml"))) {
            throw new GeneralSecurityException("Invalid recovery source path");
        }
    }

    private static Map<String, byte[]> copySources(Map<String, byte[]> original) {
        Map<String, byte[]> copy = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : original.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    private static void validateLineage(PortableSigningIdentity signer, byte[] lineage)
            throws GeneralSecurityException {
        if (lineage.length == 0) return;
        try {
            if (!SigningCertificateLineage.readFromBytes(lineage)
                    .isCertificateLatestInLineage(signer.certificate())) {
                throw new GeneralSecurityException("Recovery lineage does not end with signer");
            }
        } catch (IOException | RuntimeException malformed) {
            throw new GeneralSecurityException("Invalid recovery signing lineage", malformed);
        }
    }

    private static final class WipingByteArrayOutputStream extends ByteArrayOutputStream {
        void erase() {
            Arrays.fill(buf, (byte) 0);
        }
    }
}
