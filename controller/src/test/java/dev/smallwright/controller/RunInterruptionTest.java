package dev.smallwright.controller;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.controller.ui.StepLog;
import dev.smallwright.project.ChatStore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * A run killed at each step boundary, then reopened as the controller does at startup: the journal
 * closes the run at the project's revision and the chat is told what happened.
 */
public final class RunInterruptionTest {
    private static final String PROJECT = "counter-alpha";
    private static final String MAIN = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String SCREEN =
            "java/dev/smallwright/generated/counter/ui/ListScreen.java";
    private static final String NOTE = "java/dev/smallwright/generated/counter/ui/Note.java";
    private static final String REQUEST = "Add a list screen";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    /** What process death does to the run's thread: nothing after it runs. */
    private static final class ProcessDeath extends Error {}

    private File files;
    private ProjectSourceStore source;
    private ChatStore chats;
    private String chatId;
    private List<RunJournal.Snapshot> journal;
    private RunJournal.Progress progress;
    private int boundary;
    private int deathAt;
    private int versions;
    private final Map<Integer, Map<String, byte[]>> revisions = new HashMap<>();

    private void boundary() {
        if (boundary++ == deathAt) throw new ProcessDeath();
    }

    private static String args(String... pairs) {
        JsonObject object = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2)
            object.addProperty(pairs[index], pairs[index + 1]);
        return object.toString();
    }

    private static ScriptedModel fixesItsBuildThenEndsWithUnsavedEdits() {
        return new ScriptedModel()
                .calls(
                        "write_file",
                        args("path", SCREEN, "content", "class ListScreen {}\n"),
                        "build",
                        "{}")
                .calls(
                        "edit_file",
                        args(
                                "path",
                                MAIN,
                                "old_text",
                                "increment.setText(\"Increment\");",
                                "new_text",
                                "increment.setText(\"Add one\");"))
                .calls("build", "{}")
                .calls("write_file", args("path", NOTE, "content", "class Note {}\n"))
                .answer("Added a list screen and a note.");
    }

    private static ScriptedModel endsWithASuccessfulBuild() {
        return new ScriptedModel()
                .calls("write_file", args("path", SCREEN, "content", "class ListScreen {}\n"))
                .calls("build", "{}")
                .answer("Built a list screen.");
    }

    private static final String NOTES = "assets/notes.txt";

    private static ScriptedModel summarizesItsEarlierStepsThenEndsWithUnsavedEdits() {
        StringBuilder notes = new StringBuilder();
        for (int line = 1; line <= 300; line++)
            notes.append("Note ").append(line).append(": keep the count across restarts.\n");
        ScriptedModel script =
                new ScriptedModel()
                        .context(60_000)
                        .calls(
                                "write_file",
                                args("path", NOTES, "content", notes.toString()),
                                "build",
                                "{}");
        for (int read = 0; read < 14; read++) script.calls("read_file", args("path", NOTES));
        return script.calls(
                        "edit_file",
                        args(
                                "path",
                                MAIN,
                                "old_text",
                                "increment.setText(\"Increment\");",
                                "new_text",
                                "increment.setText(\"Add one\");"))
                .answer("Done.")
                .summaries("SUMMARY ONE.", "SUMMARY TWO.", "SUMMARY THREE.");
    }

    private StepRun.Build builds(boolean... succeeds) {
        int[] next = {0};
        return (revision, buildId) -> {
            boundary();
            if (!succeeds[next[0]++]) throw new java.io.IOException("Build failed");
            File apk = temporary.newFile();
            Files.write(apk.toPath(), ("apk of revision " + revision).getBytes());
            return VerifiedArtifact.capture(apk);
        };
    }

    private StepRun.Journal journal() {
        return (next, revision, buildId, tokens) -> {
            boundary();
            RunJournal.Snapshot last = journal.get(journal.size() - 1);
            journal.add(
                    last.advance(
                            next,
                            revision,
                            buildId,
                            tokens,
                            next == RunJournal.State.BUILDING ? ++versions : null));
            try {
                revisions.putIfAbsent(
                        source.revision(), source.readRecoverySources(source.revision()));
            } catch (java.io.IOException unreadable) {
                throw new AssertionError(unreadable);
            }
            boundary();
        };
    }

    private RunJournal.Snapshot latest() {
        return journal.get(journal.size() - 1);
    }

    private void project() throws Exception {
        files = temporary.newFolder();
        source = new ProjectSourceStore(files);
        source.initialize();
        File chatRoot = new File(files, "chats");
        assertTrue(chatRoot.mkdir());
        chats = new ChatStore(chatRoot);
        chatId = chats.createChat(PROJECT, "App ideas").id;
        chats.appendMessage(PROJECT, chatId, "user", "Hello", 0);
        chats.appendMessage(PROJECT, chatId, "assistant", "Hi.", 0);
        journal = new ArrayList<>();
        journal.add(RunJournal.Snapshot.start("run_interrupted", source.revision()));
        versions = 0;
        progress =
                new RunJournal.Progress(
                        "run_interrupted",
                        chatId,
                        REQUEST,
                        chats.listMessages(PROJECT, chatId).size(),
                        InterruptedRuns.steps(
                                List.of(new Step(StepKind.THINKING, "Thinking", ""))));
    }

    /**
     * One Send as the coordinator runs it: the run, then its chat messages, then the progress is
     * forgotten. Returns false when the process died first.
     */
    private boolean send(Supplier<ScriptedModel> script, StepRun.Build build) throws Exception {
        ScriptedModel model = script.get();
        StepRun.Model asks = before(model.model(this::boundary), this::boundary);
        RunSteps steps =
                new RunSteps(
                        chatId,
                        REQUEST,
                        "Thinking",
                        live -> {
                            progress =
                                    new RunJournal.Progress(
                                            progress.runId,
                                            progress.chatId,
                                            progress.request,
                                            progress.chatSize,
                                            InterruptedRuns.steps(live.getSteps()));
                            boundary();
                        });
        try {
            ContextPreviewGate gate = new ContextPreviewGate(source);
            int base = source.revision();
            StepRun.Outcome outcome =
                    new StepRun(source, new StepRun.Control(), noLibraries())
                            .run(
                                    gate.approve(gate.preview(REQUEST, base), REQUEST, base),
                                    base,
                                    () -> true,
                                    asks,
                                    build,
                                    journal(),
                                    steps);
            chats.appendMessage(PROJECT, chatId, "user", REQUEST, outcome.revision);
            boundary();
            chats.appendMessage(PROJECT, chatId, "tool", steps.saved(), outcome.revision);
            boundary();
            chats.appendMessage(PROJECT, chatId, "assistant", outcome.reply, outcome.revision);
            boundary();
            if (outcome.artifact != null) {
                chats.appendMessage(
                        PROJECT, chatId, "status", "Your app is ready.", outcome.revision);
                boundary();
            }
            progress = null;
            return true;
        } catch (ProcessDeath died) {
            return false;
        }
    }

    /** The same model, with something happening before each request. */
    private static StepRun.Model before(StepRun.Model model, Runnable beforeEachRequest) {
        return new StepRun.Model() {
            @Override
            public dev.smallwright.agent.ModelClient.StepReply next(
                    com.google.gson.JsonArray messages, int promptTokens) throws Exception {
                beforeEachRequest.run();
                return model.next(messages, promptTokens);
            }

            @Override
            public dev.smallwright.agent.ModelClient.StepReply summarize(
                    com.google.gson.JsonArray messages, int promptTokens) throws Exception {
                beforeEachRequest.run();
                return model.summarize(messages, promptTokens);
            }

            @Override
            public int contextTokens() {
                return model.contextTokens();
            }
        };
    }

    private LibraryTools noLibraries() throws Exception {
        return new LibraryTools(unreachableRepositories());
    }

    private dev.smallwright.libraries.MavenRepositories unreachableRepositories() throws Exception {
        return new dev.smallwright.libraries.MavenRepositories(
                temporary.newFolder(),
                new dev.smallwright.libraries.MavenRepositories.Endpoints(
                        "http://127.0.0.1:9/google/",
                        "http://127.0.0.1:9/central/",
                        "http://127.0.0.1:9/search/select"));
    }

    /** What the controller does when it opens the project again after process death. */
    private RunJournal.Snapshot reopen() throws Exception {
        RunJournal.Snapshot reopened = latest().recover(source.revision());
        if (reopened != latest()) journal.add(reopened);
        if (progress != null) InterruptedRuns.save(chats, PROJECT, progress, reopened);
        return reopened;
    }

    private Map<Integer, Map<String, byte[]>> committedRevisions(
            Supplier<ScriptedModel> script, boolean... builds) throws Exception {
        project();
        deathAt = -1;
        revisions.clear();
        assertTrue(send(script, builds(builds)));
        assertEquals(revisions.keySet().size(), source.revision() + 1);
        return new HashMap<>(revisions);
    }

    private static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(path, expected.get(path), actual.get(path));
    }

    private int killAtEveryBoundary(Supplier<ScriptedModel> script, boolean... builds)
            throws Exception {
        Map<Integer, Map<String, byte[]>> committed = committedRevisions(script, builds);
        int killed = 0;
        for (deathAt = 0; ; deathAt++) {
            project();
            boundary = 0;
            if (send(script, builds(builds))) break;
            killed++;
            String where = "killed at boundary " + deathAt;
            List<ChatStore.Message> before = chats.listMessages(PROJECT, chatId);
            String shownSteps = progress.steps;

            RunJournal.Snapshot reopened = reopen();

            int revision = source.revision();
            assertEquals(where, revision, reopened.sourceRevision);
            assertTrue(
                    where,
                    !reopened.isActive()
                            || reopened.state == RunJournal.State.BUILDING
                                    && reopened.versionCode != null);
            assertSame(where, reopened, reopened.recover(revision));
            assertTrue(where, committed.containsKey(revision));
            assertSameFiles(committed.get(revision), source.readRecoverySources(revision));

            List<ChatStore.Message> after = chats.listMessages(PROJECT, chatId);
            assertEquals(where, "Hello", after.get(0).text);
            assertEquals(where, "Hi.", after.get(1).text);
            assertTrue(where, after.size() >= 5);
            assertEquals(where, "user", after.get(2).role);
            assertEquals(where, REQUEST, after.get(2).text);
            assertEquals(where, "tool", after.get(3).role);
            if (before.size() <= 3) assertEquals(where, shownSteps, after.get(3).text);
            ChatStore.Message closing = after.get(4);
            if (before.size() <= 4) {
                assertEquals(where, 5, after.size());
                assertEquals(where, "status", closing.role);
                assertEquals(
                        where,
                        reopened.state == RunJournal.State.BUILDING
                                ? InterruptedRuns.REBUILDING
                                : reopened.state == RunJournal.State.READY_TO_TEST
                                                || reopened.state == RunJournal.State.ANSWERED
                                        ? InterruptedRuns.REPLY_UNSAVED
                                        : InterruptedRuns.ENDED,
                        closing.text);
                assertEquals(where, revision, closing.sourceRevision);
            } else {
                assertEquals(where, before.size(), after.size());
            }

            InterruptedRuns.save(chats, PROJECT, progress, reopened);
            assertEquals(where, after.size(), chats.listMessages(PROJECT, chatId).size());
        }
        return killed;
    }

    @Test
    public void aRunThatStoppedOnAFullPhoneSaysSoWhenItsChatIsWrittenLater() throws Exception {
        project();
        RunJournal.Progress full =
                new RunJournal.Progress(
                        "run_full",
                        chatId,
                        REQUEST,
                        chats.listMessages(PROJECT, chatId).size(),
                        InterruptedRuns.steps(
                                List.of(
                                        new Step(StepKind.DONE, "Read 3 files", ""),
                                        new Step(StepKind.FAILED, StepRun.STORAGE_FULL_STEP, ""))));
        RunJournal.Snapshot failed =
                RunJournal.Snapshot.start("run_full", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.FAILED, 0, null, null);
        InterruptedRuns.save(chats, PROJECT, full, failed);
        List<ChatStore.Message> after = chats.listMessages(PROJECT, chatId);
        assertEquals(REQUEST, after.get(after.size() - 3).text);
        assertEquals(GenerationFailure.STORAGE_FULL, after.get(after.size() - 1).text);
    }

    @Test
    public void aRunKilledAtAnyStepBoundaryReopensAtItsLastCommittedRevision() throws Exception {
        int killed =
                killAtEveryBoundary(
                        RunInterruptionTest::fixesItsBuildThenEndsWithUnsavedEdits, false, true);
        assertTrue("boundaries exercised: " + killed, killed > 30);
    }

    @Test
    public void aRunKilledAsItsLastBuildFinishesReopensConsistently() throws Exception {
        int killed = killAtEveryBoundary(RunInterruptionTest::endsWithASuccessfulBuild, true);
        assertTrue("boundaries exercised: " + killed, killed > 15);
    }

    @Test
    public void aRunKilledWhileItSummarizesItsEarlierStepsReopensConsistently() throws Exception {
        ScriptedModel whole = summarizesItsEarlierStepsThenEndsWithUnsavedEdits();
        project();
        deathAt = -1;
        assertTrue(send(() -> whole, builds(true)));
        assertFalse(whole.summaryRequests().isEmpty());
        int killed =
                killAtEveryBoundary(
                        RunInterruptionTest::summarizesItsEarlierStepsThenEndsWithUnsavedEdits,
                        true);
        assertTrue("boundaries exercised: " + killed, killed > 40);
    }

    @Test
    public void aRunKilledWhileItAsksAboutALibraryReopensAtItsLastCommittedRevision()
            throws Exception {
        project();
        deathAt = -1;
        ScriptedModel model =
                new ScriptedModel()
                        .calls(
                                "write_file",
                                args("path", SCREEN, "content", "class ListScreen {}\n"),
                                "build",
                                "{}")
                        .calls(
                                "edit_file",
                                args(
                                        "path",
                                        MAIN,
                                        "old_text",
                                        "increment.setText(\"Increment\");",
                                        "new_text",
                                        "increment.setText(\"Add one\");"))
                        .calls("add_library", args("library", "com.google.code.gson:gson:2.13.2"))
                        .answer("Never sent.");
        RunSteps steps =
                new RunSteps(
                        chatId,
                        REQUEST,
                        "Thinking",
                        live ->
                                progress =
                                        new RunJournal.Progress(
                                                progress.runId,
                                                progress.chatId,
                                                progress.request,
                                                progress.chatSize,
                                                InterruptedRuns.steps(live.getSteps())));
        RunQuestion dies =
                new RunQuestion(
                        steps,
                        () -> {
                            throw new ProcessDeath();
                        });
        ContextPreviewGate gate = new ContextPreviewGate(source);
        assertThrows(
                ProcessDeath.class,
                () ->
                        new StepRun(
                                        source,
                                        new StepRun.Control(),
                                        new LibraryTools(unreachableRepositories(), dies))
                                .run(
                                        gate.approve(gate.preview(REQUEST, 0), REQUEST, 0),
                                        0,
                                        () -> true,
                                        model.model(),
                                        builds(true),
                                        journal(),
                                        steps));
        assertEquals(3, model.sent.size());

        RunJournal.Snapshot reopened = reopen();

        assertEquals(RunJournal.State.FAILED, reopened.state);
        assertEquals(1, reopened.sourceRevision);
        assertEquals(1, source.revision());
        assertTrue(
                new String(source.readRecoverySources(1).get(MAIN))
                        .contains("increment.setText(\"Increment\");"));
        List<ChatStore.Message> chat = chats.listMessages(PROJECT, chatId);
        List<Step> shown = StepLog.decode(chat.get(3).text);
        Step asked = shown.get(shown.size() - 1);
        assertTrue(asked.getText(), asked.getText().startsWith("Add the library Gson"));
        assertEquals(StepKind.FAILED, asked.getKind());
        assertEquals(InterruptedRuns.ENDED, chat.get(4).text);
        assertEquals(5, chat.size());
    }

    @Test
    public void reopeningNeverAsksTheModelAgainAndAnEarlierEditIsNotHalfSaved() throws Exception {
        project();
        deathAt = -1;
        ScriptedModel model = fixesItsBuildThenEndsWithUnsavedEdits();
        int[] asked = {0};
        StepRun.Model asks =
                before(
                        model.model(),
                        () -> {
                            if (++asked[0] == 3) throw new ProcessDeath();
                        });
        ContextPreviewGate gate = new ContextPreviewGate(source);
        assertThrows(
                ProcessDeath.class,
                () ->
                        new StepRun(source, new StepRun.Control(), noLibraries())
                                .run(
                                        gate.approve(gate.preview(REQUEST, 0), REQUEST, 0),
                                        0,
                                        () -> true,
                                        asks,
                                        builds(false),
                                        journal(),
                                        new RunSteps(chatId, REQUEST, "Thinking", live -> {})));
        assertEquals(1, source.revision());
        assertEquals(2, model.sent.size());

        RunJournal.Snapshot reopened = reopen();

        assertEquals(2, model.sent.size());
        assertEquals(RunJournal.State.FAILED, reopened.state);
        assertEquals(1, reopened.sourceRevision);
        assertTrue(
                new String(source.readRecoverySources(1).get(MAIN))
                        .contains("increment.setText(\"Increment\");"));
        assertEquals(InterruptedRuns.ENDED, chats.listMessages(PROJECT, chatId).get(4).text);
    }

    @Test
    public void aRevisionLeftHalfPublishedByProcessDeathDoesNotBreakTheNextRun() throws Exception {
        project();
        Path revisions = files.toPath().resolve("projects/counter-alpha/revision-store/revisions");
        copy(revisions.resolve("0"), revisions.resolve("1"));
        assertEquals(0, source.revision());
        RunJournal.Snapshot applying =
                RunJournal.Snapshot.start("run_half", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, null)
                        .advance(RunJournal.State.APPLYING, 0, null, null);
        assertEquals(RunJournal.State.FAILED, applying.recover(0).state);

        deathAt = -1;
        assertTrue(send(RunInterruptionTest::endsWithASuccessfulBuild, builds(true)));
        assertEquals(2, source.revision());
        assertEquals(RunJournal.State.READY_TO_TEST, latest().state);
        assertEquals(2, latest().sourceRevision);
    }

    @Test
    public void stepsKeptForTheChatFitOneMessageAndKeepTheNewest() {
        List<Step> many = new ArrayList<>();
        for (int index = 0; index < 400; index++)
            many.add(new Step(StepKind.DONE, "Read file number " + index + ".java", ""));
        many.add(new Step(StepKind.RUNNING, "Building on this phone", ""));
        String kept = InterruptedRuns.steps(many);
        assertTrue(
                kept.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        <= InterruptedRuns.MAX_ROW_BYTES);
        List<Step> decoded = StepLog.decode(kept);
        assertEquals("Building on this phone", decoded.get(decoded.size() - 1).getText());
        assertEquals(StepKind.FAILED, decoded.get(decoded.size() - 1).getKind());
        assertFalse(kept.contains("Read file number 0.java"));
        assertEquals(
                StepLog.encode(many.subList(390, many.size())),
                InterruptedRuns.steps(many.subList(390, many.size())));
        String oversized =
                InterruptedRuns.steps(List.of(new Step(StepKind.DONE, "x".repeat(9000), "")));
        assertEquals("Did not finish", StepLog.decode(oversized).get(0).getText());
    }

    private static void copy(Path from, Path to) throws Exception {
        try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
            for (Path path : (Iterable<Path>) walk::iterator)
                Files.copy(path, to.resolve(from.relativize(path).toString()));
        }
    }
}
