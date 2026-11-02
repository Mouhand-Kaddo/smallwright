package dev.smallwright.controller;

import static dev.smallwright.libraries.MavenFixture.dependencies;
import static dev.smallwright.libraries.MavenFixture.dependency;
import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.libraries.MavenFixture;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The library tools in a run, against a local fixture of the two Maven repositories. */
public final class LibraryRunTest {
    private static final String GSON = "com.google.code.gson:gson";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private MavenFixture fixture;
    private volatile LiveRun live;
    private volatile boolean stopped;
    private ProjectSourceStore source;
    private RunSteps steps;
    private final List<RunJournal.Snapshot> journal = new ArrayList<>();

    @Before
    public void start() throws Exception {
        fixture = new MavenFixture();
        source = new ProjectSourceStore(temporary.newFolder());
        source.initialize();
        journal.add(RunJournal.Snapshot.start("run_test", source.revision()));
        String metadata =
                "<metadata><versioning><versions><version>2.13.2</version>"
                        + "<version>2.14.0</version><version>2.15.0-rc1</version>"
                        + "</versions></versioning></metadata>";
        fixture.put(
                "central",
                "com/google/code/gson/gson/maven-metadata.xml",
                metadata.getBytes(StandardCharsets.UTF_8));
        fixture.jar(
                "central",
                GSON + ":2.14.0",
                dependencies(
                        dependency("com.google.errorprone:error_prone_annotations:2.48.0", "")));
        fixture.jar("central", GSON + ":2.13.2", "");
        fixture.jar("central", "com.google.errorprone:error_prone_annotations:2.48.0", "");
    }

    @After
    public void stop() {
        fixture.close();
    }

    private StepRun.Outcome run(ScriptedModel model) throws Exception {
        return run(model, ignored -> library -> true);
    }

    private StepRun.Outcome run(
            ScriptedModel model, Function<RunSteps, LibraryTools.Permission> permission)
            throws Exception {
        steps = new RunSteps("chat", "request", "Thinking", snapshot -> live = snapshot);
        ContextPreviewGate gate = new ContextPreviewGate(source);
        String request = "Store notes as JSON";
        String approved =
                gate.approve(gate.preview(request, source.revision()), request, source.revision());
        return new StepRun(
                        source,
                        new StepRun.Control(),
                        new LibraryTools(
                                fixture.repositories(temporary.newFolder()),
                                permission.apply(steps)))
                .run(
                        approved,
                        source.revision(),
                        () -> !stopped,
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

    private static String library(String name) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("library", name);
        return arguments.toString();
    }

    private String list() throws Exception {
        byte[] bytes = source.readRecoverySources(source.revision()).get("libraries.txt");
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private List<Step> shown() {
        return StepLog.decode(steps.saved());
    }

    @Test
    public void addsTheNewestStableVersionWithWhatItNeedsAndShowsItAsAStep() throws Exception {
        ScriptedModel model =
                new ScriptedModel().calls("add_library", library(GSON)).answer("Added Gson.");
        StepRun.Outcome outcome = run(model);
        String result = model.lastToolResult(1);
        assertTrue(result, result.contains("Added com.google.code.gson:gson:2.14.0"));
        assertTrue(result, result.contains("com.google.errorprone:error_prone_annotations:2.48.0"));
        Step step = shown().get(0);
        assertEquals(StepKind.DONE, step.getKind());
        assertEquals("Added library com.google.code.gson:gson 2.14.0", step.getText());
        assertEquals("Also com.google.errorprone:error_prone_annotations 2.48.0", step.getDetail());
        assertEquals(1, outcome.revision);
        assertEquals("com.google.code.gson:gson:2.14.0\n", list());
        assertTrue(
                model.messages(0).get(0).getAsJsonObject().get("content").getAsString(),
                model.messages(0)
                        .get(0)
                        .getAsJsonObject()
                        .get("content")
                        .getAsString()
                        .contains("add_library"));
    }

    @Test
    public void updatesAndRemovesALibraryAtAnExactVersion() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", library(GSON + ":2.13.2"))
                        .calls("add_library", library(GSON + ":2.14.0"))
                        .calls("remove_library", library(GSON))
                        .calls("add_library", library(GSON + ":2.13.2"))
                        .answer("Done.");
        run(model);
        assertTrue(
                model.lastToolResult(1),
                model.lastToolResult(1).contains("Added " + GSON + ":2.13.2"));
        assertTrue(
                model.lastToolResult(2),
                model.lastToolResult(2).contains("Changed " + GSON + " from 2.13.2 to 2.14.0"));
        assertTrue(model.lastToolResult(3), model.lastToolResult(3).contains("Removed " + GSON));
        List<Step> shown = shown();
        assertEquals("Added library com.google.code.gson:gson 2.13.2", shown.get(0).getText());
        assertEquals("", shown.get(0).getDetail());
        assertEquals("Changed library com.google.code.gson:gson to 2.14.0", shown.get(1).getText());
        assertEquals(
                "Also com.google.errorprone:error_prone_annotations 2.48.0",
                shown.get(1).getDetail());
        assertEquals("Removed library com.google.code.gson:gson", shown.get(2).getText());
        assertEquals("com.google.code.gson:gson:2.13.2\n", list());
    }

    @Test
    public void refusesDirectEditsOfTheListAndLibrariesItCannotUseAsToolResults() throws Exception {
        fixture.jar(
                "central",
                "org.example:needs-aar:1",
                dependencies(dependency("androidx.example:view:1", "")));
        fixture.aar("google", "androidx.example:view:1", "", "", java.util.Map.of());
        fixture.put(
                "google",
                "androidx/example/view/1/view-1.aar",
                dev.smallwright.libraries.MavenFixture.archive("no manifest"));
        fixture.jar("central", "org.example:tampered:1", "");
        fixture.putRaw(
                "central",
                "org/example/tampered/1/tampered-1.jar",
                MavenFixture.archive("other bytes"));
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                "{\"path\":\"libraries.txt\",\"content\":\"x:y:1\\n\"}")
                        .calls("add_library", library("org.example:needs-aar:1"))
                        .calls("add_library", library("org.example:tampered:1"))
                        .calls("add_library", library("https://jitpack.io/x"))
                        .calls("add_library", library("org.example:missing:1"))
                        .calls("remove_library", library(GSON))
                        .answer("I could not add those.");
        run(model);
        assertTrue(
                model.lastToolResult(1),
                model.lastToolResult(1)
                        .contains("changed only with add_library and remove_library"));
        assertTrue(model.lastToolResult(2), model.lastToolResult(2).contains("has no manifest"));
        assertTrue(model.lastToolResult(3), model.lastToolResult(3).contains("checksum"));
        assertTrue(
                model.lastToolResult(4), model.lastToolResult(4).contains("is not a library name"));
        assertTrue(
                model.lastToolResult(5),
                model.lastToolResult(5)
                        .contains("is not on Google's Maven repository or Maven Central"));
        assertTrue(
                model.lastToolResult(6), model.lastToolResult(6).contains("is not in the project"));
        List<Step> shown = shown();
        for (Step step : shown.subList(0, 6)) assertEquals(StepKind.FAILED, step.getKind());
        assertEquals("Could not add library org.example:needs-aar:1", shown.get(1).getText());
        assertNull(list());
        assertEquals(0, source.revision());
    }

    @Test
    public void aBuildIsRefusedWhenALibraryManifestBreaksTheManifestPolicy() throws Exception {
        fixture.aar(
                "google",
                "androidx.example:squat:1",
                "",
                "<permission android:name=\"com.bank.READ\"/>",
                java.util.Map.of());
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", library("androidx.example:squat:1"))
                        .calls("build", "{}")
                        .answer("It was refused.");
        run(model);
        String result = model.lastToolResult(2);
        assertTrue(result, result.contains("The build did not start"));
        assertTrue(result, result.contains("libraries' manifest entries merged in"));
        assertTrue(result, result.contains("<permission>"));
        assertFalse(shown().get(1).getText(), shown().get(1).getText().contains("Built"));
    }

    @Test
    public void searchesBothRepositories() throws Exception {
        fixture.putRaw(
                "search",
                "select?q=gson&rows=10&wt=json",
                ("{\"response\":{\"docs\":[{\"g\":\"com.google.code.gson\",\"a\":\"gson\","
                                + "\"latestVersion\":\"2.14.0\"},{\"g\":\"bad group\",\"a\":\"x\"}]}}")
                        .getBytes(StandardCharsets.UTF_8));
        fixture.putRaw(
                "google",
                "master-index.xml",
                "<metadata><androidx.gsonish/><androidx.core/></metadata>".getBytes());
        fixture.putRaw(
                "google",
                "androidx/gsonish/group-index.xml",
                "<androidx.gsonish><gsonish versions=\"1.0.0,1.1.0-alpha01\"/></androidx.gsonish>"
                        .getBytes());
        ScriptedModel model =
                new ScriptedModel()
                        .calls("search_libraries", "{\"query\":\"gson\"}")
                        .answer("Gson fits.");
        run(model);
        String result = model.lastToolResult(1);
        assertTrue(
                result,
                result.contains("com.google.code.gson:gson (newest 2.14.0), Maven Central"));
        assertTrue(
                result,
                result.contains(
                        "androidx.gsonish:gsonish (newest 1.0.0), Google's Maven repository"));
        assertFalse(result, result.contains("bad group"));
        assertEquals("Searched libraries for \"gson\"", shown().get(0).getText());
        assertEquals("2 found", shown().get(0).getDetail());
    }

    private CompletableFuture<StepRun.Outcome> runInBackground(
            ScriptedModel model, RunQuestion[] question) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return run(
                                model,
                                runSteps -> {
                                    question[0] = new RunQuestion(runSteps, () -> !stopped);
                                    return question[0];
                                });
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    }
                });
    }

    private void waitForQuestion(CompletableFuture<StepRun.Outcome> run) throws Exception {
        for (int waited = 0; waited < 500; waited++) {
            LiveRun shown = live;
            if (shown != null
                    && !shown.getSteps().isEmpty()
                    && shown.getSteps().get(shown.getSteps().size() - 1).getKind()
                            == StepKind.ASKING) return;
            assertFalse("The run ended without asking", run.isDone());
            Thread.sleep(10);
        }
        fail("The question never showed");
    }

    private boolean downloadedLibraryFiles() {
        for (String request : fixture.requests())
            if (request.endsWith(".jar") || request.endsWith(".pom")) return true;
        return false;
    }

    @Test
    public void withAskingOnTheRunWaitsAndDownloadsNothingUntilThePersonSaysYes() throws Exception {
        ScriptedModel model =
                new ScriptedModel().calls("add_library", library(GSON)).answer("Added Gson.");
        RunQuestion[] question = new RunQuestion[1];
        CompletableFuture<StepRun.Outcome> run = runInBackground(model, question);
        waitForQuestion(run);
        Thread.sleep(600);

        List<Step> waiting = live.getSteps();
        Step asked = waiting.get(waiting.size() - 1);
        assertEquals("Add the library Gson (com.google.code.gson:gson 2.14.0)?", asked.getText());
        assertEquals("It may bring in other libraries.", asked.getDetail());
        assertFalse(run.isDone());
        assertFalse(fixture.requests().toString(), downloadedLibraryFiles());
        assertEquals(1, model.sent.size());

        question[0].answer(true);
        StepRun.Outcome outcome = run.get();

        assertTrue(downloadedLibraryFiles());
        assertEquals("com.google.code.gson:gson:2.14.0\n", list());
        assertEquals(1, outcome.revision);
        assertTrue(model.lastToolResult(1), model.lastToolResult(1).contains("Added " + GSON));
        assertEquals("Added library com.google.code.gson:gson 2.14.0", shown().get(0).getText());
    }

    @Test
    public void sayingNoDownloadsNothingAndTellsTheModelTheRunGoesOn() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", library(GSON))
                        .answer("I will use plain JSON handling.");
        RunQuestion[] question = new RunQuestion[1];
        CompletableFuture<StepRun.Outcome> run = runInBackground(model, question);
        waitForQuestion(run);
        question[0].answer(false);
        StepRun.Outcome outcome = run.get();

        assertFalse(fixture.requests().toString(), downloadedLibraryFiles());
        assertNull(list());
        assertEquals(0, outcome.revision);
        assertEquals("I will use plain JSON handling.", outcome.reply);
        assertTrue(
                model.lastToolResult(1),
                model.lastToolResult(1)
                        .contains("The person chose not to add " + GSON + ":2.14.0"));
        Step step = shown().get(0);
        assertEquals(StepKind.DONE, step.getKind());
        assertEquals("Did not add library com.google.code.gson:gson 2.14.0", step.getText());
        assertEquals(2, model.sent.size());
    }

    @Test
    public void stopWhileWaitingEndsTheRunWithoutDownloadingAnything() throws Exception {
        ScriptedModel model = new ScriptedModel().calls("add_library", library(GSON));
        CompletableFuture<StepRun.Outcome> run = runInBackground(model, new RunQuestion[1]);
        waitForQuestion(run);
        stopped = true;

        try {
            run.get();
            fail("The run should have stopped");
        } catch (java.util.concurrent.ExecutionException ended) {
            assertEquals("Run stopped", ended.getCause().getCause().getMessage());
        }
        assertFalse(downloadedLibraryFiles());
        assertEquals(RunJournal.State.CANCELLED, journal.get(journal.size() - 1).state);
    }

    @Test
    public void aLibraryAlreadyInTheProjectAsksNothing() throws Exception {
        ScriptedModel model =
                new ScriptedModel()
                        .calls("add_library", library(GSON + ":2.14.0"))
                        .calls("add_library", library(GSON + ":2.14.0"))
                        .answer("Done.");
        int[] asked = {0};
        run(
                model,
                runSteps ->
                        library -> {
                            asked[0]++;
                            return true;
                        });

        assertEquals(1, asked[0]);
        assertTrue(
                model.lastToolResult(2),
                model.lastToolResult(2).contains("is already in the project"));
    }
}
