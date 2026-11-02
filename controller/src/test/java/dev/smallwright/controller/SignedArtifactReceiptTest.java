package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SignedArtifactReceiptTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void roundTripRetainsExactArtifactIdentity() throws Exception {
        File project = temporary.newFolder("counter-alpha");
        File artifacts = new File(project, "artifacts");
        assertEquals(true, artifacts.mkdir());
        File apk = new File(artifacts, "counter-v3.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        VerifiedArtifact artifact = VerifiedArtifact.capture(apk);
        SignedArtifactReceipt.write(
                project,
                "counter-alpha",
                "dev.smallwright.generated.counter",
                2,
                3,
                artifact,
                "a".repeat(64));

        SignedArtifactReceipt.Record record =
                SignedArtifactReceipt.read(
                        project, "counter-alpha", "dev.smallwright.generated.counter", 2);
        assertEquals(apk.getCanonicalFile(), record.apk.getCanonicalFile());
        assertEquals(3, record.versionCode);
        assertEquals(artifact.sha256(), record.sha256);
    }

    @Test
    public void newProjectReceiptCannotBeRestoredAsAnotherProject() throws Exception {
        String id = "app" + "a".repeat(32);
        String packageName = "dev.smallwright.generated." + id;
        File project = temporary.newFolder(id);
        File artifacts = new File(project, "artifacts");
        assertEquals(true, artifacts.mkdir());
        File apk = new File(artifacts, "app-v1.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        SignedArtifactReceipt.write(
                project, id, packageName, 0, 1, VerifiedArtifact.capture(apk), "a".repeat(64));

        assertEquals(
                apk.getCanonicalFile(),
                SignedArtifactReceipt.read(project, id, packageName, 0).apk.getCanonicalFile());
        assertThrows(
                IOException.class,
                () -> SignedArtifactReceipt.read(project, "app" + "b".repeat(32), packageName, 0));
        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                project, id, "dev.smallwright.generated.counter", 0));
    }

    @Test
    public void rejectsChangedRevisionAndArtifactBytes() throws Exception {
        File project = temporary.newFolder("counter-alpha");
        File artifacts = new File(project, "artifacts");
        assertEquals(true, artifacts.mkdir());
        File apk = new File(artifacts, "counter-v3.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        SignedArtifactReceipt.write(
                project,
                "counter-alpha",
                "dev.smallwright.generated.counter",
                2,
                3,
                VerifiedArtifact.capture(apk),
                "a".repeat(64));
        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                project, "counter-alpha", "dev.smallwright.generated.counter", 1));
        Files.write(apk.toPath(), "tampered".getBytes(StandardCharsets.UTF_8));
        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                project, "counter-alpha", "dev.smallwright.generated.counter", 2));
    }

    @Test
    public void rejectsLinkedArtifactAndTraversalReceipt() throws Exception {
        File project = temporary.newFolder("counter-alpha");
        File artifacts = new File(project, "artifacts");
        assertEquals(true, artifacts.mkdir());
        File outside = temporary.newFile("outside.apk");
        Files.write(outside.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        File linked = new File(artifacts, "counter-v3.apk");
        Files.createSymbolicLink(linked.toPath(), outside.toPath());
        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.write(
                                project,
                                "counter-alpha",
                                "dev.smallwright.generated.counter",
                                2,
                                3,
                                VerifiedArtifact.capture(linked),
                                "a".repeat(64)));
    }

    @Test
    public void rejectsReceiptPointingOutsideArtifacts() throws Exception {
        File project = temporary.newFolder("counter-alpha");
        File artifacts = new File(project, "artifacts");
        assertEquals(true, artifacts.mkdir());
        File apk = new File(artifacts, "counter-v3.apk");
        Files.write(apk.toPath(), "approved".getBytes(StandardCharsets.UTF_8));
        SignedArtifactReceipt.write(
                project,
                "counter-alpha",
                "dev.smallwright.generated.counter",
                2,
                3,
                VerifiedArtifact.capture(apk),
                "a".repeat(64));
        File receipt = new File(artifacts, "verified-artifact.properties");
        String original = new String(Files.readAllBytes(receipt.toPath()), StandardCharsets.UTF_8);
        Files.write(
                receipt.toPath(),
                original.replace("counter-v3.apk", "../outside.apk")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                project, "counter-alpha", "dev.smallwright.generated.counter", 2));
    }

    @Test
    public void rejectsLinkedReceiptDirectory() throws Exception {
        File outside = temporary.newFolder("outside");
        File project = temporary.newFolder("counter-alpha");
        Files.createSymbolicLink(new File(project, "artifacts").toPath(), outside.toPath());

        assertThrows(
                IOException.class,
                () ->
                        SignedArtifactReceipt.read(
                                project, "counter-alpha", "dev.smallwright.generated.counter", 2));
    }
}
