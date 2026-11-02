package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Delays real startup recovery without touching existing source, keys or journal state. */
final class ProjectBindingResponsivenessProbe {
    static String run(Instrumentation instrumentation) throws Exception {
        Field field = MainActivity.class.getDeclaredField("RECOVERY_TASKS");
        field.setAccessible(true);
        ExecutorService recovery = (ExecutorService) field.get(null);
        CountDownLatch queued = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch pulse = new CountDownLatch(1);
        ProjectBindingProbeActivity.recoveryBlocked = release;
        ProjectBindingProbeActivity.heartbeat = pulse;
        ProjectBindingProbeActivity.heartbeatDuringRecovery = false;
        recovery.execute(
                () -> {
                    queued.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                    } finally {
                        release.countDown();
                    }
                });
        if (!queued.await(5, TimeUnit.SECONDS))
            throw new AssertionError("Recovery queue unavailable");
        Activity activity = null;
        String id = UUID.randomUUID().toString().replace("-", "");
        Intent launch =
                new Intent(instrumentation.getTargetContext(), ProjectBindingProbeActivity.class)
                        .putExtra("bindingProbeId", id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity = instrumentation.startActivitySync(launch);
            if (!pulse.await(5, TimeUnit.SECONDS)
                    || !ProjectBindingProbeActivity.heartbeatDuringRecovery)
                throw new AssertionError("Controller main thread waited for blocked recovery");
            Field blocked = MainActivity.class.getDeclaredField("startupBlocked");
            blocked.setAccessible(true);
            Activity opened = activity;
            instrumentation.runOnMainSync(
                    () -> {
                        try {
                            if (!blocked.getBoolean(opened))
                                throw new AssertionError("Controls exposed before recovery");
                        } catch (IllegalAccessException failure) {
                            throw new AssertionError(failure);
                        }
                    });
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                java.util.concurrent.atomic.AtomicBoolean ready =
                        new java.util.concurrent.atomic.AtomicBoolean();
                instrumentation.runOnMainSync(
                        () -> {
                            try {
                                ready.set(!blocked.getBoolean(opened));
                            } catch (IllegalAccessException failure) {
                                throw new AssertionError(failure);
                            }
                        });
                if (ready.get()) {
                    ControllerUi.awaitProjectBinding(instrumentation, activity);
                    assertSelected(activity, "counter-alpha");
                    ProjectRegistry projects = (ProjectRegistry) value(activity, "projects");
                    ProjectIdentity second = projects.create("Asynchronous binding probe");
                    Method refresh = MainActivity.class.getDeclaredMethod("refreshProjectPicker");
                    refresh.setAccessible(true);
                    instrumentation.runOnMainSync(
                            () -> {
                                try {
                                    refresh.invoke(opened);
                                } catch (Exception failure) {
                                    throw new AssertionError(failure);
                                }
                            });
                    instrumentation.waitForIdleSync();
                    select(instrumentation, activity, second.id());
                    assertSelected(activity, second.id());
                    select(instrumentation, activity, "counter-alpha");
                    assertSelected(activity, "counter-alpha");
                    select(instrumentation, activity, second.id());
                    assertSelected(activity, second.id());
                    instrumentation.runOnMainSync(opened::finish);
                    instrumentation.waitForIdleSync();
                    activity = instrumentation.startActivitySync(launch);
                    ControllerUi.awaitProjectBinding(instrumentation, activity);
                    assertSelected(activity, second.id());
                    return "Project binding: main thread responsive during delayed recovery; "
                            + "controls blocked until preparation; two disposable project selections "
                            + "kept source identity and survived activity reopen\n";
                }
                Thread.sleep(25);
            }
            throw new AssertionError("Prepared project did not become ready");
        } finally {
            release.countDown();
            ProjectBindingProbeActivity.recoveryBlocked = null;
            ProjectBindingProbeActivity.heartbeat = null;
            if (activity != null) {
                Activity opened = activity;
                instrumentation.runOnMainSync(opened::finish);
            }
        }
    }

    private static void select(Instrumentation instrumentation, Activity activity, String id)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<ProjectIdentity> projects = (List<ProjectIdentity>) value(activity, "projectList");
        int selected = -1;
        for (int index = 0; index < projects.size(); index++)
            if (id.equals(projects.get(index).id())) selected = index;
        if (selected < 0) throw new AssertionError("Disposable project absent from picker");
        Picker picker = (Picker) value(activity, "projectPicker");
        int position = selected;
        instrumentation.runOnMainSync(
                () -> {
                    if (!picker.isEnabled())
                        throw new AssertionError("Prepared picker is disabled");
                    picker.select(position);
                });
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
    }

    private static void assertSelected(Activity activity, String id) throws Exception {
        ProjectIdentity selected = (ProjectIdentity) value(activity, "activeProject");
        ProjectSourceStore source = (ProjectSourceStore) value(activity, "sourceStore");
        if (selected == null
                || !id.equals(selected.id())
                || !id.equals(source.projectId())
                || !selected.packageName().equals(source.packageName()))
            throw new AssertionError("Asynchronous binding mixed project and source identities");
    }

    private static Object value(Activity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
}
