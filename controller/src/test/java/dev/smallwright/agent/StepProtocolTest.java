package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** One request of a run over a fake connection: tool calls parsed, bad replies refused. */
public final class StepProtocolTest {
    private static final byte[] KEY = "dummy-key".getBytes(StandardCharsets.US_ASCII);
    private static final String CONTEXT = "secret-context-marker";

    private static JsonArray conversation() {
        JsonArray messages = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", CONTEXT);
        messages.add(user);
        return messages;
    }

    private static JsonObject call(String id, String name, String arguments) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("arguments", arguments);
        JsonObject call = new JsonObject();
        call.addProperty("id", id);
        call.addProperty("type", "function");
        call.add("function", function);
        return call;
    }

    private static JsonObject reply(String finish, JsonArray calls, String content) {
        JsonObject root = JsonParser.parseString(ModelProtocolTest.VALID).getAsJsonObject();
        JsonObject choice = root.getAsJsonArray("choices").get(0).getAsJsonObject();
        choice.addProperty("finish_reason", finish);
        JsonObject message = choice.getAsJsonObject("message");
        if (content == null) message.remove("content");
        else message.addProperty("content", content);
        if (calls != null) message.add("tool_calls", calls);
        return root;
    }

    private static JsonObject calls(JsonObject... calls) {
        JsonArray array = new JsonArray();
        for (JsonObject call : calls) array.add(call);
        return reply("tool_calls", array, null);
    }

    private static ModelClient.StepReply send(
            JsonObject reply, ModelProtocolTest.FakeConnection[] connection) throws Exception {
        connection[0] = new ModelProtocolTest.FakeConnection(200, SseBody.of(reply));
        ModelProtocolTest.FakeConnection opened = connection[0];
        return new ModelClient(url -> opened, ModelChoice.DEFAULT)
                .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
    }

    private static ModelClient.StepReply send(JsonObject reply) throws Exception {
        return send(reply, new ModelProtocolTest.FakeConnection[1]);
    }

    private static String refusal(JsonObject reply) {
        IOException error = assertThrows(IOException.class, () -> send(reply));
        String message = error.getMessage();
        assertFalse(message, message.contains(CONTEXT));
        assertFalse(message, message.contains("argument-marker"));
        return message;
    }

    @Test
    public void sendsTheConversationWithTheRunToolsAndAutoChoice() throws Exception {
        ModelProtocolTest.FakeConnection[] connection = new ModelProtocolTest.FakeConnection[1];
        ModelClient.StepReply answer = send(reply("stop", null, " Done. "), connection);
        assertEquals("Done.", answer.text);
        assertTrue(answer.calls.isEmpty());
        assertEquals(17, answer.totalTokens);
        JsonObject body =
                JsonParser.parseString(connection[0].sent.toString(StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertEquals(conversation(), body.getAsJsonArray("messages"));
        assertEquals("auto", body.get("tool_choice").getAsString());
        assertFalse(body.has("parallel_tool_calls"));
        assertFalse(body.toString().contains("max_price"));
        assertEquals(ModelChoice.DEFAULT.maxCompletionTokens, body.get("max_tokens").getAsInt());
        assertEquals(
                JsonParser.parseString("{\"effort\":\"low\",\"exclude\":false}"),
                body.get("reasoning"));
        List<String> names = new ArrayList<>();
        List<JsonObject> serverTools = new ArrayList<>();
        for (var tool : body.getAsJsonArray("tools")) {
            JsonObject offered = tool.getAsJsonObject();
            if ("function".equals(offered.get("type").getAsString()))
                names.add(offered.getAsJsonObject("function").get("name").getAsString());
            else serverTools.add(offered);
        }
        assertEquals(
                List.of(
                        JsonParser.parseString(
                                "{\"type\":\"openrouter:web_search\",\"parameters\":{\"engine\":"
                                        + "\"exa\",\"mode\":\"auto\",\"max_results\":5,"
                                        + "\"max_uses\":3}}")),
                serverTools);
        assertEquals(
                List.of(
                        "list_files",
                        "read_file",
                        "search_text",
                        "write_file",
                        "edit_file",
                        "delete_file",
                        "build",
                        "read_build_errors",
                        "lookup_code",
                        "search_libraries",
                        "add_library",
                        "remove_library",
                        "read_skill",
                        "add_skill",
                        "make_app_icon",
                        "make_image",
                        "fetch_web",
                        "ask",
                        "write_brief",
                        "update_plan",
                        "read_crash_report",
                        "look_at_app"),
                names);
    }

    @Test
    public void aFreeModelIsOfferedNoWebSearchBecauseEverySearchCosts() throws Exception {
        ModelChoice free =
                new ModelChoice(
                        "vendor/model:free",
                        "vendor/model:free",
                        "Vendor: Model (free)",
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO,
                        131072);
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(reply("stop", null, "Done.")));
        try {
            new ModelClient(url -> connection, free)
                    .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
        } catch (IOException otherModelName) {
        }
        JsonObject body =
                JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                        .getAsJsonObject();
        JsonArray tools = body.getAsJsonArray("tools");
        assertEquals(RunTools.schemas().size(), tools.size());
        for (var tool : tools)
            assertEquals("function", tool.getAsJsonObject().get("type").getAsString());
    }

    @Test
    public void countsTheWebSearchesOpenRouterReportsAndNoneWhenItReportsNone() throws Exception {
        assertEquals(0, send(reply("stop", null, "Done.")).webSearches);
        JsonObject searched = reply("stop", null, "Done.");
        JsonObject used = new JsonObject();
        used.addProperty("web_search_requests", 2);
        searched.getAsJsonObject("usage").add("server_tool_use", used);
        assertEquals(2, send(searched).webSearches);
        JsonObject streamed = reply("stop", null, "Done.");
        JsonObject details = new JsonObject();
        details.addProperty("web_search_requests", 3);
        details.addProperty("tool_calls_requested", 3);
        details.addProperty("tool_calls_executed", 3);
        streamed.getAsJsonObject("usage").add("server_tool_use_details", details);
        assertEquals(3, send(streamed).webSearches);
    }

    @Test
    public void parsesAFetchWebCallAndRefusesAMissingEmptyOrOversizedAddress() throws Exception {
        ModelClient.StepReply parsed =
                send(
                        calls(
                                call("a", "fetch_web", "{\"url\":\"https://example.com/a\"}"),
                                call(
                                        "b",
                                        "fetch_web",
                                        "{\"url\":\"https://example.com/b\",\"offset\":16001}")));
        assertEquals("https://example.com/a", parsed.calls.get(0).url);
        assertEquals(1, parsed.calls.get(0).offset);
        assertEquals(16001, parsed.calls.get(1).offset);
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "fetch_web", "{}"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "fetch_web",
                                        "{\"url\":\"https://example.com\",\"headers\":\"x\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "fetch_web", "{\"url\":\" \"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "fetch_web",
                                        "{\"url\":\"https://example.com/"
                                                + "a".repeat(RunTools.MAX_URL_CHARS)
                                                + "\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "fetch_web",
                                        "{\"url\":\"https://example.com\",\"offset\":0}"))));
    }

    @Test
    public void keepsThePromptAndCachedTokenCounts() throws Exception {
        ModelClient.StepReply plain = send(reply("stop", null, "Done."));
        assertEquals(12, plain.promptTokens);
        assertEquals(0, plain.cachedTokens);
        JsonObject cached = reply("stop", null, "Done.");
        cached.getAsJsonObject("usage")
                .add("prompt_tokens_details", JsonParser.parseString("{\"cached_tokens\":10}"));
        assertEquals(10, send(cached).cachedTokens);
        JsonObject impossible = reply("stop", null, "Done.");
        impossible
                .getAsJsonObject("usage")
                .add("prompt_tokens_details", JsonParser.parseString("{\"cached_tokens\":13}"));
        assertEquals(0, send(impossible).cachedTokens);
    }

    @Test
    public void aSummaryRequestOffersNoToolsAndKeepsALongerAnswer() throws Exception {
        String summary = "s".repeat(8 * 1024);
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(reply("stop", null, summary)));
        ModelClient client = new ModelClient(url -> connection, ModelChoice.DEFAULT);
        ModelClient.StepReply answer =
                client.completeSummary(KEY, conversation(), 0, new ModelClient.Cancellation());
        assertEquals(summary, answer.text);
        assertEquals(17, answer.totalTokens);
        JsonObject body =
                JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertEquals(conversation(), body.getAsJsonArray("messages"));
        assertFalse(body.has("tools"));
        assertFalse(body.has("tool_choice"));
        assertEquals(ModelClient.MODEL, body.get("model").getAsString());

        String longSummary = "t".repeat(256 * 1024);
        ModelProtocolTest.FakeConnection longer =
                new ModelProtocolTest.FakeConnection(
                        200, SseBody.of(reply("stop", null, longSummary)));
        assertEquals(
                longSummary,
                new ModelClient(url -> longer, ModelChoice.DEFAULT)
                        .completeSummary(KEY, conversation(), 0, new ModelClient.Cancellation())
                        .text);

        ModelProtocolTest.FakeConnection called =
                new ModelProtocolTest.FakeConnection(
                        200, SseBody.of(calls(call("call_1", "list_files", "{}"))));
        IOException refused =
                assertThrows(
                        IOException.class,
                        () ->
                                new ModelClient(url -> called, ModelChoice.DEFAULT)
                                        .completeSummary(
                                                KEY,
                                                conversation(),
                                                0,
                                                new ModelClient.Cancellation()));
        assertFalse(refused.getMessage(), refused.getMessage().contains(CONTEXT));
    }

    @Test
    public void aLongFinalReplyIsKeptWhole() throws Exception {
        String words = "Here is the plan. " + "Every part in plain words. ".repeat(2000) + "End.";
        JsonObject longReply = reply("stop", null, words);
        longReply.getAsJsonObject("usage").addProperty("completion_tokens", 40_000);
        longReply.getAsJsonObject("usage").addProperty("total_tokens", 40_012);
        ModelClient.StepReply answer = send(longReply);
        assertEquals(words, answer.text);
        assertEquals(40_012, answer.totalTokens);
    }

    @Test
    public void keepsTheReasoningButNeverRepeatsItToTheModel() throws Exception {
        JsonObject plain = reply("stop", null, "Done.");
        assertEquals("", send(plain).reasoning);

        JsonObject reasoned = calls(call("a", "list_files", "{}"));
        message(reasoned).addProperty("reasoning", "  First list the files.\nThen read them.  ");
        ModelClient.StepReply withReasoning = send(reasoned);
        assertEquals("First list the files.\nThen read them.", withReasoning.reasoning);
        assertFalse(withReasoning.assistantMessage().toString().contains("First list"));
        assertFalse(withReasoning.assistantMessage().has("reasoning"));

        JsonObject detailed = reply("stop", null, "Done.");
        message(detailed)
                .add(
                        "reasoning_details",
                        JsonParser.parseString(
                                "[{\"type\":\"reasoning.summary\",\"summary\":\"Weighed two"
                                        + " layouts.\"},{\"type\":\"reasoning.encrypted\","
                                        + "\"data\":\"c2VjcmV0\"},{\"type\":\"reasoning.text\","
                                        + "\"text\":\"Picked the simple one.\"}]"));
        assertEquals("Weighed two layouts.\n\nPicked the simple one.", send(detailed).reasoning);

        JsonObject both = reply("stop", null, "Done.");
        message(both).addProperty("reasoning", "The words.");
        message(both)
                .add(
                        "reasoning_details",
                        JsonParser.parseString(
                                "[{\"type\":\"reasoning.text\",\"text\":\"The words.\"}]"));
        assertEquals("The words.", send(both).reasoning);

        JsonObject odd = reply("stop", null, "Done.");
        message(odd).addProperty("reasoning", 7);
        message(odd).add("reasoning_details", JsonParser.parseString("{\"text\":1}"));
        ModelClient.StepReply oddReply = send(odd);
        assertEquals("Done.", oddReply.text);
        assertEquals("", oddReply.reasoning);

        JsonObject huge = reply("stop", null, "Done.");
        message(huge).addProperty("reasoning", "r".repeat(ModelClient.MAX_REASONING_BYTES * 2));
        String bounded = send(huge).reasoning;
        assertTrue(bounded.endsWith("…"));
        assertTrue(
                bounded.getBytes(StandardCharsets.UTF_8).length <= ModelClient.MAX_REASONING_BYTES);
    }

    private static JsonObject message(JsonObject reply) {
        return reply.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
    }

    @Test
    public void parsesEachToolCallAndRepeatsThemAsTheAssistantMessage() throws Exception {
        JsonObject reply =
                calls(
                        call("a", "list_files", "{}"),
                        call("b", "read_file", "{\"path\":\"res/x.xml\",\"offset\":3,\"limit\":9}"),
                        call("c", "search_text", "{\"query\":\"Button\",\"path\":\"java/\"}"),
                        call("d", "write_file", "{\"path\":\"res/y.xml\",\"content\":\"<a/>\"}"),
                        call(
                                "e",
                                "edit_file",
                                "{\"path\":\"res/y.xml\",\"old_text\":\"<a/>\",\"new_text\":\"<b/>\"}"),
                        call("f", "delete_file", "{\"path\":\"res/y.xml\"}"),
                        call("g", "build", ""),
                        call("h", "read_build_errors", "{}"));
        ModelClient.StepReply parsed = send(reply);
        assertEquals(8, parsed.calls.size());
        RunTools.Call read = parsed.calls.get(1);
        assertEquals("read_file", read.name);
        assertEquals("res/x.xml", read.path);
        assertEquals(3, read.offset);
        assertEquals(9, read.limit);
        assertEquals(1, parsed.calls.get(0).offset);
        assertEquals(RunTools.MAX_READ_LINES, parsed.calls.get(0).limit);
        assertEquals("Button", parsed.calls.get(2).query);
        assertEquals("java/", parsed.calls.get(2).path);
        assertEquals("<a/>", parsed.calls.get(3).content);
        assertEquals("<a/>", parsed.calls.get(4).oldText);
        assertEquals("<b/>", parsed.calls.get(4).newText);
        assertEquals("res/y.xml", parsed.calls.get(5).path);
        assertEquals("build", parsed.calls.get(6).name);
        assertEquals("read_build_errors", parsed.calls.get(7).name);
        JsonObject assistant = parsed.assistantMessage();
        assertEquals("assistant", assistant.get("role").getAsString());
        assertTrue(assistant.get("content").isJsonNull());
        JsonArray repeated = assistant.getAsJsonArray("tool_calls");
        assertEquals(8, repeated.size());
        assertEquals("h", repeated.get(7).getAsJsonObject().get("id").getAsString());
        assertEquals(
                "{}",
                repeated.get(6)
                        .getAsJsonObject()
                        .getAsJsonObject("function")
                        .get("arguments")
                        .getAsString());
    }

    @Test
    public void refusesWrongToolsAndMalformedCallsWithoutEchoingThem() {
        assertEquals(
                "Invalid model response (tool name)",
                refusal(calls(call("a", "replace_source", "{\"content\":\"argument-marker\"}"))));
        assertEquals(
                "Invalid model response (tool name)",
                refusal(calls(call("a", "run_shell", "{\"command\":\"argument-marker\"}"))));
        assertEquals(
                "Invalid model response (tool arguments json)",
                refusal(calls(call("a", "write_file", "{\"path\":\"argument-marker"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "read_file",
                                        "{\"path\":\"x\",\"extra\":\"argument-marker\"}"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "edit_file", "{\"path\":\"argument-marker\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "delete_file", "{\"path\":7}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "read_file", "{\"path\":\"x\",\"limit\":5000}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "read_file", "{\"path\":\"x\",\"offset\":0}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "read_file",
                                        "{\"path\":{\"nested\":\"argument-marker\"}}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "read_file", "{\"path\":\"\"}"))));
        assertEquals(
                "Invalid model response (tool id)",
                refusal(calls(call("a", "list_files", "{}"), call("a", "build", "{}"))));
        assertEquals(
                "Invalid model response (tool id)",
                refusal(calls(call("bad id!", "list_files", "{}"))));
        JsonObject notFunction = call("a", "list_files", "{}");
        notFunction.addProperty("type", "code");
        assertEquals("Invalid model response (tool type)", refusal(calls(notFunction)));
    }

    @Test
    public void parsesAMakeAppIconCallAndRefusesAMissingOrOversizedDescription() throws Exception {
        ModelClient.StepReply parsed =
                send(calls(call("a", "make_app_icon", "{\"description\":\"A green leaf\"}")));
        assertEquals("make_app_icon", parsed.calls.get(0).name);
        assertEquals("A green leaf", parsed.calls.get(0).description);
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "make_app_icon", "{}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "make_app_icon", "{\"description\":\"\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "make_app_icon",
                                        "{\"description\":\""
                                                + "x".repeat(RunTools.MAX_DESCRIPTION_CHARS + 1)
                                                + "\"}"))));
        JsonObject tool = null;
        for (com.google.gson.JsonElement offered : RunTools.schemas())
            if (offered.getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name")
                    .getAsString()
                    .equals("make_app_icon")) tool = offered.getAsJsonObject();
        String description = tool.getAsJsonObject("function").get("description").getAsString();
        assertTrue(description, description.contains("only when the person asks"));
    }

    @Test
    public void parsesALookupCodeCallAndRefusesAMissingEmptyOrOversizedSymbol() throws Exception {
        ModelClient.StepReply parsed =
                send(
                        calls(
                                call("a", "lookup_code", "{\"symbol\":\"Paint#setFlags\"}"),
                                call(
                                        "b",
                                        "lookup_code",
                                        "{\"symbol\":\"android.view.View\",\"offset\":151}")));
        assertEquals("Paint#setFlags", parsed.calls.get(0).symbol);
        assertEquals(1, parsed.calls.get(0).offset);
        assertEquals(151, parsed.calls.get(1).offset);
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "lookup_code", "{}"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "lookup_code", "{\"symbol\":\"Paint\",\"path\":\"x\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "lookup_code", "{\"symbol\":\"\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "lookup_code", "{\"symbol\":\"Paint\",\"offset\":0}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "lookup_code",
                                        "{\"symbol\":\""
                                                + "P".repeat(RunTools.MAX_QUERY_CHARS + 1)
                                                + "\"}"))));
        JsonObject parameters = null;
        for (com.google.gson.JsonElement offered : RunTools.schemas())
            if (offered.getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name")
                    .getAsString()
                    .equals("lookup_code"))
                parameters =
                        offered.getAsJsonObject()
                                .getAsJsonObject("function")
                                .getAsJsonObject("parameters");
        assertEquals(JsonParser.parseString("[\"symbol\"]"), parameters.get("required"));
        assertEquals(
                java.util.Set.of("symbol", "offset"),
                parameters.getAsJsonObject("properties").keySet());
    }

    @Test
    public void parsesAMakeImageCallWithItsShapeAndRefusesAnUnknownOrMissingShape()
            throws Exception {
        ModelClient.StepReply parsed =
                send(
                        calls(
                                call(
                                        "a",
                                        "make_image",
                                        "{\"name\":\"beach\",\"description\":\"A beach\","
                                                + "\"shape\":\"wide\"}"),
                                call(
                                        "b",
                                        "make_image",
                                        "{\"name\":\"door\",\"description\":\"A door\","
                                                + "\"shape\":\"tall\"}"),
                                call(
                                        "c",
                                        "make_image",
                                        "{\"name\":\"coin\",\"description\":\"A coin\","
                                                + "\"shape\":\"square\"}")));
        assertEquals("beach", parsed.calls.get(0).nameArgument);
        assertEquals("A beach", parsed.calls.get(0).description);
        assertEquals(ImageClient.Shape.WIDE, parsed.calls.get(0).shape);
        assertEquals(ImageClient.Shape.TALL, parsed.calls.get(1).shape);
        assertEquals(ImageClient.Shape.SQUARE, parsed.calls.get(2).shape);
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "make_image",
                                        "{\"name\":\"beach\",\"description\":\"A beach\","
                                                + "\"shape\":\"round\"}"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(
                        calls(
                                call(
                                        "a",
                                        "make_image",
                                        "{\"name\":\"beach\",\"description\":\"A beach\"}"))));
        JsonObject tool = null;
        for (com.google.gson.JsonElement offered : RunTools.schemas())
            if (offered.getAsJsonObject()
                    .getAsJsonObject("function")
                    .get("name")
                    .getAsString()
                    .equals("make_image")) tool = offered.getAsJsonObject();
        JsonObject function = tool.getAsJsonObject("function");
        String description = function.get("description").getAsString();
        assertTrue(description, description.contains("only when the person asked for pictures"));
        assertEquals(
                JsonParser.parseString("[\"name\",\"description\",\"shape\"]"),
                function.getAsJsonObject("parameters").get("required"));
    }

    @Test
    public void aReplyWithHundredsOfCallsIsKeptWhole() throws Exception {
        JsonObject[] many = new JsonObject[300];
        for (int index = 0; index < many.length; index++)
            many[index] = call("c" + index, "list_files", "{}");
        ModelClient.StepReply parsed = send(calls(many));
        assertEquals(300, parsed.calls.size());
        assertEquals(17, parsed.totalTokens);
        assertEquals(300, parsed.assistantMessage().getAsJsonArray("tool_calls").size());
    }

    @Test
    public void aFileFarLargerThanOneMebibyteIsWrittenInOneCall() throws Exception {
        String content = "class Big {}\n".repeat(3 * 1024 * 1024 / 13);
        JsonObject arguments = new JsonObject();
        arguments.addProperty("path", "java/Big.java");
        arguments.addProperty("content", content);
        ModelClient.StepReply parsed = send(calls(call("big", "write_file", arguments.toString())));
        assertEquals(content, parsed.calls.get(0).content);
    }

    @Test
    public void aReplyCutOffAtTheModelsOutputLimitIsMarkedAndNothingInItIsUsed() throws Exception {
        JsonArray partial = new JsonArray();
        partial.add(call("cut", "write_file", "{\"path\":\"java/A.java\",\"content\":\"cla"));
        JsonObject cut = reply("length", partial, "argument-marker");
        cut.getAsJsonObject("usage").addProperty("cost", 0.25);
        ModelClient.StepReply reply = send(cut);
        assertTrue(reply.cutOff);
        assertTrue(reply.calls.isEmpty());
        assertEquals(17, reply.totalTokens);
        assertEquals(0, new java.math.BigDecimal("0.25").compareTo(reply.costUsd));
        assertFalse(send(reply("stop", null, "Done.")).cutOff);
    }

    private static ModelClient.StepReply summary(JsonObject reply) throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(reply));
        return new ModelClient(url -> connection, ModelChoice.DEFAULT)
                .completeSummary(KEY, conversation(), 0, new ModelClient.Cancellation());
    }

    @Test
    public void aSummaryCutOffAtTheOutputLimitKeepsTheWordsItWrote() throws Exception {
        assertEquals(
                "The person asked for a timer.",
                summary(reply("length", null, "The person asked for a timer.")).text);
        IOException empty =
                assertThrows(IOException.class, () -> summary(reply("length", null, null)));
        assertEquals("Invalid model response (output limit)", empty.getMessage());
    }

    @Test
    public void refusesBadFinishReasonsAndUsage() {
        JsonArray one = new JsonArray();
        one.add(call("a", "list_files", "{}"));
        assertEquals(
                "Invalid model response (tool call with finish stop)",
                refusal(reply("stop", one, null)));
        assertEquals(
                "Invalid model response (no tool call, finish stop)",
                refusal(reply("stop", null, "  ")));
        assertEquals(
                "Invalid model response (no tool call, finish content_filter)",
                refusal(reply("content_filter", null, "argument-marker")));
        JsonObject noUsage = reply("stop", null, "Done");
        noUsage.remove("usage");
        assertEquals("Invalid model response (usage)", refusal(noUsage));
        JsonObject tooLong = reply("stop", null, "Done");
        tooLong.getAsJsonObject("usage")
                .addProperty("completion_tokens", UsageBudget.MAX_COMPLETION_TOKENS + 1);
        tooLong.getAsJsonObject("usage")
                .addProperty("total_tokens", UsageBudget.MAX_COMPLETION_TOKENS + 13);
        assertEquals("Invalid model response (usage)", refusal(tooLong));
        JsonObject negative = reply("stop", null, "Done");
        negative.getAsJsonObject("usage").addProperty("cost", -0.01);
        assertEquals("Invalid model response (usage)", refusal(negative));
        JsonObject otherModel = reply("stop", null, "Done");
        otherModel.addProperty("model", "someone/else");
        assertEquals("Invalid model response (envelope)", refusal(otherModel));
    }

    @Test
    public void refusesOversizedRequestsBeforeSendingAndOversizedReplies() throws Exception {
        int[] opened = {0};
        ModelProtocolTest.FakeConnection unused =
                new ModelProtocolTest.FakeConnection(200, ModelProtocolTest.VALID);
        JsonArray huge = conversation();
        JsonObject big = new JsonObject();
        big.addProperty("role", "tool");
        big.addProperty("content", "x".repeat(ModelClient.MAX_STEP_REQUEST_BYTES));
        huge.add(big);
        IOException tooLarge =
                assertThrows(
                        IOException.class,
                        () ->
                                new ModelClient(
                                                url -> {
                                                    opened[0]++;
                                                    return unused;
                                                },
                                                ModelChoice.DEFAULT)
                                        .completeStep(
                                                KEY, huge, 0, new ModelClient.Cancellation()));
        assertEquals("Model request exceeds limit", tooLarge.getMessage());
        assertEquals(0, opened[0]);
        JsonObject oversized =
                reply("stop", null, "argument-marker" + "y".repeat(ModelClient.MAX_RESPONSE_BYTES));
        assertEquals("Model response exceeds limit", refusal(oversized));
    }

    @Test
    public void parsesBriefAndPlanCallsAndOffersTheirSchemas() throws Exception {
        ModelClient.StepReply parsed =
                send(
                        calls(
                                call("a", "write_brief", "{\"text\":\"For: me.\\n- Dark\"}"),
                                call(
                                        "b",
                                        "update_plan",
                                        "{\"steps\":[{\"text\":\"Make it\",\"status\":\"doing\"}]}")));
        assertEquals("For: me.\n- Dark", parsed.calls.get(0).brief);
        assertEquals("update_plan", parsed.calls.get(1).name);
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "update_plan", "{}"))));
        assertEquals(
                "Invalid model response (tool arguments fields)",
                refusal(calls(call("a", "write_brief", "{\"text\":\"x\",\"title\":\"y\"}"))));
        assertEquals(
                "Invalid model response (tool arguments values)",
                refusal(calls(call("a", "write_brief", "{\"text\":{\"a\":1}}"))));

        JsonObject brief = null;
        JsonObject plan = null;
        for (var tool : RunTools.schemas()) {
            JsonObject function = tool.getAsJsonObject().getAsJsonObject("function");
            String name = function.get("name").getAsString();
            if (name.equals("write_brief")) brief = function.getAsJsonObject("parameters");
            if (name.equals("update_plan")) plan = function.getAsJsonObject("parameters");
        }
        assertEquals(JsonParser.parseString("[\"text\"]"), brief.get("required"));
        JsonObject steps = plan.getAsJsonObject("properties").getAsJsonObject("steps");
        assertEquals("array", steps.get("type").getAsString());
        assertEquals(1, steps.get("minItems").getAsInt());
        assertEquals(RunPlan.MAX_STEPS, steps.get("maxItems").getAsInt());
        JsonObject item = steps.getAsJsonObject("items");
        assertFalse(item.get("additionalProperties").getAsBoolean());
        assertEquals(JsonParser.parseString("[\"text\",\"status\"]"), item.get("required"));
        assertEquals(
                JsonParser.parseString("[\"todo\",\"doing\",\"done\"]"),
                item.getAsJsonObject("properties").getAsJsonObject("status").get("enum"));
        assertEquals(
                RunPlan.MAX_STEP_CHARS,
                item.getAsJsonObject("properties")
                        .getAsJsonObject("text")
                        .get("maxLength")
                        .getAsInt());
    }
}
