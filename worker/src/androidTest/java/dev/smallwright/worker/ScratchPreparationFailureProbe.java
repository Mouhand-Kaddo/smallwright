package dev.smallwright.worker;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

/** Real production entrypoint fault in disposable worker-UID storage, without Binder injection. */
final class ScratchPreparationFailureProbe {
    private ScratchPreparationFailureProbe() {}

    static String run(Context test, Context worker) throws Exception {
        if (Process.myUid() != worker.getApplicationInfo().uid)
            throw new AssertionError("Scratch probe must run in worker UID");
        File root =
                Files.createTempDirectory(
                                worker.getCacheDir().toPath(), "scratch-preparation-probe-")
                        .toFile();
        try {
            File blockedCache = new File(root, "blocked-cache");
            Files.createFile(blockedCache.toPath());
            Context fault =
                    new ContextWrapper(worker) {
                        @Override
                        public File getCacheDir() {
                            return blockedCache;
                        }
                    };
            File snapshot = new File(root, "snapshot.zip");
            try (InputStream source =
                    test.getAssets().open("default-generated-MainActivity.java")) {
                ProductionSnapshotProbe.snapshot(snapshot, source.readAllBytes());
            }
            byte[] digest = hash(snapshot);
            File artifact = new File(root, "unsigned.apk");
            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    artifact,
                                    ParcelFileDescriptor.MODE_CREATE
                                            | ParcelFileDescriptor.MODE_TRUNCATE
                                            | ParcelFileDescriptor.MODE_READ_WRITE)) {
                FileDescriptor inputFd = input.getFileDescriptor();
                FileDescriptor outputFd = output.getFileDescriptor();
                // Attest both actual descriptors are open before invoking production ownership.
                Os.fstat(inputFd);
                Os.fstat(outputFd);
                try {
                    BuildService.buildSnapshot(fault, input, output);
                    throw new AssertionError("Scratch preparation unexpectedly succeeded");
                } catch (IllegalStateException expected) {
                    if (!"Could not create build scratch directory".equals(expected.getMessage()))
                        throw new AssertionError("Unexpected scratch failure class or phase");
                }
                // Check before test-owned try-with-resources can close either descriptor.
                requireClosed(inputFd);
                requireClosed(outputFd);
            }
            if (!artifact.isFile()
                    || artifact.length() != 0
                    || !Arrays.equals(digest, hash(snapshot)))
                throw new AssertionError("Scratch failure changed snapshot or produced output");
            if (!blockedCache.isFile()
                    || blockedCache.length() != 0
                    || new File(blockedCache, "active-build").exists())
                throw new AssertionError("Scratch fault retained partial build storage");
            String recovery = ProductionSnapshotProbe.run(test, worker);
            return "Worker scratch preparation: mkdir failure before compilation; actual snapshot "
                    + "and artifact descriptors closed; zero output; unchanged fixture snapshot; "
                    + "no partial scratch; production entrypoint in worker UID (not Binder or "
                    + "controller revision verification); recovery: "
                    + recovery;
        } finally {
            removeOwnTree(root);
        }
    }

    private static byte[] hash(File file) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
    }

    private static void requireClosed(FileDescriptor descriptor) throws Exception {
        try {
            Os.fstat(descriptor);
            throw new AssertionError("Production retained a transferred descriptor");
        } catch (ErrnoException closed) {
            if (closed.errno != OsConstants.EBADF)
                throw new AssertionError("Descriptor failure was not EBADF");
        }
    }

    private static void removeOwnTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeOwnTree(child);
        if (!file.delete()) throw new IOException("Could not remove scratch probe storage");
    }
}
