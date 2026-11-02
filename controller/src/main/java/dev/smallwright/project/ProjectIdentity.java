package dev.smallwright.project;

import java.io.File;
import java.util.Objects;

/** Immutable controller-owned identity; package names never depend on display titles. */
public final class ProjectIdentity {
    private final String id;
    private final String title;
    private final String packageName;
    private final File directory;

    ProjectIdentity(String id, String title, File directory) {
        this.id = id;
        this.title = title;
        this.packageName =
                "counter-alpha".equals(id)
                        ? "dev.smallwright.generated.counter"
                        : "dev.smallwright.generated." + id;
        this.directory = directory;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String packageName() {
        return packageName;
    }

    public File directory() {
        return directory;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProjectIdentity identity)) return false;
        return id.equals(identity.id)
                && title.equals(identity.title)
                && packageName.equals(identity.packageName)
                && directory.equals(identity.directory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title, packageName, directory);
    }
}
