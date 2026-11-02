package dev.smallwright.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;

/** An OpenAI-compatible server's model list, shaped like OpenAI's GET /models reply. */
public class ServerCatalogTest {
    private static final ModelProvider SERVER =
            ModelProvider.openAiCompatible("https://api.example.com/v1");
    private static final byte[] KEY = "sk-dummy-test-key".getBytes(StandardCharsets.US_ASCII);

    @Test
    public void listsEveryModelTheServerNamesSortedWithItsContextWhenListed() throws Exception {
        List<ModelChoice> models =
                ModelCatalog.parse(
                        SERVER,
                        "{\"object\":\"list\",\"data\":["
                                + "{\"id\":\"gpt-5.5\",\"object\":\"model\",\"owned_by\":\"x\"},"
                                + "{\"id\":\"deepseek-flash\",\"context_length\":1048576},"
                                + "{\"id\":\"llama-4\",\"context_window\":131072},"
                                + "{\"id\":\"gpt-5.5\"},"
                                + "{\"id\":\"bad id\"},{\"id\":7},{\"name\":\"no id\"},"
                                + "{\"id\":\"huge\",\"context_length\":-5}]}");
        assertEquals(4, models.size());
        assertEquals("deepseek-flash", models.get(0).id);
        assertEquals(1_048_576, models.get(0).contextTokens);
        assertEquals("gpt-5.5", models.get(1).id);
        assertEquals(ModelChoice.UNKNOWN_CONTEXT_TOKENS, models.get(1).contextTokens);
        assertEquals("huge", models.get(2).id);
        assertEquals(ModelChoice.UNKNOWN_CONTEXT_TOKENS, models.get(2).contextTokens);
        assertEquals(131_072, models.get(3).contextTokens);
        for (ModelChoice model : models) {
            assertEquals(SERVER, model.provider);
            assertEquals("", model.priceLabel());
            assertFalse(model.reasoning);
            assertFalse(model.readsImages);
        }
    }

    @Test
    public void rejectsAReplyThatIsNotAModelList() {
        for (String bad : new String[] {"", "[]", "{\"data\":{}}", "{\"data\":[]", "nope"})
            assertThrows(IOException.class, () -> ModelCatalog.parse(SERVER, bad));
    }

    @Test
    public void asksWithTheKeyAndRefusesARedirectOrAnError() throws Exception {
        ModelProtocolTest.FakeConnection listed =
                new ModelProtocolTest.FakeConnection(200, "{\"data\":[{\"id\":\"gpt-5.5\"}]}");
        String[] opened = {null};
        List<ModelChoice> models =
                ModelCatalog.fetch(
                        SERVER,
                        KEY,
                        url -> {
                            opened[0] = url.toString();
                            return listed;
                        });
        assertEquals("https://api.example.com/v1/models", opened[0]);
        assertEquals("Bearer sk-dummy-test-key", listed.getRequestProperty("Authorization"));
        assertFalse(listed.getInstanceFollowRedirects());
        assertEquals("gpt-5.5", models.get(0).id);
        for (int status : new int[] {302, 401, 404, 500}) {
            IOException refused =
                    assertThrows(
                            IOException.class,
                            () ->
                                    ModelCatalog.fetch(
                                            SERVER,
                                            KEY,
                                            url ->
                                                    new ModelProtocolTest.FakeConnection(
                                                            status, "sk-dummy-test-key")));
            assertFalse(refused.getMessage().contains("dummy"));
        }
    }
}
