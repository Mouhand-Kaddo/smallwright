package dev.smallwright.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SnapshotStore {
    private static final int MAX_MANIFEST_BYTES = 64 * 1024;

    private SnapshotStore() {}

    static void write(Path revision, Path output, String manifest) throws IOException {
        write(revision, output, manifest, PatchApplier.LEGACY_PACKAGE);
    }

    static void write(Path revision, Path output, String manifest, String packageName)
            throws IOException {
        write(revision, output, manifest, packageName, Map.of());
    }

    /** As above, with the builder's own files added after the revision's (ADR 0008). */
    static void write(
            Path revision,
            Path output,
            String manifest,
            String packageName,
            Map<String, byte[]> builderFiles)
            throws IOException {
        for (Map.Entry<String, byte[]> file : builderFiles.entrySet())
            if (!PatchApplier.isBuilderPath(file.getKey(), packageName)
                    || !file.getKey().endsWith(".java")
                    || file.getValue() == null
                    || file.getValue().length > PatchApplier.MAX_FILE_BYTES)
                throw new SecurityException("Builder file is outside the builder's package");
        byte[] manifestBytes = manifest.getBytes(StandardCharsets.UTF_8);
        if (manifestBytes.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Snapshot manifest exceeds size limit");
        }
        PatchApplier.validateTree(revision, packageName);
        Path temporary = Files.createTempFile(output.getParent(), "snapshot-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
                zip.putNextEntry(new ZipEntry(PatchApplier.MANIFEST_PATH));
                zip.write(manifestBytes);
                zip.closeEntry();
                List<Path> files = new ArrayList<>();
                try (java.util.stream.Stream<Path> paths = Files.walk(revision)) {
                    paths.filter(Files::isRegularFile).sorted().forEach(files::add);
                }
                int built = builderFiles.size();
                for (Path file : files) {
                    String name = revision.relativize(file).toString().replace('\\', '/');
                    if (!PatchApplier.MANIFEST_PATH.equals(name)
                            && !dev.smallwright.libraries.LibraryList.PATH.equals(name)) built++;
                }
                if (built > PatchApplier.MAX_FILES)
                    throw new IOException(
                            "The project has too many files to build together with the builder's"
                                    + " own code. Delete one file and build again.");
                for (Path file : files) {
                    String name = revision.relativize(file).toString().replace('\\', '/');
                    PatchApplier.validatePath(name, packageName);
                    if (PatchApplier.MANIFEST_PATH.equals(name)
                            || dev.smallwright.libraries.LibraryList.PATH.equals(name)) continue;
                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream input = Files.newInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
                    }
                    zip.closeEntry();
                }
                for (Map.Entry<String, byte[]> file : new TreeMap<>(builderFiles).entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue());
                    zip.closeEntry();
                }
            }
            Files.move(
                    temporary,
                    output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
