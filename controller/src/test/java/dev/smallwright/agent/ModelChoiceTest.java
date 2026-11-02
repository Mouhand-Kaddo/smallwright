package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Offline simulated OpenRouter responses for a model the person picked. */
public class ModelChoiceTest {
    private static final ModelChoice SONNET =
            new ModelChoice(
                    "anthropic/claude-sonnet-5.5",
                    "anthropic/claude-sonnet-5.5-20260901",
                    "Anthropic: Claude Sonnet 5.5",
                    new BigDecimal("3"),
                    new BigDecimal("15"),
                    131072);

    private static String reply(String model, String cost) {
        return "{\"model\":\""
                + model
                + "\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Hello\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5,"
                + "\"total_tokens\":17,\"cost\":"
                + cost
                + "}}";
    }

    private static JsonObject send(ModelChoice model, String response) throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(200, SseBody.of(response));
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "preview");
        messages.add(user);
        new ModelClient(url -> connection, model)
                .completeStep(
                        "dummy-key".getBytes(StandardCharsets.US_ASCII),
                        messages,
                        0,
                        new ModelClient.Cancellation());
        return JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    public void requestsThePickedModelWithoutAnyProviderRouting() throws Exception {
        JsonObject body = send(SONNET, reply("anthropic/claude-sonnet-5.5", "0.01"));
        assertEquals("anthropic/claude-sonnet-5.5", body.get("model").getAsString());
        assertFalse(body.has("provider"));
        assertFalse(body.has("parallel_tool_calls"));
    }

    @Test
    public void aFreeModelRequestHasNoPriceCapOrParameterRequirement() throws Exception {
        ModelChoice free =
                new ModelChoice(
                        "vendor/small:free",
                        "vendor/small-20260901",
                        "Small",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        131072);
        JsonObject body = send(free, reply("vendor/small-20260901", "0"));
        assertFalse(body.toString().contains("max_price"));
        assertFalse(body.toString().contains("require_parameters"));
        assertFalse(body.has("parallel_tool_calls"));
    }

    @Test
    public void defaultModelKeepsItsPinnedProviders() throws Exception {
        JsonObject body = send(ModelChoice.DEFAULT, reply(ModelClient.MODEL, "0"));
        assertEquals(ModelClient.MODEL, body.get("model").getAsString());
        assertEquals(
                JsonParser.parseString("{\"only\":[\"deepinfra\",\"together\",\"nebius\"]}"),
                body.get("provider"));
    }

    @Test
    public void rejectsAReplyFromAnotherModel() {
        assertThrows(
                java.io.IOException.class, () -> send(SONNET, reply(ModelClient.MODEL, "0.01")));
        assertThrows(
                java.io.IOException.class,
                () -> send(SONNET, reply("anthropic/claude-sonnet-5.5-evil", "0.01")));
    }

    @Test
    public void acceptsOnlyTheIdAndItsListedVersionedName() throws Exception {
        assertTrue(SONNET.acceptsReplyModel("anthropic/claude-sonnet-5.5"));
        assertTrue(SONNET.acceptsReplyModel("anthropic/claude-sonnet-5.5-20260901"));
        assertFalse(SONNET.acceptsReplyModel("anthropic/claude-sonnet-5.5-20261017"));
        assertFalse(SONNET.acceptsReplyModel(null));
        send(SONNET, reply("anthropic/claude-sonnet-5.5-20260901", "0.01"));
    }

    @Test
    public void aPaidReplyMayCostMoreThanAFullRequestAtTheListedPrice() throws Exception {
        send(SONNET, reply("anthropic/claude-sonnet-5.5", "50"));
    }

    @Test
    public void aFreeModelMayReportNoCost() throws Exception {
        ModelChoice free =
                new ModelChoice(
                        "vendor/small:free",
                        "vendor/small-20260901",
                        "Small",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        131072);
        send(free, reply("vendor/small-20260901", "0"));
        assertThrows(
                java.io.IOException.class, () -> send(free, reply("vendor/small:free", "0.01")));
    }

    @Test
    public void rejectsInvalidChoices() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ModelChoice(
                                "bad id", "a/b", "Name", BigDecimal.ONE, BigDecimal.ONE, 131072));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ModelChoice(
                                "a/b", "a/b", "Name\n", BigDecimal.ONE, BigDecimal.ONE, 131072));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ModelChoice(
                                "a/b",
                                "a/b",
                                "Name",
                                new BigDecimal("-1"),
                                BigDecimal.ONE,
                                131072));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelChoice("a/b", "a/b", "Name", BigDecimal.ONE, null, 131072));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ModelChoice(
                                "a/b",
                                "a/b",
                                "Name",
                                new BigDecimal("1e-999999"),
                                BigDecimal.ONE,
                                131072));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelChoice("a/b", "a/b", "Name", BigDecimal.ONE, BigDecimal.ONE, 0));
    }

    @Test
    public void aServerModelHasNoPriceAndIsNotAnOpenRouterModel() {
        ModelProvider server = ModelProvider.openAiCompatible("https://api.example.com/v1");
        ModelChoice served = ModelChoice.atServer(server, "deepseek/deepseek-v4.1-flash", 131072);
        assertEquals(server, served.provider);
        assertEquals("", served.priceLabel());
        assertFalse(served.isFree());
        assertFalse(served.reasoning);
        assertFalse(served.readsImages);
        assertEquals(0, served.maxCompletionTokens);
        assertEquals("deepseek/deepseek-v4.1-flash", served.nameMarkedFree());
        assertNotEquals(ModelChoice.DEFAULT, served);
        assertEquals(ModelProvider.OPENROUTER, ModelChoice.DEFAULT.provider);
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelChoice.atServer(ModelProvider.OPENROUTER, "a/b", 131072));
        assertThrows(
                IllegalArgumentException.class, () -> ModelChoice.atServer(server, "bad id", 1));
        assertThrows(IllegalArgumentException.class, () -> ModelChoice.atServer(server, "a/b", 0));
    }

    @Test
    public void aServerModelAcceptsADatedVariantOfItsName() {
        ModelChoice served =
                ModelChoice.atServer(
                        ModelProvider.openAiCompatible("https://api.example.com/v1"),
                        "gpt-5.5",
                        131072);
        assertTrue(served.acceptsReplyModel("gpt-5.5"));
        assertTrue(served.acceptsReplyModel("gpt-5.5-2026-09-01"));
        assertFalse(served.acceptsReplyModel("gpt-5.5o"));
        assertFalse(served.acceptsReplyModel("gpt-5"));
        assertFalse(served.acceptsReplyModel(null));
        assertFalse(SONNET.acceptsReplyModel("anthropic/claude-sonnet-5.5-20991231"));
    }

    private static ModelChoice priced(String id, String name, String in, String out) {
        return new ModelChoice(id, id, name, new BigDecimal(in), new BigDecimal(out), 131072);
    }

    @Test
    public void shortensTheNameOnlyWhenItStartsWithTheMaker() {
        assertEquals("Claude Sonnet 5.5", SONNET.shortName());
        assertEquals("Anthropic", SONNET.maker());
        assertEquals("DeepSeek V4.1 Flash", ModelChoice.DEFAULT.shortName());
        assertEquals("DeepSeek", ModelChoice.DEFAULT.maker());
        ModelChoice qwen = priced("qwen/max", "Qwen: Qwen3.8 Max (0902)", "2", "6");
        assertEquals("Qwen3.8 Max (0902)", qwen.shortName());
        assertEquals("Qwen", qwen.maker());
        ModelChoice ratio = priced("a/b", "Ratio 3:1 Model", "1", "1");
        assertEquals("Ratio 3:1 Model", ratio.shortName());
        assertEquals("", ratio.maker());
        ModelChoice bare = priced("a/c", "Maker: ", "1", "1");
        assertEquals("Maker:", bare.shortName());
        assertEquals("", bare.maker());
    }

    @Test
    public void aFreeModelDropsItsFreeMarkButAPaidOneKeepsItsName() {
        assertEquals(
                "Laguna S 2.1",
                priced("p/l:free", "Poolside: Laguna S 2.1 (free)", "0", "0").shortName());
        assertEquals(
                "Gemma 4 (Free)", priced("g/g", "Google: Gemma 4 (Free)", "1", "1").shortName());
        assertEquals("(free)", priced("x/y:free", "(free)", "0", "0").shortName());
    }

    @Test
    public void aFreeModelSaysSoInTheNameThatTellsItFromItsPaidTwin() {
        ModelChoice paid =
                priced(
                        "nvidia/nemotron-3.5-super-vl",
                        "NVIDIA: Nemotron 3.5 Super VL",
                        "0.1",
                        "0.4");
        ModelChoice free =
                priced(
                        "nvidia/nemotron-3.5-super-vl:free",
                        "NVIDIA: Nemotron 3.5 Super VL (free)",
                        "0",
                        "0");
        assertEquals("Nemotron 3.5 Super VL", paid.nameMarkedFree());
        assertEquals("Nemotron 3.5 Super VL (free)", free.nameMarkedFree());
        assertEquals("Y (free)", priced("x/y:free", "X: Y", "0", "0").nameMarkedFree());
    }

    @Test
    public void showsTheListedPricePerMillionTokens() {
        assertEquals("free", priced("a/free", "Free", "0", "0").priceLabel());
        assertEquals("$3.00 in, $15.00 out per 1M tokens", SONNET.priceLabel());
        assertEquals("$0.30 in, $1.20 out per 1M tokens", ModelChoice.DEFAULT.priceLabel());
        assertEquals(
                "$0.035 in, $0.011 out per 1M tokens",
                priced(ModelClient.MODEL, "DeepSeek", "0.0352", "0.0112").priceLabel());
        assertEquals(
                "$0.00 in, $0.50 out per 1M tokens",
                priced("a/out", "Out only", "0", "0.5").priceLabel());
        assertEquals(
                "$1.25 in, $1.00 out per 1M tokens",
                priced("a/round", "Round", "1.249", "0.9951").priceLabel());
        assertEquals(
                "$0.000012 in, $0.08 out per 1M tokens",
                priced("a/tiny", "Tiny", "0.0000123", "0.08").priceLabel());
    }

    @Test
    public void estimatesOneTypicalAppAtTheListedPriceToOrderTheList() {
        assertEquals(
                0,
                new BigDecimal(
                                ModelChoice.TYPICAL_PROMPT_TOKENS * 3
                                        + ModelChoice.TYPICAL_COMPLETION_TOKENS * 15)
                        .movePointLeft(6)
                        .compareTo(SONNET.appCostUsd()));
    }

    @Test
    public void knowsWhetherTheLevelOfThinkingCanBeSet() {
        assertTrue(ModelChoice.DEFAULT.reasoning);
        assertFalse(SONNET.reasoning);
        assertTrue(
                new ModelChoice(
                                "a/think",
                                "a/think",
                                "Thinker",
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                131072,
                                true,
                                false,
                                0)
                        .reasoning);
    }

    private static JsonObject reasoning(ReasoningLevel level) throws Exception {
        ModelProtocolTest.FakeConnection connection =
                new ModelProtocolTest.FakeConnection(
                        200, SseBody.of(reply(ModelClient.MODEL, "0")));
        new ModelClient(url -> connection, ModelChoice.DEFAULT, level)
                .completeStep(
                        "dummy-key".getBytes(StandardCharsets.US_ASCII),
                        messages(),
                        0,
                        new ModelClient.Cancellation());
        return JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("reasoning");
    }

    private static com.google.gson.JsonArray messages() {
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "preview");
        messages.add(user);
        return messages;
    }

    @Test
    public void sendsTheThinkingLevelAsTheReasoningEffort() throws Exception {
        assertEquals("none", reasoning(ReasoningLevel.OFF).get("effort").getAsString());
        assertEquals("low", reasoning(ReasoningLevel.LOW).get("effort").getAsString());
        assertEquals("medium", reasoning(ReasoningLevel.MEDIUM).get("effort").getAsString());
        assertEquals("high", reasoning(ReasoningLevel.HIGH).get("effort").getAsString());
        assertEquals(
                "low",
                send(ModelChoice.DEFAULT, reply(ModelClient.MODEL, "0"))
                        .getAsJsonObject("reasoning")
                        .get("effort")
                        .getAsString());
    }

    @Test
    public void everyRequestOfARunKeepsTheLevelTakenAtTheTap() throws Exception {
        java.util.List<ModelProtocolTest.FakeConnection> opened = new java.util.ArrayList<>();
        ModelClient client =
                new ModelClient(
                        url -> {
                            ModelProtocolTest.FakeConnection connection =
                                    new ModelProtocolTest.FakeConnection(
                                            200, SseBody.of(reply(ModelClient.MODEL, "0")));
                            opened.add(connection);
                            return connection;
                        },
                        ModelChoice.DEFAULT,
                        ReasoningLevel.HIGH);
        byte[] key = "dummy-key".getBytes(StandardCharsets.US_ASCII);
        client.completeStep(key, messages(), 0, new ModelClient.Cancellation());
        client.completeSummary(key, messages(), 0, new ModelClient.Cancellation());
        assertEquals(2, opened.size());
        for (ModelProtocolTest.FakeConnection connection : opened)
            assertEquals(
                    "high",
                    JsonParser.parseString(connection.sent.toString(StandardCharsets.UTF_8))
                            .getAsJsonObject()
                            .getAsJsonObject("reasoning")
                            .get("effort")
                            .getAsString());
    }

    @Test
    public void aModelWithoutTheThinkingControlGetsTheDefaultLevel() throws Exception {
        ModelProtocolTest.FakeConnection[] sent = new ModelProtocolTest.FakeConnection[1];
        ModelClient client =
                new ModelClient(
                        url ->
                                sent[0] =
                                        new ModelProtocolTest.FakeConnection(
                                                200, SseBody.of(reply(SONNET.id, "0"))),
                        SONNET,
                        ReasoningLevel.HIGH);
        client.completeStep(
                "dummy-key".getBytes(StandardCharsets.US_ASCII),
                messages(),
                0,
                new ModelClient.Cancellation());
        assertEquals(
                ReasoningLevel.DEFAULT.effort,
                JsonParser.parseString(sent[0].sent.toString(StandardCharsets.UTF_8))
                        .getAsJsonObject()
                        .getAsJsonObject("reasoning")
                        .get("effort")
                        .getAsString());
    }

    private static final ModelChoice HAIKU =
            new ModelChoice(
                    "anthropic/claude-haiku-5.5",
                    "anthropic/claude-haiku-5.5",
                    "Anthropic: Claude Haiku 5.5",
                    BigDecimal.ONE,
                    new BigDecimal("5"),
                    1_000_000,
                    true,
                    true,
                    128_000);

    private static JsonObject[] stepAndSummary(ModelChoice model, int promptTokens)
            throws Exception {
        java.util.List<ModelProtocolTest.FakeConnection> opened = new java.util.ArrayList<>();
        ModelClient client =
                new ModelClient(
                        url -> {
                            ModelProtocolTest.FakeConnection connection =
                                    new ModelProtocolTest.FakeConnection(
                                            200, SseBody.of(reply(model.id, "0")));
                            opened.add(connection);
                            return connection;
                        },
                        model);
        byte[] key = "dummy-key".getBytes(StandardCharsets.US_ASCII);
        client.completeStep(key, messages(), promptTokens, new ModelClient.Cancellation());
        client.completeSummary(key, messages(), promptTokens, new ModelClient.Cancellation());
        JsonObject[] bodies = new JsonObject[2];
        for (int index = 0; index < 2; index++)
            bodies[index] =
                    JsonParser.parseString(opened.get(index).sent.toString(StandardCharsets.UTF_8))
                            .getAsJsonObject();
        return bodies;
    }

    @Test
    public void everyRequestAsksForAsMuchAsTheModelsListedCeiling() throws Exception {
        for (JsonObject body : stepAndSummary(HAIKU, 5_000))
            assertEquals(128_000, body.get("max_tokens").getAsInt());
    }

    @Test
    public void aRequestNeverAsksForMoreThanTheContextLeavesAfterItsPrompt() throws Exception {
        ModelChoice deepSeek =
                new ModelChoice(
                        "deepseek/deepseek-v4.1-flash",
                        "deepseek/deepseek-v4.1-flash",
                        "DeepSeek: DeepSeek V4.1 Flash",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        1_048_576,
                        true,
                        true,
                        943_718);
        for (JsonObject body : stepAndSummary(deepSeek, 300_000))
            assertEquals(748_576, body.get("max_tokens").getAsInt());
    }

    @Test
    public void aModelListedWithoutACeilingIsSentNone() throws Exception {
        for (JsonObject body : stepAndSummary(SONNET, 5_000)) assertFalse(body.has("max_tokens"));
    }

    @Test
    public void theBuiltInDefaultKnowsItsListedCeiling() {
        assertEquals(943_718, ModelChoice.DEFAULT.maxCompletionTokens);
    }

    @Test
    public void searchMatchesTheNameAndTheId() {
        assertTrue(SONNET.matches("sonnet"));
        assertTrue(SONNET.matches("ANTHROPIC/"));
        assertTrue(SONNET.matches("claude-sonnet-5.5"));
        assertTrue(SONNET.matches("  "));
        assertFalse(SONNET.matches("deepseek"));
    }
}
