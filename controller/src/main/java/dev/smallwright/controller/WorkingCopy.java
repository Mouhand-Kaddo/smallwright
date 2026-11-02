package dev.smallwright.controller;

import dev.smallwright.project.ProjectStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/** A run's own copy of the project files at its base revision. Edits stay here until committed. */
final class WorkingCopy {
    private final ProjectSourceStore source;
    private final TreeMap<String, byte[]> files;
    private final Map<String, byte[]> written = new TreeMap<>();
    private final Set<String> deleted = new TreeSet<>();
    private final long builderBytes;
    private final int builderFiles;
    private int revision;
    private boolean manifestStored;

    /** Refused with a stale revision when the project is no longer at {@code revision}. */
    WorkingCopy(ProjectSourceStore source, int revision) throws IOException {
        this.source = source;
        this.revision = revision;
        try {
            files = new TreeMap<>(source.readRecoverySources(revision));
        } catch (IllegalStateException stale) {
            throw new StepRun.StaleRevision();
        }
        Map<String, byte[]> builder = AppLinkSources.files(source.packageName(), new byte[32]);
        long reserved = 0;
        for (byte[] file : builder.values()) reserved += file.length;
        builderBytes = reserved;
        builderFiles = builder.size();
        manifestStored = files.containsKey(AppManifestPolicy.PATH);
        if (!manifestStored)
            files.put(
                    AppManifestPolicy.PATH,
                    source.generatedManifest().getBytes(StandardCharsets.UTF_8));
    }

    int revision() {
        return revision;
    }

    SortedMap<String, byte[]> files() {
        return Collections.unmodifiableSortedMap(files);
    }

    byte[] read(String path) {
        return files.get(path);
    }

    void write(String path, byte[] content) throws IOException {
        write(Map.of(path, content));
    }

    /**
     * Writes every file, or none when the change would take the project past its size or file
     * limits, which leave room for the builder's own code in every build. A change that does not
     * grow a project already past them is allowed, so it can be made smaller.
     */
    void write(Map<String, byte[]> changes) throws IOException {
        long before = 0;
        for (byte[] content : files.values()) before += content.length;
        long bytes = before;
        int count = files.size();
        for (Map.Entry<String, byte[]> change : changes.entrySet()) {
            byte[] replaced = files.get(change.getKey());
            if (replaced == null) count++;
            else bytes -= replaced.length;
            bytes += change.getValue().length;
        }
        long maxBytes = ProjectStore.MAX_PROJECT_BYTES - builderBytes;
        int maxFiles = ProjectStore.MAX_PROJECT_FILES - builderFiles;
        if ((bytes > maxBytes && bytes > before) || (count > maxFiles && count > files.size()))
            throw new IOException(
                    "The project would hold "
                            + bytes
                            + " bytes in "
                            + count
                            + " files, and a project holds at most "
                            + ProjectStore.MAX_PROJECT_BYTES / (1024 * 1024)
                            + " MB in "
                            + ProjectStore.MAX_PROJECT_FILES
                            + " files"
                            + (builderFiles == 0
                                    ? "."
                                    : ", of which the builder's own code takes "
                                            + builderBytes
                                            + " bytes in "
                                            + builderFiles
                                            + " file."));
        for (Map.Entry<String, byte[]> change : changes.entrySet()) {
            files.put(change.getKey(), change.getValue());
            written.put(change.getKey(), change.getValue());
            deleted.remove(change.getKey());
        }
    }

    void delete(String path) {
        files.remove(path);
        written.remove(path);
        deleted.add(path);
    }

    boolean changed() {
        return !written.isEmpty() || !deleted.isEmpty();
    }

    /**
     * Saves every edit as one new revision on top of this copy's revision, and returns it. A stale
     * revision is refused and nothing is saved.
     */
    int commit() throws IOException {
        if (!changed()) return revision;
        if (!manifestStored && files.containsKey(AppManifestPolicy.PATH))
            written.putIfAbsent(AppManifestPolicy.PATH, files.get(AppManifestPolicy.PATH));
        try {
            revision = source.commit(revision, written, deleted);
        } catch (IllegalStateException stale) {
            throw new StepRun.StaleRevision();
        }
        written.clear();
        deleted.clear();
        manifestStored = files.containsKey(AppManifestPolicy.PATH);
        return revision;
    }
}
