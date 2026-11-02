package dev.smallwright.project;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectPathPolicyTest {
    static final String PACKAGE = "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    static final String JAVA =
            "java/dev/smallwright/generated/app0123456789abcdef0123456789abcdef/";
    static final String MAIN = JAVA + "MainActivity.java";
    static final String LAYOUT = "res/layout/empty.xml";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private File project;
    private ProjectStore store;

    @Before
    public void createStore() throws Exception {
        project = temporaryFolder.newFolder("project");
        store = new ProjectStore(project, PACKAGE);
        store.initialize(Map.of(MAIN, bytes("class MainActivity {}"), LAYOUT, bytes("<View/>")));
    }

    @Test
    public void everyAcceptedPathShapeIsStoredAndReadBack() throws Exception {
        List<String> accepted = cases("accept");
        Map<String, byte[]> patch = new TreeMap<>();
        for (String path : accepted) patch.put(path, bytes("content of " + path));

        int revision = store.applyPatch(0, patch);

        assertEquals(1, revision);
        for (String path : accepted) {
            assertArrayEquals(path, bytes("content of " + path), store.readFile(path));
        }
        assertEquals(accepted.size() + 2, store.readCurrentSourceBytes(1).size());
    }

    @Test
    public void everyRefusedPathShapeLeavesTheRevisionUntouched() throws Exception {
        List<String> refused = new ArrayList<>(cases("refuse"));
        refused.addAll(List.of("", "\u0000"));

        for (String path : refused) {
            assertThrows(
                    path,
                    SecurityException.class,
                    () -> store.applyPatch(0, Map.of(path, bytes("bad"))));
        }

        assertEquals(0, store.revision());
        assertEquals(2, store.readCurrentSourceBytes(0).size());
    }

    @Test
    public void nullPathIsRefused() throws Exception {
        Map<String, byte[]> patch = new java.util.HashMap<>();
        patch.put(null, bytes("bad"));

        assertThrows(SecurityException.class, () -> store.applyPatch(0, patch));
        assertEquals(0, store.revision());
    }

    @Test
    public void readsRefuseTraversalAndWrongPackagePaths() throws Exception {
        assertThrows(SecurityException.class, () -> store.readFile("assets/../HEAD"));
        assertThrows(
                SecurityException.class,
                () -> store.readFile("java/dev/smallwright/generated/counter/MainActivity.java"));
    }

    @Test
    public void symlinkedNewDirectoryTypesAreRefusedWhenTheTreeIsValidated() throws Exception {
        for (String link :
                List.of("assets/config/link.txt", JAVA + "ui/Link.java", "res/drawable/link.png")) {
            File root = temporaryFolder.newFolder();
            ProjectStore other = new ProjectStore(root, PACKAGE);
            other.initialize(Map.of(MAIN, bytes("a"), LAYOUT, bytes("<View/>")));
            Path outside = temporaryFolder.newFile().toPath();
            Path linked = root.toPath().resolve("revisions/0").resolve(link);
            Files.createDirectories(linked.getParent());
            Files.createSymbolicLink(linked, outside);

            assertThrows(link, SecurityException.class, () -> other.applyPatch(0, Map.of()));
            assertThrows(link, SecurityException.class, () -> other.readCurrentSourceBytes(0));
            assertEquals(0, other.revision());
        }
    }

    @Test
    public void symlinkedDirectoryInsideARevisionIsRefused() throws Exception {
        Path outsideDirectory = temporaryFolder.newFolder("outside").toPath();
        Files.write(outsideDirectory.resolve("secret.txt"), bytes("secret"));
        Files.createSymbolicLink(project.toPath().resolve("revisions/0/assets"), outsideDirectory);

        assertThrows(SecurityException.class, () -> store.readCurrentSourceBytes(0));
        assertThrows(SecurityException.class, () -> store.readFile("assets/secret.txt"));
        assertThrows(
                SecurityException.class,
                () -> store.applyPatch(0, Map.of("assets/new.txt", bytes("x"))));
        assertTrue(Files.notExists(outsideDirectory.resolve("new.txt")));
    }

    @Test
    public void snapshotCarriesEveryFileAndKeepsTheHarnessManifest() throws Exception {
        store.applyPatch(
                0,
                Map.of(
                        JAVA + "ui/ListScreen.java",
                        bytes("class ListScreen {}"),
                        "res/drawable/ic.xml",
                        bytes("<vector/>"),
                        "assets/data/a.txt",
                        bytes("asset"),
                        "AndroidManifest.xml",
                        bytes("<manifest project=\"owned\"/>")));
        File snapshot = temporaryFolder.newFile("snapshot.zip");

        try (ProjectStore.BuildLease ignored =
                store.beginBuild(1, snapshot, "<manifest generated=\"harness\"/>")) {
            try (ZipFile zip = new ZipFile(snapshot)) {
                List<String> names = new ArrayList<>();
                zip.stream().map(ZipEntry::getName).forEach(names::add);
                assertEquals(6, names.size());
                assertTrue(
                        names.containsAll(
                                List.of(
                                        MAIN,
                                        JAVA + "ui/ListScreen.java",
                                        LAYOUT,
                                        "res/drawable/ic.xml",
                                        "assets/data/a.txt",
                                        "AndroidManifest.xml")));
                assertArrayEquals(
                        bytes("<manifest generated=\"harness\"/>"),
                        zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes());
            }
        }
    }

    @Test
    public void theBuildersSubPackageIsNeverAProjectPath() throws Exception {
        for (String path :
                List.of(
                        JAVA + "builderlink/BuilderLink.java",
                        JAVA + "builderlink/deep/Other.java")) {
            assertThrows(
                    path,
                    SecurityException.class,
                    () -> store.applyPatch(0, Map.of(path, bytes("class Forged {}"))));
            assertTrue(path, !ProjectStore.isEditablePath(path, PACKAGE));
        }
        assertTrue(ProjectStore.isEditablePath(JAVA + "builderlinks/Mine.java", PACKAGE));
    }

    @Test
    public void snapshotAddsTheBuildersFilesAfterTheRevision() throws Exception {
        File snapshot = temporaryFolder.newFile("snapshot.zip");
        String builder = JAVA + "builderlink/BuilderLink.java";

        try (ProjectStore.BuildLease ignored =
                store.beginBuild(
                        0, snapshot, "<manifest/>", Map.of(builder, bytes("class B {}")))) {
            try (ZipFile zip = new ZipFile(snapshot)) {
                assertEquals(4, zip.size());
                assertArrayEquals(
                        bytes("class B {}"),
                        zip.getInputStream(zip.getEntry(builder)).readAllBytes());
            }
        }
        for (String outside :
                List.of(
                        MAIN,
                        "assets/x.txt",
                        JAVA + "builderlink/notes.txt",
                        "java/x/builderlink/B.java")) {
            File other = temporaryFolder.newFile();
            assertThrows(
                    outside,
                    SecurityException.class,
                    () -> store.beginBuild(0, other, "<manifest/>", Map.of(outside, bytes("x"))));
        }
    }

    @Test
    public void snapshotRefusesAForeignFileInTheRevision() throws Exception {
        Files.write(project.toPath().resolve("revisions/0/build.gradle"), bytes("plugins {}"));
        File snapshot = temporaryFolder.newFile("snapshot.zip");

        assertThrows(SecurityException.class, () -> store.beginBuild(0, snapshot, "<manifest/>"));
    }

    static List<String> cases(String verdict) throws IOException {
        File directory = new File(System.getProperty("user.dir"));
        while (!new File(directory, "testdata/project-paths.txt").isFile()) {
            directory = directory.getParentFile();
        }
        List<String> paths = new ArrayList<>();
        for (String line :
                Files.readAllLines(
                        new File(directory, "testdata/project-paths.txt").toPath(),
                        StandardCharsets.UTF_8)) {
            if (line.startsWith(verdict + "\t")) {
                paths.add(line.substring(verdict.length() + 1).replace("$JAVA", JAVA));
            }
        }
        return paths;
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
