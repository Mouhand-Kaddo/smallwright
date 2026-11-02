package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import org.junit.Test;

public final class RunLimitsTest {
    @Test
    public void anEmptyFieldMeansNoLimit() {
        assertEquals(0, RunLimits.parseSteps(""));
        assertEquals(0, RunLimits.parseSteps("  "));
        assertNull(RunLimits.parseCostUsd(""));
        assertNull(RunLimits.parseCostUsd(" $ "));
    }

    @Test
    public void stepsAreWholeNumbersFromOne() {
        assertEquals(25, RunLimits.parseSteps(" 25 "));
        for (String bad : new String[] {"0", "-3", "2.5", "ten", "10001"})
            assertThrows(bad, IllegalArgumentException.class, () -> RunLimits.parseSteps(bad));
    }

    @Test
    public void spendingIsDollarsAboveZero() {
        assertEquals(new BigDecimal("0.50"), RunLimits.parseCostUsd("0.50"));
        assertEquals(new BigDecimal("2"), RunLimits.parseCostUsd("$2"));
        for (String bad : new String[] {"0", "-1", "free", "1,5", "10001"})
            assertThrows(bad, IllegalArgumentException.class, () -> RunLimits.parseCostUsd(bad));
    }

    @Test
    public void moneyShowsCentsOrFourDecimals() {
        assertEquals("$0.50", RunLimits.money(new BigDecimal("0.5")));
        assertEquals("$2.00", RunLimits.money(new BigDecimal("2")));
        assertEquals("$0.0123", RunLimits.money(new BigDecimal("0.0123456")));
        assertEquals("$0.0002", RunLimits.money(new BigDecimal("0.00020")));
    }

    @Test
    public void aSpendingLimitRefusesASendToAServerThatReportsNoCost() {
        dev.smallwright.agent.ModelChoice served =
                dev.smallwright.agent.ModelChoice.atServer(
                        dev.smallwright.agent.ModelProvider.openAiCompatible(
                                "https://api.example.com/v1"),
                        "gpt-5.5",
                        131_072);
        RunLimits capped = new RunLimits(0, new BigDecimal("1"), false);
        assertNull(capped.refusal(dev.smallwright.agent.ModelChoice.DEFAULT));
        assertNull(new RunLimits(5, null, true).refusal(served));
        String refusal = capped.refusal(served);
        org.junit.Assert.assertTrue(refusal, refusal.contains("spending limit"));
        org.junit.Assert.assertTrue(refusal, refusal.contains("Nothing was sent"));
    }
}
