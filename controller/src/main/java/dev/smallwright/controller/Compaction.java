package dev.smallwright.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.RunPlan;
import dev.smallwright.agent.RunTools;
import dev.smallwright.agent.UsageBudget;
import dev.smallwright.security.ContextFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Keeps a run's conversation inside the picked model's context. When the next request would pass
 * three quarters of the limit, the model summarizes the earlier steps, and the run goes on with the
 * first message approved at the tap, that summary, the project's files as they are now and the
 * latest steps, all verbatim. Tokens are counted cautiously as three bytes each, or as the model's
 * own count for the last request plus what was added since, whichever is more.
 */
final class Compaction {
    static final String INSTRUCTION =
            "This run's conversation is getting too long for the model, so its earlier steps are"
                    + " replaced by your summary. Summarize the steps below for yourself, to"
                    + " continue from the summary alone: what the person asked for, what was done"
                    + " and why, decisions made, what is left to do, and build errors not yet"
                    + " fixed. Leave out file contents; the files as they are now are kept"
                    + " separately. Answer with the summary only, in at most 500 words.";

    private static final int REQUEST_OVERHEAD_BYTES = 64 * 1024;
    private static final int RECENT_STEPS = 2;
    private static final int MAX_CALL_CHARS = 1000;
    private static final int MAX_RESULT_CHARS = 2000;
    private static final int MAX_EARLIER_SUMMARY_CHARS = 16 * 1024;

    private final int limit;
    private final SortedMap<String, byte[]> base;
    private final int toolBytes = utf8(RunTools.schemas().toString());
    private int reportedTokens;
    private int bytesAtReply;
    private int[] picturesAtReply = {0, 0};

    /** {@code baseFiles} are the project's files when the run started. */
    Compaction(int contextTokens, SortedMap<String, byte[]> baseFiles) {
        this(contextTokens, baseFiles, AttachedImages.NONE);
    }

    /**
     * {@code images} go with the first message in every request: their bytes leave less room for
     * the rest, and each counts as {@link AttachedImages#ESTIMATED_TOKENS} rather than its bytes.
     */
    Compaction(int contextTokens, SortedMap<String, byte[]> baseFiles, AttachedImages images) {
        limit =
                Math.min(
                        limit(contextTokens),
                        (ModelClient.MAX_STEP_REQUEST_BYTES
                                        - REQUEST_OVERHEAD_BYTES
                                        - images.dataUrlBytes())
                                / 3);
        base = new TreeMap<>(baseFiles);
    }

    /**
     * The most tokens one request may hold: the model's context less room for its reply, within the
     * reported-usage and request-size bounds every request is checked against.
     */
    static int limit(int contextTokens) {
        int reply = Math.min(UsageBudget.REPLY_ROOM_TOKENS, contextTokens / 4);
        return Math.min(
                Math.min(contextTokens - reply, UsageBudget.MAX_PROMPT_TOKENS),
                (ModelClient.MAX_STEP_REQUEST_BYTES - REQUEST_OVERHEAD_BYTES) / 3);
    }

    /**
     * Records the model's count for the request just answered, whose reply ends {@code messages}.
     */
    void replied(int totalTokens, JsonArray messages) {
        reportedTokens = totalTokens;
        bytesAtReply = utf8(messages.toString());
        picturesAtReply = AttachedImages.pictures(messages);
    }

    /** True when the next request would hold too much and there are steps to summarize. */
    boolean due(JsonArray messages) {
        return messages.size() > 1
                && (tokens(messages) > threshold()
                        || utf8(messages.toString()) + toolBytes + REQUEST_OVERHEAD_BYTES
                                > ModelClient.MAX_STEP_REQUEST_BYTES);
    }

    /** A cautious count of the tokens a summary request holds; it has only text and no tools. */
    static int summaryTokens(JsonArray request) {
        return (utf8(request.toString()) + 2) / 3;
    }

    /** The request, offering no tools, that asks the model to summarize the earlier steps. */
    JsonArray summaryRequest(JsonArray messages) {
        int end = recentStart(messages);
        Deque<String> entries = new ArrayDeque<>();
        int budget = limit / 4 * 3;
        int left = 0;
        for (int index = end - 1; index > 0; index--) {
            String entry = entry(messages.get(index).getAsJsonObject());
            if (utf8(entry) > budget) {
                left = index;
                break;
            }
            budget -= utf8(entry);
            entries.addFirst(entry);
        }
        StringBuilder prompt =
                new StringBuilder(INSTRUCTION)
                        .append("\n\n<request>\n")
                        .append(text(messages.get(0).getAsJsonObject()))
                        .append("\n</request>\n\n<steps>\n");
        if (left > 0) prompt.append("(").append(left).append(" earlier entries left out)\n");
        for (String entry : entries) prompt.append(entry).append('\n');
        JsonArray request = new JsonArray();
        request.add(message("user", prompt.append("</steps>").toString()));
        return request;
    }

    /**
     * The conversation that continues the run: the approved first message, the summary with the
     * project's files as they are now, the brief and plan written in this run when there are, and
     * the latest steps. Throws when even that does not fit.
     */
    JsonArray compacted(
            JsonArray messages,
            String summary,
            SortedMap<String, byte[]> files,
            String brief,
            RunPlan plan)
            throws IOException {
        int recent = recentStart(messages);
        JsonArray compacted = new JsonArray();
        compacted.add(messages.get(0));
        compacted.add(
                message(
                        "user",
                        "The earlier steps of this run were summarized to make room. What the"
                                + " summary reports as done is done; do not repeat it. Continue the"
                                + " person's request from where the summary ends.\n\nSummary of the"
                                + " earlier steps:\n"
                                + cut(summary, part())
                                + "\n\n"
                                + fileStates(files)
                                + (brief == null
                                        ? ""
                                        : "\n\nThe project brief, as you wrote it in this run:\n"
                                                + ContextFilter.redact(brief))
                                + (plan == null
                                        ? ""
                                        : "\n\nYour plan, as you last sent it with update_plan:\n"
                                                + plan.saved())));
        for (int index = recent; index < messages.size(); index++)
            compacted.add(messages.get(index));
        reportedTokens = 0;
        bytesAtReply = 0;
        picturesAtReply = new int[] {0, 0};
        if (tokens(compacted) > threshold())
            throw new IOException(
                    "The run no longer fits the model's context, even with its earlier steps"
                            + " summarized");
        return compacted;
    }

    private int threshold() {
        return limit / 4 * 3;
    }

    private int part() {
        return limit / 8 * 3;
    }

    /** A cautious count of the tokens the run's next request holds, tools and pictures included. */
    int tokens(JsonArray messages) {
        int bytes = utf8(messages.toString());
        int[] pictures = AttachedImages.pictures(messages);
        int addedText = bytes - pictures[1] - (bytesAtReply - picturesAtReply[1]);
        return Math.max(
                (bytes - pictures[1] + toolBytes + 2) / 3
                        + pictures[0] * AttachedImages.ESTIMATED_TOKENS,
                reportedTokens
                        + (addedText + 2) / 3
                        + (pictures[0] - picturesAtReply[0]) * AttachedImages.ESTIMATED_TOKENS);
    }

    /**
     * Where the latest steps start: at most {@link #RECENT_STEPS} model replies with their results,
     * within one part of the limit. With nothing older than them, every step is summarized.
     */
    private int recentStart(JsonArray messages) {
        int start = messages.size();
        int bytes = 0;
        int steps = 0;
        for (int index = messages.size() - 1; index > 0 && steps < RECENT_STEPS; index--) {
            JsonObject message = messages.get(index).getAsJsonObject();
            bytes += utf8(message.toString());
            if (bytes > part()) break;
            if (role(message).equals("assistant")) {
                start = index;
                steps++;
            }
        }
        return start == 1 ? messages.size() : start;
    }

    private String fileStates(SortedMap<String, byte[]> files) {
        StringBuilder listing = new StringBuilder("The project's files now:\n");
        StringBuilder shown = new StringBuilder();
        StringBuilder unshown = new StringBuilder();
        int budget = part();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            boolean changed = !Arrays.equals(base.get(file.getKey()), file.getValue());
            listing.append(file.getKey())
                    .append(" (")
                    .append(file.getValue().length)
                    .append(changed ? " bytes, changed in this run)\n" : " bytes)\n");
            if (!changed) continue;
            String text = StepRun.text(file.getValue());
            String block =
                    text == null
                            ? null
                            : "<file path=\""
                                    + file.getKey()
                                    + "\">\n"
                                    + ContextFilter.redact(text)
                                    + "\n</file>\n";
            if (block != null && utf8(block) <= budget) {
                shown.append(block);
                budget -= utf8(block);
            } else unshown.append(file.getKey()).append('\n');
        }
        for (String path : base.keySet())
            if (!files.containsKey(path)) listing.append(path).append(" (deleted in this run)\n");
        if (shown.length() > 0)
            listing.append("\nThe files changed in this run, as they are now:\n").append(shown);
        if (unshown.length() > 0)
            listing.append("\nChanged in this run but not shown here:\n").append(unshown);
        return listing.append(
                        "\nBefore you edit a file not shown here, read the part you change again.")
                .toString();
    }

    private static String entry(JsonObject message) {
        String role = role(message);
        if (role.equals("tool")) return "Result: " + cut(text(message), MAX_RESULT_CHARS);
        if (role.equals("user"))
            return "Earlier summary: " + cut(text(message), MAX_EARLIER_SUMMARY_CHARS);
        StringBuilder entry = new StringBuilder();
        String said = text(message);
        if (!said.isEmpty())
            entry.append("Builder: ").append(cut(said, MAX_CALL_CHARS)).append('\n');
        JsonElement calls = message.get("tool_calls");
        if (calls != null && calls.isJsonArray())
            for (JsonElement call : calls.getAsJsonArray()) {
                JsonObject function = call.getAsJsonObject().getAsJsonObject("function");
                entry.append("Called ")
                        .append(function.get("name").getAsString())
                        .append(' ')
                        .append(cut(function.get("arguments").getAsString(), MAX_CALL_CHARS))
                        .append('\n');
            }
        return entry.toString().trim();
    }

    private static String text(JsonObject message) {
        JsonElement content = message.get("content");
        if (content == null || content.isJsonNull()) return "";
        if (!content.isJsonArray()) return content.getAsString();
        StringBuilder text = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray())
            if ("text".equals(part.getAsJsonObject().get("type").getAsString()))
                text.append(part.getAsJsonObject().get("text").getAsString());
        return text.toString();
    }

    private static String role(JsonObject message) {
        return message.get("role").getAsString();
    }

    private static String cut(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        int end = Character.isHighSurrogate(text.charAt(maxChars - 1)) ? maxChars - 1 : maxChars;
        return text.substring(0, end) + "…";
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static int utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
