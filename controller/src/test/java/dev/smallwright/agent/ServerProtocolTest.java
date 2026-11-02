package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** A run's requests to an OpenAI-compatible server, over fake connections; no real server. */
public class ServerProtocolTest {
    private static final ModelProvider SERVER =
            ModelProvider.openAiCompatible("https://api.example.com/v1");
    private static final ModelChoice MODEL = ModelChoice.atServer(SERVER, "gpt-5.5", 200_000);
    private static final byte[] KEY = "sk-proj-dummy.test_key".getBytes(StandardCharsets.US_ASCII);

    private static String reply(String model, String usage) {
        return "{\"model\":\""
                + model
                + "\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Hello\"}}]"
                + (usage == null ? "" : ",\"usage\":" + usage)
                + "}";
    }

    private static JsonArray conversation() {
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "preview");
        JsonArray messages = new JsonArray();
        messages.add(user);
        return messages;
    }

    private static ModelClient.StepReply send(ModelProtocolTest.FakeConnection connection)
            throws IOException {
        return new ModelClient(url -> connection, MODEL)
                .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
    }

    private static JsonObject body(ModelProtocolTest.FakeConnection connection) {
        return JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    public void sendsAPlainChatCompletionToTheServer() throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(
                        200, SseBody.of(reply("gpt-5.5", "{\"prompt_tokens\":3}")));
        String[] opened = {null};
        assertEquals(
                "Hello",
                new ModelClient(
                                url -> {
                                    opened[0] = url.toString();
                                    return connection;
                                },
                                MODEL)
                        .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation())
                        .text);
        assertEquals("https://api.example.com/v1/chat/completions", opened[0]);
        JsonObject body = body(connection);
        assertEquals("gpt-5.5", body.get("model").getAsString());
        assertTrue(body.get("stream").getAsBoolean());
        assertEquals(
                JsonParser.parseString("{\"include_usage\":true}"), body.get("stream_options"));
        for (String openRouterOnly :
                new String[] {"provider", "reasoning", "reasoning_effort", "max_tokens"})
            assertFalse(openRouterOnly, body.has(openRouterOnly));
        assertEquals("auto", body.get("tool_choice").getAsString());
        assertFalse(body.get("tools").toString().contains("openrouter:"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertEquals(0, connection.getReadTimeout());
    }

    @Test
    public void openRouterStillCountsASilentConnectionAsDead() throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(ModelProtocolTest.VALID));
        new ModelClient(url -> connection, ModelChoice.DEFAULT)
                .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
        assertEquals(ModelClient.STALL_MILLIS, connection.getReadTimeout());
    }

    @Test
    public void usageWithoutCostCountsTokensAndLeavesTheCostUnknown() throws Exception {
        ModelClient.StepReply reply =
                send(
                        new ModelProtocolTest.FakeConnection(
                                200,
                                SseBody.of(
                                        reply(
                                                "gpt-5.5",
                                                "{\"prompt_tokens\":12,\"completion_tokens\":5,"
                                                        + "\"total_tokens\":17,"
                                                        + "\"prompt_tokens_details\":"
                                                        + "{\"cached_tokens\":4}}"))));
        assertEquals(17, reply.totalTokens);
        assertEquals(12, reply.promptTokens);
        assertEquals(4, reply.cachedTokens);
        assertNull(reply.costUsd);
        assertEquals(0, reply.webSearches);
    }

    @Test
    public void aServerThatReportsNoUsageStillAnswers() throws Exception {
        for (String usage : new String[] {null, "{\"prompt_tokens\":\"many\"}", "{\"cost\":1}"}) {
            ModelClient.StepReply reply =
                    send(
                            new ModelProtocolTest.FakeConnection(
                                    200, SseBody.of(reply("gpt-5.5", usage))));
            assertEquals("Hello", reply.text);
            assertEquals(0, reply.totalTokens);
            assertNull(reply.costUsd);
        }
    }

    @Test
    public void aDatedVariantOfThePickedModelIsAcceptedButAnotherModelIsNot() throws Exception {
        assertEquals(
                "Hello",
                send(new ModelProtocolTest.FakeConnection(
                                200, SseBody.of(reply("gpt-5.5-2026-09-01", null))))
                        .text);
        for (String other : new String[] {"gpt-5.5o", "gpt-4o", "other/gpt-5.5", ""}) {
            ModelClient.RequestFailed refused =
                    assertThrows(
                            ModelClient.RequestFailed.class,
                            () ->
                                    send(
                                            new ModelProtocolTest.FakeConnection(
                                                    200, SseBody.of(reply(other, null)))));
            assertEquals("Invalid model response (envelope)", refused.getMessage());
        }
    }

    @Test
    public void refusalsNameTheModelServerNotOpenRouter() throws Exception {
        Object[][] expected = {
            {401, "Model server authentication failed", ModelClient.RequestFailed.Retry.NO},
            {402, ModelClient.SERVER_CREDIT_UNAVAILABLE, ModelClient.RequestFailed.Retry.NO},
            {429, "Model server rate limit", ModelClient.RequestFailed.Retry.BUSY},
            {503, "Model server request rejected (HTTP 503)", ModelClient.RequestFailed.Retry.BUSY},
            {400, "Model server request rejected (HTTP 400)", ModelClient.RequestFailed.Retry.NO},
        };
        for (Object[] each : expected) {
            ModelClient.RequestFailed refused =
                    assertThrows(
                            ModelClient.RequestFailed.class,
                            () ->
                                    send(
                                            new ModelProtocolTest.FakeConnection(
                                                    (int) each[0], "sk-proj-dummy preview")));
            assertEquals(each[1], refused.getMessage());
            assertEquals(each[2], refused.retry);
            assertFalse(refused.getMessage().contains("OpenRouter"));
            assertFalse(refused.toString().contains("dummy"));
        }
    }

    @Test
    public void transportAndMidStreamFailuresNameTheModelServer() throws Exception {
        ModelProtocolTest.FakeConnection broken =
                new ModelProtocolTest.FakeConnection(200, "") {
                    @Override
                    public java.io.InputStream getInputStream() throws IOException {
                        throw new java.net.SocketTimeoutException("secret");
                    }
                };
        assertEquals(
                "Model server request failed (read timeout)",
                assertThrows(IOException.class, () -> send(broken)).getMessage());
        String error =
                SseBody.event(
                        JsonParser.parseString("{\"error\":{\"code\":429,\"message\":\"slow\"}}")
                                .getAsJsonObject());
        assertEquals(
                "Model server rate limit",
                assertThrows(
                                IOException.class,
                                () -> send(new ModelProtocolTest.FakeConnection(200, error)))
                        .getMessage());
        assertEquals(
                "Model server request failed (connection closed)",
                assertThrows(
                                IOException.class,
                                () ->
                                        send(
                                                new ModelProtocolTest.FakeConnection(
                                                        200, SseBody.HEARTBEAT)))
                        .getMessage());
    }

    @Test
    public void reasoningContentIsShownAndSentBackToTheServerThatWroteIt() throws Exception {
        String toolCall =
                "{\"model\":\"gpt-5.5\",\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":"
                        + "{\"role\":\"assistant\",\"content\":null,"
                        + "\"reasoning_content\":\"Read the files first.\","
                        + "\"tool_calls\":[{\"id\":\"call_0\",\"type\":\"function\",\"function\":"
                        + "{\"name\":\"list_files\",\"arguments\":\"{}\"}}]}}]}";
        ModelClient.StepReply reply =
                send(new ModelProtocolTest.FakeConnection(200, SseBody.of(toolCall)));
        assertEquals("Read the files first.", reply.reasoning);
        assertEquals(
                "Read the files first.",
                reply.assistantMessage().get("reasoning_content").getAsString());
    }

    @Test
    public void openRouterNeverGetsReasoningBack() throws Exception {
        String toolCall =
                "{\"model\":\"deepseek/deepseek-v4.1-flash\",\"choices\":[{\"finish_reason\":"
                        + "\"tool_calls\",\"message\":{\"role\":\"assistant\",\"content\":null,"
                        + "\"reasoning_content\":\"Read the files first.\","
                        + "\"tool_calls\":[{\"id\":\"call_0\",\"type\":\"function\",\"function\":"
                        + "{\"name\":\"list_files\",\"arguments\":\"{}\"}}]}}],\"usage\":"
                        + "{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2,"
                        + "\"cost\":0.001}}";
        ModelClient.StepReply reply =
                new ModelClient(
                                url ->
                                        new ModelProtocolTest.FakeConnection(
                                                200, SseBody.of(toolCall)),
                                ModelChoice.DEFAULT)
                        .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
        assertEquals("Read the files first.", reply.reasoning);
        assertFalse(reply.assistantMessage().has("reasoning_content"));
    }

    @Test
    public void aKeyWithPunctuationIsSentButAHeaderBreakIsNot() throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(reply("gpt-5.5", null)));
        send(connection);
        assertEquals(
                "Bearer sk-proj-dummy.test_key", connection.getRequestProperty("Authorization"));
        assertThrows(
                IOException.class,
                () ->
                        new ModelClient(url -> connection, MODEL)
                                .completeStep(
                                        "sk-dummy\r\nX: y".getBytes(StandardCharsets.US_ASCII),
                                        conversation(),
                                        0,
                                        new ModelClient.Cancellation()));
    }
}
