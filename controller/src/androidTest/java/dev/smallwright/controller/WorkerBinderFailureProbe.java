package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.FileDescriptor;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Forces a bounded input failure through the real authorized Binder service. */
final class WorkerBinderFailureProbe {
    /** Negotiated diagnostic output retains the original admission and timeout boundary. */
    static String runDiagnostics(Context controller, boolean lifecycle) throws Exception {
        File root =
                Files.createTempDirectory(
                                controller.getCacheDir().toPath(), "diagnostic-binder-probe-")
                        .toFile();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> remote = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        boolean bound = false;
        try {
            bound =
                    controller.bindService(
                            new Intent()
                                    .setComponent(
                                            new ComponentName(
                                                    "dev.smallwright.worker",
                                                    "dev.smallwright.worker.BuildService")),
                            connection,
                            Context.BIND_AUTO_CREATE);
            if (!bound || !connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            IBuildService worker = remote.get();
            if (!"worker-ready:dev.smallwright.controller:numeric-diagnostics-v2"
                    .equals(worker.handshake(2)))
                throw new AssertionError("Diagnostic negotiation failed");
            if (lifecycle) return diagnosticLifecycle(controller, root, worker);
            StringBuilder admitted = new StringBuilder();
            boolean hardlinkBlocked = false;
            for (String kind :
                    new String[] {
                        "pipe", "nonempty", "source-alias", "artifact-alias", "hardlink", "readonly"
                    }) {
                File inputFile = new File(root, kind + "-input");
                File apkFile = new File(root, kind + "-apk");
                File reportFile = new File(root, kind + "-report");
                Files.write(inputFile.toPath(), new byte[0]);
                Files.write(apkFile.toPath(), new byte[0]);
                Files.write(
                        reportFile.toPath(),
                        "nonempty".equals(kind) ? new byte[] {7} : new byte[0]);
                if ("hardlink".equals(kind)) {
                    try {
                        Files.createLink(
                                new File(root, "report-alias").toPath(), reportFile.toPath());
                    } catch (java.nio.file.AccessDeniedException deniedByPlatform) {
                        hardlinkBlocked = true;
                        continue;
                    }
                }
                ParcelFileDescriptor[] pipe =
                        "pipe".equals(kind) ? ParcelFileDescriptor.createPipe() : null;
                try (ParcelFileDescriptor input =
                                ParcelFileDescriptor.open(
                                        inputFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        ParcelFileDescriptor output =
                                ParcelFileDescriptor.open(
                                        apkFile, ParcelFileDescriptor.MODE_READ_WRITE);
                        ParcelFileDescriptor report =
                                pipe != null
                                        ? pipe[1]
                                        : ParcelFileDescriptor.open(
                                                "source-alias".equals(kind)
                                                        ? inputFile
                                                        : "artifact-alias".equals(kind)
                                                                ? apkFile
                                                                : reportFile,
                                                "readonly".equals(kind)
                                                        ? ParcelFileDescriptor.MODE_READ_ONLY
                                                        : ParcelFileDescriptor.MODE_READ_WRITE)) {
                    try {
                        worker.buildWithDiagnostics(input, output, report);
                        admitted.append(kind).append(' ');
                    } catch (IllegalArgumentException expected) {
                        /* Exact descriptor policy rejection. */
                    }
                } finally {
                    if (pipe != null) {
                        if (!reachesEof(pipe[0].getFileDescriptor(), 5_000))
                            throw new AssertionError("Rejected report pipe retained or written");
                        closeQuietly(pipe[0]);
                        closeQuietly(pipe[1]);
                    }
                }
                if ("nonempty".equals(kind)
                        && !java.util.Arrays.equals(
                                new byte[] {7}, Files.readAllBytes(reportFile.toPath())))
                    throw new AssertionError("Rejected report was modified");
                if (apkFile.length() != 0)
                    throw new AssertionError("Descriptor rejection produced APK bytes");
            }
            if (admitted.length() != 0)
                throw new AssertionError("Invalid diagnostic outputs admitted: " + admitted);
            return "Diagnostic Binder: pipe/nonempty/source-alias/artifact-alias/readonly rejected; hardlink="
                    + (hardlinkBlocked
                            ? "fixture creation denied by platform; worker case NOT RUN"
                            : "rejected")
                    + "; report pipe closed; no APK or sentinel modification\n";
        } finally {
            if (bound) controller.unbindService(connection);
            removeTestTree(root);
        }
    }

    private static String diagnosticLifecycle(Context controller, File root, IBuildService worker)
            throws Exception {
        ParcelFileDescriptor[][] pipes = createTwoPipes();
        ParcelFileDescriptor[] snapshot = pipes[0], artifact = pipes[1];
        File reportFile = new File(root, "timeout-report");
        Files.write(reportFile.toPath(), new byte[0]);
        try (ParcelFileDescriptor report =
                ParcelFileDescriptor.open(reportFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
            setNonblocking(snapshot[1].getFileDescriptor());
            FutureTask<String> first =
                    new FutureTask<>(
                            () -> worker.buildWithDiagnostics(snapshot[0], artifact[1], report));
            CountDownLatch finished = new CountDownLatch(1);
            Thread thread =
                    new Thread(
                            () -> {
                                try {
                                    first.run();
                                } finally {
                                    finished.countDown();
                                }
                            },
                            "diagnostic-timeout-build");
            thread.setDaemon(true);
            thread.start();
            try {
                waitForWorkerToConsume(snapshot[1].getFileDescriptor(), finished);
                ParcelFileDescriptor[][] busyPipes = createTwoPipes();
                File busyReportFile = new File(root, "busy-report");
                Files.write(busyReportFile.toPath(), new byte[0]);
                try (ParcelFileDescriptor busyReport =
                        ParcelFileDescriptor.open(
                                busyReportFile, ParcelFileDescriptor.MODE_READ_WRITE)) {
                    try {
                        if (!"ERROR: Worker busy"
                                .equals(
                                        worker.buildWithDiagnostics(
                                                busyPipes[0][0], busyPipes[1][1], busyReport)))
                            throw new AssertionError("Overlapping diagnostic build was admitted");
                        closeQuietly(busyPipes[0][0]);
                        closeQuietly(busyPipes[1][1]);
                        requireClosedInput(busyPipes[0][1]);
                        if (!reachesEof(busyPipes[1][0].getFileDescriptor(), 5_000)
                                || busyReportFile.length() != 0)
                            throw new AssertionError(
                                    "Busy diagnostic build retained output or wrote report");
                    } finally {
                        for (ParcelFileDescriptor[] pair : busyPipes)
                            for (ParcelFileDescriptor fd : pair) closeQuietly(fd);
                    }
                }
                if (!"SNAPSHOT_TIMEOUT".equals(first.get(20, TimeUnit.SECONDS)))
                    throw new AssertionError("Held-open diagnostic build did not time out");
                closeQuietly(snapshot[0]);
                closeQuietly(artifact[1]);
                requireClosedInput(snapshot[1]);
                if (!reachesEof(artifact[0].getFileDescriptor(), 5_000) || reportFile.length() != 0)
                    throw new AssertionError(
                            "Timed-out diagnostic build retained output or wrote report");
            } finally {
                for (ParcelFileDescriptor[] pair : pipes)
                    for (ParcelFileDescriptor fd : pair) closeQuietly(fd);
                try {
                    first.get(10, TimeUnit.SECONDS);
                } finally {
                    first.cancel(true);
                }
            }
        }
        ProjectIdentity project =
                new ProjectRegistry(new File(root, "projects"), root)
                        .create("Diagnostic timeout recovery");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        File archive = new File(root, "snapshot.zip"),
                apk = new File(root, "recovered.apk"),
                reportFile2 = new File(root, "success-report");
        Files.write(reportFile2.toPath(), new byte[0]);
        try (dev.smallwright.project.ProjectStore.BuildLease lease =
                        source.beginBuild(0, archive, 1);
                ParcelFileDescriptor input =
                        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY);
                ParcelFileDescriptor output =
                        ParcelFileDescriptor.open(
                                apk,
                                ParcelFileDescriptor.MODE_CREATE
                                        | ParcelFileDescriptor.MODE_READ_WRITE);
                ParcelFileDescriptor report =
                        ParcelFileDescriptor.open(
                                reportFile2, ParcelFileDescriptor.MODE_READ_WRITE)) {
            if (!"OK".equals(worker.buildWithDiagnostics(input, output, report))
                    || reportFile2.length() != 0)
                throw new AssertionError(
                        "Diagnostic timeout recovery build failed or wrote error report");
        }
        ArtifactValidator.validateUnsigned(controller, apk, 1, project.packageName());
        return "Diagnostic Binder: busy rejection and held-open timeout closed transferred pipes with empty reports; same worker compiled fresh native APK afterward\n";
    }

    private static void requireClosedInput(ParcelFileDescriptor writer) throws Exception {
        try {
            Os.write(writer.getFileDescriptor(), new byte[] {1}, 0, 1);
            throw new AssertionError("Worker retained transferred snapshot pipe");
        } catch (ErrnoException expected) {
            if (expected.errno != OsConstants.EPIPE) throw expected;
        }
    }

    /** A silent writer retains its pipe; the worker must finish without producer EOF. */
    static String runHeldOpen(Context controller) throws Exception {
        File root =
                Files.createTempDirectory(
                                controller.getFilesDir().toPath(), "binder-held-open-probe-")
                        .toFile();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> remote = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        boolean bound = false;
        try {
            Intent intent =
                    new Intent()
                            .setComponent(
                                    new ComponentName(
                                            "dev.smallwright.worker",
                                            "dev.smallwright.worker.BuildService"));
            bound = controller.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound || !connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            IBuildService worker = remote.get();
            ParcelFileDescriptor[][] pipes = createTwoPipes();
            ParcelFileDescriptor[] snapshot = pipes[0];
            ParcelFileDescriptor[] artifact = pipes[1];
            FutureTask<String> build =
                    new FutureTask<>(() -> worker.build(snapshot[0], artifact[1]));
            Thread caller = new Thread(build, "binder-held-open-build");
            caller.setDaemon(true);
            caller.start();
            try {
                String result = build.get(20, TimeUnit.SECONDS);
                if (!"SNAPSHOT_TIMEOUT".equals(result))
                    throw new AssertionError("Held-open snapshot returned: " + result);
                closeQuietly(snapshot[0]);
                closeQuietly(artifact[1]);
                try {
                    Os.write(snapshot[1].getFileDescriptor(), new byte[] {1}, 0, 1);
                    throw new AssertionError("Timed-out worker retained snapshot pipe");
                } catch (ErrnoException expected) {
                    if (expected.errno != OsConstants.EPIPE) throw expected;
                }
                if (!reachesEof(artifact[0].getFileDescriptor(), 5_000))
                    throw new AssertionError("Timed-out worker retained artifact pipe");
            } finally {
                closeQuietly(snapshot[0]);
                closeQuietly(snapshot[1]);
                closeQuietly(artifact[0]);
                closeQuietly(artifact[1]);
                // The old implementation can now finish: never leave a blocked request behind.
                try {
                    build.get(10, TimeUnit.SECONDS);
                } finally {
                    build.cancel(true);
                }
            }
            ProjectIdentity identity =
                    new ProjectRegistry(new File(root, "projects"), root)
                            .create("Held-open recovery probe");
            ProjectSourceStore source = new ProjectSourceStore(identity);
            source.initialize();
            File archive = new File(root, "snapshot.zip");
            File apk = new File(root, "unsigned.apk");
            try (dev.smallwright.project.ProjectStore.BuildLease lease =
                            source.beginBuild(0, archive, 1);
                    ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    archive, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    apk,
                                    ParcelFileDescriptor.MODE_CREATE
                                            | ParcelFileDescriptor.MODE_READ_WRITE)) {
                String result = worker.build(input, output);
                if (!"OK".equals(result))
                    throw new AssertionError("Build after snapshot deadline failed: " + result);
            }
            ArtifactValidator.validateUnsigned(controller, apk, 1, identity.packageName());
            if (source.revision() != 0) throw new AssertionError("Probe build changed source");
            return "Binder deadline: held-open snapshot timed out; transferred pipes closed; "
                    + "same worker admitted and compiled fresh project APK afterward; no signing keys read\n";
        } finally {
            if (bound) controller.unbindService(connection);
            removeTestTree(root);
        }
    }

    static String run(Context controller) throws Exception {
        File root =
                Files.createTempDirectory(
                                controller.getFilesDir().toPath(), "binder-failure-probe-")
                        .toFile();
        try {
            File cache = new File(root, "cache");
            if (!cache.mkdir()) throw new AssertionError("Probe cache unavailable");
            Context isolated =
                    new ContextWrapper(controller) {
                        @Override
                        public File getFilesDir() {
                            return root;
                        }

                        @Override
                        public File getCacheDir() {
                            return cache;
                        }

                        @Override
                        public File getNoBackupFilesDir() {
                            return root;
                        }
                    };
            ProjectSourceStore source = new ProjectSourceStore(isolated);
            source.initialize();
            int originalRevision = source.revision();

            CountDownLatch connected = new CountDownLatch(1);
            AtomicReference<IBuildService> remote = new AtomicReference<>();
            ServiceConnection connection =
                    new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder binder) {
                            remote.set(IBuildService.Stub.asInterface(binder));
                            connected.countDown();
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            remote.set(null);
                        }
                    };
            Intent intent =
                    new Intent()
                            .setComponent(
                                    new ComponentName(
                                            "dev.smallwright.worker",
                                            "dev.smallwright.worker.BuildService"));
            if (!controller.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                throw new AssertionError("Worker bind refused");
            }
            try {
                if (!connected.await(15, TimeUnit.SECONDS) || remote.get() == null) {
                    throw new AssertionError("Worker unavailable");
                }
                ParcelFileDescriptor[][] pipes = createTwoPipes();
                ParcelFileDescriptor[] snapshot = pipes[0];
                ParcelFileDescriptor[] artifact = pipes[1];
                try {
                    AtomicReference<Throwable> writerFailure = new AtomicReference<>();
                    CountDownLatch writerFinished = new CountDownLatch(1);
                    setNonblocking(snapshot[1].getFileDescriptor());
                    Thread writer =
                            new Thread(
                                    () -> {
                                        byte[] chunk = new byte[8192];
                                        try {
                                            while (true) {
                                                try {
                                                    Os.write(
                                                            snapshot[1].getFileDescriptor(),
                                                            chunk,
                                                            0,
                                                            chunk.length);
                                                } catch (ErrnoException failure) {
                                                    if (failure.errno == OsConstants.EAGAIN) {
                                                        Thread.sleep(10);
                                                        continue;
                                                    }
                                                    if (failure.errno == OsConstants.EPIPE) return;
                                                    throw failure;
                                                }
                                            }
                                        } catch (Throwable failure) {
                                            writerFailure.set(failure);
                                        } finally {
                                            writerFinished.countDown();
                                        }
                                    },
                                    "binder-failure-snapshot-writer");
                    writer.setDaemon(true);
                    writer.start();
                    FutureTask<String> build =
                            new FutureTask<>(() -> remote.get().build(snapshot[0], artifact[1]));
                    Thread buildThread = new Thread(build, "binder-failure-build-call");
                    buildThread.setDaemon(true);
                    buildThread.start();
                    String result;
                    try {
                        result = build.get(20, TimeUnit.SECONDS);
                    } finally {
                        build.cancel(true);
                        snapshot[0].close();
                        artifact[1].close();
                    }
                    if (!"ERROR: Input exceeds size limit".equals(result)) {
                        throw new AssertionError("Oversized Binder snapshot returned: " + result);
                    }
                    if (!writerFinished.await(5, TimeUnit.SECONDS) || writerFailure.get() != null) {
                        throw new AssertionError(
                                "Worker retained the transferred snapshot descriptor",
                                writerFailure.get());
                    }
                    if (!reachesEof(artifact[0].getFileDescriptor(), 5_000)) {
                        throw new AssertionError(
                                "Worker retained the transferred artifact descriptor");
                    }
                    if (source.revision() != originalRevision
                            || new File(root, "projects/counter-alpha/artifacts").exists()) {
                        throw new AssertionError(
                                "Failed Binder build changed source or offered an APK");
                    }
                    return "Binder failure: oversized snapshot rejected; both transferred pipes closed; "
                            + "committed source unchanged and no APK offered\n";
                } finally {
                    closeQuietly(snapshot[0]);
                    closeQuietly(snapshot[1]);
                    closeQuietly(artifact[0]);
                    closeQuietly(artifact[1]);
                }
            } finally {
                controller.unbindService(connection);
            }
        } finally {
            removeTestTree(root);
        }
    }

    static String runConcurrent(Context controller) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> remote = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!controller.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            throw new AssertionError("Worker bind refused");
        }
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || remote.get() == null) {
                throw new AssertionError("Worker unavailable");
            }
            requireConcurrentRequestsRefused(remote.get());
            return "Binder overlap: concurrent build and toolchain probe refused; "
                    + "busy build pipes closed\n";
        } finally {
            controller.unbindService(connection);
        }
    }

    private static void setNonblocking(FileDescriptor descriptor) throws ErrnoException {
        int flags = Os.fcntlInt(descriptor, OsConstants.F_GETFL, 0);
        Os.fcntlInt(descriptor, OsConstants.F_SETFL, flags | OsConstants.O_NONBLOCK);
    }

    private static void requireConcurrentRequestsRefused(IBuildService remote) throws Exception {
        ParcelFileDescriptor[][] firstPipes = createTwoPipes();
        ParcelFileDescriptor[] firstSnapshot = firstPipes[0];
        ParcelFileDescriptor[] firstArtifact = firstPipes[1];
        AtomicReference<String> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        CountDownLatch firstFinished = new CountDownLatch(1);
        setNonblocking(firstSnapshot[1].getFileDescriptor());
        Thread first =
                new Thread(
                        () -> {
                            try {
                                firstResult.set(remote.build(firstSnapshot[0], firstArtifact[1]));
                            } catch (Throwable failure) {
                                firstFailure.set(failure);
                            } finally {
                                try {
                                    firstSnapshot[0].close();
                                } catch (Exception ignored) {
                                }
                                try {
                                    firstArtifact[1].close();
                                } catch (Exception ignored) {
                                }
                                firstFinished.countDown();
                            }
                        },
                        "binder-blocked-first-build");
        first.setDaemon(true);
        first.start();
        try {
            waitForWorkerToConsume(firstSnapshot[1].getFileDescriptor(), firstFinished);
            try {
                remote.toolchainProbe();
                throw new AssertionError("Concurrent toolchain probe was admitted");
            } catch (IllegalStateException expected) {
                if (!String.valueOf(expected.getMessage()).contains("ERROR: Worker busy")) {
                    throw new AssertionError("Concurrent probe returned another failure", expected);
                }
            }
            ParcelFileDescriptor[][] secondPipes = createTwoPipes();
            ParcelFileDescriptor[] secondSnapshot = secondPipes[0];
            ParcelFileDescriptor[] secondArtifact = secondPipes[1];
            try {
                String busy = remote.build(secondSnapshot[0], secondArtifact[1]);
                secondSnapshot[0].close();
                secondArtifact[1].close();
                if (!"ERROR: Worker busy".equals(busy)) {
                    throw new AssertionError("Concurrent build was admitted: " + busy);
                }
                try {
                    Os.write(secondSnapshot[1].getFileDescriptor(), new byte[] {1}, 0, 1);
                    throw new AssertionError("Busy build retained its snapshot descriptor");
                } catch (ErrnoException expected) {
                    if (expected.errno != OsConstants.EPIPE) throw expected;
                }
                if (!reachesEof(secondArtifact[0].getFileDescriptor(), 5_000)) {
                    throw new AssertionError("Busy build retained its artifact descriptor");
                }
            } finally {
                closeQuietly(secondSnapshot[0]);
                closeQuietly(secondSnapshot[1]);
                closeQuietly(secondArtifact[0]);
                closeQuietly(secondArtifact[1]);
            }
        } finally {
            closeQuietly(firstSnapshot[1]);
            try {
                if (!firstFinished.await(30, TimeUnit.SECONDS)
                        || firstFailure.get() != null
                        || !"ERROR: Snapshot archive is malformed".equals(firstResult.get())) {
                    throw new AssertionError(
                            "First blocked build did not finish after release", firstFailure.get());
                }
            } finally {
                closeQuietly(firstSnapshot[0]);
                closeQuietly(firstArtifact[0]);
                closeQuietly(firstArtifact[1]);
            }
        }
    }

    private static void waitForWorkerToConsume(FileDescriptor writer, CountDownLatch finished)
            throws Exception {
        byte[] chunk = new byte[8192];
        int sent = 0;
        boolean wasFull = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline && sent < 500 * 1024) {
            if (finished.getCount() == 0) {
                throw new AssertionError("First build finished before concurrency check");
            }
            try {
                int written = Os.write(writer, chunk, 0, chunk.length);
                sent += written;
                if (wasFull)
                    return; // The worker drained a full pipe while its Binder call was active.
            } catch (ErrnoException failure) {
                if (failure.errno != OsConstants.EAGAIN) throw failure;
                wasFull = true;
            }
            if (wasFull) Thread.sleep(10);
        }
        throw new AssertionError("Could not establish an active first Binder build");
    }

    private static boolean reachesEof(FileDescriptor descriptor, long timeoutMillis)
            throws Exception {
        setNonblocking(descriptor);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        byte[] bytes = new byte[1];
        while (System.nanoTime() < deadline) {
            try {
                if (Os.read(descriptor, bytes, 0, 1) == 0) return true;
                throw new AssertionError("Failed worker wrote an artifact");
            } catch (ErrnoException failure) {
                if (failure.errno != OsConstants.EAGAIN) throw failure;
            }
            Thread.sleep(10);
        }
        return false;
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        try {
            descriptor.close();
        } catch (Exception ignored) {
        }
    }

    private static ParcelFileDescriptor[][] createTwoPipes() throws Exception {
        ParcelFileDescriptor[] first = ParcelFileDescriptor.createPipe();
        try {
            return new ParcelFileDescriptor[][] {first, ParcelFileDescriptor.createPipe()};
        } catch (Exception failure) {
            closeQuietly(first[0]);
            closeQuietly(first[1]);
            throw failure;
        }
    }

    private static void removeTestTree(File file) throws Exception {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) removeTestTree(child);
        }
        Files.delete(file.toPath());
    }
}
