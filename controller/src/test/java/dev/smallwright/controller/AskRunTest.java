package dev.smallwright.controller;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A run that ends on the ask tool: the round is the outcome, and nothing is built. */
public final class AskRunTest {
    private static final String MAIN = "java/dev/smallwright/generated/counter/MainActivity.java";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();
    private RunSteps steps;

    private void source() throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        steps = new RunSteps("chat", "request", "Thinking", live -> {});
    }

    private StepRun.Outcome run(ScriptedModel model) throws Exception {
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "Make me a habit tracker";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        StepRun.Build noBuild =
                (revision, buildId) -> {
                    throw new AssertionError("No build was asked for");
                };
        return new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                new dev.smallwright.libraries.MavenRepositories(
                                        temporary.newFolder(),
                                        new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                                "http://127.0.0.1:9/google/",
                                                "http://127.0.0.1:9/central/",
                                                "http://127.0.0.1:9/search/select"))))
                .run(
                        approved,
                        source.revision(),
                        () -> true,
                        model.model(),
                        noBuild,
                        (next, revision, buildId, tokens) ->
                                journal.add(
                                        journal.get(journal.size() - 1)
                                                .advance(next, revision, buildId, tokens)),
                        steps);
    }

    private RunJournal.State state() {
        return journal.get(journal.size() - 1).state;
    }

    private static String round(String question, String recommended, String other) {
        JsonObject asked = new JsonObject();
        asked.addProperty("question", question);
        asked.addProperty("recommended", recommended);
        JsonArray others = new JsonArray();
        others.add(other);
        asked.add("other_choices", others);
        JsonArray questions = new JsonArray();
        questions.add(asked);
        JsonObject arguments = new JsonObject();
        arguments.add("questions", questions);
        return arguments.toString();
    }

    private static String write(String path, String content) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("path", path);
        arguments.addProperty("content", content);
        return arguments.toString();
    }

    @Test
    public void askEndsTheRunWithTheRoundAndTheModelsWordsAndBuildsNothing() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .saysAndCalls(
                                "A couple of questions first.",
                                "ask",
                                round("Remind you each day?", "Yes, every evening", "No"));
        StepRun.Outcome outcome = run(model);
        assertEquals(0, model.remaining());
        assertEquals(1, outcome.requests);
        assertNotNull(outcome.round);
        assertEquals("A couple of questions first.", outcome.round.intro);
        assertEquals("Remind you each day?", outcome.round.questions.get(0).text);
        assertEquals("A couple of questions first.", outcome.reply);
        assertNull(outcome.artifact);
        assertFalse(outcome.limitReached);
        assertEquals(0, source.revision());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void withoutWordsTheFirstQuestionIsTheReplyTheNotificationShows() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel().calls("ask", round("Remind you each day?", "Yes", "No"));
        StepRun.Outcome outcome = run(model);
        assertEquals("", outcome.round.intro);
        assertEquals("Remind you each day?", outcome.reply);
    }

    @Test
    public void editsMadeInEarlierStepsAreSavedWhenTheRunEndsOnAsk() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("write_file", write(MAIN, "class Draft {}"))
                        .calls("ask", round("Keep this layout?", "Yes", "No"));
        StepRun.Outcome outcome = run(model);
        assertNotNull(outcome.round);
        assertEquals(1, source.revision());
        assertEquals(1, outcome.revision);
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void askBesideOtherCallsIsRefusedWhileTheOthersRunAndTheRunGoesOn() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                write(MAIN, "class Draft {}"),
                                "ask",
                                round("Keep this layout?", "Yes", "No"))
                        .calls("ask", round("Keep this layout?", "Yes", "No"));
        StepRun.Outcome outcome = run(model);
        String refused = model.lastToolResult(1);
        assertTrue(refused, refused.contains("only tool call"));
        assertTrue(
                model.messages(1).toString(),
                model.messages(1).toString().contains("Replaced " + MAIN));
        assertEquals(2, outcome.requests);
        assertNotNull(outcome.round);
        assertEquals(1, source.revision());
    }

    @Test
    public void aBadRoundIsRefusedToTheModelAndShownAsAFailedStep() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("ask", round("Remind you each day?", "Yes", "yes"))
                        .answer("I will keep it simple.");
        StepRun.Outcome outcome = run(model);
        assertTrue(model.lastToolResult(1), model.lastToolResult(1).contains("twice"));
        assertNull(outcome.round);
        assertEquals("I will keep it simple.", outcome.reply);
        List<Step> shown = StepLog.decode(steps.saved());
        assertTrue(
                shown.toString(),
                shown.stream()
                        .anyMatch(
                                step ->
                                        step.getKind() == StepKind.FAILED
                                                && step.getText().equals(StepRun.ASK_FAILED)));
    }

    @Test
    public void anOrdinaryAnswerHasNoRound() throws Exception {
        source();
        StepRun.Outcome outcome = run(new ScriptedModel().answer("It tracks habits."));
        assertNull(outcome.round);
        assertEquals(RunJournal.State.ANSWERED, state());
    }
}
