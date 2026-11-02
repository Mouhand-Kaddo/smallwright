package dev.smallwright.controller;

import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.QuestionRound;
import dev.smallwright.project.ChatStore;
import dev.smallwright.security.ContextFilter;
import dev.smallwright.skills.SkillStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Binds the first request of a run to the person's message and the project revision at the tap. */
final class ContextPreviewGate {
    private final ProjectSourceStore source;
    private final ChatStore chats;
    private final String projectId;
    private final SkillStore skills;

    ContextPreviewGate(ProjectSourceStore source) {
        this(source, null, null);
    }

    ContextPreviewGate(ProjectSourceStore source, ChatStore chats, String projectId) {
        this(source, chats, projectId, null);
    }

    ContextPreviewGate(
            ProjectSourceStore source, ChatStore chats, String projectId, SkillStore skills) {
        if (chats != null && (projectId == null || !projectId.equals(source.projectId()))) {
            throw new IllegalArgumentException("Chat project does not match source project");
        }
        this.source = source;
        this.chats = chats;
        this.projectId = projectId;
        this.skills = skills;
    }

    Preview preview(String request, int expectedRevision) throws IOException {
        return preview(request, expectedRevision, null, AttachedImages.NONE);
    }

    Preview previewChat(String chatId, String request, int expectedRevision) throws IOException {
        return previewChat(chatId, request, expectedRevision, AttachedImages.NONE);
    }

    Preview previewChat(String chatId, String request, int expectedRevision, AttachedImages images)
            throws IOException {
        return preview(request, expectedRevision, chatId, images);
    }

    private Preview preview(
            String request, int expectedRevision, String chatId, AttachedImages images)
            throws IOException {
        String exactRequest = request == null ? "" : request;
        RunSkills atSend =
                RunSkills.atSend(skills, source.projectId(), exactRequest, personMessages(chatId));
        return new Preview(
                exactRequest,
                expectedRevision,
                payload(exactRequest, expectedRevision, chatId, atSend),
                chatId,
                atSend,
                images);
    }

    String approve(Preview preview, String currentRequest, int expectedRevision)
            throws IOException {
        return approve(preview, currentRequest, expectedRevision, AttachedImages.NONE);
    }

    /**
     * Returns the payload of the preview when the request, revision, chat and pictures are still
     * the ones it was made with; the pictures sent are then {@link Preview#images()}.
     */
    String approve(
            Preview preview, String currentRequest, int expectedRevision, AttachedImages images)
            throws IOException {
        if (preview == null
                || images == null
                || expectedRevision != preview.revision
                || !preview.request.equals(currentRequest)
                || !preview.images.digest().equals(images.digest())) {
            throw new IllegalStateException("Context preview changed; review it again");
        }
        String currentPayload =
                payload(
                        currentRequest,
                        expectedRevision,
                        preview.chatId,
                        RunSkills.atSend(
                                skills,
                                source.projectId(),
                                currentRequest,
                                personMessages(preview.chatId)));
        if (!preview.payload.equals(currentPayload)) {
            throw new IllegalStateException("Context preview changed; review it again");
        }
        return currentPayload;
    }

    private String payload(String request, int revision, String chatId, RunSkills atSend)
            throws IOException {
        Map<String, Integer> sizes = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : source.readRecoverySources(revision).entrySet())
            sizes.put(file.getKey(), file.getValue().length);
        return ContextFilter.firstRequest(
                requestWithHistory(chatId, request),
                sizes,
                source.packageName(),
                source.projectTitle(),
                source.brief().read(),
                atSend.offered(),
                atSend.used == null ? null : atSend.used.name,
                atSend.usedText,
                atSend.wanted);
    }

    private static final int MAX_HISTORY_MESSAGE_BYTES = 4096;
    private static final int MAX_REQUEST_WITH_HISTORY_BYTES = 8 * 1024;

    /**
     * The latest chat turns that fit, oldest first, each shortened to {@link
     * #MAX_HISTORY_MESSAGE_BYTES}; an undo note counts as the person's, and status and step lines
     * stay out.
     */
    private String requestWithHistory(String chatId, String request) throws IOException {
        if (chatId == null) return request;
        if (chats == null || projectId == null) throw new IllegalStateException("No chat store");
        List<ChatStore.Message> messages = chats.listMessages(projectId, chatId);
        String head = "Earlier messages in this chat. Current source is authoritative:\n";
        String tail = "Current message:\n" + request;
        int used = utf8Length(head) + utf8Length(tail);
        List<String> prior = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatStore.Message message = messages.get(index);
            boolean asked = ChatStore.QUESTION.equals(message.role);
            String speaker =
                    "user".equals(message.role) || ChatStore.UNDO.equals(message.role)
                            ? "Person"
                            : "assistant".equals(message.role) || asked ? "Builder" : null;
            if (speaker == null) continue;
            String said =
                    asked ? QuestionRound.fromSaved(message.text).historyText() : message.text;
            String line =
                    speaker + ": " + ModelClient.shortened(said, MAX_HISTORY_MESSAGE_BYTES) + "\n";
            if (used + utf8Length(line) > MAX_REQUEST_WITH_HISTORY_BYTES) break;
            used += utf8Length(line);
            prior.add(line);
        }
        if (prior.isEmpty()) return request;
        Collections.reverse(prior);
        StringBuilder context = new StringBuilder(head);
        for (String line : prior) context.append(line);
        return context.append(tail).toString();
    }

    /** What the person wrote earlier in the chat, oldest first. */
    private List<String> personMessages(String chatId) throws IOException {
        List<String> texts = new ArrayList<>();
        if (chatId == null || chats == null) return texts;
        for (ChatStore.Message message : chats.listMessages(projectId, chatId))
            if ("user".equals(message.role)) texts.add(message.text);
        return texts;
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    static final class Preview {
        private final String request;
        private final int revision;
        private final String payload;
        private final String chatId;
        private final RunSkills skills;
        private final AttachedImages images;

        private Preview(
                String request,
                int revision,
                String payload,
                String chatId,
                RunSkills skills,
                AttachedImages images) {
            if (images == null) throw new IllegalArgumentException("No pictures given");
            this.request = request;
            this.revision = revision;
            this.payload = payload;
            this.chatId = chatId;
            this.skills = skills;
            this.images = images;
        }

        /** The pictures attached when this preview was made. */
        AttachedImages images() {
            return images;
        }

        /** The skills as they were when this preview was made. */
        RunSkills skills() {
            return skills;
        }

        String payload() {
            return payload;
        }

        int revision() {
            return revision;
        }

        String chatId() {
            return chatId;
        }
    }
}
