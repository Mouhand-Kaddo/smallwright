package dev.smallwright.worker;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

/** Intentional worker-process death; the subsequent instrumentation verifies its receipt. */
final class RequestDeadlineProbe {
    private static final long BUDGET_MILLIS = 500;

    static void kill(Context worker, String id) throws Exception {
        requireWorkerUid(worker);
        File root = root(worker, id);
        Files.createDirectory(root.toPath()); // Never replace an existing probe directory.
        // Fixed test command only. exec preserves the printed PID for the sleeping child.
        java.lang.Process child =
                WorkerProcesses.start(
                        new ProcessBuilder(
                                        "/system/bin/sh",
                                        "-c",
                                        "echo $$; exec /system/bin/sleep 60")
                                .redirectErrorStream(true));
        try {
            int childPid;
            try (BufferedReader output =
                    new BufferedReader(
                            new InputStreamReader(
                                    child.getInputStream(), StandardCharsets.UTF_8))) {
                childPid = Integer.parseInt(output.readLine());
            }
            int workerPid = Process.myPid();
            if (childPid <= 0 || childPid == workerPid || !child.isAlive())
                throw new AssertionError("Probe child did not remain active");
            File marker = new File(root, "phase.txt");
            Files.write(
                    marker.toPath(),
                    ("running\n" + workerPid + "\n" + childPid + "\n")
                            .getBytes(StandardCharsets.UTF_8));
            long started = SystemClock.elapsedRealtime();
            BuildAdmission[] admission = new BuildAdmission[1];
            admission[0] =
                    new BuildAdmission(
                            BUDGET_MILLIS,
                            () -> {
                                try {
                                    long elapsed = SystemClock.elapsedRealtime() - started;
                                    String busy =
                                            admission[0].run(() -> "unsafe overlap", () -> "busy");
                                    if (elapsed < BUDGET_MILLIS
                                            || elapsed > 5_000
                                            || !"busy".equals(busy))
                                        throw new AssertionError(
                                                "Request deadline or admission was not enforced");
                                    Files.write(
                                            marker.toPath(),
                                            ("expired\n"
                                                            + workerPid
                                                            + "\n"
                                                            + childPid
                                                            + "\n"
                                                            + elapsed
                                                            + "\nbusy\n")
                                                    .getBytes(StandardCharsets.UTF_8));
                                } catch (Throwable failure) {
                                    try {
                                        Files.write(
                                                marker.toPath(),
                                                "failed\n".getBytes(StandardCharsets.UTF_8));
                                    } catch (Exception ignored) {
                                    }
                                } finally {
                                    WorkerProcesses.terminateWorker();
                                }
                            },
                            SystemClock::elapsedRealtime);
            CountDownLatch stalledCompiler = new CountDownLatch(1);
            admission[0].run(
                    () -> {
                        while (true) {
                            try {
                                stalledCompiler.await();
                            } catch (InterruptedException ignored) {
                                // Models an in-process compiler that cannot be stopped by
                                // interruption.
                            }
                        }
                    },
                    () -> {
                        throw new AssertionError("Initial probe request was busy");
                    });
            throw new AssertionError("Hard deadline returned instead of terminating the worker");
        } finally {
            child.destroyForcibly();
            WorkerProcesses.finished(child);
        }
    }

    static String recover(Context test, Context worker, String id) throws Exception {
        requireWorkerUid(worker);
        File root = root(worker, id);
        File marker = new File(root, "phase.txt");
        try {
            String[] phases =
                    new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8)
                            .split("\n");
            if (phases.length != 5 || !"expired".equals(phases[0]) || !"busy".equals(phases[4]))
                throw new AssertionError("Missing successful request-deadline receipt");
            int originalPid = Integer.parseInt(phases[1]);
            int childPid = Integer.parseInt(phases[2]);
            long elapsed = Long.parseLong(phases[3]);
            if (originalPid == Process.myPid() || elapsed < BUDGET_MILLIS || elapsed > 5_000)
                throw new AssertionError("Worker process did not restart after bounded deadline");
            requireGone(childPid);
            return "Worker deadline: process restarted after "
                    + elapsed
                    + "ms test deadline; active child terminated; admission held until death; "
                    + ProductionSnapshotProbe.run(test, worker);
        } finally {
            Files.deleteIfExists(marker.toPath());
            Files.deleteIfExists(root.toPath());
        }
    }

    private static File root(Context worker, String id) {
        if (id == null || !id.matches("deadline-probe-[0-9a-f]{32}"))
            throw new IllegalArgumentException("A fresh deadline-probe-<32 hex> ID is required");
        return new File(worker.getCacheDir(), id);
    }

    private static void requireWorkerUid(Context worker) {
        if (Process.myUid() != worker.getApplicationInfo().uid)
            throw new AssertionError("Deadline probe must run in the worker UID");
    }

    private static void requireGone(int pid) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                Os.kill(pid, 0);
            } catch (ErrnoException gone) {
                if (gone.errno == OsConstants.ESRCH) return;
                throw gone;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Deadline left its registered child process alive");
    }
}
