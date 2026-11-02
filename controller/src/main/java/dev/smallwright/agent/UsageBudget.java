package dev.smallwright.agent;

/**
 * Bounds every model reply's reported usage is checked against. A request asks for as long a reply
 * as the model's listed ceiling and its context allow, and a run itself has no request or token
 * cap.
 */
public final class UsageBudget {
    /**
     * More than twice the longest reply OpenRouter lists for any model: 1,800,000 tokens for Grok
     * 4.20 on 2026-10-25.
     */
    public static final int MAX_COMPLETION_TOKENS = 4_000_000;

    public static final int MAX_PROMPT_TOKENS = 1_000_000;
    public static final int MAX_REPLY_TOKENS = MAX_PROMPT_TOKENS + MAX_COMPLETION_TOKENS;

    /** Context a request leaves free for the reply, when deciding to summarize earlier steps. */
    public static final int REPLY_ROOM_TOKENS = 16384;

    private UsageBudget() {}
}
