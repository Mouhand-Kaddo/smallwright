package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** UI idleness alone does not wait for asynchronous project preparation. */
final class ControllerUi {
    static void awaitProjectBinding(Instrumentation instrumentation, Activity activity)
            throws Exception {
        Field pending = MainActivity.class.getDeclaredField("projectBindingInProgress");
        pending.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            AtomicBoolean complete = new AtomicBoolean();
            instrumentation.runOnMainSync(
                    () -> {
                        try {
                            complete.set(!pending.getBoolean(activity));
                        } catch (IllegalAccessException failure) {
                            throw new AssertionError(failure);
                        }
                    });
            if (complete.get()) {
                instrumentation.waitForIdleSync();
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Controller did not finish project preparation");
    }
}
