package dev.smallwright.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.SseBody;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * OpenRouter replies scripted in order over a fake connection; records every request body. A
 * request that offers no tools asks for a summary and takes the next scripted summary instead.
 */
final class ScriptedModel {
    private static final String REFUSED = "refused ";
    private static final byte[] KEY = "dummy-key".getBytes(StandardCharsets.US_ASCII);
    private final Deque<String> replies = new ArrayDeque<>();
    private final Deque<String> summaries = new ArrayDeque<>();
    final List<JsonObject> sent = new ArrayList<>();
    private int contextTokens = ModelChoice.DEFAULT.contextTokens;
    private int promptTokens = 100;
    private int completionTokens = 20;
    private String reasoning = "";
    private int webSearches;
    private final ModelClient client =
            new ModelClient(url -> new Connection(url), ModelChoice.DEFAULT);

    ScriptedModel calls(String... toolCalls) {
        return saysAndCalls(null, toolCalls);
    }

    /** A reply with these words beside its tool calls; null words send no content. */
    ScriptedModel saysAndCalls(String text, String... toolCalls) {
        return reply("tool_calls", callMessage(text, toolCalls));
    }

    private JsonObject callMessage(String text, String... toolCalls) {
        JsonArray calls = new JsonArray();
        for (int index = 0; index < toolCalls.length; index += 2) {
            JsonObject function = new JsonObject();
            function.addProperty("name", toolCalls[index]);
            function.addProperty("arguments", toolCalls[index + 1]);
            JsonObject call = new JsonObject();
            call.addProperty("id", "call_" + replies.size() + "_" + index / 2);
            call.addProperty("type", "function");
            call.add("function", function);
            calls.add(call);
        }
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        if (text == null) message.add("content", com.google.gson.JsonNull.INSTANCE);
        else message.addProperty("content", text);
        message.add("tool_calls", calls);
        return message;
    }

    /**
     * The next reply stops at the model's output limit partway through a write_file call, with its
     * arguments cut short.
     */
    ScriptedModel cutOff() {
        return reply(
                "length",
                callMessage(
                        null,
                        "write_file",
                        "{\"path\":\"res/values/strings.xml\",\"content\":\"<resou"));
    }

    ScriptedModel answer(String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", text);
        return reply("stop", message);
    }

    ScriptedModel raw(String body) {
        replies.add(body);
        return this;
    }

    /** The next request is refused with this HTTP status. */
    ScriptedModel refused(int status) {
        return refused(status, -1);
    }

    /** As above, asking to wait this many seconds before trying again; -1 asks for no wait. */
    ScriptedModel refused(int status, int retryAfterSeconds) {
        replies.add(REFUSED + status + " " + retryAfterSeconds);
        return this;
    }

    /** The next request's stream ends before the model finishes. */
    ScriptedModel dropped() {
        return raw(SseBody.HEARTBEAT);
    }

    /** The reply scripted last stops partway through its usage line, before {@code [DONE]}. */
    ScriptedModel cutShort() {
        String whole = replies.removeLast();
        return raw(whole.substring(0, whole.lastIndexOf("data: [DONE]") - 3));
    }

    /** The context length the catalog lists for this model. */
    ScriptedModel context(int tokens) {
        contextTokens = tokens;
        return this;
    }

    /** The reasoning text the replies scripted after this return; empty returns none. */
    ScriptedModel reasoning(String text) {
        reasoning = text;
        return this;
    }

    /** The web searches the replies scripted after this report, each costing $0.007. */
    ScriptedModel webSearches(int count) {
        webSearches = count;
        return this;
    }

    /** The prompt tokens the replies scripted after this report. */
    ScriptedModel promptTokens(int tokens) {
        promptTokens = tokens;
        return this;
    }

    /** The completion tokens the replies scripted after this report. */
    ScriptedModel completionTokens(int tokens) {
        completionTokens = tokens;
        return this;
    }

    ScriptedModel summaries(String... texts) {
        for (String text : texts) {
            answer(text);
            summaries.add(replies.removeLast());
        }
        return this;
    }

    /** The summary scripted last reports its usage, then its stream ends before {@code [DONE]}. */
    ScriptedModel summaryEndsBeforeDone() {
        String whole = summaries.removeLast();
        summaries.add(whole.substring(0, whole.lastIndexOf("data: [DONE]")));
        return this;
    }

    private ScriptedModel reply(String finish, JsonObject message) {
        if (!reasoning.isEmpty()) message.addProperty("reasoning", reasoning);
        JsonObject choice = new JsonObject();
        choice.addProperty("finish_reason", finish);
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject usage = new JsonObject();
        usage.addProperty("prompt_tokens", promptTokens);
        usage.addProperty("completion_tokens", completionTokens);
        usage.addProperty("total_tokens", promptTokens + completionTokens);
        usage.addProperty(
                "cost",
                new java.math.BigDecimal("0.0001")
                        .add(
                                new java.math.BigDecimal("0.007")
                                        .multiply(java.math.BigDecimal.valueOf(webSearches))));
        if (webSearches > 0) {
            JsonObject searches = new JsonObject();
            searches.addProperty("web_search_requests", webSearches);
            usage.add("server_tool_use_details", searches);
        }
        JsonObject root = new JsonObject();
        root.addProperty("model", ModelClient.MODEL);
        root.add("choices", choices);
        root.add("usage", usage);
        replies.add(SseBody.of(root));
        return this;
    }

    StepRun.Model model() {
        return model(() -> {});
    }

    StepRun.Model model(Runnable beforeEachReply) {
        return model(beforeEachReply, thinking -> {});
    }

    /** As above; {@code thinking} gets the reasoning so far as each reply streams in. */
    StepRun.Model model(Consumer<CharSequence> thinking) {
        return model(() -> {}, thinking);
    }

    private StepRun.Model model(Runnable beforeEachReply, Consumer<CharSequence> thinking) {
        return new StepRun.Model() {
            @Override
            public ModelClient.StepReply next(JsonArray messages, int promptTokens)
                    throws Exception {
                ModelClient.StepReply reply =
                        client.completeStep(
                                KEY,
                                messages,
                                promptTokens,
                                new ModelClient.Cancellation(),
                                thinking);
                beforeEachReply.run();
                return reply;
            }

            @Override
            public ModelClient.StepReply summarize(JsonArray messages, int promptTokens)
                    throws Exception {
                return client.completeSummary(
                        KEY, messages, promptTokens, new ModelClient.Cancellation(), thinking);
            }

            @Override
            public int contextTokens() {
                return contextTokens;
            }
        };
    }

    int remaining() {
        return replies.size();
    }

    /** The requests that asked for a summary, in order. */
    List<JsonObject> summaryRequests() {
        List<JsonObject> asked = new ArrayList<>();
        for (JsonObject body : sent) if (!body.has("tools")) asked.add(body);
        return asked;
    }

    /** The requests that offered the run's tools, in order. */
    List<JsonObject> stepRequests() {
        List<JsonObject> asked = new ArrayList<>();
        for (JsonObject body : sent) if (body.has("tools")) asked.add(body);
        return asked;
    }

    /** The messages of request number {@code index}, counting from zero. */
    JsonArray messages(int index) {
        return sent.get(index).getAsJsonArray("messages");
    }

    /** The content of the last tool message in request {@code index}. */
    List<String> toolResults(int index) {
        List<String> results = new ArrayList<>();
        for (com.google.gson.JsonElement element : messages(index)) {
            JsonObject message = element.getAsJsonObject();
            if ("tool".equals(message.get("role").getAsString()))
                results.add(message.get("content").getAsString());
        }
        return results;
    }

    String lastToolResult(int index) {
        JsonArray messages = messages(index);
        for (int position = messages.size() - 1; position >= 0; position--) {
            JsonObject message = messages.get(position).getAsJsonObject();
            if ("tool".equals(message.get("role").getAsString()))
                return message.get("content").getAsString();
        }
        throw new AssertionError("No tool result in request " + index);
    }

    private final class Connection extends HttpURLConnection {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        Connection(URL url) {
            super(url);
        }

        @Override
        public void connect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void disconnect() {}

        private Deque<String> queue;

        @Override
        public int getResponseCode() {
            JsonObject request =
                    JsonParser.parseString(body.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            sent.add(request);
            queue = request.has("tools") ? replies : summaries;
            if (queue.isEmpty()) return 500;
            if (!queue.peekFirst().startsWith(REFUSED)) return 200;
            String[] refusal = queue.removeFirst().substring(REFUSED.length()).split(" ");
            if (!refusal[1].equals("-1")) retryAfter = refusal[1];
            return Integer.parseInt(refusal[0]);
        }

        private String retryAfter;

        @Override
        public String getHeaderField(String name) {
            return "Retry-After".equals(name) ? retryAfter : null;
        }

        @Override
        public OutputStream getOutputStream() {
            return body;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(queue.removeFirst().getBytes(StandardCharsets.UTF_8));
        }
    }
}
