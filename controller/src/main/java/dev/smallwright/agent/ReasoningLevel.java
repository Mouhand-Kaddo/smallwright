package dev.smallwright.agent;

import java.util.Locale;

/**
 * How much the model thinks before it answers, sent as OpenRouter's {@code reasoning.effort}. Off
 * sends {@code "none"}, which OpenRouter documents as disabling reasoning entirely.
 */
public enum ReasoningLevel {
    OFF("none"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    public static final ReasoningLevel DEFAULT = LOW;

    final String effort;

    ReasoningLevel(String effort) {
        this.effort = effort;
    }

    /** The level as the person reads it, for example "low". */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The level saved under {@link #name()}; nothing saved or anything unreadable is the default.
     */
    public static ReasoningLevel stored(String name) {
        for (ReasoningLevel level : values()) if (level.name().equals(name)) return level;
        return DEFAULT;
    }
}
