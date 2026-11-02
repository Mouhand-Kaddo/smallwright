package dev.smallwright.controller;

import android.content.Context;
import java.util.Arrays;
import java.util.Map;

/** Prepares a user-requested encrypted project and signer backup in memory. */
final class RecoveryExport {
    private RecoveryExport() {}

    static byte[] prepare(
            Context context,
            ProjectSourceStore source,
            ProjectSigner signer,
            PortableSignerStore portableStore,
            int expectedRevision,
            int lastVersion,
            char[] passphrase)
            throws Exception {
        if (passphrase == null || passphrase.length < 12 || passphrase.length > 1024) {
            throw new IllegalArgumentException("Recovery passphrase must be 12-1024 characters");
        }
        if (lastVersion < 1) throw new IllegalArgumentException("Invalid recovery version");
        Map<String, byte[]> snapshot = source.readRecoverySources(expectedRevision);
        try {
            PortableSignerStore.StoredIdentity stored = portableStore.load();
            if (stored == null) {
                signer.activatePortable(context);
                stored = portableStore.load();
            }
            if (stored == null) throw new IllegalStateException("Portable signer unavailable");
            byte[] sealed =
                    RecoveryBundle.create(
                                    expectedRevision,
                                    lastVersion,
                                    snapshot,
                                    stored.signer(),
                                    stored.publicLineage())
                            .seal(passphrase);
            if (source.revision() != expectedRevision) {
                Arrays.fill(sealed, (byte) 0);
                throw new IllegalStateException("Project changed during recovery export");
            }
            return sealed;
        } finally {
            for (byte[] bytes : snapshot.values()) Arrays.fill(bytes, (byte) 0);
        }
    }
}
