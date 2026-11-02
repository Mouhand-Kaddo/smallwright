package dev.smallwright.project;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ChatStoreTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void separateChatsAndMessagesSurviveReopen() throws Exception {
        File root = temporaryFolder.newFolder("chats");
        ChatStore first = new ChatStore(root);
        ChatStore.Chat planning = first.createChat("counter-alpha", "Plan");
        ChatStore.Chat repair = first.createChat("counter-alpha", "Repair");
        first.appendMessage("counter-alpha", planning.id, "user", "Make a counter", 0);
        first.appendMessage("counter-alpha", planning.id, "assistant", "Built it", 1);
        first.appendMessage("counter-alpha", repair.id, "user", "Fix the label", 1);

        ChatStore reopened = new ChatStore(root);
        assertEquals(2, reopened.listChats("counter-alpha").size());
        assertEquals(2, reopened.listMessages("counter-alpha", planning.id).size());
        assertEquals(
                "Make a counter", reopened.listMessages("counter-alpha", planning.id).get(0).text);
        assertEquals(1, reopened.listMessages("counter-alpha", planning.id).get(1).sourceRevision);
        assertEquals(
                "Fix the label", reopened.listMessages("counter-alpha", repair.id).get(0).text);
    }

    @Test
    public void builderStepsAreKeptAsToolMessagesAndOtherRolesAreRefused() throws Exception {
        File root = temporaryFolder.newFolder("tool-chats");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("counter-alpha", "Steps");
        store.appendMessage("counter-alpha", chat.id, "tool", "called\tCalled replace_source\t", 1);
        ChatStore.Message step = new ChatStore(root).listMessages("counter-alpha", chat.id).get(0);
        assertEquals("tool", step.role);
        assertEquals("called\tCalled replace_source\t", step.text);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessage("counter-alpha", chat.id, "system", "x", 1));
    }

    @Test
    public void deletingAProjectRemovesOnlyItsChats() throws Exception {
        ChatStore store = new ChatStore(temporaryFolder.newFolder("chats"));
        ChatStore.Chat doomed = store.createChat("first", "First");
        store.appendMessage("first", doomed.id, "user", "Private project text", 0);
        ChatStore.Chat kept = store.createChat("second", "Second");

        store.deleteProject("first");
        store.deleteProject("never-created");

        assertTrue(store.listChats("first").isEmpty());
        assertEquals(1, store.listChats("second").size());
        assertEquals(kept.id, store.listChats("second").get(0).id);
    }

    @Test
    public void projectIdsConfineChatHistory() throws Exception {
        ChatStore store = new ChatStore(temporaryFolder.newFolder("chats"));
        ChatStore.Chat chat = store.createChat("first", "First");
        store.appendMessage("first", chat.id, "user", "Private project text", 0);

        assertTrue(store.listChats("second").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.listMessages("second", chat.id));
        assertThrows(IllegalArgumentException.class, () -> store.createChat("../escape", "Bad"));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessage("first", "../escape", "user", "Bad", 0));
    }

    @Test
    public void keepsALongBuilderReplyWhole() throws Exception {
        ChatStore store = new ChatStore(temporaryFolder.newFolder("long"));
        ChatStore.Chat chat = store.createChat("first", "First");
        String reply = "Plan. " + "Every step in words. ".repeat(3000);
        store.appendMessage("first", chat.id, "assistant", reply, 0);
        assertEquals(reply, store.listMessages("first", chat.id).get(0).text);
    }

    @Test
    public void keepsABuilderReplyAsLongAsAModelReplyCanHold() throws Exception {
        ChatStore store = new ChatStore(temporaryFolder.newFolder("longest"));
        ChatStore.Chat chat = store.createChat("first", "First");
        String reply = "w".repeat(dev.smallwright.agent.ModelClient.MAX_RESPONSE_BYTES);
        store.appendMessage("first", chat.id, "assistant", reply, 0);
        assertEquals(reply, store.listMessages("first", chat.id).get(0).text);
    }

    @Test
    public void rejectsOversizedInputAndSymlinkRedirect() throws Exception {
        File root = temporaryFolder.newFolder("chats");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("first", "First");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.appendMessage(
                                "first",
                                chat.id,
                                "user",
                                "x".repeat(ChatStore.MAX_TEXT_BYTES + 1),
                                0));
        assertThrows(
                IllegalArgumentException.class, () -> store.createChat("first", "x".repeat(257)));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessage("first", chat.id, "user", "text", -1));
        assertTrue(store.listMessages("first", chat.id).isEmpty());

        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(root.toPath().resolve("linked"), outside);
        assertThrows(SecurityException.class, () -> store.listChats("linked"));
    }

    @Test
    public void unfinishedAtomicWriteDoesNotHideCommittedMessages() throws Exception {
        File root = temporaryFolder.newFolder("chats");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("first", "First");
        store.appendMessage("first", chat.id, "user", "Committed", 2);
        Path messages = root.toPath().resolve("first").resolve(chat.id).resolve("messages");
        Files.write(
                messages.resolve("write-123456.tmp"), "partial".getBytes(StandardCharsets.UTF_8));
        Files.write(
                messages.resolve("write--123456.tmp"), "partial".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, new ChatStore(root).listMessages("first", chat.id).size());
        assertEquals(
                "Acknowledged",
                store.appendMessage("first", chat.id, "assistant", "Acknowledged", 2).text);
        assertEquals(2, store.listMessages("first", chat.id).size());
        Files.write(messages.resolve("unexpected.tmp"), "partial".getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> store.listMessages("first", chat.id));
    }

    @Test
    public void symlinkedAncestorCannotRedirectStorage() throws Exception {
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createDirectory(outside.resolve("chats"));
        Path link = temporaryFolder.getRoot().toPath().resolve("link");
        Files.createSymbolicLink(link, outside);
        ChatStore store = new ChatStore(link.resolve("chats").toFile());

        assertThrows(SecurityException.class, () -> store.createChat("first", "First"));
        assertTrue(Files.notExists(outside.resolve("chats/first")));
    }

    @Test
    public void privateBaseAllowsSystemAncestorsButRejectsRedirectsInsideBase() throws Exception {
        File base = temporaryFolder.newFolder("private-files");
        ChatStore store = new ChatStore(new File(base, "chats"), base);
        ChatStore.Chat chat = store.createChat("first", "First");
        assertEquals(1, store.listChats("first").size());
        assertEquals(chat.id, store.listChats("first").get(0).id);

        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(new File(base, "chats/second").toPath(), outside);
        assertThrows(SecurityException.class, () -> store.listChats("second"));
        assertThrows(
                SecurityException.class,
                () -> new ChatStore(outside.resolve("chats").toFile(), base));
    }

    private static byte[] jpeg(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int index = 0; index < size; index++) bytes[index] = (byte) (index * 7 + seed);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    @Test
    public void picturesStayWithTheirMessageAndSurviveReopen() throws Exception {
        File root = temporaryFolder.newFolder("picture-chats");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("counter-alpha", "Pictures");
        byte[] first = jpeg(100, 1);
        byte[] second = jpeg(200, 2);
        ChatStore.Message sent =
                store.appendMessage(
                        "counter-alpha", chat.id, "user", "Like this", 0, List.of(first, second));
        ChatStore.Message reply =
                store.appendMessage("counter-alpha", chat.id, "assistant", "Ok", 1);

        ChatStore reopened = new ChatStore(root);
        List<byte[]> kept = reopened.images("counter-alpha", chat.id, sent.id);
        assertEquals(2, kept.size());
        assertArrayEquals(first, kept.get(0));
        assertArrayEquals(second, kept.get(1));
        assertTrue(reopened.images("counter-alpha", chat.id, reply.id).isEmpty());
        assertEquals(2, reopened.imageCount("counter-alpha", chat.id));
        assertEquals("Like this", reopened.listMessages("counter-alpha", chat.id).get(0).text);
    }

    @Test
    public void refusesTooManyOversizedOrNonJpegPicturesAndSavesNothing() throws Exception {
        File root = temporaryFolder.newFolder("bad-pictures");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("counter-alpha", "Pictures");
        byte[] small = jpeg(10, 0);
        for (List<byte[]> bad :
                List.of(
                        List.of(small, small, small, small, small),
                        List.of(jpeg(ChatStore.MAX_IMAGE_BYTES + 1, 0)),
                        List.of("not a jpeg".getBytes(StandardCharsets.UTF_8)),
                        List.of(new byte[0])))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> store.appendMessage("counter-alpha", chat.id, "user", "x", 0, bad));
        assertTrue(store.listMessages("counter-alpha", chat.id).isEmpty());
        assertEquals(0, store.imageCount("counter-alpha", chat.id));
    }

    @Test
    public void aChatKeepsAtMostItsPictureLimit() throws Exception {
        File root = temporaryFolder.newFolder("full-pictures");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("counter-alpha", "Pictures");
        byte[] small = jpeg(10, 0);
        List<byte[]> four = List.of(small, small, small, small);
        for (int sent = 0; sent < ChatStore.MAX_CHAT_IMAGES / 4; sent++)
            store.appendMessage("counter-alpha", chat.id, "user", "more", 0, four);
        assertEquals(ChatStore.MAX_CHAT_IMAGES, store.imageCount("counter-alpha", chat.id));
        assertThrows(
                java.io.IOException.class,
                () ->
                        store.appendMessage(
                                "counter-alpha", chat.id, "user", "one more", 0, List.of(small)));
        assertEquals(
                ChatStore.MAX_CHAT_IMAGES / 4, store.listMessages("counter-alpha", chat.id).size());
    }

    @Test
    public void picturesLeftByAnUnfinishedSaveDoNotJoinTheNextMessage() throws Exception {
        File root = temporaryFolder.newFolder("leftover-pictures");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("counter-alpha", "Pictures");
        Path images = root.toPath().resolve("counter-alpha").resolve(chat.id).resolve("images");
        Files.createDirectories(images);
        Files.write(images.resolve("00000001-2.jpg"), jpeg(10, 9));
        assertEquals(0, store.imageCount("counter-alpha", chat.id));
        ChatStore.Message sent =
                store.appendMessage("counter-alpha", chat.id, "user", "x", 0, List.of(jpeg(10, 1)));
        assertEquals(1, store.images("counter-alpha", chat.id, sent.id).size());
        assertEquals(1, store.imageCount("counter-alpha", chat.id));
    }

    @Test
    public void deletingAProjectRemovesItsPictures() throws Exception {
        File root = temporaryFolder.newFolder("deleted-pictures");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("doomed", "Pictures");
        store.appendMessage("doomed", chat.id, "user", "x", 0, List.of(jpeg(10, 1)));
        store.deleteProject("doomed");
        assertFalse(Files.exists(root.toPath().resolve("doomed")));
    }

    private static final String ROUND =
            "{\"questions\":[{\"question\":\"Remind you?\",\"recommended\":\"Yes\","
                    + "\"other_choices\":[\"No\"]}]}";

    private static byte[] legacyRecord(String role, String text, long revision) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            out.writeInt(0x504c4d47);
            byte[] roleBytes = role.getBytes(StandardCharsets.UTF_8);
            out.writeInt(roleBytes.length);
            out.write(roleBytes);
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            out.writeInt(textBytes.length);
            out.write(textBytes);
            out.writeLong(revision);
        }
        return bytes.toByteArray();
    }

    @Test
    public void aQuestionRoundIsSavedAndReadBackAsAskedAfterAReopen() throws Exception {
        File root = temporaryFolder.newFolder("rounds");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("first", "First");
        String saved = dev.smallwright.agent.QuestionRound.fromCall("First:", ROUND).saved();
        store.appendMessage("first", chat.id, "question", saved, 0);

        ChatStore.Message read = new ChatStore(root).listMessages("first", chat.id).get(0);
        assertEquals("question", read.role);
        assertEquals(saved, read.text);
        assertEquals(
                "Remind you?",
                dev.smallwright.agent.QuestionRound.fromSaved(read.text).questions.get(0).text);
    }

    @Test
    public void aQuestionMessageThatIsNotAValidRoundIsRefusedOnWriteAndOnRead() throws Exception {
        File root = temporaryFolder.newFolder("bad-rounds");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("first", "First");
        assertThrows(
                IllegalArgumentException.class,
                () -> store.appendMessage("first", chat.id, "question", "Remind you?", 0));
        assertTrue(store.listMessages("first", chat.id).isEmpty());

        Path messages = root.toPath().resolve("first").resolve(chat.id).resolve("messages");
        Files.createDirectories(messages);
        Files.write(
                messages.resolve("00000001.bin"),
                legacyRecord("question", ROUND.replace("\"No\"", "\"yes\""), 0));
        assertThrows(SecurityException.class, () -> store.listMessages("first", chat.id));
    }

    @Test
    public void chatsSavedBeforeQuestionRoundsStillRead() throws Exception {
        File root = temporaryFolder.newFolder("legacy");
        ChatStore store = new ChatStore(root);
        ChatStore.Chat chat = store.createChat("first", "First");
        Path messages = root.toPath().resolve("first").resolve(chat.id).resolve("messages");
        Files.createDirectories(messages);
        Files.write(messages.resolve("00000001.bin"), legacyRecord("user", "Make a counter", 0));
        Files.write(messages.resolve("00000002.bin"), legacyRecord("tool", "done\tBuilt\t", 1));
        Files.write(messages.resolve("00000003.bin"), legacyRecord("assistant", "Built it.", 1));
        Files.write(messages.resolve("00000004.bin"), legacyRecord("status", "Ready.", 1));

        List<ChatStore.Message> read = new ChatStore(root).listMessages("first", chat.id);
        assertEquals(4, read.size());
        assertEquals("Built it.", read.get(2).text);
        assertEquals("status", read.get(3).role);
    }
}
