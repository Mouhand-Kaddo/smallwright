package dev.smallwright.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The builder's to-do list for a big job, from its latest update_plan call: steps in plain words,
 * each to do, being done or done. Saved as one line per step, its mark first.
 */
public final class RunPlan {
    public static final int MAX_STEPS = 20;
    public static final int MAX_STEP_CHARS = 200;
    private static final Set<String> STEP_FIELDS = Set.of("text", "status");

    /** Where a step stands; {@link #mark} is how the checklist and the saved plan show it. */
    public enum Status {
        TODO("[ ]"),
        DOING("[>]"),
        DONE("[x]");

        public final String mark;

        Status(String mark) {
            this.mark = mark;
        }

        static Status named(String name) {
            for (Status status : values())
                if (status.name().toLowerCase(Locale.ROOT).equals(name)) return status;
            return null;
        }
    }

    public static final class Item {
        public final String text;
        public final Status status;

        private Item(String text, Status status) {
            this.text = text;
            this.status = status;
        }
    }

    /** A plan the controller does not show; the message tells the model what to fix. */
    public static final class Refused extends Exception {
        Refused(String message) {
            super(message);
        }
    }

    public final List<Item> items;

    private RunPlan(List<Item> items) {
        this.items = List.copyOf(items);
    }

    /** The plan from an update_plan call's arguments. */
    public static RunPlan fromCall(String arguments) throws Refused {
        String shape =
                "Not updated: steps must be a list of 1 to "
                        + MAX_STEPS
                        + " steps, each with text and status (todo, doing or done).";
        JsonElement steps;
        try {
            steps = ModelClient.strictJson(arguments).getAsJsonObject().get("steps");
        } catch (IOException | RuntimeException malformed) {
            throw new Refused(shape);
        }
        if (steps == null || !steps.isJsonArray()) throw new Refused(shape);
        JsonArray list = steps.getAsJsonArray();
        if (list.isEmpty() || list.size() > MAX_STEPS) throw new Refused(shape);
        List<Item> items = new ArrayList<>();
        for (JsonElement element : list) items.add(item(element, items.size() + 1));
        return new RunPlan(items);
    }

    private static Item item(JsonElement element, int number) throws Refused {
        String where = "Not updated: step " + number;
        if (!element.isJsonObject()) throw new Refused(where + " is not an object.");
        JsonObject step = element.getAsJsonObject();
        if (!STEP_FIELDS.equals(step.keySet()))
            throw new Refused(where + " must have exactly text and status.");
        String text = string(step.get("text"));
        text = text == null ? null : text.trim();
        if (text == null || !plainLine(text))
            throw new Refused(
                    where
                            + " needs text: 1 to "
                            + MAX_STEP_CHARS
                            + " characters of plain words on one line.");
        Status status = Status.named(string(step.get("status")));
        if (status == null) throw new Refused(where + " needs status todo, doing or done.");
        return new Item(text, status);
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static boolean plainLine(String text) {
        if (text.isEmpty() || text.length() > MAX_STEP_CHARS) return false;
        for (int index = 0; index < text.length(); index++)
            if (Character.isISOControl(text.charAt(index))) return false;
        return true;
    }

    /** A plan as {@link #saved} wrote it, or null when it is not one. */
    public static RunPlan fromSaved(String saved) {
        if (saved == null || saved.isEmpty()) return null;
        String[] lines = saved.split("\n", -1);
        if (lines.length > MAX_STEPS) return null;
        List<Item> items = new ArrayList<>();
        for (String line : lines) {
            Status status = null;
            for (Status candidate : Status.values())
                if (line.startsWith(candidate.mark + " ")) status = candidate;
            if (status == null) return null;
            String text = line.substring(status.mark.length() + 1);
            if (!plainLine(text) || !text.equals(text.trim())) return null;
            items.add(new Item(text, status));
        }
        return new RunPlan(items);
    }

    public String saved() {
        StringBuilder saved = new StringBuilder();
        for (Item item : items) {
            if (saved.length() > 0) saved.append('\n');
            saved.append(item.status.mark).append(' ').append(item.text);
        }
        return saved.toString();
    }

    /** How far the plan is, such as "3 of 7 done". */
    public String progress() {
        int done = 0;
        for (Item item : items) if (item.status == Status.DONE) done++;
        return done + " of " + items.size() + " done";
    }
}
