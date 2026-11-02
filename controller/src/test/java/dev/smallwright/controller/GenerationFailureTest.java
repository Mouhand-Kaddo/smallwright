package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import org.junit.Test;

public final class GenerationFailureTest {
    @Test
    public void reportsUsefulBoundedProviderFailureWithoutEchoingUnknownText() {
        assertEquals(
                "Invalid model response",
                GenerationFailure.safeProviderMessage(new IOException("Invalid model response")));
        assertEquals(
                "Invalid model response (tool target)",
                GenerationFailure.safeProviderMessage(
                        new IOException("Invalid model response (tool target)")));
        assertEquals(
                "Invalid model response (tool count)",
                GenerationFailure.safeProviderMessage(
                        new IOException("Invalid model response (tool count)")));
        assertEquals(
                "OpenRouter rate limit",
                GenerationFailure.safeProviderMessage(new IOException("OpenRouter rate limit")));
        assertEquals(
                dev.smallwright.agent.ModelClient.CREDIT_UNAVAILABLE,
                GenerationFailure.safeProviderMessage(
                        new IOException(dev.smallwright.agent.ModelClient.CREDIT_UNAVAILABLE)));
        assertEquals(
                "OpenRouter request rejected (HTTP 400)",
                GenerationFailure.safeProviderMessage(
                        new IOException("OpenRouter request rejected (HTTP 400)")));
        assertEquals(
                "OpenRouter request failed",
                GenerationFailure.safeProviderMessage(
                        new IOException("Authorization: Bearer secret-source-text")));
    }

    @Test
    public void passesTheModelServersFixedMessagesAndTheImageRefusal() {
        for (String message :
                new String[] {
                    "Model server authentication failed",
                    "Model server rate limit",
                    "Model server request rejected (HTTP 503)",
                    "Model server request failed (read timeout)",
                    "Model server request failed (provider error)",
                    dev.smallwright.agent.ModelClient.SERVER_CREDIT_UNAVAILABLE,
                    dev.smallwright.agent.ImageClient.NEEDS_OPENROUTER
                })
            assertEquals(message, GenerationFailure.safeProviderMessage(new IOException(message)));
        org.junit.Assert.assertFalse(
                GenerationFailure.safeProviderMessage(
                                new IOException("Model server request failed (secret)"))
                        .contains("secret"));
    }

    @Test
    public void passesEveryFixedParserAndTransportCategory() {
        for (String message :
                new String[] {
                    "Invalid model response (output limit)",
                    "Invalid model response (tool call with finish stop)",
                    "Invalid model response (no tool call, finish other)",
                    "Invalid model response (tool arguments json, control character)",
                    "Invalid model response (tool arguments json, duplicate key)",
                    "Invalid model response (tool arguments json)",
                    "Invalid model response (tool arguments values)",
                    "Invalid model response (tool name)",
                    "OpenRouter request failed (read timeout)",
                    "OpenRouter request failed (connection closed)",
                    "OpenRouter request failed (provider error)",
                    "Model response exceeds limit"
                })
            assertEquals(message, GenerationFailure.safeProviderMessage(new IOException(message)));
        assertEquals(
                "OpenRouter request failed",
                GenerationFailure.safeProviderMessage(
                        new IOException("Invalid model response (no tool call, finish secret)")));
        assertEquals(
                "OpenRouter request failed",
                GenerationFailure.safeProviderMessage(
                        new IOException("OpenRouter request failed (secret)")));
        assertEquals(
                "OpenRouter request failed",
                GenerationFailure.safeProviderMessage(new IOException("Model request timed out")));
    }

    @Test
    public void distinguishesProviderAndLocalBuildOutcomes() {
        assertEquals(
                "The builder stopped at a model response: Invalid model response. Changes it saved"
                        + " remain; send a message to continue.",
                GenerationFailure.describe(
                        GenerationFailure.Stage.MODEL, "Invalid model response"));
        assertEquals(
                "The builder stopped during a build on this phone. Changes it saved remain; send a"
                        + " message to continue.",
                GenerationFailure.describe(GenerationFailure.Stage.BUILD, null));
    }

    private static final Exception STALE = new StepRun.StaleRevision();

    @Test
    public void aRunThatEndsWithoutTheModelsReplySaysWhy() {
        assertEquals(
                "Stopped. Changes the builder saved remain; unsaved changes were dropped. "
                        + "Provider usage may already have occurred.",
                GenerationFailure.runEnded(true, true, STALE, GenerationFailure.Stage.BUILD, null));
        assertEquals(
                "Signed APK saved, but run history could not be completed. "
                        + "Restart the controller to recover the saved build.",
                GenerationFailure.runEnded(
                        false, true, STALE, GenerationFailure.Stage.BUILD, null));
        assertEquals(
                "The project changed while the builder worked, so nothing more from this run was"
                        + " saved. Send your message again to continue from the current version.",
                GenerationFailure.runEnded(
                        false, false, STALE, GenerationFailure.Stage.BUILD, null));
        assertEquals(
                "Your phone's storage is full, so the builder stopped. Changes it saved remain."
                        + " Free some space on the phone, then send a message to continue.",
                GenerationFailure.runEnded(
                        false,
                        false,
                        new StepRun.StorageFull(new java.io.IOException("ENOSPC")),
                        GenerationFailure.Stage.BUILD,
                        null));
        assertEquals(
                "The builder stopped at a model response: OpenRouter rate limit. Changes it saved"
                        + " remain; send a message to continue.",
                GenerationFailure.runEnded(
                        false,
                        false,
                        new java.io.IOException("OpenRouter rate limit"),
                        GenerationFailure.Stage.MODEL,
                        "OpenRouter rate limit"));
    }
}
