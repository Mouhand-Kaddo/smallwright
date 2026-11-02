package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class BuildAdmissionTest {
    @Test
    public void deadlineRequestsTerminationWithoutReleasingRunningCompilerAdmission()
            throws Exception {
        CountDownLatch terminate = new CountDownLatch(1);
        BuildAdmission admission = new BuildAdmission(40, terminate::countDown);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread compiler =
                new Thread(
                        () -> {
                            try {
                                admission.run(
                                        () -> {
                                            started.countDown();
                                            try {
                                                if (!finish.await(5, TimeUnit.SECONDS))
                                                    throw new AssertionError("timed out");
                                            } catch (InterruptedException unexpected) {
                                                throw new AssertionError(
                                                        "Compiler interruption is not process termination",
                                                        unexpected);
                                            }
                                            return "late artifact";
                                        },
                                        () -> "busy");
                                failure.set(
                                        new AssertionError("Expired request returned an artifact"));
                            } catch (Throwable thrown) {
                                failure.set(thrown);
                            }
                        });
        compiler.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(terminate.await(5, TimeUnit.SECONDS));
            assertTrue(compiler.isAlive());
            assertEquals("busy", admission.run(() -> "unsafe overlapping build", () -> "busy"));
        } finally {
            finish.countDown();
            compiler.join(5000);
        }
        assertFalse(compiler.isAlive());
        assertTrue(failure.get() instanceof IllegalStateException);
        assertEquals("REQUEST_TIMEOUT", failure.get().getMessage());
        assertEquals("busy", admission.run(() -> "late request", () -> "busy"));
        BuildAdmission restarted =
                new BuildAdmission(
                        5_000,
                        () -> {
                            throw new AssertionError("Fresh worker unexpectedly expired");
                        });
        assertEquals("recovered", restarted.run(() -> "recovered", () -> "busy"));
    }

    @Test
    public void concurrentRequestReturnsBusyWithoutRunningItsJob() throws Exception {
        BuildAdmission admission =
                new BuildAdmission(
                        5_000,
                        () -> {
                            throw new AssertionError(
                                    "Concurrent request test unexpectedly expired");
                        });
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<String> firstResult = new AtomicReference<>();
        Thread first =
                new Thread(
                        () ->
                                firstResult.set(
                                        admission.run(
                                                () -> {
                                                    started.countDown();
                                                    try {
                                                        if (!finish.await(5, TimeUnit.SECONDS))
                                                            throw new AssertionError("timed out");
                                                    } catch (InterruptedException exception) {
                                                        Thread.currentThread().interrupt();
                                                        throw new AssertionError(exception);
                                                    }
                                                    return "first";
                                                },
                                                () -> "busy")));
        first.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(
                    "busy",
                    admission.run(
                            () -> {
                                throw new AssertionError("concurrent job ran");
                            },
                            () -> "busy"));
        } finally {
            finish.countDown();
            first.join(5000);
        }
        assertFalse(first.isAlive());
        assertEquals("first", firstResult.get());
        assertEquals("next", admission.run(() -> "next", () -> "busy"));
    }

    @Test
    public void failedJobReleasesAdmission() {
        BuildAdmission admission =
                new BuildAdmission(
                        5_000,
                        () -> {
                            throw new AssertionError("Failed request test unexpectedly expired");
                        });
        assertThrows(
                IllegalStateException.class,
                () ->
                        admission.run(
                                () -> {
                                    throw new IllegalStateException("failed");
                                },
                                () -> "busy"));
        assertEquals("recovered", admission.run(() -> "recovered", () -> "busy"));
    }
}
