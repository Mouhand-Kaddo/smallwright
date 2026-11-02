package dev.smallwright.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class ManifestPolicyTest {
    private final String manifest =
            new String(
                    Files.readAllBytes(Path.of("src/main/AndroidManifest.xml")),
                    StandardCharsets.UTF_8);

    public ManifestPolicyTest() throws Exception {}

    @Test
    public void controllerMayUseInternetButNotSharedUid() {
        assertTrue(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("sharedUserId"));
    }

    @Test
    public void controllerRequestsWorkerBindPermission() {
        assertTrue(manifest.contains("dev.smallwright.permission.BIND_BUILD_WORKER"));
    }

    @Test
    public void generatedManifestTargetsTheSupportedSdk() throws Exception {
        String source =
                new String(
                        Files.readAllBytes(
                                Path.of(
                                        "src/main/java/dev/smallwright/controller/ProjectSourceStore.java")),
                        StandardCharsets.UTF_8);
        assertTrue(source.contains("android:targetSdkVersion=\\\"36\\\""));
    }

    @Test
    public void installerClosesTheApkStreamBeforeCommitting() throws Exception {
        String source =
                new String(
                        Files.readAllBytes(
                                Path.of(
                                        "src/main/java/dev/smallwright/controller/InstallCoordinator.java")),
                        StandardCharsets.UTF_8);
        assertTrue(
                source.indexOf("session.fsync(output);")
                        < source.indexOf("session.commit(sender);"));
        assertTrue(source.contains("Installation cancelled."));
    }
}
