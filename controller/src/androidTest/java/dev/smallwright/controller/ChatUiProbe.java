package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises visible chat creation and recovery on a disposable emulator install. */
final class ChatUiProbe {
    static void run(Instrumentation instrumentation) throws Exception {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity first = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, first);
        Picker firstChats = chatPicker(first);
        AtomicInteger expectedCount = new AtomicInteger();
        AtomicReference<String> expectedSelection = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        Picker chats = firstChats;
                        Control add = control(first, Labels.NEW_CHAT);
                        int before = chats.getCount();
                        if (before < 1) throw new AssertionError("No initial chat");
                        Ui.click(add);
                        if (chats.getCount() != before + 1 || chats.getSelectedItemPosition() != 0)
                            throw new AssertionError("New chat was not selected");
                        expectedCount.set(before + 1);
                    });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(
                    () -> {
                        Picker chats = firstChats;
                        chats.select(1);
                        expectedSelection.set(chats.getItems().get(1));
                    });
            instrumentation.waitForIdleSync();
        } finally {
            instrumentation.runOnMainSync(first::finish);
        }
        Activity reopened = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        ControllerUi.awaitProjectBinding(instrumentation, reopened);
        Picker reopenedChats = chatPicker(reopened);
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        Picker chats = reopenedChats;
                        if (chats.getCount() != expectedCount.get())
                            throw new AssertionError("Chats did not survive controller reopen");
                        String selected = chats.getItems().get(chats.getSelectedItemPosition());
                        if (!expectedSelection.get().equals(selected))
                            throw new AssertionError(
                                    "Selected chat did not survive controller reopen: expected "
                                            + expectedSelection.get()
                                            + ", got "
                                            + selected);
                    });
        } finally {
            instrumentation.runOnMainSync(reopened::finish);
        }
    }

    private static Picker chatPicker(Activity activity) throws Exception {
        return Ui.picker(activity, "chatPicker");
    }

    private static Control control(Activity activity, String label) {
        try {
            return Ui.button(activity, label);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
