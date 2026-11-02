package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import dev.smallwright.security.ModelKeyInput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * OpenRouter's public model list, reduced to the models the builder can use, which needs no key; or
 * the models a model server lists, asked for with its key.
 */
public final class ModelCatalog {
    public static final String ENDPOINT = "https://openrouter.ai/api/v1/models";
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final int MAX_MODELS = 2000;
    private static final BigDecimal MILLION = new BigDecimal(1_000_000);

    private ModelCatalog() {}

    /** Call off the UI thread. */
    public static List<ModelChoice> fetch() throws IOException {
        java.net.HttpURLConnection connection =
                ModelProvider.OPENROUTER.open(new java.net.URL(ENDPOINT));
        try {
            return parse(download(connection));
        } catch (IOException | RuntimeException failure) {
            throw new IOException("Could not load the model list");
        } finally {
            connection.disconnect();
        }
    }

    /**
     * The models {@code server} lists at {@code /models}, by name, with the person's key. Call off
     * the UI thread.
     */
    public static List<ModelChoice> fetch(ModelProvider server, byte[] key) throws IOException {
        return fetch(server, key, server::open);
    }

    static List<ModelChoice> fetch(
            ModelProvider server, byte[] key, ModelClient.ConnectionFactory connections)
            throws IOException {
        if (key == null) throw new IOException("Could not load the model list");
        byte[] owned = key.clone();
        java.net.HttpURLConnection connection = null;
        try {
            String apiKey = new String(owned, StandardCharsets.US_ASCII);
            if (!ModelKeyInput.sendable(apiKey)) throw new IOException("Invalid credential");
            connection = connections.open(new java.net.URL(server.baseUrl + "/models"));
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            return parse(server, download(connection));
        } catch (IOException | RuntimeException failure) {
            throw new IOException("Could not load the model list");
        } finally {
            Arrays.fill(owned, (byte) 0);
            if (connection != null) connection.disconnect();
        }
    }

    private static String download(java.net.HttpURLConnection connection) throws IOException {
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept", "application/json");
        if (connection.getResponseCode() != 200) throw new IOException("Model list unavailable");
        try (InputStream input = connection.getInputStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[16 * 1024];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (bytes.size() + count > MAX_BYTES)
                    throw new IOException("Model list exceeds limit");
                bytes.write(chunk, 0, count);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static JsonArray data(String json) throws IOException {
        JsonArray data;
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            data = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("data");
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid model list");
        }
        if (data == null || data.size() > MAX_MODELS) throw new IOException("Invalid model list");
        return data;
    }

    /**
     * Every model the server names, once, by name; the context it lists as {@code context_length}
     * or {@code context_window}, else {@link ModelChoice#UNKNOWN_CONTEXT_TOKENS}. A standard list
     * says nothing about tools, so all are kept and the person picks.
     */
    static List<ModelChoice> parse(ModelProvider server, String json) throws IOException {
        List<ModelChoice> models = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement entry : data(json)) {
            try {
                JsonObject listed = entry.getAsJsonObject();
                String id = listed.getAsJsonPrimitive("id").getAsString();
                if (!listed.getAsJsonPrimitive("id").isString() || seen.contains(id)) continue;
                models.add(ModelChoice.atServer(server, id, listedContext(listed)));
                seen.add(id);
            } catch (RuntimeException skipped) {
            }
        }
        models.sort(Comparator.comparing(model -> model.id));
        return models;
    }

    private static int listedContext(JsonObject listed) {
        for (String field : List.of("context_length", "context_window")) {
            try {
                int tokens = listed.get(field).getAsInt();
                if (tokens >= 1 && tokens <= ModelChoice.MAX_CONTEXT_TOKENS) return tokens;
            } catch (RuntimeException unlisted) {
            }
        }
        return ModelChoice.UNKNOWN_CONTEXT_TOKENS;
    }

    static List<ModelChoice> parse(String json) throws IOException {
        JsonArray data = data(json);
        List<ModelChoice> models = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JsonElement entry : data) {
            try {
                ModelChoice model = usable(entry.getAsJsonObject());
                if (model != null && seen.add(model.id)) models.add(model);
            } catch (RuntimeException skipped) {
            }
        }
        return models;
    }

    private static ModelChoice usable(JsonObject entry) {
        String id = entry.get("id").getAsString();
        if (id.endsWith(":batch")) return null;
        JsonElement alias = entry.get("alias_target");
        if (id.startsWith("~") || alias != null && !alias.isJsonNull()) return null;
        List<String> parameters = strings(entry.getAsJsonArray("supported_parameters"));
        if (!parameters.contains("tools") || !parameters.contains("tool_choice")) return null;
        JsonObject architecture = entry.getAsJsonObject("architecture");
        if (!strings(architecture.getAsJsonArray("output_modalities")).contains("text"))
            return null;
        JsonObject pricing = entry.getAsJsonObject("pricing");
        BigDecimal prompt = highest(pricing, "prompt");
        BigDecimal completion = highest(pricing, "completion");
        if (prompt.signum() < 0 || completion.signum() < 0) return null;
        JsonElement canonical = entry.get("canonical_slug");
        return new ModelChoice(
                id,
                canonical == null || canonical.isJsonNull() ? id : canonical.getAsString(),
                entry.get("name").getAsString(),
                prompt.multiply(MILLION),
                completion.multiply(MILLION),
                contextTokens(entry),
                parameters.contains("reasoning"),
                readsImages(architecture),
                maxCompletionTokens(entry));
    }

    /** The top provider's listed reply ceiling, or 0 when it lists none or a broken one. */
    private static int maxCompletionTokens(JsonObject entry) {
        try {
            int tokens =
                    entry.getAsJsonObject("top_provider").get("max_completion_tokens").getAsInt();
            return tokens >= 1 && tokens <= ModelChoice.MAX_CONTEXT_TOKENS ? tokens : 0;
        } catch (RuntimeException unlisted) {
            return 0;
        }
    }

    private static boolean readsImages(JsonObject architecture) {
        JsonElement inputs = architecture.get("input_modalities");
        return inputs != null
                && inputs.isJsonArray()
                && strings(inputs.getAsJsonArray()).contains("image");
    }

    /** The listed context length, or the top provider's when it is smaller. */
    private static int contextTokens(JsonObject entry) {
        JsonElement listed = entry.get("context_length");
        if (listed == null || listed.isJsonNull()) return ModelChoice.UNKNOWN_CONTEXT_TOKENS;
        int tokens = listed.getAsInt();
        JsonElement top = entry.get("top_provider");
        if (top != null && top.isJsonObject()) {
            JsonElement provider = top.getAsJsonObject().get("context_length");
            if (provider != null && !provider.isJsonNull())
                tokens = Math.min(tokens, provider.getAsInt());
        }
        return tokens;
    }

    /** The base price or the highest time-of-day override, per token. */
    private static BigDecimal highest(JsonObject pricing, String kind) {
        BigDecimal price = new BigDecimal(pricing.get(kind).getAsString());
        JsonArray overrides = pricing.getAsJsonArray("overrides");
        if (overrides == null || price.signum() < 0) return price;
        for (JsonElement override : overrides) {
            JsonElement value = override.getAsJsonObject().get(kind);
            if (value != null) price = price.max(new BigDecimal(value.getAsString()));
        }
        return price;
    }

    private static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement value : array) values.add(value.getAsString());
        return values;
    }
}
