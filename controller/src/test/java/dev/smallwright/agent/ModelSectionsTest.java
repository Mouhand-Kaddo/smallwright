package dev.smallwright.agent;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ModelSectionsTest {
    private static ModelChoice model(String id, String in, String out) {
        return new ModelChoice(id, id, id, new BigDecimal(in), new BigDecimal(out), 131072);
    }

    private static List<String> ids(ModelSections.Section section) {
        List<String> ids = new ArrayList<>();
        for (ModelChoice model : section.models) ids.add(model.id);
        return ids;
    }

    @Test
    public void placesEachModelOnceInItsFirstSectionCheapestFirst() {
        ModelChoice dear = model("x/dear", "20", "75");
        ModelChoice cheap = model("x/cheap", "0.04", "0.15");
        ModelChoice free = model("x/free", "0", "0");
        ModelChoice middle = model("x/middle", "1", "4");
        ModelChoice suggested = model(ModelClient.MODEL, "0.045", "0.08");
        List<ModelSections.Section> sections =
                ModelSections.of(List.of(dear, suggested, cheap, free, middle), middle);
        List<String> titles = new ArrayList<>();
        for (ModelSections.Section section : sections) titles.add(section.title);
        assertEquals(List.of("in use", "suggested", "free", "all models"), titles);
        assertEquals(List.of("x/middle"), ids(sections.get(0)));
        assertEquals(List.of(ModelClient.MODEL), ids(sections.get(1)));
        assertEquals(List.of("x/free"), ids(sections.get(2)));
        assertEquals(List.of("x/cheap", "x/dear"), ids(sections.get(3)));
    }

    @Test
    public void leavesOutEmptySectionsAndTheSuggestedModelInUse() {
        ModelChoice suggested = model(ModelClient.MODEL, "0.045", "0.08");
        List<ModelSections.Section> sections = ModelSections.of(List.of(suggested), suggested);
        assertEquals(1, sections.size());
        assertEquals("in use", sections.get(0).title);
        assertEquals(List.of(ModelClient.MODEL), ids(sections.get(0)));
    }
}
