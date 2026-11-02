package dev.smallwright.project;

import dev.smallwright.libraries.LibraryList;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.regex.Pattern;

final class PatchApplier {
    static final String LEGACY_PACKAGE = "dev.smallwright.generated.counter";
    static final String MANIFEST_PATH = "AndroidManifest.xml";
    static final int MAX_FILE_BYTES = 1024 * 1024;
    static final int MAX_CONTENT_BYTES = 16 * 1024 * 1024;
    static final int MAX_FILES = 500;
    private static final String QUALIFIERS = "(?:-[A-Za-z0-9+]{1,32}){0,8}";
    private static final String RESOURCE_NAME = "[a-z][a-z0-9_]{0,99}";
    private static final Pattern RESOURCE_PATH =
            Pattern.compile(
                    "res/(?:(?:anim|animator|color|interpolator|layout|menu|navigation|transition"
                            + "|values|xml)"
                            + QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "\\.xml"
                            + "|(?:drawable|mipmap)"
                            + QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "(?:\\.(?:xml|jpg|jpeg|webp|gif)|(?:\\.9)?\\.png)"
                            + "|font"
                            + QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "\\.(?:ttf|otf|ttc|xml)"
                            + "|raw"
                            + QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "(?:\\.[a-z0-9]{1,16}){0,2})");
    private static final Pattern ASSET_PATH =
            Pattern.compile("assets(?:/[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}){1,6}");

    private PatchApplier() {}

    static void validatePath(String name) {
        validatePath(name, LEGACY_PACKAGE);
    }

    static void validatePath(String name, String packageName) {
        if (name == null
                || !(MANIFEST_PATH.equals(name)
                        || LibraryList.PATH.equals(name)
                        || ASSET_PATH.matcher(name).matches()
                        || RESOURCE_PATH.matcher(name).matches()
                        || isJavaPath(name, packageName))) {
            throw new SecurityException("Project path is not editable");
        }
    }

    private static boolean isJavaPath(String name, String packageName) {
        String javaDirectory = "java/" + packageName.replace('.', '/') + "/";
        return name.matches(
                        Pattern.quote(javaDirectory)
                                + "(?:[a-z][a-z0-9_]*/){0,6}[A-Za-z][A-Za-z0-9_]*\\.java")
                && !isBuilderPath(name, packageName);
    }

    /** A path in the sub-package whose code the builder adds to every build. */
    static boolean isBuilderPath(String name, String packageName) {
        return name.startsWith(
                "java/"
                        + packageName.replace('.', '/')
                        + "/"
                        + ProjectStore.BUILDER_SUBPACKAGE
                        + "/");
    }

    static void writeFiles(Path directory, Map<String, byte[]> files) throws IOException {
        writeFiles(directory, files, LEGACY_PACKAGE);
    }

    static void writeFiles(Path directory, Map<String, byte[]> files, String packageName)
            throws IOException {
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            validatePath(entry.getKey(), packageName);
            if (entry.getValue() == null || entry.getValue().length > MAX_FILE_BYTES) {
                throw new IOException("Project file exceeds size limit");
            }
            Path destination = directory.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.write(destination, entry.getValue());
        }
    }

    static void validateTree(Path directory) throws IOException {
        validateTree(directory, LEGACY_PACKAGE);
    }

    static void validateTree(Path directory, String packageName) throws IOException {
        final long[] totalBytes = {0};
        final int[] fileCount = {0};
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (attrs.isSymbolicLink() || !attrs.isRegularFile()) {
                            throw new SecurityException(
                                    "Project revision contains an unsupported file");
                        }
                        validatePath(
                                directory.relativize(file).toString().replace('\\', '/'),
                                packageName);
                        totalBytes[0] += attrs.size();
                        if (attrs.size() > MAX_FILE_BYTES
                                || totalBytes[0] > MAX_CONTENT_BYTES
                                || ++fileCount[0] > MAX_FILES) {
                            throw new IOException("Project source exceeds snapshot limit");
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
