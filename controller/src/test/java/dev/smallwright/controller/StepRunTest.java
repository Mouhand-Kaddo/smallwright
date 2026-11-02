package dev.smallwright.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** A run driven by scripted model replies against a real project store; builds are simulated. */
public final class StepRunTest {
    private static final String MAIN = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String LAYOUT = "res/layout/empty.xml";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ProjectSourceStore source;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();
    private final List<LiveRun> published = new ArrayList<>();
    private RunSteps steps;

    private ProjectSourceStore source() throws Exception {
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        steps = new RunSteps("chat", "request", "Thinking", published::add);
        return source;
    }

    private void restartJournal() throws Exception {
        journal.clear();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
    }

    private StepRun.Journal journal() {
        return (next, revision, buildId, tokens) ->
                journal.add(
                        journal.get(journal.size() - 1).advance(next, revision, buildId, tokens));
    }

    private RunJournal.State state() {
        return journal.get(journal.size() - 1).state;
    }

    private String approved(String request) throws Exception {
        ContextPreviewGate gate = new ContextPreviewGate(source);
        return gate.approve(gate.preview(request, source.revision()), request, source.revision());
    }

    private StepRun lastRun;

    private StepRun.Outcome run(ScriptedModel model, StepRun.Build build) throws Exception {
        return run(model, build, AttachedImages.NONE);
    }

    private StepRun.Outcome run(ScriptedModel model, StepRun.Build build, AttachedImages images)
            throws Exception {
        lastRun = new StepRun(source, new StepRun.Control(), noLibraries());
        return lastRun.run(
                approved("Make it better"),
                images,
                source.revision(),
                () -> true,
                model.model(),
                build,
                journal(),
                steps);
    }

    private LibraryTools noLibraries() throws Exception {
        return new LibraryTools(
                new dev.smallwright.libraries.MavenRepositories(
                        temporary.newFolder(),
                        new dev.smallwright.libraries.MavenRepositories.Endpoints(
                                "http://127.0.0.1:9/google/",
                                "http://127.0.0.1:9/central/",
                                "http://127.0.0.1:9/search/select")));
    }

    private static StepRun.Build noBuild() {
        return (revision, buildId) -> {
            throw new AssertionError("No build was asked for");
        };
    }

    private List<Step> shown() {
        return StepLog.decode(steps.saved());
    }

    private static String args(String... pairs) {
        JsonObject object = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) {
            String value = pairs[index + 1];
            if (value.matches("-?[0-9]+"))
                object.addProperty(pairs[index], Integer.parseInt(value));
            else object.addProperty(pairs[index], value);
        }
        return object.toString();
    }

    @Test
    public void firstRequestCarriesTheContractAndAFileListingButNoFileContents() throws Exception {
        source();
        ScriptedModel model = new ScriptedModel().answer("It counts taps.");
        run(model, noBuild());
        String first = model.messages(0).get(0).getAsJsonObject().get("content").getAsString();
        assertTrue(first.contains("build_contract"));
        assertTrue(first.contains(MAIN));
        assertTrue(first.contains(LAYOUT));
        assertTrue(first.contains("Make it better"));
        assertFalse(first.contains("installCrashHandler"));
        assertEquals("auto", model.sent.get(0).get("tool_choice").getAsString());
        assertEquals(23, model.sent.get(0).getAsJsonArray("tools").size());
    }

    @Test
    public void answersInWordsWithoutChangingTheProject() throws Exception {
        source();
        ScriptedModel model = new ScriptedModel().answer("It counts taps.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("It counts taps.", outcome.reply);
        assertNull(outcome.artifact);
        assertEquals(1, outcome.requests);
        assertEquals(0, source.revision());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void listsAndReadsTheFilesItNeedsThenAnswers() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("list_files", "{}")
                        .calls("read_file", args("path", MAIN, "offset", "1", "limit", "2"))
                        .answer("It counts taps.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("It counts taps.", outcome.reply);
        assertEquals(3, outcome.requests);
        assertEquals(360, outcome.tokens);
        String listing = model.lastToolResult(1);
        assertTrue(listing, listing.contains(MAIN));
        assertTrue(listing, listing.contains(LAYOUT));
        String read = model.lastToolResult(2);
        assertTrue(read, read.contains("package dev.smallwright.generated.counter;"));
        assertFalse(read, read.contains("import android.app.Activity;"));
        List<Step> shown = shown();
        assertEquals("Listed the project's files", shown.get(0).getText());
        assertEquals("Read MainActivity.java", shown.get(1).getText());
        assertEquals(StepKind.DONE, shown.get(1).getKind());
        assertEquals(0, source.revision());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void eachReplysReasoningIsShownUnderItsThoughtForStepAndNeverSentBack()
            throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .reasoning("REASON_ONE: list the files first.")
                        .calls("list_files", "{}")
                        .reasoning("")
                        .calls("list_files", "{}")
                        .reasoning("REASON_TWO: it counts taps.\nSay so.")
                        .answer("It counts taps.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("It counts taps.", outcome.reply);
        List<String> texts = new ArrayList<>();
        for (Step step : shown()) texts.add(step.getText().replaceAll("[0-9.]+ s$", "N s"));
        assertEquals(
                List.of(
                        "Thought for N s",
                        "Listed the project's files",
                        "Listed the project's files",
                        "Thought for N s"),
                texts);
        assertEquals("REASON_ONE: list the files first.", shown().get(0).getTrace());
        assertEquals("", shown().get(1).getTrace());
        assertEquals("REASON_TWO: it counts taps.\nSay so.", shown().get(3).getTrace());
        for (JsonObject request : model.sent)
            assertFalse(request.toString(), request.toString().contains("REASON_"));
    }

    @Test
    public void aLongFinalReplyReachesThePersonWhole() throws Exception {
        source();
        String words = "The app splits a bill. " + "It rounds each share. ".repeat(1000);
        ScriptedModel model = new ScriptedModel().answer(words);
        assertEquals(words.trim(), run(model, noBuild()).reply);
    }

    @Test
    public void everyCallOfALongReplyRuns() throws Exception {
        source();
        String[] listings = new String[40 * 2];
        for (int index = 0; index < listings.length; index += 2) {
            listings[index] = "list_files";
            listings[index + 1] = "{}";
        }
        ScriptedModel model = new ScriptedModel().calls(listings).answer("Done.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("Done.", outcome.reply);
        assertEquals(2, outcome.requests);
        JsonArray next = model.messages(1);
        int toolResults = 0;
        for (int index = 0; index < next.size(); index++) {
            JsonObject message = next.get(index).getAsJsonObject();
            if (!"tool".equals(message.get("role").getAsString())) continue;
            String content = message.get("content").getAsString();
            assertTrue(content, content.contains(MAIN));
            toolResults++;
        }
        assertEquals(40, toolResults);
    }

    @Test
    public void aReplyCutOffAtTheModelsOutputLimitIsDroppedAndTheRunGoesOn() throws Exception {
        source();
        int base = source.revision();
        ScriptedModel model = new ScriptedModel().cutOff().answer("Done in smaller steps.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("Done in smaller steps.", outcome.reply);
        assertEquals(base, outcome.revision);
        assertEquals(2, outcome.requests);
        assertEquals(0, new java.math.BigDecimal("0.0002").compareTo(outcome.costUsd));
        assertEquals("200 prompt tokens (0 cached), 40 completion tokens", lastRun.tokenCounts());
        JsonArray next = model.messages(1);
        assertEquals(2, next.size());
        assertFalse(next.toString(), next.toString().contains("<resou"));
        JsonObject note = next.get(1).getAsJsonObject();
        assertEquals("user", note.get("role").getAsString());
        String said = note.get("content").getAsString();
        assertTrue(said, said.contains("cut off"));
        assertTrue(said, said.contains("edit_file"));
        List<Step> cut = new ArrayList<>();
        for (Step step : shown())
            if (step.getKind() == StepKind.FAILED && step.getText().contains("cut off"))
                cut.add(step);
        assertEquals(1, cut.size());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void theNoteAfterACutOffNamesLongThinkingAndOneHugeToolCallNotOnlyFileSize()
            throws Exception {
        source();
        ScriptedModel model = new ScriptedModel().cutOff().answer("Done.");
        run(model, noBuild());
        String said = model.messages(1).get(1).getAsJsonObject().get("content").getAsString();
        assertTrue(said, said.contains("thinking"));
        assertTrue(said, said.contains("one tool call"));
    }

    @Test
    public void threeCutOffRepliesInARowEndTheRunWithAPlainMessage() throws Exception {
        source();
        int base = source.revision();
        ScriptedModel model =
                new ScriptedModel().cutOff().cutOff().cutOff().answer("Never asked for.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals(3, outcome.requests);
        assertEquals(1, model.remaining());
        assertTrue(outcome.limitReached);
        assertEquals(base, outcome.revision);
        assertTrue(outcome.reply, outcome.reply.contains("cut off 3 times in a row"));
        assertTrue(outcome.reply, outcome.reply.contains("nothing from them was used"));
        assertTrue(outcome.reply, outcome.reply.contains("another model"));
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aUsableReplyBetweenCutOffsStartsTheCountAgain() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .cutOff()
                        .cutOff()
                        .calls("list_files", "{}")
                        .cutOff()
                        .cutOff()
                        .answer("Done.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("Done.", outcome.reply);
        assertEquals(6, outcome.requests);
        assertFalse(outcome.limitReached);
    }

    @Test
    public void aWriteThatWouldTakeTheProjectPastSixteenMegabytesIsRefusedAndTheRunKeepsItsWork()
            throws Exception {
        source();
        java.util.Map<String, byte[]> blobs = new java.util.TreeMap<>();
        for (int file = 0; file < 16; file++)
            blobs.put("assets/blob_" + file + ".txt", new byte[1_000_000]);
        source.commit(source.revision(), blobs, java.util.Set.of());
        restartJournal();
        int full = source.revision();
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                args("path", "assets/small.txt", "content", "fits"),
                                "write_file",
                                args(
                                        "path",
                                        "assets/one_more.txt",
                                        "content",
                                        "y".repeat(1_000_000)))
                        .answer("Saved what fits.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("Saved what fits.", outcome.reply);
        String refusal = model.lastToolResult(1);
        assertTrue(refusal, refusal.contains("16 MB"));
        assertTrue(refusal, refusal.contains("Nothing was changed"));
        assertEquals(full + 1, outcome.revision);
        java.util.Map<String, byte[]> saved = source.readRecoverySources(source.revision());
        assertTrue(saved.containsKey("assets/small.txt"));
        assertFalse(saved.containsKey("assets/one_more.txt"));
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aRequestNeverAsksForMoreThanTheContextLeavesAfterItsPrompt() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel().promptTokens(600_000).calls("list_files", "{}").answer("Done.");
        run(model, noBuild());
        List<JsonObject> asked = model.stepRequests();
        int ceiling = dev.smallwright.agent.ModelChoice.DEFAULT.maxCompletionTokens;
        int context = dev.smallwright.agent.ModelChoice.DEFAULT.contextTokens;
        assertEquals(ceiling, asked.get(0).get("max_tokens").getAsInt());
        int second = asked.get(1).get("max_tokens").getAsInt();
        assertTrue(second + " tokens", second > 0 && second <= context - 600_020);
    }

    @Test
    public void aFileLargerThanAProjectFileMayHoldIsRefusedAndTheRunGoesOn() throws Exception {
        source();
        int base = source.revision();
        String before = stored(MAIN);
        String huge = "x".repeat(dev.smallwright.project.ProjectStore.MAX_FILE_BYTES);
        ScriptedModel model =
                new ScriptedModel()
                        .calls("write_file", args("path", MAIN, "content", huge + "x"))
                        .calls(
                                "edit_file",
                                args(
                                        "path",
                                        MAIN,
                                        "old_text",
                                        "increment.setText(\"Increment\");",
                                        "new_text",
                                        huge))
                        .answer("Split it.");
        StepRun.Outcome outcome = run(model, noBuild());
        assertEquals("Split it.", outcome.reply);
        assertEquals(base, outcome.revision);
        assertEquals(before, stored(MAIN));
        for (int request = 1; request <= 2; request++) {
            JsonArray messages = model.messages(request);
            String result =
                    messages.get(messages.size() - 1)
                            .getAsJsonObject()
                            .get("content")
                            .getAsString();
            assertTrue(result, result.contains("split"));
        }
    }

    @Test
    public void searchFindsTextAcrossTheProject() throws Exception {
        source();
        ScriptedModel model =
                new ScriptedModel()
                        .calls("search_text", args("query", "Increment"))
                        .answer("Found it.");
        run(model, noBuild());
        String found = model.lastToolResult(1);
        assertTrue(found, found.contains(MAIN + ":"));
        assertTrue(found, found.contains("increment.setText(\"Increment\");"));
        assertEquals("Searched for \"Increment\"", shown().get(0).getText());
    }

    private static final String SCREEN =
            "java/dev/smallwright/generated/counter/ui/ListScreen.java";

    private String stored(String path) throws Exception {
        byte[] bytes = source.readRecoverySources(source.revision()).get(path);
        return bytes == null ? null : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void editsStayInTheWorkingCopyUntilTheRunEndsThenCommitAsOneRevision() throws Exception {
        source();
        String layout = stored(LAYOUT);
        int[] seen = {0};
        ScriptedModel script =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                args(
                                        "path",
                                        SCREEN,
                                        "content",
                                        "package x;\nclass ListScreen {}\n"))
                        .calls(
                                "edit_file",
                                args(
                                        "path",
                                        MAIN,
                                        "old_text",
                                        "increment.setText(\"Increment\");",
                                        "new_text",
                                        "increment.setText(\"Add one\");"))
                        .calls("delete_file", args("path", LAYOUT))
                        .calls("read_file", args("path", SCREEN))
                        .answer("Added a list screen.");
        StepRun.Outcome outcome =
                new StepRun(source, new StepRun.Control(), noLibraries())
                        .run(
                                approved("Add a list"),
                                0,
                                () -> true,
                                script.model(
                                        () -> {
                                            try {
                                                assertEquals(0, source.revision());
                                                assertEquals(layout, stored(LAYOUT));
                                                seen[0]++;
                                            } catch (Exception failure) {
                                                throw new AssertionError(failure);
                                            }
                                        }),
                                noBuild(),
                                journal(),
                                steps);
        assertEquals(5, seen[0]);
        assertTrue(script.lastToolResult(4), script.lastToolResult(4).contains("class ListScreen"));
        assertEquals("Added a list screen.", outcome.reply);
        assertEquals(1, outcome.revision);
        assertEquals(1, source.revision());
        assertEquals("package x;\nclass ListScreen {}\n", stored(SCREEN));
        assertTrue(stored(MAIN).contains("increment.setText(\"Add one\");"));
        assertFalse(stored(MAIN).contains("\"Increment\""));
        assertNull(stored(LAYOUT));
        assertNull(outcome.artifact);
        assertEquals(RunJournal.State.ANSWERED, state());
        List<String> texts = new ArrayList<>();
        for (Step step : shown()) texts.add(step.getText());
        assertEquals("Created ui/ListScreen.java", texts.get(0));
        assertEquals("Edited MainActivity.java", texts.get(1));
        assertEquals("Deleted res/layout/empty.xml", texts.get(2));
        assertEquals("Read ui/ListScreen.java", texts.get(3));
    }

    @Test
    public void refusesPathsTheProjectMayNotHoldAsToolResults() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", "../escape.java", "content", "x"))
                        .calls(
                                "write_file",
                                args("path", "app/src/main/java/Main.java", "content", "x"))
                        .calls(
                                "write_file",
                                args("path", "java/dev/other/Main.java", "content", "x"))
                        .calls("delete_file", args("path", "AndroidManifest.xml"))
                        .calls("delete_file", args("path", MAIN))
                        .calls("read_file", args("path", "res/layout/missing.xml"))
                        .answer("I could not do that.");
        run(script, noBuild());
        assertTrue(script.lastToolResult(1).contains("is not a path a project may hold"));
        assertTrue(
                script.lastToolResult(1)
                        .contains(
                                "java/dev/smallwright/generated/counter/ (sub-packages allowed)"));
        assertTrue(script.lastToolResult(2).contains("is not a path a project may hold"));
        assertTrue(script.lastToolResult(3).contains("is not a path a project may hold"));
        assertTrue(script.lastToolResult(4).contains("edit it instead of deleting"));
        assertTrue(script.lastToolResult(5).contains("launcher activity"));
        assertTrue(script.lastToolResult(6).contains("There is no file res/layout/missing.xml"));
        for (Step step : shown().subList(0, 6)) assertEquals(StepKind.FAILED, step.getKind());
        assertEquals(0, source.revision());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void editRefusesOldTextThatIsMissingOrNotUnique() throws Exception {
        source();
        String main = stored(MAIN);
        ScriptedModel script =
                new ScriptedModel()
                        .calls(
                                "edit_file",
                                args("path", MAIN, "old_text", "count", "new_text", "total"))
                        .calls(
                                "edit_file",
                                args("path", MAIN, "old_text", "no such text", "new_text", "x"))
                        .answer("I need the exact text.");
        run(script, noBuild());
        assertTrue(script.lastToolResult(1).contains("occurs more than once"));
        assertTrue(script.lastToolResult(2).contains("does not occur"));
        assertEquals("Could not edit MainActivity.java", shown().get(0).getText());
        assertEquals(0, source.revision());
        assertEquals(main, stored(MAIN));
    }

    @Test
    public void stopBetweenStepsDiscardsUncommittedEditsAndCancelsTheRun() throws Exception {
        source();
        String main = stored(MAIN);
        StepRun.Control control = new StepRun.Control();
        int[] replies = {0};
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", MAIN, "content", "class Gone {}"))
                        .calls("write_file", args("path", SCREEN, "content", "class Never {}"))
                        .answer("Never sent.");
        assertThrows(
                IllegalStateException.class,
                () ->
                        new StepRun(source, control, noLibraries())
                                .run(
                                        approved("Rewrite it"),
                                        0,
                                        () -> true,
                                        script.model(
                                                () -> {
                                                    if (++replies[0] == 2) control.stop();
                                                }),
                                        noBuild(),
                                        journal(),
                                        steps));
        assertEquals(2, script.sent.size());
        assertEquals(1, script.remaining());
        assertEquals(0, source.revision());
        assertEquals(main, stored(MAIN));
        assertNull(stored(SCREEN));
        assertEquals(RunJournal.State.CANCELLED, state());
    }

    private final List<Integer> built = new ArrayList<>();

    private StepRun.Build builds(Object... results) {
        int[] next = {0};
        return (revision, buildId) -> {
            built.add(revision);
            assertEquals(RunJournal.State.BUILDING, state());
            assertEquals(revision, journal.get(journal.size() - 1).sourceRevision);
            assertEquals(buildId, journal.get(journal.size() - 1).buildId);
            assertEquals(revision, source.revision());
            Object result = results[next[0]++];
            if (result instanceof java.util.concurrent.Callable)
                result = ((java.util.concurrent.Callable<?>) result).call();
            if (result instanceof Exception) throw (Exception) result;
            java.io.File apk = temporary.newFile();
            java.nio.file.Files.write(apk.toPath(), ("apk of revision " + revision).getBytes());
            return VerifiedArtifact.capture(apk);
        };
    }

    private static final Object OK = new Object();

    private java.util.concurrent.Callable<Exception> undefinedMethodOnLine(int line) {
        return () -> undefinedMethodIn(MAIN, line);
    }

    /** A report as the controller decodes it from the worker, against the committed sources. */
    private JavaCompileFailure undefinedMethodIn(String path, int line) throws Exception {
        java.util.SortedMap<String, byte[]> sources = new java.util.TreeMap<>();
        for (java.util.Map.Entry<String, byte[]> file :
                source.readRecoverySources(source.revision()).entrySet())
            if (file.getKey().endsWith(".java")) sources.put(file.getKey(), file.getValue());
        int index = new ArrayList<>(sources.keySet()).indexOf(path);
        dev.smallwright.protocol.CompilerDiagnostics.Report report =
                dev.smallwright.protocol.CompilerDiagnostics.create(
                        dev.smallwright.protocol.CompilerDiagnostics.sourceSetSha256(sources),
                        List.of(
                                new dev.smallwright.protocol.CompilerDiagnostics.Problem(
                                        index, 67108964, 50, line)),
                        false);
        return new JavaCompileFailure(
                dev.smallwright.protocol.CompilerDiagnostics.decode(report.toBytes(), sources));
    }

    private static int lineOf(String text, String needle) {
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++)
            if (lines[index].contains(needle)) return index + 1;
        throw new AssertionError(needle);
    }

    @Test
    public void buildCommitsTheWorkingCopyAsOneRevisionAndASuccessfulLastBuildIsReady()
            throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                args("path", SCREEN, "content", "class ListScreen {}"),
                                "edit_file",
                                args(
                                        "path",
                                        MAIN,
                                        "old_text",
                                        "increment.setText(\"Increment\");",
                                        "new_text",
                                        "increment.setText(\"Add one\");"),
                                "build",
                                "{}")
                        .answer("Built a list screen.");
        StepRun.Outcome outcome = run(script, builds(OK));
        assertEquals(List.of(1), built);
        assertEquals(1, source.revision());
        assertEquals("class ListScreen {}", stored(SCREEN));
        assertTrue(stored(MAIN).contains("Add one"));
        assertEquals("The build succeeded. The app is ready to install.", script.lastToolResult(1));
        assertNotNull(outcome.artifact);
        assertEquals(1, outcome.revision);
        assertEquals("Built a list screen.", outcome.reply);
        assertEquals(RunJournal.State.READY_TO_TEST, state());
        assertTrue(shown().get(2).getText().startsWith("Built on this phone in "));
        assertEquals(StepKind.DONE, shown().get(2).getKind());
    }

    @Test
    public void buildErrorsComeBackAsToolResultsAndAreFixedInTheSameRun() throws Exception {
        source();
        String broken =
                stored(MAIN)
                        .replace(
                                "label.setText(\"Counter: \" + count);\n    Button",
                                "label.setTxt(\"Counter: \" + count);\n    Button");
        int line = lineOf(broken, "label.setTxt(");
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", MAIN, "content", broken), "build", "{}")
                        .calls("read_build_errors", "{}")
                        .calls(
                                "edit_file",
                                args(
                                        "path",
                                        MAIN,
                                        "old_text",
                                        "label.setTxt(",
                                        "new_text",
                                        "label.setText("),
                                "build",
                                "{}")
                        .calls("build", "{}")
                        .answer("Fixed the build.");
        StepRun.Outcome outcome = run(script, builds(undefinedMethodOnLine(line), OK, OK));
        String errors = script.lastToolResult(1);
        assertTrue(errors, errors.startsWith("The build failed."));
        assertTrue(errors, errors.contains(MAIN + ":" + line + ": UndefinedMethod"));
        assertTrue(errors, errors.contains("label.setTxt(\"Counter: \" + count);"));
        assertEquals(errors, script.lastToolResult(2));
        assertEquals(List.of(1, 2, 2), built);
        assertEquals(2, source.revision());
        assertFalse(stored(MAIN).contains("setTxt"));
        assertEquals(RunJournal.State.READY_TO_TEST, state());
        assertNotNull(outcome.artifact);
        assertEquals(5, outcome.requests);
        Step failedBuild = shown().get(1);
        assertEquals(StepKind.FAILED, failedBuild.getKind());
        assertEquals("The build failed", failedBuild.getText());
        assertEquals("1 error", failedBuild.getDetail());
    }

    @Test
    public void aRunWhoseLastBuildFailedEndsWithoutAnApp() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel().calls("build", "{}").answer("I could not fix it.");
        StepRun.Outcome outcome = run(script, builds(undefinedMethodOnLine(3)));
        assertNull(outcome.artifact);
        assertEquals("I could not fix it.", outcome.reply);
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aBuildThatRunsOutOfStorageEndsTheRunWithoutAskingTheModelAgain() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel().calls("build", "{}").answer("I made the build smaller.");
        try {
            run(
                    script,
                    builds(
                            new java.io.IOException(
                                    "write failed: ENOSPC (No space left on device)")));
            fail("A full phone ends the run");
        } catch (StepRun.StorageFull full) {
            assertEquals(1, script.sent.size());
        }
        assertEquals(RunJournal.State.FAILED, state());
        assertEquals("Your phone's storage is full", shown().get(shown().size() - 1).getText());
    }

    @Test
    public void editsAfterTheLastBuildAreSavedWhenTheRunEndsButOfferNoApp() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .calls("build", "{}")
                        .calls("write_file", args("path", SCREEN, "content", "class Later {}"))
                        .answer("Added a screen; build it next time.");
        StepRun.Outcome outcome = run(script, builds(OK));
        assertEquals(List.of(0), built);
        assertEquals(1, source.revision());
        assertEquals("class Later {}", stored(SCREEN));
        assertNull(outcome.artifact);
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    @Test
    public void aStaleBaseRevisionIsRefusedAtTheStartBeforeAnyModelRequest() throws Exception {
        source();
        String approved = approved("Change it");
        source.writeSource(0, "class Other {}");
        ScriptedModel script = new ScriptedModel().answer("Never sent.");
        assertThrows(
                IllegalStateException.class,
                () ->
                        new StepRun(source, new StepRun.Control(), noLibraries())
                                .run(
                                        approved,
                                        0,
                                        () -> true,
                                        script.model(),
                                        noBuild(),
                                        journal(),
                                        steps));
        assertEquals(0, script.sent.size());
        assertEquals(RunJournal.State.FAILED, state());
    }

    @Test
    public void aCommitOnAStaleRevisionIsRefusedAndSavesNothing() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", SCREEN, "content", "class Mine {}"))
                        .calls("build", "{}")
                        .answer("Never sent.");
        int[] replies = {0};
        assertThrows(
                IllegalStateException.class,
                () ->
                        new StepRun(source, new StepRun.Control(), noLibraries())
                                .run(
                                        approved("Add a screen"),
                                        0,
                                        () -> true,
                                        script.model(
                                                () -> {
                                                    if (++replies[0] == 2) {
                                                        try {
                                                            source.writeSource(0, "class Other {}");
                                                        } catch (java.io.IOException failure) {
                                                            throw new AssertionError(failure);
                                                        }
                                                    }
                                                }),
                                        builds(OK),
                                        journal(),
                                        steps));
        assertEquals(List.of(), built);
        assertEquals(1, source.revision());
        assertEquals("class Other {}", stored(MAIN));
        assertNull(stored(SCREEN));
        assertEquals(RunJournal.State.FAILED, state());
        assertEquals(0, journal.get(journal.size() - 1).sourceRevision);
    }

    @Test
    public void buildWithoutPlatformFilesSavesTheWorkAndEndsTheRunAskingForThem() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                args("path", SCREEN, "content", "class Kept {}"),
                                "build",
                                "{}",
                                "read_file",
                                args("path", SCREEN))
                        .answer("Never sent.");
        StepRun.Outcome outcome = run(script, builds(new StepRun.PlatformFilesMissing()));
        assertTrue(outcome.platformNeeded);
        assertEquals(StepRun.PLATFORM_NEEDED, outcome.reply);
        assertNull(outcome.artifact);
        assertEquals(1, outcome.requests);
        assertEquals(1, script.remaining());
        assertEquals(1, source.revision());
        assertEquals("class Kept {}", stored(SCREEN));
        assertEquals(RunJournal.State.FAILED, state());
        assertEquals("Could not build", shown().get(1).getText());
        assertEquals(2, shown().size());
    }

    @Test
    public void errorsInAnyJavaFileComeBackWithTheirPathAndLine() throws Exception {
        source();
        String screen =
                "package dev.smallwright.generated.counter.ui;\n\nclass ListScreen {\n  int size() { return itms.size(); }\n}\n";
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", SCREEN, "content", screen), "build", "{}")
                        .answer("It does not build yet.");
        run(
                script,
                builds(
                        (java.util.concurrent.Callable<Exception>)
                                () -> undefinedMethodIn(SCREEN, 4)));
        String errors = script.lastToolResult(1);
        assertTrue(
                errors,
                errors.contains(
                        SCREEN + ":4: UndefinedMethod in: int size() { return itms.size(); }"));
    }

    private static final String MANIFEST = "AndroidManifest.xml";
    private static final String DETAIL =
            "java/dev/smallwright/generated/counter/DetailActivity.java";

    private String manifestWithSecondScreen() throws Exception {
        return stored(MANIFEST)
                .replace(
                        "<application",
                        "<uses-permission android:name=\"android.permission.INTERNET\"/>"
                                + "<application")
                .replace(
                        "</application>",
                        "<activity android:name=\".DetailActivity\"/></application>");
    }

    @Test
    public void theModelReadsAndWritesTheManifestAndABuildCommitsIt() throws Exception {
        source();
        String edited = manifestWithSecondScreen();
        ScriptedModel script =
                new ScriptedModel()
                        .calls("read_file", args("path", MANIFEST))
                        .calls("write_file", args("path", MANIFEST, "content", edited))
                        .calls("build", "{}")
                        .calls(
                                "write_file",
                                args(
                                        "path",
                                        DETAIL,
                                        "content",
                                        "package dev.smallwright.generated.counter;\n"
                                                + "class DetailActivity extends android.app.Activity {}\n"))
                        .calls("build", "{}")
                        .answer("Added a second screen.");
        StepRun.Outcome outcome = run(script, builds(OK));
        assertTrue(script.lastToolResult(1), script.lastToolResult(1).contains("<manifest"));
        assertTrue(
                script.lastToolResult(3), script.lastToolResult(3).contains("DetailActivity.java"));
        assertEquals(edited, stored(MANIFEST));
        assertEquals(List.of(1), built);
        assertNotNull(outcome.artifact);
    }

    @Test
    public void aRefusedManifestComesBackAsABuildResultAndNothingIsBuiltOrSaved() throws Exception {
        source();
        String shared =
                stored(MANIFEST).replace("<manifest ", "<manifest android:sharedUserId=\"x\" ");
        ScriptedModel script =
                new ScriptedModel()
                        .calls("write_file", args("path", MANIFEST, "content", shared))
                        .calls("build", "{}")
                        .calls("write_file", args("path", MANIFEST, "content", stored(MANIFEST)))
                        .calls("build", "{}")
                        .answer("Fixed.");
        run(script, builds(OK));
        assertTrue(script.lastToolResult(2), script.lastToolResult(2).contains("sharedUserId"));
        assertEquals(List.of(1), built);
        List<String> texts = new ArrayList<>();
        for (Step step : shown()) texts.add(step.getText());
        assertTrue(texts.toString(), texts.contains("The manifest was refused"));
    }

    @Test
    public void aProjectWithoutAManifestFileShowsTheGeneratedOneAndSavesItWithTheFirstChange()
            throws Exception {
        source();
        source.commit(0, java.util.Map.of(), java.util.Set.of(MANIFEST));
        restartJournal();
        assertNull(stored(MANIFEST));
        ScriptedModel script =
                new ScriptedModel()
                        .calls("read_file", args("path", MANIFEST))
                        .answer("It is fine.");
        StepRun.Outcome unchanged = run(script, noBuild());
        assertTrue(
                script.lastToolResult(1), script.lastToolResult(1).contains(source.packageName()));
        assertEquals(1, unchanged.revision);
        assertNull(stored(MANIFEST));
        restartJournal();

        ScriptedModel edit =
                new ScriptedModel()
                        .calls("write_file", args("path", SCREEN, "content", "package x;\n"))
                        .answer("Added.");
        run(edit, noBuild());
        assertEquals(source.generatedManifest(), stored(MANIFEST));
    }

    @Test
    public void mainActivityCanBeDeletedOnceTheLauncherPointsElsewhere() throws Exception {
        source();
        String moved = stored(MANIFEST).replace(".MainActivity", ".HomeActivity");
        ScriptedModel script =
                new ScriptedModel()
                        .calls("delete_file", args("path", MAIN))
                        .calls("write_file", args("path", MANIFEST, "content", moved))
                        .calls("delete_file", args("path", MAIN))
                        .answer("Moved the launcher.");
        run(script, noBuild());
        assertTrue(script.lastToolResult(1), script.lastToolResult(1).contains("launcher"));
        assertNull(stored(MAIN));
        assertEquals(moved, stored(MANIFEST));
    }

    private static final String NOTES = "assets/notes.txt";
    private static final int CONTEXT = 60_000;

    private static String notes(String first) {
        StringBuilder text = new StringBuilder("Note 1: keep the counter " + first + ".\n");
        for (int line = 2; line <= 300; line++)
            text.append("Note ").append(line).append(": keep the count across restarts.\n");
        return text.toString();
    }

    private static int estimatedTokens(JsonObject request) {
        int bytes =
                request.getAsJsonArray("messages").toString().getBytes(UTF_8).length
                        + request.getAsJsonArray("tools").toString().getBytes(UTF_8).length;
        return (bytes + 2) / 3;
    }

    private static String content(JsonObject request, int message) {
        return request.getAsJsonArray("messages")
                .get(message)
                .getAsJsonObject()
                .get("content")
                .getAsString();
    }

    @Test
    public void aRunThatOutgrowsTheModelsContextSummarizesItsEarlierStepsAndContinues()
            throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .context(CONTEXT)
                        .calls("write_file", args("path", NOTES, "content", notes("old")))
                        .calls(
                                "edit_file",
                                args(
                                        "path",
                                        NOTES,
                                        "old_text",
                                        "keep the counter old",
                                        "new_text",
                                        "keep the counter new"));
        for (int read = 0; read < READS; read++) script.calls("read_file", args("path", NOTES));
        script.calls(
                        "edit_file",
                        args(
                                "path",
                                MAIN,
                                "old_text",
                                "increment.setText(\"Increment\");",
                                "new_text",
                                "increment.setText(\"Add one\");"))
                .answer("Done.")
                .summaries("SUMMARY ONE: wrote the notes.", "SUMMARY TWO: read them again.");

        StepRun.Outcome outcome = run(script, noBuild());

        assertEquals("Done.", outcome.reply);
        assertEquals(0, script.remaining());
        assertEquals(script.sent.size(), outcome.requests);
        assertTrue(stored(MAIN).contains("increment.setText(\"Add one\");"));
        assertEquals(notes("new"), stored(NOTES));

        List<JsonObject> summaries = script.summaryRequests();
        assertEquals(2, summaries.size());
        for (JsonObject summary : summaries) {
            assertEquals(1, summary.getAsJsonArray("messages").size());
            assertTrue(content(summary, 0).contains("Make it better"));
        }
        assertTrue(content(summaries.get(0), 0).contains("write_file"));
        assertTrue(content(summaries.get(1), 0).contains("SUMMARY ONE: wrote the notes."));

        int limit = Compaction.limit(CONTEXT);
        List<JsonObject> requests = script.stepRequests();
        String first = content(requests.get(0), 0);
        int continued = 0;
        for (JsonObject request : requests) {
            assertTrue(estimatedTokens(request) + " > " + limit, estimatedTokens(request) <= limit);
            assertEquals(first, content(request, 0));
            JsonArray messages = request.getAsJsonArray("messages");
            if (messages.size() < 2
                    || !messages.get(1).getAsJsonObject().get("role").getAsString().equals("user"))
                continue;
            continued++;
            String note = content(request, 1);
            assertTrue(note, note.contains("SUMMARY"));
            assertTrue(note, note.contains("do not repeat it"));
            assertTrue(
                    note,
                    note.contains(
                            NOTES + " (" + notes("new").length() + " bytes, changed in this run)"));
            assertTrue(note, note.contains("Note 1: keep the counter new."));
            assertFalse(note, note.contains("Note 1: keep the counter old."));
            assertTrue(note, note.contains(MAIN + " ("));
            assertTrue(estimatedTokens(request) <= limit / 4 * 3);
        }
        assertTrue(continued > 0);
        assertTrue(
                content(requests.get(requests.size() - 1), 1)
                        .contains("SUMMARY TWO: read them again."));

        List<String> texts = new ArrayList<>();
        for (Step step : shown()) texts.add(step.getText());
        assertEquals(
                2, texts.stream().filter("Summarized earlier steps to make room"::equals).count());
        assertEquals(RunJournal.State.ANSWERED, state());
    }

    private static final int READS = 14;

    @Test
    public void theTokensTheModelReportsCountTowardTheLimitToo() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .context(CONTEXT)
                        .promptTokens(Compaction.limit(CONTEXT))
                        .calls("list_files", "{}")
                        .promptTokens(100)
                        .answer("It has three files.")
                        .summaries("Listed the files.");
        StepRun.Outcome outcome = run(script, noBuild());
        assertEquals("It has three files.", outcome.reply);
        assertEquals(1, script.summaryRequests().size());
        assertEquals(3, outcome.requests);
        JsonObject continued = script.stepRequests().get(1);
        assertEquals(2, continued.getAsJsonArray("messages").size());
        assertTrue(content(continued, 1).contains("Listed the files."));
    }

    @Test
    public void aSummaryThatFailsEndsTheRunAndSavesNothing() throws Exception {
        source();
        ScriptedModel script =
                new ScriptedModel()
                        .context(CONTEXT)
                        .promptTokens(Compaction.limit(CONTEXT))
                        .calls("write_file", args("path", SCREEN, "content", "class Lost {}"))
                        .answer("Never sent.");
        assertThrows(java.io.IOException.class, () -> run(script, noBuild()));
        assertEquals(0, source.revision());
        assertNull(stored(SCREEN));
        assertEquals(RunJournal.State.FAILED, state());
        Step last = shown().get(shown().size() - 1);
        assertEquals(StepKind.FAILED, last.getKind());
        assertEquals("Could not summarize earlier steps", last.getText());
    }

    private static AttachedImages pictures(int count, int size) {
        List<byte[]> picked = new ArrayList<>();
        for (int index = 0; index < count; index++)
            picked.add(AttachedImagesTest.jpeg(size, index));
        return new AttachedImages(picked);
    }

    private static void assertCarriesPictures(JsonObject request, AttachedImages images) {
        JsonArray parts =
                request.getAsJsonArray("messages")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("content");
        assertEquals(images.userContent("unused").getAsJsonArray().size(), parts.size());
        assertTrue(
                parts.get(0)
                        .getAsJsonObject()
                        .get("text")
                        .getAsString()
                        .contains("Make it better"));
        for (int index = 1; index < parts.size(); index++)
            assertEquals(
                    images.userContent("unused").getAsJsonArray().get(index), parts.get(index));
    }

    @Test
    public void attachedPicturesGoWithTheFirstMessageInEveryStepOfTheRun() throws Exception {
        source();
        AttachedImages images = pictures(2, 300);
        ScriptedModel model = new ScriptedModel().calls("list_files", "{}").answer("A red circle.");
        StepRun.Outcome outcome = run(model, noBuild(), images);
        assertEquals("A red circle.", outcome.reply);
        assertEquals(2, model.sent.size());
        for (JsonObject request : model.sent) assertCarriesPictures(request, images);
    }

    @Test
    public void fourLargePicturesDoNotMakeASmallContextRunSummarizeAtOnce() throws Exception {
        source();
        AttachedImages images = pictures(4, dev.smallwright.project.ChatStore.MAX_IMAGE_BYTES);
        ScriptedModel model =
                new ScriptedModel()
                        .context(dev.smallwright.agent.ModelChoice.UNKNOWN_CONTEXT_TOKENS)
                        .calls("list_files", "{}")
                        .answer("Four pictures.");
        StepRun.Outcome outcome = run(model, noBuild(), images);
        assertEquals("Four pictures.", outcome.reply);
        assertTrue(model.summaryRequests().isEmpty());
        assertEquals(2, model.stepRequests().size());
    }

    @Test
    public void aSummarizedRunKeepsThePicturesInItsFirstMessage() throws Exception {
        source();
        AttachedImages images = pictures(1, 500);
        ScriptedModel script =
                new ScriptedModel()
                        .context(CONTEXT)
                        .promptTokens(Compaction.limit(CONTEXT))
                        .calls("list_files", "{}")
                        .promptTokens(100)
                        .answer("It has three files.")
                        .summaries("Listed the files.");
        StepRun.Outcome outcome = run(script, noBuild(), images);
        assertEquals("It has three files.", outcome.reply);
        assertEquals(1, script.summaryRequests().size());
        assertTrue(content(script.summaryRequests().get(0), 0).contains("Make it better"));
        assertFalse(content(script.summaryRequests().get(0), 0).contains("base64"));
        for (JsonObject request : script.stepRequests()) assertCarriesPictures(request, images);
    }
}
