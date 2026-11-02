package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class StepLogTest {
    @Test
    public void stepsSurviveTheChatMessageRoundTrip() {
        List<Step> steps =
                List.of(
                        new Step(StepKind.DONE, "Thought for 12 s", ""),
                        new Step(StepKind.CALLED, "Called replace_source", "MainActivity.java"),
                        new Step(StepKind.FAILED, "Build failed", "3 errors"));
        assertEquals(steps, StepLog.decode(StepLog.encode(steps)));
    }

    @Test
    public void unfinishedStepsAreSavedAsFailedAndSeparatorsCannotSplitAStep() {
        String saved =
                StepLog.encode(
                        List.of(
                                new Step(StepKind.THINKING, "Thinking", ""),
                                new Step(StepKind.RUNNING, "Building\ton\nthis phone", "a\tb"),
                                new Step(StepKind.ASKING, "Add the library Gson?", "")));
        assertEquals(
                List.of(
                        new Step(StepKind.FAILED, "Thinking", ""),
                        new Step(StepKind.FAILED, "Building on this phone", "a b"),
                        new Step(StepKind.FAILED, "Add the library Gson?", "")),
                StepLog.decode(saved));
    }

    @Test
    public void aThinkingTraceKeepsItsLinesThroughTheRoundTrip() {
        String trace = "First:\n\tlist the files\\n stays\r\nThen \\ build.";
        List<Step> steps =
                List.of(
                        new Step(StepKind.DONE, "Thought for 3.0 s", "", trace),
                        new Step(StepKind.DONE, "Listed the project's files", ""));
        String saved = StepLog.encode(steps);
        assertEquals(2, saved.lines().count());
        assertEquals(
                List.of(
                        new Step(
                                StepKind.DONE,
                                "Thought for 3.0 s",
                                "",
                                "First:\n\tlist the files\\n stays\nThen \\ build."),
                        steps.get(1)),
                StepLog.decode(saved));
    }

    @Test
    public void stepsSavedBeforeTracesStillReadTheSame() {
        assertEquals(
                "done\tThought for 12 s\t",
                StepLog.encode(List.of(new Step(StepKind.DONE, "Thought for 12 s", ""))));
        assertEquals(
                List.of(
                        new Step(StepKind.DONE, "Thought for 12 s", ""),
                        new Step(StepKind.FAILED, "Build failed", "3 errors")),
                StepLog.decode("done\tThought for 12 s\t\nfailed\tBuild failed\t3 errors"));
    }

    @Test
    public void unknownLinesStillShowAsText() {
        assertEquals(
                List.of(new Step(StepKind.DONE, "an older note", "")),
                StepLog.decode("an older note\n\n"));
    }
}
