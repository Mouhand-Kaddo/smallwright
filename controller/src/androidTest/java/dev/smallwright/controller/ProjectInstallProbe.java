package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable emulator proof of the controller's explicit PackageInstaller path. */
final class ProjectInstallProbe {
    private static final String NOTE_SOURCE =
            "package %s;"
                    + " public final class MainActivity extends android.app.Activity {"
                    + " @Override public void onCreate(android.os.Bundle state) {"
                    + " super.onCreate(state);"
                    + " android.widget.EditText note = new android.widget.EditText(this);"
                    + " note.setInputType(android.text.InputType.TYPE_CLASS_TEXT"
                    + " | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);"
                    + " note.setText(getPreferences(0).getString(\"note\", \"\"));"
                    + " note.addTextChangedListener(new android.text.TextWatcher() {"
                    + " @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}"
                    + " @Override public void onTextChanged(CharSequence s,int start,int before,int count) {"
                    + " getPreferences(0).edit().putString(\"note\",s.toString()).commit(); }"
                    + " @Override public void afterTextChanged(android.text.Editable value) {} });"
                    + " setContentView(note); } }";

    private ProjectInstallProbe() {}

    static String run(Instrumentation instrumentation) throws Exception {
        return run(instrumentation, false);
    }

    static String runNote(Instrumentation instrumentation) throws Exception {
        return run(instrumentation, true);
    }

    private static String run(Instrumentation instrumentation, boolean note) throws Exception {
        if (!"ranchu".equals(Build.HARDWARE) && !Build.FINGERPRINT.contains("generic"))
            throw new AssertionError(
                    "Installer journey probe is restricted to a disposable emulator");
        Context target = instrumentation.getTargetContext();
        ProjectRegistry registry =
                new ProjectRegistry(
                        new File(target.getFilesDir(), "projects"), target.getFilesDir());
        ProjectIdentity project =
                registry.create(note ? "Note journey probe" : "Install journey probe");
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String noteSource =
                String.format(java.util.Locale.ROOT, NOTE_SOURCE, project.packageName());
        int sourceRevision = note ? source.writeSource(0, noteSource) : 0;
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> worker = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        worker.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        worker.set(null);
                    }
                };
        Intent bind =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(bind, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || worker.get() == null)
                throw new AssertionError("Worker unavailable");
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(target, source, project)
                            .buildCommittedRevision(sourceRevision, 1, worker.get());
            SignedArtifactReceipt.publish(target, project, sourceRevision, 1, artifact);
            artifact.verifyUnchanged();
        } finally {
            target.unbindService(connection);
        }

        Activity activity =
                instrumentation.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity[] active = {activity};
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        try {
            Method select =
                    MainActivity.class.getDeclaredMethod("selectProject", ProjectIdentity.class);
            select.setAccessible(true);
            instrumentation.runOnMainSync(
                    () -> {
                        try {
                            select.invoke(activity, project);
                        } catch (Exception failure) {
                            throw new AssertionError("Project selection failed", failure);
                        }
                    });
            Control install = (Control) field(activity, "install");
            waitEnabled(instrumentation, install);
            boolean neededSourceApproval = !target.getPackageManager().canRequestPackageInstalls();
            instrumentation.runOnMainSync(() -> Ui.click(install));
            if (neededSourceApproval) {
                if (!clickText(instrumentation, "Allow from this source", 15000))
                    throw new AssertionError(
                            "Android unknown-source switch inaccessible: "
                                    + describeWindow(instrumentation, "Allow from this source"));
                long approvalDeadline = System.currentTimeMillis() + 10000;
                while (!target.getPackageManager().canRequestPackageInstalls()
                        && System.currentTimeMillis() < approvalDeadline) Thread.sleep(200);
                if (!target.getPackageManager().canRequestPackageInstalls())
                    throw new AssertionError(
                            "Android unknown-source switch did not grant install access");
                instrumentation
                        .getUiAutomation()
                        .performGlobalAction(
                                android.accessibilityservice.AccessibilityService
                                        .GLOBAL_ACTION_BACK);
                // Settings is a separate task on this emulator. Relaunching the app
                // models the user returning from the launcher after approval.
                try (ParcelFileDescriptor launched =
                                instrumentation
                                        .getUiAutomation()
                                        .executeShellCommand(
                                                "am start -n dev.smallwright.controller/.MainActivity"
                                                        + " -f 0x10000000");
                        InputStream commandOutput =
                                new ParcelFileDescriptor.AutoCloseInputStream(launched)) {
                    drain(commandOutput);
                }
                Activity reopened =
                        instrumentation.startActivitySync(
                                new Intent(target, MainActivity.class)
                                        .addFlags(
                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                active[0] = reopened;
                instrumentation.waitForIdleSync();
                ControllerUi.awaitProjectBinding(instrumentation, reopened);
                waitWindow(instrumentation, target.getPackageName());
                Control reopenedInstall = (Control) field(reopened, "install");
                waitEnabled(instrumentation, reopenedInstall);
                if (!target.getPackageManager().canRequestPackageInstalls())
                    throw new AssertionError("Install access was lost on return from Settings");
                instrumentation.runOnMainSync(() -> Ui.click(reopenedInstall));
            }
            long deadline = System.currentTimeMillis() + 30000;
            boolean clicked = false;
            while (System.currentTimeMillis() < deadline) {
                if (installed(target, project.packageName())) break;
                if (!clicked) clicked = clickText(instrumentation, "Install", 1000);
                Thread.sleep(250);
            }
            if (!clicked || !installed(target, project.packageName()))
                throw new AssertionError(
                        "Explicit installer confirmation did not install the generated app: "
                                + "clicked="
                                + clicked
                                + ", project="
                                + ((ProjectIdentity) field(active[0], "activeProject"))
                                        .packageName()
                                + ", status="
                                + ((TextBox) field(active[0], "status")).getText()
                                + ", window="
                                + describeWindow(instrumentation, "Install"));
            long statusDeadline = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < statusDeadline
                    && !((TextBox) field(active[0], "status"))
                            .getText()
                            .toString()
                            .contains("Installation completed.")) Thread.sleep(200);
            if (!((TextBox) field(active[0], "status"))
                    .getText()
                    .toString()
                    .contains("Installation completed."))
                throw new AssertionError(
                        "Installed package lacked the completion callback: "
                                + ((TextBox) field(active[0], "status")).getText());
            try (ParcelFileDescriptor launched =
                            instrumentation
                                    .getUiAutomation()
                                    .executeShellCommand(
                                            "am start -n "
                                                    + project.packageName()
                                                    + "/.MainActivity -f 0x10000000");
                    InputStream commandOutput =
                            new ParcelFileDescriptor.AutoCloseInputStream(launched)) {
                drain(commandOutput);
            }
            waitWindow(instrumentation, project.packageName());
            if (note) {
                String expected = "Note saved by local emulator";
                AccessibilityNodeInfo editor =
                        waitClass(
                                instrumentation,
                                "android.widget.EditText",
                                project.packageName(),
                                5000);
                android.os.Bundle text = new android.os.Bundle();
                text.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, expected);
                if (!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                    throw new AssertionError("Installed note editor refused text input");
                try (ParcelFileDescriptor stopped =
                                instrumentation
                                        .getUiAutomation()
                                        .executeShellCommand(
                                                "am force-stop " + project.packageName());
                        InputStream commandOutput =
                                new ParcelFileDescriptor.AutoCloseInputStream(stopped)) {
                    drain(commandOutput);
                }
                try (ParcelFileDescriptor launched =
                                instrumentation
                                        .getUiAutomation()
                                        .executeShellCommand(
                                                "am start -n "
                                                        + project.packageName()
                                                        + "/.MainActivity -f 0x10000000");
                        InputStream commandOutput =
                                new ParcelFileDescriptor.AutoCloseInputStream(launched)) {
                    drain(commandOutput);
                }
                waitWindow(instrumentation, project.packageName());
                if (!waitText(instrumentation, expected, 5000))
                    throw new AssertionError("Installed native note did not survive restart");
                return "Note app: controller-to-worker build/sign, explicit install, "
                        + "native text edit and restart persistence passed\n";
            }
            if (!waitText(instrumentation, "Counter: 0", 5000)
                    || !clickText(instrumentation, "Increment", 5000)
                    || !waitText(instrumentation, "Counter: 1", 5000))
                throw new AssertionError("Installed generated app did not open and increment");
            return "New project: phone-local build/sign, explicit Android installer confirmation, "
                    + "installed package launch and native button passed\n";
        } finally {
            instrumentation.runOnMainSync(
                    () -> {
                        active[0].finish();
                        if (active[0] != activity) activity.finish();
                    });
        }
    }

    private static boolean clickText(
            Instrumentation instrumentation, String text, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo node = findText(root, text);
                for (AccessibilityNodeInfo target = node;
                        target != null;
                        target = target.getParent()) {
                    if (target.isClickable()
                            && target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        return true;
                }
            }
            Thread.sleep(200);
        }
        return false;
    }

    private static String describeWindow(Instrumentation instrumentation, String text) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return "no active window";
        return String.valueOf(root.getPackageName())
                + " matching nodes "
                + root.findAccessibilityNodeInfosByText(text).size()
                + " recursive "
                + (findText(root, text) != null);
    }

    private static void waitWindow(Instrumentation instrumentation, String packageName)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null && packageName.contentEquals(root.getPackageName())) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Controller did not return to the foreground after Settings");
    }

    private static boolean waitText(
            Instrumentation instrumentation, String text, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null && findText(root, text) != null) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private static AccessibilityNodeInfo waitClass(
            Instrumentation instrumentation,
            String className,
            String packageName,
            long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo node = findClass(root, className);
            if (node != null && packageName.contentEquals(node.getPackageName())) return node;
            Thread.sleep(200);
        }
        throw new AssertionError("Installed native editor did not appear");
    }

    private static AccessibilityNodeInfo findClass(AccessibilityNodeInfo root, String className) {
        if (root == null) return null;
        if (className.contentEquals(root.getClassName())) return root;
        for (int index = 0; index < root.getChildCount(); index++) {
            AccessibilityNodeInfo found = findClass(root.getChild(index), className);
            if (found != null) return found;
        }
        return null;
    }

    private static AccessibilityNodeInfo findText(AccessibilityNodeInfo root, String text) {
        if (root.getText() != null && text.equalsIgnoreCase(root.getText().toString())) return root;
        for (int index = 0; index < root.getChildCount(); index++) {
            AccessibilityNodeInfo child = root.getChild(index);
            if (child == null) continue;
            AccessibilityNodeInfo found = findText(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[256];
        while (input.read(buffer) != -1) {
            /* Wait for the fixed command to finish. */
        }
    }

    private static void waitEnabled(Instrumentation instrumentation, Control control)
            throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (!PhoneAlphaCounterProbe.mainValue(instrumentation, control::isEnabled)) {
            if (System.nanoTime() > deadline) throw new AssertionError("Action stayed disabled");
            Thread.sleep(50);
        }
    }

    private static void waitEnabled(Instrumentation instrumentation, Button button)
            throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            instrumentation.runOnMainSync(() -> ready.set(button.isEnabled()));
            if (ready.get()) return;
            Thread.sleep(250);
        }
        throw new AssertionError("Verified Install control did not become enabled");
    }

    private static boolean installed(Context context, String packageName) {
        try {
            return context.getPackageManager().getPackageInfo(packageName, 0).getLongVersionCode()
                    == 1;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }
}
