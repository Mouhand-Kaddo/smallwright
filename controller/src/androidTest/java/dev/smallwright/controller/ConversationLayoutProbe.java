package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;

/** Read-only layout checks suitable for the authorized physical phone. */
final class ConversationLayoutProbe {
    static String run(Instrumentation instrumentation) throws Exception {
        Activity activity =
                instrumentation.startActivitySync(
                        new Intent(instrumentation.getTargetContext(), MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        try {
            Control manualBuild = Ui.button(activity, Labels.BUILD_SOURCE);
            if (shown(instrumentation, manualBuild))
                throw new AssertionError(
                        "Manual source/build tools must be collapsed when a chat opens");
            String hint = Ui.text(activity, "modelRequest").getHint();
            if (!Ui.node(instrumentation, activity, "android.widget.Button", Labels.GENERATE)
                    .isVisibleToUser()) throw new AssertionError("Generate action is not visible");
            AccessibilityNodeInfo request =
                    Ui.find(instrumentation.getUiAutomation().getRootInActiveWindow(), null, hint);
            if (request == null || !request.isVisibleToUser())
                throw new AssertionError("Chat request is not visible when a chat opens");
            Ui.showTools(instrumentation, activity, activity);
            if (!shown(instrumentation, manualBuild))
                throw new AssertionError("Project tools did not open");
            Ui.closePages(instrumentation, activity);
            if (shown(instrumentation, manualBuild))
                throw new AssertionError("Project tools did not collapse");
            checkMenu(instrumentation, activity, manualBuild);
            return "Conversation layout: request and Generate visible; source tools expandable;"
                    + " menu tree, close and Back work\n";
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static void checkMenu(Instrumentation in, Activity activity, Control manualBuild)
            throws Exception {
        Picker chats = Ui.picker(activity, "chatPicker");
        String current =
                PhoneAlphaCounterProbe.mainValue(
                        in, () -> chats.getItems().get(chats.getSelectedItemPosition()));
        Ui.openMenu(in, activity);
        for (String label : new String[] {Labels.NEW_PROJECT, Labels.NEW_CHAT, Labels.TOOLS})
            if (!Ui.node(in, activity, "android.widget.Button", label).isVisibleToUser())
                throw new AssertionError("Menu action not visible: " + label);
        AccessibilityNodeInfo chat = Ui.node(in, activity, "android.widget.Button", current);
        if (!chat.isVisibleToUser() || !chat.isSelected())
            throw new AssertionError("Current chat is not shown as selected in the menu");
        if (Ui.shown(in, activity, Labels.INSTALL))
            throw new AssertionError("Install is in the menu; it belongs beside Send and in tools");
        if (Ui.shown(in, activity, Labels.GENERATE))
            throw new AssertionError("The chat behind the menu is still read out");
        Ui.tap(in, activity, Labels.CLOSE_MENU);
        awaitMenuClosed(in, activity);
        Ui.openMenu(in, activity);
        in.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        awaitMenuClosed(in, activity);
        Ui.openMenu(in, activity);
        Ui.tap(in, activity, Labels.TOOLS);
        if (!shown(in, manualBuild)) throw new AssertionError("Project tools did not open");
        Ui.closePages(in, activity);
    }

    private static void awaitMenuClosed(Instrumentation in, Activity activity) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (PhoneAlphaCounterProbe.mainValue(in, () -> Ui.state(activity).getMenuOpen())) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Menu did not close");
            Thread.sleep(50);
        }
        in.waitForIdleSync();
    }

    private static boolean shown(Instrumentation instrumentation, Control control)
            throws Exception {
        return PhoneAlphaCounterProbe.mainValue(instrumentation, control::isShown);
    }
}
