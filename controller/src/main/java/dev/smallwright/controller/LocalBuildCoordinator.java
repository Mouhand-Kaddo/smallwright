package dev.smallwright.controller;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import dev.smallwright.libraries.LibraryList;
import dev.smallwright.libraries.LibraryResolver;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.CompilerDiagnostics;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Compiles one already committed revision and returns only a verified signed artifact. */
final class LocalBuildCoordinator {
    private static final int MAX_ADMITTED_SOURCE_BYTES = 1024 * 1024;
    private final Context context;
    private final ProjectSourceStore source;
    private final ProjectIdentity identity;

    LocalBuildCoordinator(Context context, ProjectSourceStore source) {
        this.context = context;
        this.source = source;
        this.identity = null;
    }

    LocalBuildCoordinator(Context context, ProjectSourceStore source, ProjectIdentity identity) {
        if (identity == null
                || !identity.id().equals(source.projectId())
                || !identity.packageName().equals(source.packageName())) {
            throw new IllegalArgumentException("Build source does not match project identity");
        }
        this.context = context;
        this.source = source;
        this.identity = identity;
    }

    /** Call off the UI thread. Version allocation and installation remain controller decisions. */
    VerifiedArtifact buildCommittedRevision(
            int expectedRevision, int versionCode, IBuildService worker) throws Exception {
        if (worker == null) throw new IOException("Worker is not connected");
        File artifacts = artifactsDirectory();
        if (!artifacts.mkdirs() && !artifacts.isDirectory())
            throw new IOException("Could not create artifacts");
        List<LibraryResolver.Resolved> libraries = libraries(expectedRevision);
        File snapshot = File.createTempFile("snapshot-", ".zip", context.getCacheDir());
        try (ProjectStore.BuildLease lease =
                source.beginBuild(
                        expectedRevision,
                        snapshot,
                        versionCode,
                        LibraryTools.manifests(libraries),
                        AppLinkAndroid.controllerCertificateSha256(context))) {
            File unsigned = File.createTempFile("unsigned-", ".apk", artifacts);
            Throwable failure = null;
            try {
                String result;
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        unsigned,
                                        ParcelFileDescriptor.MODE_READ_WRITE
                                                | ParcelFileDescriptor.MODE_TRUNCATE)) {
                    result = dispatchBuild(worker, input, output, snapshot, libraries);
                }
                requireWorkerSuccess(result);
                ArtifactValidator.validateUnsigned(
                        context, unsigned, versionCode, source.packageName());
                File signed =
                        new File(
                                artifacts,
                                (identity == null || "counter-alpha".equals(identity.id())
                                                ? "counter-v"
                                                : "app-v")
                                        + versionCode
                                        + ".apk");
                VerifiedArtifact artifact = signer().sign(context, unsigned, signed);
                ArtifactValidator.validateUnsigned(
                        context, signed, versionCode, source.packageName());
                artifact.verifyUnchanged();
                return artifact;
            } catch (Exception | Error thrown) {
                failure = thrown;
                throw thrown;
            } finally {
                try {
                    Files.deleteIfExists(unsigned.toPath());
                } catch (IOException cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        } finally {
            snapshot.delete();
        }
    }

    /** Recovery-safe output is scoped to a journaled build identity. */
    VerifiedArtifact buildCommittedRevision(
            int expectedRevision, int versionCode, String buildId, IBuildService worker)
            throws Exception {
        if (worker == null) throw new IOException("Worker is not connected");
        if (versionCode < 1) throw new IllegalArgumentException("Invalid build version");
        File artifacts = artifactsDirectory();
        File directory = RunRecovery.directory(artifacts, buildId);
        RunRecovery.discardPartials(artifacts, buildId);
        File snapshot = new File(directory, "snapshot.partial.zip");
        File unsigned = new File(directory, "unsigned.partial.apk");
        File signed = new File(directory, "signed.partial.apk");
        File complete = new File(directory, "app.apk");
        List<LibraryResolver.Resolved> libraries = libraries(expectedRevision);
        try (ProjectStore.BuildLease lease =
                source.beginBuild(
                        expectedRevision,
                        snapshot,
                        versionCode,
                        LibraryTools.manifests(libraries),
                        AppLinkAndroid.controllerCertificateSha256(context))) {
            Files.createFile(unsigned.toPath());
            String result;
            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    unsigned,
                                    ParcelFileDescriptor.MODE_READ_WRITE
                                            | ParcelFileDescriptor.MODE_TRUNCATE)) {
                result = dispatchBuild(worker, input, output, snapshot, libraries);
            }
            requireWorkerSuccess(result);
            ArtifactValidator.validateUnsigned(
                    context, unsigned, versionCode, source.packageName());
            VerifiedArtifact pending = signer().sign(context, unsigned, signed);
            ArtifactValidator.validateUnsigned(context, signed, versionCode, source.packageName());
            pending.verifyUnchanged();
            Files.move(
                    signed.toPath(),
                    complete.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            VerifiedArtifact artifact = VerifiedArtifact.capture(complete);
            if (!artifact.sha256().equals(pending.sha256()))
                throw new IOException("Published artifact changed");
            artifact.verifyUnchanged();
            return artifact;
        } finally {
            RunRecovery.discardPartials(artifacts, buildId);
        }
    }

    /** A saved build keeps its identity while a previous worker request settles. */
    VerifiedArtifact buildRecoveredRevision(
            int revision,
            int versionCode,
            String buildId,
            IBuildService worker,
            java.util.function.BooleanSupplier active,
            Runnable waiting)
            throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (!active.getAsBoolean() || Thread.currentThread().isInterrupted())
                throw new IllegalStateException("Saved build stopped");
            try {
                return buildCommittedRevision(revision, versionCode, buildId, worker);
            } catch (WorkerBusyFailure busy) {
                if (attempt == 19) throw busy;
                if (!active.getAsBoolean() || Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("Saved build stopped");
                waiting.run();
                Thread.sleep(250);
            }
        }
        throw new AssertionError("Bounded recovery exhausted without a result");
    }

    static void requireWorkerSuccess(String result) throws IOException {
        if ("OK".equals(result)) return;
        if ("ERROR: Worker busy".equals(result)) throw new WorkerBusyFailure();
        if ("COMPILE_ERROR".equals(result)) throw new JavaCompileFailure();
        if ("SNAPSHOT_TIMEOUT".equals(result))
            throw new IOException("Worker snapshot transfer timed out. Retry the build.");
        throw new IOException(result);
    }

    private String dispatchBuild(
            IBuildService worker,
            ParcelFileDescriptor input,
            ParcelFileDescriptor output,
            File leasedSnapshot,
            List<LibraryResolver.Resolved> libraries)
            throws Exception {
        File platform =
                new PlatformFiles(
                                new File(
                                        context.getApplicationContext().getNoBackupFilesDir(),
                                        "platform"),
                                PlatformFiles.GOOGLE_36_R02)
                        .verifiedJar();
        try (ParcelFileDescriptor platformInput =
                ParcelFileDescriptor.open(platform, ParcelFileDescriptor.MODE_READ_ONLY)) {
            requireWorkerSuccess(worker.usePlatform(platformInput));
        }
        giveLibraries(worker, libraries);
        boolean detailed;
        try {
            String capability = worker.handshake(2);
            if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                    .equals(capability)) {
                throw new IOException("Unexpected worker diagnostics capability");
            }
            detailed = true;
        } catch (IllegalArgumentException legacyVersion) {
            detailed = false;
        }
        if (!detailed) return worker.build(input, output);
        java.util.SortedMap<String, byte[]> admitted = admittedSources(leasedSnapshot);
        File reportFile =
                File.createTempFile("compiler-report-", ".partial", context.getCacheDir());
        try (ParcelFileDescriptor reportOutput =
                ParcelFileDescriptor.open(
                        reportFile,
                        ParcelFileDescriptor.MODE_READ_WRITE
                                | ParcelFileDescriptor.MODE_TRUNCATE)) {
            String result = worker.buildWithDiagnostics(input, output, reportOutput);
            if (!Files.isRegularFile(reportFile.toPath(), LinkOption.NOFOLLOW_LINKS)
                    || Files.size(reportFile.toPath()) > CompilerDiagnostics.MAX_BYTES) {
                throw new IOException("Invalid worker diagnostics file");
            }
            byte[] wire;
            try (InputStream reportInput = Files.newInputStream(reportFile.toPath())) {
                wire = readAtMost(reportInput, CompilerDiagnostics.MAX_BYTES + 1);
            }
            if (wire.length > CompilerDiagnostics.MAX_BYTES)
                throw new IOException("Oversized worker diagnostics");
            if ("COMPILE_ERROR".equals(result)) {
                throw new JavaCompileFailure(
                        wire.length == 0 ? null : CompilerDiagnostics.decode(wire, admitted));
            }
            if (wire.length != 0) throw new IOException("Diagnostics without a compiler failure");
            return result;
        } finally {
            Files.deleteIfExists(reportFile.toPath());
        }
    }

    /**
     * The checked JAR and AAR files the revision's library list needs, from the controller's cache
     * or downloaded. Read before the build lease, which keeps the store busy.
     */
    private List<LibraryResolver.Resolved> libraries(int revision) throws IOException {
        try {
            return LibraryTools.resolve(
                    LibraryTools.repositories(context),
                    source.readRecoverySources(revision).get(LibraryList.PATH));
        } catch (IOException unavailable) {
            throw new IOException(
                    "The project's libraries could not be prepared: " + unavailable.getMessage());
        }
    }

    /** Hands the worker each library file as a read-only descriptor with its SHA-256. */
    private static void giveLibraries(
            IBuildService worker, List<LibraryResolver.Resolved> libraries) throws Exception {
        ParcelFileDescriptor[] files = new ParcelFileDescriptor[libraries.size()];
        String[] hashes = new String[libraries.size()];
        try {
            for (int index = 0; index < files.length; index++) {
                LibraryResolver.Resolved library = libraries.get(index);
                files[index] =
                        ParcelFileDescriptor.open(
                                library.file, ParcelFileDescriptor.MODE_READ_ONLY);
                hashes[index] = library.sha256;
            }
            requireWorkerSuccess(worker.useLibraries(files, hashes));
        } finally {
            for (ParcelFileDescriptor file : files) if (file != null) file.close();
        }
    }

    /** The snapshot's Java sources by path, as the worker admits them for its report. */
    private static java.util.SortedMap<String, byte[]> admittedSources(File snapshot)
            throws IOException {
        java.util.SortedMap<String, byte[]> sources = new java.util.TreeMap<>();
        try (ZipFile zip = new ZipFile(snapshot)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("java/") || !entry.getName().endsWith(".java"))
                    continue;
                if (sources.size() == CompilerDiagnostics.MAX_SOURCES)
                    throw new IOException("Snapshot holds too many Java sources");
                try (InputStream input = zip.getInputStream(entry)) {
                    byte[] bytes = readAtMost(input, MAX_ADMITTED_SOURCE_BYTES + 1);
                    if (bytes.length > MAX_ADMITTED_SOURCE_BYTES)
                        throw new IOException("Admitted source exceeds limit");
                    sources.put(entry.getName(), bytes);
                }
            }
        }
        if (sources.isEmpty()) throw new IOException("Snapshot has no admitted Java source");
        return sources;
    }

    /** Bounded sentinel read using APIs available at the project's minimum Android version. */
    private static byte[] readAtMost(InputStream input, int capacity) throws IOException {
        byte[] bytes = new byte[capacity];
        int used = 0;
        while (used < capacity) {
            int count = input.read(bytes, used, capacity - used);
            if (count < 0) break;
            if (count == 0) {
                int next = input.read();
                if (next < 0) break;
                bytes[used++] = (byte) next;
            } else used += count;
        }
        return java.util.Arrays.copyOf(bytes, used);
    }

    private File artifactsDirectory() {
        return identity == null
                ? new File(context.getFilesDir(), "projects/counter-alpha/artifacts")
                : new File(identity.directory(), "artifacts");
    }

    private ProjectSigner signer() {
        return identity == null ? new ProjectSigner() : new ProjectSigner(identity);
    }
}
