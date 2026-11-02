package dev.smallwright.libraries;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A local Maven server bound to 127.0.0.1 with three repositories: {@code google}, {@code central}
 * and {@code other}. Files get SHA-512, SHA-256 and SHA-1 checksum files unless a test replaces
 * them.
 */
public final class MavenFixture implements AutoCloseable {
    private final ServerSocket server;
    private final Thread acceptor;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, String> redirects = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    public MavenFixture() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptor =
                new Thread(
                        () -> {
                            while (!server.isClosed()) {
                                try (Socket client = server.accept()) {
                                    answer(client);
                                } catch (IOException closed) {
                                    return;
                                }
                            }
                        });
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void answer(Socket client) throws IOException {
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        String line = reader.readLine();
        if (line == null) return;
        for (String header = reader.readLine();
                header != null && !header.isEmpty();
                header = reader.readLine()) {}
        String target = line.split(" ")[1];
        requests.add(target);
        String path = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        String redirect = redirects.get(path);
        byte[] body = files.get(target);
        OutputStream output = client.getOutputStream();
        String head;
        Integer status = statuses.get(target);
        if (status != null) {
            head = "HTTP/1.1 " + status + " Refused\r\n";
            body = null;
        } else if (redirect != null) head = "HTTP/1.1 302 Found\r\nLocation: " + redirect + "\r\n";
        else if (body == null) head = "HTTP/1.1 404 Not Found\r\n";
        else head = "HTTP/1.1 200 OK\r\n";
        byte[] content = redirect == null && body != null ? body : new byte[0];
        output.write(
                (head + "Content-Length: " + content.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        output.write(content);
        output.flush();
    }

    public List<String> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    public String base(String repository) {
        return "http://127.0.0.1:" + server.getLocalPort() + "/" + repository + "/";
    }

    public MavenRepositories repositories(java.io.File cache) {
        return new MavenRepositories(
                cache,
                new MavenRepositories.Endpoints(
                        base("google"), base("central"), base("search") + "select"));
    }

    /** Serves bytes with checksum files beside them. */
    public void put(String repository, String path, byte[] bytes) {
        String full = "/" + repository + "/" + path;
        files.put(full, bytes);
        files.put(full + ".sha512", hex("SHA-512", bytes).getBytes(StandardCharsets.US_ASCII));
        files.put(full + ".sha256", hex("SHA-256", bytes).getBytes(StandardCharsets.US_ASCII));
        files.put(
                full + ".sha1",
                (hex("SHA-1", bytes) + "  " + path.substring(path.lastIndexOf('/') + 1))
                        .getBytes(StandardCharsets.US_ASCII));
    }

    public void putRaw(String repository, String path, byte[] bytes) {
        files.put("/" + repository + "/" + path, bytes);
    }

    public void remove(String repository, String path) {
        files.remove("/" + repository + "/" + path);
    }

    /** Answers {@code path}, with its query, with this HTTP status and no body. */
    public void status(String repository, String path, int code) {
        statuses.put("/" + repository + "/" + path, code);
    }

    public void redirect(String repository, String path, String location) {
        redirects.put("/" + repository + "/" + path, location);
    }

    /**
     * Publishes a POM and, unless packaging is pom, a JAR holding one marker entry or an AAR
     * holding a manifest that declares nothing.
     */
    public void library(String repository, String coordinates, String packaging, String body) {
        if ("aar".equals(packaging)) {
            aar(repository, coordinates, body, "", Map.of());
            return;
        }
        String[] parts = coordinates.split(":");
        put(
                repository,
                directory(parts) + parts[1] + "-" + parts[2] + ".pom",
                pom(parts[0], parts[1], parts[2], packaging, body)
                        .getBytes(StandardCharsets.UTF_8));
        if (!"pom".equals(packaging))
            put(
                    repository,
                    directory(parts) + parts[1] + "-" + parts[2] + ".jar",
                    archive(coordinates));
    }

    /**
     * Publishes an Android library: its POM and an AAR whose manifest, in package {@code
     * androidx.example.<artifact>}, holds {@code manifestBody}, with {@code entries} beside it.
     */
    public void aar(
            String repository,
            String coordinates,
            String body,
            String manifestBody,
            Map<String, byte[]> entries) {
        String[] parts = coordinates.split(":");
        String name = parts[1] + "-" + parts[2];
        put(
                repository,
                directory(parts) + name + ".pom",
                pom(parts[0], parts[1], parts[2], "aar", body).getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> files = new java.util.LinkedHashMap<>();
        files.put("AndroidManifest.xml", manifest(parts[1], manifestBody));
        files.putAll(entries);
        put(repository, directory(parts) + name + ".aar", zip(files));
    }

    public static byte[] manifest(String artifact, String body) {
        return ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " xmlns:tools=\"http://schemas.android.com/tools\""
                        + " package=\"androidx.example."
                        + artifact.replaceAll("[^a-z0-9]", "")
                        + "\"><uses-sdk android:minSdkVersion=\"21\"/>"
                        + body
                        + "</manifest>\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] zip(Map<String, byte[]> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
        return bytes.toByteArray();
    }

    private static String directory(String[] parts) {
        return parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/";
    }

    public void jar(String repository, String coordinates, String body) {
        library(repository, coordinates, "jar", body);
    }

    public static String pom(
            String group, String artifact, String version, String packaging, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + (group == null ? "" : "  <groupId>" + group + "</groupId>\n")
                + "  <artifactId>"
                + artifact
                + "</artifactId>\n"
                + (version == null ? "" : "  <version>" + version + "</version>\n")
                + "  <packaging>"
                + packaging
                + "</packaging>\n"
                + body
                + "</project>\n";
    }

    public static String dependency(String coordinates, String extra) {
        String[] parts = coordinates.split(":");
        return "<dependency><groupId>"
                + parts[0]
                + "</groupId><artifactId>"
                + parts[1]
                + "</artifactId>"
                + (parts.length > 2 ? "<version>" + parts[2] + "</version>" : "")
                + extra
                + "</dependency>";
    }

    public static String dependencies(String... dependencies) {
        return "<dependencies>" + String.join("", dependencies) + "</dependencies>\n";
    }

    public static byte[] archive(String marker) {
        return zip(Map.of("marker.txt", marker.getBytes(StandardCharsets.UTF_8)));
    }

    public static String hex(String algorithm, byte[] bytes) {
        try {
            StringBuilder text = new StringBuilder();
            for (byte value : MessageDigest.getInstance(algorithm).digest(bytes))
                text.append(String.format("%02x", value));
            return text.toString();
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public void close() {
        try {
            server.close();
            acceptor.join(5000);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
