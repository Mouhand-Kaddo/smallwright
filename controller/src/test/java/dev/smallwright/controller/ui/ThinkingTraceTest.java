package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ThinkingTraceTest {
    @Test
    public void aFoldedTraceIsReadAsItsOpeningWordsAndAnOpenOneWhole() {
        String trace = "Plan the screens.\n" + "x".repeat(300);
        assertEquals("Thinking: " + trace, ChatKt.traceDescription(trace, true));
        String folded = ChatKt.traceDescription(trace, false);
        assertEquals("Thinking, folded: " + trace.substring(0, 160) + "…", folded);
        assertEquals("Thinking, folded: short", ChatKt.traceDescription("short", false));
    }

    @Test
    public void foldingNeverSplitsACharacter() {
        String trace = "a".repeat(159) + "😀" + "b";
        assertEquals(
                "Thinking, folded: " + "a".repeat(159) + "😀…",
                ChatKt.traceDescription(trace, false));
    }

    @Test
    public void aTraceStillGrowingIsReadAsItsNewestWords() {
        String trace = "x".repeat(300) + "\nNow the layout.";
        assertEquals(
                "Thinking so far: …" + trace.substring(trace.length() - 160),
                ChatKt.traceDescription(trace, false, true));
        assertEquals("Thinking so far: short", ChatKt.traceDescription("short", false, true));
        assertEquals("Thinking: short", ChatKt.traceDescription("short", true, true));
    }
}
