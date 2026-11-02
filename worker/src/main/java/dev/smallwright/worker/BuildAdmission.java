package dev.smallwright.worker;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class BuildAdmission {
    private final AtomicBoolean active = new AtomicBoolean();
    private final long timeoutMillis;
    private final Runnable terminateWorker;
    private final LongSupplier clockMillis;

    BuildAdmission(long timeoutMillis, Runnable terminateWorker) {
        this(timeoutMillis, terminateWorker, () -> System.nanoTime() / 1_000_000L);
    }

    BuildAdmission(long timeoutMillis, Runnable terminateWorker, LongSupplier clockMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Invalid request deadline");
        this.timeoutMillis = timeoutMillis;
        this.terminateWorker = Objects.requireNonNull(terminateWorker);
        this.clockMillis = Objects.requireNonNull(clockMillis);
    }

    <T> T run(Supplier<T> job, Supplier<T> busy) {
        if (!active.compareAndSet(false, true)) return busy.get();
        Deadline deadline = new Deadline();
        try {
            deadline.start();
            T result = job.get();
            deadline.finish();
            return result;
        } finally {
            deadline.cancel();
            // A hard-expired process is quarantined even if termination unexpectedly returns.
            // Neither a late compiler result nor a subsequent request may escape that process.
            if (!deadline.expired) active.set(false);
        }
    }

    private final class Deadline {
        private final long end = clockMillis.getAsLong() + timeoutMillis;
        private boolean finished;
        private volatile boolean expired;

        void start() {
            Thread watchdog = new Thread(this::awaitDeadline, "worker-request-deadline");
            watchdog.setDaemon(true);
            watchdog.start();
        }

        private void awaitDeadline() {
            boolean terminate = false;
            synchronized (this) {
                while (!finished) {
                    long remaining = end - clockMillis.getAsLong();
                    if (remaining <= 0) {
                        expired = true;
                        finished = true;
                        terminate = true;
                        break;
                    }
                    try {
                        wait(remaining);
                    } catch (InterruptedException ignored) {
                        // Interrupting a timer is not permission to abandon the request deadline.
                    }
                }
                if (!terminate) return;
            }
            terminateWorker.run();
        }

        void finish() {
            cancel();
            if (expired) throw new IllegalStateException("REQUEST_TIMEOUT");
        }

        void cancel() {
            boolean terminate = false;
            synchronized (this) {
                if (!finished) {
                    expired = clockMillis.getAsLong() >= end;
                    terminate = expired;
                    finished = true;
                    notifyAll();
                }
            }
            if (terminate) terminateWorker.run();
        }
    }
}
