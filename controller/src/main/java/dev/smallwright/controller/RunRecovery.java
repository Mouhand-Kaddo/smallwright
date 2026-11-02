package dev.smallwright.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Known temporary files for one build identity; complete artifacts are preserved. */
final class RunRecovery {
    private RunRecovery() {}

    static int nextVersionCode(int stored, int journalMaximum, int installed) {
        if (stored < 1
                || stored == Integer.MAX_VALUE
                || journalMaximum < 0
                || installed < 0
                || journalMaximum == Integer.MAX_VALUE
                || installed == Integer.MAX_VALUE)
            throw new IllegalStateException("No generated version available");
        int next = Math.max(stored, Math.max(journalMaximum + 1, installed + 1));
        if (next == Integer.MAX_VALUE)
            throw new IllegalStateException("No generated version available");
        return next;
    }

    static File directory(File artifacts, String buildId) throws IOException {
        if (buildId == null || !buildId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid build identity");
        if (Files.isSymbolicLink(artifacts.toPath()))
            throw new SecurityException("Artifact root is a link");
        File directory = new File(artifacts, buildId);
        if (Files.isSymbolicLink(directory.toPath()))
            throw new SecurityException("Build directory is a link");
        if (!directory.mkdirs() && !directory.isDirectory())
            throw new IOException("Could not create build directory");
        return directory;
    }

    static void discardPartials(File artifacts, String buildId) throws IOException {
        File directory = directory(artifacts, buildId);
        for (String name :
                new String[] {
                    "snapshot.partial.zip", "unsigned.partial.apk", "signed.partial.apk"
                }) {
            File partial = new File(directory, name);
            if (Files.isSymbolicLink(partial.toPath()))
                throw new SecurityException("Partial artifact is a link");
            Files.deleteIfExists(partial.toPath());
        }
    }
}
