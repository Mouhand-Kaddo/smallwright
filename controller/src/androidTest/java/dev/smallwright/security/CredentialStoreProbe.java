package dev.smallwright.security;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Disposable emulator probe; uses the controller UID and dummy data. */
public final class CredentialStoreProbe {
    private CredentialStoreProbe() {}

    public static void run(Context targetContext) throws Exception {
        byte[] dummy = "dummy-emulator-credential".getBytes(StandardCharsets.UTF_8);
        CredentialStore store = CredentialStore.openRouter(targetContext);
        File ciphertext =
                new File(targetContext.getNoBackupFilesDir(), "credentials/model-credential.bin");
        if (ciphertext.exists()) throw new AssertionError("Test credential file already exists");
        try {
            store.save(dummy);
            if (!ciphertext.isFile()) throw new AssertionError("Ciphertext file missing");
            byte[] loaded = store.load();
            try {
                if (!Arrays.equals(dummy, loaded))
                    throw new AssertionError("Credential round-trip failed");
            } finally {
                if (loaded != null) Arrays.fill(loaded, (byte) 0);
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(ciphertext.toPath());
            if (new String(bytes, StandardCharsets.ISO_8859_1)
                    .contains("dummy-emulator-credential")) {
                throw new AssertionError("Plaintext found in credential file");
            }
        } finally {
            Arrays.fill(dummy, (byte) 0);
            store.clear();
        }
        if (store.load() != null) throw new AssertionError("Credential remained after clear");
        CredentialStore server = CredentialStore.server(targetContext);
        if (server.exists()) throw new AssertionError("Test server credential already exists");
        byte[] serverDummy = "dummy-emulator-server-credential".getBytes(StandardCharsets.UTF_8);
        try {
            server.save(serverDummy);
            if (store.exists())
                throw new AssertionError("Server key landed in the OpenRouter slot");
            byte[] loaded = server.load();
            try {
                if (!Arrays.equals(serverDummy, loaded))
                    throw new AssertionError("Server credential round-trip failed");
            } finally {
                if (loaded != null) Arrays.fill(loaded, (byte) 0);
            }
        } finally {
            Arrays.fill(serverDummy, (byte) 0);
            server.clear();
        }
        if (server.exists()) throw new AssertionError("Server credential remained after clear");
    }
}
