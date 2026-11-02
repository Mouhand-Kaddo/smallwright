package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.compose.runtime.SnapshotStateKt;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.UiState;
import org.json.JSONObject;

/**
 * One Send through the screen in a disposable project, with DeepSeek V4.1 Flash at the built-in
 * price and providers but a lowered context length, so the run reaches compaction quickly. The
 * person's saved choice is not touched; only this process's screen state is.
 */
final class CompactionRunProbe {
    private static final long START_MILLIS = 15_000;
    private static final long RUN_MILLIS = 15 * 60_000;

    private CompactionRunProbe() {}

    static String run(Instrumentation in, String project, int contextTokens, String message)
            throws Exception {
        Context target = in.getTargetContext();
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerUi.awaitProjectBinding(in, ui);
        ModelChoice base = ModelChoice.DEFAULT;
        ModelChoice lowered =
                new ModelChoice(
                        base.id,
                        base.canonicalId,
                        base.name,
                        base.promptUsdPerMillion,
                        base.completionUsdPerMillion,
                        contextTokens);
        in.runOnMainSync(
                () -> {
                    try {
                        UiState state = Ui.state(ui);
                        Picker projects = state.getProjectPicker();
                        String selected =
                                projects.getItems().get(projects.getSelectedItemPosition());
                        if (!project.equals(selected))
                            throw new AssertionError("Selected project is not the disposable one");
                        java.lang.reflect.Field model =
                                UiState.class.getDeclaredField("model$delegate");
                        model.setAccessible(true);
                        model.set(
                                state,
                                SnapshotStateKt.mutableStateOf(
                                        lowered, SnapshotStateKt.referentialEqualityPolicy()));
                        if (state.getModel().contextTokens != contextTokens)
                            throw new AssertionError("Context length not applied");
                        state.getModelRequest().setText(message);
                    } catch (Exception failure) {
                        throw new AssertionError(failure);
                    }
                });
        in.waitForIdleSync();
        long sent = SystemClock.elapsedRealtime();
        Ui.tap(in, target, Labels.GENERATE);
        while (!Ui.shown(in, target, Labels.STOP)) {
            if (SystemClock.elapsedRealtime() - sent > START_MILLIS)
                throw new AssertionError("The run did not start");
            Thread.sleep(200);
        }
        while (Ui.shown(in, target, Labels.STOP)) {
            if (SystemClock.elapsedRealtime() - sent > RUN_MILLIS)
                throw new AssertionError("The run did not end in time");
            Thread.sleep(1000);
        }
        return new JSONObject()
                .put("project", project)
                .put("model", lowered.id)
                .put("contextTokens", contextTokens)
                .put("seconds", (SystemClock.elapsedRealtime() - sent) / 1000)
                .toString();
    }
}
