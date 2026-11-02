package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Test;

public final class SourceExportTest {
    private static final String JAVA = "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String XML = "res/layout/empty.xml";

    @Test
    public void exportsOnlyTheCurrentAllowlistedJavaAndXml() throws Exception {
        Map<String, String> files = Map.of(JAVA, "class MainActivity {}", XML, "<View/>");
        SourceExport export = SourceExport.prepare(7, files, ProjectSourceStore.exportManifest());
        assertEquals(7, export.revision());
        assertTrue(export.preview().contains("MainActivity.java"));
        assertTrue(export.preview().contains("empty.xml"));
        assertTrue(export.preview().contains("AndroidManifest.xml"));
        assertFalse(export.preview().contains("class MainActivity {}"));

        Map<String, String> contents = unzip(export.bytes());
        assertEquals(3, contents.size());
        assertTrue(
                contents.get("src/main/AndroidManifest.xml")
                        .contains("dev.smallwright.generated.counter"));
        assertEquals("class MainActivity {}", contents.get("src/main/" + JAVA));
        assertEquals("<View/>", contents.get("src/main/" + XML));
        assertFalse(contents.containsKey("project.json"));
    }

    @Test
    public void rejectsUnexpectedFilesInsteadOfSilentlyExportingThem() {
        Map<String, String> files = new HashMap<>();
        files.put(JAVA, "class MainActivity {}");
        files.put(XML, "<View/>");
        files.put("credentials.json", "secret");
        assertThrows(
                SecurityException.class,
                () -> SourceExport.prepare(1, files, ProjectSourceStore.exportManifest()));
    }

    @Test
    public void rejectsMissingAndOversizedSource() {
        assertThrows(
                SecurityException.class,
                () ->
                        SourceExport.prepare(
                                1,
                                Map.of(JAVA, "class MainActivity {}"),
                                ProjectSourceStore.exportManifest()));
        assertThrows(
                SecurityException.class,
                () ->
                        SourceExport.prepare(
                                1,
                                Map.of(JAVA, "x".repeat(1024 * 1024 + 1), XML, "<View/>"),
                                ProjectSourceStore.exportManifest()));
    }

    @Test
    public void copiedArchiveIsUnaffectedByLaterMapChanges() throws Exception {
        Map<String, String> files = new HashMap<>(Map.of(JAVA, "before", XML, "<View/>"));
        SourceExport export = SourceExport.prepare(2, files, ProjectSourceStore.exportManifest());
        files.put(JAVA, "after");
        assertEquals("before", unzip(export.bytes()).get("src/main/" + JAVA));
    }

    @Test
    public void newProjectExportUsesOnlyItsOwnPackageAndSourcePath() throws Exception {
        String app = "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
        String java = "java/" + app.replace('.', '/') + "/MainActivity.java";
        Map<String, String> files =
                Map.of(java, "package " + app + "; class MainActivity {}", XML, "<View/>");
        SourceExport export =
                SourceExport.prepare(3, files, ProjectSourceStore.exportManifest(app), app);
        Map<String, String> contents = unzip(export.bytes());
        assertEquals(3, contents.size());
        assertTrue(
                contents.get("src/main/AndroidManifest.xml").contains("package=\"" + app + "\""));
        assertEquals(files.get(java), contents.get("src/main/" + java));
        assertThrows(
                SecurityException.class,
                () ->
                        SourceExport.prepare(
                                3,
                                files,
                                ProjectSourceStore.exportManifest(app),
                                ProjectSourceStore.PACKAGE_NAME));
        assertThrows(
                SecurityException.class,
                () ->
                        SourceExport.prepare(
                                3,
                                files,
                                ProjectSourceStore.exportManifest(ProjectSourceStore.PACKAGE_NAME),
                                app));
    }

    private static Map<String, String> unzip(byte[] archive) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                input.transferTo(data);
                files.put(entry.getName(), data.toString(StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
