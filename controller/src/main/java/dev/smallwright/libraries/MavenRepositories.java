package dev.smallwright.libraries;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.smallwright.net.HttpsDownload;
import dev.smallwright.net.HttpsDownload.NotFound;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google's Maven repository and Maven Central, and nothing else. Every file is checked against the
 * strongest checksum the repository publishes for it (SHA-512, then SHA-256, then SHA-1) and kept
 * in a controller-private cache keyed by repository and coordinates, so later builds work offline.
 * Redirects are never followed.
 */
public final class MavenRepositories {
    public static final String GOOGLE = "Google's Maven repository";
    public static final String CENTRAL = "Maven Central";
    static final long MAX_POM_BYTES = 1024 * 1024;
    static final long MAX_LIBRARY_BYTES = 16L * 1024 * 1024;
    private static final long MAX_INDEX_BYTES = 2L * 1024 * 1024;
    private static final String[][] CHECKSUMS = {
        {"sha512", "SHA-512", "128"}, {"sha256", "SHA-256", "64"}, {"sha1", "SHA-1", "40"}
    };
    private static final Pattern GROUP_ELEMENT = Pattern.compile("<([A-Za-z0-9_.-]+)\\s*/>");
    private static final Pattern ARTIFACT_ELEMENT =
            Pattern.compile("<([A-Za-z0-9_.-]+)\\s+versions=\"([^\"]*)\"\\s*/>");

    /** Where the two repositories and their search services live. */
    public static final class Endpoints {
        final String googleBase, centralBase, centralSearch;

        public Endpoints(String googleBase, String centralBase, String centralSearch) {
            this.googleBase = googleBase;
            this.centralBase = centralBase;
            this.centralSearch = centralSearch;
        }
    }

    public static final Endpoints PUBLIC =
            new Endpoints(
                    "https://dl.google.com/dl/android/maven2/",
                    "https://repo1.maven.org/maven2/",
                    "https://search.maven.org/solrsearch/select");

    /** A checked file in the cache. */
    static final class Fetched {
        final File file;
        final String sha256;
        final String repository;

        Fetched(File file, String sha256, String repository) {
            this.file = file;
            this.sha256 = sha256;
            this.repository = repository;
        }
    }

    private final File cache;
    private final Endpoints endpoints;

    public MavenRepositories(File cache, Endpoints endpoints) {
        this.cache = cache;
        this.endpoints = endpoints;
    }

    /**
     * One file of a library version, from {@code repository} or, when that is null, from Google's
     * repository if it has the file and otherwise Maven Central.
     */
    Fetched file(Library library, String name, long maxBytes, String repository)
            throws IOException {
        String[] order =
                repository == null ? new String[] {GOOGLE, CENTRAL} : new String[] {repository};
        for (String from : order) {
            Fetched kept = kept(from, library, name);
            if (kept != null) return kept;
        }
        for (String from : order) {
            Fetched downloaded = downloadIfPublished(from, library, name, maxBytes);
            if (downloaded != null) return downloaded;
        }
        if (repository == null)
            throw new IOException(library + " is not on " + GOOGLE + " or " + CENTRAL);
        throw new IOException(name + " is not on " + repository);
    }

    /** The highest stable version either repository lists for {@code group:artifact}. */
    public String newestStableVersion(String group, String artifact) throws IOException {
        String path = group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml";
        for (String from : new String[] {GOOGLE, CENTRAL}) {
            byte[] metadata;
            try {
                metadata = checkedBytes(from, path, MAX_POM_BYTES);
            } catch (NotFound missing) {
                continue;
            }
            String newest = null;
            for (String version :
                    Pom.versions(metadata, group + ":" + artifact + "'s version list"))
                if (MavenVersion.isStable(version)
                        && (newest == null || MavenVersion.compare(version, newest) > 0))
                    newest = version;
            if (newest == null)
                throw new IOException(group + ":" + artifact + " has no stable version on " + from);
            return Library.of(group, artifact, newest).version;
        }
        throw new IOException(group + ":" + artifact + " is not on " + GOOGLE + " or " + CENTRAL);
    }

    /**
     * Libraries matching {@code query}, as {@code group:artifact (newest version)} lines: Maven
     * Central's search, then Google's group index. Search answers carry no checksum; only names
     * that are valid coordinates are kept.
     */
    public List<String> search(String query, int limit) throws IOException {
        List<String> found = new ArrayList<>();
        IOException failure = null;
        try {
            String url =
                    endpoints.centralSearch
                            + "?q="
                            + URLEncoder.encode(query, "UTF-8")
                            + "&rows="
                            + limit
                            + "&wt=json";
            JsonObject answer =
                    JsonParser.parseString(
                                    new String(
                                            get(CENTRAL, url, MAX_INDEX_BYTES),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            for (JsonElement element : answer.getAsJsonObject("response").getAsJsonArray("docs")) {
                JsonObject doc = element.getAsJsonObject();
                addFound(
                        found,
                        doc.get("g").getAsString(),
                        doc.get("a").getAsString(),
                        doc.has("latestVersion") ? doc.get("latestVersion").getAsString() : null,
                        CENTRAL);
            }
        } catch (IOException | RuntimeException unavailable) {
            failure = new IOException(CENTRAL + "'s search did not answer");
        }
        try {
            String index =
                    new String(
                            get(GOOGLE, endpoints.googleBase + "master-index.xml", MAX_INDEX_BYTES),
                            StandardCharsets.UTF_8);
            List<String> words = new ArrayList<>();
            for (String word : query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                if (word.length() > 1) words.add(word);
            int groups = 0;
            Matcher group = GROUP_ELEMENT.matcher(index);
            while (group.find() && groups < 3 && found.size() < limit * 2) {
                String name = group.group(1);
                if (!matchesAny(name, words)) continue;
                groups++;
                String groupIndex =
                        new String(
                                get(
                                        GOOGLE,
                                        endpoints.googleBase
                                                + name.replace('.', '/')
                                                + "/group-index.xml",
                                        MAX_INDEX_BYTES),
                                StandardCharsets.UTF_8);
                Matcher artifact = ARTIFACT_ELEMENT.matcher(groupIndex);
                while (artifact.find()) {
                    String newest = null;
                    for (String version : artifact.group(2).split(","))
                        if (MavenVersion.isStable(version)
                                && (newest == null || MavenVersion.compare(version, newest) > 0))
                            newest = version;
                    addFound(found, name, artifact.group(1), newest, GOOGLE);
                }
            }
        } catch (IOException | RuntimeException unavailable) {
            if (failure != null) throw new IOException("Neither repository's search answered");
        }
        return found;
    }

    private static boolean matchesAny(String name, List<String> words) {
        for (String word : words) if (name.toLowerCase(Locale.ROOT).contains(word)) return true;
        return false;
    }

    private static void addFound(
            List<String> found, String group, String artifact, String version, String from) {
        Library library = Library.parseOrNull(group + ":" + artifact);
        if (library == null) return;
        boolean versioned = version != null && Library.parseOrNull(library + ":" + version) != null;
        String line = library + (versioned ? " (newest " + version + ")" : "") + ", " + from;
        if (!found.contains(line)) found.add(line);
    }

    private Fetched kept(String repository, Library library, String name) throws IOException {
        File file = cached(repository, library, name);
        File recorded = new File(file.getPath() + ".sha256");
        if (!file.isFile() || !recorded.isFile()) return null;
        String expected =
                new String(Files.readAllBytes(recorded.toPath()), StandardCharsets.US_ASCII);
        if (expected.equals(hex(digest(file, "SHA-256"))))
            return new Fetched(file, expected, repository);
        Files.deleteIfExists(recorded.toPath());
        Files.deleteIfExists(file.toPath());
        return null;
    }

    private File cached(String repository, Library library, String name) {
        return new File(
                cache,
                (GOOGLE.equals(repository) ? "google/" : "central/") + library.directory() + name);
    }

    private Fetched downloadIfPublished(
            String repository, Library library, String name, long maxBytes) throws IOException {
        try {
            return download(repository, library, name, maxBytes);
        } catch (NotFound missing) {
            return null;
        }
    }

    private Fetched download(String repository, Library library, String name, long maxBytes)
            throws IOException {
        File file = cached(repository, library, name);
        File partial = partial(name);
        try {
            String url = base(repository) + library.directory() + name;
            try (OutputStream output = Files.newOutputStream(partial.toPath())) {
                fetch(repository, url, name, maxBytes, output);
            }
            verify(repository, url, name, partial);
            String sha256 = hex(digest(partial, "SHA-256"));
            File directory = file.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("Could not make room for " + name);
            Files.move(
                    partial.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            Files.write(
                    new File(file.getPath() + ".sha256").toPath(),
                    sha256.getBytes(StandardCharsets.US_ASCII));
            return new Fetched(file, sha256, repository);
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private byte[] checkedBytes(String repository, String path, long maxBytes) throws IOException {
        File partial = partial(path);
        try {
            String name = path.substring(path.lastIndexOf('/') + 1);
            String url = base(repository) + path;
            try (OutputStream output = Files.newOutputStream(partial.toPath())) {
                fetch(repository, url, name, maxBytes, output);
            }
            verify(repository, url, name, partial);
            return Files.readAllBytes(partial.toPath());
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    private File partial(String name) throws IOException {
        File directory = new File(cache, "partial");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not make room for " + name);
        return File.createTempFile("download-", ".partial", directory);
    }

    private void verify(String repository, String url, String name, File file) throws IOException {
        for (String[] checksum : CHECKSUMS) {
            ByteArrayOutputStream published = new ByteArrayOutputStream();
            try {
                fetch(repository, url + "." + checksum[0], name, 1024, published);
            } catch (NotFound missing) {
                continue;
            }
            String text = published.toString("US-ASCII").trim();
            String expected = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            if (!expected.matches("[0-9a-f]{" + checksum[2] + "}"))
                throw new IOException(
                        repository
                                + "'s "
                                + checksum[1]
                                + " checksum for "
                                + name
                                + " is malformed");
            if (!expected.equals(hex(digest(file, checksum[1]))))
                throw new IOException(
                        name
                                + " from "
                                + repository
                                + " does not match its published "
                                + checksum[1]
                                + " checksum, so it was not used");
            return;
        }
        throw new IOException(
                name + " on " + repository + " has no published checksum, so it was not used");
    }

    private byte[] get(String repository, String url, long maxBytes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        fetch(repository, url, url.substring(url.lastIndexOf('/') + 1), maxBytes, bytes);
        return bytes.toByteArray();
    }

    private void fetch(
            String repository, String url, String name, long maxBytes, OutputStream output)
            throws IOException {
        HttpsDownload.fetch(repository, url, name, maxBytes, output);
    }

    private String base(String repository) {
        return GOOGLE.equals(repository) ? endpoints.googleBase : endpoints.centralBase;
    }

    private static byte[] digest(File file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1; ) digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            text.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return text.toString();
    }
}
