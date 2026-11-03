package dev.smallwright.agent;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** The OpenRouter transport of a run's requests over fake connections; no provider calls. */
public class ModelProtocolTest {
    static final String VALID =
            "{\"model\":\"deepseek/deepseek-v4.1-flash\",\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5,\"total_tokens\":17,\"cost\":0}}";

    private static final byte[] KEY = "dummy-key".getBytes(StandardCharsets.US_ASCII);

    private static JsonArray conversation() {
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "preview");
        JsonArray messages = new JsonArray();
        messages.add(user);
        return messages;
    }

    private static ModelClient.StepReply send(ModelClient.ConnectionFactory connections)
            throws IOException {
        return new ModelClient(connections, ModelChoice.DEFAULT)
                .completeStep(KEY, conversation(), 0, new ModelClient.Cancellation());
    }

    @Test
    public void sendsTheRoutedBoundedRequestAndRejectsRedirectWithoutLeakingSecrets()
            throws Exception {
        FakeConnection connection = new FakeConnection(200, SseBody.of(VALID));
        assertEquals(
                "Hello",
                send(url -> {
                            assertEquals(
                                    "https://openrouter.ai/api/v1/chat/completions",
                                    url.toString());
                            return connection;
                        })
                        .text);
        String sent = connection.sent.toString(StandardCharsets.UTF_8);
        JsonObject body = JsonParser.parseString(sent).getAsJsonObject();
        assertEquals("deepseek/deepseek-v4.1-flash", body.get("model").getAsString());
        assertEquals(
                JsonParser.parseString("{\"only\":[\"deepinfra\",\"together\",\"nebius\"]}"),
                body.get("provider"));
        assertEquals(
                JsonParser.parseString("{\"effort\":\"low\",\"exclude\":false}"),
                body.get("reasoning"));
        assertTrue(body.get("stream").getAsBoolean());
        assertEquals(ModelChoice.DEFAULT.maxCompletionTokens, body.get("max_tokens").getAsInt());
        assertFalse(connection.getInstanceFollowRedirects());
        for (int status : new int[] {302, 401, 402, 429, 500}) {
            FakeConnection error = new FakeConnection(status, "dummy-key preview");
            IOException expected = assertThrows(IOException.class, () -> send(url -> error));
            assertFalse(expected.toString().contains("dummy-key"));
            assertFalse(expected.toString().contains("preview"));
            if (status == 401) assertTrue(expected.getMessage().contains("authentication"));
            if (status == 402) {
                assertTrue(expected.getMessage().startsWith("OpenRouter credit unavailable: "));
                assertTrue(expected.getMessage().contains("longest possible reply"));
                assertTrue(expected.getMessage().contains("Add credit"));
            }
            if (status == 429) assertTrue(expected.getMessage().contains("rate limit"));
        }
    }

    @Test
    public void namesSmallwrightAsTheAppInOpenRoutersRankings() throws Exception {
        FakeConnection connection = new FakeConnection(200, SseBody.of(VALID));
        send(url -> connection);
        assertEquals(
                "https://github.com/Mouhand-Kaddo/smallwright",
                connection.getRequestProperty("HTTP-Referer"));
        assertEquals("Smallwright", connection.getRequestProperty("X-OpenRouter-Title"));
        assertEquals(
                "native-app-builder", connection.getRequestProperty("X-OpenRouter-Categories"));
    }

    @Test
    public void namesTheTransportFailureKindWithoutItsText() throws Exception {
        IOException[] failures = {
            new java.net.SocketTimeoutException("secret"),
            new java.io.EOFException("secret"),
            new javax.net.ssl.SSLException("secret"),
            new java.net.ConnectException("secret"),
            new IOException("secret")
        };
        String[] kinds = {"read timeout", "connection closed", "TLS", "connection", "other"};
        for (int i = 0; i < failures.length; i++) {
            IOException failure = failures[i];
            FakeConnection broken =
                    new FakeConnection(200, VALID) {
                        @Override
                        public java.io.InputStream getInputStream() throws IOException {
                            throw failure;
                        }
                    };
            ModelClient.RequestFailed error =
                    assertThrows(ModelClient.RequestFailed.class, () -> send(url -> broken));
            assertEquals("OpenRouter request failed (" + kinds[i] + ")", error.getMessage());
            assertEquals(ModelClient.RequestFailed.Retry.CONNECTION, error.retry);
        }
    }

    @Test
    public void boundsCancellationAndCutReplies() throws Exception {
        ModelClient.Cancellation cancelled = new ModelClient.Cancellation();
        cancelled.cancel();
        assertThrows(
                IOException.class,
                () ->
                        new ModelClient(
                                        url -> {
                                            throw new AssertionError("Cancelled request connected");
                                        },
                                        ModelChoice.DEFAULT)
                                .completeStep(KEY, conversation(), 0, cancelled));
        for (String response :
                new String[] {
                    " ".repeat(ModelClient.MAX_RESPONSE_BYTES + 1),
                    SseBody.of(VALID).substring(0, SseBody.of(VALID).length() / 2)
                }) {
            FakeConnection truncated = new FakeConnection(200, response);
            assertThrows(IOException.class, () -> send(url -> truncated));
        }
    }

    @Test
    public void everyRequestOfARunIsSentWithNoRequestBudget() throws Exception {
        ModelClient client =
                new ModelClient(
                        url -> new FakeConnection(200, SseBody.of(VALID)), ModelChoice.DEFAULT);
        for (int request = 0; request < 12; request++)
            assertEquals(
                    "Hello",
                    client.completeStep(KEY, conversation(), 0, new ModelClient.Cancellation())
                            .text);
    }

    static class FakeConnection extends java.net.HttpURLConnection {
        final int status;
        final String body;
        final java.io.ByteArrayOutputStream sent = new java.io.ByteArrayOutputStream();
        final java.util.Map<String, String> headers = new java.util.HashMap<>();

        FakeConnection(int status, String body) throws IOException {
            super(new java.net.URL(ModelClient.ENDPOINT));
            this.status = status;
            this.body = body;
        }

        public void connect() {}

        public boolean usingProxy() {
            return false;
        }

        public void disconnect() {}

        public int getResponseCode() {
            return status;
        }

        public String getHeaderField(String name) {
            return headers.get(name);
        }

        public java.io.OutputStream getOutputStream() {
            return sent;
        }

        public java.io.InputStream getInputStream() throws java.io.IOException {
            return new java.io.ByteArrayInputStream(
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
