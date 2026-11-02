package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

/** A streamed reply put back together from its fragments, and how a failed stream is classed. */
public final class ReplyStreamTest {
    private static final byte[] KEY = "dummy-key".getBytes(StandardCharsets.US_ASCII);
    private static final String USAGE =
            "{\"prompt_tokens\":12,\"completion_tokens\":5,\"total_tokens\":17,\"cost\":0}";

    private static JsonArray conversation() {
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "preview");
        JsonArray messages = new JsonArray();
        messages.add(user);
        return messages;
    }

    private static String chunk(String delta, String finish) {
        return chunk(delta, finish, null);
    }

    private static String chunk(String delta, String finish, String usage) {
        return "data: {\"id\":\"gen-1\",\"object\":\"chat.completion.chunk\",\"model\":\""
                + ModelClient.MODEL
                + "\",\"choices\":[{\"index\":0,\"delta\":"
                + delta
                + ",\"finish_reason\":"
                + (finish == null ? "null" : "\"" + finish + "\"")
                + "}]"
                + (usage == null ? "" : ",\"usage\":" + usage)
                + "}\n\n";
    }

    private static String ending(String finish) {
        return chunk("{}", finish) + chunk("{}", finish, USAGE) + "data: [DONE]\n\n";
    }

    private static ModelClient.StepReply send(String body) throws IOException {
        return send(new ModelProtocolTest.FakeConnection(200, body), text -> {});
    }

    private static ModelClient.StepReply send(
            ModelProtocolTest.FakeConnection connection, Consumer<CharSequence> thinking)
            throws IOException {
        return new ModelClient(url -> connection, ModelChoice.DEFAULT)
                .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation(), thinking);
    }

    private static ModelClient.RequestFailed failure(ModelProtocolTest.FakeConnection connection) {
        return assertThrows(ModelClient.RequestFailed.class, () -> send(connection, text -> {}));
    }

    private static ModelClient.RequestFailed failure(String body) throws IOException {
        return failure(new ModelProtocolTest.FakeConnection(200, body));
    }

    @Test
    public void putsTheWordsReasoningAndSplitToolCallsBackTogether() throws Exception {
        String body =
                SseBody.HEARTBEAT
                        + chunk("{\"role\":\"assistant\",\"content\":null}", null)
                        + chunk(
                                "{\"reasoning\":\"First \",\"reasoning_details\":[{\"type\":"
                                        + "\"reasoning.text\",\"text\":\"First \",\"index\":0}]}",
                                null)
                        + ": OPENROUTER PROCESSING\n\n"
                        + chunk(
                                "{\"reasoning\":\"read it.\",\"reasoning_details\":[{\"type\":"
                                        + "\"reasoning.text\",\"text\":\"read it.\",\"index\":0}]}",
                                null)
                        + chunk(
                                "{\"tool_calls\":[{\"index\":0,\"id\":\"call_a\",\"type\":"
                                        + "\"function\",\"function\":{\"name\":\"read_file\","
                                        + "\"arguments\":\"{\\\"pa\"}}]}",
                                null)
                        + chunk(
                                "{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":"
                                        + "\"th\\\":\\\"res/x.xml\\\"}\"}}]}",
                                null)
                        + chunk(
                                "{\"tool_calls\":[{\"index\":1,\"id\":\"call_b\",\"type\":"
                                        + "\"function\",\"function\":{\"name\":\"list_files\","
                                        + "\"arguments\":\"\"}}]}",
                                null)
                        + chunk(
                                "{\"tool_calls\":[{\"index\":1,\"function\":{\"arguments\":\"{}\"}}]}",
                                null)
                        + ending("tool_calls");
        ModelClient.StepReply reply = send(body);
        assertEquals("First read it.", reply.reasoning);
        assertEquals(2, reply.calls.size());
        assertEquals("call_a", reply.calls.get(0).id);
        assertEquals("read_file", reply.calls.get(0).name);
        assertEquals("res/x.xml", reply.calls.get(0).path);
        assertEquals("list_files", reply.calls.get(1).name);
        assertEquals(17, reply.totalTokens);
        assertEquals("", reply.text);
    }

    @Test
    public void readsTheWordsAndReasoningGivenOnlyAsReasoningDetails() throws Exception {
        String body =
                chunk("{\"role\":\"assistant\"}", null)
                        + chunk(
                                "{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":"
                                        + "\"Weigh \",\"index\":0}]}",
                                null)
                        + chunk(
                                "{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":"
                                        + "\"both.\",\"index\":0},{\"type\":\"reasoning.encrypted\","
                                        + "\"data\":\"c2VjcmV0\",\"index\":1}]}",
                                null)
                        + chunk("{\"content\":\"Hel\"}", null)
                        + chunk("{\"content\":\"lo there\"}", null)
                        + ending("stop");
        ModelClient.StepReply reply = send(body);
        assertEquals("Weigh both.", reply.reasoning);
        assertEquals("Hello there", reply.text);
        assertTrue(reply.calls.isEmpty());
    }

    @Test
    public void tellsTheReasoningSoFarEachTimeMoreArrives() throws Exception {
        String body =
                chunk("{\"role\":\"assistant\"}", null)
                        + chunk("{\"reasoning\":\"Look \"}", null)
                        + chunk("{\"reasoning\":\"at the \"}", null)
                        + chunk("{\"reasoning\":\"layout.\"}", null)
                        + chunk("{\"content\":\"Done.\"}", null)
                        + ending("stop");
        List<String> seen = new ArrayList<>();
        ModelClient.StepReply reply =
                send(
                        new ModelProtocolTest.FakeConnection(200, body),
                        text -> {
                            String now = text.toString();
                            if (!now.isEmpty()
                                    && (seen.isEmpty() || !seen.get(seen.size() - 1).equals(now)))
                                seen.add(now);
                        });
        assertEquals(List.of("Look ", "Look at the ", "Look at the layout."), seen);
        assertEquals("Look at the layout.", reply.reasoning);
    }

    @Test
    public void anErrorChunkMidStreamIsAFailedRequestNeverAReply() throws Exception {
        String start =
                chunk("{\"role\":\"assistant\"}", null) + chunk("{\"content\":\"Half\"}", null);
        for (String code : new String[] {"502", "503", "504", "500", "408", "\"server_error\""}) {
            ModelClient.RequestFailed failed =
                    failure(start + errorChunk(code) + "data: [DONE]\n\n");
            assertEquals(code, ModelClient.RequestFailed.Retry.BUSY, failed.retry);
            assertEquals("OpenRouter request failed (provider error)", failed.getMessage());
        }
        ModelClient.RequestFailed limited = failure(start + errorChunk("429"));
        assertEquals(ModelClient.RequestFailed.Retry.BUSY, limited.retry);
        assertEquals("OpenRouter rate limit", limited.getMessage());
        for (String code : new String[] {"400", "\"content_policy_violation\""})
            assertEquals(
                    code,
                    ModelClient.RequestFailed.Retry.NO,
                    failure(start + errorChunk(code)).retry);
    }

    private static String errorChunk(String code) {
        return "data: {\"id\":\"gen-1\",\"object\":\"chat.completion.chunk\",\"model\":\""
                + ModelClient.MODEL
                + "\",\"error\":{\"code\":"
                + code
                + ",\"message\":\"secret upstream text\"},\"choices\":[{\"index\":0,\"delta\":"
                + "{\"content\":\"\"},\"finish_reason\":\"error\"}]}\n\n";
    }

    @Test
    public void classesEachRefusedStatusAndKeepsTheWaitOpenRouterAsksFor() throws Exception {
        for (int status : new int[] {408, 429, 500, 502, 503, 504}) {
            ModelClient.RequestFailed failed =
                    failure(new ModelProtocolTest.FakeConnection(status, "secret"));
            assertEquals(
                    String.valueOf(status), ModelClient.RequestFailed.Retry.BUSY, failed.retry);
            assertEquals(-1, failed.retryAfterSeconds);
        }
        for (int status : new int[] {400, 401, 402, 403, 404, 413, 422})
            assertEquals(
                    String.valueOf(status),
                    ModelClient.RequestFailed.Retry.NO,
                    failure(new ModelProtocolTest.FakeConnection(status, "secret")).retry);
        ModelProtocolTest.FakeConnection waits = new ModelProtocolTest.FakeConnection(429, "");
        waits.headers.put("Retry-After", "7");
        assertEquals(7, failure(waits).retryAfterSeconds);
        ModelProtocolTest.FakeConnection dated = new ModelProtocolTest.FakeConnection(503, "");
        dated.headers.put("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT");
        assertEquals(-1, failure(dated).retryAfterSeconds);
    }

    @Test
    public void aSilentConnectionIsAStallWorthTryingAgainAndThinkingHasNoTimeLimit()
            throws Exception {
        ModelProtocolTest.FakeConnection stalled =
                new ModelProtocolTest.FakeConnection(200, "") {
                    @Override
                    public InputStream getInputStream() {
                        byte[] start =
                                (SseBody.HEARTBEAT + chunk("{\"reasoning\":\"Hm\"}", null))
                                        .getBytes(StandardCharsets.UTF_8);
                        return new InputStream() {
                            int next;

                            @Override
                            public int read() throws IOException {
                                if (next < start.length) return start[next++];
                                throw new java.net.SocketTimeoutException("secret");
                            }
                        };
                    }
                };
        ModelClient.RequestFailed failed = failure(stalled);
        assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, failed.retry);
        assertEquals("OpenRouter request failed (read timeout)", failed.getMessage());
        assertEquals(90_000, stalled.getReadTimeout());
        assertEquals(10_000, stalled.getConnectTimeout());
        assertEquals("text/event-stream", stalled.getRequestProperty("Accept"));
        JsonObject sent =
                JsonParser.parseString(stalled.sent.toString(StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertTrue(sent.get("stream").getAsBoolean());
    }

    @Test
    public void aStreamThatEndsBeforeItsFinishIsADroppedConnection() throws Exception {
        ModelClient.RequestFailed failed =
                failure(
                        chunk("{\"role\":\"assistant\"}", null)
                                + chunk("{\"content\":\"Hal\"}", null));
        assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, failed.retry);
        assertEquals("OpenRouter request failed (connection closed)", failed.getMessage());
    }

    @Test
    public void boundsWhatTheReplyHoldsNotTheBytesOfTheStream() throws Exception {
        StringBuilder many = new StringBuilder(chunk("{\"role\":\"assistant\"}", null));
        String piece = "w".repeat(100);
        int pieces = ModelClient.MAX_RESPONSE_BYTES / 2 / piece.length();
        for (int index = 0; index < pieces; index++)
            many.append(chunk("{\"content\":\"" + piece + "\"}", null));
        assertTrue(many.length() > ModelClient.MAX_RESPONSE_BYTES);
        assertEquals(pieces * piece.length(), send(many + ending("stop")).text.length());

        String tooMuch = "y".repeat(ModelClient.MAX_RESPONSE_BYTES / 2 + 1);
        ModelClient.RequestFailed oversized =
                failure(
                        chunk("{\"content\":\"" + tooMuch + "\"}", null)
                                + chunk(
                                        "{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":"
                                                + "\"function\",\"function\":{\"name\":"
                                                + "\"write_file\",\"arguments\":\""
                                                + tooMuch
                                                + "\"}}]}",
                                        null)
                                + ending("tool_calls"));
        assertEquals("Model response exceeds limit", oversized.getMessage());
        assertEquals(ModelClient.RequestFailed.Retry.NO, oversized.retry);

        ModelClient.RequestFailed longLine =
                failure("data: " + "z".repeat(ReplyStream.MAX_EVENT_BYTES) + "\n\n");
        assertEquals("Model response exceeds limit", longLine.getMessage());
    }

    @Test
    public void aWholeFileSentInOneEventFarBeyondOneMebibyteIsKept() throws Exception {
        String content = "line\\\\n".repeat(5 * 1024 * 1024 / 6);
        String arguments =
                "{\\\"path\\\":\\\"res/raw/big.txt\\\",\\\"content\\\":\\\"" + content + "\\\"}";
        ModelClient.StepReply reply =
                send(
                        chunk(
                                        "{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,"
                                                + "\"id\":\"a\",\"type\":\"function\",\"function\":"
                                                + "{\"name\":\"write_file\",\"arguments\":\""
                                                + arguments
                                                + "\"}}]}",
                                        null)
                                + ending("tool_calls"));
        assertEquals("line\n".repeat(5 * 1024 * 1024 / 6), reply.calls.get(0).content);
    }

    @Test
    public void onlyGarbledToolArgumentsAreAReplyWorthAskingForAgain() throws Exception {
        String garbled =
                chunk(
                                "{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":\"function\","
                                        + "\"function\":{\"name\":\"write_file\",\"arguments\":"
                                        + "\"{\\\"path\\\":\"}}]}",
                                null)
                        + ending("tool_calls");
        ModelClient.RequestFailed failed = failure(garbled);
        assertEquals("Invalid model response (tool arguments json)", failed.getMessage());
        assertEquals(ModelClient.RequestFailed.Retry.REPLY, failed.retry);
        String unknownTool =
                chunk(
                                "{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":\"function\","
                                        + "\"function\":{\"name\":\"run_shell\",\"arguments\":"
                                        + "\"{}\"}}]}",
                                null)
                        + ending("tool_calls");
        assertEquals(ModelClient.RequestFailed.Retry.NO, failure(unknownTool).retry);
        assertEquals(
                ModelClient.RequestFailed.Retry.NO,
                failure("data: {not json\n\n" + ending("stop")).retry);
    }

    @Test
    public void stopDuringTheStreamIsNeverTriedAgain() throws Exception {
        ModelClient.Cancellation cancellation = new ModelClient.Cancellation();
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(
                        200, chunk("{\"reasoning\":\"Hm\"}", null) + ending("stop"));
        IOException stopped =
                assertThrows(
                        IOException.class,
                        () ->
                                new ModelClient(url -> connection, ModelChoice.DEFAULT)
                                        .completeStep(
                                                KEY,
                                                conversation(),
                                                0,
                                                cancellation,
                                                text -> cancellation.cancel()));
        assertEquals("Model request cancelled", stopped.getMessage());
        assertFalse(stopped instanceof ModelClient.RequestFailed);
    }

    @Test
    public void aStreamCutPartwayThroughALineIsADroppedConnectionNeverAGarbledReply()
            throws Exception {
        String start =
                chunk("{\"role\":\"assistant\"}", null) + chunk("{\"content\":\"Hi\"}", null);
        String finish = chunk("{}", "stop");
        String usage = chunk("{}", "stop", USAGE);
        for (String cut :
                new String[] {
                    start + finish.substring(0, finish.length() / 2),
                    start + finish + usage.substring(0, usage.length() - 2),
                    start + finish + usage + "data: [DONE]"
                }) {
            ModelClient.RequestFailed failed = failure(cut);
            assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, failed.retry);
            assertEquals("OpenRouter request failed (connection closed)", failed.getMessage());
        }
    }

    @Test
    public void aStreamThatEndsAfterTheFinishButBeforeDoneIsADroppedConnection() throws Exception {
        String finished =
                chunk("{\"role\":\"assistant\"}", null)
                        + chunk("{\"content\":\"Hi\"}", null)
                        + chunk("{}", "stop");
        ModelClient.RequestFailed noUsage = failure(finished);
        assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, noUsage.retry);
        assertEquals("OpenRouter request failed (connection closed)", noUsage.getMessage());
        assertNull(noUsage.used());

        ModelClient.RequestFailed noDone = failure(finished + chunk("{}", "stop", USAGE));
        assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, noDone.retry);
        assertEquals(17, noDone.used().totalTokens);
        assertEquals(12, noDone.used().promptTokens);
    }

    @Test
    public void joinsAnEventsDataLinesUpToTheBlankLine() throws Exception {
        String body =
                chunk("{\"role\":\"assistant\"}", null)
                        + "data: {\"id\":\"gen-1\",\"object\":\"chat.completion.chunk\",\n"
                        + "data: \"model\":\""
                        + ModelClient.MODEL
                        + "\",\"choices\":[{\"index\":0,\n"
                        + ": a comment line inside the event\n"
                        + "data:\"delta\":{\"content\":\"Two lines\"},\"finish_reason\":null}]}\r\n"
                        + "\r\n"
                        + ending("stop");
        assertEquals("Two lines", send(body).text);
    }

    @Test
    public void thinkingFarBeyondTheReplyLimitKeepsItsStartAndShowsItsNewestPart()
            throws Exception {
        String piece = "a".repeat(16 * 1024);
        StringBuilder body = new StringBuilder(chunk("{\"role\":\"assistant\"}", null));
        body.append(chunk(thinking("Start here. "), null));
        int pieces = ModelClient.MAX_RESPONSE_BYTES / piece.length() + 2;
        for (int index = 0; index < pieces; index++) body.append(chunk(thinking(piece), null));
        body.append(chunk(thinking(" The end."), null));
        body.append(chunk("{\"content\":\"Done.\"}", null)).append(ending("stop"));
        int[] longest = {0};
        String[] last = {""};
        ModelClient.StepReply reply =
                send(
                        new ModelProtocolTest.FakeConnection(200, body.toString()),
                        text -> {
                            longest[0] = Math.max(longest[0], text.length());
                            last[0] = text.toString();
                        });
        assertEquals("Done.", reply.text);
        assertTrue(reply.reasoning.startsWith("Start here. aaa"));
        assertTrue(reply.reasoning.endsWith("…"));
        assertTrue(
                reply.reasoning.getBytes(StandardCharsets.UTF_8).length
                        <= ModelClient.MAX_REASONING_BYTES);
        assertTrue(last[0].endsWith("a The end."));
        assertTrue(last[0].length() >= 8 * 1024);
        assertTrue(longest[0] <= 4 * ModelClient.MAX_REASONING_BYTES);
    }

    @Test
    public void theKeptStartOfLongReasoningNeverEndsHalfwayThroughACharacter() throws Exception {
        int kept = 2 * ModelClient.MAX_REASONING_BYTES;
        String body =
                chunk("{\"role\":\"assistant\"}", null)
                        + chunk(
                                "{\"reasoning\":\"" + "a".repeat(kept - 1) + "\uD83D\uDE00b\"}",
                                null)
                        + chunk("{\"reasoning\":\"c\"}", null)
                        + chunk("{\"content\":\"Done.\"}", null)
                        + ending("stop");
        String start =
                new ReplyStream(ModelProvider.OPENROUTER)
                        .read(
                                new java.io.ByteArrayInputStream(
                                        body.getBytes(StandardCharsets.UTF_8)),
                                new ModelClient.Cancellation(),
                                text -> {})
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("reasoning")
                        .getAsString();
        assertTrue(start.length() >= kept - 1);
        assertTrue(("a".repeat(kept - 1) + "\uD83D\uDE00bc").startsWith(start));
        assertFalse(Character.isHighSurrogate(start.charAt(start.length() - 1)));
    }

    private static String thinking(String piece) {
        return "{\"reasoning\":\""
                + piece
                + "\",\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\""
                + piece
                + "\",\"index\":0}]}";
    }

    @Test
    public void aFailedTryThatReportedItsUsageCarriesItsTokensAndCost() throws Exception {
        String garbled =
                chunk(
                                "{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"type\":\"function\","
                                        + "\"function\":{\"name\":\"write_file\",\"arguments\":"
                                        + "\"{\\\"path\\\":\"}}]}",
                                null)
                        + chunk("{}", "tool_calls")
                        + chunk(
                                "{}",
                                "tool_calls",
                                "{\"prompt_tokens\":12,\"completion_tokens\":5,"
                                        + "\"total_tokens\":17,\"cost\":0.0003,"
                                        + "\"prompt_tokens_details\":{\"cached_tokens\":4}}")
                        + "data: [DONE]\n\n";
        ModelClient.RequestFailed failed = failure(garbled);
        assertEquals(ModelClient.RequestFailed.Retry.REPLY, failed.retry);
        assertEquals(17, failed.used().totalTokens);
        assertEquals(12, failed.used().promptTokens);
        assertEquals(4, failed.used().cachedTokens);
        assertEquals(new java.math.BigDecimal("0.0003"), failed.used().costUsd);
        assertNull(failure(chunk("{\"content\":\"Hi\"}", null)).used());
    }

    @Test
    public void aNullModelFieldIsSkippedForTheNamedOne() throws Exception {
        String body =
                "data: {\"id\":\"gen-1\",\"model\":null,\"choices\":[{\"index\":0,\"delta\":"
                        + "{\"role\":\"assistant\"},\"finish_reason\":null}]}\n\n"
                        + chunk("{\"content\":\"Hi\"}", null)
                        + ending("stop");
        assertEquals("Hi", send(body).text);
    }
}
