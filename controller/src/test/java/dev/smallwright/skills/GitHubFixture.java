package dev.smallwright.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.smallwright.libraries.MavenFixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** A local stand-in for GitHub's API and raw file host serving one repository, owner/repo. */
public final class GitHubFixture implements AutoCloseable {
    public static final String COMMIT = "49dd158d1076134a641b33efb035946536778336";

    private final MavenFixture server;

    public GitHubFixture() throws IOException {
        server = new MavenFixture();
    }

    public GitHubSkills skills() {
        return new GitHubSkills(new GitHubSkills.Endpoints(server.base("api"), server.base("raw")));
    }

    /** A SKILL.md with this name and description. */
    public static String skill(String name, String description, boolean personOnly) {
        return "---\nname: "
                + name
                + "\ndescription: "
                + description
                + (personOnly ? "\ndisable-model-invocation: true" : "")
                + "\n---\nINSTRUCTIONS OF "
                + name
                + "\n";
    }

    /** Serves these files, by path, at the default branch of owner/repo. */
    public void publish(Map<String, String> files) {
        server.putRaw(
                "api", "repos/owner/repo/commits/HEAD", COMMIT.getBytes(StandardCharsets.US_ASCII));
        JsonArray tree = new JsonArray();
        for (Map.Entry<String, String> file : files.entrySet()) {
            byte[] bytes = file.getValue().getBytes(StandardCharsets.UTF_8);
            JsonObject blob = new JsonObject();
            blob.addProperty("path", file.getKey());
            blob.addProperty("type", "blob");
            blob.addProperty("sha", GitHubSkills.blobSha(bytes));
            blob.addProperty("size", bytes.length);
            tree.add(blob);
            server.putRaw("raw", "owner/repo/" + COMMIT + "/" + file.getKey(), bytes);
        }
        JsonObject root = new JsonObject();
        root.addProperty("sha", COMMIT);
        root.add("tree", tree);
        root.addProperty("truncated", false);
        server.putRaw(
                "api",
                "repos/owner/repo/git/trees/" + COMMIT + "?recursive=1",
                root.toString().getBytes(StandardCharsets.UTF_8));
    }

    public List<String> requests() {
        return server.requests();
    }

    @Override
    public void close() {
        server.close();
    }
}
