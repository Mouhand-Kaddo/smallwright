package dev.smallwright.controller.ui;

import static org.junit.Assert.assertEquals;

import dev.smallwright.agent.RunPlan;
import org.junit.Test;

public final class PlanChecklistTest {
    private static RunPlan plan() throws Exception {
        return RunPlan.fromCall(
                "{\"steps\":[{\"text\":\"Make the screen\",\"status\":\"done\"},"
                        + "{\"text\":\"Save counts\",\"status\":\"doing\"},"
                        + "{\"text\":\"Build\",\"status\":\"todo\"}]}");
    }

    @Test
    public void aScreenReaderHearsTheProgressAndEachStepsState() throws Exception {
        RunPlan plan = plan();
        assertEquals("Plan, 1 of 3 done", PlanChecklistKt.planDescription(plan));
        assertEquals("Done: Make the screen", PlanChecklistKt.stepDescription(plan.items.get(0)));
        assertEquals("Doing now: Save counts", PlanChecklistKt.stepDescription(plan.items.get(1)));
        assertEquals("To do: Build", PlanChecklistKt.stepDescription(plan.items.get(2)));
    }
}
