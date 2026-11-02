package dev.smallwright.controller;

import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;

/** Two instrumentation invocations exercise durable adoption with a disposable real store. */
final class RecoveryAdoptionProbe {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";
    private static final byte[] JAVA_BYTES =
            ("package dev.smallwright.generated.counter;\n"
                            + "import android.app.Activity;\n"
                            + "import android.os.Bundle;\n"
                            + "import android.widget.TextView;\n"
                            + "public final class MainActivity extends Activity {\n"
                            + "  @Override public void onCreate(Bundle state) {\n"
                            + "    super.onCreate(state);\n"
                            + "    TextView label = new TextView(this);\n"
                            + "    label.setText(\"Recovered on device\");\n"
                            + "    setContentView(label);\n"
                            + "  }\n"
                            + "}\n")
                    .getBytes(StandardCharsets.UTF_8);
    private static final byte[] XML_BYTES =
            ("<View xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                            + "android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>\n")
                    .getBytes(StandardCharsets.UTF_8);
    private static final int EXPORTED_VERSION = 7;
    private static final int NEXT_VERSION = EXPORTED_VERSION + 1;

    private RecoveryAdoptionProbe() {}

    static String prepare(Context target) throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        File root = root(target, id);
        if (!root.mkdirs()) throw new IOException("Disposable recovery directory unavailable");
        ProjectSourceStore source = new ProjectSourceStore(root);
        source.initialize();
        if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
            throw new AssertionError("Disposable project did not start pristine");
        PortableSignerStore store = signerStore(root, id);
        PortableSigningIdentity identity = PortableSigningIdentity.create();
        char[] phrase = "disposable adoption phrase".toCharArray();
        byte[] sealed = null;
        try {
            RecoveryBundle original =
                    RecoveryBundle.create(3, EXPORTED_VERSION, expectedSources(), identity, null);
            sealed = original.seal(phrase);
            RecoveryBundle imported = RecoveryBundle.open(sealed, phrase);
            Files.write(
                    new File(root, "expected-certificate.sha256").toPath(),
                    sha256(identity.certificate().getEncoded()));

            RecoveryAdoption.Signer interruptAfterSave =
                    new StoreSigner(store) {
                        @Override
                        public void saveNew(PortableSigningIdentity value, byte[] lineage)
                                throws Exception {
                            super.saveNew(value, lineage);
                            throw new IOException(
                                    "Deliberate interruption after durable signer save");
                        }
                    };
            try {
                adoption(target, root, source, interruptAfterSave, id).adopt(imported);
                throw new AssertionError("Adoption did not stop after signer save");
            } catch (IOException expected) {
                if (!"Deliberate interruption after durable signer save"
                        .equals(expected.getMessage())) throw expected;
            }
            if (!new File(root, "adoption.marker").isFile()
                    || source.revision() != 0
                    || store.load() == null
                    || floor(target, id).nextVersion() != 1)
                throw new AssertionError("Interrupted import state was not durable");
            return id;
        } finally {
            Arrays.fill(phrase, '\0');
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
        }
    }

    static String verify(Context target, String id) throws Exception {
        requireId(id);
        File root = root(target, id);
        if (!new File(root, "adoption.marker").isFile())
            throw new AssertionError("Recovery marker missing after controller restart");
        ProjectSourceStore source = new ProjectSourceStore(root);
        PortableSignerStore store = signerStore(root, id);
        RecoveryAdoption adoption = adoption(target, root, source, new StoreSigner(store), id);
        if (source.revision() != 0 || floor(target, id).nextVersion() != 1)
            throw new AssertionError("Interrupted import advanced before restart");

        RecoveryBundle wrong =
                RecoveryBundle.create(
                        3,
                        EXPORTED_VERSION + 1,
                        expectedSources(),
                        PortableSigningIdentity.create(),
                        null);
        try {
            adoption.adopt(wrong);
            throw new AssertionError("Different bundle replaced pending import");
        } catch (SecurityException expected) {
            // The durable marker binds the original version, sources, and certificate.
        }
        if (source.revision() != 0 || floor(target, id).nextVersion() != 1)
            throw new AssertionError("Rejected bundle changed project or version floor");

        if (adoption.resume() != RecoveryAdoption.Status.COMPLETE
                || new File(root, "adoption.marker").exists())
            throw new AssertionError("Pending import did not complete");
        if (source.revision() != 1 || floor(target, id).nextVersion() != NEXT_VERSION)
            throw new AssertionError("Recovered revision or version floor differs");
        Map<String, byte[]> actual = source.readRecoverySources(1);
        Map<String, byte[]> expected = expectedSources();
        if (!actual.keySet().equals(expected.keySet()))
            throw new AssertionError("Recovered source paths differ");
        for (String path : expected.keySet()) {
            if (!Arrays.equals(expected.get(path), actual.get(path)))
                throw new AssertionError("Recovered source bytes differ");
        }
        PortableSignerStore.StoredIdentity stored = store.load();
        if (stored == null
                || stored.publicLineage().length != 0
                || !Arrays.equals(
                        Files.readAllBytes(new File(root, "expected-certificate.sha256").toPath()),
                        sha256(stored.signer().certificate().getEncoded())))
            throw new AssertionError("Recovered signer certificate differs");
        if (adoption.resume() != RecoveryAdoption.Status.COMPLETE
                || source.revision() != 1
                || floor(target, id).nextVersion() != NEXT_VERSION)
            throw new AssertionError("Repeated resume changed recovered state");

        verifyOrphanedKeyRecovery(target);

        return "Recovery adoption: restart, exact Java/XML, signer, version floor, wrong-bundle rejection, and orphaned key passed\n";
    }

    private static void verifyOrphanedKeyRecovery(Context target) throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        File root = root(target, id);
        if (!root.mkdirs())
            throw new IOException("Disposable orphan recovery directory unavailable");
        ProjectSourceStore source = new ProjectSourceStore(root);
        source.initialize();
        PortableSignerStore store = signerStore(root, id);
        PortableSigningIdentity identity = PortableSigningIdentity.create();
        RecoveryBundle bundle =
                RecoveryBundle.create(3, EXPORTED_VERSION, expectedSources(), identity, null);
        RecoveryAdoption.Signer interruptAfterKey =
                new StoreSigner(store) {
                    @Override
                    public void saveNew(PortableSigningIdentity value, byte[] lineage)
                            throws Exception {
                        KeyGenerator generator =
                                KeyGenerator.getInstance(
                                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                        generator.init(
                                new KeyGenParameterSpec.Builder(
                                                alias(id),
                                                KeyProperties.PURPOSE_ENCRYPT
                                                        | KeyProperties.PURPOSE_DECRYPT)
                                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                        .setEncryptionPaddings(
                                                KeyProperties.ENCRYPTION_PADDING_NONE)
                                        .setKeySize(256)
                                        .build());
                        generator.generateKey(); // Exact crash window: key exists before
                        // identity.bin.
                        throw new IOException(
                                "Deliberate interruption after wrapping key creation");
                    }
                };
        try {
            adoption(target, root, source, interruptAfterKey, id).adopt(bundle);
            throw new AssertionError("Orphan recovery did not stop after key creation");
        } catch (IOException expected) {
            if (!"Deliberate interruption after wrapping key creation"
                    .equals(expected.getMessage())) throw expected;
        }
        RecoveryAdoption restarted = adoption(target, root, source, new StoreSigner(store), id);
        if (restarted.resume() != RecoveryAdoption.Status.PENDING_REIMPORT
                || source.revision() != 0
                || floor(target, id).nextVersion() != 1)
            throw new AssertionError("Orphaned key did not await matching reimport");
        RecoveryBundle wrong =
                RecoveryBundle.create(
                        3,
                        EXPORTED_VERSION + 1,
                        expectedSources(),
                        PortableSigningIdentity.create(),
                        null);
        try {
            restarted.adopt(wrong);
            throw new AssertionError("Different bundle recovered orphaned key");
        } catch (SecurityException expected) {
            // The durable marker must bind the reimport to the original bundle.
        }
        if (restarted.adopt(bundle) != RecoveryAdoption.Status.COMPLETE
                || source.revision() != 1
                || floor(target, id).nextVersion() != NEXT_VERSION)
            throw new AssertionError("Matching reimport did not recover orphaned key");
        PortableSignerStore.StoredIdentity stored = store.load();
        if (stored == null
                || !Arrays.equals(
                        identity.certificate().getEncoded(),
                        stored.signer().certificate().getEncoded()))
            throw new AssertionError("Recovered orphaned signer differs");
    }

    private static RecoveryAdoption adoption(
            Context target,
            File root,
            ProjectSourceStore source,
            RecoveryAdoption.Signer signer,
            String id) {
        RecoveryAdoption.Project project =
                new RecoveryAdoption.Project() {
                    @Override
                    public int revision() throws Exception {
                        return source.revision();
                    }

                    @Override
                    public boolean isPristineDefaultSnapshot() throws Exception {
                        return source.isPristineDefaultSnapshot();
                    }

                    @Override
                    public int stageExactSnapshot(int expected, Map<String, byte[]> files)
                            throws Exception {
                        return source.stageRecoverySources(expected, files);
                    }

                    @Override
                    public byte[] validatedStagedSnapshotDigest(int expected, int staged)
                            throws Exception {
                        return source.validatedStagedRecoveryDigest(expected, staged);
                    }

                    @Override
                    public int promoteStagedSnapshot(int expected, int staged) throws Exception {
                        return source.promoteRecoverySources(expected, staged);
                    }
                };
        RecoveryAdoption.Preconditions preconditions =
                () -> {
                    try {
                        target.getPackageManager()
                                .getPackageInfo(ProjectSourceStore.PACKAGE_NAME, 0);
                        throw new SecurityException("Generated app is installed");
                    } catch (PackageManager.NameNotFoundException absent) {
                        // Dedicated emulator must have no installed generated app.
                    }
                };
        return new RecoveryAdoption(
                new File(root, "adoption.marker"),
                project,
                signer,
                (savedVersion, certificate, lineage) ->
                        RecoveryInstalledApp.nextVersionCode(
                                savedVersion, certificate, lineage, null, 0),
                preconditions,
                floor(target, id));
    }

    private static RecoveryAdoption.VersionFloor floor(Context target, String id) {
        android.content.SharedPreferences preferences =
                target.getSharedPreferences("recovery-adoption-probe-" + id, Context.MODE_PRIVATE);
        return new RecoveryAdoption.VersionFloor() {
            @Override
            public int nextVersion() {
                return preferences.getInt("next_version", 1);
            }

            @Override
            public void raiseToAtLeast(int next) {
                if (!preferences
                        .edit()
                        .putInt("next_version", Math.max(nextVersion(), next))
                        .commit())
                    throw new IllegalStateException("Probe version floor was not saved");
            }
        };
    }

    private static class StoreSigner implements RecoveryAdoption.Signer {
        final PortableSignerStore store;

        StoreSigner(PortableSignerStore store) {
            this.store = store;
        }

        @Override
        public RecoveryAdoption.StoredSigner load() throws Exception {
            PortableSignerStore.StoredIdentity identity = store.load();
            return identity == null
                    ? null
                    : new RecoveryAdoption.StoredSigner(
                            identity.signer().certificate().getEncoded(), identity.publicLineage());
        }

        @Override
        public void saveNew(PortableSigningIdentity identity, byte[] lineage) throws Exception {
            store.saveNew(identity, lineage);
        }

        @Override
        public void saveForPendingRecovery(PortableSigningIdentity identity, byte[] lineage)
                throws Exception {
            store.saveForPendingRecovery(identity, lineage);
        }
    }

    private static Map<String, byte[]> expectedSources() {
        return Map.of(JAVA, JAVA_BYTES.clone(), XML, XML_BYTES.clone());
    }

    private static File signerDirectory(File root) {
        return new File(root, "portable-identity");
    }

    private static String alias(String id) {
        return "dev.smallwright.controller.test.portable.adoption." + id;
    }

    private static PortableSignerStore signerStore(File root, String id) {
        return new PortableSignerStore(signerDirectory(root), alias(id));
    }

    private static File root(Context target, String id) {
        requireId(id);
        return new File(target.getNoBackupFilesDir(), "recovery-adoption-probe-" + id);
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Invalid disposable probe ID");
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}
