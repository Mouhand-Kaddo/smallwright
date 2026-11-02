package dev.smallwright.controller;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smallwright.agent.RunPlan;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.security.ContextFilter;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** write_brief and update_plan in a run: a scripted model against a real project store. */
public final class BriefAndPlanRunTest {
    private static final String BRIEF =
            "For: me, to count glasses of water.\n- One screen with a big plus button";
    private static final int CONTEXT = 60_000;

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();
    private final List<LiveRun> published = new ArrayList<>();
    private RunSteps steps;

    private void source() throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
    }

    private StepRun.Outcome run(ScriptedModel model) throws Exception {
        journal.clear();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        published.clear();
        steps = new RunSteps("chat", "request", "Thinking", published::add);
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "Make me a water tracker";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
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
                        (revision, buildId) -> {
                            throw new AssertionError("No build was asked for");
                        },
                        (next, revision, buildId, tokens) ->
                                journal.add(
                                        journal.get(journal.size() - 1)
                                                .advance(next, revision, buildId, tokens)),
                        steps);
    }

    private static String brief(String text) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("text", text);
        return arguments.toString();
    }

    private static String plan(String... textAndStatus) {
        JsonArray list = new JsonArray();
        for (int index = 0; index < textAndStatus.length; index += 2) {
            JsonObject step = new JsonObject();
            step.addProperty("text", textAndStatus[index]);
            step.addProperty("status", textAndStatus[index + 1]);
            list.add(step);
        }
        JsonObject arguments = new JsonObject();
        arguments.add("steps", list);
        return arguments.toString();
    }

    private static JsonObject firstRequest(ScriptedModel model) {
        return JsonParser.parseString(
                        model.messages(0).get(0).getAsJsonObject().get("content").getAsString())
                .getAsJsonObject();
    }

    private List<Step> saved() {
        return StepLog.decode(steps.saved());
    }

    private List<Step> plans(List<Step> shown) {
        List<Step> plans = new ArrayList<>();
        for (Step step : shown) if (step.getKind() == StepKind.PLAN) plans.add(step);
        return plans;
    }

    @Test
    public void theBriefTheBuilderWritesIsSavedAndEveryLaterRunStartsWithIt() throws Exception {
        source();
        ScriptedModel first =
                new ScriptedModel().calls("write_brief", brief(BRIEF)).answer("Here is the plan.");
        StepRun.Outcome outcome = run(first);

        assertEquals("Here is the plan.", outcome.reply);
        assertEquals(BRIEF, source.brief().read());
        assertTrue(first.lastToolResult(1), first.lastToolResult(1).startsWith("Saved"));
        assertEquals(0, source.revision());
        assertFalse(firstRequest(first).has("project_brief"));
        assertTrue(
                saved().stream()
                        .anyMatch(
                                step ->
                                        step.getKind() == StepKind.DONE
                                                && step.getText()
                                                        .equals("Wrote the project brief")));

        ScriptedModel next = new ScriptedModel().answer("Done.");
        run(next);
        assertEquals(BRIEF, firstRequest(next).get("project_brief").getAsString());
    }

    @Test
    public void aBriefTooLongIsRefusedAndTheSavedOneStays() throws Exception {
        source();
        source.brief().write(BRIEF);
        ScriptedModel model =
                new ScriptedModel()
                        .calls("write_brief", brief("x".repeat(ContextFilter.MAX_BRIEF_BYTES + 1)))
                        .answer("Sorry.");
        run(model);
        assertEquals(BRIEF, source.brief().read());
        assertTrue(
                model.lastToolResult(1),
                model.lastToolResult(1).contains(String.valueOf(ContextFilter.MAX_BRIEF_BYTES)));
        assertTrue(
                saved().stream()
                        .anyMatch(
                                step ->
                                        step.getKind() == StepKind.FAILED
                                                && step.getText()
                                                        .equals(
                                                                "Could not save the project brief")));
    }

    @Test
    public void aBriefChangedAfterThePreviewRefusesTheApproval() throws Exception {
        source();
        ContextPreviewGate gate = new ContextPreviewGate(source);
        ContextPreviewGate.Preview preview = gate.preview("Add a button", 0);
        source.brief().write(BRIEF);
        assertThrows(IllegalStateException.class, () -> gate.approve(preview, "Add a button", 0));
        ContextPreviewGate.Preview again = gate.preview("Add a button", 0);
        assertEquals(again.payload(), gate.approve(again, "Add a button", 0));
        assertTrue(again.payload().contains("big plus button"));
    }

    @Test
    public void eachPlanReplacesTheLastAndTheRunKeepsOnlyTheNewest() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "update_plan",
                                plan(
                                        "Make the screen",
                                        "doing",
                                        "Save counts",
                                        "todo",
                                        "Build",
                                        "todo"))
                        .calls(
                                "update_plan",
                                plan(
                                        "Make the screen",
                                        "done",
                                        "Save counts",
                                        "doing",
                                        "Build",
                                        "todo"))
                        .answer("Done.");
        run(model);

        assertEquals("Plan updated: 0 of 3 done.", model.lastToolResult(1));
        assertEquals("Plan updated: 1 of 3 done.", model.lastToolResult(2));
        List<Step> plans = plans(saved());
        assertEquals(1, plans.size());
        assertEquals("1 of 3 done", plans.get(0).getText());
        RunPlan kept = RunPlan.fromSaved(plans.get(0).getTrace());
        assertEquals(RunPlan.Status.DONE, kept.items.get(0).status);
        assertEquals(RunPlan.Status.DOING, kept.items.get(1).status);
        assertEquals("Build", kept.items.get(2).text);

        boolean sawFirst = false;
        for (LiveRun live : published) {
            List<Step> shown = plans(live.getSteps());
            assertTrue(shown.size() <= 1);
            if (!shown.isEmpty() && shown.get(0).getText().equals("0 of 3 done")) sawFirst = true;
        }
        assertTrue(sawFirst);
    }

    @Test
    public void aBadPlanIsRefusedToTheModelAndTheRunGoesOn() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("update_plan", plan("Make the screen", "started"))
                        .answer("Done.");
        StepRun.Outcome outcome = run(model);
        assertEquals("Done.", outcome.reply);
        assertTrue(model.lastToolResult(1), model.lastToolResult(1).contains("step 1"));
        assertTrue(plans(saved()).isEmpty());
        assertTrue(
                saved().stream()
                        .anyMatch(
                                step ->
                                        step.getKind() == StepKind.FAILED
                                                && step.getText()
                                                        .equals("Could not update the plan")));
    }

    @Test
    public void theBriefAndPlanWrittenInARunSurviveSummarizingItsEarlierSteps() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .context(CONTEXT)
                        .promptTokens(Compaction.limit(CONTEXT))
                        .calls(
                                "write_brief",
                                brief(BRIEF),
                                "update_plan",
                                plan("Make the screen", "doing", "Build", "todo"))
                        .promptTokens(100)
                        .answer("Done.")
                        .summaries("Wrote the brief.");
        run(model);

        assertEquals(1, model.summaryRequests().size());
        JsonObject continued = model.stepRequests().get(1);
        String note =
                continued
                        .getAsJsonArray("messages")
                        .get(1)
                        .getAsJsonObject()
                        .get("content")
                        .getAsString();
        assertTrue(note, note.contains("Wrote the brief."));
        assertTrue(note, note.contains(BRIEF));
        assertTrue(note, note.contains("[>] Make the screen"));
        assertTrue(note, note.contains("[ ] Build"));
    }
}
