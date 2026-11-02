package dev.smallwright.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The model list in sections: in use, suggested, free, then all models cheapest per app first. */
public final class ModelSections {
    /** One titled run of models; empty sections are left out. */
    public static final class Section {
        public final String title;
        public final List<ModelChoice> models;

        Section(String title, List<ModelChoice> models) {
            this.title = title;
            this.models = List.copyOf(models);
        }
    }

    private ModelSections() {}

    /**
     * Each model appears once, in the first section it belongs to; the model in use leads only when
     * it is an OpenRouter model.
     */
    public static List<Section> of(List<ModelChoice> models, ModelChoice current) {
        Set<String> placed = new HashSet<>();
        List<Section> sections = new ArrayList<>();
        add(
                sections,
                placed,
                "in use",
                current.provider.isOpenRouter() ? List.of(current) : List.of());
        List<ModelChoice> suggested = new ArrayList<>();
        for (String id : ModelChoice.SUGGESTED)
            for (ModelChoice model : models) if (model.id.equals(id)) suggested.add(model);
        add(sections, placed, "suggested", suggested);
        add(
                sections,
                placed,
                "free",
                models.stream().filter(ModelChoice::isFree).collect(Collectors.toList()));
        add(
                sections,
                placed,
                "all models",
                models.stream()
                        .sorted(Comparator.comparing(ModelChoice::appCostUsd))
                        .collect(Collectors.toList()));
        return sections;
    }

    private static void add(
            List<Section> sections, Set<String> placed, String title, List<ModelChoice> models) {
        List<ModelChoice> fresh = new ArrayList<>();
        for (ModelChoice model : models) if (placed.add(model.id)) fresh.add(model);
        if (!fresh.isEmpty()) sections.add(new Section(title, fresh));
    }
}
