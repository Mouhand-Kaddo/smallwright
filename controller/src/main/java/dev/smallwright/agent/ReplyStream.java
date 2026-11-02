package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * One streamed reply from OpenRouter or a model server, read as server-sent {@code
 * chat.completion.chunk} events and put back together as the reply a request without streaming
 * gets, so one parser checks both. Its words, tool calls and their names and ids are bounded by
 * {@link ModelClient#MAX_RESPONSE_BYTES}, each tool call counting {@link #CALL_BYTES} more, and
 * each event by {@link #MAX_EVENT_BYTES}, so a whole reply still fits in one escaped event.
 * Reasoning of any length is kept only as its start and its newest part.
 */
final class ReplyStream {
    static final int MAX_EVENT_BYTES = 2 * ModelClient.MAX_RESPONSE_BYTES;

    /**
     * About what one more tool call takes in memory, so a stream cannot open countless empty ones.
     */
    private static final int CALL_BYTES = 64;

    private final ModelProvider provider;
    private final StringBuilder content = new StringBuilder();
    private final Thought reasoning = new Thought();
    private final Thought reasoningContent = new Thought();
    private final Thought details = new Thought();
    private final Map<Integer, ToolCall> calls = new TreeMap<>();
    private Object detailPart;
    private String model;
    private String role;
    private String finish;
    private JsonObject usage;
    private long held;

    /**
     * Reasoning kept as its first {@code 2 * MAX_REASONING_BYTES} characters, one more when the
     * last would split a character, more than the reply keeps after shortening, and its newest
     * {@code MAX_REASONING_BYTES} characters or more.
     */
    private static final class Thought {
        private static final int KEPT = ModelClient.MAX_REASONING_BYTES;
        final StringBuilder start = new StringBuilder();
        final StringBuilder newest = new StringBuilder();

        void add(String piece) {
            int end = Math.max(0, Math.min(piece.length(), 2 * KEPT - start.length()));
            if (end > 0 && end < piece.length() && Character.isHighSurrogate(piece.charAt(end - 1)))
                end++;
            start.append(piece, 0, end);
            newest.append(piece);
            if (newest.length() > 2 * KEPT) newest.delete(0, newest.length() - KEPT);
        }

        boolean isEmpty() {
            return start.length() == 0;
        }
    }

    private static final class ToolCall {
        String id;
        String type;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    ReplyStream(ModelProvider provider) {
        this.provider = provider;
    }

    /**
     * Reads the whole stream; {@code thinking} gets the newest part of the reasoning so far after
     * each event. Each event is its {@code data:} lines joined, up to a blank line. A stream that
     * ends before {@code [DONE]} is a dropped connection, and a line it cut short is never read.
     */
    JsonObject read(
            InputStream input,
            ModelClient.Cancellation cancellation,
            Consumer<CharSequence> thinking)
            throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        StringBuilder event = new StringBuilder();
        long eventBytes = 0;
        boolean done = false;
        while (!done) {
            if (!nextLine(input, line)) throw dropped();
            cancellation.check();
            String text = new String(line.toByteArray(), StandardCharsets.UTF_8);
            if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
            String data =
                    text.startsWith("data:")
                            ? text.substring(text.startsWith("data: ") ? 6 : 5)
                            : null;
            done = "[DONE]".equals(data);
            if (data != null && !done) {
                eventBytes += line.size();
                if (eventBytes > MAX_EVENT_BYTES) throw ModelClient.tooLarge();
                if (event.length() > 0) event.append('\n');
                event.append(data);
            } else if ((text.isEmpty() || done) && eventBytes > 0) {
                add(event.toString());
                thinking.accept(thought());
                event.setLength(0);
                eventBytes = 0;
            }
        }
        cancellation.check();
        if (finish == null) throw dropped();
        return envelope();
    }

    /** The usage the stream reported so far, or null. */
    JsonObject usage() {
        return usage;
    }

    private ModelClient.RequestFailed dropped() {
        return new ModelClient.RequestFailed(
                provider.label + " request failed (connection closed)",
                ModelClient.RequestFailed.Retry.CONNECTION);
    }

    private static boolean nextLine(InputStream input, ByteArrayOutputStream line)
            throws IOException {
        line.reset();
        int next;
        while ((next = input.read()) != -1 && next != '\n') {
            if (line.size() >= MAX_EVENT_BYTES) throw ModelClient.tooLarge();
            line.write(next);
        }
        return next != -1;
    }

    private CharSequence thought() {
        return !reasoning.isEmpty()
                ? reasoning.newest
                : !reasoningContent.isEmpty() ? reasoningContent.newest : details.newest;
    }

    private void add(String data) throws IOException {
        JsonObject chunk;
        try {
            chunk = ModelClient.strictJson(data, MAX_EVENT_BYTES).getAsJsonObject();
        } catch (IOException | RuntimeException malformed) {
            throw new ModelClient.InvalidModelResponse("envelope");
        }
        if (chunk.has("error")) throw ModelClient.failedMidStream(provider, chunk.get("error"));
        try {
            if (model == null) model = text(chunk.get("model"));
            JsonElement reported = chunk.get("usage");
            if (reported != null && !reported.isJsonNull()) usage = reported.getAsJsonObject();
            JsonArray choices = chunk.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return;
            if (choices.size() != 1) throw new IllegalArgumentException();
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonElement reason = choice.get("finish_reason");
            if (finish == null && reason != null && !reason.isJsonNull())
                finish = reason.getAsString();
            JsonElement delta = choice.get("delta");
            if (delta == null || delta.isJsonNull()) return;
            JsonObject fragment = delta.getAsJsonObject();
            if (role == null && text(fragment.get("role")) != null)
                role = fragment.get("role").getAsString();
            JsonElement words = fragment.get("content");
            if (words != null && !words.isJsonNull()) {
                if (text(words) == null) throw new IllegalArgumentException();
                hold(content, text(words));
            }
            String thinking = text(fragment.get("reasoning"));
            if (thinking != null) reasoning.add(thinking);
            String written = text(fragment.get("reasoning_content"));
            if (written != null) reasoningContent.add(written);
            addDetails(fragment.get("reasoning_details"));
            addToolCalls(fragment.get("tool_calls"));
        } catch (RuntimeException malformed) {
            throw new ModelClient.InvalidModelResponse("envelope");
        }
    }

    private void addDetails(JsonElement parts) {
        if (parts == null || !parts.isJsonArray()) return;
        for (JsonElement element : parts.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            String type = text(detail.get("type"));
            String piece =
                    "reasoning.text".equals(type)
                            ? text(detail.get("text"))
                            : "reasoning.summary".equals(type) ? text(detail.get("summary")) : null;
            if (piece == null) continue;
            Object part = java.util.List.of(type, String.valueOf(detail.get("index")));
            if (!details.isEmpty() && !Objects.equals(part, detailPart)) details.add("\n\n");
            detailPart = part;
            details.add(piece);
        }
    }

    private void addToolCalls(JsonElement fragments) throws IOException {
        if (fragments == null || fragments.isJsonNull()) return;
        for (JsonElement element : fragments.getAsJsonArray()) {
            JsonObject fragment = element.getAsJsonObject();
            int index = fragment.get("index").getAsInt();
            if (index < 0) throw new IllegalArgumentException();
            ToolCall call = calls.get(index);
            if (call == null) {
                count(CALL_BYTES);
                call = new ToolCall();
                calls.put(index, call);
            }
            if (call.id == null) call.id = held(text(fragment.get("id")));
            if (call.type == null) call.type = held(text(fragment.get("type")));
            JsonElement function = fragment.get("function");
            if (function == null || function.isJsonNull()) continue;
            JsonObject named = function.getAsJsonObject();
            if (call.name == null) call.name = held(text(named.get("name")));
            hold(call.arguments, text(named.get("arguments")));
        }
    }

    private JsonObject envelope() {
        JsonObject message = new JsonObject();
        message.addProperty("role", role == null ? "assistant" : role);
        if (content.length() > 0) message.addProperty("content", content.toString());
        else message.add("content", JsonNull.INSTANCE);
        if (!reasoning.isEmpty()) message.addProperty("reasoning", reasoning.start.toString());
        if (!reasoningContent.isEmpty())
            message.addProperty("reasoning_content", reasoningContent.start.toString());
        if (!details.isEmpty()) {
            JsonObject detail = new JsonObject();
            detail.addProperty("type", "reasoning.text");
            detail.addProperty("text", details.start.toString());
            JsonArray parts = new JsonArray();
            parts.add(detail);
            message.add("reasoning_details", parts);
        }
        if (!calls.isEmpty()) {
            JsonArray toolCalls = new JsonArray();
            for (ToolCall call : calls.values()) {
                JsonObject function = new JsonObject();
                if (call.name != null) function.addProperty("name", call.name);
                function.addProperty("arguments", call.arguments.toString());
                JsonObject made = new JsonObject();
                if (call.id != null) made.addProperty("id", call.id);
                made.addProperty("type", call.type == null ? "function" : call.type);
                made.add("function", function);
                toolCalls.add(made);
            }
            message.add("tool_calls", toolCalls);
        }
        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.addProperty("finish_reason", finish);
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject root = new JsonObject();
        if (model != null) root.addProperty("model", model);
        root.add("choices", choices);
        if (usage != null) root.add("usage", usage);
        return root;
    }

    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private String held(String piece) throws IOException {
        if (piece != null) count(piece);
        return piece;
    }

    private void hold(StringBuilder into, String piece) throws IOException {
        if (piece == null) return;
        count(piece);
        into.append(piece);
    }

    private void count(String piece) throws IOException {
        count(piece.getBytes(StandardCharsets.UTF_8).length);
    }

    private void count(int bytes) throws IOException {
        held += bytes;
        if (held > ModelClient.MAX_RESPONSE_BYTES) throw ModelClient.tooLarge();
    }
}
