package dev.smallwright.controller;

import dev.smallwright.security.ContextFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The file side of {@code make_app_icon} and {@code make_image}: turns a checked PNG into the app's
 * launcher icon, or into a picture the app shows, in the run's working copy. The icon fills the
 * visible part of an Android adaptive icon, which the launcher then shapes, and the manifest is
 * pointed at it.
 */
final class AppIcons {
    static final String IMAGE_PATH = "res/drawable-nodpi/app_icon_image.png";
    static final String ICON_PATH = "res/mipmap-anydpi-v26/app_icon.xml";
    static final String ICON = "@mipmap/app_icon";
    static final int SIDE = 288;
    static final int PICTURE_MAX_SIDE = 1536;
    static final int PICTURE_MAX_BYTES = 1024 * 1024;

    private static final String PICTURE_NAME = "[a-z][a-z0-9_]{0,99}";
    private static final Set<String> ICON_NAMES = Set.of("app_icon", "app_icon_image");
    private static final Set<String> JAVA_WORDS =
            Set.of(
                    "abstract",
                    "assert",
                    "boolean",
                    "break",
                    "byte",
                    "case",
                    "catch",
                    "char",
                    "class",
                    "const",
                    "continue",
                    "default",
                    "do",
                    "double",
                    "else",
                    "enum",
                    "extends",
                    "false",
                    "final",
                    "finally",
                    "float",
                    "for",
                    "goto",
                    "if",
                    "implements",
                    "import",
                    "instanceof",
                    "int",
                    "interface",
                    "long",
                    "native",
                    "new",
                    "null",
                    "package",
                    "private",
                    "protected",
                    "public",
                    "return",
                    "short",
                    "static",
                    "strictfp",
                    "super",
                    "switch",
                    "synchronized",
                    "this",
                    "throw",
                    "throws",
                    "transient",
                    "true",
                    "try",
                    "void",
                    "volatile",
                    "while");

    private static final String ADAPTIVE_ICON =
            "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                    + "    <background android:drawable=\"@android:color/white\"/>\n"
                    + "    <foreground>\n"
                    + "        <inset android:drawable=\"@drawable/app_icon_image\""
                    + " android:inset=\"16.667%\"/>\n"
                    + "    </foreground>\n"
                    + "</adaptive-icon>\n";

    /** Decodes a PNG, JPEG or WebP and returns a PNG of its centred square, {@code side} pixels. */
    interface Square {
        byte[] png(byte[] image, int side) throws IOException;
    }

    /** A picture ready for the project: its file bytes and size in pixels. */
    static final class Picture {
        final byte[] bytes;
        final int width, height;

        Picture(byte[] bytes, int width, int height) {
            this.bytes = bytes;
            this.width = width;
            this.height = height;
        }
    }

    /** Decodes a PNG, JPEG or WebP and returns a WebP no larger than {@code maxSide} a side. */
    interface Fit {
        Picture picture(byte[] image, int maxSide) throws IOException;
    }

    static final AppIcons NONE = new AppIcons(null, null);

    private final Square square;
    private final Fit fit;

    AppIcons(Square square, Fit fit) {
        this.square = square;
        this.fit = fit;
    }

    boolean available() {
        return square != null && fit != null;
    }

    static String picturePath(String name) {
        return "res/drawable-nodpi/" + name + ".webp";
    }

    /** Why {@code name} cannot name a new picture in {@code files}, or null when it can. */
    static String pictureNameRefusal(String name, Map<String, byte[]> files) {
        if (!name.matches(PICTURE_NAME))
            return "Not made: the name must be a lower-case resource name of letters, digits and"
                    + " _, starting with a letter, such as beach_background.";
        if (JAVA_WORDS.contains(name))
            return "Not made: " + name + " is a Java word and cannot name a resource.";
        if (ICON_NAMES.contains(name))
            return "Not made: " + name + " is the app icon's. Pick another name.";
        String path = picturePath(name);
        for (String existing : files.keySet())
            if (!existing.equals(path)
                    && existing.matches("res/drawable(?:-[^/]+)?/" + name + "(?:\\.[^/.]+)+"))
                return "Not made: the drawable "
                        + name
                        + " already exists as "
                        + existing
                        + ". Pick another name, or delete that file first.";
        return null;
    }

    /** What the image model is asked for, around the builder's description of a picture. */
    static String picturePrompt(String description) {
        return "A picture for an Android app: "
                + ContextFilter.redact(description.trim())
                + " Fill the whole frame edge to edge, with no border, frame or watermark, and no"
                + " words or letters unless the description asks for them.";
    }

    /** Writes the picture as a drawable; returns the tool result for the model. */
    String writePicture(String name, byte[] image, WorkingCopy files) throws IOException {
        Picture picture = fit.picture(image, PICTURE_MAX_SIDE);
        if (picture.bytes.length > PICTURE_MAX_BYTES)
            throw new IOException(
                    "The picture is too large for a project file ("
                            + picture.bytes.length / 1024
                            + " KB, at most "
                            + PICTURE_MAX_BYTES / 1024
                            + " KB).");
        String path = picturePath(name);
        files.write(path, picture.bytes);
        return "Made the picture "
                + path
                + " ("
                + picture.width
                + "×"
                + picture.height
                + " pixels): use @drawable/"
                + name
                + " in layouts and R.drawable."
                + name
                + " in Java. It is saved when you build.";
    }

    /** What the image model is asked for, around the builder's description of the picture. */
    static String prompt(String description) {
        return "An app icon for an Android phone: "
                + ContextFilter.redact(description.trim())
                + " One clear subject in the centre, simple bold shapes that read at a small"
                + " size, filling the whole square edge to edge. Square, no text, no letters, no"
                + " border, no rounded corners, no shadow around it.";
    }

    /** Writes the icon and points the manifest at it; returns the tool result for the model. */
    String write(byte[] image, WorkingCopy files) throws IOException {
        Map<String, byte[]> writes = new TreeMap<>();
        writes.put(IMAGE_PATH, square.png(image, SIDE));
        writes.put(ICON_PATH, ADAPTIVE_ICON.getBytes(StandardCharsets.UTF_8));
        byte[] manifest = files.read(AppManifestPolicy.PATH);
        String manifestRefusal = null;
        try {
            String pointed =
                    AppManifestPolicy.withIcon(
                            manifest == null ? "" : new String(manifest, StandardCharsets.UTF_8),
                            ICON);
            writes.put(AppManifestPolicy.PATH, pointed.getBytes(StandardCharsets.UTF_8));
        } catch (AppManifestPolicy.Refused refused) {
            manifestRefusal = refused.getMessage();
        }
        files.write(writes);
        String made =
                "Made the app icon: "
                        + IMAGE_PATH
                        + " ("
                        + SIDE
                        + "×"
                        + SIDE
                        + " pixels) and "
                        + ICON_PATH
                        + ". ";
        if (manifestRefusal != null)
            return made
                    + "AndroidManifest.xml could not be changed: "
                    + manifestRefusal
                    + " Fix it, then set android:icon=\""
                    + ICON
                    + "\" on <application>. Build to see it, then tell the person.";
        return made
                + "AndroidManifest.xml now has android:icon=\""
                + ICON
                + "\" on <application>. Build to see it on the phone. Tell the person"
                + " the app has a new icon; they can ask for another.";
    }
}
