package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Bounded reader for a user-selected encrypted recovery document. */
final class RecoveryFileReader {
    private static final int MAX_FILE_BYTES = 512 * 1024;

    private RecoveryFileReader() {}

    static RecoveryBundle open(InputStream input, char[] passphrase)
            throws IOException, GeneralSecurityException {
        if (input == null) throw new IllegalArgumentException("Missing recovery document");
        WipingByteArrayOutputStream output = new WipingByteArrayOutputStream();
        byte[] sealed = null;
        byte[] buffer = new byte[4096];
        try {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) {
                    int single = input.read();
                    if (single == -1) break;
                    if (output.size() == MAX_FILE_BYTES)
                        throw new IOException("Recovery document exceeds size limit");
                    output.write(single);
                    continue;
                }
                if (count > MAX_FILE_BYTES - output.size()) {
                    throw new IOException("Recovery document exceeds size limit");
                }
                output.write(buffer, 0, count);
            }
            sealed = output.toByteArray();
            return RecoveryBundle.open(sealed, passphrase);
        } finally {
            Arrays.fill(buffer, (byte) 0);
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
            output.erase();
        }
    }

    private static final class WipingByteArrayOutputStream extends ByteArrayOutputStream {
        void erase() {
            Arrays.fill(buf, (byte) 0);
        }
    }
}
