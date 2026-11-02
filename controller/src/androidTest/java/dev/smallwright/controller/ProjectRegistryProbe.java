package dev.smallwright.controller;

import android.content.Context;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/** Disposable cache-only check of Android project metadata persistence and directory sync. */
final class ProjectRegistryProbe {
    private ProjectRegistryProbe() {}

    static void run(Context context) throws Exception {
        File root =
                new File(
                        context.getCacheDir(),
                        "project-registry-probe-" + UUID.randomUUID().toString());
        ProjectRegistry first = new ProjectRegistry(root, context.getCacheDir());
        ProjectIdentity created = first.create("Phone notes");
        if (!Files.isRegularFile(new File(created.directory(), "project.bin").toPath())) {
            throw new AssertionError("Project metadata was not published");
        }
        ProjectRegistry reopened = new ProjectRegistry(root, context.getCacheDir());
        if (!created.equals(reopened.get(created.id()))
                || reopened.list().size() != 1
                || !created.packageName().equals("dev.smallwright.generated." + created.id())) {
            throw new AssertionError("Project identity changed after reopen");
        }
        ProjectIdentity legacy = reopened.ensureLegacyProject();
        if (!"counter-alpha".equals(legacy.id())
                || !"dev.smallwright.generated.counter".equals(legacy.packageName())
                || new ProjectRegistry(root, context.getCacheDir()).list().size() != 2) {
            throw new AssertionError("Legacy identity was not preserved");
        }
    }
}
