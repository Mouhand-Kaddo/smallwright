package dev.smallwright.controller;

import android.content.Context;
import android.content.SharedPreferences;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelProvider;
import dev.smallwright.agent.ReasoningLevel;
import java.math.BigDecimal;

/**
 * The model the person picked and how much it thinks, and the model server's address, kept on this
 * phone. The OpenRouter model stays saved while a server model is in use. A missing or damaged
 * entry reads as the default.
 */
final class ModelChoiceStore {
    private static final String IN_USE = "provider";
    private static final String SERVER_IN_USE = "server";
    private static final String SERVER_BASE = "server-base";
    private static final String SERVER_ID = "server-id";
    private static final String SERVER_CONTEXT = "server-context";

    private final SharedPreferences preferences;

    ModelChoiceStore(Context context) {
        preferences = context.getSharedPreferences("model-choice", Context.MODE_PRIVATE);
    }

    /** True when a saved choice existed but could not be read, so the default was used. */
    boolean damaged;

    ModelChoice load() {
        damaged = false;
        try {
            if (SERVER_IN_USE.equals(preferences.getString(IN_USE, null)))
                return ModelChoice.atServer(
                        server(),
                        preferences.getString(SERVER_ID, null),
                        preferences.getInt(SERVER_CONTEXT, ModelChoice.UNKNOWN_CONTEXT_TOKENS));
            String id = preferences.getString("id", null);
            if (id == null) return ModelChoice.DEFAULT;
            return new ModelChoice(
                    id,
                    preferences.getString("canonicalId", id),
                    preferences.getString("name", id),
                    new BigDecimal(preferences.getString("prompt", "")),
                    new BigDecimal(preferences.getString("completion", "")),
                    preferences.getInt(
                            "context",
                            id.equals(ModelChoice.DEFAULT.id)
                                    ? ModelChoice.DEFAULT.contextTokens
                                    : ModelChoice.UNKNOWN_CONTEXT_TOKENS),
                    preferences.getBoolean("reasoning", id.equals(ModelChoice.DEFAULT.id)),
                    preferences.getBoolean("images", id.equals(ModelChoice.DEFAULT.id)),
                    preferences.getInt(
                            "maxCompletion",
                            id.equals(ModelChoice.DEFAULT.id)
                                    ? ModelChoice.DEFAULT.maxCompletionTokens
                                    : 0));
        } catch (RuntimeException unreadable) {
            damaged = true;
            return ModelChoice.DEFAULT;
        }
    }

    /**
     * True when the saved model was saved before the phone kept whether it thinks, reads pictures
     * and how long a reply may be, so the model list should fill those in.
     */
    boolean needsRefresh() {
        String id = preferences.getString("id", null);
        return id != null
                && !SERVER_IN_USE.equals(preferences.getString(IN_USE, null))
                && !id.equals(ModelChoice.DEFAULT.id)
                && !(preferences.contains("reasoning")
                        && preferences.contains("images")
                        && preferences.contains("maxCompletion"));
    }

    boolean save(ModelChoice model) {
        if (!model.provider.isOpenRouter())
            return preferences
                    .edit()
                    .putString(IN_USE, SERVER_IN_USE)
                    .putString(SERVER_BASE, model.provider.baseUrl)
                    .putString(SERVER_ID, model.id)
                    .putInt(SERVER_CONTEXT, model.contextTokens)
                    .commit();
        return preferences
                .edit()
                .remove(IN_USE)
                .putString("id", model.id)
                .putString("canonicalId", model.canonicalId)
                .putString("name", model.name)
                .putString("prompt", model.promptUsdPerMillion.toPlainString())
                .putString("completion", model.completionUsdPerMillion.toPlainString())
                .putInt("context", model.contextTokens)
                .putBoolean("reasoning", model.reasoning)
                .putBoolean("images", model.readsImages)
                .putInt("maxCompletion", model.maxCompletionTokens)
                .commit();
    }

    /** The saved model server, or null when none is saved or it can't be read. */
    ModelProvider server() {
        try {
            return ModelProvider.openAiCompatible(preferences.getString(SERVER_BASE, null));
        } catch (IllegalArgumentException unsaved) {
            return null;
        }
    }

    boolean saveServer(ModelProvider server) {
        return preferences.edit().putString(SERVER_BASE, server.baseUrl).commit();
    }

    ReasoningLevel loadReasoning() {
        return ReasoningLevel.stored(preferences.getString("reasoning-level", null));
    }

    boolean saveReasoning(ReasoningLevel level) {
        return preferences.edit().putString("reasoning-level", level.name()).commit();
    }
}
