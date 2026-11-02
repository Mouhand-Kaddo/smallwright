package dev.smallwright.controller;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises visible project creation, switching, and chat selection on a disposable emulator. */
final class ProjectNavigationProbe {
    private ProjectNavigationProbe() {}

    static void run(Instrumentation instrumentation) throws Exception {
        if (!"ranchu".equals(Build.HARDWARE) && !Build.FINGERPRINT.contains("generic"))
            throw new AssertionError(
                    "Project navigation probe is restricted to a disposable emulator");
        AtomicReference<Activity> resumed = new AtomicReference<>();
        Application application =
                (Application) instrumentation.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks observer =
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle state) {}

                    @Override
                    public void onActivityStarted(Activity activity) {}

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (activity instanceof MainActivity) resumed.set(activity);
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {}

                    @Override
                    public void onActivityStopped(Activity activity) {}

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

                    @Override
                    public void onActivityDestroyed(Activity activity) {}
                };
        application.registerActivityLifecycleCallbacks(observer);
        try {
            Intent launch =
                    new Intent(instrumentation.getTargetContext(), MainActivity.class)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            instrumentation.startActivitySync(launch);
            instrumentation.waitForIdleSync();
            // Instrumentation can create the Activity behind the emulator launcher.
            // Bring that same task forward before using its visible controls.
            try (ParcelFileDescriptor launched =
                            instrumentation
                                    .getUiAutomation()
                                    .executeShellCommand(
                                            "am start -W -n dev.smallwright.controller/.MainActivity"
                                                    + " -f 0x34000000");
                    InputStream commandOutput =
                            new ParcelFileDescriptor.AutoCloseInputStream(launched)) {
                commandOutput.readAllBytes();
            }
            waitWindow(instrumentation, instrumentation.getTargetContext().getPackageName());
            instrumentation.waitForIdleSync();
            Activity activity = resumed.get();
            if (activity == null || activity.isDestroyed() || !activity.hasWindowFocus())
                throw new AssertionError("Could not identify the foreground controller Activity");
            ControllerUi.awaitProjectBinding(instrumentation, activity);
            String title = "Navigation probe " + System.currentTimeMillis();
            ProjectIdentity created;
            String selectedChatId;
            try {
                Control newProject = (Control) field(activity, "newProject");
                if (!newProject.isEnabled())
                    throw new AssertionError("New project is disabled at idle");
                instrumentation.runOnMainSync(() -> Ui.click(newProject));
                AccessibilityNodeInfo input = waitClass(instrumentation, "android.widget.EditText");
                Bundle text = new Bundle();
                text.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
                if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                    throw new AssertionError(
                            "Project name was not editable: node package="
                                    + input.getPackageName()
                                    + ", hint="
                                    + input.getHintText()
                                    + ", enabled="
                                    + input.isEnabled()
                                    + ", editable="
                                    + input.isEditable()
                                    + ", focused="
                                    + input.isFocused());
                clickText(instrumentation, "Create");
                instrumentation.waitForIdleSync();
                ControllerUi.awaitProjectBinding(instrumentation, activity);
                created = (ProjectIdentity) field(activity, "activeProject");
                if (!title.equals(created.title()))
                    throw new AssertionError(
                            "Visible New project action selected the wrong project");
                Picker chats = (Picker) field(activity, "chatPicker");
                if (chats.getCount() != 1)
                    throw new AssertionError("New project lacks its own first chat");
                Control addChat = (Control) field(activity, "newChat");
                instrumentation.runOnMainSync(() -> Ui.click(addChat));
                instrumentation.waitForIdleSync();
                if (chats.getCount() != 2 || chats.getSelectedItemPosition() != 0)
                    throw new AssertionError("Second project chat was not selected");
                selectedChatId = ((ChatStore.Chat) field(activity, "selectedChat")).id;
                selectProject(instrumentation, activity, "counter-alpha");
                if (!"counter-alpha"
                        .equals(((ProjectIdentity) field(activity, "activeProject")).id()))
                    throw new AssertionError("Project picker did not switch to the legacy project");
                selectProject(instrumentation, activity, created.id());
                if (!created.id().equals(((ProjectIdentity) field(activity, "activeProject")).id())
                        || chats.getCount() != 2
                        || !selectedChatId.equals(
                                ((ChatStore.Chat) field(activity, "selectedChat")).id))
                    throw new AssertionError("Switching projects lost the selected chat");
            } finally {
                instrumentation.runOnMainSync(activity::finish);
            }

            Activity reopened =
                    instrumentation.startActivitySync(
                            new Intent(instrumentation.getTargetContext(), MainActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            try {
                ControllerUi.awaitProjectBinding(instrumentation, reopened);
                Picker chats = (Picker) field(reopened, "chatPicker");
                if (!created.id().equals(((ProjectIdentity) field(reopened, "activeProject")).id())
                        || chats.getCount() != 2
                        || !selectedChatId.equals(
                                ((ChatStore.Chat) field(reopened, "selectedChat")).id))
                    throw new AssertionError("Project or selected chat did not survive reopen");
            } finally {
                instrumentation.runOnMainSync(reopened::finish);
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(observer);
        }
    }

    private static void selectProject(
            Instrumentation instrumentation, Activity activity, String projectId) throws Exception {
        Picker picker = (Picker) field(activity, "projectPicker");
        @SuppressWarnings("unchecked")
        List<ProjectIdentity> projects = (List<ProjectIdentity>) field(activity, "projectList");
        int position = -1;
        for (int index = 0; index < projects.size(); index++)
            if (projectId.equals(projects.get(index).id())) position = index;
        if (position < 0 || !picker.isEnabled())
            throw new AssertionError("Target project is unavailable in picker");
        int selected = position;
        instrumentation.runOnMainSync(() -> picker.select(selected));
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
    }

    private static AccessibilityNodeInfo waitClass(
            Instrumentation instrumentation, String className) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo found =
                    find(root, null, "Create") == null ? null : find(root, className, null);
            if (found != null) return found;
            Thread.sleep(200);
        }
        throw new AssertionError("Project name dialog did not open");
    }

    private static void waitWindow(Instrumentation instrumentation, String packageName)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null && packageName.contentEquals(root.getPackageName())) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Controller did not reach the foreground");
    }

    private static void clickText(Instrumentation instrumentation, String label)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo node = find(root, null, label);
            for (AccessibilityNodeInfo target = node; target != null; target = target.getParent()) {
                if (target.isClickable()
                        && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Project dialog action missing: " + label);
    }

    private static AccessibilityNodeInfo find(
            AccessibilityNodeInfo node, String className, String text) {
        if (node == null) return null;
        if (className != null && className.contentEquals(node.getClassName())) return node;
        if (text != null
                && node.getText() != null
                && text.equalsIgnoreCase(node.getText().toString())) return node;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo found = find(node.getChild(index), className, text);
            if (found != null) return found;
        }
        return null;
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
