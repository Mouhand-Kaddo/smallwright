package dev.smallwright.libraries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The project file listing the project's libraries, one exact {@code group:artifact:version} per
 * line. Only the library tools change it.
 */
public final class LibraryList {
    public static final String PATH = "libraries.txt";
    private static final int MAX_LIBRARIES = 100;

    private LibraryList() {}

    /** The listed libraries by {@code group:artifact}; a missing file lists none. */
    public static Map<String, Library> read(byte[] bytes) throws IOException {
        Map<String, Library> libraries = new TreeMap<>();
        if (bytes == null) return libraries;
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            if (line.trim().isEmpty()) continue;
            Library library = Library.parseOrNull(line);
            if (library == null || library.version == null)
                throw new IOException(PATH + " has a line that is not group:artifact:version");
            if (libraries.put(library.key(), library) != null)
                throw new IOException(PATH + " lists " + library.key() + " twice");
        }
        if (libraries.size() > MAX_LIBRARIES)
            throw new IOException(PATH + " lists more than " + MAX_LIBRARIES + " libraries");
        return libraries;
    }

    /** The file's bytes, or null when no library is listed and the file should not exist. */
    public static byte[] write(Map<String, Library> libraries) {
        if (libraries.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        for (Library library : new TreeMap<>(libraries).values()) text.append(library).append('\n');
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static List<Library> libraries(byte[] bytes) throws IOException {
        return new ArrayList<>(read(bytes).values());
    }
}
