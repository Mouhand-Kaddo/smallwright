package dev.smallwright.worker;

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
    public void workerHasNoInternetOrSharedUid() {
        assertFalse(manifest.contains("android.permission.INTERNET"));
        assertFalse(manifest.contains("sharedUserId"));
    }

    @Test
    public void buildServiceRequiresSignaturePermission() {
        assertTrue(
                manifest.contains("android:name=\"dev.smallwright.permission.BIND_BUILD_WORKER\""));
        assertTrue(manifest.contains("android:protectionLevel=\"signature\""));
        assertTrue(
                manifest.contains(
                        "android:permission=\"dev.smallwright.permission.BIND_BUILD_WORKER\""));
    }

    @Test
    public void serviceDoesNotTrustACallerSuppliedPackageName() throws Exception {
        String service =
                new String(
                        Files.readAllBytes(
                                Path.of("src/main/java/dev/smallwright/worker/BuildService.java")),
                        StandardCharsets.UTF_8);
        assertTrue(service.contains("CONTROLLER_PACKAGE = \"dev.smallwright.controller\""));
        assertFalse(service.contains("handshake(String controllerPackage"));
    }
}
