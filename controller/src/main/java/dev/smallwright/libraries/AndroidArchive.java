package dev.smallwright.libraries;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What the controller reads from an Android library (AAR): only its manifest, for merging into the
 * app's. The rest of the archive is unpacked by the worker, never here.
 */
final class AndroidArchive {
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    static final int MAX_ENTRIES = 20_000;

    private AndroidArchive() {}

    /** The library's manifest text; refuses archives that are not Android libraries. */
    static String manifest(Library library, File aar) throws IOException {
        try (ZipFile zip = new ZipFile(aar)) {
            if (zip.size() > MAX_ENTRIES)
                throw new IOException(library + " holds more files than the builder accepts");
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if ("AndroidManifest.xml".equals(entry.getName())) {
                    if (manifest != null) throw new IOException(library + " holds two manifests");
                    manifest = entry;
                }
            }
            if (manifest == null)
                throw new IOException(library + " is not an Android library: it has no manifest");
            try (InputStream input = zip.getInputStream(manifest)) {
                return utf8(library, readAtMost(library, input));
            }
        } catch (java.util.zip.ZipException malformed) {
            throw new IOException(library + " is not a readable Android library");
        }
    }

    private static byte[] readAtMost(Library library, InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) != -1; ) {
            if (bytes.size() + read > MAX_MANIFEST_BYTES)
                throw new IOException(library + "'s manifest is larger than the builder accepts");
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static String utf8(Library library, byte[] bytes) throws IOException {
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes))
                            .toString();
            if (text.indexOf('\0') >= 0) throw new CharacterCodingException();
            return text;
        } catch (CharacterCodingException malformed) {
            throw new IOException(library + "'s manifest is not UTF-8 text");
        }
    }
}
