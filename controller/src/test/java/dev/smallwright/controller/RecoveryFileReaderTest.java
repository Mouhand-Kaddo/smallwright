package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public final class RecoveryFileReaderTest {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";
    private static final String EXTRA = "java/dev/smallwright/generated/counter/Extra.java";
    private static final char[] PHRASE = "disposable import phrase 42".toCharArray();

    @Test
    public void readsCompleteEncryptedExportWithExactExtraSource() throws Exception {
        PortableSigningIdentity signer = PortableSigningIdentity.create();
        byte[] unusual = {(byte) 0xc3, (byte) 0x28};
        byte[] sealed =
                RecoveryBundle.create(
                                3,
                                8,
                                Map.of(JAVA, new byte[] {1}, XML, new byte[] {2}, EXTRA, unusual),
                                signer,
                                null)
                        .seal(PHRASE);

        RecoveryBundle opened = RecoveryFileReader.open(new ByteArrayInputStream(sealed), PHRASE);

        assertEquals(3, opened.revision());
        assertEquals(8, opened.versionCode());
        assertEquals(3, opened.sources().size());
        assertArrayEquals(unusual, opened.sources().get(EXTRA));
        assertArrayEquals(
                signer.certificate().getEncoded(), opened.signer().certificate().getEncoded());
        assertThrows(
                GeneralSecurityException.class,
                () ->
                        RecoveryFileReader.open(
                                new ByteArrayInputStream(Arrays.copyOf(sealed, sealed.length - 1)),
                                PHRASE));
    }

    @Test
    public void refusesOversizedDocumentWithoutUnboundedRead() {
        InputStream oversized =
                new InputStream() {
                    int remaining = 4 * 1024 * 1024;

                    @Override
                    public int read() {
                        return remaining-- > 0 ? 0 : -1;
                    }

                    @Override
                    public int read(byte[] target, int offset, int length) {
                        if (remaining == 0) return -1;
                        int count = Math.min(length, remaining);
                        Arrays.fill(target, offset, offset + count, (byte) 0);
                        remaining -= count;
                        return count;
                    }
                };
        assertThrows(IOException.class, () -> RecoveryFileReader.open(oversized, PHRASE));
    }
}
