package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import dev.smallwright.protocol.CompilerDiagnostics;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Each run limit ends a run at the right step with the right message; none is on by default. */
public final class StepRunLimitsTest {
    private static final String MAIN = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String SCREEN = "java/dev/smallwright/generated/counter/ListScreen.java";
    private static final String SAVED = " The changes so far are saved. Send a message to go on.";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();
    private int builds;

    private void source() throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
    }

    private RunJournal.State state() {
        return journal.get(journal.size() - 1).state;
    }

    private StepRun.Outcome run(ScriptedModel model, RunLimits limits, Object... buildResults)
            throws Exception {
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String approved =
                gate.approve(
                        gate.preview("Make it better", source.revision()),
                        "Make it better",
                        source.revision());
        LibraryTools libraries =
                new LibraryTools(
                        new dev.smallwright.libraries.MavenRepositories(
                                temporary.newFolder(),
                                new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                        "http://127.0.0.1:9/google/",
                                        "http://127.0.0.1:9/central/",
                                        "http://127.0.0.1:9/search/select")));
        journal.clear();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        builds = 0;
        return new StepRun(source, new StepRun.Control(), libraries, limits)
                .run(
                        approved,
                        source.revision(),
                        () -> true,
                        model.model(),
                        (revision, buildId) -> {
                            Object result = buildResults[builds++];
                            if (result instanceof Exception) throw (Exception) result;
                            java.io.File apk = temporary.newFile();
                            java.nio.file.Files.write(apk.toPath(), ("apk " + revision).getBytes());
                            return VerifiedArtifact.capture(apk);
                        },
                        (next, revision, buildId, tokens) ->
                                journal.add(
                                        journal.get(journal.size() - 1)
                                                .advance(next, revision, buildId, tokens)),
                        new RunSteps("chat", "request", "Thinking", published -> {}));
    }

    private String stored(String path) throws Exception {
        byte[] bytes = source.readRecoverySources(source.revision()).get(path);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String args(String path, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("path", path);
        object.addProperty("content", content);
        return object.toString();
    }

    private JavaCompileFailure undefinedMethodOnLine(int line) throws Exception {
        SortedMap<String, byte[]> sources = new TreeMap<>();
        for (Map.Entry<String, byte[]> file :
                source.readRecoverySources(source.revision()).entrySet())
            if (file.getKey().endsWith(".java")) sources.put(file.getKey(), file.getValue());
        CompilerDiagnostics.Report report =
                CompilerDiagnostics.create(
                        CompilerDiagnostics.sourceSetSha256(sources),
                        List.of(
                                new CompilerDiagnostics.Problem(
                                        new ArrayList<>(sources.keySet()).indexOf(MAIN),
                                        67108964,
                                        50,
                                        line)),
                        false);
        return new JavaCompileFailure(CompilerDiagnostics.decode(report.toBytes(), sources));
    }

    private ScriptedModel longRun() {
        return new ScriptedModel()
                .calls("list_files", "{}")
                .calls("write_file", args(SCREEN, "class ListScreen {}"))
                .calls("list_files", "{}")
                .calls("list_files", "{}")
                .answer("Done at last.");
    }

    @Test
    public void everyLimitIsOffByDefault() throws Exception {
        assertEquals(0, RunLimits.OFF.maxSteps);
        assertNull(RunLimits.OFF.maxCostUsd);
        assertFalse(RunLimits.OFF.stopWhenStuck);
        source();
        ScriptedModel model = longRun();
        StepRun.Outcome outcome = run(model, RunLimits.OFF);
        assertEquals("Done at last.", outcome.reply);
        assertFalse(outcome.limitReached);
        assertEquals(5, outcome.requests);
    }

    @Test
    public void aRunWhoseTokensPassTwoBillionKeepsGoing() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel().promptTokens(1_000_000).completionTokens(4_000_000);
        String[] summaries = new String[220];
        java.util.Arrays.fill(summaries, "Listed the files.");
        for (int step = 0; step < summaries.length; step++) model.calls("list_files", "{}");
        model.answer("Done at last.").summaries(summaries);

        StepRun.Outcome outcome = run(model, RunLimits.OFF);

        assertEquals("Done at last.", outcome.reply);
        assertEquals(441, outcome.requests);
        assertEquals(Integer.MAX_VALUE, outcome.tokens);
        assertEquals(
                Integer.valueOf(Integer.MAX_VALUE), journal.get(journal.size() - 1).reportedTokens);
    }

    @Test
    public void stepLimitEndsTheRunAfterThatManyStepsAndTheNextSendContinues() throws Exception {
        source();
        ScriptedModel model = longRun();
        StepRun.Outcome outcome = run(model, new RunLimits(2, null, false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after 2 steps, the step limit you set in Settings." + SAVED,
                outcome.reply);
        assertEquals(2, outcome.requests);
        assertEquals(2, model.sent.size());
        assertEquals(3, model.remaining());
        assertNull(outcome.artifact);
        assertEquals(1, source.revision());
        assertEquals("class ListScreen {}", stored(SCREEN));
        assertEquals(RunJournal.State.ANSWERED, state());

        StepRun.Outcome next = run(new ScriptedModel().answer("Carrying on."), RunLimits.OFF);
        assertEquals("Carrying on.", next.reply);
        assertFalse(next.limitReached);
    }

    @Test
    public void aStepLimitOfOneNamesOneStepAndAnAnswerOnTheLastStepIsNotALimit() throws Exception {
        source();
        StepRun.Outcome limited =
                run(
                        new ScriptedModel().calls("list_files", "{}").answer("Never asked."),
                        new RunLimits(1, null, false));
        assertEquals(
                "I stopped after 1 step, the step limit you set in Settings." + SAVED,
                limited.reply);
        StepRun.Outcome answered =
                run(new ScriptedModel().answer("Just words."), new RunLimits(1, null, false));
        assertEquals("Just words.", answered.reply);
        assertFalse(answered.limitReached);
    }

    @Test
    public void spendingLimitEndsTheRunOnceTheReportedCostReachesIt() throws Exception {
        source();
        ScriptedModel model = longRun();
        StepRun.Outcome outcome = run(model, new RunLimits(0, new BigDecimal("0.0002"), false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after spending $0.0002, which reached the spending limit of $0.0002 you set in Settings."
                        + SAVED,
                outcome.reply);
        assertEquals(2, model.sent.size());
        assertEquals(0, new BigDecimal("0.0002").compareTo(outcome.costUsd));
    }

    @Test
    public void aBuildThatKeepsFailingTheSameWayStopsTheRunOnTheFourthFailure() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("build", "{}")
                        .calls("build", "{}")
                        .calls("build", "{}")
                        .calls("build", "{}")
                        .answer("Never reached.");
        StepRun.Outcome outcome =
                run(
                        model,
                        new RunLimits(0, null, true),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(4),
                        undefinedMethodOnLine(5),
                        undefinedMethodOnLine(6));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped because the same build error came back after 3 fixes in a row. You turned on Stop when stuck in Settings."
                        + SAVED,
                outcome.reply);
        assertEquals(4, model.sent.size());
        assertEquals(4, builds);
        assertEquals(1, model.remaining());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aSuccessfulBuildInBetweenStartsTheCountAgain() throws Exception {
        source();
        ScriptedModel model = new ScriptedModel();
        for (int index = 0; index < 7; index++) model.calls("build", "{}");
        model.answer("Finished anyway.");
        StepRun.Outcome outcome =
                run(
                        model,
                        new RunLimits(0, null, true),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        new Object(),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3));
        assertFalse(outcome.limitReached);
        assertEquals("Finished anyway.", outcome.reply);
        assertEquals(8, outcome.requests);
    }

    @Test
    public void withStopWhenStuckOffTheSameErrorNeverStopsTheRun() throws Exception {
        source();
        ScriptedModel model = new ScriptedModel();
        for (int index = 0; index < 5; index++) model.calls("build", "{}");
        model.answer("I give up.");
        StepRun.Outcome outcome =
                run(
                        model,
                        RunLimits.OFF,
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3),
                        undefinedMethodOnLine(3));
        assertFalse(outcome.limitReached);
        assertEquals("I give up.", outcome.reply);
    }

    private static final int CONTEXT = 60_000;

    private ScriptedModel summaryBeforeSecondStep() {
        return new ScriptedModel()
                .context(CONTEXT)
                .promptTokens(Compaction.limit(CONTEXT))
                .calls("list_files", "{}")
                .promptTokens(100)
                .calls("list_files", "{}")
                .answer("Never asked.")
                .summaries("Listed the files.");
    }

    @Test
    public void aSummaryThatReachesTheSpendingLimitEndsTheRunBeforeTheNextStep() throws Exception {
        source();
        ScriptedModel model = summaryBeforeSecondStep();
        StepRun.Outcome outcome = run(model, new RunLimits(0, new BigDecimal("0.0002"), false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after spending $0.0002, which reached the spending limit of $0.0002 you set in Settings."
                        + SAVED,
                outcome.reply);
        assertEquals(1, model.stepRequests().size());
        assertEquals(1, model.summaryRequests().size());
        assertEquals(2, outcome.requests);
        assertEquals(2, model.remaining());
    }

    @Test
    public void aFailedSummaryThatReachesTheSpendingLimitIsNotTriedAgain() throws Exception {
        source();
        ScriptedModel model = summaryBeforeSecondStep().summaryEndsBeforeDone();
        StepRun.Outcome outcome = run(model, new RunLimits(0, new BigDecimal("0.0002"), false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after spending $0.0002, which reached the spending limit of $0.0002 you set in Settings."
                        + SAVED,
                outcome.reply);
        assertEquals(1, model.stepRequests().size());
        assertEquals(1, model.summaryRequests().size());
        assertEquals(2, outcome.requests);
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aSummaryIsNotAStepForTheStepLimit() throws Exception {
        source();
        ScriptedModel model = summaryBeforeSecondStep();
        StepRun.Outcome outcome = run(model, new RunLimits(2, null, false));
        assertTrue(outcome.limitReached);
        assertEquals(
                "I stopped after 2 steps, the step limit you set in Settings." + SAVED,
                outcome.reply);
        assertEquals(2, model.stepRequests().size());
        assertEquals(1, model.summaryRequests().size());
        assertEquals(3, outcome.requests);
    }
}
