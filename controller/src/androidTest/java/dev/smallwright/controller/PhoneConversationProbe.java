package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

/** Physical acceptance using a new, explicitly named test project; never reads a key. */
final class PhoneConversationProbe {
    private static final String PREFS = "phone-conversation-probe";
    private static final String REQUEST =
            "Change the Increment button label to Add one. "
                    + "Preserve the counter, saved count, and crash report features. "
                    + "Change only that label in the existing Java source; keep its package and APIs.";

    static String run(Instrumentation instrumentation, String phase, boolean freeApproved)
            throws Exception {
        Context target = instrumentation.getTargetContext();
        SharedPreferences state = target.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Activity activity =
                instrumentation.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        try (ParcelFileDescriptor launched =
                        instrumentation
                                .getUiAutomation()
                                .executeShellCommand(
                                        "am start -W -n dev.smallwright.controller/.MainActivity -f 0x34000000");
                InputStream output = new ParcelFileDescriptor.AutoCloseInputStream(launched)) {
            output.readAllBytes();
        }
        instrumentation.waitForIdleSync();
        try {
            if ("prepare".equals(phase)) {
                String title = "Phone acceptance " + System.currentTimeMillis();
                onMain(instrumentation, () -> Ui.click(button(activity, Labels.NEW_PROJECT)));
                AccessibilityNodeInfo input =
                        waitNode(instrumentation, "android.widget.EditText", null);
                Bundle text = new Bundle();
                text.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, title);
                if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                    throw new AssertionError("Test project title could not be entered");
                click(instrumentation, "Create");
                instrumentation.waitForIdleSync();
                ControllerUi.awaitProjectBinding(instrumentation, activity);
                ProjectIdentity project = mainValue(instrumentation, () -> project(activity));
                if (!title.equals(project.title()))
                    throw new AssertionError("Test project was not created");
                onMain(instrumentation, () -> Ui.click(button(activity, Labels.NEW_CHAT)));
                instrumentation.waitForIdleSync();
                if (mainValue(
                                instrumentation,
                                () -> ((Picker) field(activity, "chatPicker")).getCount())
                        != 2) throw new AssertionError("Test project chats are missing");
                ChatStore.Chat chat =
                        mainValue(
                                instrumentation,
                                () -> (ChatStore.Chat) field(activity, "selectedChat"));
                if (!state.edit()
                        .putString("project", project.id())
                        .putString("chat", chat.id)
                        .commit())
                    throw new AssertionError("Could not persist test project reference");
                return "Phone conversation prepared: project="
                        + project.id()
                        + " package="
                        + project.packageName()
                        + " chats=2 revision=0\n";
            }
            onMain(instrumentation, () -> requirePreparedSelection(activity, state));
            if ("prepareUpdate".equals(phase)) {
                ProjectIdentity project = mainValue(instrumentation, () -> project(activity));
                ProjectSourceStore source = new ProjectSourceStore(project);
                int revision = source.revision();
                VerifiedArtifact previous =
                        SignedArtifactReceipt.restore(target, project, revision);
                if (revision != state.getInt("revision", -1)
                        || previous == null
                        || !previous.sha256().equals(state.getString("sha256", "")))
                    throw new AssertionError(
                            "Only the original successful live build may be updated");
                android.content.pm.PackageInfo installed =
                        target.getPackageManager()
                                .getPackageInfo(
                                        project.packageName(),
                                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (installed.getLongVersionCode() != state.getLong("version", -1))
                    throw new AssertionError(
                            "Original test app must be installed before preparing its update");
                String original = source.readSource();
                String literal = "\"Add one\"";
                int first = original.indexOf(literal);
                if (first < 0 || original.indexOf(literal, first + literal.length()) >= 0)
                    throw new AssertionError("Expected exactly one counter label literal");
                onMain(
                        instrumentation,
                        () -> {
                            requirePreparedSelection(activity, state);
                            Ui.state(activity).setToolsOpen(true);
                            ((TextBox) field(activity, "source"))
                                    .setText(original.replace(literal, "\"Add one again\""));
                            Control build = button(activity, Labels.BUILD_SOURCE);
                            if (!build.isEnabled())
                                throw new AssertionError("Manual phone build unavailable");
                            Ui.click(build);
                        });
                long deadline =
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(150);
                while (mainValue(
                        instrumentation, () -> (boolean) field(activity, "buildInProgress"))) {
                    if (System.nanoTime() >= deadline)
                        throw new AssertionError("Update build deadline exceeded");
                    Thread.sleep(100);
                }
                int nextRevision = source.revision();
                VerifiedArtifact update =
                        SignedArtifactReceipt.restore(target, project, nextRevision);
                if (nextRevision != revision + 1
                        || update == null
                        || update.sha256().equals(previous.sha256())
                        || !source.readSource()
                                .equals(original.replace(literal, "\"Add one again\""))
                        || !mainValue(
                                instrumentation,
                                () -> button(activity, Labels.INSTALL).isEnabled()))
                    throw new AssertionError(
                            "Update did not produce its exact source revision and verified APK");
                android.content.pm.PackageInfo archive =
                        target.getPackageManager()
                                .getPackageArchiveInfo(
                                        update.file().getAbsolutePath(),
                                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (archive == null
                        || archive.getLongVersionCode() <= installed.getLongVersionCode()
                        || archive.signingInfo == null
                        || installed.signingInfo == null
                        || !java.util.Arrays.equals(
                                archive.signingInfo.getApkContentsSigners(),
                                installed.signingInfo.getApkContentsSigners()))
                    throw new AssertionError(
                            "Update version or project signing identity changed incorrectly");
                if (!state.edit()
                        .putInt("updateRevision", nextRevision)
                        .putString("updateSha256", update.sha256())
                        .putLong("updateVersion", archive.getLongVersionCode())
                        .commit()) throw new AssertionError("Could not pin the test update");
                return "Phone update ready: project="
                        + project.id()
                        + " revision="
                        + nextRevision
                        + " version="
                        + archive.getLongVersionCode()
                        + " sha256="
                        + update.sha256()
                        + " apk="
                        + update.file().getAbsolutePath()
                        + " same installed signer\n";
            }
            if ("latestMessage".equals(phase)) {
                onMain(
                        instrumentation,
                        () -> {
                            java.util.List<dev.smallwright.controller.ui.Turn> turns =
                                    Ui.state(activity).getTranscript();
                            String result =
                                    "Build ready for installation at revision "
                                            + state.getInt("revision", -1)
                                            + ".";
                            if (turns.isEmpty()
                                    || !turns.get(turns.size() - 1).getText().equals(result))
                                throw new AssertionError(
                                        "Latest live build result is missing from its chat");
                        });
                return "Conversation history: latest saved build result is visible after reopen\n";
            }
            if ("send".equals(phase)) {
                if (!freeApproved)
                    throw new AssertionError("Live free-only request requires explicit approval");
                int initialRevision =
                        new ProjectSourceStore(mainValue(instrumentation, () -> project(activity)))
                                .revision();
                onMain(
                        instrumentation,
                        () -> {
                            requirePreparedSelection(activity, state);
                            Ui.text(activity, "modelRequest").setText(REQUEST);
                            Ui.click(button(activity, Labels.GENERATE));
                        });
                waitNode(instrumentation, null, "Send & generate");
                click(instrumentation, "Send & generate");
                instrumentation.waitForIdleSync();
                long deadline =
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(150);
                while (System.nanoTime() < deadline) {
                    AtomicReference<Boolean> busy = new AtomicReference<>();
                    onMain(
                            instrumentation,
                            () -> busy.set((boolean) field(activity, "generationInProgress")));
                    if (!busy.get()) break;
                    Thread.sleep(200);
                }
                onMain(
                        instrumentation,
                        () -> {
                            if ((boolean) field(activity, "generationInProgress"))
                                throw new AssertionError(
                                        "Live request/build did not finish within the probe deadline");
                            if (!button(activity, Labels.INSTALL).isEnabled())
                                throw new AssertionError(
                                        "Live generation produced no installable APK: "
                                                + ((TextBox) field(activity, "status")).getText());
                        });
                ProjectIdentity project = mainValue(instrumentation, () -> project(activity));
                int revision = new ProjectSourceStore(project).revision();
                if (revision != initialRevision + 1)
                    throw new AssertionError(
                            "Live proposal did not commit exactly one source revision");
                String result = ready(instrumentation, target, activity);
                VerifiedArtifact artifact =
                        SignedArtifactReceipt.restore(target, project, revision);
                long version =
                        target.getPackageManager()
                                .getPackageArchiveInfo(artifact.file().getAbsolutePath(), 0)
                                .getLongVersionCode();
                if (!state.edit()
                        .putInt("revision", revision)
                        .putString("sha256", artifact.sha256())
                        .putLong("version", version)
                        .commit())
                    throw new AssertionError("Could not pin the successful live test build");
                return result;
            }
            if ("restore".equals(phase) || "restoreUpdate".equals(phase)) {
                long deadline =
                        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
                while (!mainValue(
                                instrumentation, () -> button(activity, Labels.INSTALL).isEnabled())
                        && System.nanoTime() < deadline) Thread.sleep(100);
                ChatStore.Chat chat =
                        mainValue(
                                instrumentation,
                                () -> (ChatStore.Chat) field(activity, "selectedChat"));
                if (!state.getString("chat", "").equals(chat.id)
                        || mainValue(
                                        instrumentation,
                                        () -> ((Picker) field(activity, "chatPicker")).getCount())
                                != 2)
                    throw new AssertionError("Selected test chat did not survive restart");
                String result = ready(instrumentation, target, activity);
                ProjectIdentity project = mainValue(instrumentation, () -> project(activity));
                ProjectSourceStore source = new ProjectSourceStore(project);
                VerifiedArtifact artifact =
                        SignedArtifactReceipt.restore(target, project, source.revision());
                boolean update = "restoreUpdate".equals(phase);
                if (source.revision() != state.getInt(update ? "updateRevision" : "revision", -1)
                        || !artifact.sha256()
                                .equals(state.getString(update ? "updateSha256" : "sha256", ""))
                        || target.getPackageManager()
                                        .getPackageArchiveInfo(artifact.file().getAbsolutePath(), 0)
                                        .getLongVersionCode()
                                != state.getLong(update ? "updateVersion" : "version", -1))
                    throw new AssertionError(
                            "Restart did not recover the exact successful live build");
                return result
                        + "Project/chats and verified Install action survived controller restart.\n";
            }
            throw new AssertionError("Unknown physical conversation phase");
        } finally {
            onMain(instrumentation, activity::finish);
        }
    }

    private static String ready(Instrumentation instrumentation, Context target, Activity activity)
            throws Exception {
        ProjectIdentity project = mainValue(instrumentation, () -> project(activity));
        ProjectSourceStore source = new ProjectSourceStore(project);
        VerifiedArtifact artifact =
                SignedArtifactReceipt.restore(target, project, source.revision());
        if (artifact == null
                || !mainValue(instrumentation, () -> button(activity, Labels.INSTALL).isEnabled()))
            throw new AssertionError("No verified test-project APK is ready");
        if (!source.readSource().contains("Add one"))
            throw new AssertionError("Live model did not apply the requested label change");
        ChatStore chats =
                new ChatStore(
                        new java.io.File(target.getFilesDir(), "chats"), target.getFilesDir());
        ChatStore.Chat selected =
                mainValue(instrumentation, () -> (ChatStore.Chat) field(activity, "selectedChat"));
        if (chats.listMessages(project.id(), selected.id).size() < 2)
            throw new AssertionError("Live request/result were not saved to the selected chat");
        return "Live phone build ready: project="
                + project.id()
                + " revision="
                + source.revision()
                + " sha256="
                + artifact.sha256()
                + " apk="
                + artifact.file().getAbsolutePath()
                + "\n";
    }

    private static Control button(Activity activity, String text) throws Exception {
        return Ui.button(activity, text);
    }

    private static ProjectIdentity project(Activity activity) throws Exception {
        return (ProjectIdentity) field(activity, "activeProject");
    }

    private static void requirePreparedSelection(Activity activity, SharedPreferences state)
            throws Exception {
        if (!project(activity).id().equals(state.getString("project", null))
                || !((ChatStore.Chat) field(activity, "selectedChat"))
                        .id.equals(state.getString("chat", null)))
            throw new AssertionError("Only the probe's prepared project and chat may be tested");
    }

    private interface Value<T> {
        T get() throws Exception;
    }

    private static <T> T mainValue(Instrumentation instrumentation, Value<T> value)
            throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        onMain(instrumentation, () -> result.set(value.get()));
        return result.get();
    }

    private static Object field(Activity activity, String name) throws Exception {
        return Ui.field(activity, name);
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void onMain(Instrumentation instrumentation, Action action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        instrumentation.runOnMainSync(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable failure) {
                        error.set(failure);
                    }
                });
        if (error.get() instanceof Exception exception) throw exception;
        if (error.get() instanceof Error failure) throw failure;
    }

    private static void click(Instrumentation instrumentation, String text) throws Exception {
        AccessibilityNodeInfo node = waitNode(instrumentation, null, text);
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new AssertionError("Could not tap " + text);
    }

    private static AccessibilityNodeInfo waitNode(
            Instrumentation instrumentation, String type, String text) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            AccessibilityNodeInfo found =
                    find(instrumentation.getUiAutomation().getRootInActiveWindow(), type, text);
            if (found != null) return found;
            Thread.sleep(100);
        }
        throw new AssertionError("Visible control unavailable: " + (text == null ? type : text));
    }

    private static AccessibilityNodeInfo find(
            AccessibilityNodeInfo node, String type, String text) {
        AccessibilityNodeInfo here = findHere(node, type, text);
        return here != null || text == null ? here : Ui.find(node, type, text);
    }

    private static AccessibilityNodeInfo findHere(
            AccessibilityNodeInfo node, String type, String text) {
        if (node == null) return null;
        if ((type == null || type.contentEquals(node.getClassName()))
                && (text == null || text.equalsIgnoreCase(String.valueOf(node.getText()))))
            return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findHere(node.getChild(i), type, text);
            if (found != null) return found;
        }
        return null;
    }
}
