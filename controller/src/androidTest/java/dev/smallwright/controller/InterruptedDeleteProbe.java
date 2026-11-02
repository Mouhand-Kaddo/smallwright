package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.file.Files;

/**
 * Leaves a throwaway project in the state of a delete killed after unpublishing, selected and with
 * a chat, then opens the controller and checks that startup finishes the delete.
 */
final class InterruptedDeleteProbe {
    private InterruptedDeleteProbe() {}

    static String run(Instrumentation in) throws Exception {
        Context target = in.getTargetContext();
        File files = target.getFilesDir();
        ProjectRegistry projects = new ProjectRegistry(new File(files, "projects"), files);
        ChatStore chats = new ChatStore(new File(files, "chats"), files);
        android.content.SharedPreferences selection =
                target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        String previous = selection.getString("active-project", null);
        ProjectIdentity doomed = projects.create("Interrupted delete probe");
        chats.createChat(doomed.id(), "Probe chat");
        Files.createDirectories(doomed.directory().toPath().resolve("artifacts"));
        if (!target.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                .edit()
                .putString("active-project", doomed.id())
                .commit()) throw new AssertionError("Selection not saved");
        projects.unpublish(doomed.id());
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(in, ui);
            ProjectIdentity active =
                    PhoneAlphaCounterProbe.mainValue(
                            in,
                            () ->
                                    (ProjectIdentity)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "activeProject"));
            boolean blocked =
                    PhoneAlphaCounterProbe.mainValue(
                            in,
                            () ->
                                    (boolean)
                                            ActiveBuildInterruptionProbe.field(
                                                    ui, "startupBlocked"));
            if (active.id().equals(doomed.id()) || blocked)
                throw new AssertionError("Startup did not recover");
            if (doomed.directory().exists()
                    || new File(new File(files, "chats"), doomed.id()).exists()
                    || !projects.unpublished().isEmpty())
                throw new AssertionError("Interrupted delete was not finished");
            return "Interrupted delete recovered: opened "
                    + active.id()
                    + ", project directory and chats removed\n";
        } finally {
            ui.finish();
            if (!selection.edit().putString("active-project", previous).commit())
                throw new AssertionError("Selection not restored");
        }
    }
}
