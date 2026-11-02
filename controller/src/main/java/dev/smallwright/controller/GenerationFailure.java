package dev.smallwright.controller;

import java.io.IOException;

/** Fixed, nonsecret status for the boundary where a generation run stopped. */
final class GenerationFailure {
    enum Stage {
        START,
        KEY,
        MODEL,
        BUILD
    }

    static String safeProviderMessage(IOException error) {
        String message = error.getMessage();
        if ("Invalid model response".equals(message)
                || message != null
                        && message.matches(
                                "Invalid model response \\((envelope|output limit|usage"
                                        + "|(tool call with|no tool call,) finish (stop|content_filter|error|other)"
                                        + "|tool( (count|fields|type|id|function fields|name|target|content|revision"
                                        + "|arguments( (type|size|fields|values|json(, (control character|escape|unterminated|duplicate key|syntax))?))?))?)\\)")
                || message != null
                        && message.matches(
                                "(OpenRouter|Model server) request failed \\((read timeout|connection closed|TLS|connection|other|provider error)\\)")
                || message != null
                        && message.matches(
                                "(OpenRouter|Model server) (authentication failed|rate limit)")
                || dev.smallwright.agent.ModelClient.CREDIT_UNAVAILABLE.equals(message)
                || dev.smallwright.agent.ModelClient.SERVER_CREDIT_UNAVAILABLE.equals(message)
                || dev.smallwright.agent.ImageClient.NEEDS_OPENROUTER.equals(message)
                || "Model response exceeds limit".equals(message)
                || "Model request cancelled".equals(message)
                || message != null
                        && message.matches(
                                "(OpenRouter|Model server) request rejected \\(HTTP [0-9]{3}\\)"))
            return message;
        return "OpenRouter request failed";
    }

    static final String RUN_STOPPED =
            "Stopped. Changes the builder saved remain; unsaved changes were dropped. "
                    + "Provider usage may already have occurred.";

    static final String STALE =
            "The project changed while the builder worked, so nothing more from this run was"
                    + " saved. Send your message again to continue from the current version.";

    static final String STORAGE_FULL =
            "Your phone's storage is full, so the builder stopped. Changes it saved remain."
                    + " Free some space on the phone, then send a message to continue.";

    static final String CHAT_UNSAVED =
            "An earlier reply could not be saved in the chat, so nothing was sent. If your phone's"
                    + " storage is full, free some space on the phone, then send again.";

    /** The closing chat message of a run that ended with {@code failure} instead of the reply. */
    static String runEnded(
            boolean stopped,
            boolean published,
            Exception failure,
            Stage stage,
            String providerMessage) {
        if (stopped) return RUN_STOPPED;
        if (published)
            return "Signed APK saved, but run history could not be completed. Restart the controller to recover the saved build.";
        if (failure instanceof StepRun.StaleRevision) return STALE;
        if (failure instanceof StepRun.StorageFull) return STORAGE_FULL;
        return describe(stage, providerMessage);
    }

    static String describe(Stage stage, String providerMessage) {
        return switch (stage) {
            case KEY -> "Could not load the saved key. No request was sent.";
            case MODEL ->
                    "The builder stopped at a model response: "
                            + (providerMessage == null
                                    ? "OpenRouter request failed"
                                    : providerMessage)
                            + ". Changes it saved remain; send a message to continue.";
            case BUILD ->
                    "The builder stopped during a build on this phone. Changes it saved remain;"
                            + " send a message to continue.";
            case START -> "The builder could not start. Nothing was changed.";
        };
    }
}
