package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.TextBox;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proves Stop prevents a queued local build from committing source or producing an APK, and that
 * leaving the screen does not stop it.
 */
final class StopUiProbe {
    static void stopBeforeQueuedBuild(Instrumentation instrumentation) throws Exception {
        queuedBuild(instrumentation, false);
    }

    static void backgroundKeepsQueuedBuild(Instrumentation instrumentation) throws Exception {
        queuedBuild(instrumentation, true);
    }

    private static void queuedBuild(Instrumentation instrumentation, boolean background)
            throws Exception {
        Intent intent =
                new Intent(instrumentation.getTargetContext(), ProjectBindingProbeActivity.class)
                        .putExtra(
                                "bindingProbeId",
                                java.util.UUID.randomUUID().toString().replace("-", ""))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        CountDownLatch release = new CountDownLatch(1);
        try {
            File root =
                    Files.createTempDirectory(activity.getCacheDir().toPath(), "stop-ui-probe-")
                            .toFile();
            ProjectSourceStore isolated = new ProjectSourceStore(root);
            isolated.initialize();
            AtomicBoolean connected = new AtomicBoolean();
            for (int attempt = 0; attempt < 50 && !connected.get(); attempt++) {
                instrumentation.runOnMainSync(
                        () -> {
                            try {
                                connected.set(field(activity, "worker") != null);
                            } catch (Exception failure) {
                                throw new AssertionError(failure);
                            }
                        });
                if (!connected.get()) SystemClock.sleep(100);
            }
            if (!connected.get() || field(activity, "pendingRecovery") != null)
                throw new AssertionError("Controller was not idle with its worker connected");
            ExecutorService executor = (ExecutorService) field(activity, "executor");
            CountDownLatch parked = new CountDownLatch(1);
            executor.execute(
                    () -> {
                        parked.countDown();
                        try {
                            release.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    });
            if (!parked.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Executor did not park");
            instrumentation.runOnMainSync(
                    () -> {
                        try {
                            setField(activity, "sourceStore", isolated);
                            setField(activity, "editorRevision", 0);
                            ((TextBox) field(activity, "source")).setText(isolated.readSource());
                            Control build = Ui.button(activity, Labels.BUILD_SOURCE);
                            Control stop = Ui.button(activity, Labels.STOP);
                            if (!build.isEnabled())
                                throw new AssertionError("Local build controls unavailable");
                            Ui.click(build);
                            if (!stop.isEnabled())
                                throw new AssertionError("Stop was not offered for queued build");
                            if (background) {
                                if (!activity.moveTaskToBack(true))
                                    throw new AssertionError("Could not background controller");
                            } else {
                                Ui.click(stop);
                                if (stop.isEnabled())
                                    throw new AssertionError("Stop stayed enabled after request");
                            }
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    });
            if (background) {
                SystemClock.sleep(3000);
                AtomicBoolean kept = new AtomicBoolean();
                instrumentation.runOnMainSync(
                        () -> {
                            Control stop = control(activity, Labels.STOP);
                            kept.set(stop.isEnabled());
                            if (kept.get()) Ui.click(stop);
                        });
                if (!kept.get())
                    throw new AssertionError("Leaving the screen stopped the queued build");
            }
            release.countDown();
            boolean done = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                AtomicBoolean busy = new AtomicBoolean(true);
                instrumentation.runOnMainSync(
                        () -> {
                            try {
                                busy.set((boolean) field(activity, "buildInProgress"));
                            } catch (Exception failure) {
                                throw new AssertionError(failure);
                            }
                        });
                if (!busy.get()) {
                    done = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            if (!done || isolated.revision() != 0 || field(activity, "signedArtifact") != null)
                throw new AssertionError("Stopped queued build changed source or offered an APK");
        } finally {
            release.countDown();
            activity.finish();
        }
    }

    private static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    private static void setField(Activity activity, String name, Object value) throws Exception {
        Ui.setField(activity, name, value);
    }

    private static Control control(Activity activity, String label) {
        try {
            return Ui.button(activity, label);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
