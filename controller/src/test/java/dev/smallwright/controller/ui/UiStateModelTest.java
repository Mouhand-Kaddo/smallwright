package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ReasoningLevel;
import java.math.BigDecimal;
import org.junit.Test;

public final class UiStateModelTest {
    @Test
    public void aSavedChoiceOfTheDefaultModelReplacesTheBuiltInOneWithItsOwnPriceAndContext() {
        ModelChoice saved =
                new ModelChoice(
                        ModelChoice.DEFAULT.id,
                        ModelChoice.DEFAULT.canonicalId,
                        ModelChoice.DEFAULT.name,
                        new BigDecimal("0.04"),
                        new BigDecimal("0.144"),
                        200_000);
        UiState ui = new UiState();
        ui.setModel(saved);
        assertSame(saved, ui.getModel());
    }

    @Test
    public void theThinkingLevelStartsLowAndClosed() {
        UiState ui = new UiState();
        assertEquals(ReasoningLevel.LOW, ui.getReasoning());
        assertEquals(false, ui.getReasoningOpen());
    }
}
