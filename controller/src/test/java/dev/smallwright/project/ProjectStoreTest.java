package dev.smallwright.project;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ProjectStoreTest {
    private static final String MAIN = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String LAYOUT = "res/layout/empty.xml";

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ordinarySourcePatchCreatesANewCompleteRevision() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        Map<String, byte[]> initial = new HashMap<>();
        initial.put(MAIN, bytes("class MainActivity {}"));
        initial.put(LAYOUT, bytes("<View/>"));
        store.initialize(initial);

        int newRevision = store.applyPatch(0, Map.of(MAIN, bytes("class MainActivity { int n; }")));

        assertEquals(1, newRevision);
        assertEquals(1, store.revision());
        assertArrayEquals(bytes("class MainActivity { int n; }"), store.readFile(MAIN));
        assertArrayEquals(bytes("<View/>"), store.readFile(LAYOUT));
        assertArrayEquals(bytes("class MainActivity {}"), store.readFile(0, MAIN));
    }

    @Test
    public void restoreCommitsANewRevisionWithTheEarlierFilesAndKeepsHistory() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("first"), LAYOUT, bytes("<View/>")));
        Map<String, byte[]> first = store.readCurrentSourceBytes(0);
        store.applyPatch(0, Map.of("res/values/extra.xml", bytes("<resources/>")));
        store.applyPatch(1, Map.of(MAIN, bytes("second")), java.util.Set.of(LAYOUT));

        assertThrows(IllegalStateException.class, () -> store.restore(1, 0));
        assertThrows(IllegalArgumentException.class, () -> store.restore(2, 2));
        assertEquals(2, store.revision());

        int restored = store.restore(2, 0);

        assertEquals(3, restored);
        assertEquals(3, store.revision());
        Map<String, byte[]> now = store.readCurrentSourceBytes(3);
        assertEquals(first.keySet(), now.keySet());
        for (String path : first.keySet()) assertArrayEquals(first.get(path), now.get(path));
        assertArrayEquals(bytes("second"), store.readFile(2, MAIN));
        assertEquals(3, store.readRevisionBytes(1).size());
    }

    @Test
    public void traversalPatchLeavesCurrentRevisionAndOutsideFileUntouched() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));

        assertThrows(
                SecurityException.class,
                () -> store.applyPatch(0, Map.of("../escape.java", bytes("bad"))));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(MAIN));
        assertFalse(Files.exists(project.toPath().resolve("escape.java")));
    }

    @Test
    public void symlinkInExistingRevisionCannotRedirectPatchOutsideProject() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        Path outside = temporaryFolder.newFile("outside.java").toPath();
        Files.write(outside, bytes("outside"));
        Path main = project.toPath().resolve("revisions/0").resolve(MAIN);
        Files.delete(main);
        Files.createSymbolicLink(main, outside);

        assertThrows(
                SecurityException.class,
                () -> store.applyPatch(0, Map.of(MAIN, bytes("replacement"))));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("outside"), Files.readAllBytes(outside));
    }

    @Test
    public void oversizedPatchDoesNotPublishARevision() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        byte[] oversized = new byte[1024 * 1024 + 1];
        Arrays.fill(oversized, (byte) 'x');

        assertThrows(java.io.IOException.class, () -> store.applyPatch(0, Map.of(MAIN, oversized)));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(MAIN));
    }

    @Test
    public void absolutePathCannotBePatched() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));

        assertThrows(
                SecurityException.class,
                () -> store.applyPatch(0, Map.of("/tmp/escape.java", bytes("bad"))));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(MAIN));
    }

    @Test
    public void archiveStyleTraversalCannotBecomeASnapshotEntry() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));

        assertThrows(
                SecurityException.class,
                () ->
                        store.applyPatch(
                                0, Map.of("res/layout/../../AndroidManifest.xml", bytes("bad"))));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("<View/>"), store.readFile(LAYOUT));
    }

    @Test
    public void staleEditorRevisionCannotOverwriteNewerSource() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        store.applyPatch(0, Map.of(MAIN, bytes("first edit")));

        assertThrows(
                IllegalStateException.class,
                () -> store.applyPatch(0, Map.of(MAIN, bytes("stale edit"))));

        assertEquals(1, store.revision());
        assertArrayEquals(bytes("first edit"), store.readFile(MAIN));
    }

    @Test
    public void recoverySnapshotReadsAllExactSourceBytesAtOneRevision() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("recovery-project"));
        String extra = "java/dev/smallwright/generated/counter/Extra.java";
        byte[] unusual = {(byte) 0xc3, (byte) 0x28};
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>"), extra, unusual));

        Map<String, byte[]> snapshot = store.readCurrentSourceBytes(0);
        assertEquals(3, snapshot.size());
        assertArrayEquals(unusual, snapshot.get(extra));
        assertArrayEquals(bytes("original"), snapshot.get(MAIN));
        assertThrows(IllegalStateException.class, () -> store.readCurrentSourceBytes(1));

        store.applyPatch(0, Map.of(MAIN, bytes("later")));
        assertArrayEquals(bytes("original"), snapshot.get(MAIN));
        assertThrows(IllegalStateException.class, () -> store.readCurrentSourceBytes(0));
    }

    @Test
    public void totalSourceBytesMustFitWorkerSnapshotLimit() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        byte[] file = new byte[1024 * 1024];
        Arrays.fill(file, (byte) 'x');
        Map<String, byte[]> seventeenMebibytes = new HashMap<>();
        seventeenMebibytes.put(MAIN, file);
        for (int i = 0; i < 16; i++) seventeenMebibytes.put("assets/blob" + i + ".bin", file);

        assertThrows(java.io.IOException.class, () -> store.applyPatch(0, seventeenMebibytes));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(MAIN));
    }

    @Test
    public void buildLeaseSnapshotsOneRevisionAndRejectsMutationUntilClosed() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        java.io.File snapshot = temporaryFolder.newFile("snapshot.zip");

        try (ProjectStore.BuildLease lease =
                store.beginBuild(
                        0, snapshot, "<manifest package=\"dev.smallwright.generated.counter\"/>")) {
            assertEquals(0, lease.revision());
            assertThrows(
                    IllegalStateException.class,
                    () -> store.applyPatch(0, Map.of(MAIN, bytes("later"))));
            try (ZipFile archive = new ZipFile(snapshot)) {
                assertArrayEquals(
                        bytes("original"),
                        archive.getInputStream(archive.getEntry(MAIN)).readAllBytes());
                assertArrayEquals(
                        bytes("<View/>"),
                        archive.getInputStream(archive.getEntry(LAYOUT)).readAllBytes());
                assertEquals(3, archive.size());
            }
        }

        assertEquals(1, store.applyPatch(0, Map.of(MAIN, bytes("later"))));
    }

    @Test
    public void symlinkedSourceCannotBeReadThroughProjectStore() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        Path outside = temporaryFolder.newFile("outside.txt").toPath();
        Files.write(outside, bytes("outside"));
        Path main = project.toPath().resolve("revisions/0").resolve(MAIN);
        Files.delete(main);
        Files.createSymbolicLink(main, outside);

        assertThrows(SecurityException.class, () -> store.readFile(MAIN));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("outside"), Files.readAllBytes(outside));
    }

    @Test
    public void interruptedPublishOrphanDoesNotBlockNextPatchOrLosePriorRevision()
            throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        Path orphan = project.toPath().resolve("revisions/1").resolve(MAIN);
        Files.createDirectories(orphan.getParent());
        Files.write(orphan, bytes("interrupted edit"));

        int revision = store.applyPatch(0, Map.of(MAIN, bytes("new edit")));

        assertEquals(2, revision);
        assertEquals(2, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(0, MAIN));
        assertArrayEquals(bytes("new edit"), store.readFile(MAIN));
        assertArrayEquals(bytes("interrupted edit"), Files.readAllBytes(orphan));
    }

    @Test
    public void secondStoreInstanceCannotMutateDuringFirstInstanceBuild() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore first = new ProjectStore(project);
        ProjectStore second = new ProjectStore(project);
        first.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        java.io.File snapshot = temporaryFolder.newFile("snapshot.zip");

        try (ProjectStore.BuildLease ignored =
                first.beginBuild(
                        0, snapshot, "<manifest package=\"dev.smallwright.generated.counter\"/>")) {
            assertThrows(
                    IllegalStateException.class,
                    () -> second.applyPatch(0, Map.of(MAIN, bytes("concurrent edit"))));
        }

        assertEquals(1, second.applyPatch(0, Map.of(MAIN, bytes("later edit"))));
    }

    @Test
    public void maximumRevisionCannotWrapToNegativeNumber() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        Path last = project.toPath().resolve("revisions/" + Integer.MAX_VALUE);
        Path main = last.resolve(MAIN);
        Path layout = last.resolve(LAYOUT);
        Files.createDirectories(main.getParent());
        Files.createDirectories(layout.getParent());
        Files.write(main, bytes("last edit"));
        Files.write(layout, bytes("<View/>"));
        Files.write(project.toPath().resolve("HEAD"), bytes(Integer.toString(Integer.MAX_VALUE)));

        assertThrows(
                java.io.IOException.class,
                () -> store.applyPatch(Integer.MAX_VALUE, Map.of(MAIN, bytes("overflow"))));

        assertEquals(Integer.MAX_VALUE, store.revision());
        assertArrayEquals(bytes("last edit"), store.readFile(MAIN));
    }

    @Test
    public void stagedRecoverySnapshotDoesNotChangeHeadAndPromotesExactBytesIdempotently()
            throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<TextView/>")));

        assertEquals(0, store.revision());
        assertArrayEquals(bytes("original"), store.readFile(MAIN));
        assertArrayEquals(bytes("recovered"), store.readFile(staged, MAIN));
        assertEquals(staged, store.promoteStagedSnapshot(0, staged));
        assertEquals(staged, store.promoteStagedSnapshot(0, staged));
        assertArrayEquals(bytes("recovered"), store.readFile(MAIN));
        assertArrayEquals(bytes("original"), store.readFile(0, MAIN));
    }

    @Test
    public void stagingRejectsIncompleteOversizedAndStaleSnapshots() throws Exception {
        ProjectStore store = new ProjectStore(temporaryFolder.newFolder("project"));
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        assertThrows(
                java.io.IOException.class,
                () -> store.stageExactSnapshot(0, Map.of(MAIN, bytes("alone"))));
        assertThrows(
                java.io.IOException.class,
                () ->
                        store.stageExactSnapshot(
                                0,
                                Map.of(
                                        MAIN,
                                        bytes("no layout"),
                                        "AndroidManifest.xml",
                                        bytes("<manifest/>"))));
        assertThrows(
                java.io.IOException.class,
                () ->
                        store.stageExactSnapshot(
                                0,
                                Map.of(
                                        "java/dev/smallwright/generated/counter/Other.java",
                                        bytes("other"),
                                        LAYOUT,
                                        bytes("<View/>"))));
        assertThrows(
                java.io.IOException.class,
                () ->
                        store.stageExactSnapshot(
                                0,
                                Map.of(
                                        MAIN,
                                        bytes("main"),
                                        "res/layout/other.xml",
                                        bytes("<View/>"))));
        assertThrows(
                SecurityException.class,
                () ->
                        store.stageExactSnapshot(
                                0, Map.of(MAIN, bytes("good"), "../bad.xml", bytes("bad"))));
        assertThrows(
                java.io.IOException.class,
                () ->
                        store.stageExactSnapshot(
                                0,
                                Map.of(MAIN, new byte[1024 * 1024 + 1], LAYOUT, bytes("<View/>"))));
        byte[] mebibyte = new byte[1024 * 1024];
        Map<String, byte[]> seventeenMebibytes = new HashMap<>();
        seventeenMebibytes.put(MAIN, mebibyte);
        seventeenMebibytes.put(LAYOUT, mebibyte);
        for (int i = 0; i < 15; i++) seventeenMebibytes.put("assets/blob" + i + ".bin", mebibyte);
        seventeenMebibytes.put("assets/last.bin", new byte[1]);
        assertThrows(
                java.io.IOException.class, () -> store.stageExactSnapshot(0, seventeenMebibytes));
        store.applyPatch(0, Map.of(MAIN, bytes("newer")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        store.stageExactSnapshot(
                                0, Map.of(MAIN, bytes("stale"), LAYOUT, bytes("<View/>"))));
        assertEquals(1, store.revision());
    }

    @Test
    public void promotionRejectsChangedStagingAndStaleHead() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<View/>")));
        Files.write(
                project.toPath().resolve("revisions/" + staged).resolve(MAIN), bytes("tampered"));
        assertThrows(SecurityException.class, () -> store.promoteStagedSnapshot(0, staged));
        assertEquals(0, store.revision());

        int next =
                store.stageExactSnapshot(0, Map.of(MAIN, bytes("next"), LAYOUT, bytes("<View/>")));
        store.applyPatch(0, Map.of(MAIN, bytes("user edit")));
        assertThrows(IllegalStateException.class, () -> store.promoteStagedSnapshot(0, next));
        assertArrayEquals(bytes("user edit"), store.readFile(MAIN));
    }

    @Test
    public void promotionRejectsSymlinkInStagedRevision() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<View/>")));
        Path outside = temporaryFolder.newFile("outside.java").toPath();
        Files.write(outside, bytes("outside"));
        Path main = project.toPath().resolve("revisions/" + staged).resolve(MAIN);
        Files.delete(main);
        Files.createSymbolicLink(main, outside);

        assertThrows(SecurityException.class, () -> store.promoteStagedSnapshot(0, staged));
        assertEquals(0, store.revision());
        assertArrayEquals(bytes("outside"), Files.readAllBytes(outside));
    }

    @Test
    public void idempotentPromotionStillChecksCommittedBytes() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<View/>")));
        store.promoteStagedSnapshot(0, staged);
        Files.write(
                project.toPath().resolve("revisions/" + staged).resolve(MAIN), bytes("changed"));

        assertThrows(SecurityException.class, () -> store.promoteStagedSnapshot(0, staged));
        assertEquals(staged, store.revision());
    }

    @Test
    public void validatedStageDigestRejectsChangesWithoutPromoting() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<View/>")));
        byte[] digest = store.validatedStagedSnapshotDigest(0, staged);
        assertEquals(32, digest.length);
        assertEquals(0, store.revision());
        Files.write(
                project.toPath().resolve("revisions/" + staged).resolve(MAIN), bytes("tampered"));
        assertThrows(SecurityException.class, () -> store.validatedStagedSnapshotDigest(0, staged));
        assertEquals(0, store.revision());
    }

    @Test
    public void promotionRejectsOversizedOrMalformedStageRecord() throws Exception {
        java.io.File project = temporaryFolder.newFolder("project");
        ProjectStore store = new ProjectStore(project);
        store.initialize(Map.of(MAIN, bytes("original"), LAYOUT, bytes("<View/>")));
        int staged =
                store.stageExactSnapshot(
                        0, Map.of(MAIN, bytes("recovered"), LAYOUT, bytes("<View/>")));
        Path record = project.toPath().resolve("recovery-stages/" + staged);
        byte[] valid = Files.readAllBytes(record);
        byte[] oversized = Arrays.copyOf(valid, 1024);
        Files.write(record, oversized);
        assertThrows(SecurityException.class, () -> store.promoteStagedSnapshot(0, staged));
        assertEquals(0, store.revision());

        Files.write(record, bytes("0\n" + "z".repeat(64) + "\n"));
        assertThrows(SecurityException.class, () -> store.promoteStagedSnapshot(0, staged));
        assertEquals(0, store.revision());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
