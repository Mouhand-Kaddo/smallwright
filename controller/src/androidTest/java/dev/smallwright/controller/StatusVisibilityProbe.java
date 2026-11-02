package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import dev.smallwright.controller.ui.Labels;
import java.util.concurrent.atomic.AtomicBoolean;

/** The result status, the prompt's Build it and the feedback step are on the screen when due. */
final class StatusVisibilityProbe {
    static void run(Instrumentation instrumentation) throws Exception {
        Activity activity = launch(instrumentation);
        try {
            if (!Ui.node(instrumentation, activity, "android.widget.Button", Labels.GENERATE)
                    .isVisibleToUser()) throw new AssertionError("Build it is not on the screen");
            long deadline = SystemClock.elapsedRealtime() + 10_000;
            while (true) {
                String message =
                        PhoneAlphaCounterProbe.mainValue(
                                instrumentation,
                                () -> Ui.state(activity).getStatus().getText().split("\\n", 2)[1]);
                if (Ui.find(
                                instrumentation.getUiAutomation().getRootInActiveWindow(),
                                null,
                                message)
                        != null) break;
                if (SystemClock.elapsedRealtime() > deadline)
                    throw new AssertionError("Status line is not on the screen");
                SystemClock.sleep(100);
            }
            Ui.button(activity, Labels.STOP);
        } finally {
            activity.finish();
        }
    }

    static void feedbackAvailableAfterRestart(Instrumentation instrumentation) throws Exception {
        Activity activity = launch(instrumentation);
        AtomicBoolean available = new AtomicBoolean();
        try {
            for (int attempt = 0; attempt < 30 && !available.get(); attempt++) {
                available.set(
                        PhoneAlphaCounterProbe.mainValue(
                                instrumentation,
                                () -> Ui.button(activity, "Send my feedback").isEnabled()));
                if (!available.get()) SystemClock.sleep(100);
            }
        } finally {
            activity.finish();
        }
        if (!available.get())
            throw new AssertionError("Feedback unavailable after controller restart");
    }

    static void readyControls(Instrumentation instrumentation) throws Exception {
        Activity activity = launch(instrumentation);
        AtomicBoolean ready = new AtomicBoolean();
        try {
            for (int attempt = 0; attempt < 50 && !ready.get(); attempt++) {
                ready.set(
                        PhoneAlphaCounterProbe.mainValue(
                                instrumentation,
                                () ->
                                        Ui.control(activity, "build").isEnabled()
                                                && Ui.control(activity, "generate").isEnabled()
                                                && Ui.control(activity, "newChat").isEnabled()));
                if (!ready.get()) SystemClock.sleep(100);
            }
        } finally {
            activity.finish();
        }
        if (!ready.get())
            throw new AssertionError("Build, Generate, or New chat remained disabled");
    }

    private static Activity launch(Instrumentation instrumentation) {
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return instrumentation.startActivitySync(intent);
    }
}
