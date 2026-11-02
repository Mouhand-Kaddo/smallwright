package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** An in-memory, allowlisted copy of one committed Java/XML source revision. */
final class SourceExport {
    private static final String XML = "res/layout/empty.xml";
    private static final String MANIFEST = "AndroidManifest.xml";
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private final int revision;
    private final String packageName;
    private final byte[] archive;
    private final String preview;

    private SourceExport(int revision, String packageName, byte[] archive, String preview) {
        this.revision = revision;
        this.packageName = packageName;
        this.archive = archive;
        this.preview = preview;
    }

    static SourceExport prepare(int revision, Map<String, String> files, String manifest)
            throws IOException {
        return prepare(revision, files, manifest, ProjectSourceStore.PACKAGE_NAME);
    }

    static SourceExport prepare(
            int revision, Map<String, String> files, String manifest, String packageName)
            throws IOException {
        String javaPath;
        try {
            String expectedManifest = ProjectSourceStore.exportManifest(packageName);
            if (!expectedManifest.equals(manifest))
                throw new SecurityException("Source export manifest belongs to another project");
            javaPath = "java/" + packageName.replace('.', '/') + "/MainActivity.java";
        } catch (IllegalArgumentException invalid) {
            throw new SecurityException("Invalid source export project", invalid);
        }
        if (revision < 0 || files == null || !files.keySet().equals(Set.of(javaPath, XML))) {
            throw new SecurityException("Source export requires the two known project files");
        }
        byte[] java = bytes(files.get(javaPath));
        byte[] xml = bytes(files.get(XML));
        byte[] manifestBytes = bytes(manifest);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            add(zip, MANIFEST, manifestBytes);
            add(zip, javaPath, java);
            add(zip, XML, xml);
        }
        String preview =
                "Committed revision "
                        + revision
                        + "\n\n"
                        + "src/main/"
                        + MANIFEST
                        + " ("
                        + manifestBytes.length
                        + " bytes; build version set when compiling)\n"
                        + "src/main/"
                        + javaPath
                        + " ("
                        + java.length
                        + " bytes)\n"
                        + "src/main/"
                        + XML
                        + " ("
                        + xml.length
                        + " bytes)\n\n"
                        + "The ZIP contains these three source files only. It excludes project history, "
                        + "controller-stored credentials, signing keys, and APK. "
                        + "Cancel saves nothing.";
        return new SourceExport(revision, packageName, buffer.toByteArray(), preview);
    }

    private static byte[] bytes(String value) {
        if (value == null) throw new SecurityException("Missing project source");
        byte[] result = value.getBytes(StandardCharsets.UTF_8);
        if (result.length > MAX_FILE_BYTES)
            throw new SecurityException("Project source exceeds export limit");
        return result;
    }

    private static void add(ZipOutputStream zip, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry("src/main/" + path);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    int revision() {
        return revision;
    }

    String packageName() {
        return packageName;
    }

    String preview() {
        return preview;
    }

    byte[] bytes() {
        return archive.clone();
    }
}
