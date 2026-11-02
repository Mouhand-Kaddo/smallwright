package dev.smallwright.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class SnapshotPolicyTest {
    @Test
    public void acceptsOnlyFixedJavaXmlSourcePaths() {
        assertTrue(BuildService.isAllowedSnapshotPath("AndroidManifest.xml"));
        assertTrue(
                BuildService.isAllowedSnapshotPath(
                        "java/dev/smallwright/generated/counter/MainActivity.java"));
        assertTrue(BuildService.isAllowedSnapshotPath("res/values/strings.xml"));
        assertFalse(BuildService.isAllowedSnapshotPath("../AndroidManifest.xml"));
        assertFalse(BuildService.isAllowedSnapshotPath("build.gradle"));
        assertTrue(BuildService.isAllowedSnapshotPath("res/raw/tool.jar"));
    }

    @Test
    public void followsTheSharedPathCasesForEveryProjectFileShape() throws Exception {
        String packageName = "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
        String javaDirectory =
                "java/dev/smallwright/generated/app0123456789abcdef0123456789abcdef/";

        for (String path : cases("accept", javaDirectory)) {
            assertTrue(path, BuildService.isAllowedSnapshotPath(path, packageName));
        }
        for (String path : cases("refuse", javaDirectory)) {
            assertFalse(path, BuildService.isAllowedSnapshotPath(path, packageName));
        }
    }

    @Test
    public void refusesWhatTheProjectPackageDoesNotOwn() {
        assertFalse(BuildService.isAllowedSnapshotPath("assets/a.txt", "dev.smallwright.other"));
        assertFalse(BuildService.isAllowedSnapshotPath(null, "dev.smallwright.generated.counter"));
        assertFalse(BuildService.isAllowedSnapshotPath("", "dev.smallwright.generated.counter"));
        assertFalse(
                BuildService.isAllowedSnapshotPath("\u0000", "dev.smallwright.generated.counter"));
    }

    private static List<String> cases(String verdict, String javaDirectory) throws IOException {
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
                paths.add(line.substring(verdict.length() + 1).replace("$JAVA", javaDirectory));
            }
        }
        return paths;
    }

    @Test
    public void bindsNewProjectJavaPathsToTheValidatedManifestPackage() throws Exception {
        String packageName = "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
        assertEquals(
                packageName,
                BuildService.snapshotPackage(
                        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                                + packageName
                                + "\"><application/></manifest>"));
        assertTrue(
                BuildService.isAllowedSnapshotPath(
                        "java/dev/smallwright/generated/"
                                + "app0123456789abcdef0123456789abcdef/MainActivity.java",
                        packageName));
        assertFalse(
                BuildService.isAllowedSnapshotPath(
                        "java/dev/smallwright/generated/counter/MainActivity.java", packageName));
        assertFalse(
                BuildService.isAllowedSnapshotPath(
                        "java/dev/smallwright/generated/"
                                + "appffffffffffffffffffffffffffffffff/MainActivity.java",
                        packageName));
        assertThrows(
                IOException.class,
                () ->
                        BuildService.snapshotPackage(
                                "<manifest package=\"dev.smallwright.generated.other\"/>"));
        assertThrows(
                IOException.class,
                () ->
                        BuildService.snapshotPackage(
                                "<!-- package=\""
                                        + packageName
                                        + "\" --><manifest package=\"dev.bad\"/>"));
    }

    @Test
    public void anyPermissionIsAcceptedButSharedUserIdIsStillRefused() throws Exception {
        String packageName = "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
        String head =
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                        + packageName
                        + "\"";
        assertEquals(
                packageName,
                BuildService.snapshotPackage(
                        head
                                + "><uses-permission android:name=\"android.permission.INTERNET\"/>"
                                + "<application/></manifest>"));
        assertThrows(
                IOException.class,
                () ->
                        BuildService.snapshotPackage(
                                head + " android:sharedUserId=\"x\"><application/></manifest>"));
    }
}
