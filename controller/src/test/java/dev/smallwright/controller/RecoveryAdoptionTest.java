package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class RecoveryAdoptionTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";

    @Test
    public void pristineImportStagesBeforeSavingSignerAndResumesAfterCrash() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        File marker = new File(temp.getRoot(), "adoption.marker");
        RecoveryAdoption adoption =
                new RecoveryAdoption(marker, project, signer, absent(), () -> {}, floor);
        signer.failAfterSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        assertTrue(marker.isFile());
        assertEquals(0, project.revision);
        assertEquals(1, floor.next);
        signer.failAfterSave = false;
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.resume());
        assertEquals(1, project.revision);
        assertEquals(8, floor.next);
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.resume());
        assertEquals(1, signer.saves);
    }

    @Test
    public void crashBeforeSignerSaveRequiresSameBundleReimport() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "marker"),
                        project,
                        signer,
                        absent(),
                        () -> {},
                        floor);
        signer.failBeforeSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        assertEquals(RecoveryAdoption.Status.PENDING_REIMPORT, adoption.resume());
        assertEquals(0, project.revision);
        assertThrows(
                SecurityException.class,
                () ->
                        adoption.adopt(
                                RecoveryBundle.create(
                                        3, 9, bundle.sources(), bundle.signer(), null)));
        signer.failBeforeSave = false;
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.adopt(bundle));
        assertEquals(8, floor.next);
    }

    @Test
    public void orphanedWrappingKeyRequiresMatchingPendingBundle() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "orphan.marker"),
                        project,
                        signer,
                        absent(),
                        () -> {},
                        floor);
        signer.failBeforeSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        signer.failBeforeSave = false;
        signer.orphanedKey = true;
        assertEquals(RecoveryAdoption.Status.PENDING_REIMPORT, adoption.resume());
        assertThrows(
                SecurityException.class,
                () ->
                        adoption.adopt(
                                RecoveryBundle.create(
                                        3, 9, bundle.sources(), bundle.signer(), null)));
        assertEquals(0, signer.orphanRecoveries);
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.adopt(bundle));
        assertEquals(1, signer.orphanRecoveries);
        assertEquals(0, signer.saves);
        assertEquals(1, project.revision);
        assertEquals(8, floor.next);
    }

    @Test
    public void unrelatedSignerReadFailureCannotEnterOrphanRecovery() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "corrupt.marker"),
                        project,
                        signer,
                        absent(),
                        () -> {},
                        new FakeFloor());
        signer.failBeforeSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        signer.failBeforeSave = false;
        signer.corrupt = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        assertEquals(0, signer.orphanRecoveries);
        assertEquals(0, project.revision);
    }

    @Test
    public void editedOrNonfreshProjectNeverImports() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "marker"),
                        project,
                        signer,
                        absent(),
                        () -> {},
                        floor);
        project.pristine = false;
        assertThrows(SecurityException.class, () -> adoption.adopt(bundle));
        assertEquals(0, project.stages);
        project.pristine = true;
        floor.next = 2;
        assertThrows(SecurityException.class, () -> adoption.adopt(bundle));
        assertEquals(0, project.stages);
    }

    @Test
    public void installedCompatibleAppRaisesRecoveredVersionFloor() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeFloor floor = new FakeFloor();
        FakeInstalledApp installed = new FakeInstalledApp();
        installed.certificate = bundle.signer().certificate().getEncoded();
        installed.version = 12;
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "installed.marker"),
                        project,
                        new FakeSigner(),
                        installed,
                        () -> {},
                        floor);
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.adopt(bundle));
        assertEquals(13, floor.next);
        assertEquals(1, project.revision);
    }

    @Test
    public void unrelatedInstalledSignerIsRejectedBeforeStaging() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeInstalledApp installed = new FakeInstalledApp();
        installed.certificate = PortableSigningIdentity.create().certificate().getEncoded();
        installed.version = 9;
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "wrong.marker"),
                        project,
                        new FakeSigner(),
                        installed,
                        () -> {},
                        new FakeFloor());
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        assertEquals(0, project.stages);
    }

    @Test
    public void installedAppChangeDuringPendingImportFailsClosed() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        FakeInstalledApp installed = new FakeInstalledApp();
        installed.certificate = bundle.signer().certificate().getEncoded();
        installed.version = 12;
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "changed.marker"),
                        project,
                        signer,
                        installed,
                        () -> {},
                        floor);
        signer.failAfterSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        signer.failAfterSave = false;
        installed.certificate = PortableSigningIdentity.create().certificate().getEncoded();
        assertThrows(GeneralSecurityException.class, adoption::resume);
        assertEquals(0, project.revision);
        assertEquals(1, floor.next);
    }

    @Test
    public void installedVersionIncreaseDuringPendingImportRaisesFloor() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        FakeInstalledApp installed = new FakeInstalledApp();
        installed.certificate = bundle.signer().certificate().getEncoded();
        installed.version = 12;
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "advanced.marker"),
                        project,
                        signer,
                        installed,
                        () -> {},
                        floor);
        signer.failAfterSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        signer.failAfterSave = false;
        installed.version = 15;
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.resume());
        assertEquals(16, floor.next);
    }

    @Test
    public void changedStageOrSignerFailsClosed() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "marker"),
                        project,
                        signer,
                        absent(),
                        () -> {},
                        new FakeFloor());
        signer.failAfterSave = true;
        assertThrows(GeneralSecurityException.class, () -> adoption.adopt(bundle));
        project.stageDigest = new byte[32];
        assertThrows(SecurityException.class, adoption::resume);
        assertEquals(0, project.revision);
    }

    @Test
    public void promotedSnapshotCanResumeVersionFloorPersistence() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner signer = new FakeSigner();
        FakeFloor floor = new FakeFloor();
        floor.failOnce = true;
        File marker = new File(temp.getRoot(), "marker");
        RecoveryAdoption adoption =
                new RecoveryAdoption(marker, project, signer, absent(), () -> {}, floor);
        assertThrows(IllegalStateException.class, () -> adoption.adopt(bundle));
        assertEquals(1, project.revision);
        assertEquals(1, floor.next);
        assertTrue(marker.isFile());
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.resume());
        assertEquals(8, floor.next);
        assertEquals(1, signer.saves);
    }

    @Test
    public void exhaustedVersionIsRejectedBeforeStaging() throws Exception {
        RecoveryBundle ordinary = bundle();
        RecoveryBundle exhausted =
                RecoveryBundle.create(
                        ordinary.revision(),
                        Integer.MAX_VALUE - 1,
                        ordinary.sources(),
                        ordinary.signer(),
                        null);
        FakeProject project = new FakeProject();
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        new File(temp.getRoot(), "marker"),
                        project,
                        new FakeSigner(),
                        absent(),
                        () -> {},
                        new FakeFloor());
        assertThrows(IllegalArgumentException.class, () -> adoption.adopt(exhausted));
        assertEquals(0, project.stages);
    }

    @Test
    public void trustedPrivateDirectoryMayHavePlatformSymlinkAncestor() throws Exception {
        File actual = temp.newFolder("actual");
        java.nio.file.Path alias = temp.getRoot().toPath().resolve("platform-alias");
        Files.createSymbolicLink(alias, actual.toPath());
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        alias.resolve("marker").toFile(),
                        new FakeProject(),
                        new FakeSigner(),
                        absent(),
                        () -> {},
                        new FakeFloor());
        assertEquals(RecoveryAdoption.Status.COMPLETE, adoption.adopt(bundle()));
    }

    @Test
    public void markerItselfMayNotBeSymlink() throws Exception {
        File outside = temp.newFile("outside");
        java.nio.file.Path marker = temp.getRoot().toPath().resolve("marker-link");
        Files.createSymbolicLink(marker, outside.toPath());
        RecoveryAdoption adoption =
                new RecoveryAdoption(
                        marker.toFile(),
                        new FakeProject(),
                        new FakeSigner(),
                        absent(),
                        () -> {},
                        new FakeFloor());
        assertThrows(SecurityException.class, () -> adoption.adopt(bundle()));
    }

    @Test
    public void newActivityWaitsForImportInPreviousActivity() throws Exception {
        RecoveryBundle bundle = bundle();
        FakeProject project = new FakeProject();
        FakeSigner stored = new FakeSigner();
        CountDownLatch saving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RecoveryAdoption.Signer signer =
                new RecoveryAdoption.Signer() {
                    @Override
                    public RecoveryAdoption.StoredSigner load() throws Exception {
                        return stored.load();
                    }

                    @Override
                    public void saveNew(PortableSigningIdentity identity, byte[] lineage)
                            throws Exception {
                        saving.countDown();
                        if (!release.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("Timed out waiting for import test");
                        stored.saveNew(identity, lineage);
                    }

                    @Override
                    public void saveForPendingRecovery(
                            PortableSigningIdentity identity, byte[] lineage) throws Exception {
                        stored.saveForPendingRecovery(identity, lineage);
                    }
                };
        File marker = new File(temp.getRoot(), "concurrent.marker");
        FakeFloor floor = new FakeFloor();
        RecoveryAdoption first =
                new RecoveryAdoption(marker, project, signer, absent(), () -> {}, floor);
        RecoveryAdoption nextActivity =
                new RecoveryAdoption(marker, project, signer, absent(), () -> {}, floor);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<RecoveryAdoption.Status> initial = threads.submit(() -> first.adopt(bundle));
            assertTrue(saving.await(5, TimeUnit.SECONDS));
            CountDownLatch restarting = new CountDownLatch(1);
            Future<RecoveryAdoption.Status> restarted =
                    threads.submit(
                            () -> {
                                restarting.countDown();
                                return nextActivity.resume();
                            });
            assertTrue(restarting.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> restarted.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            assertEquals(RecoveryAdoption.Status.COMPLETE, initial.get(5, TimeUnit.SECONDS));
            assertEquals(RecoveryAdoption.Status.COMPLETE, restarted.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            threads.shutdownNow();
        }
    }

    private static RecoveryBundle bundle() throws Exception {
        return RecoveryBundle.create(
                3,
                7,
                Map.of(
                        JAVA, "class MainActivity {}".getBytes(StandardCharsets.UTF_8),
                        XML, "<View/>".getBytes(StandardCharsets.UTF_8)),
                PortableSigningIdentity.create(),
                null);
    }

    private static RecoveryAdoption.InstalledApp absent() {
        return (savedVersion, certificate, lineage) ->
                RecoveryInstalledApp.nextVersionCode(savedVersion, certificate, lineage, null, 0);
    }

    private static final class FakeInstalledApp implements RecoveryAdoption.InstalledApp {
        byte[] certificate;
        long version;

        @Override
        public int nextVersionCode(int savedVersion, byte[] recoveredCertificate, byte[] lineage)
                throws Exception {
            return RecoveryInstalledApp.nextVersionCode(
                    savedVersion, recoveredCertificate, lineage, certificate, version);
        }
    }

    private static final class FakeProject implements RecoveryAdoption.Project {
        int revision;
        int stages;
        boolean pristine = true;
        byte[] stageDigest;

        @Override
        public int revision() {
            return revision;
        }

        @Override
        public boolean isPristineDefaultSnapshot() {
            return pristine;
        }

        @Override
        public int stageExactSnapshot(int expected, Map<String, byte[]> files) {
            stages++;
            stageDigest = RecoveryAdoption.sourceDigest(files);
            return 1;
        }

        @Override
        public byte[] validatedStagedSnapshotDigest(int expected, int staged) {
            if (staged != 1 || expected != 0) throw new SecurityException("Invalid stage");
            return stageDigest;
        }

        @Override
        public int promoteStagedSnapshot(int expected, int staged) {
            revision = staged;
            return revision;
        }
    }

    private static final class FakeSigner implements RecoveryAdoption.Signer {
        RecoveryAdoption.StoredSigner stored;
        boolean failBeforeSave;
        boolean failAfterSave;
        boolean orphanedKey;
        boolean corrupt;
        int saves;
        int orphanRecoveries;

        @Override
        public RecoveryAdoption.StoredSigner load() throws Exception {
            if (corrupt) throw new GeneralSecurityException("corrupt signer");
            if (orphanedKey && stored == null) {
                throw new PortableSignerStore.OrphanedWrappingKeyException(
                        new FileNotFoundException("interrupted before identity.bin"));
            }
            return stored;
        }

        @Override
        public void saveNew(PortableSigningIdentity identity, byte[] lineage) throws Exception {
            if (failBeforeSave) throw new GeneralSecurityException("injected before save");
            saves++;
            stored =
                    new RecoveryAdoption.StoredSigner(identity.certificate().getEncoded(), lineage);
            if (failAfterSave) throw new GeneralSecurityException("injected after save");
        }

        @Override
        public void saveForPendingRecovery(PortableSigningIdentity identity, byte[] lineage)
                throws Exception {
            if (!orphanedKey || stored != null) throw new GeneralSecurityException("not orphaned");
            orphanRecoveries++;
            stored =
                    new RecoveryAdoption.StoredSigner(identity.certificate().getEncoded(), lineage);
        }
    }

    private static final class FakeFloor implements RecoveryAdoption.VersionFloor {
        int next = 1;
        boolean failOnce;

        @Override
        public int nextVersion() {
            return next;
        }

        @Override
        public void raiseToAtLeast(int value) {
            if (failOnce) {
                failOnce = false;
                throw new IllegalStateException("injected floor failure");
            }
            next = Math.max(next, value);
        }
    }
}
