package dev.smallwright.agent;

import static org.junit.Assert.*;

import org.junit.Test;

public class ModelProviderTest {
    @Test
    public void openRouterIsItsOwnProvider() {
        assertTrue(ModelProvider.OPENROUTER.isOpenRouter());
        assertEquals("https://openrouter.ai/api/v1", ModelProvider.OPENROUTER.baseUrl);
        assertEquals("OpenRouter", ModelProvider.OPENROUTER.label);
    }

    @Test
    public void aServerAddressIsNormalized() {
        assertEquals(
                "https://api.openai.com/v1",
                ModelProvider.openAiCompatible("  HTTPS://API.OpenAI.com:443/v1/ ").baseUrl);
        assertEquals(
                "https://api.groq.com/openai/v1",
                ModelProvider.openAiCompatible("https://api.groq.com/openai/v1").baseUrl);
        assertEquals(
                "https://models.example.com:8443",
                ModelProvider.openAiCompatible("https://models.example.com:8443/").baseUrl);
        assertEquals(
                "https://api.example.com/v1",
                ModelProvider.openAiCompatible("https://api.example.com/x/../v1").baseUrl);
    }

    @Test
    public void aServerIsNeverOpenRouterEvenAtOpenRoutersAddress() {
        ModelProvider server = ModelProvider.openAiCompatible("https://openrouter.ai/api/v1");
        assertFalse(server.isOpenRouter());
        assertEquals("Model server", server.label);
        assertNotEquals(ModelProvider.OPENROUTER, server);
        assertEquals(server, ModelProvider.openAiCompatible("https://openrouter.ai/api/v1/"));
    }

    @Test
    public void refusesAddressesThatAreNotPlainHttpsBaseUrls() {
        String[] refused = {
            "",
            "   ",
            "http://api.openai.com/v1",
            "ftp://api.openai.com/v1",
            "api.openai.com/v1",
            "https://user:secret@api.openai.com/v1",
            "https://api.openai.com/v1?key=secret",
            "https://api.openai.com/v1#models",
            "https:///v1",
            "https://api.open ai.com/v1",
            "https://api.öpenai.com/v1",
            "https://api.openai.com/../v1",
            "https://api.openai.com/v\u00011",
            "https://api.openai.com/" + "v".repeat(400),
        };
        for (String address : refused)
            assertThrows(
                    address,
                    IllegalArgumentException.class,
                    () -> ModelProvider.openAiCompatible(address));
    }

    @Test
    public void aRefusalNeverRepeatsTheAddress() {
        IllegalArgumentException refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ModelProvider.openAiCompatible(
                                        "https://user:secret@api.openai.com/v1"));
        assertFalse(refused.getMessage().contains("secret"));
    }
}
