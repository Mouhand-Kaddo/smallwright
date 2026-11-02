package dev.smallwright.libraries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a project's libraries to the JAR and AAR files a build needs. Compile and runtime
 * dependencies are followed transitively, the highest requested version of each library wins,
 * exclusions apply below the dependency that declares them, and versions, properties and managed
 * versions are read through parent POMs and imported BOMs.
 */
public final class LibraryResolver {
    private static final int MAX_PARENTS = 10;
    private static final int MAX_LIBRARIES = 300;
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]{1,200})\\}");
    private static final String KOTLIN = "org.jetbrains.kotlin:";
    private static final List<String> KOTLIN_PARTS =
            List.of("kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8", "kotlin-stdlib-common");
    private static final Pattern EXACT_RANGE = Pattern.compile("\\[([^,\\]]+)(?:\\]|,.*)");

    /** One library a build uses, with its checked JAR or AAR file in the cache. */
    public static final class Resolved {
        public final Library library;
        public final File file;
        public final String sha256;
        public final String repository;

        /** The manifest of an Android library (AAR); null for a plain JAR. */
        public final String manifest;

        Resolved(Library library, MavenRepositories.Fetched fetched, String manifest) {
            this.library = library;
            this.file = fetched.file;
            this.sha256 = fetched.sha256;
            this.repository = fetched.repository;
            this.manifest = manifest;
        }
    }

    private static final class Model {
        String repository;
        String packaging;
        final Map<String, String> properties = new HashMap<>();
        final Map<String, Pom.Dependency> managed = new HashMap<>();
        final List<Pom.Dependency> dependencies = new ArrayList<>();
    }

    private static final class Visit {
        final Library library;
        final Set<String> exclusions;

        Visit(Library library, Set<String> exclusions) {
            this.library = library;
            this.exclusions = exclusions;
        }
    }

    private final MavenRepositories repositories;
    private final Map<Library, Model> models = new HashMap<>();

    public LibraryResolver(MavenRepositories repositories) {
        this.repositories = repositories;
    }

    /** The libraries, with every library they need, sorted by name. */
    public List<Resolved> resolve(List<Library> wanted) throws IOException {
        Map<String, Library> selected = new TreeMap<>();
        for (Library library : wanted) select(selected, library);
        Set<String> reached;
        for (int round = 0; ; round++) {
            if (round > MAX_LIBRARIES)
                throw new IOException("The libraries' versions never settle");
            Map<String, Library> requested = new HashMap<>();
            reached = traverse(wanted, selected, requested);
            boolean changed = false;
            for (Library request : requested.values()) changed |= select(selected, request);
            changed |= alignKotlin(selected);
            if (!changed) break;
        }
        List<Resolved> resolved = new ArrayList<>();
        for (String key : new TreeSet<>(reached)) {
            Library library = selected.get(key);
            Model model = model(library, 0);
            String packaging = model.packaging == null ? "jar" : model.packaging;
            if ("pom".equals(packaging)) continue;
            boolean android = "aar".equals(packaging);
            if (!android && !"jar".equals(packaging) && !"bundle".equals(packaging))
                throw new IOException(library + " is a " + packaging + " package, not a library");
            MavenRepositories.Fetched fetched =
                    repositories.file(
                            library,
                            library.fileName(android ? "aar" : "jar"),
                            MavenRepositories.MAX_LIBRARY_BYTES,
                            model.repository);
            resolved.add(
                    new Resolved(
                            library,
                            fetched,
                            android ? AndroidArchive.manifest(library, fetched.file) : null));
        }
        return resolved;
    }

    /**
     * Kotlin's standard library parts follow kotlin-stdlib's version, as Kotlin's Gradle metadata
     * requires: from Kotlin 1.8 the parts are empty and their classes are in kotlin-stdlib, so an
     * older part beside a newer kotlin-stdlib would define the same classes twice.
     */
    private static boolean alignKotlin(Map<String, Library> selected) {
        Library stdlib = selected.get(KOTLIN + "kotlin-stdlib");
        if (stdlib == null) return false;
        boolean changed = false;
        for (String part : KOTLIN_PARTS) {
            Library current = selected.get(KOTLIN + part);
            if (current != null) changed |= select(selected, current.at(stdlib.version));
        }
        return changed;
    }

    private static boolean select(Map<String, Library> selected, Library library) {
        Library current = selected.get(library.key());
        if (current != null && MavenVersion.compare(library.version, current.version) <= 0)
            return false;
        selected.put(library.key(), library);
        return true;
    }

    private Set<String> traverse(
            List<Library> wanted, Map<String, Library> selected, Map<String, Library> requested)
            throws IOException {
        Set<String> reached = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<Visit> queue = new ArrayDeque<>();
        for (Library library : wanted)
            queue.add(new Visit(selected.get(library.key()), new TreeSet<>()));
        while (!queue.isEmpty()) {
            Visit visit = queue.removeFirst();
            if (!visited.add(visit.library + "|" + visit.exclusions)) continue;
            reached.add(visit.library.key());
            if (reached.size() > MAX_LIBRARIES)
                throw new IOException("The libraries need more than " + MAX_LIBRARIES + " others");
            Model model = model(visit.library, 0);
            for (Pom.Dependency dependency : model.dependencies) {
                Library needed = dependency(visit.library, model, dependency, visit.exclusions);
                if (needed == null) continue;
                Library request = requested.get(needed.key());
                if (request == null || MavenVersion.compare(needed.version, request.version) > 0)
                    requested.put(needed.key(), needed);
                Set<String> exclusions = new TreeSet<>(visit.exclusions);
                exclusions.addAll(dependency.exclusions);
                Pom.Dependency managed =
                        model.managed.get(
                                value(model, dependency.group)
                                        + ":"
                                        + value(model, dependency.artifact));
                if (managed != null) exclusions.addAll(managed.exclusions);
                Library chosen = selected.get(needed.key());
                queue.add(new Visit(chosen == null ? needed : chosen, exclusions));
            }
        }
        return reached;
    }

    /** The library a dependency entry asks for, or null when a build does not need it. */
    private Library dependency(
            Library owner, Model model, Pom.Dependency dependency, Set<String> exclusions)
            throws IOException {
        String group = value(model, dependency.group);
        String artifact = value(model, dependency.artifact);
        if (group == null || artifact == null)
            throw new IOException("The POM of " + owner + " has a dependency without a name");
        Pom.Dependency managed = model.managed.get(group + ":" + artifact);
        String scope = value(model, dependency.scope);
        if (scope == null && managed != null) scope = value(model, managed.scope);
        if (scope != null && !"compile".equals(scope) && !"runtime".equals(scope)) return null;
        if ("true".equals(value(model, dependency.optional))) return null;
        for (String pattern : exclusions) {
            String[] parts = pattern.split(":", 2);
            if ((parts[0].equals("*") || parts[0].equals(group))
                    && (parts.length < 2 || parts[1].equals("*") || parts[1].equals(artifact)))
                return null;
        }
        String type = value(model, dependency.type);
        if (dependency.classifier != null)
            throw new IOException(
                    owner
                            + " needs the "
                            + value(model, dependency.classifier)
                            + " variant of "
                            + group
                            + ":"
                            + artifact
                            + ", which the builder cannot use yet");
        if (type != null
                && !"jar".equals(type)
                && !"pom".equals(type)
                && !"bundle".equals(type)
                && !"aar".equals(type))
            throw new IOException(owner + " needs a " + type + " file, which is not a library");
        String version = value(model, dependency.version);
        if (version == null && managed != null) version = value(model, managed.version);
        if (version != null && (version.startsWith("[") || version.startsWith("("))) {
            Matcher exact = EXACT_RANGE.matcher(version);
            if (!exact.matches())
                throw new IOException(
                        owner
                                + " asks for "
                                + group
                                + ":"
                                + artifact
                                + " in the range "
                                + version
                                + ", which the builder cannot choose from");
            version = exact.group(1).trim();
        }
        Library needed =
                version == null
                        ? null
                        : Library.parseOrNull(group + ":" + artifact + ":" + version);
        if (needed == null || needed.version == null)
            throw new IOException(
                    "Could not work out which version of "
                            + group
                            + ":"
                            + artifact
                            + " "
                            + owner
                            + " needs");
        return needed;
    }

    private Model model(Library library, int depth) throws IOException {
        Model known = models.get(library);
        if (known != null) return known;
        if (depth > MAX_PARENTS) throw new IOException(library + " has too many parent POMs");
        MavenRepositories.Fetched fetched =
                repositories.file(
                        library, library.fileName("pom"), MavenRepositories.MAX_POM_BYTES, null);
        Pom pom = Pom.parse(Files.readAllBytes(fetched.file.toPath()), "The POM of " + library);
        Model model = new Model();
        model.repository = fetched.repository;
        model.packaging = pom.packaging;
        Model parent = null;
        if (pom.parentArtifact != null) {
            Library parentLibrary =
                    Library.parseOrNull(
                            pom.parentGroup + ":" + pom.parentArtifact + ":" + pom.parentVersion);
            if (parentLibrary == null || parentLibrary.version == null)
                throw new IOException("The POM of " + library + " names an unreadable parent");
            parent = model(parentLibrary, depth + 1);
            model.properties.putAll(parent.properties);
            model.managed.putAll(parent.managed);
            model.dependencies.addAll(parent.dependencies);
            model.properties.put("project.parent.groupId", parentLibrary.group);
            model.properties.put("project.parent.version", parentLibrary.version);
        }
        model.properties.putAll(pom.properties);
        String group = pom.group != null ? pom.group : pom.parentGroup;
        String version = pom.version != null ? pom.version : pom.parentVersion;
        if (!library.artifact.equals(pom.artifact)
                || !library.group.equals(group)
                || (version != null && !version.contains("$") && !library.version.equals(version)))
            throw new IOException("The POM of " + library + " describes a different library");
        for (String prefix : new String[] {"project.", "pom.", ""}) {
            model.properties.put(prefix + "groupId", library.group);
            model.properties.put(prefix + "artifactId", library.artifact);
            model.properties.put(prefix + "version", library.version);
        }
        Map<String, Pom.Dependency> declared = new LinkedHashMap<>();
        for (Pom.Dependency managed : pom.managed) {
            String key = value(model, managed.group) + ":" + value(model, managed.artifact);
            if ("import".equals(value(model, managed.scope))) {
                Library bom = Library.parseOrNull(key + ":" + value(model, managed.version));
                if (bom == null || bom.version == null)
                    throw new IOException("The POM of " + library + " imports an unreadable BOM");
                model.managed.putAll(model(bom, depth + 1).managed);
            } else declared.put(key, managed);
        }
        for (Map.Entry<String, Pom.Dependency> managed : declared.entrySet())
            model.managed.put(
                    managed.getKey(), managed.getValue().with(text -> value(model, text)));
        model.dependencies.addAll(pom.dependencies);
        models.put(library, model);
        return model;
    }

    private static String value(Model model, String text) {
        if (text == null) return null;
        String value = text;
        for (int round = 0; round < 10 && value.contains("${"); round++) {
            Matcher property = PROPERTY.matcher(value);
            StringBuffer replaced = new StringBuffer();
            while (property.find()) {
                String known = model.properties.get(property.group(1));
                property.appendReplacement(
                        replaced,
                        Matcher.quoteReplacement(known == null ? property.group() : known));
            }
            property.appendTail(replaced);
            if (replaced.toString().equals(value)) break;
            value = replaced.toString();
        }
        return value.contains("${") ? null : value;
    }
}
