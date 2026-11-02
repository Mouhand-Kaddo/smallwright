package dev.smallwright.controller;

import static org.junit.Assert.*;

import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.SseBody;
import dev.smallwright.agent.UsageBudget;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Each reply is bounded; a run as a whole has no token cap. */
public final class GenerationLimitsTest {
    private static int tokens(String response) throws Exception {
        ScriptedConnection connection = new ScriptedConnection(SseBody.of(response));
        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        com.google.gson.JsonObject user = new com.google.gson.JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", "context");
        messages.add(user);
        return new ModelClient(url -> connection, ModelChoice.DEFAULT)
                .completeStep(
                        "dummy-key".getBytes(StandardCharsets.US_ASCII),
                        messages,
                        0,
                        new ModelClient.Cancellation())
                .totalTokens;
    }

    private static String usage(int prompt, int completion) {
        return "{\"model\":\"deepseek/deepseek-v4.1-flash\",\"choices\":[{\"finish_reason\":"
                + "\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"}}],"
                + "\"usage\":{\"prompt_tokens\":"
                + prompt
                + ",\"completion_tokens\":"
                + completion
                + ",\"total_tokens\":"
                + (prompt + completion)
                + ",\"cost\":0}}";
    }

    @Test
    public void replyUsageAcceptsTheBoundsButRejectsPromptOrCompletionOverflow() throws Exception {
        int prompt = UsageBudget.MAX_PROMPT_TOKENS;
        int completion = UsageBudget.MAX_COMPLETION_TOKENS;
        assertEquals(prompt + completion, tokens(usage(prompt, completion)));
        assertEquals(1_000 + 1_800_000, tokens(usage(1_000, 1_800_000)));
        assertThrows(java.io.IOException.class, () -> tokens(usage(prompt + 1, completion)));
        assertThrows(java.io.IOException.class, () -> tokens(usage(prompt, completion + 1)));
    }

    @Test
    public void journalAcceptsAnyGrowingRunTotalButNeverLessThanReported() {
        RunJournal.Snapshot editing =
                RunJournal.Snapshot.start("large_run", 0)
                        .advance(RunJournal.State.PLANNING, 0, null, null)
                        .advance(RunJournal.State.EDITING, 0, null, 50_000_000);
        assertEquals(
                Integer.valueOf(50_000_000),
                editing.advance(RunJournal.State.ANSWERED, 0, null, null).reportedTokens);
        assertThrows(
                IllegalArgumentException.class,
                () -> editing.advance(RunJournal.State.ANSWERED, 0, null, 49_999_999));
    }

    private static final class ScriptedConnection extends java.net.HttpURLConnection {
        private final String body;

        ScriptedConnection(String body) throws java.io.IOException {
            super(new java.net.URL(ModelClient.ENDPOINT));
            this.body = body;
        }

        @Override
        public void connect() {}

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void disconnect() {}

        @Override
        public int getResponseCode() {
            return 200;
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}
