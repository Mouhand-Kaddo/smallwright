package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import dev.smallwright.agent.ModelClient;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ChatStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class RunStepsTest {
    @Test
    public void publishesEachStepOfTheRunForItsChat() {
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", "Add a counter", "Thinking", shown::add);
        steps.finishLast(StepKind.DONE, "Thought for 2.0 s", "");
        steps.add(StepKind.CALLED, "Called replace_source", "MainActivity.java");
        steps.add(StepKind.RUNNING, "Building on this phone", null);

        assertEquals(
                List.of(
                        new Step(StepKind.DONE, "Thought for 2.0 s", ""),
                        new Step(StepKind.CALLED, "Called replace_source", "MainActivity.java"),
                        new Step(StepKind.RUNNING, "Building on this phone", "")),
                shown.get(shown.size() - 1).getSteps());
        assertEquals("chat_1", shown.get(0).getChatId());
        assertEquals("Add a counter", shown.get(0).getRequest());
    }

    @Test
    public void aLongRunSavesItsNewestStepsWithinOneChatMessage() {
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {});
        steps.finishLast(StepKind.DONE, "Thought for 1.0 s", "");
        for (int step = 1; step <= 400; step++)
            steps.add(StepKind.DONE, "Read src/Screen" + step + ".java", "");

        String saved = steps.saved();
        assertTrue(
                saved.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        <= InterruptedRuns.MAX_ROW_BYTES);
        assertTrue(saved.endsWith("done\tRead src/Screen400.java\t"));
        assertTrue(!saved.contains("Thought for 1.0 s"));
    }

    @Test
    public void savedStepsKeepTheNewestTracesWithinTheirBoundAndEveryRowThatFits() {
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {});
        for (int step = 1; step <= 20; step++) {
            steps.finishLast(
                    StepKind.DONE,
                    "Thought for " + step + ".0 s",
                    "",
                    "trace " + step + " " + "t".repeat(ModelClient.MAX_REASONING_BYTES - 20));
            steps.add(StepKind.DONE, "Read src/Screen" + step + ".java", "");
            steps.add(StepKind.THINKING, "Thinking", "");
        }
        steps.finishLast(StepKind.DONE, "Thought for 21.0 s", "", "last thought");

        List<Step> saved = StepLog.decode(steps.saved());
        assertEquals(41, saved.size());
        assertEquals("last thought", saved.get(40).getTrace());
        assertTrue(saved.get(38).getTrace().startsWith("trace 20 "));
        assertEquals("", saved.get(0).getTrace());
        int traces = 0;
        for (Step step : saved) traces += step.getTrace().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(traces <= InterruptedRuns.MAX_TRACE_BYTES);
        assertTrue(
                steps.saved().getBytes(StandardCharsets.UTF_8).length <= ChatStore.MAX_TEXT_BYTES);
    }

    @Test
    public void theNewestPlanIsSavedWholeHoweverLongTheRunGrewAfterIt() throws Exception {
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {});
        steps.plan(
                dev.smallwright.agent.RunPlan.fromCall(
                        "{\"steps\":[{\"text\":\"Old\",\"status\":\"todo\"}]}"));
        dev.smallwright.agent.RunPlan newest =
                dev.smallwright.agent.RunPlan.fromCall(
                        "{\"steps\":[{\"text\":\"Make the screen\",\"status\":\"done\"},"
                                + "{\"text\":\"Build\",\"status\":\"doing\"}]}");
        steps.plan(newest);
        for (int step = 1; step <= 200; step++) {
            steps.add(StepKind.THINKING, "Thinking", "");
            steps.finishLast(
                    StepKind.DONE,
                    "Thought for 1.0 s",
                    "",
                    "t".repeat(ModelClient.MAX_REASONING_BYTES - 20));
            steps.add(StepKind.DONE, "Read src/Screen" + step + ".java", "");
        }

        List<Step> plans = new ArrayList<>();
        for (Step step : StepLog.decode(steps.saved()))
            if (step.getKind() == StepKind.PLAN) plans.add(step);
        assertEquals(1, plans.size());
        assertEquals("1 of 2 done", plans.get(0).getText());
        assertEquals(newest.saved(), plans.get(0).getTrace());
        assertTrue(steps.saved().endsWith("done\tRead src/Screen200.java\t"));
    }

    @Test
    public void aStoppedRunSavesItsUnfinishedStepAsStopped() {
        RunSteps steps = new RunSteps("chat_1", null, "Fixing the build error", live -> {});
        steps.finishLast(StepKind.DONE, "Thought for 12 s", "");
        steps.add(StepKind.RUNNING, "Building on this phone", "");
        steps.stopUnfinished("Stopped");

        assertEquals("done\tThought for 12 s\t\nfailed\tStopped\t", steps.saved());
    }

    @Test
    public void aStoppedRunSavesItsUnansweredQuestionAsStopped() {
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {});
        steps.finishLast(StepKind.ASKING, "Add the library Gson?", "");
        steps.stopUnfinished("Stopped");

        assertEquals("failed\tStopped\t", steps.saved());
    }

    @Test
    public void finishedStepsStayAsTheyWereWhenTheRunEnds() {
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", shown::add);
        steps.finishLast(StepKind.FAILED, "DeepSeek did not answer", "OpenRouter rate limit");
        steps.stopUnfinished("Did not finish");

        assertEquals("failed\tDeepSeek did not answer\tOpenRouter rate limit", steps.saved());
        assertEquals(1, shown.size());
        assertNull(shown.get(0).getRequest());
        assertTrue(new RunSteps("c", null, "Thinking", live -> {}).lastIs(StepKind.THINKING));
    }

    @Test
    public void liveThinkingIsShownAtMostEvery150MillisecondsAndNeverSaved() throws Exception {
        List<LiveRun> saved = new ArrayList<>();
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", saved::add, shown::add);
        steps.thought("Look");
        steps.thought("Look at");
        assertEquals(1, shown.size());
        assertEquals(
                new Step(StepKind.THINKING, "Thinking", "", "Look"),
                shown.get(0).getSteps().get(0));
        Thread.sleep(160);
        steps.thought("Look at it");
        assertEquals(2, shown.size());
        assertEquals("Look at it", shown.get(1).getSteps().get(0).getTrace());
        assertTrue(saved.isEmpty());

        steps.finishLast(StepKind.DONE, "Thought for 1.0 s", "", "Look at it.");
        Thread.sleep(160);
        steps.thought("late");
        assertEquals(2, shown.size());
        assertEquals("Look at it.", saved.get(0).getSteps().get(0).getTrace());
    }

    @Test
    public void longLiveThinkingKeepsItsNewestWords() {
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {}, shown::add);
        steps.thought("old ".repeat(ModelClient.MAX_REASONING_BYTES) + "the newest words");
        String trace = shown.get(0).getSteps().get(0).getTrace();
        assertTrue(trace.startsWith("…"));
        assertTrue(trace.endsWith("the newest words"));
        assertTrue(
                trace.getBytes(StandardCharsets.UTF_8).length
                        <= ModelClient.MAX_REASONING_BYTES + 3);
    }

    @Test
    public void aRetrySaysWhyOnlyOnScreenAndTheNextReplyClearsItAtOnce() {
        List<LiveRun> saved = new ArrayList<>();
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", saved::add, shown::add);
        steps.thought("half a thought");
        steps.retrying("Connection dropped. Trying again (1 of 3)…");
        assertEquals(
                List.of(
                        new Step(
                                StepKind.THINKING,
                                "Thinking",
                                "Connection dropped. Trying again (1 of 3)…")),
                shown.get(shown.size() - 1).getSteps());

        steps.thought("A new");
        assertEquals(
                List.of(new Step(StepKind.THINKING, "Thinking", "", "A new")),
                shown.get(shown.size() - 1).getSteps());
        assertTrue(saved.isEmpty());
    }

    @Test
    public void newThinkingOfTheSameLengthIsStillShown() throws Exception {
        List<LiveRun> shown = new ArrayList<>();
        RunSteps steps = new RunSteps("chat_1", null, "Thinking", live -> {}, shown::add);
        steps.thought("Look at the old part");
        Thread.sleep(160);
        steps.thought("Look at the new part");
        assertEquals(2, shown.size());
        assertEquals("Look at the new part", shown.get(1).getSteps().get(0).getTrace());
    }
}
