package dev.smallwright.controller;

import static org.junit.Assert.*;

import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A run's failed model request is sent again when another try can help, and only then. */
public final class RequestRetryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();
    private final List<LiveRun> published = new ArrayList<>();
    private final List<LiveRun> shown = new ArrayList<>();
    private final List<Long> waited = new ArrayList<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private RunSteps steps;
    private dev.smallwright.agent.ModelProvider provider =
            dev.smallwright.agent.ModelProvider.OPENROUTER;

    private StepRun.Outcome run(ScriptedModel model) throws Exception {
        return run(model, millis -> waited.add(millis));
    }

    private StepRun.Outcome run(ScriptedModel model, RequestRetry.Pause pause) throws Exception {
        return run(model, pause, RunLimits.OFF);
    }

    private StepRun.Outcome run(ScriptedModel model, RequestRetry.Pause pause, RunLimits limits)
            throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        steps = new RunSteps("chat", "request", "Thinking", published::add, shown::add);
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String approved =
                gate.approve(
                        gate.preview("Make it", source.revision()), "Make it", source.revision());
        StepRun run =
                new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                new dev.smallwright.libraries.MavenRepositories(
                                        temporary.newFolder(),
                                        new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                                "http://127.0.0.1:9/google/",
                                                "http://127.0.0.1:9/central/",
                                                "http://127.0.0.1:9/search/select"))),
                        limits,
                        RunSkills.NONE,
                        SkillAdding.NONE,
                        AppIcons.NONE,
                        new RequestRetry(pause, provider));
        return run.run(
                approved,
                source.revision(),
                active::get,
                model.model(text -> steps.thought(text)),
                (revision, buildId) -> {
                    throw new AssertionError("No build was asked for");
                },
                (next, revision, buildId, tokens) ->
                        journal.add(
                                journal.get(journal.size() - 1)
                                        .advance(next, revision, buildId, tokens)),
                steps);
    }

    private long totalWait() {
        return waited.stream().mapToLong(Long::longValue).sum();
    }

    private boolean showed(String detail) {
        for (LiveRun live : shown)
            for (Step step : live.getSteps())
                if (step.getKind() == StepKind.THINKING && step.getDetail().equals(detail))
                    return true;
        return false;
    }

    private boolean savedARetryNote() {
        for (LiveRun live : published)
            for (Step step : live.getSteps())
                if (step.getKind() == StepKind.THINKING && !step.getDetail().isEmpty()) return true;
        return false;
    }

    private RunJournal.State state() {
        return journal.get(journal.size() - 1).state;
    }

    @Test
    public void aDroppedConnectionIsTriedAgainWithTheSameMessagesAndTheRunCompletes()
            throws Exception {
        ScriptedModel model = new ScriptedModel().dropped().answer("It counts taps.");
        StepRun.Outcome outcome = run(model);
        assertEquals("It counts taps.", outcome.reply);
        assertEquals(1, outcome.requests);
        assertEquals(2, model.sent.size());
        assertEquals(model.messages(0), model.messages(1));
        assertEquals(2_000, totalWait());
        assertTrue(showed("Connection dropped. Trying again (1 of 3)…"));
        assertFalse(savedARetryNote());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aBusyOpenRouterIsWaitedForAsLongAsItAsksUpToAMinute() throws Exception {
        ScriptedModel model = new ScriptedModel().refused(429, 7).refused(503, 600).answer("Done.");
        assertEquals("Done.", run(model).reply);
        assertEquals(7_000 + 60_000, totalWait());
        assertTrue(showed("OpenRouter is busy. Trying again (1 of 3)…"));
        assertTrue(showed("OpenRouter is busy. Trying again (2 of 3)…"));
    }

    @Test
    public void aBusyModelServerIsNamedAsTheServer() throws Exception {
        provider =
                dev.smallwright.agent.ModelProvider.openAiCompatible("https://api.example.com/v1");
        ScriptedModel model = new ScriptedModel().refused(503).answer("Done.");
        assertEquals("Done.", run(model).reply);
        assertTrue(showed("Model server is busy. Trying again (1 of 3)…"));
    }

    @Test
    public void afterFourFailedTriesTheRunEndsWithTheFailedStep() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .refused(502)
                        .refused(502)
                        .refused(502)
                        .refused(502)
                        .answer("Never sent.");
        IOException failed = assertThrows(IOException.class, () -> run(model));
        assertEquals("OpenRouter request rejected (HTTP 502)", failed.getMessage());
        assertEquals(4, model.sent.size());
        assertEquals(2_000 + 5_000 + 15_000, totalWait());
        assertTrue(showed("OpenRouter is busy. Trying again (3 of 3)…"));
        List<Step> saved = StepLog.decode(steps.saved());
        assertEquals(
                new Step(
                        StepKind.FAILED,
                        "The model did not answer",
                        "OpenRouter request rejected (HTTP 502)"),
                saved.get(saved.size() - 1));
        assertEquals(RunJournal.State.FAILED, state());
    }

    @Test
    public void aRefusedKeyIsNotTriedAgain() throws Exception {
        ScriptedModel model = new ScriptedModel().refused(401).answer("Never sent.");
        IOException failed = assertThrows(IOException.class, () -> run(model));
        assertEquals("OpenRouter authentication failed", failed.getMessage());
        assertEquals(1, model.sent.size());
        assertTrue(waited.isEmpty());
    }

    @Test
    public void stopDuringTheWaitEndsItAtOnce() throws Exception {
        ScriptedModel model = new ScriptedModel().refused(503).answer("Never sent.");
        assertThrows(
                IllegalStateException.class,
                () ->
                        run(
                                model,
                                millis -> {
                                    waited.add(millis);
                                    active.set(false);
                                }));
        assertEquals(1, waited.size());
        assertEquals(1, model.sent.size());
        assertEquals(RunJournal.State.CANCELLED, state());
    }

    @Test
    public void aStreamCutPartwayThroughALineIsTriedAgain() throws Exception {
        ScriptedModel model =
                new ScriptedModel().answer("Cut short.").cutShort().answer("It counts taps.");
        StepRun.Outcome outcome = run(model);
        assertEquals("It counts taps.", outcome.reply);
        assertEquals(2, model.sent.size());
        assertTrue(showed("Connection dropped. Trying again (1 of 3)…"));
    }

    @Test
    public void aGarbledTryThatReportedItsUsageCountsTowardTheRunsTotals() throws Exception {
        ScriptedModel model =
                new ScriptedModel().calls("write_file", "{\"path\":").answer("It counts taps.");
        StepRun.Outcome outcome = run(model);
        assertEquals("It counts taps.", outcome.reply);
        assertEquals(2, model.sent.size());
        assertTrue(showed("The model's answer came out garbled. Trying again (1 of 3)…"));
        assertEquals(2, outcome.requests);
        assertEquals(240, outcome.tokens);
        assertEquals(0, new java.math.BigDecimal("0.0002").compareTo(outcome.costUsd));
    }

    @Test
    public void theRetryNoteGoesOnceTheNextTryAnswersAndItsTimeIsThatTrysAlone() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .refused(429, 1)
                        .reasoning("Count the taps.")
                        .answer("It counts taps.");
        run(
                model,
                millis -> {
                    waited.add(millis);
                    Thread.sleep(millis);
                });
        int noted = -1;
        int cleared = -1;
        for (int index = 0; index < shown.size(); index++) {
            Step last = shown.get(index).getSteps().get(0);
            if (last.getDetail().startsWith("OpenRouter is busy.")) noted = index;
            else if (noted >= 0 && cleared < 0 && last.getKind() == StepKind.THINKING)
                cleared = index;
        }
        assertTrue(noted >= 0);
        assertTrue(cleared > noted);
        assertEquals("", shown.get(cleared).getSteps().get(0).getDetail());
        assertFalse(savedARetryNote());
        Step thought = StepLog.decode(steps.saved()).get(0);
        assertEquals("", thought.getDetail());
        assertTrue(thought.getText(), thought.getText().startsWith("Thought for 0."));
    }

    @Test
    public void aFailedTryThatReachesTheSpendingLimitIsNotTriedAgainAndTheRunEndsOnTheLimit()
            throws Exception {
        ScriptedModel model =
                new ScriptedModel().calls("write_file", "{\"path\":").answer("Never sent.");
        StepRun.Outcome outcome =
                run(
                        model,
                        millis -> waited.add(millis),
                        new RunLimits(0, new java.math.BigDecimal("0.0001"), false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after spending $0.0001, which reached the spending limit of $0.0001"
                        + " you set in Settings. The changes so far are saved. Send a message to"
                        + " go on.",
                outcome.reply);
        assertEquals(1, model.sent.size());
        assertEquals(1, outcome.requests);
        assertTrue(waited.isEmpty());
        Step last = StepLog.decode(steps.saved()).get(0);
        assertEquals(StepKind.FAILED, last.getKind());
        assertEquals("The model did not answer", last.getText());
        assertEquals(RunJournal.State.ANSWERED, state());
    }
}
