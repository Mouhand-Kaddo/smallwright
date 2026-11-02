package dev.smallwright.controller;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class BuilderChangesTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ProjectIdentity identity;
    private ProjectSourceStore source;
    private ChatStore chats;
    private ChatStore.Chat chat;
    private BuilderChanges changes;

    @Before
    public void project() throws Exception {
        File files = temporaryFolder.newFolder("files");
        identity = new ProjectRegistry(new File(files, "projects")).create("Counter");
        source = new ProjectSourceStore(identity);
        source.initialize();
        chats = new ChatStore(new File(files, "chats"));
        chat = chats.createChat(identity.id(), "Chat 1");
        changes = new BuilderChanges(source, chats);
    }

    @Test
    public void undoCommitsANewRevisionWithTheFilesFromBeforeTheLatestRun() throws Exception {
        run("Add a reset button", "reset");
        ChatStore.Message blue = run("Make it blue", "blue");

        BuilderChanges.Undoable latest = changes.undoable(2);
        assertEquals("Make it blue", latest.change().request());
        assertEquals(blue.id, latest.change().messageId());
        assertEquals(chat.id, latest.change().chatId());
        assertTrue(latest.newest());

        BuilderChanges.Undone undone = undo(2);

        assertEquals(3, undone.revision());
        assertEquals(3, source.revision());
        assertSameFiles(source.readRevision(1), source.readRecoverySources(3));
        assertEquals("blue", text(source.readRevision(2).get(source.mainPath())));
    }

    @Test
    public void undoingAgainWalksBackOneChangeAtATimeUntilNothingIsLeft() throws Exception {
        Map<String, byte[]> start = source.readRecoverySources(0);
        run("Add a reset button", "reset");
        run("Make it blue", "blue");
        undo(2);

        BuilderChanges.Undoable before = changes.undoable(3);
        assertEquals("Add a reset button", before.change().request());
        assertFalse(before.newest());

        assertEquals(4, undo(3).revision());
        assertSameFiles(start, source.readRecoverySources(4));
        assertNull(changes.undoable(4));
        assertThrows(BuilderChanges.Refused.class, () -> undo(4));
        assertEquals(4, source.revision());
    }

    @Test
    public void aRunThatPutTheFilesBackIsUndoneOnlyOnce() throws Exception {
        run("Make it blue", "blue");
        run("Put it back", text(source.readRevision(0).get(source.mainPath())));
        undo(2);
        assertEquals("Make it blue", changes.undoable(3).change().request());
        undo(3);
        assertNull(changes.undoable(4));
    }

    @Test
    public void undoIsRefusedDuringARunWithUnsavedEditsOrWithNothingToUndo() throws Exception {
        assertNull(changes.undoable(0));
        assertNotNull(BuilderChanges.refusal(false, false, null));
        run("Make it blue", "blue");
        BuilderChanges.Undoable latest = changes.undoable(1);

        assertNull(BuilderChanges.refusal(false, false, latest));
        String running = BuilderChanges.refusal(true, false, latest);
        String edited = BuilderChanges.refusal(false, true, latest);
        assertNotNull(running);
        assertNotNull(edited);
        assertNotEquals(running, edited);

        assertThrows(IllegalStateException.class, () -> undo(0));
        assertEquals(1, source.revision());
    }

    @Test
    public void aChangeMadeOutsideARunIsNotUndoneThroughTheBuilder() throws Exception {
        run("Make it blue", "blue");
        source.commit(1, Map.of(source.mainPath(), bytes("my own edit")), Set.of());
        assertNull(changes.undoable(2));
        assertThrows(BuilderChanges.Refused.class, () -> undo(2));
    }

    @Test
    public void theChatNoteIsSavedAndReachesLaterModelRequests() throws Exception {
        run("Make it blue", "blue");
        assertTrue(undo(1).noted());

        List<ChatStore.Message> messages = chats.listMessages(identity.id(), chat.id);
        ChatStore.Message note = messages.get(messages.size() - 1);
        assertEquals(ChatStore.UNDO, note.role);
        assertEquals(2, note.sourceRevision);
        assertTrue(note.text.contains("\"Make it blue\""));

        ContextPreviewGate gate = new ContextPreviewGate(source, chats, identity.id());
        String sent = userRequest(gate.previewChat(chat.id, "Add a title", 2));
        assertTrue(sent.contains(note.text));
        assertTrue(sent.indexOf(note.text) > sent.indexOf("Make it blue"));
    }

    @Test
    public void aFullChatStillGetsTheUndoButSaysTheNoteIsMissing() throws Exception {
        run("Make it blue", "blue");
        while (chats.listMessages(identity.id(), chat.id).size() < 100)
            chats.appendMessage(identity.id(), chat.id, "status", "Your app is ready.", 1);

        BuilderChanges.Undone undone = undo(1);

        assertFalse(undone.noted());
        assertEquals(2, source.revision());
        assertSameFiles(source.readRevision(0), source.readRecoverySources(2));
    }

    @Test
    public void aSendPreparedBeforeTheUndoIsRefusedAsStale() throws Exception {
        run("Make it blue", "blue");
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, identity.id());
        ContextPreviewGate.Preview prepared = gate.previewChat(chat.id, "Add a title", 1);

        undo(1);

        assertThrows(IllegalStateException.class, () -> gate.approve(prepared, "Add a title", 1));
        assertThrows(IllegalStateException.class, () -> gate.previewChat(chat.id, "Add", 1));
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(prepared, "Add a title", source.revision()));
    }

    @Test
    public void theSignedAppIsKeptWhenItWasBuiltFromTheRestoredFiles() throws Exception {
        run("Add a reset button", "reset");
        VerifiedArtifact built = signedApp(1, 4);
        run("Make it blue", "blue");

        BuilderChanges.Undone undone = undo(2);

        assertTrue(undone.artifactKept());
        SignedArtifactReceipt.Record kept =
                SignedArtifactReceipt.read(
                        identity.directory(), identity.id(), identity.packageName(), 3);
        assertEquals(built.sha256(), kept.sha256);
        assertEquals(4, kept.versionCode);
    }

    @Test
    public void theUndoneBuildIsNotOfferedForTheRestoredFiles() throws Exception {
        run("Add a reset button", "reset");
        signedApp(1, 4);
        run("Make it blue", "blue");
        signedApp(2, 5);

        BuilderChanges.Undone undone = undo(2);

        assertFalse(undone.artifactKept());
        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                identity.directory(), identity.id(), identity.packageName(), 3));
    }

    @Test
    public void aChangeWhoseSummaryWasNotSavedCanStillBeUndone() throws Exception {
        int after = source.commit(0, Map.of(source.mainPath(), bytes("blue")), Set.of());
        changes.recordRun(0, after, chat.id, null, "Make it blue", 1_000L);

        BuilderChanges.Undoable latest = new BuilderChanges(source, chats).undoable(after);
        assertEquals("Make it blue", latest.change().request());
        assertNull(latest.change().messageId());

        assertEquals(2, undo(after).revision());
        assertSameFiles(source.readRevision(0), source.readRecoverySources(2));
    }

    @Test
    public void anUnreadableHistoryIsSetAsideAndANewOneStarts() throws Exception {
        run("Make it blue", "blue");
        File history = new File(identity.directory(), "builder-changes.txt");
        Files.write(
                history.toPath(), bytes("not a change\n"), java.nio.file.StandardOpenOption.APPEND);
        byte[] unreadable = Files.readAllBytes(history.toPath());

        assertNull(changes.undoable(1));
        assertArrayEquals(
                unreadable,
                Files.readAllBytes(
                        new File(identity.directory(), "builder-changes.txt.invalid").toPath()));

        run("Add a title", "title");
        assertEquals("Add a title", changes.undoable(2).change().request());
        assertEquals(3, undo(2).revision());
        assertSameFiles(source.readRevision(1), source.readRecoverySources(3));
    }

    @Test
    public void undoIsRefusedWhenTheChangeIsNoLongerTheOneShown() throws Exception {
        run("Make it blue", "blue");
        BuilderChanges.Change shown = changes.undoable(1).change();
        changes.recordRun(0, 1, chat.id, null, "Something else", 2_000L);

        assertThrows(BuilderChanges.Refused.class, () -> changes.undo(1, shown));
        assertEquals(1, source.revision());
        assertEquals("Something else", changes.undoable(1).change().request());
    }

    @Test
    public void theRevisionAnUndoMadeIsKnownAfterARestart() throws Exception {
        run("Make it blue", "blue");
        assertFalse(changes.undidAt(1));

        int undone = undo(1).revision();

        assertTrue(new BuilderChanges(source, chats).undidAt(undone));
        run("Add a title", "title");
        assertFalse(new BuilderChanges(source, chats).undidAt(source.revision()));
    }

    private BuilderChanges.Undone undo(int revision) throws Exception {
        BuilderChanges.Undoable shown = changes.undoable(revision);
        return changes.undo(revision, shown == null ? null : shown.change());
    }

    private ChatStore.Message run(String request, String main) throws IOException {
        int before = source.revision();
        int after = source.commit(before, Map.of(source.mainPath(), bytes(main)), Set.of());
        chats.appendMessage(identity.id(), chat.id, "user", request, after);
        ChatStore.Message summary =
                chats.appendMessage(identity.id(), chat.id, "assistant", "Done.", after);
        changes.recordRun(before, after, chat.id, summary.id, request, 1_000L);
        return summary;
    }

    private VerifiedArtifact signedApp(int revision, int version) throws IOException {
        File artifacts = new File(identity.directory(), "artifacts");
        artifacts.mkdirs();
        File apk = new File(artifacts, "app-v" + version + ".apk");
        Files.write(apk.toPath(), bytes("app " + version));
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        SignedArtifactReceipt.write(
                identity.directory(),
                identity.id(),
                identity.packageName(),
                revision,
                version,
                artifact,
                "a".repeat(64));
        return artifact;
    }

    private static String userRequest(ContextPreviewGate.Preview preview) {
        return JsonParser.parseString(preview.payload())
                .getAsJsonObject()
                .get("user_request")
                .getAsString();
    }

    private static void assertSameFiles(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (String path : expected.keySet())
            assertArrayEquals(expected.get(path), actual.get(path));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
