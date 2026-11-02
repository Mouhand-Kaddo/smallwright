package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectSourceManifestTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ProjectIdentity project;
    private ProjectSourceStore source;

    private void newProject() throws Exception {
        project =
                new ProjectRegistry(new File(temporaryFolder.newFolder("files"), "projects"))
                        .create("Manifest");
        source = new ProjectSourceStore(project);
        source.initialize();
    }

    private String snapshotManifest(int revision, int versionCode) throws Exception {
        return snapshotManifest(revision, versionCode, Map.of());
    }

    private String snapshotManifest(int revision, int versionCode, Map<String, String> libraries)
            throws Exception {
        File archive = temporaryFolder.newFile();
        try (dev.smallwright.project.ProjectStore.BuildLease ignored =
                        source.beginBuild(revision, archive, versionCode, libraries);
                ZipFile zip = new ZipFile(archive)) {
            return new String(
                    zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
        }
    }

    private int writeManifest(int revision, String manifest) throws Exception {
        return source.commit(
                revision,
                Map.of("AndroidManifest.xml", manifest.getBytes(StandardCharsets.UTF_8)),
                Set.of());
    }

    @Test
    public void newProjectsHoldTheGeneratedManifestAsAFile() throws Exception {
        newProject();
        String stored =
                new String(
                        source.readRecoverySources(0).get("AndroidManifest.xml"),
                        StandardCharsets.UTF_8);
        assertEquals(ProjectSourceStore.exportManifest(project.packageName()), stored);
        assertTrue(source.isPristineDefaultSnapshot());
        assertFalse(source.readCurrentFiles(0).containsKey("AndroidManifest.xml"));
    }

    @Test
    public void aBuildUsesTheProjectsManifestWithTheVersionForced() throws Exception {
        newProject();
        String internet =
                ProjectSourceStore.exportManifest(project.packageName())
                        .replace(
                                "<application",
                                "<uses-permission android:name=\"android.permission.INTERNET\"/><application")
                        .replace("android:versionCode=\"1\"", "android:versionCode=\"500\"");
        int revision = writeManifest(0, internet);
        String built = snapshotManifest(revision, 9);
        assertTrue(built, built.contains("android.permission.INTERNET"));
        assertTrue(built, built.contains("android:versionCode=\"9\""));
        assertTrue(built, built.contains("android:minSdkVersion=\"31\""));
    }

    @Test
    public void aRefusedManifestStopsTheBuildBeforeASnapshotExists() throws Exception {
        newProject();
        int revision =
                writeManifest(
                        0,
                        ProjectSourceStore.exportManifest(project.packageName())
                                .replace(
                                        "<application",
                                        "<application android:debuggable=\"true\""));
        assertThrows(AppManifestPolicy.Refused.class, () -> snapshotManifest(revision, 1));
    }

    @Test
    public void aBuildMergesTheAndroidLibrariesManifestEntries() throws Exception {
        newProject();
        String work =
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"androidx.work\"><application><service"
                        + " android:name=\".impl.background.systemjob.SystemJobService\""
                        + " android:exported=\"true\"/></application></manifest>";
        Map<String, String> libraries = Map.of("androidx.work:work-runtime:2.10.0", work);
        String built = snapshotManifest(0, 2, libraries);
        assertTrue(
                built, built.contains("androidx.work.impl.background.systemjob.SystemJobService"));
        assertTrue(built, built.contains("android:versionCode=\"2\""));
        int old = source.commit(0, Map.of(), Set.of("AndroidManifest.xml"));
        built = snapshotManifest(old, 3, libraries);
        assertTrue(
                built, built.contains("androidx.work.impl.background.systemjob.SystemJobService"));
        String foreign =
                work.replace(
                        "<application>",
                        "<permission android:name=\"com.bank.READ\"/><application>");
        assertThrows(
                AppManifestPolicy.Refused.class,
                () -> snapshotManifest(old, 4, Map.of("org.example:bad:1", foreign)));
    }

    @Test
    public void aProjectWithoutAManifestFileStillBuildsWithTheGeneratedOne() throws Exception {
        newProject();
        int revision = source.commit(0, Map.of(), Set.of("AndroidManifest.xml"));
        String built = snapshotManifest(revision, 3);
        assertTrue(built, built.contains("package=\"" + project.packageName() + "\""));
        assertTrue(built, built.contains("android:versionCode=\"3\""));
        assertEquals(
                ProjectSourceStore.exportManifest(project.packageName()),
                source.generatedManifest());
    }

    @Test
    public void readingTheEditorSourceSurvivesADeletedMainActivity() throws Exception {
        newProject();
        int revision = source.commit(0, Map.of(), Set.of(source.mainPath()));
        assertEquals(1, revision);
        assertEquals("", source.readSource());
    }
}
