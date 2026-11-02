package dev.smallwright.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AppLinkSourcesTest {
    private static final String PACKAGE =
            "dev.smallwright.generated.app0123456789abcdef0123456789abcdef";
    private static final String PATH =
            "java/dev/smallwright/generated/app0123456789abcdef0123456789abcdef/builderlink/BuilderLink.java";
    private static final byte[] CERT = new byte[32];
    private static final String LAUNCHER =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">"
                    + "<application><activity android:name=\".MainActivity\""
                    + " android:exported=\"true\"><intent-filter>"
                    + "<action android:name=\"android.intent.action.MAIN\"/>"
                    + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                    + "</intent-filter></activity>%s</application></manifest>";

    static {
        Arrays.fill(CERT, (byte) 0xAB);
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rendersOneFileInTheReservedPackageForThisProjectAndController() throws Exception {
        Map<String, byte[]> files = AppLinkSources.files(PACKAGE, CERT);

        assertEquals(1, files.size());
        String source = new String(files.get(PATH), StandardCharsets.UTF_8);
        assertTrue(source, source.startsWith("package " + PACKAGE + ".builderlink;"));
        assertTrue(source, source.contains("\"" + AppLinkSources.CONTROLLER_PACKAGE + "\""));
        assertTrue(source, source.contains("\"" + "ab".repeat(32) + "\""));
        assertTrue(source, source.contains("\"" + AppReportActivity.class.getName() + "\""));
        assertFalse(source, source.matches("(?s).*@[A-Z_0-9]+@.*"));
        assertFalse(source, source.contains("getMessage()"));
        assertTrue(AppLinkSources.files(ProjectSourceStore.PACKAGE_NAME, CERT).isEmpty());
        assertThrows(IOException.class, () -> AppLinkSources.files(PACKAGE, new byte[31]));
        assertThrows(IOException.class, () -> AppLinkSources.files(PACKAGE, null));
    }

    @Test
    public void theBuildersCodeCompilesAgainstTheAndroidPlatformFile() throws Exception {
        File source = new File(temporary.newFolder("src"), "BuilderLink.java");
        Files.write(source.toPath(), AppLinkSources.files(PACKAGE, CERT).get(PATH));
        File classes = temporary.newFolder("classes");

        String diagnostics = compile(source, classes);

        String prefix = PACKAGE.replace('.', '/') + "/builderlink/BuilderLink";
        for (String nested : new String[] {"", "$Starter", "$Look", "$Crash", "$Tracker"})
            assertTrue(
                    nested + "\n" + diagnostics,
                    new File(classes, prefix + nested + ".class").isFile());
    }

    @Test
    public void everyReportTheBuildersCodeWritesPassesTheImportWhateverTheFramesAreNamed()
            throws Exception {
        File source = new File(temporary.newFolder("src"), "BuilderLink.java");
        Files.write(source.toPath(), AppLinkSources.files(PACKAGE, CERT).get(PATH));
        File classes = temporary.newFolder("classes");
        compile(source, classes);
        IllegalStateException failure = new IllegalStateException("the person's secret note");
        failure.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement(PACKAGE + ".ui.Écran", "getCount-impl", "Écran.kt", 12),
                    new StackTraceElement(PACKAGE + ".Counter", "plus-WZ4Q5Ns", "Counter.kt", 3),
                    new StackTraceElement(
                            PACKAGE + ".MainActivity",
                            "lambda$onCreate$0",
                            "MainActivity.java",
                            -1),
                    new StackTraceElement(
                            PACKAGE + ".Checks", "a name with spaces", "Checks.kt", 7),
                    new StackTraceElement("android.view.View", "performClick", "View.java", -2)
                });

        String report;
        try (URLClassLoader loader =
                new URLClassLoader(
                        new java.net.URL[] {
                            classes.toURI().toURL(),
                            toolchainFile("toolchain/input/android/android-36.jar").toURI().toURL()
                        },
                        null)) {
            Method write =
                    Class.forName(PACKAGE + ".builderlink.BuilderLink", false, loader)
                            .getDeclaredMethod("report", Throwable.class);
            write.setAccessible(true);
            report = (String) write.invoke(null, failure);
        }

        String imported =
                CrashReportImport.read(
                        new java.io.ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8)));
        assertEquals(report, imported);
        assertTrue(imported, imported.contains(".ui.Écran.getCount-impl:12\n"));
        assertTrue(imported, imported.contains(".Counter.plus-WZ4Q5Ns:3\n"));
        assertTrue(imported, imported.contains(".MainActivity.lambda$onCreate$0:-1\n"));
        assertTrue(imported, imported.contains(".Checks.unknown:7\n"));
        assertFalse(imported, imported.contains("secret"));
    }

    @Test
    public void aBuildCarriesTheCodeAndTheEntriesThatStartIt() throws Exception {
        ProjectIdentity project =
                new ProjectRegistry(temporary.newFolder("projects")).create("Notes");
        ProjectSourceStore store = new ProjectSourceStore(project);
        store.initialize();
        File snapshot = temporary.newFile("snapshot.zip");
        String path =
                "java/" + project.packageName().replace('.', '/') + "/builderlink/BuilderLink.java";

        String manifest;
        try (ProjectStore.BuildLease ignored = store.beginBuild(0, snapshot, 3, Map.of(), CERT);
                ZipFile zip = new ZipFile(snapshot)) {
            assertTrue(zip.getEntry(path) != null);
            manifest =
                    new String(
                            zip.getInputStream(zip.getEntry("AndroidManifest.xml")).readAllBytes(),
                            StandardCharsets.UTF_8);
        }
        String reserved = project.packageName() + ".builderlink";
        assertTrue(
                manifest,
                manifest.contains("<package android:name=\"dev.smallwright.controller\"/>"));
        assertEquals(manifest, 1, count(manifest, "android:authorities=\"" + reserved + "\""));
        String starter = tag(manifest, "provider", reserved + ".BuilderLink$Starter");
        assertTrue(starter, starter.contains("android:exported=\"false\""));
        assertTrue(starter, starter.contains("android:grantUriPermissions=\"true\""));
        String look = tag(manifest, "activity", reserved + ".BuilderLink$Look");
        assertTrue(look, look.contains("android:exported=\"true\""));
        assertFalse(look, look.contains("intent-filter"));
        String crash = tag(manifest, "activity", reserved + ".BuilderLink$Crash");
        assertTrue(crash, crash.contains("android:exported=\"false\""));
        assertTrue(crash, crash.contains("android:process=\":builderlink\""));
        assertFalse(manifest, manifest.contains("uses-permission"));
        assertTrue(manifest, manifest.contains("android:versionCode=\"3\""));
    }

    @Test
    public void theModelOrALibraryCannotDeclareTheBuildersComponents() {
        for (String forged :
                new String[] {
                    "<activity android:name=\".builderlink.BuilderLink$Look\" android:exported=\"true\"/>",
                    "<activity android:name=\"" + PACKAGE + ".builderlink.Other\"/>",
                    "<provider android:name=\".Mine\" android:authorities=\"x;"
                            + PACKAGE
                            + ".builderlink\" android:exported=\"false\"/>",
                    "<activity-alias android:name=\".Alias\" android:targetActivity=\".builderlink.BuilderLink$Look\"/>",
                    "<service android:name=\"" + PACKAGE + ".builderlink\"/>"
                }) {
            AppManifestPolicy.Refused refused =
                    assertThrows(
                            forged,
                            AppManifestPolicy.Refused.class,
                            () ->
                                    AppManifestPolicy.normalize(
                                            String.format(LAUNCHER, forged), PACKAGE, 2));
            assertTrue(
                    refused.getMessage(), refused.getMessage().contains("belongs to the builder"));
        }
        String library =
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\""
                        + " package=\"org.example\"><application><activity android:name=\""
                        + PACKAGE
                        + ".builderlink.BuilderLink$Look\" android:exported=\"true\"/>"
                        + "</application></manifest>";
        assertThrows(
                AppManifestPolicy.Refused.class,
                () ->
                        AppManifestPolicy.normalize(
                                String.format(LAUNCHER, ""),
                                PACKAGE,
                                2,
                                Map.of("org.example:x:1", library)));
    }

    @Test
    public void entriesAreAddedOnceBesideTheModelsOwnQueries() throws Exception {
        String own =
                String.format(LAUNCHER, "")
                        .replace(
                                "<application>",
                                "<queries><package android:name=\"com.example\"/></queries>"
                                        + "<application>");
        String manifest =
                AppLinkSources.withEntries(AppManifestPolicy.normalize(own, PACKAGE, 2), PACKAGE);

        assertEquals(manifest, 1, count(manifest, "<queries>"));
        assertTrue(manifest, manifest.contains("com.example"));
        assertEquals(manifest, 1, count(manifest, "BuilderLink$Starter"));
        assertEquals(
                AppManifestPolicy.normalize(own, ProjectSourceStore.PACKAGE_NAME, 2),
                AppLinkSources.withEntries(
                        AppManifestPolicy.normalize(own, ProjectSourceStore.PACKAGE_NAME, 2),
                        ProjectSourceStore.PACKAGE_NAME));
        assertThrows(
                AppManifestPolicy.Refused.class,
                () ->
                        AppLinkSources.withEntries(
                                String.format(
                                        LAUNCHER, "<activity android:name=\".builderlink.X\"/>"),
                                PACKAGE));
    }

    @Test
    public void theEntriesTheBuilderAddsPassTheTaskAffinityRuleTheValidatorApplies()
            throws Exception {
        String manifest =
                AppLinkSources.withEntries(
                        AppManifestPolicy.normalize(String.format(LAUNCHER, ""), PACKAGE, 2),
                        PACKAGE);
        org.w3c.dom.Element root = AppManifestPolicy.parse(manifest).getDocumentElement();
        org.w3c.dom.Element application = AppManifestPolicy.children(root, "application").get(0);
        String inherited = attribute(application, "taskAffinity", PACKAGE);
        int ownTasks = 0;
        for (org.w3c.dom.Element activity : AppManifestPolicy.children(application, "activity")) {
            String name = AppManifestPolicy.className(attribute(activity, "name", ""), PACKAGE);
            String affinity = attribute(activity, "taskAffinity", inherited);
            assertTrue(
                    name + " " + affinity,
                    AppLinkSources.allowsTaskAffinity(PACKAGE, name, affinity));
            if (!affinity.equals(PACKAGE)) ownTasks++;
        }
        assertEquals(manifest, 1, ownTasks);
    }

    @Test
    public void onlyTheBuildersCrashDialogMayKeepATaskOfItsOwn() {
        String crash = AppLinkSources.component(PACKAGE, AppLinkSources.CRASH);
        String reserved = PACKAGE + ".builderlink";
        assertTrue(AppLinkSources.allowsTaskAffinity(PACKAGE, crash, reserved));
        assertTrue(AppLinkSources.allowsTaskAffinity(PACKAGE, crash, PACKAGE));
        assertTrue(AppLinkSources.allowsTaskAffinity(PACKAGE, PACKAGE + ".MainActivity", PACKAGE));
        assertFalse(
                AppLinkSources.allowsTaskAffinity(PACKAGE, PACKAGE + ".MainActivity", reserved));
        assertFalse(
                AppLinkSources.allowsTaskAffinity(
                        PACKAGE, AppLinkSources.component(PACKAGE, AppLinkSources.LOOK), reserved));
        assertFalse(AppLinkSources.allowsTaskAffinity(PACKAGE, crash, "com.example.other"));
        assertFalse(AppLinkSources.allowsTaskAffinity(PACKAGE, crash, reserved + ".x"));
        assertFalse(AppLinkSources.allowsTaskAffinity(PACKAGE, crash, null));
        String legacy = ProjectSourceStore.PACKAGE_NAME;
        assertFalse(
                AppLinkSources.allowsTaskAffinity(
                        legacy,
                        AppLinkSources.component(legacy, AppLinkSources.CRASH),
                        legacy + ".builderlink"));
    }

    @Test
    public void theEntriesTheBuilderAddsPassTheComponentRuleTheValidatorApplies() throws Exception {
        String manifest =
                AppLinkSources.withEntries(
                        AppManifestPolicy.normalize(String.format(LAUNCHER, ""), PACKAGE, 2),
                        PACKAGE);
        org.w3c.dom.Element root = AppManifestPolicy.parse(manifest).getDocumentElement();
        org.w3c.dom.Element application = AppManifestPolicy.children(root, "application").get(0);
        int checked = 0;
        for (String kind : new String[] {"activity", "provider"})
            for (org.w3c.dom.Element component : AppManifestPolicy.children(application, kind)) {
                String name =
                        AppManifestPolicy.className(attribute(component, "name", ""), PACKAGE);
                String process = attribute(component, "process", "");
                String resolved = process.startsWith(":") ? PACKAGE + process : PACKAGE;
                boolean exported = Boolean.parseBoolean(attribute(component, "exported", "false"));
                assertTrue(
                        name + " " + exported + " " + resolved,
                        AppLinkSources.allowsLinkComponent(PACKAGE, name, exported, resolved));
                if (name.contains(".builderlink.")) checked++;
            }
        assertEquals(manifest, 3, checked);
    }

    @Test
    public void theBuildersComponentsKeepTheirOwnExposureAndProcess() {
        String look = AppLinkSources.component(PACKAGE, AppLinkSources.LOOK);
        String crash = AppLinkSources.component(PACKAGE, AppLinkSources.CRASH);
        String starter = AppLinkSources.component(PACKAGE, AppLinkSources.STARTER);
        String crashProcess = PACKAGE + ":builderlink";
        assertTrue(AppLinkSources.allowsLinkComponent(PACKAGE, look, true, PACKAGE));
        assertTrue(AppLinkSources.allowsLinkComponent(PACKAGE, crash, false, crashProcess));
        assertTrue(AppLinkSources.allowsLinkComponent(PACKAGE, starter, false, PACKAGE));
        assertTrue(
                AppLinkSources.allowsLinkComponent(
                        PACKAGE, PACKAGE + ".MainActivity", true, PACKAGE));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, look, false, PACKAGE));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, look, true, crashProcess));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, crash, true, crashProcess));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, crash, false, PACKAGE));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, crash, false, null));
        assertFalse(AppLinkSources.allowsLinkComponent(PACKAGE, starter, true, PACKAGE));
        String legacy = ProjectSourceStore.PACKAGE_NAME;
        assertFalse(
                AppLinkSources.allowsLinkComponent(
                        legacy,
                        AppLinkSources.component(legacy, AppLinkSources.LOOK),
                        true,
                        legacy));
    }

    @Test
    public void aReportWithNonLatinNamesStopsAtTheImportsByteBound() throws Exception {
        File source = new File(temporary.newFolder("src"), "BuilderLink.java");
        Files.write(source.toPath(), AppLinkSources.files(PACKAGE, CERT).get(PATH));
        File classes = temporary.newFolder("classes");
        compile(source, classes);
        StackTraceElement[] frames = new StackTraceElement[40];
        for (int i = 0; i < frames.length; i++)
            frames[i] =
                    new StackTraceElement(
                            PACKAGE + "." + "画面".repeat(100), "数える".repeat(30), "画面.kt", i);
        IllegalStateException failure = new IllegalStateException();
        failure.setStackTrace(frames);
        IllegalArgumentException cause = new IllegalArgumentException();
        cause.setStackTrace(frames);
        failure.initCause(cause);

        String report;
        try (URLClassLoader loader =
                new URLClassLoader(
                        new java.net.URL[] {
                            classes.toURI().toURL(),
                            toolchainFile("toolchain/input/android/android-36.jar").toURI().toURL()
                        },
                        null)) {
            Method write =
                    Class.forName(PACKAGE + ".builderlink.BuilderLink", false, loader)
                            .getDeclaredMethod("report", Throwable.class);
            write.setAccessible(true);
            report = (String) write.invoke(null, failure);
        }

        int bytes = report.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(Integer.toString(bytes), bytes <= CrashReportImport.MAX_BYTES);
        assertTrue(Integer.toString(bytes), bytes > CrashReportImport.MAX_BYTES - 1024);
        assertEquals(
                report,
                CrashReportImport.read(
                        new java.io.ByteArrayInputStream(report.getBytes(StandardCharsets.UTF_8))));
    }

    private static String attribute(org.w3c.dom.Element element, String name, String otherwise) {
        String android = "http://schemas.android.com/apk/res/android";
        return element.hasAttributeNS(android, name)
                ? element.getAttributeNS(android, name)
                : otherwise;
    }

    private static String tag(String xml, String element, String name) {
        for (String part : xml.split("(?=<)"))
            if (part.startsWith("<" + element + " ")
                    && part.contains("android:name=\"" + name + "\"")) return part;
        throw new AssertionError("No <" + element + "> " + name + " in " + xml);
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private static String compile(File source, File classes) throws Exception {
        File platform = toolchainFile("toolchain/input/android/android-36.jar");
        File ecj = toolchainFile("toolchain/input/ecj-4.12.jar");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (URLClassLoader loader =
                        new URLClassLoader(new java.net.URL[] {ecj.toURI().toURL()}, null);
                PrintWriter output = new PrintWriter(diagnostics, true)) {
            Class<?> compiler =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.batch.BatchCompiler", true, loader);
            Class<?> progress =
                    Class.forName(
                            "org.eclipse.jdt.core.compiler.CompilationProgress", true, loader);
            Method compile =
                    compiler.getMethod(
                            "compile",
                            String[].class,
                            PrintWriter.class,
                            PrintWriter.class,
                            progress);
            String[] arguments = {
                "-1.8",
                "-proc:none",
                "-bootclasspath",
                platform.getAbsolutePath(),
                "-classpath",
                platform.getAbsolutePath(),
                "-d",
                classes.getAbsolutePath(),
                source.getAbsolutePath()
            };
            boolean compiled = (Boolean) compile.invoke(null, arguments, output, output, null);
            assertTrue(diagnostics.toString(), compiled);
        }
        return diagnostics.toString();
    }

    private static File toolchainFile(String path) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File candidate = new File(directory, path);
            if (candidate.isFile()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IllegalStateException("Toolchain input is missing: " + path);
    }
}
