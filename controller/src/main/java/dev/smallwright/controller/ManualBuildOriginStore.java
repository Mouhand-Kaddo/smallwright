package dev.smallwright.controller;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Bounded, immutable manual source origins in the owning project's private directory. */
final class ManualBuildOriginStore {
    private static final int MAGIC = 0x504c4d4f;
    private static final int MAX_RECORD = 512;
    static final int MAX_ORIGINS = 1024;
    private final Path root;
    private final Path base;
    private final String projectId;

    ManualBuildOriginStore(File project, String projectId) {
        this.base = project.getParentFile().getParentFile().toPath().toAbsolutePath().normalize();
        this.root = project.toPath().toAbsolutePath().normalize().resolve("manual-build-origins");
        this.projectId = projectId;
        check(root);
    }

    static final class Origin {
        final String projectId, chatId, buildId;
        final int sourceRevision, versionCode;
        final byte[] sourceDigest;
        final String artifactSha256;

        Origin(
                String projectId,
                String chatId,
                String buildId,
                int sourceRevision,
                int versionCode,
                byte[] sourceDigest,
                String artifactSha256) {
            validate(projectId, chatId, buildId, versionCode);
            if (sourceRevision < 0
                    || sourceDigest == null
                    || sourceDigest.length != 32
                    || artifactSha256 != null && !artifactSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid manual source identity");
            this.projectId = projectId;
            this.chatId = chatId;
            this.buildId = buildId;
            this.sourceRevision = sourceRevision;
            this.versionCode = versionCode;
            this.sourceDigest = sourceDigest.clone();
            this.artifactSha256 = artifactSha256;
        }
    }

    static void validate(String project, String chat, String build, int version) {
        if (project == null
                || !(project.equals("counter-alpha") || project.matches("app[0-9a-f]{32}"))
                || chat == null
                || !chat.matches("[0-9a-f]{32}")
                || build == null
                || !build.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
                || version < 1
                || version == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid manual build identity");
    }

    void requireCapacity() throws IOException {
        check(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new SecurityException("Invalid manual origin directory");
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root, "*.bin")) {
            for (Path path : entries) {
                check(path);
                if (++count >= MAX_ORIGINS) throw new IOException("Manual origin limit reached");
            }
        }
    }

    void create(Origin origin) throws IOException {
        requireCapacity();
        Path path = path(origin.sourceRevision);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Manual source origin already exists");
        write(path, origin);
    }

    void pinArtifact(Origin origin, String sha256) throws IOException {
        Origin current = read(origin.sourceRevision);
        if (current == null
                || current.artifactSha256 != null
                || !current.projectId.equals(origin.projectId)
                || !current.chatId.equals(origin.chatId)
                || !current.buildId.equals(origin.buildId)
                || current.versionCode != origin.versionCode
                || !MessageDigest.isEqual(current.sourceDigest, origin.sourceDigest))
            throw new IllegalStateException("Manual artifact origin already bound or changed");
        write(
                path(origin.sourceRevision),
                new Origin(
                        origin.projectId,
                        origin.chatId,
                        origin.buildId,
                        origin.sourceRevision,
                        origin.versionCode,
                        origin.sourceDigest,
                        sha256));
    }

    Origin read(int revision) throws IOException {
        Path path = path(revision);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_RECORD)
            throw new SecurityException("Invalid manual origin file");
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 32) throw new SecurityException("Invalid manual origin checksum");
        byte[] data = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(
                hash(data), Arrays.copyOfRange(bytes, data.length, bytes.length)))
            throw new SecurityException("Invalid manual origin checksum");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != MAGIC) throw new SecurityException("Invalid manual origin format");
            String project = in.readUTF(), chat = in.readUTF(), build = in.readUTF();
            int source = in.readInt(), version = in.readInt();
            byte[] digest = new byte[32];
            in.readFully(digest);
            String apk = in.readBoolean() ? in.readUTF() : null;
            Origin origin = new Origin(project, chat, build, source, version, digest, apk);
            if (in.read() != -1 || !projectId.equals(project) || source != revision)
                throw new SecurityException("Manual origin belongs to another source/project");
            return origin;
        }
    }

    private Path path(int revision) {
        if (revision < 0) throw new IllegalArgumentException("Invalid source revision");
        Path path = root.resolve(revision + ".bin");
        check(path);
        return path;
    }

    private void write(Path target, Origin origin) throws IOException {
        check(root);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(root);
            sync(root.getParent());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeUTF(origin.projectId);
            out.writeUTF(origin.chatId);
            out.writeUTF(origin.buildId);
            out.writeInt(origin.sourceRevision);
            out.writeInt(origin.versionCode);
            out.write(origin.sourceDigest);
            out.writeBoolean(origin.artifactSha256 != null);
            if (origin.artifactSha256 != null) out.writeUTF(origin.artifactSha256);
        }
        bytes.write(hash(bytes.toByteArray()));
        if (bytes.size() > MAX_RECORD) throw new IOException("Manual origin too large");
        Path temporary = Files.createTempFile(root, "origin-", ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary.toFile())) {
                out.write(bytes.toByteArray());
                out.getFD().sync();
            }
            check(target);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            sync(root);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void check(Path path) {
        if (!path.startsWith(base))
            throw new SecurityException("Manual origin outside private storage");
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part))
                throw new SecurityException("Manual origin path is a link");
            if (part.equals(base)) return;
        }
    }

    private static void sync(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
