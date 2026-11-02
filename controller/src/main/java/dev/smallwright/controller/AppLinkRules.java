package dev.smallwright.controller;

import dev.smallwright.project.ProjectStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * What the controller accepts from a generated app (ADR 0008). Pure, so every rule is host-tested;
 * the Android classes only gather the inputs.
 */
final class AppLinkRules {
    static final int MAX_PNG_BYTES = 6 * 1024 * 1024;
    static final int MAX_PNG_SIDE = 4096;
    static final int MAX_PNG_CHUNKS = 4096;
    static final String LOOK_PATH = "/look.png";
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };
    private static final String GENERATED_APP = "dev\\.smallwright\\.generated\\.app[0-9a-f]{32}";
    private static final String CLASS_NAME =
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*";

    /** The request cannot be met; the message tells the model why. */
    static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }

    private AppLinkRules() {}

    /**
     * True only when Android named the caller, it is this project's app, and the installed app has
     * exactly one signer: the project's current certificate. A package name alone is never enough.
     */
    static boolean acceptsSender(
            String callingPackage,
            String projectPackage,
            byte[][] installedSigners,
            byte[] projectCertificate) {
        return callingPackage != null
                && callingPackage.matches(GENERATED_APP)
                && callingPackage.equals(projectPackage)
                && installedSigners != null
                && installedSigners.length == 1
                && installedSigners[0] != null
                && projectCertificate != null
                && projectCertificate.length > 0
                && MessageDigest.isEqual(installedSigners[0], projectCertificate);
    }

    /** The authority of the app's look picture. */
    static String lookAuthority(String packageName) {
        return packageName + "." + ProjectStore.BUILDER_SUBPACKAGE;
    }

    /** True for exactly content://&lt;package&gt;.builderlink/look.png with no query. */
    static boolean isLookUri(
            String scheme, String authority, String path, String query, String packageName) {
        return "content".equals(scheme)
                && lookAuthority(packageName).equals(authority)
                && LOOK_PATH.equals(path)
                && query == null;
    }

    /** Reads at most {@link #MAX_PNG_BYTES}; more is refused. */
    static byte[] readBounded(InputStream input) throws IOException {
        if (input == null) throw new IOException("The app sent no picture");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (bytes.size() + read > MAX_PNG_BYTES)
                throw new IOException("The app's picture is too large");
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    /**
     * Width and height of a PNG whose signature, chunk lengths and checksums are sound, with IHDR
     * first and IEND last, each side 1 to {@link #MAX_PNG_SIDE} pixels. Nothing is decoded.
     */
    static int[] pngSize(byte[] png) throws IOException {
        if (png == null || png.length > MAX_PNG_BYTES || png.length < PNG_SIGNATURE.length + 25)
            throw new IOException("Not a PNG picture");
        for (int index = 0; index < PNG_SIGNATURE.length; index++)
            if (png[index] != PNG_SIGNATURE[index]) throw new IOException("Not a PNG picture");
        int offset = PNG_SIGNATURE.length;
        int width = 0;
        int height = 0;
        boolean first = true;
        for (int chunks = 1; ; chunks++) {
            if (chunks > MAX_PNG_CHUNKS)
                throw new IOException("The PNG picture has too many parts");
            if (png.length - offset < 12) throw new IOException("The PNG picture is cut short");
            long length = Integer.toUnsignedLong(intAt(png, offset));
            if (length > png.length - offset - 12L)
                throw new IOException("The PNG picture is cut short");
            String type = new String(png, offset + 4, 4, StandardCharsets.ISO_8859_1);
            CRC32 crc = new CRC32();
            crc.update(png, offset + 4, 4 + (int) length);
            if ((int) crc.getValue() != intAt(png, offset + 8 + (int) length))
                throw new IOException("The PNG picture is damaged");
            if (first) {
                if (!"IHDR".equals(type) || length != 13)
                    throw new IOException("The PNG picture has no header");
                width = intAt(png, offset + 8);
                height = intAt(png, offset + 12);
                if (width < 1 || height < 1 || width > MAX_PNG_SIDE || height > MAX_PNG_SIDE)
                    throw new IOException("The PNG picture has an unusable size");
                first = false;
            }
            offset += 12 + (int) length;
            if ("IEND".equals(type)) {
                if (offset != png.length) throw new IOException("The PNG picture has extra data");
                return new int[] {width, height};
            }
        }
    }

    /**
     * The full class name of the activity to open: the launcher when {@code requested} is null,
     * otherwise a declared activity named in full, from the package (.Name) or by its class name.
     */
    static String target(
            String requested, List<String> declared, String launcher, String packageName)
            throws Refused {
        List<String> own = new ArrayList<>();
        for (String name : declared) if (!isBuilders(name, packageName)) own.add(name);
        if (requested == null) {
            if (launcher == null || !own.contains(launcher))
                throw new Refused("The installed app has no launcher activity to open.");
            return launcher;
        }
        String wanted = requested.trim();
        List<String> found = new ArrayList<>();
        if (wanted.matches("\\.?" + CLASS_NAME)) {
            String full =
                    wanted.startsWith(".")
                            ? packageName + wanted
                            : wanted.indexOf('.') < 0 ? null : wanted;
            for (String name : own)
                if (full != null ? name.equals(full) : simpleName(name).equals(wanted))
                    found.add(name);
        }
        if (found.size() == 1) return found.get(0);
        StringBuilder names = new StringBuilder();
        for (String name : own) {
            if (names.length() > 0) names.append(", ");
            names.append(
                    name.startsWith(packageName + ".")
                            ? name.substring(packageName.length())
                            : name);
        }
        throw new Refused(
                (found.isEmpty()
                                ? "That is not an activity the installed app declares."
                                : "More than one declared activity has that name; give its full"
                                        + " name.")
                        + " Its activities: "
                        + names
                        + ".");
    }

    private static boolean isBuilders(String name, String packageName) {
        return name.startsWith(lookAuthority(packageName) + ".");
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static int intAt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }
}
