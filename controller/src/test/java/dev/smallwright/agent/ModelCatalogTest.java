package dev.smallwright.agent;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;

/** Offline OpenRouter model-list samples shaped like the public /api/v1/models reply. */
public class ModelCatalogTest {
    private static String model(String id, String params, String pricing) {
        return "{\"id\":\""
                + id
                + "\",\"canonical_slug\":\""
                + id.replace(":free", "")
                + "-20260901\",\"name\":\"Vendor: "
                + id
                + "\",\"architecture\":{\"input_modalities\":[\"text\"],"
                + "\"output_modalities\":[\"text\"]},\"pricing\":"
                + pricing
                + ",\"supported_parameters\":["
                + params
                + "]}";
    }

    private static final String TOOLS = "\"max_tokens\",\"tools\",\"tool_choice\"";
    private static final String PRICE = "{\"prompt\":\"0.000003\",\"completion\":\"0.000015\"}";

    @Test
    public void keepsChatModelsThatCanBeMadeToCallTheBuilderTool() throws Exception {
        String json =
                "{\"data\":["
                        + model("anthropic/claude-sonnet-5.5", TOOLS, PRICE)
                        + ","
                        + model("vendor/no-tools", "\"max_tokens\"", PRICE)
                        + ","
                        + model("vendor/no-choice", "\"tools\"", PRICE)
                        + ","
                        + model("vendor/batch:batch", TOOLS, PRICE)
                        + ","
                        + model(
                                "openrouter/auto",
                                TOOLS,
                                "{\"prompt\":\"-1\",\"completion\":\"-1\"}")
                        + ","
                        + model(
                                "vendor/small:free",
                                TOOLS,
                                "{\"prompt\":\"0\",\"completion\":\"0\"}")
                        + "]}";
        List<ModelChoice> models = ModelCatalog.parse(json);
        assertEquals(2, models.size());
        ModelChoice sonnet = models.get(0);
        assertEquals("anthropic/claude-sonnet-5.5", sonnet.id);
        assertEquals("anthropic/claude-sonnet-5.5-20260901", sonnet.canonicalId);
        assertEquals("Vendor: anthropic/claude-sonnet-5.5", sonnet.name);
        assertEquals(0, new BigDecimal("3").compareTo(sonnet.promptUsdPerMillion));
        assertEquals(0, new BigDecimal("15").compareTo(sonnet.completionUsdPerMillion));
        assertTrue(models.get(1).isFree());
    }

    @Test
    public void knowsWhichModelsReadImagesFromTheirListedInputs() throws Exception {
        String text = "\"input_modalities\":[\"text\"]";
        String json =
                "{\"data\":["
                        + model("vendor/sees", TOOLS, PRICE)
                                .replace(text, "\"input_modalities\":[\"text\",\"image\"]")
                        + ","
                        + model("vendor/reads", TOOLS, PRICE)
                        + ","
                        + model("vendor/unlisted", TOOLS, PRICE).replace(text + ",", "")
                        + ","
                        + model("vendor/odd", TOOLS, PRICE)
                                .replace(text, "\"input_modalities\":\"image\"")
                        + "]}";
        List<ModelChoice> models = ModelCatalog.parse(json);
        assertEquals(4, models.size());
        assertTrue(models.get(0).readsImages);
        assertFalse(models.get(1).readsImages);
        assertFalse(models.get(2).readsImages);
        assertFalse(models.get(3).readsImages);
    }

    @Test
    public void theBuiltInDefaultReadsImagesWithoutTheList() {
        assertTrue(ModelChoice.DEFAULT.readsImages);
    }

    @Test
    public void leavesOutAliasesBecauseTheyAnswerUnderAnotherName() throws Exception {
        String alias =
                model("~vendor/latest", TOOLS, PRICE)
                        .replace(
                                "\"pricing\"",
                                "\"alias_target\":{\"slug\":\"vendor/x-1\"},\"pricing\"");
        String json =
                "{\"data\":["
                        + alias
                        + ","
                        + model("~vendor/unmarked", TOOLS, PRICE)
                        + ","
                        + model("vendor/x-1", TOOLS, PRICE)
                        + "]}";
        List<ModelChoice> models = ModelCatalog.parse(json);
        assertEquals(1, models.size());
        assertEquals("vendor/x-1", models.get(0).id);
    }

    @Test
    public void keepsTheFirstOfRepeatedIds() throws Exception {
        String json =
                "{\"data\":["
                        + model("vendor/x", TOOLS, PRICE)
                        + ","
                        + model("vendor/x", TOOLS, "{\"prompt\":\"0\",\"completion\":\"0\"}")
                        + "]}";
        List<ModelChoice> models = ModelCatalog.parse(json);
        assertEquals(1, models.size());
        assertFalse(models.get(0).isFree());
    }

    @Test
    public void usesTheHighestTimeOfDayPrice() throws Exception {
        String pricing =
                "{\"prompt\":\"0.00000015\",\"completion\":\"0.0000006\",\"overrides\":["
                        + "{\"utc_days\":[\"monday\"],\"prompt\":\"0.0000003\",\"completion\":\"0.0000012\"},"
                        + "{\"utc_days\":[\"sunday\"],\"prompt\":\"0.0000001\"}]}";
        ModelChoice model =
                ModelCatalog.parse("{\"data\":[" + model("deepseek/x", TOOLS, pricing) + "]}")
                        .get(0);
        assertEquals(0, new BigDecimal("0.3").compareTo(model.promptUsdPerMillion));
        assertEquals(0, new BigDecimal("1.2").compareTo(model.completionUsdPerMillion));
    }

    @Test
    public void readsTheContextLengthAndTheTopProvidersSmallerOne() throws Exception {
        String listed =
                model("vendor/listed", TOOLS, PRICE)
                        .replace("\"pricing\"", "\"context_length\":1048576,\"pricing\"");
        String smaller =
                model("vendor/smaller", TOOLS, PRICE)
                        .replace(
                                "\"pricing\"",
                                "\"context_length\":1048576,\"top_provider\":"
                                        + "{\"context_length\":131072},\"pricing\"");
        String unknown = model("vendor/unknown", TOOLS, PRICE);
        String broken =
                model("vendor/broken", TOOLS, PRICE)
                        .replace("\"pricing\"", "\"context_length\":-1,\"pricing\"");
        List<ModelChoice> models =
                ModelCatalog.parse(
                        "{\"data\":["
                                + listed
                                + ","
                                + smaller
                                + ","
                                + unknown
                                + ","
                                + broken
                                + "]}");
        assertEquals(3, models.size());
        assertEquals(1048576, models.get(0).contextTokens);
        assertEquals(131072, models.get(1).contextTokens);
        assertEquals(ModelChoice.UNKNOWN_CONTEXT_TOKENS, models.get(2).contextTokens);
    }

    @Test
    public void readsTheTopProvidersReplyCeilingAndKeepsAModelWithoutOne() throws Exception {
        String listed =
                model("vendor/listed", TOOLS, PRICE)
                        .replace(
                                "\"pricing\"",
                                "\"top_provider\":{\"max_completion_tokens\":128000},\"pricing\"");
        String unlisted =
                model("vendor/unlisted", TOOLS, PRICE)
                        .replace(
                                "\"pricing\"",
                                "\"top_provider\":{\"max_completion_tokens\":null},\"pricing\"");
        String noProvider = model("vendor/no-provider", TOOLS, PRICE);
        String broken =
                model("vendor/broken", TOOLS, PRICE)
                        .replace(
                                "\"pricing\"",
                                "\"top_provider\":{\"max_completion_tokens\":-5},\"pricing\"");
        List<ModelChoice> models =
                ModelCatalog.parse(
                        "{\"data\":["
                                + listed
                                + ","
                                + unlisted
                                + ","
                                + noProvider
                                + ","
                                + broken
                                + "]}");
        assertEquals(4, models.size());
        assertEquals(128000, models.get(0).maxCompletionTokens);
        assertEquals(0, models.get(1).maxCompletionTokens);
        assertEquals(0, models.get(2).maxCompletionTokens);
        assertEquals(0, models.get(3).maxCompletionTokens);
    }

    @Test
    public void marksTheModelsWhoseThinkingLevelCanBeSet() throws Exception {
        List<ModelChoice> models =
                ModelCatalog.parse(
                        "{\"data\":["
                                + model("vendor/thinks", TOOLS + ",\"reasoning\"", PRICE)
                                + ","
                                + model("vendor/plain", TOOLS + ",\"include_reasoning\"", PRICE)
                                + "]}");
        assertTrue(models.get(0).reasoning);
        assertFalse(models.get(1).reasoning);
    }

    @Test
    public void skipsMalformedEntriesAndKeepsTheRest() throws Exception {
        String json =
                "{\"data\":[{\"id\":\"bad id\"},"
                        + model("vendor/no-price", TOOLS, "{}")
                        + ",{\"id\":7},"
                        + model("vendor/good", TOOLS, PRICE)
                        + "]}";
        List<ModelChoice> models = ModelCatalog.parse(json);
        assertEquals(1, models.size());
        assertEquals("vendor/good", models.get(0).id);
    }

    @Test
    public void rejectsAReplyThatIsNotAModelList() {
        for (String bad : new String[] {"", "[]", "{\"data\":{}}", "{\"data\":[]", "nope"}) {
            assertThrows(java.io.IOException.class, () -> ModelCatalog.parse(bad));
        }
    }
}
