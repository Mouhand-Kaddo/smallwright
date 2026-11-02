package dev.smallwright.libraries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class PomTest {
    private static final String ENTITY_POM =
            "<?xml version=\"1.0\" encoding=\"UTF-16\"?>"
                    + "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/hosts\">]>"
                    + "<project><groupId>&x;</groupId></project>";

    @Test
    public void aDocumentTypeIsRefusedInAnyEncoding() {
        for (byte[] bytes :
                new byte[][] {
                    ENTITY_POM.getBytes(StandardCharsets.UTF_8),
                    ENTITY_POM.getBytes(StandardCharsets.UTF_16)
                }) {
            assertThrows(IOException.class, () -> Pom.parse(bytes, "lib.pom"));
            assertThrows(IOException.class, () -> Pom.versions(bytes, "maven-metadata.xml"));
        }
    }

    @Test
    public void metadataVersionsAreRead() throws IOException {
        byte[] metadata =
                ("<metadata><versioning><versions><version>1.0</version>"
                                + "<version>2.0</version></versions></versioning></metadata>")
                        .getBytes(StandardCharsets.UTF_8);
        assertEquals(java.util.List.of("1.0", "2.0"), Pom.versions(metadata, "maven-metadata.xml"));
    }
}
