package dev.smallwright.controller;

import android.content.Context;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.libraries.Library;
import dev.smallwright.libraries.LibraryList;
import dev.smallwright.libraries.LibraryResolver;
import dev.smallwright.libraries.MavenRepositories;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The run's library tools. They change only the project's library list in the run's working copy,
 * after the controller has resolved, downloaded and checked every file the change needs.
 */
final class LibraryTools {
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_MESSAGE_CHARS = 500;

    /** Whether the person agrees to add this library, asked before anything is downloaded. */
    interface Permission {
        boolean allows(Library library);
    }

    /** The checked Android platform jar; throws when it is not on this phone. */
    interface PlatformJar {
        File verifiedJar() throws IOException;
    }

    private final MavenRepositories repositories;
    private final Permission permission;
    private final PlatformJar platform;

    LibraryTools(MavenRepositories repositories) {
        this(repositories, library -> true);
    }

    LibraryTools(MavenRepositories repositories, Permission permission) {
        this(
                repositories,
                permission,
                () -> {
                    throw new IOException("Android platform files are not on this phone");
                });
    }

    LibraryTools(MavenRepositories repositories, Permission permission, PlatformJar platform) {
        this.repositories = repositories;
        this.permission = permission;
        this.platform = platform;
    }

    /** Google's Maven repository and Maven Central, cached in controller-private storage. */
    static MavenRepositories repositories(Context context) {
        return new MavenRepositories(
                new File(context.getApplicationContext().getNoBackupFilesDir(), "libraries"),
                MavenRepositories.PUBLIC);
    }

    /** The checked JAR and AAR files a library list needs, downloading any not kept yet. */
    static List<LibraryResolver.Resolved> resolve(MavenRepositories repositories, byte[] list)
            throws IOException {
        return new LibraryResolver(repositories).resolve(LibraryList.libraries(list));
    }

    /** The manifests of the Android libraries among {@code resolved}, by library, in order. */
    static Map<String, String> manifests(List<LibraryResolver.Resolved> resolved) {
        Map<String, String> manifests = new LinkedHashMap<>();
        for (LibraryResolver.Resolved library : resolved)
            if (library.manifest != null)
                manifests.put(library.library.toString(), library.manifest);
        return manifests;
    }

    /** The Android library manifests a library list brings, for checking the merged manifest. */
    Map<String, String> manifests(byte[] list) throws IOException {
        try {
            return manifests(resolve(repositories, list));
        } catch (IOException unavailable) {
            throw new IOException(
                    "The project's libraries could not be prepared: " + unavailable.getMessage());
        }
    }

    String search(String query, RunSteps steps) {
        List<String> found;
        try {
            found = repositories.search(query, MAX_SEARCH_RESULTS);
        } catch (IOException failure) {
            return refused(steps, "Could not search libraries", bounded(failure));
        }
        steps.show(
                StepKind.DONE, "Searched libraries for \"" + query + "\"", found.size() + " found");
        if (found.isEmpty()) return "No library matches. Try another word.";
        return "Libraries (group:artifact, newest version listed, repository):\n"
                + String.join("\n", found)
                + "\nadd_library with group:artifact adds the newest stable version.";
    }

    String add(String name, WorkingCopy files, RunSteps steps) {
        Library wanted;
        try {
            wanted = Library.parse(name);
        } catch (IllegalArgumentException invalid) {
            return refused(steps, "Could not add a library", invalid.getMessage());
        }
        steps.show(StepKind.RUNNING, "Adding library " + wanted.key(), "");
        try {
            Map<String, Library> listed = LibraryList.read(files.read(LibraryList.PATH));
            Library chosen =
                    wanted.version != null
                            ? wanted
                            : wanted.at(
                                    repositories.newestStableVersion(
                                            wanted.group, wanted.artifact));
            Library previous = listed.get(chosen.key());
            if (chosen.equals(previous)) {
                steps.finishLast(
                        StepKind.DONE,
                        "Library " + chosen.key() + " is already at " + chosen.version,
                        "");
                return chosen + " is already in the project.";
            }
            if (!permission.allows(chosen)) {
                steps.finishLast(
                        StepKind.DONE,
                        "Did not add library " + chosen.key() + " " + chosen.version,
                        "You chose not to.");
                return "The person chose not to add "
                        + chosen
                        + ". Nothing was downloaded. Do not add it again. Carry on without it or"
                        + " tell the person what else you could do.";
            }
            Set<Library> before = new HashSet<>();
            for (LibraryResolver.Resolved resolved : resolvedOrNone(listed))
                before.add(resolved.library);
            Map<String, Library> next = new TreeMap<>(listed);
            next.put(chosen.key(), chosen);
            List<LibraryResolver.Resolved> resolved =
                    new LibraryResolver(repositories).resolve(new ArrayList<>(next.values()));
            files.write(LibraryList.PATH, LibraryList.write(next));
            List<String> also = new ArrayList<>();
            StringBuilder uses = new StringBuilder();
            for (LibraryResolver.Resolved library : resolved) {
                if (!library.library.equals(chosen) && !before.contains(library.library))
                    also.add(library.library.key() + " " + library.library.version);
                uses.append("- ")
                        .append(library.library)
                        .append(" (")
                        .append(library.file.length())
                        .append(" bytes, ")
                        .append(library.repository)
                        .append(")\n");
            }
            steps.finishLast(
                    StepKind.DONE,
                    previous == null
                            ? "Added library " + chosen.key() + " " + chosen.version
                            : "Changed library " + chosen.key() + " to " + chosen.version,
                    also.isEmpty() ? "" : "Also " + String.join(", ", also));
            return (previous == null
                            ? "Added " + chosen + "."
                            : "Changed "
                                    + chosen.key()
                                    + " from "
                                    + previous.version
                                    + " to "
                                    + chosen.version
                                    + ".")
                    + " Downloaded and checked; with what they need, the project's libraries"
                    + " are now:\n"
                    + uses
                    + "It is saved when you build.";
        } catch (IOException failure) {
            String why = bounded(failure);
            steps.finishLast(StepKind.FAILED, "Could not add library " + wanted, why);
            return why;
        }
    }

    String remove(String name, WorkingCopy files, RunSteps steps) {
        Library wanted;
        try {
            wanted = Library.parse(name);
        } catch (IllegalArgumentException invalid) {
            return refused(steps, "Could not remove a library", invalid.getMessage());
        }
        String what = "Could not remove library " + wanted.key();
        Map<String, Library> listed;
        try {
            listed = LibraryList.read(files.read(LibraryList.PATH));
        } catch (IOException failure) {
            return refused(steps, what, bounded(failure));
        }
        if (listed.remove(wanted.key()) == null)
            return refused(
                    steps,
                    what,
                    wanted.key()
                            + " is not in the project's libraries. "
                            + LibraryList.PATH
                            + " lists them.");
        byte[] next = LibraryList.write(listed);
        if (next == null) files.delete(LibraryList.PATH);
        else
            try {
                files.write(LibraryList.PATH, next);
            } catch (IOException full) {
                return refused(steps, what, bounded(full));
            }
        steps.show(StepKind.DONE, "Removed library " + wanted.key(), "");
        return "Removed " + wanted.key() + ". It is saved when you build.";
    }

    /** Looks a class or member up in the platform jar and the run's current libraries. */
    String lookUp(
            String symbol,
            int offset,
            WorkingCopy files,
            RunSteps steps,
            java.util.function.BooleanSupplier active) {
        String shown = CodeLookup.shortName(symbol);
        List<CodeLookup.Archive> archives = new ArrayList<>();
        String note = "";
        try {
            archives.add(
                    new CodeLookup.Archive(
                            "the Android platform files", platform.verifiedJar(), false));
        } catch (IOException missing) {
            note =
                    "The Android platform files are not on this phone, so only the project's"
                            + " libraries were searched.\n";
        }
        try {
            for (LibraryResolver.Resolved library :
                    resolve(repositories, files.read(LibraryList.PATH)))
                archives.add(
                        new CodeLookup.Archive(
                                "library " + library.library,
                                library.file,
                                library.manifest != null));
            CodeLookup.Result found = CodeLookup.find(archives, symbol, offset, active);
            steps.show(StepKind.DONE, "Looked up " + shown, found.detail);
            return note + found.text;
        } catch (IOException failure) {
            return refused(steps, "Could not look up " + shown, bounded(failure));
        }
    }

    private List<LibraryResolver.Resolved> resolvedOrNone(Map<String, Library> listed) {
        try {
            return new LibraryResolver(repositories).resolve(new ArrayList<>(listed.values()));
        } catch (IOException unavailable) {
            return new ArrayList<>();
        }
    }

    private static String refused(RunSteps steps, String what, String why) {
        steps.show(StepKind.FAILED, what, why);
        return why;
    }

    private static String bounded(IOException failure) {
        String message = failure.getMessage() == null ? "no details" : failure.getMessage();
        return message.length() <= MAX_MESSAGE_CHARS
                ? message
                : message.substring(0, MAX_MESSAGE_CHARS) + "…";
    }
}
