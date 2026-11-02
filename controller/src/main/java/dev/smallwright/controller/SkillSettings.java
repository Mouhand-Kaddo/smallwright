package dev.smallwright.controller;

import android.app.Activity;
import dev.smallwright.controller.ui.SkillRow;
import dev.smallwright.controller.ui.UiState;
import dev.smallwright.skills.GitHubSkills;
import dev.smallwright.skills.SkillStore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The skills part of Settings: finds skills at a GitHub link, adds the ticked ones after asking
 * before any replaces a skill already here, and turns added skills on or off or removes them. The
 * list is read again each time Settings opens, since the builder may add skills in a run.
 */
final class SkillSettings {
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();

    final SkillStore store;
    private final Activity activity;
    private final UiState ui;
    private GitHubSkills.Result found;
    private final Set<String> ticked = new LinkedHashSet<>();

    SkillSettings(Activity activity, UiState ui) {
        this.activity = activity;
        this.ui = ui;
        store = new SkillStore(new File(activity.getFilesDir(), "skills"));
        ui.getFindSkills().setOnClickListener(this::find);
        ui.getAddSkills().setOnClickListener(this::add);
        ui.getReplaceSkills().setOnClickListener(this::save);
        ui.getCancelSkills().setOnClickListener(() -> ui.setSkillsReplacing(List.of()));
        ui.setOnToggleFoundSkill(this::tick);
        ui.setOnToggleSkill(this::toggle);
        ui.setOnRemoveSkill(this::remove);
        ui.getRemoveAllSkills().setOnClickListener(this::removeAll);
        ui.setOnShowSkills(this::showAdded);
        showAdded();
    }

    private void find() {
        String link = ui.getSkillLink().getText();
        found = null;
        ticked.clear();
        showFound();
        ui.getFindSkills().setEnabled(false);
        ui.setSkillsMessage("Looking for skills at that link…");
        TASKS.execute(
                () -> {
                    GitHubSkills.Result result = null;
                    String message;
                    try {
                        result =
                                new GitHubSkills(GitHubSkills.PUBLIC)
                                        .find(
                                                link,
                                                (done, total) ->
                                                        later(
                                                                () ->
                                                                        ui.setSkillsMessage(
                                                                                "Downloading skill "
                                                                                        + Math.min(
                                                                                                done
                                                                                                        + 1,
                                                                                                total)
                                                                                        + " of "
                                                                                        + total
                                                                                        + "…")));
                        message = foundMessage(result);
                    } catch (IOException refused) {
                        message = refused.getMessage();
                    }
                    GitHubSkills.Result shown = result;
                    String text = message;
                    later(
                            () -> {
                                found = shown;
                                if (shown != null)
                                    for (GitHubSkills.Found skill : shown.found)
                                        ticked.add(skill.name);
                                ui.getFindSkills().setEnabled(true);
                                ui.setSkillsMessage(text);
                                showFound();
                            });
                });
    }

    private static String foundMessage(GitHubSkills.Result result) {
        int count = result.found.size();
        StringBuilder message =
                new StringBuilder(
                        count == 0
                                ? "No skill at that link could be used."
                                : "Found " + count + (count == 1 ? " skill." : " skills."));
        if (!result.skipped.isEmpty()) {
            message.append(" Skipped:");
            for (String skipped : result.skipped) message.append("\n- ").append(skipped);
        }
        return message.toString();
    }

    private void tick(String name) {
        if (!ticked.remove(name)) ticked.add(name);
        ui.setSkillsReplacing(List.of());
        showFound();
    }

    private void add() {
        showAdded();
        List<String> replacing = new ArrayList<>();
        for (GitHubSkills.Found skill : chosen())
            if (installed(skill.name)) replacing.add(skill.name);
        if (replacing.isEmpty()) save();
        else ui.setSkillsReplacing(replacing);
    }

    private void save() {
        if (found == null) return;
        List<GitHubSkills.Found> chosen = chosen();
        String source = found.source;
        ui.getAddSkills().setEnabled(false);
        ui.getReplaceSkills().setEnabled(false);
        TASKS.execute(
                () -> {
                    String message;
                    boolean saved = false;
                    try {
                        store.add(source, chosen);
                        saved = true;
                        message =
                                "Added "
                                        + chosen.size()
                                        + (chosen.size() == 1 ? " skill." : " skills.");
                    } catch (IOException failure) {
                        message = "Could not save the skills. Try again.";
                    }
                    boolean done = saved;
                    String text = message;
                    later(
                            () -> {
                                ui.getReplaceSkills().setEnabled(true);
                                ui.setSkillsReplacing(List.of());
                                if (done) {
                                    found = null;
                                    ticked.clear();
                                    ui.getSkillLink().clear();
                                }
                                ui.setSkillsMessage(text);
                                showFound();
                                showAdded();
                            });
                });
    }

    private void toggle(String name) {
        change(() -> store.toggle(name), null, "Could not change the skill. Try again.");
    }

    private void remove(String name) {
        change(
                () -> store.remove(name),
                "Removed " + name + ".",
                "Could not remove the skill. Try again.");
    }

    private void removeAll() {
        change(store::removeAll, "Removed all skills.", "Could not remove the skills. Try again.");
    }

    private interface Change {
        void run() throws IOException;
    }

    private void change(Change change, String done, String failed) {
        TASKS.execute(
                () -> {
                    String message = done;
                    try {
                        change.run();
                    } catch (IOException failure) {
                        message = failed;
                    }
                    String text = message;
                    later(
                            () -> {
                                if (text != null) ui.setSkillsMessage(text);
                                showAdded();
                                showFound();
                            });
                });
    }

    private List<GitHubSkills.Found> chosen() {
        List<GitHubSkills.Found> chosen = new ArrayList<>();
        if (found != null)
            for (GitHubSkills.Found skill : found.found)
                if (ticked.contains(skill.name)) chosen.add(skill);
        return chosen;
    }

    private boolean installed(String name) {
        for (SkillRow skill : ui.getSkills()) if (skill.getName().equals(name)) return true;
        return false;
    }

    private void showFound() {
        List<SkillRow> rows = new ArrayList<>();
        if (found != null)
            for (GitHubSkills.Found skill : found.found)
                rows.add(
                        new SkillRow(
                                skill.name,
                                skill.description,
                                ticked.contains(skill.name),
                                installed(skill.name),
                                !skill.modelInvocable,
                                null));
        ui.setFoundSkills(rows);
        int count = chosen().size();
        ui.getAddSkills().setText("Add " + count + (count == 1 ? " skill" : " skills"));
        ui.getAddSkills().setEnabled(count > 0);
    }

    private void showAdded() {
        List<SkillRow> rows = new ArrayList<>();
        ui.setSkillsDamaged(false);
        try {
            for (SkillStore.Installed skill : store.list())
                rows.add(
                        new SkillRow(
                                skill.name,
                                skill.description,
                                skill.on,
                                false,
                                !skill.modelInvocable,
                                skill.projectTitle));
        } catch (IOException damaged) {
            ui.setSkillsDamaged(true);
        }
        ui.setSkills(rows);
    }

    private void later(Runnable update) {
        activity.runOnUiThread(
                () -> {
                    if (!activity.isDestroyed()) update.run();
                });
    }
}
