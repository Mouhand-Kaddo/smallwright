package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class BoundedProcessTest {
    @Test
    public void returnsOutputFromACompletedProcess() {
        FakeProcess process =
                new FakeProcess(new ByteArrayInputStream("AAPT2\n".getBytes()), false, 0);

        assertEquals("AAPT2", BuildService.runBounded(process, 100, 64));
    }

    @Test
    public void capturesOutputFromARealCompletedChildProcess() throws Exception {
        Process process = completedJavaVersionProcess();

        assertTrue(BuildService.runBounded(process, 5_000, 4_096).contains("version"));
    }

    @Test
    public void rejectsOutputOverflowFromARealCompletedChildProcess() throws Exception {
        Process process = completedJavaVersionProcess();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> BuildService.runBounded(process, 5_000, 1));

        assertTrue(exception.getCause().getMessage().contains("exceeds limit"));
    }

    private static Process completedJavaVersionProcess() throws Exception {
        File java = new File(System.getProperty("java.home"), "bin/java");
        Process process =
                new ProcessBuilder(java.getAbsolutePath(), "-version")
                        .redirectErrorStream(true)
                        .start();
        try {
            assertTrue("Java version child did not exit", process.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            return process;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    public void terminatesAProcessThatKeepsItsOutputOpen() {
        FakeProcess process = new FakeProcess(new BlockingInputStream(), true, 0);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> BuildService.runBounded(process, 20, 64));

        assertEquals("Packaged AAPT2 timed out", exception.getMessage());
        assertTrue(process.destroyed);
    }

    @Test
    public void terminatesAProcessWhoseOutputExceedsTheLimit() {
        FakeProcess process = new FakeProcess(new ByteArrayInputStream(new byte[65]), true, 0);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> BuildService.runBounded(process, 100, 64));

        assertTrue(exception.getCause().getMessage().contains("exceeds limit"));
        assertTrue(process.destroyed);
    }

    @Test
    public void rejectsOutputOverflowDiscoveredAfterTheProcessExited() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch releaseOutput = new CountDownLatch(1);
        InputStream delayedOutput =
                new ByteArrayInputStream(new byte[65]) {
                    @Override
                    public synchronized int read(byte[] buffer, int offset, int length) {
                        reading.countDown();
                        try {
                            if (!releaseOutput.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("Test did not release process output");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                        return super.read(buffer, offset, length);
                    }
                };
        FakeProcess process = new FakeProcess(delayedOutput, false, 0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller =
                new Thread(
                        () -> {
                            try {
                                BuildService.runBounded(process, 5_000, 64);
                            } catch (Throwable exception) {
                                failure.set(exception);
                            }
                        });
        caller.start();
        try {
            assertTrue("Output reader did not start", reading.await(5, TimeUnit.SECONDS));
            // Release stdout only once the exited process's caller waits for its reader.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (caller.isAlive()
                    && caller.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertEquals(Thread.State.TIMED_WAITING, caller.getState());
        } finally {
            releaseOutput.countDown();
            caller.join(5_000);
        }
        assertTrue(
                "Process output overflow was accepted",
                failure.get() instanceof IllegalStateException);
        assertTrue(failure.get().getCause().getMessage().contains("exceeds limit"));
    }

    @Test
    public void interruptCancelsAptProcessAndPreservesInterruptFlag() {
        FakeProcess process = new FakeProcess(new BlockingInputStream(), true, 0);
        Thread.currentThread().interrupt();
        try {
            IllegalStateException exception =
                    assertThrows(
                            IllegalStateException.class,
                            () -> BuildService.runBounded(process, 10_000, 64));
            assertEquals("Packaged AAPT2 was interrupted", exception.getMessage());
            assertTrue(process.destroyed);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class FakeProcess extends Process {
        private final InputStream input;
        private boolean alive;
        private final int exitCode;
        private boolean destroyed;

        FakeProcess(InputStream input, boolean alive, int exitCode) {
            this.input = input;
            this.alive = alive;
            this.exitCode = exitCode;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            alive = false;
            return this;
        }

        @Override
        public void destroy() {
            destroyForcibly();
        }
    }

    private static final class BlockingInputStream extends InputStream {
        @Override
        public int read() throws IOException {
            try {
                Thread.sleep(Long.MAX_VALUE);
                return -1;
            } catch (InterruptedException exception) {
                throw new IOException("interrupted", exception);
            }
        }
    }
}
