package dev.smallwright.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.junit.Test;

public final class RunPlanTest {
    private static JsonObject step(String text, String status) {
        JsonObject step = new JsonObject();
        if (text != null) step.addProperty("text", text);
        if (status != null) step.addProperty("status", status);
        return step;
    }

    private static String plan(JsonObject... steps) {
        JsonArray list = new JsonArray();
        for (JsonObject step : steps) list.add(step);
        JsonObject arguments = new JsonObject();
        arguments.add("steps", list);
        return arguments.toString();
    }

    private static String refusal(String arguments) {
        return assertThrows(RunPlan.Refused.class, () -> RunPlan.fromCall(arguments)).getMessage();
    }

    @Test
    public void readsEveryStepInOrderAndCountsTheDoneOnes() throws Exception {
        RunPlan plan =
                RunPlan.fromCall(
                        plan(
                                step("Make the list screen", "done"),
                                step("Save the glasses each day", "doing"),
                                step("Pick the colours", "todo")));
        assertEquals(3, plan.items.size());
        assertEquals("Make the list screen", plan.items.get(0).text);
        assertEquals(RunPlan.Status.DONE, plan.items.get(0).status);
        assertEquals(RunPlan.Status.DOING, plan.items.get(1).status);
        assertEquals(RunPlan.Status.TODO, plan.items.get(2).status);
        assertEquals("1 of 3 done", plan.progress());
    }

    @Test
    public void theSavedPlanReadsBackAsTheSameSteps() throws Exception {
        RunPlan plan =
                RunPlan.fromCall(
                        plan(
                                step("Make the list screen", "done"),
                                step("Add [x] marks", "doing"),
                                step("Build it", "todo")));
        RunPlan read = RunPlan.fromSaved(plan.saved());
        assertEquals(plan.items.size(), read.items.size());
        for (int index = 0; index < plan.items.size(); index++) {
            assertEquals(plan.items.get(index).text, read.items.get(index).text);
            assertEquals(plan.items.get(index).status, read.items.get(index).status);
        }
        assertEquals("1 of 3 done", read.progress());
    }

    @Test
    public void aSavedPlanThatWasChangedOrCutReadsAsNone() {
        assertNull(RunPlan.fromSaved(""));
        assertNull(RunPlan.fromSaved("[x] Done\nnot a step"));
        assertNull(RunPlan.fromSaved("[?] Unknown mark"));
        assertNull(RunPlan.fromSaved("[x] "));
        StringBuilder tooMany = new StringBuilder();
        for (int index = 0; index <= RunPlan.MAX_STEPS; index++) tooMany.append("[ ] Step\n");
        assertNull(RunPlan.fromSaved(tooMany.toString()));
    }

    @Test
    public void refusesArgumentsThatAreNotAListOfSteps() {
        refusal("not json");
        refusal("{}");
        refusal("{\"steps\":\"Make it\"}");
        refusal(plan());
        JsonObject[] tooMany = new JsonObject[RunPlan.MAX_STEPS + 1];
        for (int index = 0; index < tooMany.length; index++)
            tooMany[index] = step("Step " + index, "todo");
        refusal(plan(tooMany));
    }

    @Test
    public void refusesAStepWithoutPlainWordsOrAKnownStatusAndSaysWhichStep() throws Exception {
        JsonObject good = step("Make the list screen", "done");
        for (JsonObject bad :
                List.of(
                        step(null, "todo"),
                        step("   ", "todo"),
                        step("x".repeat(RunPlan.MAX_STEP_CHARS + 1), "todo"),
                        step("Two\nlines", "todo"),
                        step("Tab\tinside", "todo"),
                        step("Make it", null),
                        step("Make it", "started"),
                        step("Make it", "DONE"))) {
            assertTrue(refusal(plan(good, bad)).contains("step 2"));
        }
        JsonObject extra = step("Make it", "todo");
        extra.addProperty("priority", "high");
        assertTrue(refusal(plan(good, extra)).contains("step 2"));
        JsonObject number = new JsonObject();
        number.addProperty("text", 5);
        number.addProperty("status", "todo");
        assertTrue(refusal(plan(good, number)).contains("step 2"));
        assertEquals(
                "x".repeat(RunPlan.MAX_STEP_CHARS),
                RunPlan.fromCall(plan(step("x".repeat(RunPlan.MAX_STEP_CHARS), "todo")))
                        .items
                        .get(0)
                        .text);
    }

    @Test
    public void wordsAroundAStepAreTrimmed() throws Exception {
        assertEquals(
                "Make the list screen",
                RunPlan.fromCall(plan(step("  Make the list screen ", "todo"))).items.get(0).text);
    }
}
