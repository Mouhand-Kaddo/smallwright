package dev.smallwright.controller;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Finds a class, or a class's members, in the jars an app compiles against: the Android platform
 * files and the project's libraries. Class files are read by exact entry name, as bytes, and
 * parsed; nothing is extracted, loaded or run.
 */
final class CodeLookup {
    static final int MAX_PAGE_LINES = 150;
    static final int MAX_PAGE_BYTES = 32 * 1024;
    private static final int FOOTER_BYTES = 200;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_HIERARCHY = 50;
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_CANDIDATES = 30;
    private static final String NOT_FOUND = "Not found";
    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern NAME =
            Pattern.compile(IDENTIFIER + "(?:\\." + IDENTIFIER + ")*(?:#" + IDENTIFIER + ")?");
    private static final Pattern CLASS_ENTRY =
            Pattern.compile("(?:" + IDENTIFIER + "/)*" + IDENTIFIER + "\\.class");
    private static final Pattern ANONYMOUS = Pattern.compile(".*\\$[0-9].*");
    private static final Pattern BUNDLED_JAR =
            Pattern.compile("libs/[A-Za-z0-9_][A-Za-z0-9_.+-]{0,99}\\.jar");

    /** A JAR, or an Android library (AAR) whose classes.jar and libs/*.jar hold its classes. */
    static final class Archive {
        /** Where the classes come from, such as {@code the Android platform files}. */
        final String label;

        final File file;
        final boolean android;

        Archive(String label, File file, boolean android) {
            this.label = label;
            this.file = file;
            this.android = android;
        }
    }

    /** The tool result, and a short detail for the step: empty when found. */
    static final class Result {
        final String text, detail;

        Result(String text, String detail) {
            this.text = text;
            this.detail = detail;
        }
    }

    private CodeLookup() {}

    /**
     * Looks up {@code symbol}: a class, simple or fully qualified, optionally followed by {@code
     * .member} or {@code #member}. Members are shown from {@code offset}, counting from 1. An
     * archive that cannot be read is left out, and the result says so. Stop, when {@code active}
     * turns false, ends it with a {@link CancellationException}.
     */
    static Result find(List<Archive> archives, String symbol, int offset, BooleanSupplier active)
            throws IOException {
        String name = symbol.trim();
        if (name.indexOf('(') >= 0) name = name.substring(0, name.indexOf('(')).trim();
        if (!NAME.matcher(name).matches())
            return new Result(
                    "That is not a class name. Give a class, such as android.graphics.Paint or"
                            + " Paint, or a class and member, such as Paint.STRIKE_THRU_TEXT_FLAG"
                            + " or Paint#setFlags.\n",
                    "Not a class name");
        String className = name;
        String member = null;
        if (name.indexOf('#') >= 0) {
            className = name.substring(0, name.indexOf('#'));
            member = name.substring(name.indexOf('#') + 1);
        }
        try (ClassPath path = ClassPath.open(archives, active)) {
            Result found = find(path, className, member, offset);
            return path.skipped.length() == 0
                    ? found
                    : new Result(path.skipped + found.text, found.detail);
        }
    }

    private static Result find(ClassPath path, String className, String member, int offset)
            throws IOException {
        List<String> found = path.named(className);
        int dot = className.lastIndexOf('.');
        if (found.isEmpty() && member == null && dot > 0) {
            String last = className.substring(dot + 1);
            List<String> owner = path.named(className.substring(0, dot));
            if (!owner.isEmpty() || !looksLikeAClass(last)) {
                found = owner;
                member = last;
                className = className.substring(0, dot);
            }
        }
        if (found.isEmpty()) return notFound(path, className);
        if (found.size() > 1) return several(className, found);
        ClassFile file = path.read(found.get(0));
        String label = path.classes.get(found.get(0)).archive.label;
        return member == null
                ? listing(path, file, label, offset)
                : members(path, file, label, member, offset);
    }

    /** The symbol as a step names it: no package, such as {@code Paint.setFlags}. */
    static String shortName(String symbol) {
        String name = symbol.trim();
        if (name.indexOf('(') >= 0) name = name.substring(0, name.indexOf('('));
        String[] parts = name.replace('#', '.').split("\\.", -1);
        int first = 0;
        while (first < parts.length - 1
                && !(parts[first].length() > 0 && Character.isUpperCase(parts[first].charAt(0))))
            first++;
        return String.join(".", Arrays.copyOfRange(parts, first, parts.length));
    }

    private static boolean looksLikeAClass(String name) {
        return Character.isUpperCase(name.charAt(0)) && !name.equals(name.toUpperCase(Locale.ROOT));
    }

    private static Result listing(ClassPath path, ClassFile file, String label, int offset) {
        StringBuilder head =
                new StringBuilder(file.declaration()).append(", in ").append(label).append(".\n");
        List<String> nested = new ArrayList<>();
        for (String binary : path.classes.keySet())
            if (binary.startsWith(file.name + "$")
                    && binary.indexOf('$', file.name.length() + 1) < 0)
                nested.add(binary.substring(binary.lastIndexOf('/') + 1).replace('$', '.'));
        nested.sort(null);
        if (!nested.isEmpty())
            head.append("Nested classes: ").append(String.join(", ", nested)).append('\n');
        List<String> lines = new ArrayList<>();
        for (ClassFile.Member member : file.members()) lines.add("  " + file.declaration(member));
        return page(head.toString(), lines, offset, display(file.name));
    }

    private static Result members(
            ClassPath path, ClassFile file, String label, String name, int offset)
            throws IOException {
        boolean constructors = name.equals(file.simpleName());
        List<ClassFile> owners = constructors ? List.of(file) : path.hierarchy(file);
        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassFile owner : owners)
            for (ClassFile.Member member : owner.members())
                if ((constructors ? member.constructor() : member.name.equals(name))
                        && seen.add(member.name + member.descriptor))
                    lines.add(line(file, owner, member));
        String what = display(file.name);
        if (!lines.isEmpty())
            return page(file.declaration() + ", in " + label + ":\n", lines, offset, what);
        List<Close> close = new ArrayList<>();
        for (ClassFile owner : owners)
            for (ClassFile.Member member : owner.members()) {
                int distance = closeness(name, member.name);
                if (!member.constructor()
                        && distance >= 0
                        && seen.add(member.name + member.descriptor))
                    close.add(new Close(distance, 0, member.name, line(file, owner, member)));
            }
        close.sort(Close.ORDER);
        StringBuilder text =
                new StringBuilder(what)
                        .append(", in ")
                        .append(label)
                        .append(", has no member ")
                        .append(name)
                        .append(", nor do the classes it extends.");
        if (close.isEmpty())
            return new Result(
                    text.append(" Look up ")
                            .append(what)
                            .append(" alone to list its members.\n")
                            .toString(),
                    NOT_FOUND);
        text.append(" Close names:\n");
        List<String> names = new ArrayList<>();
        for (Close entry : close) {
            if (!names.contains(entry.name)) {
                if (names.size() == MAX_SUGGESTIONS) continue;
                names.add(entry.name);
            }
            text.append(entry.text).append('\n');
        }
        return new Result(text.toString(), NOT_FOUND);
    }

    private static String line(ClassFile looked, ClassFile owner, ClassFile.Member member) {
        String declaration = "  " + owner.declaration(member);
        return owner == looked ? declaration : declaration + "  (from " + display(owner.name) + ")";
    }

    private static Result page(String head, List<String> lines, int offset, String what) {
        if (offset > Math.max(1, lines.size()))
            return new Result(
                    what
                            + " has "
                            + lines.size()
                            + " members; offset "
                            + offset
                            + " is past the last.\n",
                    "");
        StringBuilder text = new StringBuilder(head);
        int bytes = utf8(head);
        int end = offset - 1;
        while (end < lines.size() && end - (offset - 1) < MAX_PAGE_LINES) {
            int size = utf8(lines.get(end)) + 1;
            if (end > offset - 1 && bytes + size > MAX_PAGE_BYTES - FOOTER_BYTES) break;
            text.append(lines.get(end)).append('\n');
            bytes += size;
            end++;
        }
        if (offset > 1 || end < lines.size()) {
            text.append("Members ")
                    .append(offset)
                    .append(" to ")
                    .append(end)
                    .append(" of ")
                    .append(lines.size());
            text.append(
                    end < lines.size()
                            ? ". Call lookup_code with offset " + (end + 1) + " for more.\n"
                            : ".\n");
        }
        return new Result(text.toString(), "");
    }

    private static Result notFound(ClassPath path, String className) {
        String wanted = className.substring(className.lastIndexOf('.') + 1);
        String wantedPackage =
                className.lastIndexOf('.') < 0
                        ? ""
                        : className.substring(0, className.lastIndexOf('.'));
        List<Close> close = new ArrayList<>();
        for (String binary : path.classes.keySet()) {
            String shown = display(binary);
            int distance = closeness(wanted, shown.substring(shown.lastIndexOf('.') + 1));
            if (distance >= 0) {
                String binaryPackage =
                        binary.lastIndexOf('/') < 0
                                ? ""
                                : display(binary.substring(0, binary.lastIndexOf('/')));
                close.add(
                        new Close(
                                distance,
                                binaryPackage.equals(wantedPackage) ? 0 : 1,
                                shown,
                                shown));
            }
        }
        close.sort(Close.ORDER);
        StringBuilder text =
                new StringBuilder("No class ")
                        .append(className)
                        .append(" in the Android platform files or the project's libraries.");
        if (close.isEmpty())
            text.append(
                    " No close names. If a library has it, search_libraries and add_library add"
                            + " it.");
        else {
            List<String> names = new ArrayList<>();
            for (Close entry : close.subList(0, Math.min(MAX_SUGGESTIONS, close.size())))
                names.add(entry.text);
            text.append(" Close names: ").append(String.join(", ", names)).append('.');
        }
        return new Result(text.append('\n').toString(), NOT_FOUND);
    }

    private static Result several(String className, List<String> found) {
        StringBuilder text =
                new StringBuilder("Several classes are named ")
                        .append(className)
                        .append(". Look one up by its full name:\n");
        for (String binary : found.subList(0, Math.min(MAX_CANDIDATES, found.size())))
            text.append("  ").append(display(binary)).append('\n');
        if (found.size() > MAX_CANDIDATES)
            text.append("  and ").append(found.size() - MAX_CANDIDATES).append(" more\n");
        return new Result(text.toString(), "Several classes");
    }

    /**
     * How far {@code candidate} is from {@code wanted}, ignoring case and underscores; -1 when it
     * is not close. A candidate containing what was asked for is always close.
     */
    private static int closeness(String wanted, String candidate) {
        String a = normal(wanted), b = normal(candidate);
        if (a.isEmpty() || b.isEmpty()) return -1;
        int distance = editDistance(a, b);
        if (distance <= Math.max(1, a.length() / 3)) return distance;
        return a.length() >= 3 && b.contains(a) ? distance : -1;
    }

    private static String normal(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1], current = new int[b.length() + 1];
        for (int column = 0; column <= b.length(); column++) previous[column] = column;
        for (int row = 1; row <= a.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= b.length(); column++)
                current[column] =
                        Math.min(
                                Math.min(current[column - 1], previous[column]) + 1,
                                previous[column - 1]
                                        + (a.charAt(row - 1) == b.charAt(column - 1) ? 0 : 1));
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String display(String binaryName) {
        return binaryName.replace('/', '.').replace('$', '.');
    }

    private static int utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /** A name close to one that does not exist, ranked by distance, then by rank. */
    private static final class Close {
        static final Comparator<Close> ORDER =
                Comparator.comparingInt((Close close) -> close.distance)
                        .thenComparingInt(close -> close.rank)
                        .thenComparing(close -> close.name);

        final int distance, rank;
        final String name, text;

        Close(int distance, int rank, String name, String text) {
            this.distance = distance;
            this.rank = rank;
            this.name = name;
            this.text = text;
        }
    }

    /** Where one class file is: an entry of a jar, or of a jar inside an Android library. */
    private static final class Location {
        final Archive archive;
        final ZipFile zip;

        /** The jar inside an Android library that holds the class; null for a plain jar. */
        final ZipEntry jar;

        Location(Archive archive, ZipFile zip, ZipEntry jar) {
            this.archive = archive;
            this.zip = zip;
            this.jar = jar;
        }
    }

    /** The classes of every archive by binary name; the first archive holding a name wins. */
    private static final class ClassPath implements Closeable {
        final Map<String, Location> classes = new LinkedHashMap<>();

        /** A line for each archive that could not be read and was left out. */
        final StringBuilder skipped = new StringBuilder();

        private final List<ZipFile> open = new ArrayList<>();
        private final BooleanSupplier active;

        private ClassPath(BooleanSupplier active) {
            this.active = active;
        }

        static ClassPath open(List<Archive> archives, BooleanSupplier active) throws IOException {
            ClassPath path = new ClassPath(active);
            try {
                for (Archive archive : archives) path.index(archive);
                return path;
            } catch (RuntimeException failure) {
                path.close();
                throw failure;
            }
        }

        private void ensureActive() {
            if (!active.getAsBoolean()) throw new CancellationException("Run is no longer active");
        }

        private void index(Archive archive) {
            ensureActive();
            try {
                ZipFile zip = new ZipFile(archive.file);
                open.add(zip);
                if (zip.size() > MAX_ENTRIES) throw new IOException("too many entries");
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ensureActive();
                    ZipEntry entry = entries.nextElement();
                    if (!archive.android) add(entry.getName(), new Location(archive, zip, null));
                    else if (entry.getName().equals("classes.jar")
                            || BUNDLED_JAR.matcher(entry.getName()).matches())
                        indexBundled(archive, zip, entry);
                }
            } catch (CancellationException stopped) {
                throw stopped;
            } catch (IOException | RuntimeException unreadable) {
                classes.values().removeIf(location -> location.archive == archive);
                skipped.append(archive.label)
                        .append(" could not be read, so it was not searched.\n");
            }
        }

        private void indexBundled(Archive archive, ZipFile zip, ZipEntry jar) throws IOException {
            Location location = new Location(archive, zip, jar);
            try (ZipInputStream input = new ZipInputStream(zip.getInputStream(jar))) {
                int count = 0;
                for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
                    ensureActive();
                    if (++count > MAX_ENTRIES) throw new IOException("too many entries");
                    add(entry.getName(), location);
                }
            }
        }

        private void add(String entry, Location location) {
            if (!CLASS_ENTRY.matcher(entry).matches()) return;
            String binary = entry.substring(0, entry.length() - ".class".length());
            if (!ANONYMOUS.matcher(binary).matches()) classes.putIfAbsent(binary, location);
        }

        /** Classes a name matches: a fully qualified name exactly, else by its simple name. */
        List<String> named(String name) {
            String candidate = name.replace('.', '/');
            while (true) {
                if (classes.containsKey(candidate)) return List.of(candidate);
                int slash = candidate.lastIndexOf('/');
                if (slash < 0) break;
                candidate = candidate.substring(0, slash) + "$" + candidate.substring(slash + 1);
            }
            String nested = name.replace('.', '$');
            List<String> exact = new ArrayList<>(), inner = new ArrayList<>();
            for (String binary : classes.keySet()) {
                String simple = binary.substring(binary.lastIndexOf('/') + 1);
                if (simple.equals(nested)) exact.add(binary);
                else if (simple.endsWith("$" + nested)) inner.add(binary);
            }
            List<String> found = exact.isEmpty() ? inner : exact;
            found.sort(Comparator.comparing(CodeLookup::display));
            return found;
        }

        /** The class, then the classes and interfaces it extends that are on the path. */
        List<ClassFile> hierarchy(ClassFile start) throws IOException {
            List<ClassFile> found = new ArrayList<>(List.of(start));
            Set<String> seen = new HashSet<>(Set.of(start.name));
            for (int index = 0; index < found.size(); index++) {
                ClassFile current = found.get(index);
                List<String> parents = new ArrayList<>(current.interfaceNames);
                if (current.superName != null) parents.add(0, current.superName);
                for (String parent : parents)
                    if (found.size() < MAX_HIERARCHY
                            && seen.add(parent)
                            && classes.containsKey(parent)) found.add(read(parent));
            }
            return found;
        }

        ClassFile read(String binary) throws IOException {
            Location location = classes.get(binary);
            String entry = binary + ".class";
            byte[] bytes = null;
            try {
                if (location.jar == null) {
                    try (InputStream input =
                            location.zip.getInputStream(location.zip.getEntry(entry))) {
                        bytes = readAtMost(input);
                    }
                } else {
                    try (ZipInputStream input =
                            new ZipInputStream(location.zip.getInputStream(location.jar))) {
                        for (ZipEntry inner; (inner = input.getNextEntry()) != null; )
                            if (inner.getName().equals(entry)) {
                                bytes = readAtMost(input);
                                break;
                            }
                    }
                }
            } catch (IOException | RuntimeException unreadable) {
                throw new IOException(location.archive.label + " could not be read");
            }
            if (bytes == null) throw new IOException(location.archive.label + " could not be read");
            try {
                return ClassFile.parse(bytes);
            } catch (IOException malformed) {
                throw new IOException(
                        entry + " in " + location.archive.label + " is not a valid class file");
            }
        }

        private static byte[] readAtMost(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1; ) {
                bytes.write(buffer, 0, read);
                if (bytes.size() > ClassFile.MAX_BYTES) break;
            }
            return bytes.toByteArray();
        }

        @Override
        public void close() throws IOException {
            for (ZipFile zip : open) zip.close();
        }
    }
}
