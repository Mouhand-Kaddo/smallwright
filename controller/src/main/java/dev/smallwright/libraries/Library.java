package dev.smallwright.libraries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A library's Maven coordinates: {@code group:artifact}, with an exact version when known. */
public final class Library implements Comparable<Library> {
    private static final Pattern COORDINATES =
            Pattern.compile(
                    "([A-Za-z0-9_-]{1,64}(?:\\.[A-Za-z0-9_-]{1,64}){0,9})"
                            + ":([A-Za-z0-9_][A-Za-z0-9_.-]{0,99})"
                            + "(?::([A-Za-z0-9_][A-Za-z0-9_.+-]{0,63}))?");

    public final String group;
    public final String artifact;

    /** Null when only {@code group:artifact} was given. */
    public final String version;

    private Library(String group, String artifact, String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    /** Parses {@code group:artifact} or {@code group:artifact:version}. */
    public static Library parse(String text) {
        Library library = parseOrNull(text);
        if (library == null)
            throw new IllegalArgumentException(
                    "\""
                            + text
                            + "\" is not a library name. Write it as group:artifact or"
                            + " group:artifact:version, for example com.google.code.gson:gson.");
        return library;
    }

    /** The library, or null when {@code text} is not valid coordinates. */
    public static Library parseOrNull(String text) {
        Matcher matcher = COORDINATES.matcher(text == null ? "" : text.trim());
        return matcher.matches()
                ? new Library(matcher.group(1), matcher.group(2), matcher.group(3))
                : null;
    }

    static Library of(String group, String artifact, String version) {
        return parse(group + ":" + artifact + (version == null ? "" : ":" + version));
    }

    public Library at(String newVersion) {
        return of(group, artifact, newVersion);
    }

    /** {@code group:artifact}, the same for every version. */
    public String key() {
        return group + ":" + artifact;
    }

    String directory() {
        return group.replace('.', '/') + "/" + artifact + "/" + version + "/";
    }

    String fileName(String extension) {
        return artifact + "-" + version + "." + extension;
    }

    @Override
    public String toString() {
        return version == null ? key() : key() + ":" + version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Library && toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public int compareTo(Library other) {
        int byKey = key().compareTo(other.key());
        if (byKey != 0 || version == null || other.version == null) return byKey;
        return MavenVersion.compare(version, other.version);
    }
}
