package dev.smallwright.project;

import static dev.smallwright.project.ProjectPathPolicyTest.JAVA;
import static dev.smallwright.project.ProjectPathPolicyTest.LAYOUT;
import static dev.smallwright.project.ProjectPathPolicyTest.MAIN;
import static dev.smallwright.project.ProjectPathPolicyTest.PACKAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectLimitsTest {
    private static final int MEBIBYTE = 1024 * 1024;

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private ProjectStore store;

    @Before
    public void createStore() throws Exception {
        File project = temporaryFolder.newFolder("project");
        store = new ProjectStore(project, PACKAGE);
        store.initialize(Map.of(MAIN, filled(10), LAYOUT, filled(10)));
    }

    @Test
    public void aFileOfExactlyTheLimitIsStoredAndOneByteOverIsRefused() throws Exception {
        assertEquals(1, store.applyPatch(0, Map.of("assets/exact.bin", filled(MEBIBYTE))));

        assertThrows(
                IOException.class,
                () -> store.applyPatch(1, Map.of("assets/over.bin", filled(MEBIBYTE + 1))));
        assertEquals(1, store.revision());
    }

    @Test
    public void fiveHundredFilesAreStoredAndFiveHundredAndOneAreRefused() throws Exception {
        Map<String, byte[]> atLimit = new HashMap<>();
        for (int i = 0; i < 498; i++) atLimit.put(JAVA + "pkg/C" + i + ".java", filled(10));
        assertEquals(1, store.applyPatch(0, atLimit));
        assertEquals(500, store.readCurrentSourceBytes(1).size());

        assertThrows(
                IOException.class,
                () -> store.applyPatch(1, Map.of("assets/one-more.txt", filled(1))));
        assertEquals(1, store.revision());
    }

    @Test
    public void aProjectOfExactlySixteenMebibytesIsStoredAndOneByteOverIsRefused()
            throws Exception {
        Map<String, byte[]> atLimit = new HashMap<>();
        for (int i = 0; i < 15; i++) atLimit.put("assets/blob" + i + ".bin", filled(MEBIBYTE));
        atLimit.put(MAIN, filled(MEBIBYTE / 2));
        atLimit.put(LAYOUT, filled(MEBIBYTE / 2));
        assertEquals(1, store.applyPatch(0, atLimit));

        assertThrows(
                IOException.class,
                () -> store.applyPatch(1, Map.of(LAYOUT, filled(MEBIBYTE / 2 + 1))));
        assertEquals(1, store.revision());
    }

    @Test
    public void aSnapshotAtTheLimitsIsWrittenForTheWorker() throws Exception {
        Map<String, byte[]> atLimit = new HashMap<>();
        for (int i = 0; i < 15; i++) atLimit.put("assets/blob" + i + ".bin", filled(MEBIBYTE));
        for (int i = 0; i < 483; i++) atLimit.put(JAVA + "pkg/C" + i + ".java", filled(1));
        atLimit.put(MAIN, filled(MEBIBYTE / 2));
        atLimit.put(LAYOUT, filled(MEBIBYTE / 2 - 483));
        assertEquals(1, store.applyPatch(0, atLimit));
        File snapshot = temporaryFolder.newFile("snapshot.zip");

        try (ProjectStore.BuildLease ignored = store.beginBuild(1, snapshot, "<manifest/>")) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(snapshot)) {
                assertEquals(501, zip.size());
            }
        }
    }

    private static byte[] filled(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'x');
        return bytes;
    }
}
