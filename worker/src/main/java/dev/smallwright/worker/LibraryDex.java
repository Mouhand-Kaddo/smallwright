package dev.smallwright.worker;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Library JARs dexed once and kept by SHA-256 in worker-private storage, so a build dexes the app
 * and merges the kept library dex files. Each entry is the worker's own D8 output, written whole or
 * not at all, and the cache is trimmed to a size limit, least recently used first.
 */
final class LibraryDex {
    static final long MAX_CACHE_BYTES = 512L * 1024 * 1024;

    private final File directory;

    LibraryDex(File directory) {
        this.directory = directory;
    }

    /** The dex files of each JAR, in order, dexing those not kept yet. */
    List<File> dexFiles(List<File> jars, File platform) throws Exception {
        Files.createDirectories(directory.toPath());
        List<File> dexFiles = new ArrayList<>();
        Set<File> used = new HashSet<>();
        for (File jar : jars) {
            File kept = new File(directory, LibraryInputs.sha256(jar));
            if (!kept.isDirectory()) {
                File partial = new File(directory, kept.getName() + ".partial");
                deleteTree(partial);
                Files.createDirectories(partial.toPath());
                D8.run(
                        D8Command.builder()
                                .setMinApiLevel(31)
                                .setIntermediate(true)
                                .addLibraryFiles(platform.toPath())
                                .addProgramFiles(jar.toPath())
                                .setOutput(partial.toPath(), OutputMode.DexIndexed)
                                .build());
                Files.move(partial.toPath(), kept.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
            if (!kept.setLastModified(System.currentTimeMillis()))
                throw new IOException("Could not mark a kept library dex file as used");
            used.add(kept);
            dexFiles.addAll(BuildService.dexFiles(kept));
        }
        trim(used);
        return dexFiles;
    }

    private void trim(Set<File> used) {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        Arrays.sort(entries, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        long total = 0;
        for (File entry : entries) {
            total += size(entry);
            if (entry.getName().endsWith(".partial")
                    || (total > MAX_CACHE_BYTES && !used.contains(entry))) deleteTree(entry);
        }
    }

    private static long size(File file) {
        File[] children = file.listFiles();
        if (children == null) return file.length();
        long total = 0;
        for (File child : children) total += size(child);
        return total;
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
