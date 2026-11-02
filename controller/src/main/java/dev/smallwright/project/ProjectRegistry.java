package dev.smallwright.project;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Bounded project catalog in the controller's private projects directory. */
public final class ProjectRegistry {
    public static final int MAX_PROJECTS = 32;
    private static final int MAGIC = 0x504c5052;
    private static final int FORMAT = 1;
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_RECORD_BYTES = 4 + 4 + 4 + 35 + 4 + MAX_TITLE_BYTES;
    private static final String LEGACY_ID = "counter-alpha";
    private static final String METADATA = "project.bin";
    private static final Object LOCK = new Object();
    private final Path root;
    private final Path trustedBase;

    public ProjectRegistry(File projectsDirectory) {
        this(projectsDirectory, null);
    }

    /** Android supplies its app-private directory to bound symlink checks below system paths. */
    public ProjectRegistry(File projectsDirectory, File trustedBase) {
        if (projectsDirectory == null)
            throw new IllegalArgumentException("Missing projects directory");
        root = projectsDirectory.toPath().toAbsolutePath().normalize();
        this.trustedBase =
                trustedBase == null ? null : trustedBase.toPath().toAbsolutePath().normalize();
        if (this.trustedBase != null
                && (!root.startsWith(this.trustedBase)
                        || !Files.isDirectory(this.trustedBase, LinkOption.NOFOLLOW_LINKS))) {
            throw new SecurityException("Project root is outside private storage");
        }
    }

    /** Registers the existing fixed project without moving its source or signer. */
    public ProjectIdentity ensureLegacyProject() throws IOException {
        synchronized (LOCK) {
            ensureRoot();
            Path directory = root.resolve(LEGACY_ID);
            Path metadata = directory.resolve(METADATA);
            if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)
                    && listRegistered().size() >= MAX_PROJECTS) {
                throw new IOException("Project limit reached");
            }
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                requireDirectory(directory);
            } else {
                Files.createDirectory(directory);
                syncDirectory(root);
            }
            if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
                writeMetadata(directory, LEGACY_ID, "Counter alpha");
            }
            return readProject(directory, LEGACY_ID);
        }
    }

    public ProjectIdentity create(String title) throws IOException {
        byte[] titleBytes = checkedTitle(title);
        synchronized (LOCK) {
            ensureRoot();
            int occupied = listRegistered().size();
            Path legacy = root.resolve(LEGACY_ID);
            if (Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(legacy.resolve(METADATA), LinkOption.NOFOLLOW_LINKS)) {
                occupied++;
            }
            if (occupied >= MAX_PROJECTS) throw new IOException("Project limit reached");
            String id;
            Path directory;
            do {
                id = "app" + UUID.randomUUID().toString().replace("-", "");
                directory = root.resolve(id);
            } while (Files.exists(directory, LinkOption.NOFOLLOW_LINKS));
            Files.createDirectory(directory);
            syncDirectory(root);
            writeMetadata(directory, id, new String(titleBytes, StandardCharsets.UTF_8));
            return readProject(directory, id);
        }
    }

    /**
     * Removes the project from the catalog. Its directory stays as the marker for unfinished
     * deletion until {@link #purge} removes it.
     */
    public void unpublish(String id) throws IOException {
        requireDeletable(id);
        synchronized (LOCK) {
            ensureRoot();
            Path directory = root.resolve(id);
            Path metadata = directory.resolve(METADATA);
            rejectSymlink(directory);
            rejectSymlink(metadata);
            if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Unknown project");
            }
            requireDirectory(directory);
            Files.delete(metadata);
            syncDirectory(directory);
        }
    }

    /** Deletes an unpublished project's directory; does nothing when it is already gone. */
    public void purge(String id) throws IOException {
        requireDeletable(id);
        synchronized (LOCK) {
            ensureRoot();
            Path directory = root.resolve(id);
            rejectSymlink(directory);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            requireDirectory(directory);
            Path metadata = directory.resolve(METADATA);
            rejectSymlink(metadata);
            if (Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Project is still published");
            }
            ProjectStore.deleteTree(directory);
            syncDirectory(root);
        }
    }

    /** Ids of interrupted creations or deletions, other than the recreated original project. */
    public List<String> unpublished() throws IOException {
        synchronized (LOCK) {
            ensureRoot();
            List<String> ids = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (LEGACY_ID.equals(name) || !name.matches("app[0-9a-f]{32}")) continue;
                    rejectSymlink(entry);
                    requireDirectory(entry);
                    Path metadata = entry.resolve(METADATA);
                    rejectSymlink(metadata);
                    if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) ids.add(name);
                }
            }
            ids.sort(null);
            return ids;
        }
    }

    public ProjectIdentity get(String id) throws IOException {
        validId(id);
        synchronized (LOCK) {
            ensureRoot();
            Path directory = root.resolve(id);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                    || !Files.exists(directory.resolve(METADATA), LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Unknown project");
            }
            return readProject(directory, id);
        }
    }

    private static void requireDeletable(String id) {
        validId(id);
        if (LEGACY_ID.equals(id)) {
            throw new IllegalArgumentException("The original project cannot be deleted");
        }
    }

    public List<ProjectIdentity> list() throws IOException {
        synchronized (LOCK) {
            ensureRoot();
            return List.copyOf(listRegistered());
        }
    }

    private List<ProjectIdentity> listRegistered() throws IOException {
        List<ProjectIdentity> projects = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                rejectSymlink(entry);
                String name = entry.getFileName().toString();
                if (name.matches("write--?[0-9]+\\.tmp")
                        && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    validId(name);
                } catch (IllegalArgumentException invalid) {
                    throw new SecurityException("Unexpected project entry", invalid);
                }
                requireDirectory(entry);
                Path metadata = entry.resolve(METADATA);
                rejectSymlink(metadata);
                // An interrupted creation has no published metadata and is not a project.
                if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) continue;
                projects.add(readProject(entry, name));
                if (projects.size() > MAX_PROJECTS) throw new IOException("Project limit exceeded");
            }
        }
        projects.sort(Comparator.comparing(ProjectIdentity::id));
        return projects;
    }

    private ProjectIdentity readProject(Path directory, String id) throws IOException {
        requireDirectory(directory);
        Path metadata = directory.resolve(METADATA);
        rejectSymlink(metadata);
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Project metadata is missing or invalid");
        }
        if (Files.size(metadata) > MAX_RECORD_BYTES) {
            throw new SecurityException("Project metadata is too large");
        }
        byte[] record = Files.readAllBytes(metadata);
        if (record.length > MAX_RECORD_BYTES)
            throw new SecurityException("Project metadata is too large");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(record))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT)
                throw new SecurityException("Invalid project metadata");
            int idLength = input.readInt();
            if (idLength != id.length()) throw new SecurityException("Project identity changed");
            byte[] encodedId = new byte[idLength];
            input.readFully(encodedId);
            if (!id.equals(new String(encodedId, StandardCharsets.US_ASCII))) {
                throw new SecurityException("Project identity changed");
            }
            int titleLength = input.readInt();
            if (titleLength < 1 || titleLength > MAX_TITLE_BYTES)
                throw new SecurityException("Invalid project title");
            byte[] encodedTitle = new byte[titleLength];
            input.readFully(encodedTitle);
            if (input.read() != -1) throw new SecurityException("Invalid project metadata");
            String title =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(encodedTitle))
                            .toString();
            checkedTitle(title);
            return new ProjectIdentity(id, title, directory.toFile());
        } catch (IOException | IllegalArgumentException failure) {
            throw new SecurityException("Invalid project metadata", failure);
        }
    }

    private static void writeMetadata(Path directory, String id, String title) throws IOException {
        byte[] encodedId = id.getBytes(StandardCharsets.US_ASCII);
        byte[] encodedTitle = checkedTitle(title);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(encodedId.length);
            output.write(encodedId);
            output.writeInt(encodedTitle.length);
            output.write(encodedTitle);
        }
        Path temporary = Files.createTempFile(directory, "write-", ".tmp");
        try {
            Files.write(temporary, bytes.toByteArray());
            syncFile(temporary);
            Files.move(temporary, directory.resolve(METADATA), StandardCopyOption.ATOMIC_MOVE);
            syncDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void ensureRoot() throws IOException {
        for (Path current = root; current != null; current = current.getParent()) {
            rejectSymlink(current);
            if (current.equals(trustedBase)) break;
        }
        Files.createDirectories(root);
        requireDirectory(root);
    }

    private static void requireDirectory(Path path) {
        rejectSymlink(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Project path is not a directory");
        }
    }

    private static void rejectSymlink(Path path) {
        if (Files.isSymbolicLink(path)) throw new SecurityException("Project path is a symlink");
    }

    private static void validId(String id) {
        if (id == null || !(LEGACY_ID.equals(id) || id.matches("app[0-9a-f]{32}"))) {
            throw new IllegalArgumentException("Invalid project ID");
        }
    }

    private static byte[] checkedTitle(String title) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Empty project title");
        byte[] bytes = title.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TITLE_BYTES
                || title.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid project title");
        }
        return bytes;
    }

    private static void syncFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
