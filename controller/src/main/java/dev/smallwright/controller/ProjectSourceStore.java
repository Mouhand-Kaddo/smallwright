package dev.smallwright.controller;

import android.content.Context;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectStore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

final class ProjectSourceStore {
    static final String PACKAGE_NAME = "dev.smallwright.generated.counter";
    private static final String MAIN_PATH =
            "java/dev/smallwright/generated/counter/MainActivity.java";
    private static final String LAYOUT_PATH = "res/layout/empty.xml";
    private static final String DEFAULT_LAYOUT =
            "<View xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>\n";
    private final File filesDirectory;
    private final File legacyDirectory;
    private final String projectId;
    private final String projectTitle;
    private final String packageName;
    private final String mainPath;
    private final ProjectStore store;
    private final File projectDirectory;

    ProjectSourceStore(Context context) {
        this(context.getFilesDir());
    }

    ProjectSourceStore(File filesDirectory) {
        this.filesDirectory = filesDirectory;
        File project = new File(filesDirectory, "projects/counter-alpha");
        projectDirectory = project;
        legacyDirectory = new File(project, "source");
        projectId = "counter-alpha";
        projectTitle = null;
        packageName = PACKAGE_NAME;
        mainPath = MAIN_PATH;
        store = new ProjectStore(new File(project, "revision-store"));
    }

    ProjectSourceStore(ProjectIdentity identity) {
        if (identity == null) throw new IllegalArgumentException("Missing project identity");
        boolean legacy = "counter-alpha".equals(identity.id());
        projectDirectory = identity.directory();
        filesDirectory = legacy ? identity.directory().getParentFile().getParentFile() : null;
        legacyDirectory = legacy ? new File(identity.directory(), "source") : null;
        projectId = identity.id();
        projectTitle = identity.title();
        packageName = identity.packageName();
        mainPath = "java/" + packageName.replace('.', '/') + "/MainActivity.java";
        store = new ProjectStore(new File(identity.directory(), "revision-store"), packageName);
    }

    String projectId() {
        return projectId;
    }

    /** The name the person gave the project, or null for the first sample project. */
    String projectTitle() {
        return projectTitle;
    }

    String packageName() {
        return packageName;
    }

    /** The project's brief, kept beside its source. */
    ProjectBrief brief() {
        return new ProjectBrief(projectDirectory);
    }

    /** The project's own folder: its revisions, builds and receipts. */
    File directory() {
        return projectDirectory;
    }

    void initialize() throws IOException {
        if (store.isInitialized()) return;
        store.initialize(
                Map.of(
                        mainPath,
                        initialSource(),
                        LAYOUT_PATH,
                        initialLayout(),
                        AppManifestPolicy.PATH,
                        generatedManifest().getBytes(StandardCharsets.UTF_8)));
    }

    String mainPath() {
        return mainPath;
    }

    boolean isEditablePath(String path) {
        return ProjectStore.isEditablePath(path, packageName);
    }

    /**
     * Writes and deletes files as one new revision; refused unless the head is expectedRevision.
     */
    int commit(int expectedRevision, Map<String, byte[]> writes, java.util.Set<String> deletions)
            throws IOException {
        return store.applyPatch(expectedRevision, writes, deletions);
    }

    String readSource() throws IOException {
        try {
            return new String(store.readFile(mainPath), StandardCharsets.UTF_8);
        } catch (NoSuchFileException deleted) {
            return "";
        }
    }

    /** The manifest the builder starts every project with, and builds old projects with. */
    String generatedManifest() {
        return exportManifest(packageName);
    }

    int revision() throws IOException {
        return store.revision();
    }

    Map<String, String> readCurrentFiles(int expectedRevision) throws IOException {
        Map<String, String> files = new TreeMap<>(store.readCurrentSourceFiles(expectedRevision));
        if (generatedManifest().equals(files.get(AppManifestPolicy.PATH)))
            files.remove(AppManifestPolicy.PATH);
        return files;
    }

    Map<String, byte[]> readRecoverySources(int expectedRevision) throws IOException {
        return store.readCurrentSourceBytes(expectedRevision);
    }

    /** The exact files of a revision committed at or before the current one. */
    Map<String, byte[]> readRevision(int revision) throws IOException {
        return store.readRevisionBytes(revision);
    }

    /**
     * Commits a new revision with an earlier revision's files; refused unless the head is expected.
     */
    int restore(int expectedRevision, int earlierRevision) throws IOException {
        return store.restore(expectedRevision, earlierRevision);
    }

    boolean isPristineDefaultSnapshot() throws IOException {
        if (store.revision() != 0
                || (legacyDirectory != null && Files.exists(legacyDirectory.toPath())))
            return false;
        Map<String, byte[]> files = new TreeMap<>(store.readCurrentSourceBytes(0));
        byte[] manifest = files.remove(AppManifestPolicy.PATH);
        if (manifest != null
                && !Arrays.equals(manifest, generatedManifest().getBytes(StandardCharsets.UTF_8)))
            return false;
        return files.size() == 2
                && Arrays.equals(files.get(mainPath), initialSource())
                && Arrays.equals(
                        files.get(LAYOUT_PATH), DEFAULT_LAYOUT.getBytes(StandardCharsets.UTF_8));
    }

    int stageRecoverySources(int expectedRevision, Map<String, byte[]> files) throws IOException {
        return store.stageExactSnapshot(expectedRevision, files);
    }

    byte[] validatedStagedRecoveryDigest(int expectedRevision, int stagedRevision)
            throws IOException {
        return store.validatedStagedSnapshotDigest(expectedRevision, stagedRevision);
    }

    int promoteRecoverySources(int expectedRevision, int stagedRevision) throws IOException {
        return store.promoteStagedSnapshot(expectedRevision, stagedRevision);
    }

    int writeSource(int expectedRevision, String source) throws IOException {
        return store.applyPatch(
                expectedRevision, Map.of(mainPath, source.getBytes(StandardCharsets.UTF_8)));
    }

    int writeLayout(int expectedRevision, String layout) throws IOException {
        return store.applyPatch(
                expectedRevision, Map.of(LAYOUT_PATH, layout.getBytes(StandardCharsets.UTF_8)));
    }

    /** Save captured chat provenance before promoting these exact staged source bytes. */
    int writeSource(
            int expectedRevision,
            String source,
            String chatId,
            String buildId,
            int versionCode,
            ChatStore chats)
            throws IOException {
        ManualBuildOriginStore.validate(projectId, chatId, buildId, versionCode);
        chats.listMessages(projectId, chatId);
        ManualBuildOriginStore origins = manualOrigins();
        origins.requireCapacity();
        Map<String, byte[]> files = new TreeMap<>(store.readCurrentSourceBytes(expectedRevision));
        files.put(mainPath, source.getBytes(StandardCharsets.UTF_8));
        int revision = store.stageExactSnapshot(expectedRevision, files);
        byte[] digest = store.validatedStagedSnapshotDigest(expectedRevision, revision);
        origins.create(
                new ManualBuildOriginStore.Origin(
                        projectId, chatId, buildId, revision, versionCode, digest, null));
        return store.promoteStagedSnapshot(expectedRevision, revision);
    }

    ManualBuildOriginStore.Origin manualBuildOrigin(int revision, ChatStore chats)
            throws IOException {
        ManualBuildOriginStore.Origin origin = manualOrigins().read(revision);
        if (origin == null) return null;
        chats.listMessages(projectId, origin.chatId);
        if (!MessageDigest.isEqual(
                origin.sourceDigest,
                SourceSnapshotDigest.compute(store.readCurrentSourceBytes(revision))))
            throw new SecurityException("Manual source changed after origin capture");
        return origin;
    }

    ManualBuildOriginStore.Origin verifiedManualBuildOrigin(int revision, ChatStore chats)
            throws IOException {
        ManualBuildOriginStore.Origin origin = manualBuildOrigin(revision, chats);
        if (origin == null || origin.artifactSha256 == null) return null;
        final SignedArtifactReceipt.Record receipt;
        try {
            receipt =
                    SignedArtifactReceipt.read(projectDirectory, projectId, packageName, revision);
        } catch (IOException invalidOrUnpublishedReceipt) {
            return null;
        }
        if (receipt.versionCode != origin.versionCode
                || !receipt.sha256.equals(origin.artifactSha256))
            throw new SecurityException("Manual build differs from signed artifact receipt");
        return origin;
    }

    /** Candidate bytes alone never claim success; the signed receipt must be published last. */
    void pinManualBuildArtifact(int revision, VerifiedArtifact artifact, ChatStore chats)
            throws IOException {
        ManualBuildOriginStore.Origin origin = manualBuildOrigin(revision, chats);
        if (origin == null) throw new IllegalStateException("Manual source has no captured origin");
        Path expected =
                new File(
                                projectDirectory,
                                "artifacts/"
                                        + ("counter-alpha".equals(projectId)
                                                ? "counter-v"
                                                : "app-v")
                                        + origin.versionCode
                                        + ".apk")
                        .toPath()
                        .toAbsolutePath()
                        .normalize();
        if (!artifact.file().toPath().toAbsolutePath().normalize().equals(expected))
            throw new SecurityException("Manual artifact belongs to another build/project");
        for (Path current = expected; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current))
                throw new SecurityException("Manual artifact path is a link");
            if (current.equals(projectDirectory.toPath().toAbsolutePath().normalize())) break;
        }
        artifact.verifyUnchanged();
        manualOrigins().pinArtifact(origin, artifact.sha256());
    }

    private ManualBuildOriginStore manualOrigins() {
        return new ManualBuildOriginStore(projectDirectory, projectId);
    }

    ProjectStore.BuildLease beginBuild(int expectedRevision, File output, int versionCode)
            throws IOException {
        return beginBuild(expectedRevision, output, versionCode, Map.of());
    }

    /** Builds with the project's Android library manifests (name to text) merged into the app's. */
    ProjectStore.BuildLease beginBuild(
            int expectedRevision, File output, int versionCode, Map<String, String> libraries)
            throws IOException {
        if (versionCode < 1) throw new IOException("Invalid generated version");
        return store.beginBuild(
                expectedRevision, output, buildManifest(expectedRevision, versionCode, libraries));
    }

    /**
     * As above, with the builder's own code and its manifest entries added (ADR 0008), signed to
     * talk only to the controller whose signing certificate has this SHA-256.
     */
    ProjectStore.BuildLease beginBuild(
            int expectedRevision,
            File output,
            int versionCode,
            Map<String, String> libraries,
            byte[] controllerCertificateSha256)
            throws IOException {
        if (versionCode < 1) throw new IOException("Invalid generated version");
        Map<String, byte[]> builderFiles =
                AppLinkSources.files(packageName, controllerCertificateSha256);
        String manifest = buildManifest(expectedRevision, versionCode, libraries);
        return store.beginBuild(
                expectedRevision,
                output,
                builderFiles.isEmpty()
                        ? manifest
                        : AppLinkSources.withEntries(manifest, packageName),
                builderFiles);
    }

    /** The project's own manifest, checked and normalized; old projects use the generated one. */
    String buildManifest(int revision, int versionCode, Map<String, String> libraries)
            throws IOException {
        try {
            String stored =
                    new String(
                            store.readFile(revision, AppManifestPolicy.PATH),
                            StandardCharsets.UTF_8);
            return AppManifestPolicy.normalize(stored, packageName, versionCode, libraries);
        } catch (NoSuchFileException absent) {
            if (libraries.isEmpty()) return projectManifest(versionCode);
            return AppManifestPolicy.normalize(
                    projectManifest(versionCode), packageName, versionCode, libraries);
        }
    }

    private byte[] initialSource() throws IOException {
        String source =
                packageName.equals(PACKAGE_NAME)
                        ? defaultSource()
                        : defaultSource()
                                .replace(
                                        "package " + PACKAGE_NAME + ";",
                                        "package " + packageName + ";");
        return legacyDirectory == null
                ? source.getBytes(StandardCharsets.UTF_8)
                : legacyOrDefault(mainPath, source);
    }

    private byte[] initialLayout() throws IOException {
        return legacyDirectory == null
                ? DEFAULT_LAYOUT.getBytes(StandardCharsets.UTF_8)
                : legacyOrDefault(LAYOUT_PATH, DEFAULT_LAYOUT);
    }

    private byte[] legacyOrDefault(String path, String fallback) throws IOException {
        File legacy = new File(legacyDirectory, path);
        Path current = filesDirectory.toPath();
        if (Files.isSymbolicLink(current))
            throw new SecurityException("Legacy project path is a symlink");
        for (Path segment : current.relativize(legacy.toPath())) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new SecurityException("Legacy project path is a symlink");
            }
        }
        if (!legacy.isFile()) return fallback.getBytes(StandardCharsets.UTF_8);
        return Files.readAllBytes(legacy.toPath());
    }

    private static String manifest(int versionCode) {
        return manifest(versionCode, PACKAGE_NAME);
    }

    private String projectManifest(int versionCode) {
        return manifest(versionCode, packageName);
    }

    private static String manifest(int versionCode, String packageName) {
        return "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\""
                + packageName
                + "\" android:versionCode=\""
                + versionCode
                + "\">"
                + "<uses-sdk android:minSdkVersion=\"31\" android:targetSdkVersion=\"36\"/>"
                + "<application android:allowBackup=\"false\" android:label=\""
                + (PACKAGE_NAME.equals(packageName) ? "Smallwright Counter" : "Smallwright App")
                + "\">"
                + "<activity android:name=\".MainActivity\" android:exported=\"true\" android:theme=\"@android:style/Theme.Material.Light.NoActionBar\">"
                + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                + (PACKAGE_NAME.equals(packageName)
                        ? ""
                        : "<action android:name=\"dev.smallwright.generated.PROJECT_APP\"/>")
                + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                + "</intent-filter></activity></application></manifest>\n";
    }

    /** Editable source bundle manifest; installation versions are assigned when building. */
    static String exportManifest() {
        return manifest(1);
    }

    static String exportManifest(String packageName) {
        if (packageName == null
                || !packageName.matches(
                        "dev\\.smallwright\\.generated\\.(?:counter|app[0-9a-f]{32})")) {
            throw new IllegalArgumentException("Invalid project package");
        }
        return manifest(1, packageName);
    }

    private static String defaultSource() {
        return ""
                + "package dev.smallwright.generated.counter;\n"
                + "\n"
                + "import android.app.Activity;\n"
                + "import android.app.AlertDialog;\n"
                + "import android.content.Intent;\n"
                + "import android.graphics.Insets;\n"
                + "import android.os.Bundle;\n"
                + "import android.view.View;\n"
                + "import android.view.WindowInsets;\n"
                + "import android.widget.Button;\n"
                + "import android.widget.LinearLayout;\n"
                + "import android.widget.ScrollView;\n"
                + "import android.widget.TextView;\n"
                + "import java.io.ByteArrayOutputStream;\n"
                + "import java.io.FileInputStream;\n"
                + "import java.io.FileOutputStream;\n"
                + "import java.io.IOException;\n"
                + "import java.io.OutputStream;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.util.concurrent.atomic.AtomicBoolean;\n"
                + "\n"
                + "public final class MainActivity extends Activity {\n"
                + "  private static final String REPORT = \"crash-report.txt\";\n"
                + "  private static final int MAX_REPORT_BYTES = 16384;\n"
                + "  private static final int EXPORT_REPORT = 1;\n"
                + "  private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean(false);\n"
                + "  private int count;\n"
                + "  private byte[] pendingReport;\n"
                + "\n"
                + "  @Override public void onCreate(Bundle state) {\n"
                + "    super.onCreate(state);\n"
                + "    installCrashHandler();\n"
                + "    count = getPreferences(0).getInt(\"count\", 0);\n"
                + "    LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);\n"
                + "    layout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {\n"
                + "      @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {\n"
                + "        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());\n"
                + "        view.setPadding(bars.left, bars.top, bars.right, bars.bottom);\n"
                + "        return insets;\n"
                + "      }\n"
                + "    });\n"
                + "    TextView label = new TextView(this); Button increment = new Button(this);\n"
                + "    increment.setText(\"Increment\");\n"
                + "    increment.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {\n"
                + "      count++; getPreferences(0).edit().putInt(\"count\", count).apply(); label.setText(\"Counter: \" + count);\n"
                + "    } });\n"
                + "    label.setText(\"Counter: \" + count);\n"
                + "    Button report = new Button(this); report.setText(\"Preview crash report\");\n"
                + "    report.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { previewReport(); } });\n"
                + "    layout.addView(label); layout.addView(increment); layout.addView(report); setContentView(layout);\n"
                + "  }\n"
                + "\n"
                + "  private void installCrashHandler() {\n"
                + "    if (!HANDLER_INSTALLED.compareAndSet(false, true)) return;\n"
                + "    final android.content.Context app = getApplicationContext();\n"
                + "    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();\n"
                + "    final AtomicBoolean handling = new AtomicBoolean(false);\n"
                + "    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {\n"
                + "      @Override public void uncaughtException(Thread thread, Throwable failure) {\n"
                + "        if (handling.compareAndSet(false, true)) {\n"
                + "          try {\n"
                + "            byte[] report = formatReport(failure).getBytes(StandardCharsets.UTF_8);\n"
                + "            if (report.length > MAX_REPORT_BYTES) {\n"
                + "              int end = MAX_REPORT_BYTES;\n"
                + "              while ((report[end] & 0xc0) == 0x80) end--;\n"
                + "              byte[] limited = new byte[end];\n"
                + "              System.arraycopy(report, 0, limited, 0, limited.length);\n"
                + "              report = limited;\n"
                + "            }\n"
                + "            try (FileOutputStream out = app.openFileOutput(REPORT, MODE_PRIVATE)) { out.write(report); }\n"
                + "          } catch (Throwable ignored) { /* Crash reporting must not replace the original crash. */ }\n"
                + "        }\n"
                + "        if (previous != null) previous.uncaughtException(thread, failure);\n"
                + "        else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }\n"
                + "      }\n"
                + "    });\n"
                + "  }\n"
                + "\n"
                + "  private static String formatReport(Throwable failure) {\n"
                + "    StringBuilder text = new StringBuilder(4096);\n"
                + "    text.append(\"Generated app crash\\nException messages and values omitted.\\n\");\n"
                + "    Throwable current = failure;\n"
                + "    for (int cause = 0; current != null && cause < 4 && text.length() < 12000; cause++) {\n"
                + "      text.append(\"Cause \").append(cause).append(\": \").append(shortText(current.getClass().getName()))\n"
                + "          .append('\\n');\n"
                + "      StackTraceElement[] frames = current.getStackTrace();\n"
                + "      for (int i = 0; i < frames.length && i < 40 && text.length() < 12000; i++) {\n"
                + "        StackTraceElement frame = frames[i];\n"
                + "        text.append(\"  at \").append(shortText(frame.getClassName())).append('.')\n"
                + "            .append(shortText(frame.getMethodName())).append(':').append(frame.getLineNumber()).append('\\n');\n"
                + "      }\n"
                + "      current = current.getCause();\n"
                + "    }\n"
                + "    return text.toString();\n"
                + "  }\n"
                + "\n"
                + "  private static String shortText(String value) {\n"
                + "    if (value == null) return \"\";\n"
                + "    return value.length() <= 256 ? value : value.substring(0, 256);\n"
                + "  }\n"
                + "\n"
                + "  private byte[] readReport() throws IOException {\n"
                + "    try (FileInputStream in = openFileInput(REPORT); ByteArrayOutputStream out = new ByteArrayOutputStream()) {\n"
                + "      byte[] buffer = new byte[2048];\n"
                + "      int read;\n"
                + "      while ((read = in.read(buffer)) != -1) {\n"
                + "        if (out.size() + read > MAX_REPORT_BYTES) throw new IOException(\"Crash report is too large\");\n"
                + "        out.write(buffer, 0, read);\n"
                + "      }\n"
                + "      return out.toByteArray();\n"
                + "    }\n"
                + "  }\n"
                + "\n"
                + "  private void previewReport() {\n"
                + "    final byte[] report;\n"
                + "    try { report = readReport(); }\n"
                + "    catch (java.io.FileNotFoundException missing) { message(\"No crash report saved\"); return; }\n"
                + "    catch (IOException error) { message(\"Could not read crash report\"); return; }\n"
                + "    if (report.length == 0) { message(\"No crash report saved\"); return; }\n"
                + "    ScrollView scroll = new ScrollView(this);\n"
                + "    TextView preview = new TextView(this);\n"
                + "    preview.setPadding(24, 24, 24, 24);\n"
                + "    preview.setText(new String(report, StandardCharsets.UTF_8));\n"
                + "    scroll.addView(preview);\n"
                + "    new AlertDialog.Builder(this).setTitle(\"Crash report preview\").setView(scroll)\n"
                + "        .setNegativeButton(\"Close\", null)\n"
                + "        .setPositiveButton(\"Export file\", new android.content.DialogInterface.OnClickListener() {\n"
                + "          @Override public void onClick(android.content.DialogInterface dialog, int which) { chooseExport(report); }\n"
                + "        })\n"
                + "        .show();\n"
                + "  }\n"
                + "\n"
                + "  private void chooseExport(byte[] report) {\n"
                + "    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);\n"
                + "    intent.addCategory(Intent.CATEGORY_OPENABLE);\n"
                + "    intent.setType(\"text/plain\");\n"
                + "    intent.putExtra(Intent.EXTRA_TITLE, \"smallwright-crash-report.txt\");\n"
                + "    pendingReport = report;\n"
                + "    try { startActivityForResult(intent, EXPORT_REPORT); }\n"
                + "    catch (RuntimeException unavailable) { pendingReport = null; message(\"Document picker unavailable\"); }\n"
                + "  }\n"
                + "\n"
                + "  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {\n"
                + "    super.onActivityResult(requestCode, resultCode, data);\n"
                + "    if (requestCode != EXPORT_REPORT) return;\n"
                + "    byte[] report = pendingReport;\n"
                + "    pendingReport = null;\n"
                + "    if (resultCode != RESULT_OK || report == null || data == null || data.getData() == null) return;\n"
                + "    if (!\"content\".equals(data.getData().getScheme())) { message(\"Invalid document destination\"); return; }\n"
                + "    try {\n"
                + "      if (report.length == 0) { message(\"No crash report saved\"); return; }\n"
                + "      try (OutputStream out = getContentResolver().openOutputStream(data.getData(), \"w\")) {\n"
                + "        if (out == null) throw new IOException(\"Destination unavailable\");\n"
                + "        out.write(report);\n"
                + "      }\n"
                + "      message(\"Crash report exported\");\n"
                + "    } catch (IOException | SecurityException error) { message(\"Could not export crash report\"); }\n"
                + "  }\n"
                + "\n"
                + "  private void message(String text) {\n"
                + "    new AlertDialog.Builder(this).setMessage(text).setPositiveButton(\"OK\", null).show();\n"
                + "  }\n"
                + "}\n";
    }
}
