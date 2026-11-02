package dev.smallwright.controller;

import static org.junit.Assert.*;

import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exercises the existing project source seam, using actual private files and chat metadata. */
public final class ManualBuildOriginTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void committedManualSourceRetainsItsCapturedChatAcrossRestart() throws Exception {
        File base = temporary.newFolder("private");
        ProjectIdentity project =
                new ProjectRegistry(new File(base, "projects"), base).create("Counter");
        ChatStore chats = new ChatStore(new File(base, "chats"), base);
        String captured = chats.createChat(project.id(), "Origin").id;
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String edited = source.readSource() + "\n// Manual change\n";
        int revision = source.writeSource(0, edited, captured, "manual_build_1", 1, chats);
        chats.createChat(project.id(), "Browsed during build");

        ManualBuildOriginStore.Origin restored =
                new ProjectSourceStore(project).manualBuildOrigin(revision, chats);
        assertNotNull("Committed manual source has no durable captured-chat origin", restored);
        assertEquals(1, restored.sourceRevision);
        assertEquals(project.id(), restored.projectId);
        assertEquals(captured, restored.chatId);
        assertEquals("manual_build_1", restored.buildId);
        assertEquals(1, restored.versionCode);
        assertNull(new ProjectSourceStore(project).verifiedManualBuildOrigin(revision, chats));
    }

    @Test
    public void aProjectWhoseBuilderDeletedTheStarterLayoutCanStillBeBuiltByHand()
            throws Exception {
        File base = temporary.newFolder("private");
        ProjectIdentity project =
                new ProjectRegistry(new File(base, "projects"), base).create("Water");
        ChatStore chats = new ChatStore(new File(base, "chats"), base);
        String chat = chats.createChat(project.id(), "Origin").id;
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        String layout = "res/layout/activity_main.xml";
        new dev.smallwright.project.ProjectStore(
                        new File(project.directory(), "revision-store"), project.packageName())
                .applyPatch(
                        0,
                        java.util.Map.of(
                                layout, "<LinearLayout/>".getBytes(StandardCharsets.UTF_8)),
                        java.util.Set.of("res/layout/empty.xml"));

        int revision = source.writeSource(1, source.readSource(), chat, "manual_build_1", 1, chats);

        assertEquals(2, revision);
        assertFalse(source.readRevision(revision).containsKey("res/layout/empty.xml"));
        assertTrue(source.readRevision(revision).containsKey(layout));
    }

    @Test
    public void guardedCompletionRetainsExactPublishedArtifactOriginAcrossRestart()
            throws Exception {
        File base = temporary.newFolder("completed");
        ProjectIdentity project =
                new ProjectRegistry(new File(base, "projects"), base).create("Counter");
        ChatStore chats = new ChatStore(new File(base, "chats"), base);
        String chat = chats.createChat(project.id(), "Build").id;
        ProjectSourceStore source = new ProjectSourceStore(project);
        source.initialize();
        int revision = source.writeSource(0, source.readSource(), chat, "build_complete", 3, chats);
        File artifacts = new File(project.directory(), "artifacts");
        assertTrue(artifacts.mkdir());
        File apk = new File(artifacts, "app-v3.apk");
        // Verification-only receipt bytes; no compiler/signing/runtime assertion in this JVM test.
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        StepRun.Control control = new StepRun.Control();
        control.finish(
                () -> true,
                revision,
                artifact,
                (rev, verified) -> {
                    source.pinManualBuildArtifact(rev, verified, chats);
                    SignedArtifactReceipt.write(
                            project.directory(),
                            project.id(),
                            project.packageName(),
                            rev,
                            3,
                            verified,
                            "a".repeat(64));
                },
                (state, rev, build, tokens) -> {});

        ManualBuildOriginStore.Origin restored =
                new ProjectSourceStore(project).verifiedManualBuildOrigin(revision, chats);
        assertNotNull("Published manual artifact has no immutable origin binding", restored);
        assertEquals(chat, restored.chatId);
        assertEquals("build_complete", restored.buildId);
        assertEquals(artifact.sha256(), restored.artifactSha256);
    }

    @Test
    public void foreignChatAndStaleEditorCannotCommitOrRelabelSource() throws Exception {
        Fixture f = new Fixture();
        ProjectIdentity other = f.registry.create("Other app");
        String foreign = f.chats.createChat(other.id(), "Other chat").id;
        assertThrows(
                IllegalArgumentException.class,
                () -> f.source.writeSource(0, f.saved, foreign, "wrong_chat", 1, f.chats));
        assertEquals(0, f.source.revision());
        assertNull(f.source.manualBuildOrigin(0, f.chats));
        int revision = f.commit();
        assertThrows(
                IllegalStateException.class,
                () -> f.source.writeSource(0, f.saved, f.chat, "stale", 2, f.chats));
        assertEquals(revision, f.source.revision());
        ProjectSourceStore otherSource = new ProjectSourceStore(other);
        otherSource.initialize();
        assertNull(otherSource.manualBuildOrigin(revision, f.chats));
        File foreignRecords = new File(other.directory(), "manual-build-origins");
        assertTrue(foreignRecords.mkdir());
        Files.copy(
                f.record(revision).toPath(), new File(foreignRecords, revision + ".bin").toPath());
        assertThrows(
                SecurityException.class, () -> otherSource.manualBuildOrigin(revision, f.chats));
        assertEquals(f.chat, f.source.manualBuildOrigin(revision, f.chats).chatId);
    }

    @Test
    public void wholeSnapshotChangesAndCorruptRecordsCannotRecoverAnOrigin() throws Exception {
        Fixture f = new Fixture();
        int revision = f.commit();
        File layout =
                new File(
                        f.project.directory(),
                        "revision-store/revisions/" + revision + "/res/layout/empty.xml");
        byte[] originalLayout = Files.readAllBytes(layout.toPath());
        Files.write(layout.toPath(), "<View/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> f.source.manualBuildOrigin(revision, f.chats));
        Files.write(layout.toPath(), originalLayout);
        byte[] record = Files.readAllBytes(f.record(revision).toPath());
        record[20] ^= 1;
        Files.write(f.record(revision).toPath(), record);
        assertThrows(
                SecurityException.class,
                () -> new ProjectSourceStore(f.project).manualBuildOrigin(revision, f.chats));
        assertThrows(
                SecurityException.class,
                () ->
                        new ProjectSourceStore(f.project)
                                .verifiedManualBuildOrigin(revision, f.chats));
    }

    @Test
    public void originIoFailureDoesNotPromoteSourceOrPublishReceipt() throws Exception {
        Fixture f = new Fixture();
        File outside = temporary.newFolder("outside");
        Files.createSymbolicLink(
                new File(f.project.directory(), "manual-build-origins").toPath(), outside.toPath());
        assertThrows(SecurityException.class, f::commit);
        assertEquals(0, f.source.revision());
        assertEquals(f.saved, f.source.readSource());
        assertEquals(0, outside.list().length);
        assertFalse(f.receipt().exists());
    }

    @Test
    public void candidateMetadataFailureNeverPublishesReadiness() throws Exception {
        Fixture f = new Fixture();
        int revision = f.commit();
        VerifiedArtifact artifact = f.artifact("approved");
        Files.write(f.record(revision).toPath(), new byte[] {1, 2, 3});
        StepRun.Control control = new StepRun.Control();
        assertThrows(
                SecurityException.class,
                () ->
                        control.finish(
                                () -> true,
                                revision,
                                artifact,
                                (rev, apk) -> f.publish(rev, apk),
                                (state, rev, build, tokens) -> {}));
        assertFalse(control.isFinished());
        assertFalse(f.receipt().exists());
        assertEquals(revision, f.source.revision());
    }

    @Test
    public void receiptIoFailureLeavesCandidateSourceButNoVerifiedOrigin() throws Exception {
        Fixture f = new Fixture();
        int revision = f.commit();
        VerifiedArtifact artifact = f.artifact("approved");
        assertTrue(f.receipt().mkdir());
        StepRun.Control control = new StepRun.Control();
        assertThrows(
                java.io.IOException.class,
                () ->
                        control.finish(
                                () -> true,
                                revision,
                                artifact,
                                (rev, apk) -> f.publish(rev, apk),
                                (state, rev, build, tokens) -> {}));
        assertFalse(control.isFinished());
        assertEquals(
                artifact.sha256(), f.source.manualBuildOrigin(revision, f.chats).artifactSha256);
        assertNull(new ProjectSourceStore(f.project).verifiedManualBuildOrigin(revision, f.chats));
        assertEquals(revision, f.source.revision());
    }

    @Test
    public void stopRetainsSourceOriginWithoutBindingOrPublishingAnArtifact() throws Exception {
        Fixture f = new Fixture();
        int revision = f.commit();
        VerifiedArtifact artifact = f.artifact("approved");
        StepRun.Control control = new StepRun.Control();
        assertEquals(StepRun.Control.StopOutcome.REQUESTED, control.stop());
        assertThrows(
                IllegalStateException.class,
                () ->
                        control.finish(
                                () -> true,
                                revision,
                                artifact,
                                (rev, apk) -> f.publish(rev, apk),
                                (state, rev, build, tokens) -> {}));
        assertNull(f.source.manualBuildOrigin(revision, f.chats).artifactSha256);
        assertNull(new ProjectSourceStore(f.project).verifiedManualBuildOrigin(revision, f.chats));
        assertFalse(f.receipt().exists());
        assertEquals(f.chat, f.source.manualBuildOrigin(revision, f.chats).chatId);
    }

    @Test
    public void artifactBindingIsImmutableAndCannotUseAnotherProjectsFile() throws Exception {
        Fixture f = new Fixture();
        int revision = f.commit();
        File outside = temporary.newFile("other-project.apk");
        Files.write(outside.toPath(), "foreign".getBytes(StandardCharsets.UTF_8));
        assertThrows(
                SecurityException.class,
                () ->
                        f.source.pinManualBuildArtifact(
                                revision, VerifiedArtifact.capture(outside), f.chats));
        VerifiedArtifact artifact = f.artifact("approved");
        f.publish(revision, artifact);
        assertNotNull(f.source.verifiedManualBuildOrigin(revision, f.chats));
        Files.write(artifact.file().toPath(), "changed!".getBytes(StandardCharsets.UTF_8));
        VerifiedArtifact changed = VerifiedArtifact.capture(artifact.file());
        assertThrows(
                IllegalStateException.class,
                () -> f.source.pinManualBuildArtifact(revision, changed, f.chats));
        SignedArtifactReceipt.write(
                f.project.directory(),
                f.project.id(),
                f.project.packageName(),
                revision,
                1,
                changed,
                "a".repeat(64));
        assertThrows(
                SecurityException.class,
                () -> f.source.verifiedManualBuildOrigin(revision, f.chats));
    }

    @Test
    public void legacySourceAndReceiptsNeverInventAManualChatOrigin() throws Exception {
        Fixture f = new Fixture();
        int revision = f.source.writeSource(0, f.saved);
        VerifiedArtifact artifact = f.artifact("legacy");
        SignedArtifactReceipt.write(
                f.project.directory(),
                f.project.id(),
                f.project.packageName(),
                revision,
                1,
                artifact,
                "a".repeat(64));
        assertNull(f.source.manualBuildOrigin(revision, f.chats));
        assertNull(new ProjectSourceStore(f.project).verifiedManualBuildOrigin(revision, f.chats));
    }

    private final class Fixture {
        final File base = temporary.newFolder();
        final ProjectRegistry registry = new ProjectRegistry(new File(base, "projects"), base);
        final ProjectIdentity project = registry.create("Counter");
        final ChatStore chats = new ChatStore(new File(base, "chats"), base);
        final String chat = chats.createChat(project.id(), "Manual origin").id;
        final ProjectSourceStore source = new ProjectSourceStore(project);
        final String saved;

        Fixture() throws Exception {
            source.initialize();
            saved = source.readSource();
        }

        int commit() throws Exception {
            return source.writeSource(0, saved, chat, "manual", 1, chats);
        }

        File record(int revision) {
            return new File(project.directory(), "manual-build-origins/" + revision + ".bin");
        }

        File receipt() {
            return new File(project.directory(), "artifacts/verified-artifact.properties");
        }

        VerifiedArtifact artifact(String bytes) throws Exception {
            File artifacts = new File(project.directory(), "artifacts");
            if (!artifacts.mkdirs() && !artifacts.isDirectory())
                throw new java.io.IOException("No artifact directory");
            File apk = new File(artifacts, "app-v1.apk");
            Files.write(apk.toPath(), bytes.getBytes(StandardCharsets.UTF_8));
            return VerifiedArtifact.capture(apk);
        }

        void publish(int revision, VerifiedArtifact artifact) throws Exception {
            source.pinManualBuildArtifact(revision, artifact, chats);
            SignedArtifactReceipt.write(
                    project.directory(),
                    project.id(),
                    project.packageName(),
                    revision,
                    1,
                    artifact,
                    "a".repeat(64));
        }
    }
}
