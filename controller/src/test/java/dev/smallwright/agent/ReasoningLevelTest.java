package dev.smallwright.agent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReasoningLevelTest {
    @Test
    public void readsBackEveryStoredLevel() {
        for (ReasoningLevel level : ReasoningLevel.values())
            assertEquals(level, ReasoningLevel.stored(level.name()));
    }

    @Test
    public void nothingStoredOrSomethingUnreadableIsLow() {
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.DEFAULT);
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.stored(null));
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.stored(""));
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.stored("EXTREME"));
        assertEquals(ReasoningLevel.LOW, ReasoningLevel.stored("high"));
    }

    @Test
    public void namesTheLevelsInPlainWords() {
        assertEquals("off", ReasoningLevel.OFF.label());
        assertEquals("low", ReasoningLevel.LOW.label());
        assertEquals("medium", ReasoningLevel.MEDIUM.label());
        assertEquals("high", ReasoningLevel.HIGH.label());
    }
}
