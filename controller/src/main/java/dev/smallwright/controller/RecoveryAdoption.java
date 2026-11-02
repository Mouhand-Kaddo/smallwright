package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/** One-project recovery import. A public-only marker permits safe restart after signer storage. */
final class RecoveryAdoption {
    enum Status {
        COMPLETE,
        PENDING_REIMPORT
    }

    interface Project {
        int revision() throws Exception;

        boolean isPristineDefaultSnapshot() throws Exception;

        int stageExactSnapshot(int expected, Map<String, byte[]> files) throws Exception;

        byte[] validatedStagedSnapshotDigest(int expected, int staged) throws Exception;

        int promoteStagedSnapshot(int expected, int staged) throws Exception;
    }

    static final class StoredSigner {
        final byte[] certificate;
        final byte[] publicLineage;

        StoredSigner(byte[] certificate, byte[] publicLineage) {
            this.certificate = certificate.clone();
            this.publicLineage = publicLineage.clone();
        }
    }

    interface Signer {
        StoredSigner load() throws Exception;

        void saveNew(PortableSigningIdentity identity, byte[] publicLineage) throws Exception;

        void saveForPendingRecovery(PortableSigningIdentity identity, byte[] publicLineage)
                throws Exception;
    }

    interface Preconditions {
        /** Reject legacy signer, active run or build, and other conflicting work. */
        void requireNoLegacySignerOrActiveWork() throws Exception;
    }

    interface InstalledApp {
        /**
         * Verify update signing compatibility and return a usable version above both saved and
         * installed.
         */
        int nextVersionCode(int savedVersion, byte[] recoveredCertificate, byte[] publicLineage)
                throws Exception;
    }

    interface VersionFloor {
        int nextVersion() throws Exception;

        /**
         * Must be durable before returning; Android implementation should use
         * SharedPreferences.commit().
         */
        void raiseToAtLeast(int next) throws Exception;
    }

    private static final int MAGIC = 0x504c4144; // PLAD
    private static final int FORMAT = 1;
    private static final int MARKER_SIZE = 4 + 4 + 4 * 4 + 32 * 3;
    private static final Object IMPORT_LOCK = new Object();
    private final Path marker;
    private final Project project;
    private final Signer signer;
    private final InstalledApp installedApp;
    private final Preconditions preconditions;
    private final VersionFloor floor;

    RecoveryAdoption(
            File marker,
            Project project,
            Signer signer,
            InstalledApp installedApp,
            Preconditions preconditions,
            VersionFloor floor) {
        if (marker == null
                || project == null
                || signer == null
                || installedApp == null
                || preconditions == null
                || floor == null) throw new IllegalArgumentException("Missing recovery dependency");
        try {
            File parent = marker.getParentFile();
            if (parent == null)
                throw new IllegalArgumentException("Recovery marker needs a parent");
            // Android may put a platform-owned symlink above the app's private directory.
            this.marker = parent.getCanonicalFile().toPath().resolve(marker.getName());
        } catch (IOException unavailable) {
            throw new IllegalArgumentException(
                    "Recovery marker directory unavailable", unavailable);
        }
        this.project = project;
        this.signer = signer;
        this.installedApp = installedApp;
        this.preconditions = preconditions;
        this.floor = floor;
    }

    Status adopt(RecoveryBundle bundle) throws Exception {
        synchronized (IMPORT_LOCK) {
            return adoptLocked(bundle);
        }
    }

    private Status adoptLocked(RecoveryBundle bundle) throws Exception {
        if (bundle == null || bundle.versionCode() >= Integer.MAX_VALUE - 1)
            throw new IllegalArgumentException("Invalid recovery bundle");
        Marker pending = readMarker();
        if (pending != null) {
            if (!pending.matches(bundle))
                throw new SecurityException("Different recovery import is pending");
            return finish(pending, bundle);
        }
        preconditions.requireNoLegacySignerOrActiveWork();
        if (project.revision() != 0
                || !project.isPristineDefaultSnapshot()
                || signer.load() != null
                || floor.nextVersion() != 1) {
            throw new SecurityException("Recovery import requires a pristine project");
        }
        byte[] sourceDigest = sourceDigest(bundle.sources());
        int nextVersion =
                installedApp.nextVersionCode(
                        bundle.versionCode(),
                        bundle.signer().certificate().getEncoded(),
                        bundle.publicLineage());
        int staged = project.stageExactSnapshot(0, bundle.sources());
        if (staged <= 0
                || !MessageDigest.isEqual(
                        sourceDigest, project.validatedStagedSnapshotDigest(0, staged))) {
            throw new SecurityException("Recovery staging changed");
        }
        Marker created =
                new Marker(
                        0,
                        staged,
                        bundle.revision(),
                        nextVersion,
                        sourceDigest,
                        sha256(bundle.signer().certificate().getEncoded()),
                        sha256(bundle.publicLineage()));
        writeMarker(created); // Durable before saveNew: promotion can never precede signer storage.
        return finish(created, bundle);
    }

    Status resume() throws Exception {
        synchronized (IMPORT_LOCK) {
            return resumeLocked();
        }
    }

    private Status resumeLocked() throws Exception {
        Marker pending = readMarker();
        return pending == null ? Status.COMPLETE : finish(pending, null);
    }

    private Status finish(Marker pending, RecoveryBundle bundle) throws Exception {
        preconditions.requireNoLegacySignerOrActiveWork();
        int revision = project.revision();
        if (revision != pending.expected && revision != pending.staged
                || !MessageDigest.isEqual(
                        pending.sourceDigest,
                        project.validatedStagedSnapshotDigest(pending.expected, pending.staged))) {
            throw new SecurityException("Pending recovery snapshot changed");
        }
        StoredSigner stored = null;
        boolean orphanedKey = false;
        try {
            stored = signer.load();
        } catch (PortableSignerStore.OrphanedWrappingKeyException interrupted) {
            orphanedKey = true;
        }
        if (stored == null) {
            if (bundle == null) return Status.PENDING_REIMPORT;
            if (revision != pending.expected || floor.nextVersion() != 1) {
                throw new SecurityException("Recovery state changed before signer save");
            }
            installedApp.nextVersionCode(
                    pending.nextVersion - 1,
                    bundle.signer().certificate().getEncoded(),
                    bundle.publicLineage());
            if (orphanedKey) {
                signer.saveForPendingRecovery(bundle.signer(), bundle.publicLineage());
            } else {
                signer.saveNew(bundle.signer(), bundle.publicLineage());
            }
            stored = signer.load();
            if (stored == null)
                throw new GeneralSecurityException("Recovery signer was not persisted");
        }
        if (!MessageDigest.isEqual(pending.signerDigest, sha256(stored.certificate))
                || !MessageDigest.isEqual(pending.lineageDigest, sha256(stored.publicLineage))) {
            throw new SecurityException("Recovery signer differs from pending import");
        }
        int nextVersion =
                installedApp.nextVersionCode(
                        pending.nextVersion - 1, stored.certificate, stored.publicLineage);
        project.promoteStagedSnapshot(pending.expected, pending.staged);
        floor.raiseToAtLeast(nextVersion);
        if (floor.nextVersion() < nextVersion)
            throw new IllegalStateException("Recovery version floor was not persisted");
        Files.delete(marker);
        return Status.COMPLETE;
    }

    static byte[] sourceDigest(Map<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : new TreeMap<>(files).entrySet()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
                out.writeInt(name.length);
                out.write(name);
                out.writeInt(entry.getValue().length);
                out.write(entry.getValue());
                digest.update(bytes.toByteArray());
            }
            return digest.digest();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private Marker readMarker() throws IOException {
        rejectLinks();
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) != MARKER_SIZE)
            throw new SecurityException("Invalid recovery marker");
        java.nio.ByteBuffer data = java.nio.ByteBuffer.wrap(Files.readAllBytes(marker));
        if (data.getInt() != MAGIC || data.getInt() != FORMAT)
            throw new SecurityException("Invalid recovery marker");
        int expected = data.getInt();
        int staged = data.getInt();
        int origin = data.getInt();
        int next = data.getInt();
        if (expected != 0
                || staged <= expected
                || origin < 0
                || next < 2
                || next >= Integer.MAX_VALUE)
            throw new SecurityException("Invalid recovery marker metadata");
        byte[] sources = new byte[32], certificate = new byte[32], lineage = new byte[32];
        data.get(sources).get(certificate).get(lineage);
        return new Marker(expected, staged, origin, next, sources, certificate, lineage);
    }

    private void writeMarker(Marker value) throws IOException {
        rejectLinks();
        Path parent = marker.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Recovery marker directory unavailable");
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS))
            throw new SecurityException("Recovery marker already exists");
        java.nio.ByteBuffer data = java.nio.ByteBuffer.allocate(MARKER_SIZE);
        data.putInt(MAGIC)
                .putInt(FORMAT)
                .putInt(value.expected)
                .putInt(value.staged)
                .putInt(value.originRevision)
                .putInt(value.nextVersion)
                .put(value.sourceDigest)
                .put(value.signerDigest)
                .put(value.lineageDigest);
        Path temporary = Files.createTempFile(parent, "adoption-", ".tmp");
        try {
            Files.write(temporary, data.array());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
            try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
                channel.force(true);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void rejectLinks() {
        if (Files.isSymbolicLink(marker) || Files.isSymbolicLink(marker.getParent()))
            throw new SecurityException("Recovery marker path is a link");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static final class Marker {
        final int expected, staged, originRevision, nextVersion;
        final byte[] sourceDigest, signerDigest, lineageDigest;

        Marker(
                int expected,
                int staged,
                int originRevision,
                int nextVersion,
                byte[] sourceDigest,
                byte[] signerDigest,
                byte[] lineageDigest) {
            this.expected = expected;
            this.staged = staged;
            this.originRevision = originRevision;
            this.nextVersion = nextVersion;
            this.sourceDigest = sourceDigest.clone();
            this.signerDigest = signerDigest.clone();
            this.lineageDigest = lineageDigest.clone();
        }

        boolean matches(RecoveryBundle bundle) throws GeneralSecurityException {
            return bundle.versionCode() < Integer.MAX_VALUE - 1
                    && originRevision == bundle.revision()
                    && nextVersion >= bundle.versionCode() + 1
                    && MessageDigest.isEqual(sourceDigest, sourceDigest(bundle.sources()))
                    && MessageDigest.isEqual(
                            signerDigest, sha256(bundle.signer().certificate().getEncoded()))
                    && MessageDigest.isEqual(lineageDigest, sha256(bundle.publicLineage()));
        }
    }
}
