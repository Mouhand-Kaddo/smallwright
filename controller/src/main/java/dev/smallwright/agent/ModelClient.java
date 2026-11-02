package dev.smallwright.agent;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.smallwright.security.ModelKeyInput;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * The chat completions protocol for the picked model, at OpenRouter or at an OpenAI-compatible
 * server. Returned text never authorizes a local action.
 */
public final class ModelClient {
    public static final String MODEL = "deepseek/deepseek-v4.1-flash";
    public static final String PRICE_LABEL = "paid";
    public static final List<String> PROVIDERS = List.of("deepinfra", "together", "nebius");
    public static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";

    /**
     * What one reply's words and tool calls may hold: four bytes for each token any reply could be
     * counted as, which no model writes. It keeps a broken server from filling the phone's memory.
     */
    public static final int MAX_RESPONSE_BYTES = 4 * UsageBudget.MAX_COMPLETION_TOKENS;

    public static final int MAX_REASONING_BYTES = 32 * 1024;
    public static final int MAX_STEP_REQUEST_BYTES = 4 * 1024 * 1024;

    /**
     * How long a streamed reply from OpenRouter may stay silent before the connection counts as
     * dead. OpenRouter sends heartbeats and the model's tokens while it works, so this never cuts
     * off thinking. A model server promises no heartbeat, so its reply may stay silent for as long
     * as the model thinks, until Stop.
     */
    static final int STALL_MILLIS = 90_000;

    public interface ConnectionFactory {
        java.net.HttpURLConnection open(java.net.URL url) throws IOException;
    }

    private final ConnectionFactory connections;
    private final ModelChoice model;
    private final ReasoningLevel reasoning;

    public ModelClient() {
        this(ModelChoice.DEFAULT, ReasoningLevel.DEFAULT);
    }

    public ModelClient(ModelChoice model, ReasoningLevel reasoning) {
        this(model.provider::open, model, reasoning);
    }

    public ModelClient(ConnectionFactory connections, ModelChoice model) {
        this(connections, model, ReasoningLevel.DEFAULT);
    }

    public ModelClient(ConnectionFactory connections, ModelChoice model, ReasoningLevel reasoning) {
        if (model == null) throw new IllegalArgumentException("No model choice");
        if (reasoning == null) throw new IllegalArgumentException("No reasoning level");
        this.connections = connections;
        this.model = model;
        this.reasoning = reasoning;
    }

    public static final class Cancellation {
        private volatile boolean cancelled;
        private volatile java.net.HttpURLConnection connection;

        public void cancel() {
            cancelled = true;
            java.net.HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        /** Stop disconnects {@code connection} from now on; refused at once if Stop came first. */
        void watch(java.net.HttpURLConnection connection) throws IOException {
            this.connection = connection;
            check();
        }

        void check() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted())
                throw new IOException("Model request cancelled");
        }
    }

    /**
     * One request of a run: the conversation so far, with the run's tools offered. Returns the
     * model's words or its tool calls, unexecuted. {@code promptTokens} is the caller's cautious
     * count of the tokens the request holds, so it asks for no more reply than the context leaves.
     */
    public StepReply completeStep(
            byte[] credential, JsonArray messages, int promptTokens, Cancellation cancellation)
            throws IOException {
        return completeStep(credential, messages, promptTokens, cancellation, thinking -> {});
    }

    /**
     * As above, streamed: {@code thinking} gets the newest part of the model's reasoning so far, at
     * least its last {@link #MAX_REASONING_BYTES} characters, on this thread, each time a piece of
     * the reply arrives. A failed try that reported its usage carries it.
     */
    public StepReply completeStep(
            byte[] credential,
            JsonArray messages,
            int promptTokens,
            Cancellation cancellation,
            Consumer<CharSequence> thinking)
            throws IOException {
        JsonObject body = body(messages, promptTokens);
        JsonArray tools = RunTools.schemas();
        if (model.provider.isOpenRouter() && !model.isFree()) tools.add(RunTools.webSearch());
        body.add("tools", tools);
        body.addProperty("tool_choice", "auto");
        return stream(
                credential, body, cancellation, thinking, root -> parseStepResponse(root, model));
    }

    /**
     * A request that offers no tools, so the model answers in words. A run uses it to summarize its
     * earlier steps. A summary cut off at the model's output limit keeps the words it wrote.
     */
    public StepReply completeSummary(
            byte[] credential, JsonArray messages, int promptTokens, Cancellation cancellation)
            throws IOException {
        return completeSummary(credential, messages, promptTokens, cancellation, thinking -> {});
    }

    /** As above, with the newest part of the reasoning told to {@code thinking} as it arrives. */
    public StepReply completeSummary(
            byte[] credential,
            JsonArray messages,
            int promptTokens,
            Cancellation cancellation,
            Consumer<CharSequence> thinking)
            throws IOException {
        return stream(
                credential,
                body(messages, promptTokens),
                cancellation,
                thinking,
                root -> {
                    StepReply reply = parseStepResponse(root, model);
                    if (!reply.calls.isEmpty()) throw new InvalidModelResponse("tool");
                    if (reply.cutOff && reply.text.isEmpty())
                        throw new InvalidModelResponse("output limit");
                    return reply;
                });
    }

    private JsonObject body(JsonArray messages, int promptTokens) throws IOException {
        if (messages == null || messages.isEmpty())
            throw new IOException("Invalid model conversation");
        JsonObject body = new JsonObject();
        body.addProperty("model", model.id);
        if (model.provider.isOpenRouter()) addRouting(body, model, reasoning);
        body.addProperty("stream", true);
        if (!model.provider.isOpenRouter()) {
            JsonObject options = new JsonObject();
            options.addProperty("include_usage", true);
            body.add("stream_options", options);
        }
        int room = Math.min(model.maxCompletionTokens, model.contextTokens - promptTokens);
        if (room >= 1) body.addProperty("max_tokens", room);
        body.add("messages", messages.deepCopy());
        return body;
    }

    /** The tokens and cost reported for one request. */
    public static final class Usage {
        private static final Usage UNREPORTED = new Usage(0, 0, 0, null, 0);

        public final int totalTokens;
        public final int promptTokens;

        /** Prompt tokens read from the provider's cache; 0 if not reported. */
        public final int cachedTokens;

        /** The cost OpenRouter reported; null from a model server, which reports none. */
        public final java.math.BigDecimal costUsd;

        /** Web searches OpenRouter ran within the request; 0 if not reported. */
        public final int webSearches;

        private Usage(
                int totalTokens,
                int promptTokens,
                int cachedTokens,
                java.math.BigDecimal costUsd,
                int webSearches) {
            this.totalTokens = totalTokens;
            this.promptTokens = promptTokens;
            this.cachedTokens = cachedTokens;
            this.costUsd = costUsd;
            this.webSearches = webSearches;
        }
    }

    /**
     * The model's reply to one request of a run: words that end it, tool calls to run, or a reply
     * cut off at the model's output limit.
     */
    public static final class StepReply {
        /** The model's words; with tool calls they may be empty and are not shown. */
        public final String text;

        public final List<RunTools.Call> calls;

        /**
         * The model's reasoning for this reply, up to {@link #MAX_REASONING_BYTES}; empty if it
         * returned none. Shown to the person, never repeated to the model.
         */
        public final String reasoning;

        public final int totalTokens;
        public final int promptTokens;

        /**
         * Prompt tokens OpenRouter reported as read from the provider's cache; 0 if not reported.
         */
        public final int cachedTokens;

        /** The cost OpenRouter reported; null from a model server, which reports none. */
        public final java.math.BigDecimal costUsd;

        /**
         * True when the reply stopped at the model's output limit: its tool calls were cut short
         * and are left out, and its words may stop partway.
         */
        public final boolean cutOff;

        /** Web searches OpenRouter ran for this reply; their cost is in {@link #costUsd}. */
        public final int webSearches;

        /**
         * The {@code reasoning_content} a model server sent, which the next request repeats because
         * some servers refuse a tool conversation without it; null from OpenRouter or when none
         * came.
         */
        private final String reasoningContent;

        private StepReply(
                String text,
                List<RunTools.Call> calls,
                String reasoning,
                String reasoningContent,
                Usage used,
                boolean cutOff) {
            this.text = text;
            this.reasoningContent = reasoningContent;
            this.calls = List.copyOf(calls);
            this.reasoning = reasoning;
            this.cutOff = cutOff;
            this.totalTokens = used.totalTokens;
            this.promptTokens = used.promptTokens;
            this.cachedTokens = used.cachedTokens;
            this.costUsd = used.costUsd;
            this.webSearches = used.webSearches;
        }

        /** The reply as the assistant message the next request repeats. */
        public JsonObject assistantMessage() {
            JsonObject message = new JsonObject();
            message.addProperty("role", "assistant");
            if (text.isEmpty()) message.add("content", JsonNull.INSTANCE);
            else message.addProperty("content", text);
            if (reasoningContent != null)
                message.addProperty("reasoning_content", reasoningContent);
            if (!calls.isEmpty()) {
                JsonArray toolCalls = new JsonArray();
                for (RunTools.Call call : calls) toolCalls.add(call.toJson());
                message.add("tool_calls", toolCalls);
            }
            return message;
        }
    }

    private static StepReply parseStepResponse(JsonObject root, ModelChoice model)
            throws IOException {
        JsonObject message;
        String finish;
        String text;
        try {
            JsonObject choice = validatedChoice(root, model);
            message = choice.getAsJsonObject("message");
            finish = string(choice, "finish_reason");
            text =
                    message.has("content") && !message.get("content").isJsonNull()
                            ? string(message, "content").trim()
                            : "";
        } catch (RuntimeException malformed) {
            throw new InvalidModelResponse("envelope");
        }
        boolean hasCalls = hasToolCalls(message);
        boolean cutOff = "length".equals(finish);
        List<RunTools.Call> calls = List.of();
        if ("tool_calls".equals(finish) && hasCalls) {
            try {
                calls = RunTools.parse(message.getAsJsonArray("tool_calls"));
            } catch (RunTools.InvalidCall invalid) {
                throw new InvalidModelResponse("tool " + invalid.category);
            } catch (RuntimeException malformed) {
                throw new InvalidModelResponse("tool");
            }
        } else if (!cutOff && (!"stop".equals(finish) || hasCalls || text.isEmpty())) {
            throw new InvalidModelResponse(finishCategory(finish, hasCalls));
        }
        Usage used;
        try {
            used = usage(root.getAsJsonObject("usage"), model);
        } catch (RuntimeException malformed) {
            throw new InvalidModelResponse("usage");
        }
        return new StepReply(
                text,
                calls,
                shortened(reasoning(message), MAX_REASONING_BYTES),
                model.provider.isOpenRouter() ? null : text(message.get("reasoning_content")),
                used,
                cutOff);
    }

    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    /**
     * The {@code reasoning} or {@code reasoning_content} text, or else the text and summary parts
     * of {@code reasoning_details}; empty when there is none or it is malformed, which never
     * refuses the reply.
     */
    private static String reasoning(JsonObject message) {
        for (String field : List.of("reasoning", "reasoning_content")) {
            String plain = text(message.get(field));
            if (plain != null && !plain.isBlank()) return plain.trim();
        }
        JsonElement details = message.get("reasoning_details");
        if (details == null || !details.isJsonArray()) return "";
        List<String> parts = new ArrayList<>();
        for (JsonElement detail : details.getAsJsonArray()) {
            if (!detail.isJsonObject()) continue;
            for (String field : List.of("text", "summary")) {
                JsonElement part = detail.getAsJsonObject().get(field);
                if (part != null && part.isJsonPrimitive() && part.getAsJsonPrimitive().isString())
                    parts.add(part.getAsString().trim());
            }
        }
        parts.removeIf(String::isEmpty);
        return String.join("\n\n", parts);
    }

    private static int webSearches(JsonObject usage) {
        for (String field : List.of("server_tool_use", "server_tool_use_details")) {
            try {
                return integer(
                        usage.getAsJsonObject(field), "web_search_requests", Integer.MAX_VALUE);
            } catch (RuntimeException unreported) {
            }
        }
        return 0;
    }

    private static int cachedTokens(JsonObject usage, int prompt) {
        try {
            return integer(usage.getAsJsonObject("prompt_tokens_details"), "cached_tokens", prompt);
        } catch (RuntimeException unreported) {
            return 0;
        }
    }

    interface ResponseParser<T> {
        T parse(String json) throws IOException;
    }

    private interface ReplyParser<T> {
        T parse(JsonObject reply) throws IOException;
    }

    private interface ResponseReader<T> {
        T read(InputStream input) throws IOException;
    }

    private <T> T stream(
            byte[] credential,
            JsonObject body,
            Cancellation cancellation,
            Consumer<CharSequence> thinking,
            ReplyParser<T> parser)
            throws IOException {
        return send(
                credential,
                body,
                cancellation,
                model.provider.isOpenRouter() ? STALL_MILLIS : 0,
                input -> {
                    ReplyStream reply = new ReplyStream(model.provider);
                    try {
                        return parser.parse(reply.read(input, cancellation, thinking));
                    } catch (RequestFailed failed) {
                        failed.used = reported(reply.usage());
                        throw failed;
                    }
                });
    }

    private Usage reported(JsonObject usage) {
        try {
            return usage == null ? null : usage(usage, model);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /**
     * One request to the provider with the person's key, not streamed; the reply may hold up to
     * maxResponseBytes. A readTimeoutMillis of 0 waits for the reply without a limit.
     */
    <T> T send(
            byte[] credential,
            JsonObject body,
            Cancellation cancellation,
            int maxResponseBytes,
            int readTimeoutMillis,
            ResponseParser<T> parser)
            throws IOException {
        return send(
                credential,
                body,
                cancellation,
                readTimeoutMillis,
                input -> parser.parse(readAll(input, maxResponseBytes, cancellation)));
    }

    private static String readAll(InputStream input, int maxBytes, Cancellation cancellation)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int count;
        while ((count = input.read(chunk)) != -1) {
            cancellation.check();
            if (bytes.size() + count > maxBytes) throw tooLarge();
            bytes.write(chunk, 0, count);
        }
        cancellation.check();
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    static RequestFailed tooLarge() {
        return new RequestFailed("Model response exceeds limit", RequestFailed.Retry.NO);
    }

    private <T> T send(
            byte[] credential,
            JsonObject body,
            Cancellation cancellation,
            int readTimeoutMillis,
            ResponseReader<T> reader)
            throws IOException {
        if (credential == null || credential.length < 1 || credential.length > 1024)
            throw new IOException("Invalid credential");
        byte[] owned = credential.clone();
        try {
            // HttpURLConnection requires a String header; that temporary immutable value cannot be
            // wiped.
            return completeWithKey(
                    new String(owned, StandardCharsets.US_ASCII),
                    body,
                    cancellation,
                    readTimeoutMillis,
                    reader);
        } finally {
            Arrays.fill(owned, (byte) 0);
        }
    }

    private <T> T completeWithKey(
            String apiKey,
            JsonObject body,
            Cancellation cancellation,
            int readTimeoutMillis,
            ResponseReader<T> reader)
            throws IOException {
        cancellation.check();
        if (!ModelKeyInput.sendable(apiKey)) throw new IOException("Invalid credential");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_STEP_REQUEST_BYTES)
            throw new IOException("Model request exceeds limit");
        java.net.HttpURLConnection connection;
        try {
            connection =
                    connections.open(
                            new java.net.URL(model.provider.baseUrl + "/chat/completions"));
        } catch (IOException | RuntimeException error) {
            throw new IOException(model.provider.label + " connection failed");
        }
        cancellation.connection = connection;
        try {
            cancellation.check();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty(
                    "Accept",
                    body.get("stream").getAsBoolean() ? "text/event-stream" : "application/json");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            cancellation.check();
            int status = connection.getResponseCode();
            if (status != 200)
                throw refused(model.provider, status, connection.getHeaderField("Retry-After"));
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                return reader.read(input);
            }
        } catch (IOException | RuntimeException failure) {
            cancellation.check();
            if (failure instanceof RequestFailed) throw (IOException) failure;
            // Never expose a transport/provider exception, body, prompt or authorization header.
            throw new RequestFailed(
                    model.provider.label + " request failed (" + transportKind(failure) + ")",
                    failure instanceof IOException
                            ? RequestFailed.Retry.CONNECTION
                            : RequestFailed.Retry.NO);
        } finally {
            cancellation.connection = null;
            connection.disconnect();
            Arrays.fill(payload, (byte) 0);
        }
    }

    /**
     * OpenRouter's 402. Before a paid request it sets aside credit for the prompt and the longest
     * reply the request allows, and refuses one the balance or the key's limit cannot cover.
     */
    public static final String CREDIT_UNAVAILABLE =
            "OpenRouter credit unavailable: before it answers, OpenRouter sets aside enough credit"
                    + " for the model's longest possible reply, and your OpenRouter credit does not"
                    + " cover that. Add credit on openrouter.ai, or pick a cheaper model";

    /** A model server's 402: it wants payment before it answers. */
    public static final String SERVER_CREDIT_UNAVAILABLE =
            "Model server refused the request for payment (HTTP 402). Check the credit or billing"
                    + " of the account the key belongs to";

    private static final java.util.Set<Integer> BUSY_STATUSES =
            java.util.Set.of(408, 429, 500, 502, 503, 504);

    private static RequestFailed refused(ModelProvider provider, int status, String retryAfter) {
        String message =
                status == 401
                        ? provider.label + " authentication failed"
                        : status == 429
                                ? provider.label + " rate limit"
                                : status == 402
                                        ? provider.isOpenRouter()
                                                ? CREDIT_UNAVAILABLE
                                                : SERVER_CREDIT_UNAVAILABLE
                                        : provider.label
                                                + " request rejected (HTTP "
                                                + status
                                                + ")";
        return new RequestFailed(
                message,
                BUSY_STATUSES.contains(status) ? RequestFailed.Retry.BUSY : RequestFailed.Retry.NO,
                retryAfter != null && retryAfter.trim().matches("[0-9]{1,6}")
                        ? Integer.parseInt(retryAfter.trim())
                        : -1);
    }

    /** A mid-stream error chunk: the request failed after the provider had answered 200. */
    static RequestFailed failedMidStream(ModelProvider provider, JsonElement error) {
        JsonElement code = error.isJsonObject() ? error.getAsJsonObject().get("code") : null;
        int status = -1;
        boolean busy = false;
        if (code != null && code.isJsonPrimitive() && code.getAsJsonPrimitive().isNumber()) {
            status = code.getAsDouble() == (int) code.getAsDouble() ? (int) code.getAsDouble() : -1;
            busy = BUSY_STATUSES.contains(status);
        } else if (code != null && code.isJsonPrimitive() && code.getAsJsonPrimitive().isString()) {
            busy = BUSY_ERRORS.contains(code.getAsString());
        }
        return new RequestFailed(
                status == 429
                        ? provider.label + " rate limit"
                        : provider.label + " request failed (provider error)",
                busy ? RequestFailed.Retry.BUSY : RequestFailed.Retry.NO);
    }

    private static final java.util.Set<String> BUSY_ERRORS =
            java.util.Set.of(
                    "server_error",
                    "server",
                    "timeout",
                    "rate_limit_exceeded",
                    "provider_overloaded",
                    "provider_unavailable");

    /**
     * A request that failed; its message is from a fixed, nonsecret set. {@link #retry} says
     * whether sending the same request again can help, and why.
     */
    public static class RequestFailed extends IOException {
        /** What a second try of the same request could get past, or {@link #NO}. */
        public enum Retry {
            NO,
            /** The connection failed, dropped or went silent. */
            CONNECTION,
            /**
             * The provider, or OpenRouter's upstream provider, was busy, rate limited or failing.
             */
            BUSY,
            /** The model's reply was garbled; a new sample may not be. */
            REPLY
        }

        public final Retry retry;

        /** Seconds the provider asked to wait before trying again, or -1 when it did not say. */
        public final int retryAfterSeconds;

        private Usage used;

        /** What the provider reported this failed try used, or null when it did not say. */
        public Usage used() {
            return used;
        }

        RequestFailed(String message, Retry retry) {
            this(message, retry, -1);
        }

        RequestFailed(String message, Retry retry, int retryAfterSeconds) {
            super(message);
            this.retry = retry;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    static final class InvalidModelResponse extends RequestFailed {
        InvalidModelResponse(String category) {
            super(
                    "Invalid model response (" + category + ")",
                    category.startsWith("tool arguments json")
                            ? RequestFailed.Retry.REPLY
                            : RequestFailed.Retry.NO);
        }
    }

    private static boolean hasToolCalls(JsonObject message) {
        JsonElement calls = message.get("tool_calls");
        return calls != null
                && !calls.isJsonNull()
                && !(calls.isJsonArray() && calls.getAsJsonArray().isEmpty());
    }

    /** {@code answer} cut to at most {@code maxBytes} of UTF-8, ending in "…" when cut. */
    public static String shortened(String answer, int maxBytes) {
        if (answer.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return answer;
        int end = 0;
        int bytes = "…".getBytes(StandardCharsets.UTF_8).length;
        while (end < answer.length()) {
            int next = answer.offsetByCodePoints(end, 1);
            bytes += answer.substring(end, next).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) break;
            end = next;
        }
        return answer.substring(0, end) + "…";
    }

    private static void addRouting(JsonObject body, ModelChoice model, ReasoningLevel level) {
        if (!model.providers().isEmpty()) {
            JsonArray only = new JsonArray();
            model.providers().forEach(only::add);
            JsonObject provider = new JsonObject();
            provider.add("only", only);
            body.add("provider", provider);
        }
        JsonObject reasoning = new JsonObject();
        reasoning.addProperty("effort", (model.reasoning ? level : ReasoningLevel.DEFAULT).effort);
        reasoning.addProperty("exclude", false);
        body.add("reasoning", reasoning);
    }

    private static String transportKind(Exception failure) {
        if (failure instanceof java.net.SocketTimeoutException) return "read timeout";
        if (failure instanceof javax.net.ssl.SSLException) return "TLS";
        if (failure instanceof java.net.ConnectException
                || failure instanceof java.net.UnknownHostException
                || failure instanceof java.net.NoRouteToHostException) return "connection";
        if (failure instanceof java.io.EOFException
                || failure instanceof java.net.SocketException
                || failure instanceof java.net.ProtocolException) return "connection closed";
        return "other";
    }

    private static String finishCategory(String finish, boolean toolCall) {
        String known =
                java.util.Set.of("stop", "content_filter", "error").contains(finish)
                        ? finish
                        : "other";
        return (toolCall ? "tool call with finish " : "no tool call, finish ") + known;
    }

    private static JsonObject validatedChoice(JsonObject root, ModelChoice model) {
        if (root.has("error")) throw new IllegalArgumentException();
        if (!model.acceptsReplyModel(string(root, "model"))) throw new IllegalArgumentException();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.size() != 1) throw new IllegalArgumentException();
        JsonObject choice = choices.get(0).getAsJsonObject();
        if (!"assistant".equals(string(choice.getAsJsonObject("message"), "role")))
            throw new IllegalArgumentException();
        return choice;
    }

    /**
     * OpenRouter's usage, which must report tokens and cost. A model server's tokens are counted
     * when it reports them, and anything else counts as unreported: it lists no cost and its usage
     * only sharpens the estimate of when to summarize.
     */
    private static Usage usage(JsonObject usage, ModelChoice model) {
        if (!model.provider.isOpenRouter()) {
            try {
                int prompt = integer(usage, "prompt_tokens", UsageBudget.MAX_PROMPT_TOKENS);
                int completion =
                        integer(usage, "completion_tokens", UsageBudget.MAX_COMPLETION_TOKENS);
                return new Usage(prompt + completion, prompt, cachedTokens(usage, prompt), null, 0);
            } catch (RuntimeException unreported) {
                return Usage.UNREPORTED;
            }
        }
        int prompt = integer(usage, "prompt_tokens", UsageBudget.MAX_PROMPT_TOKENS);
        int completion = integer(usage, "completion_tokens", UsageBudget.MAX_COMPLETION_TOKENS);
        int total = integer(usage, "total_tokens", UsageBudget.MAX_REPLY_TOKENS);
        if (total != prompt + completion) throw new IllegalArgumentException();
        JsonPrimitive cost = usage.getAsJsonPrimitive("cost");
        if (cost == null
                || !cost.isNumber()
                || cost.getAsBigDecimal().signum() < 0
                || (model.isFree() && cost.getAsBigDecimal().signum() > 0))
            throw new IllegalArgumentException();
        return new Usage(
                total,
                prompt,
                cachedTokens(usage, prompt),
                cost.getAsBigDecimal(),
                webSearches(usage));
    }

    static String string(JsonObject obj, String name) {
        JsonPrimitive value = obj.getAsJsonPrimitive(name);
        if (value == null || !value.isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    static int integer(JsonObject obj, String name, int max) {
        JsonPrimitive value = obj.getAsJsonPrimitive(name);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException();
        int number = value.getAsBigDecimal().intValueExact();
        if (number < 0 || number > max) throw new IllegalArgumentException();
        return number;
    }

    static JsonElement strictJson(String json) throws IOException {
        return strictJson(json, MAX_RESPONSE_BYTES);
    }

    static JsonElement strictJson(String json, int maxBytes) throws IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > maxBytes)
            throw new IOException("JSON limit");
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.STRICT);
        JsonElement result = readJson(reader, 0);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
        return result;
    }

    private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("JSON nesting limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IOException("Duplicate key");
                    object.add(key, readJson(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) array.add(readJson(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw new IOException("Invalid JSON");
        }
    }
}
