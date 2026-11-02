package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ContextPreviewGateTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void previewBindsSourceAndChatToTheSameNewProject() throws Exception {
        File files = temporaryFolder.newFolder("new-project-preview");
        ProjectIdentity identity = new ProjectRegistry(new File(files, "projects")).create("Notes");
        ProjectSourceStore source = new ProjectSourceStore(identity);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat chat = chats.createChat(identity.id(), "Ideas");
        chats.appendMessage(identity.id(), chat.id, "user", "Keep notes local", 0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, identity.id());
        ContextPreviewGate.Preview preview = gate.previewChat(chat.id, "Add a title", 0);
        assertTrue(preview.payload().contains("java/" + identity.packageName().replace('.', '/')));
        assertTrue(preview.payload().contains("Keep notes local"));
        assertTrue(preview.payload().equals(gate.approve(preview, "Add a title", 0)));
        assertEquals(
                "Notes",
                com.google.gson.JsonParser.parseString(preview.payload())
                        .getAsJsonObject()
                        .get("project_name")
                        .getAsString());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContextPreviewGate(source, chats, "counter-alpha"));
    }

    @Test
    public void previewUsesOnlySelectedChatAndInvalidatesWhenHistoryChanges() throws Exception {
        File files = temporaryFolder.newFolder("chat-context");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat selected = chats.createChat("counter-alpha", "First");
        ChatStore.Chat other = chats.createChat("counter-alpha", "Second");
        chats.appendMessage(
                "counter-alpha",
                selected.id,
                "user",
                "Keep the blue label; api_key=example_secret_value",
                0);
        chats.appendMessage("counter-alpha", other.id, "user", "OTHER_CHAT_MARKER", 0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, "counter-alpha");

        ContextPreviewGate.Preview preview = gate.previewChat(selected.id, "Add a save button", 0);
        assertTrue(preview.payload().contains("Keep the blue label"));
        assertFalse(preview.payload().contains("example_secret_value"));
        assertTrue(preview.payload().contains("[REDACTED]"));
        assertTrue(preview.payload().contains("Add a save button"));
        assertFalse(preview.payload().contains("OTHER_CHAT_MARKER"));
        assertTrue(preview.payload().equals(gate.approve(preview, "Add a save button", 0)));

        chats.appendMessage("counter-alpha", selected.id, "user", "Newer chat message", 0);
        assertThrows(
                IllegalStateException.class, () -> gate.approve(preview, "Add a save button", 0));
    }

    @Test
    public void chatHistoryCarriesTheBuilderAnswersInOrderAndDropsTheOldestFirst()
            throws Exception {
        File files = temporaryFolder.newFolder("chat-answers");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat chat = chats.createChat("counter-alpha", "Ideas");
        chats.appendMessage("counter-alpha", chat.id, "user", "OLDEST " + "a".repeat(1500), 0);
        for (int turn = 0; turn < 4; turn++) {
            chats.appendMessage(
                    "counter-alpha", chat.id, "user", "idea " + turn + "b".repeat(900), 0);
            chats.appendMessage(
                    "counter-alpha", chat.id, "assistant", "answer " + turn + "c".repeat(900), 0);
        }
        chats.appendMessage("counter-alpha", chat.id, "status", "STATUS_MARKER", 0);
        chats.appendMessage("counter-alpha", chat.id, "assistant", "PLAN " + "d".repeat(3500), 0);
        chats.appendMessage("counter-alpha", chat.id, "user", "Should it have a reset?", 0);
        chats.appendMessage("counter-alpha", chat.id, "assistant", "Yes, one reset button.", 0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, "counter-alpha");

        String payload = gate.previewChat(chat.id, "Let's build it", 0).payload();
        assertTrue(payload.contains("Person: Should it have a reset?"));
        assertTrue(payload.contains("Builder: Yes, one reset button."));
        assertTrue(
                payload.indexOf("Person: Should it have a reset?")
                        < payload.indexOf("Builder: Yes, one reset button."));
        assertTrue(
                payload.indexOf("Builder: Yes, one reset button.")
                        < payload.indexOf("Current message:\\nLet's build it"));
        assertTrue(payload.contains("PLAN dddd"));
        assertFalse(payload.contains("OLDEST"));
        assertFalse(payload.contains("STATUS_MARKER"));
    }

    @Test
    public void chatHistoryShowsTheModelTheQuestionsItAskedBeforeTheAnswers() throws Exception {
        File files = temporaryFolder.newFolder("asked");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat chat = chats.createChat("counter-alpha", "Ideas");
        chats.appendMessage("counter-alpha", chat.id, "user", "A habit tracker", 0);
        String asked =
                dev.smallwright.agent.QuestionRound.fromCall(
                                "Two things first.",
                                "{\"questions\":[{\"question\":\"Remind you?\","
                                        + "\"recommended\":\"Yes\",\"other_choices\":[\"No\"],"
                                        + "\"reason\":\"Habits stick.\"}]}")
                        .saved();
        chats.appendMessage("counter-alpha", chat.id, "question", asked, 0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, "counter-alpha");

        String payload = gate.previewChat(chat.id, "1. Remind you?: No", 0).payload();
        assertTrue(
                payload,
                payload.contains(
                        "Builder: Two things first.\\nQuestions asked with ask:\\n"
                                + "1. Remind you?\\n- Yes (recommended: Habits stick.)\\n- No"));
        assertFalse(payload, payload.contains("other_choices"));
        assertTrue(
                payload.indexOf("Person: A habit tracker")
                        < payload.indexOf("Builder: Two things first."));
    }

    @Test
    public void aLongEarlierReplyIsShortenedToFitInsteadOfSkipped() throws Exception {
        File files = temporaryFolder.newFolder("long-answer");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat chat = chats.createChat("counter-alpha", "Ideas");
        chats.appendMessage("counter-alpha", chat.id, "user", "Plan a tip calculator", 0);
        chats.appendMessage(
                "counter-alpha",
                chat.id,
                "assistant",
                "LONG_PLAN_START " + "e".repeat(20_000) + " LONG_PLAN_END",
                0);
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, "counter-alpha");

        String payload = gate.previewChat(chat.id, "Build it", 0).payload();
        assertTrue(payload.contains("Person: Plan a tip calculator"));
        assertTrue(payload.contains("Builder: LONG_PLAN_START eeee"));
        assertTrue(payload.contains("e…"));
        assertFalse(payload.contains("LONG_PLAN_END"));
        assertTrue(
                payload.indexOf("Person: Plan a tip calculator")
                        < payload.indexOf("Builder: LONG_PLAN_START"));
    }

    @Test
    public void previewIsTheExactApprovedPayloadFromCurrentProjectRevision() throws Exception {
        File files = temporaryFolder.newFolder("files");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        File unrelated = new File(files, "projects/other/private.txt");
        Files.createDirectories(unrelated.toPath().getParent());
        Files.write(unrelated.toPath(), "UNRELATED_MARKER".getBytes(StandardCharsets.UTF_8));
        source.writeSource(0, "// current source\n");
        ContextPreviewGate gate = new ContextPreviewGate(source);

        ContextPreviewGate.Preview preview = gate.preview("Make the button blue", 1);
        String approved = gate.approve(preview, "Make the button blue", 1);

        assertTrue(approved.equals(preview.payload()));
        assertTrue(approved.contains("Make the button blue"));
        assertTrue(approved.contains("MainActivity.java"));
        assertTrue(approved.contains("res/layout/empty.xml"));
        assertFalse(approved.contains("UNRELATED_MARKER"));
    }

    @Test
    public void requestAndProjectEditsInvalidateDisplayedPreview() throws Exception {
        ProjectSourceStore source = new ProjectSourceStore(temporaryFolder.newFolder("files"));
        source.initialize();
        ContextPreviewGate gate = new ContextPreviewGate(source);
        ContextPreviewGate.Preview preview = gate.preview("First request", 0);

        assertThrows(
                IllegalStateException.class, () -> gate.approve(preview, "Changed request", 0));
        source.writeSource(0, "// changed source\n");
        assertThrows(IllegalStateException.class, () -> gate.approve(preview, "First request", 0));
        assertThrows(IllegalStateException.class, () -> gate.preview("First request", 0));
    }

    @Test
    public void changedPayloadWithSameRevisionInvalidatesConsent() throws Exception {
        File files = temporaryFolder.newFolder("files");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ContextPreviewGate gate = new ContextPreviewGate(source);
        ContextPreviewGate.Preview preview = gate.preview("Request", 0);
        File storedSource =
                new File(
                        files,
                        "projects/counter-alpha/revision-store/revisions/0/"
                                + "java/dev/smallwright/generated/counter/MainActivity.java");
        Files.write(
                storedSource.toPath(),
                "// changed behind the preview\n".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> gate.approve(preview, "Request", 0));
    }

    @Test
    public void previewRejectsUnapprovedProjectPathAndSymlinkedRevisionFile() throws Exception {
        File files = temporaryFolder.newFolder("files");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        File revision = new File(files, "projects/counter-alpha/revision-store/revisions/0");
        File outside = temporaryFolder.newFile("outside");
        Files.write(outside.toPath(), "OUTSIDE_MARKER".getBytes(StandardCharsets.UTF_8));
        File link = new File(revision, "java/dev/smallwright/generated/counter/Other.java");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        assertThrows(
                SecurityException.class,
                () -> new ContextPreviewGate(source).preview("Request", 0));
    }

    @Test
    public void picturesAreBoundToThePreviewAndAChangedPictureIsRefused() throws Exception {
        File files = temporaryFolder.newFolder("picture-context");
        ProjectSourceStore source = new ProjectSourceStore(files);
        source.initialize();
        ChatStore chats = new ChatStore(new File(files, "chats"));
        ChatStore.Chat chat = chats.createChat("counter-alpha", "Ideas");
        ContextPreviewGate gate = new ContextPreviewGate(source, chats, "counter-alpha");
        byte[] picked = AttachedImagesTest.jpeg(64, 1);
        AttachedImages images = new AttachedImages(java.util.List.of(picked));

        ContextPreviewGate.Preview preview =
                gate.previewChat(chat.id, "Make it look like this", 0, images);
        assertTrue(
                preview.payload()
                        .equals(gate.approve(preview, "Make it look like this", 0, images)));
        assertTrue(preview.images().digest().equals(images.digest()));

        byte[] changed = picked.clone();
        changed[40]++;
        assertThrows(
                IllegalStateException.class,
                () ->
                        gate.approve(
                                preview,
                                "Make it look like this",
                                0,
                                new AttachedImages(java.util.List.of(changed))));
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(preview, "Make it look like this", 0, AttachedImages.NONE));
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(preview, "Make it look like this", 0));

        ContextPreviewGate.Preview withoutPictures =
                gate.previewChat(chat.id, "Make it look like this", 0);
        assertThrows(
                IllegalStateException.class,
                () -> gate.approve(withoutPictures, "Make it look like this", 0, images));
        assertTrue(withoutPictures.images().isEmpty());
    }
}
