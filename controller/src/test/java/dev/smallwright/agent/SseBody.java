package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A non-streaming OpenRouter reply sent again as the server-sent events OpenRouter sends with
 * {@code stream: true}: a heartbeat, the words, reasoning and tool arguments split in two fragments
 * each, the finish chunk, the usage chunk that repeats the finish, and {@code [DONE]}.
 */
public final class SseBody {
    public static final String HEARTBEAT = ": OPENROUTER PROCESSING\n\n";

    private SseBody() {}

    public static String of(String envelope) {
        return of(JsonParser.parseString(envelope).getAsJsonObject());
    }

    public static String of(JsonObject envelope) {
        JsonElement model = envelope.get("model");
        JsonObject choice = envelope.getAsJsonArray("choices").get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        JsonElement finish = choice.get("finish_reason");
        StringBuilder out = new StringBuilder(HEARTBEAT);
        JsonObject role = new JsonObject();
        role.add("role", message.get("role"));
        out.append(chunk(model, role, null, null));
        for (String field : new String[] {"reasoning", "reasoning_content", "content"}) {
            JsonElement value = message.get(field);
            if (value == null || value.isJsonNull()) continue;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                out.append(chunk(model, delta(field, value), null, null));
                continue;
            }
            for (String half : halves(value.getAsString()))
                out.append(
                        chunk(
                                model,
                                delta(field, new com.google.gson.JsonPrimitive(half)),
                                null,
                                null));
        }
        JsonElement details = message.get("reasoning_details");
        if (details != null && details.isJsonArray()) {
            int index = 0;
            for (JsonElement detail : details.getAsJsonArray()) {
                JsonObject indexed = detail.getAsJsonObject().deepCopy();
                indexed.addProperty("index", index++);
                JsonArray one = new JsonArray();
                one.add(indexed);
                out.append(chunk(model, delta("reasoning_details", one), null, null));
            }
        } else if (details != null) {
            out.append(chunk(model, delta("reasoning_details", details), null, null));
        }
        JsonElement calls = message.get("tool_calls");
        if (calls != null && calls.isJsonArray()) {
            int index = 0;
            for (JsonElement element : calls.getAsJsonArray()) {
                JsonObject call = element.getAsJsonObject();
                JsonObject function = call.getAsJsonObject("function");
                String[] arguments = halves(function.get("arguments").getAsString());
                JsonObject first = new JsonObject();
                first.addProperty("index", index);
                first.add("id", call.get("id"));
                first.add("type", call.get("type"));
                JsonObject named = new JsonObject();
                named.add("name", function.get("name"));
                named.addProperty("arguments", arguments[0]);
                first.add("function", named);
                JsonObject rest = new JsonObject();
                rest.addProperty("index", index++);
                JsonObject more = new JsonObject();
                more.addProperty("arguments", arguments[1]);
                rest.add("function", more);
                for (JsonObject fragment : new JsonObject[] {first, rest}) {
                    JsonArray one = new JsonArray();
                    one.add(fragment);
                    out.append(chunk(model, delta("tool_calls", one), null, null));
                }
            }
        } else if (calls != null) {
            out.append(chunk(model, delta("tool_calls", calls), null, null));
        }
        out.append(chunk(model, new JsonObject(), finish, null));
        JsonElement usage = envelope.get("usage");
        if (usage != null) out.append(chunk(model, new JsonObject(), finish, usage));
        return out.append("data: [DONE]\n\n").toString();
    }

    /** One {@code data:} event holding {@code chunk} as it is. */
    public static String event(JsonObject chunk) {
        return "data: " + chunk + "\n\n";
    }

    private static String chunk(
            JsonElement model, JsonObject delta, JsonElement finish, JsonElement usage) {
        JsonObject choice = new JsonObject();
        choice.addProperty("index", 0);
        choice.add("delta", delta);
        choice.add("finish_reason", finish);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject chunk = new JsonObject();
        chunk.addProperty("id", "gen-1");
        chunk.addProperty("object", "chat.completion.chunk");
        if (model != null) chunk.add("model", model);
        chunk.add("choices", choices);
        if (usage != null) chunk.add("usage", usage);
        return event(chunk);
    }

    private static JsonObject delta(String field, JsonElement value) {
        JsonObject delta = new JsonObject();
        delta.add(field, value);
        return delta;
    }

    private static String[] halves(String text) {
        int middle = text.offsetByCodePoints(0, text.codePointCount(0, text.length()) / 2);
        return new String[] {text.substring(0, middle), text.substring(middle)};
    }
}
