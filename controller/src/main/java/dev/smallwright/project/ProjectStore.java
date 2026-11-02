package dev.smallwright.project;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

public final class ProjectStore {
    /** The most bytes one project file may hold; the worker checks each snapshot file the same. */
    public static final int MAX_FILE_BYTES = PatchApplier.MAX_FILE_BYTES;

    /** The most bytes all of a project's files may hold together. */
    public static final int MAX_PROJECT_BYTES = PatchApplier.MAX_CONTENT_BYTES;

    public static final int MAX_PROJECT_FILES = PatchApplier.MAX_FILES;

    /** The sub-package of the app's package that holds the builder's own code; never editable. */
    public static final String BUILDER_SUBPACKAGE = "builderlink";

    private static final String REQUIRED_LAYOUT = "res/layout/empty.xml";
    private static final int MAX_STAGE_RECORD_BYTES = 76;
    private static final ReentrantLock ACTIVE = new ReentrantLock();
    private final Path root;
    private final String packageName;
    private final String requiredMain;
    private final ReentrantLock active = ACTIVE;

    public ProjectStore(File root) {
        this(root, PatchApplier.LEGACY_PACKAGE);
    }

    public ProjectStore(File root, String packageName) {
        if (packageName == null
                || !packageName.matches(
                        "dev\\.smallwright\\.generated\\.(counter|app[0-9a-f]{32})")) {
            throw new IllegalArgumentException("Invalid generated package");
        }
        this.root = root.toPath();
        this.packageName = packageName;
        this.requiredMain = "java/" + packageName.replace('.', '/') + "/MainActivity.java";
    }

    public void initialize(Map<String, byte[]> files) throws IOException {
        enter();
        try {
            rejectSymlinks(root);
            Files.createDirectories(root.resolve("revisions"));
            if (Files.exists(root.resolve("HEAD"))) return;
            Path stage = Files.createTempDirectory(root, "stage-");
            try {
                PatchApplier.writeFiles(stage, files, packageName);
                PatchApplier.validateTree(stage, packageName);
                publish(stage, 0);
            } finally {
                if (Files.exists(stage)) deleteTree(stage);
            }
        } finally {
            active.unlock();
        }
    }

    /** Whether the path policy lets a project hold this path. */
    public static boolean isEditablePath(String path, String packageName) {
        try {
            PatchApplier.validatePath(path, packageName);
            return true;
        } catch (SecurityException refused) {
            return false;
        }
    }

    public boolean isInitialized() {
        enter();
        try {
            rejectSymlinks(root.resolve("HEAD"));
            return Files.isRegularFile(root.resolve("HEAD"), LinkOption.NOFOLLOW_LINKS);
        } finally {
            active.unlock();
        }
    }

    public int revision() throws IOException {
        enter();
        try {
            return currentRevision();
        } finally {
            active.unlock();
        }
    }

    public byte[] readFile(String path) throws IOException {
        enter();
        try {
            PatchApplier.validatePath(path, packageName);
            Path file = revisionPath(currentRevision()).resolve(path);
            rejectSymlinks(file);
            return Files.readAllBytes(file);
        } finally {
            active.unlock();
        }
    }

    public byte[] readFile(int revision, String path) throws IOException {
        enter();
        try {
            PatchApplier.validatePath(path, packageName);
            Path file = revisionPath(revision).resolve(path);
            rejectSymlinks(file);
            return Files.readAllBytes(file);
        } finally {
            active.unlock();
        }
    }

    public Map<String, String> readCurrentSourceFiles(int expectedRevision) throws IOException {
        Map<String, String> decoded = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : readCurrentSourceBytes(expectedRevision).entrySet()) {
            decoded.put(file.getKey(), new String(file.getValue(), StandardCharsets.UTF_8));
        }
        return decoded;
    }

    /** A complete, exact-byte source snapshot from one validated committed revision. */
    public Map<String, byte[]> readCurrentSourceBytes(int expectedRevision) throws IOException {
        enter();
        try {
            int current = currentRevision();
            if (current != expectedRevision)
                throw new IllegalStateException("Stale project revision");
            return revisionBytes(current);
        } finally {
            active.unlock();
        }
    }

    /** The exact bytes of a revision committed at or before the current one. */
    public Map<String, byte[]> readRevisionBytes(int revision) throws IOException {
        enter();
        try {
            if (revision < 0 || revision > currentRevision())
                throw new IllegalArgumentException("Not a committed revision");
            return revisionBytes(revision);
        } finally {
            active.unlock();
        }
    }

    private Map<String, byte[]> revisionBytes(int number) throws IOException {
        Path revision = revisionPath(number);
        rejectSymlinks(revision);
        PatchApplier.validateTree(revision, packageName);
        Map<String, byte[]> files = new TreeMap<>();
        Files.walkFileTree(
                revision,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        rejectSymlinks(file);
                        if (!attrs.isRegularFile()) {
                            throw new SecurityException(
                                    "Project revision contains an unsupported file");
                        }
                        String path = revision.relativize(file).toString().replace('\\', '/');
                        PatchApplier.validatePath(path, packageName);
                        files.put(path, Files.readAllBytes(file));
                        return FileVisitResult.CONTINUE;
                    }
                });
        return files;
    }

    public int applyPatch(int expectedRevision, Map<String, byte[]> changes) throws IOException {
        return applyPatch(expectedRevision, changes, java.util.Set.of());
    }

    /** Writes and deletes files as one new revision, refused unless HEAD is expectedRevision. */
    public int applyPatch(
            int expectedRevision, Map<String, byte[]> changes, java.util.Set<String> deletions)
            throws IOException {
        enter();
        try {
            int current = currentRevision();
            if (current != expectedRevision)
                throw new IllegalStateException("Stale project revision");
            if (current == Integer.MAX_VALUE) throw new IOException("No revision number available");
            Path stage = Files.createTempDirectory(root, "stage-");
            try {
                copyTree(revisionPath(current), stage);
                for (String deleted : deletions) {
                    PatchApplier.validatePath(deleted, packageName);
                    Files.deleteIfExists(stage.resolve(deleted));
                }
                PatchApplier.writeFiles(stage, changes, packageName);
                PatchApplier.validateTree(stage, packageName);
                int next = nextRevision(current);
                publish(stage, next);
                return next;
            } finally {
                if (Files.exists(stage)) deleteTree(stage);
            }
        } finally {
            active.unlock();
        }
    }

    /**
     * Commits a new revision whose files equal an earlier revision's, refused unless HEAD is
     * expectedRevision. Every revision in between is kept.
     */
    public int restore(int expectedRevision, int earlierRevision) throws IOException {
        enter();
        try {
            int current = currentRevision();
            if (current != expectedRevision)
                throw new IllegalStateException("Stale project revision");
            if (earlierRevision < 0 || earlierRevision >= current)
                throw new IllegalArgumentException("Not an earlier revision");
            Path earlier = revisionPath(earlierRevision);
            rejectSymlinks(earlier);
            PatchApplier.validateTree(earlier, packageName);
            Path stage = Files.createTempDirectory(root, "stage-");
            try {
                copyTree(earlier, stage);
                PatchApplier.validateTree(stage, packageName);
                int next = nextRevision(current);
                publish(stage, next);
                return next;
            } finally {
                if (Files.exists(stage)) deleteTree(stage);
            }
        } finally {
            active.unlock();
        }
    }

    private int nextRevision(int current) throws IOException {
        if (current == Integer.MAX_VALUE) throw new IOException("No revision number available");
        int next = current + 1;
        while (Files.exists(revisionPath(next), LinkOption.NOFOLLOW_LINKS)) {
            if (next == Integer.MAX_VALUE) throw new IOException("No revision number available");
            next++;
        }
        return next;
    }

    /** Reserves a complete source revision for recovery without moving HEAD. */
    public int stageExactSnapshot(int expectedRevision, Map<String, byte[]> files)
            throws IOException {
        enter();
        try {
            int current = currentRevision();
            if (current != expectedRevision)
                throw new IllegalStateException("Stale project revision");
            rejectSymlinks(revisionPath(current));
            PatchApplier.validateTree(revisionPath(current), packageName);
            if (current == Integer.MAX_VALUE) throw new IOException("No revision number available");
            rejectSymlinks(root.resolve("revisions"));
            Path stage = Files.createTempDirectory(root, "stage-");
            try {
                PatchApplier.writeFiles(stage, files, packageName);
                validateCompleteSnapshot(stage);
                int next = nextRevision(current);
                byte[] digest = snapshotDigest(stage);
                syncTree(stage);
                Files.move(stage, revisionPath(next), StandardCopyOption.ATOMIC_MOVE);
                syncDirectory(revisionPath(next).getParent());
                writeStageRecord(next, expectedRevision, digest);
                return next;
            } finally {
                if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) deleteTree(stage);
            }
        } finally {
            active.unlock();
        }
    }

    /** Promotes only the bytes staged against expectedRevision; safe to retry after HEAD moves. */
    public int promoteStagedSnapshot(int expectedRevision, int stagedRevision) throws IOException {
        enter();
        try {
            int current = checkedStageHead(expectedRevision, stagedRevision);
            validatedStageDigest(expectedRevision, stagedRevision);
            if (current == expectedRevision) writeHead(stagedRevision);
            return stagedRevision;
        } finally {
            active.unlock();
        }
    }

    /** Returns the exact staged source digest after checking its durable record and bytes. */
    public byte[] validatedStagedSnapshotDigest(int expectedRevision, int stagedRevision)
            throws IOException {
        enter();
        try {
            checkedStageHead(expectedRevision, stagedRevision);
            return validatedStageDigest(expectedRevision, stagedRevision);
        } finally {
            active.unlock();
        }
    }

    private int checkedStageHead(int expectedRevision, int stagedRevision) throws IOException {
        if (stagedRevision < 0 || stagedRevision <= expectedRevision) {
            throw new SecurityException("Invalid staged revision");
        }
        int current = currentRevision();
        if (current != expectedRevision && current != stagedRevision) {
            throw new IllegalStateException("Stale project revision");
        }
        return current;
    }

    private byte[] validatedStageDigest(int expectedRevision, int stagedRevision)
            throws IOException {
        Path record = stageRecordPath(stagedRevision);
        rejectSymlinks(record);
        if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("Missing staged snapshot record");
        }
        byte[] recordBytes = new byte[MAX_STAGE_RECORD_BYTES + 1];
        int recordLength = 0;
        try (InputStream input = Files.newInputStream(record)) {
            int value;
            while (recordLength < recordBytes.length && (value = input.read()) != -1) {
                recordBytes[recordLength++] = (byte) value;
            }
        }
        if (recordLength > MAX_STAGE_RECORD_BYTES) {
            throw new SecurityException("Invalid staged snapshot record");
        }
        String contents = new String(recordBytes, 0, recordLength, StandardCharsets.US_ASCII);
        if (!contents.matches("[0-9]{1,10}\\n[0-9a-f]{64}\\n")) {
            throw new SecurityException("Invalid staged snapshot record");
        }
        String[] parts = contents.split("\\n");
        if (!parts[0].equals(Integer.toString(expectedRevision))) {
            throw new SecurityException("Invalid staged snapshot record");
        }
        Path staged = revisionPath(stagedRevision);
        rejectSymlinks(staged);
        validateCompleteSnapshot(staged);
        byte[] digest = snapshotDigest(staged);
        if (!parts[1].equals(hex(digest))) {
            throw new SecurityException("Staged snapshot changed");
        }
        return digest;
    }

    private void validateCompleteSnapshot(Path directory) throws IOException {
        PatchApplier.validateTree(directory, packageName);
        if (!Files.isRegularFile(directory.resolve(requiredMain), LinkOption.NOFOLLOW_LINKS)
                || PatchApplier.LEGACY_PACKAGE.equals(packageName)
                        && !Files.isRegularFile(
                                directory.resolve(REQUIRED_LAYOUT), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Recovery snapshot is missing starter files");
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
            long count =
                    entries.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .count();
            if (count < 2) throw new IOException("Recovery snapshot is incomplete");
        }
    }

    private byte[] snapshotDigest(Path directory) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
        TreeMap<String, byte[]> files = new TreeMap<>();
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (!attrs.isRegularFile() || attrs.isSymbolicLink()) {
                            throw new SecurityException(
                                    "Staged snapshot contains unsupported file");
                        }
                        files.put(
                                directory.relativize(file).toString().replace('\\', '/'),
                                Files.readAllBytes(file));
                        return FileVisitResult.CONTINUE;
                    }
                });
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            ByteArrayOutputStream framing = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(framing)) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                output.writeInt(name.length);
                output.write(name);
                output.writeInt(entry.getValue().length);
                output.write(entry.getValue());
            }
            digest.update(framing.toByteArray());
        }
        return digest.digest();
    }

    private Path stageRecordPath(int revision) {
        return root.resolve("recovery-stages").resolve(Integer.toString(revision));
    }

    private void writeStageRecord(int revision, int expected, byte[] digest) throws IOException {
        Path records = root.resolve("recovery-stages");
        rejectSymlinks(records);
        Files.createDirectories(records);
        syncDirectory(root);
        Path temp = Files.createTempFile(records, "record-", ".tmp");
        try {
            Files.write(
                    temp,
                    (expected + "\n" + hex(digest) + "\n").getBytes(StandardCharsets.US_ASCII));
            syncFile(temp);
            Files.move(temp, stageRecordPath(revision), StandardCopyOption.ATOMIC_MOVE);
            syncDirectory(records);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void syncTree(Path directory) throws IOException {
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (!attrs.isRegularFile() || attrs.isSymbolicLink())
                            throw new SecurityException(
                                    "Project source changed during persistence");
                        syncFile(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException failure)
                            throws IOException {
                        if (failure != null) throw failure;
                        syncDirectory(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
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

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    public BuildLease beginBuild(int expectedRevision, File output, String manifest)
            throws IOException {
        return beginBuild(expectedRevision, output, manifest, Map.of());
    }

    /** As above, with the builder's own Java files, under {@link #BUILDER_SUBPACKAGE}, added. */
    public BuildLease beginBuild(
            int expectedRevision, File output, String manifest, Map<String, byte[]> builderFiles)
            throws IOException {
        enter();
        boolean leased = false;
        try {
            int current = currentRevision();
            if (current != expectedRevision)
                throw new IllegalStateException("Stale project revision");
            SnapshotStore.write(
                    revisionPath(current), output.toPath(), manifest, packageName, builderFiles);
            BuildLease lease = new BuildLease(active, current);
            leased = true;
            return lease;
        } finally {
            if (!leased) active.unlock();
        }
    }

    public static final class BuildLease implements AutoCloseable {
        private final ReentrantLock lock;
        private final int revision;
        private boolean closed;

        private BuildLease(ReentrantLock lock, int revision) {
            this.lock = lock;
            this.revision = revision;
        }

        public int revision() {
            return revision;
        }

        @Override
        public void close() {
            if (!closed) {
                lock.unlock();
                closed = true;
            }
        }
    }

    private Path revisionPath(int revision) {
        return root.resolve("revisions").resolve(Integer.toString(revision));
    }

    private int currentRevision() throws IOException {
        Path head = root.resolve("HEAD");
        rejectSymlinks(head);
        return Integer.parseInt(
                new String(Files.readAllBytes(head), StandardCharsets.US_ASCII).trim());
    }

    private void rejectSymlinks(Path path) {
        Path current = root;
        if (Files.isSymbolicLink(current)) throw new SecurityException("Project path is a symlink");
        for (Path segment : root.relativize(path)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current))
                throw new SecurityException("Project path is a symlink");
        }
    }

    private void publish(Path stage, int revision) throws IOException {
        Path destination = revisionPath(revision);
        syncTree(stage);
        Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE);
        syncDirectory(destination.getParent());
        writeHead(revision);
    }

    private void writeHead(int revision) throws IOException {
        Path tempHead = Files.createTempFile(root, "head-", ".tmp");
        try {
            Files.write(tempHead, Integer.toString(revision).getBytes(StandardCharsets.US_ASCII));
            syncFile(tempHead);
            Files.move(
                    tempHead,
                    root.resolve("HEAD"),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            syncDirectory(root);
        } finally {
            Files.deleteIfExists(tempHead);
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        Files.walkFileTree(
                source,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(destination.resolve(source.relativize(directory)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        if (attrs.isSymbolicLink())
                            throw new SecurityException("Project revision contains a symlink");
                        Files.copy(file, destination.resolve(source.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /** Deletes a directory tree without following symlinks inside it. */
    public static void deleteTree(Path directory) throws IOException {
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path folder, IOException exception)
                            throws IOException {
                        if (exception != null) throw exception;
                        Files.delete(folder);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private void enter() {
        if (active.isHeldByCurrentThread() || !active.tryLock()) {
            throw new IllegalStateException("Project is busy");
        }
    }
}
