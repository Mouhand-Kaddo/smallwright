package dev.smallwright.controller;

import android.content.Context;
import android.content.ContextWrapper;
import android.util.AtomicFile;
import dev.smallwright.project.ProjectStore;
import java.io.File;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Disposable runtime check of the product recovery preparation boundary. */
final class RecoveryExportProbe {
    private static final String EXTRA = "java/dev/smallwright/generated/counter/Extra.java";

    static String run(Context controller) throws Exception {
        String nonce = UUID.randomUUID().toString();
        File root = new File(controller.getNoBackupFilesDir(), "recovery-export-probe-" + nonce);
        File cache = new File(root, "cache");
        if (!cache.mkdirs()) throw new AssertionError("Probe directory unavailable");
        Context isolated =
                new ContextWrapper(controller) {
                    @Override
                    public File getFilesDir() {
                        return root;
                    }

                    @Override
                    public File getCacheDir() {
                        return cache;
                    }

                    @Override
                    public File getNoBackupFilesDir() {
                        return root;
                    }
                };
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        ProjectStore raw =
                new ProjectStore(new File(root, "projects/counter-alpha/revision-store"));
        byte[] unusual = {(byte) 0xc3, (byte) 0x28};
        int revision = raw.applyPatch(0, Map.of(EXTRA, unusual));
        String oldAlias = "rotation-apk-old-recovery-export-" + nonce;
        String wrapAlias = "dev.smallwright.controller.test.portable.recovery." + nonce;
        File identityDirectory = new File(root, "portable-identity");
        PortableSignerStore store = new PortableSignerStore(identityDirectory, wrapAlias);
        ProjectSigner signer = new ProjectSigner(oldAlias, store);
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        char[] invalid = {'s', 'h', 'o', 'r', 't'};
        char[] phrase = "disposable recovery export phrase".toCharArray();
        byte[] sealed = null;
        byte[] second = null;
        try {
            try {
                RecoveryExport.prepare(isolated, source, signer, store, revision, 4, invalid);
                throw new AssertionError("Short recovery phrase accepted");
            } catch (IllegalArgumentException expected) {
                if (keys.containsAlias(oldAlias) || keys.containsAlias(wrapAlias)) {
                    throw new AssertionError("Invalid phrase activated the signer");
                }
            }
            sealed = RecoveryExport.prepare(isolated, source, signer, store, revision, 4, phrase);
            RecoveryBundle opened = RecoveryBundle.open(sealed, phrase);
            if (opened.revision() != revision
                    || opened.versionCode() != 4
                    || opened.sources().size() != 3
                    || !Arrays.equals(unusual, opened.sources().get(EXTRA))
                    || opened.publicLineage().length == 0
                    || store.load() == null) {
                throw new AssertionError("Recovery export lost project or signer state");
            }
            second = RecoveryExport.prepare(isolated, source, signer, store, revision, 4, phrase);
            RecoveryBundle reopened = RecoveryBundle.open(second, phrase);
            if (!Arrays.equals(
                    opened.signer().certificate().getEncoded(),
                    reopened.signer().certificate().getEncoded())) {
                throw new AssertionError("Repeated export changed the signer");
            }
            return "Recovery export: exact three-file snapshot and durable portable signer sealed twice\n";
        } finally {
            Arrays.fill(invalid, '\0');
            Arrays.fill(phrase, '\0');
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
            if (second != null) Arrays.fill(second, (byte) 0);
            for (String alias : new String[] {oldAlias, wrapAlias}) {
                if (keys.containsAlias(alias)) keys.deleteEntry(alias);
            }
            new AtomicFile(new File(identityDirectory, "identity.bin")).delete();
        }
    }
}
