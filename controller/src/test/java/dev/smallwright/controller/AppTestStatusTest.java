package dev.smallwright.controller;

import static org.junit.Assert.*;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AppTestStatusTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String V1 = "a".repeat(64);

    @Test
    public void acceptanceOfExactInstalledBuildSurvivesReopen() throws Exception {
        File project = temporary.newFolder("project");
        AppTestStatus status = new AppTestStatus(project, temporary.getRoot());
        assertEquals(AppTestStatus.State.INSTALLED_UNTESTED, status.observe(V1, V1));
        status.accept(V1, V1);
        assertEquals(
                AppTestStatus.State.USER_ACCEPTED,
                new AppTestStatus(project, temporary.getRoot()).observe(V1, V1));
    }

    @Test
    public void absentOrDifferentInstalledBuildCannotBeAccepted() throws Exception {
        AppTestStatus status =
                new AppTestStatus(temporary.newFolder("project"), temporary.getRoot());
        assertEquals(AppTestStatus.State.READY_TO_INSTALL, status.observe(V1, null));
        assertThrows(IllegalStateException.class, () -> status.accept(V1, null));
        assertThrows(IllegalStateException.class, () -> status.accept(V1, "b".repeat(64)));
        assertEquals(AppTestStatus.State.INSTALLED_UNTESTED, status.observe(V1, V1));
    }

    @Test
    public void anotherBuildOrProjectDoesNotInheritAcceptance() throws Exception {
        AppTestStatus status = new AppTestStatus(temporary.newFolder("first"), temporary.getRoot());
        status.accept(V1, V1);
        String v2 = "b".repeat(64);
        assertEquals(AppTestStatus.State.INSTALLED_UNTESTED, status.observe(v2, v2));
        assertEquals(AppTestStatus.State.READY_TO_INSTALL, status.observe(V1, v2));
        assertEquals(
                AppTestStatus.State.INSTALLED_UNTESTED,
                new AppTestStatus(temporary.newFolder("second"), temporary.getRoot())
                        .observe(V1, V1));
    }

    @Test
    public void corruptRecordFailsClosedWithoutChangingIt() throws Exception {
        File project = temporary.newFolder("project");
        java.nio.file.Path path = new File(project, "test-acceptance.txt").toPath();
        java.nio.file.Files.write(
                path, "USER_ACCEPTED".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        AppTestStatus status = new AppTestStatus(project, temporary.getRoot());
        assertThrows(java.io.IOException.class, () -> status.observe(V1, V1));
        assertThrows(java.io.IOException.class, () -> status.accept(V1, V1));
        assertEquals(
                "USER_ACCEPTED",
                new String(
                        java.nio.file.Files.readAllBytes(path),
                        java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    public void linkedAcceptancePathsCannotReadOrOverwriteOutsideFiles() throws Exception {
        File outside = temporary.newFile("outside");
        java.nio.file.Files.write(
                outside.toPath(), "untouched".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        File project = temporary.newFolder("project");
        java.nio.file.Files.createSymbolicLink(
                new File(project, "test-acceptance.txt").toPath(), outside.toPath());
        AppTestStatus status = new AppTestStatus(project, temporary.getRoot());
        assertThrows(java.io.IOException.class, () -> status.observe(V1, V1));
        assertThrows(java.io.IOException.class, () -> status.accept(V1, V1));
        assertEquals(
                "untouched",
                new String(
                        java.nio.file.Files.readAllBytes(outside.toPath()),
                        java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test
    public void failedWriteDoesNotReplacePreviousAcceptance() throws Exception {
        File project = temporary.newFolder("project");
        AppTestStatus status = new AppTestStatus(project, temporary.getRoot());
        status.accept(V1, V1);
        assertTrue(new File(project, "test-acceptance.txt.partial").mkdir());
        assertThrows(
                java.io.IOException.class, () -> status.accept("b".repeat(64), "b".repeat(64)));
        assertEquals(
                AppTestStatus.State.USER_ACCEPTED,
                new AppTestStatus(project, temporary.getRoot()).observe(V1, V1));
    }

    @Test
    public void trustedPrivateRootCanHavePlatformManagedAncestors() throws Exception {
        File platform = temporary.newFolder("platform");
        File privateRoot = new File(platform, "private");
        assertTrue(privateRoot.mkdir());
        java.nio.file.Path alias = new File(temporary.getRoot(), "platform-alias").toPath();
        java.nio.file.Files.createSymbolicLink(alias, platform.toPath());
        File trusted = alias.resolve("private").toFile();
        File project = new File(trusted, "project");
        assertTrue(project.mkdir());
        AppTestStatus status = new AppTestStatus(project, trusted);
        assertEquals(AppTestStatus.State.INSTALLED_UNTESTED, status.observe(V1, V1));
        status.accept(V1, V1);
        assertEquals(
                AppTestStatus.State.USER_ACCEPTED,
                new AppTestStatus(project, trusted).observe(V1, V1));
    }
}
