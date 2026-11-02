package dev.smallwright.worker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import dev.smallwright.protocol.CompilerDiagnostics;
import dev.smallwright.protocol.IBuildService;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;

public final class BuildService extends Service {
    static final String COMPILE_ERROR = "COMPILE_ERROR";
    static final String BUSY_ERROR = "ERROR: Worker busy";

    static final class JavaCompileFailure extends Exception {
        final EcjDiagnostics.Report diagnostics;

        JavaCompileFailure() {
            this(null);
        }

        JavaCompileFailure(EcjDiagnostics.Report diagnostics) {
            super(COMPILE_ERROR);
            this.diagnostics = diagnostics;
        }
    }

    private static final String TAG = "SmallwrightWorker";
    public static final String BIND_PERMISSION = "dev.smallwright.permission.BIND_BUILD_WORKER";
    private static final String CONTROLLER_PACKAGE = "dev.smallwright.controller";
    private static final int SUPPORTED_PROTOCOL = 1;
    private static final String GENERATED_PACKAGE = "dev.smallwright.generated.counter";
    private static final Pattern GENERATED_PACKAGE_PATTERN =
            Pattern.compile("dev\\.smallwright\\.generated\\.(?:counter|app[0-9a-f]{32})");
    private static final Pattern MANIFEST_START =
            Pattern.compile("^\\s*<manifest\\s+([^>]*)>.*", Pattern.DOTALL);
    private static final Pattern PACKAGE_ATTRIBUTE =
            Pattern.compile("(?:^|\\s)package\\s*=\\s*\"([^\"]+)\"");
    private static final String MANIFEST_NAME = "AndroidManifest.xml";
    private static final long MAX_SNAPSHOT_FILE_BYTES = 1024 * 1024;
    private static final long MAX_SNAPSHOT_BYTES = 16L * 1024 * 1024;
    private static final int MAX_SNAPSHOT_FILES = 500;
    private static final long MAX_MANIFEST_BYTES = 64 * 1024;
    private static final long MAX_GENERATED_SOURCE_BYTES = 4L * 1024 * 1024;
    private static final long MAX_SNAPSHOT_ARCHIVE_BYTES = 17L * 1024 * 1024;
    private static final long MAX_ARTIFACT_BYTES = 32L * 1024 * 1024;
    private static final int MAX_DEX_FILES = 100;
    private static final int MAX_JAVA_RESOURCES = 10_000;
    private static final Pattern KOTLIN_METADATA =
            Pattern.compile(
                    "[A-Za-z0-9]+Main/default/.*|.*\\.knm|META-INF/kotlin-project-structure-metadata\\.json");
    private static final Pattern JAVA_RESOURCE =
            Pattern.compile("[A-Za-z0-9_$][A-Za-z0-9_$.+=@#~,!/-]*");
    private static final long SNAPSHOT_TIMEOUT_MILLIS = 10_000;
    private static final long REQUEST_TIMEOUT_MILLIS = 600_000;
    private static final long PLATFORM_TIMEOUT_MILLIS = 60_000;
    private final BuildAdmission admission =
            new BuildAdmission(
                    REQUEST_TIMEOUT_MILLIS,
                    WorkerProcesses::terminateWorker,
                    SystemClock::elapsedRealtime);

    private final IBuildService.Stub binder =
            new IBuildService.Stub() {
                @Override
                public String handshake(int protocolVersion) {
                    enforceAuthorizedCaller();
                    if (protocolVersion == 2) {
                        return "worker-ready:" + CONTROLLER_PACKAGE + ":numeric-diagnostics-v2";
                    }
                    if (protocolVersion != SUPPORTED_PROTOCOL) {
                        throw new IllegalArgumentException(
                                "Unsupported protocol version: " + protocolVersion);
                    }
                    return "worker-ready:" + CONTROLLER_PACKAGE;
                }

                @Override
                public String aapt2Version() {
                    enforceAuthorizedCaller();
                    return admission.run(
                            BuildService.this::runAapt2Version,
                            () -> {
                                throw new IllegalStateException(BUSY_ERROR);
                            });
                }

                @Override
                public String toolchainProbe() {
                    enforceAuthorizedCaller();
                    return admission.run(
                            BuildService.this::runToolchainProbe,
                            () -> {
                                throw new IllegalStateException(BUSY_ERROR);
                            });
                }

                @Override
                public String build(ParcelFileDescriptor snapshot, ParcelFileDescriptor artifact) {
                    enforceAuthorizedCaller();
                    if (snapshot == null || artifact == null) {
                        throw new IllegalArgumentException(
                                "Snapshot and artifact descriptors are required");
                    }
                    return admission.run(
                            () -> buildSnapshot(BuildService.this, snapshot, artifact),
                            () -> {
                                closeQuietly(snapshot);
                                closeQuietly(artifact);
                                return BUSY_ERROR;
                            });
                }

                @Override
                public String buildWithDiagnostics(
                        ParcelFileDescriptor snapshot,
                        ParcelFileDescriptor artifact,
                        ParcelFileDescriptor diagnostics) {
                    try {
                        enforceAuthorizedCaller();
                        if (snapshot == null || artifact == null || diagnostics == null) {
                            throw new IllegalArgumentException(
                                    "Three build descriptors are required");
                        }
                        prepareDiagnosticOutput(snapshot, artifact, diagnostics);
                        return admission.run(
                                () ->
                                        buildSnapshot(
                                                BuildService.this, snapshot, artifact, diagnostics),
                                () -> BUSY_ERROR);
                    } finally {
                        closeQuietly(snapshot);
                        closeQuietly(artifact);
                        closeQuietly(diagnostics);
                    }
                }

                @Override
                public String usePlatform(ParcelFileDescriptor platform) {
                    enforceAuthorizedCaller();
                    if (platform == null) {
                        return "ERROR: The Android platform files were not given to the compiler";
                    }
                    try {
                        return admission.run(
                                () -> acceptPlatform(BuildService.this, platform),
                                () -> BUSY_ERROR);
                    } finally {
                        closeQuietly(platform);
                    }
                }

                @Override
                public String useLibraries(ParcelFileDescriptor[] libraries, String[] sha256) {
                    enforceAuthorizedCaller();
                    try {
                        if (libraries == null
                                || sha256 == null
                                || libraries.length != sha256.length
                                || Arrays.asList(libraries).contains(null)) {
                            return "ERROR: The libraries were not given to the compiler";
                        }
                        return admission.run(
                                () -> acceptLibraries(BuildService.this, libraries, sha256),
                                () -> BUSY_ERROR);
                    } finally {
                        if (libraries != null)
                            for (ParcelFileDescriptor library : libraries)
                                if (library != null) closeQuietly(library);
                    }
                }
            };

    static LibraryInputs libraryInputs(Context context) {
        return new LibraryInputs(new File(context.getNoBackupFilesDir(), "libraries"));
    }

    private static String acceptLibraries(
            Context context, ParcelFileDescriptor[] libraries, String[] sha256) {
        try {
            libraryInputs(context)
                    .accept(
                            java.util.Arrays.asList(sha256),
                            index ->
                                    new SnapshotInputStream(
                                            libraries[index], PLATFORM_TIMEOUT_MILLIS));
            return "OK";
        } catch (IOException refused) {
            return failureResult(refused);
        }
    }

    static PlatformInput platformInput(Context context) {
        return new PlatformInput(new File(context.getNoBackupFilesDir(), "platform"));
    }

    private static String acceptPlatform(Context context, ParcelFileDescriptor platform) {
        try (InputStream input = new SnapshotInputStream(platform, PLATFORM_TIMEOUT_MILLIS)) {
            platformInput(context).accept(input);
            return "OK";
        } catch (IOException refused) {
            return failureResult(refused);
        }
    }

    private static void prepareDiagnosticOutput(
            ParcelFileDescriptor snapshot,
            ParcelFileDescriptor artifact,
            ParcelFileDescriptor diagnostics) {
        try {
            StructStat report = Os.fstat(diagnostics.getFileDescriptor());
            StructStat source = Os.fstat(snapshot.getFileDescriptor());
            StructStat apk = Os.fstat(artifact.getFileDescriptor());
            if (!OsConstants.S_ISREG(report.st_mode)
                    || report.st_size != 0
                    || report.st_nlink != 1
                    || (Os.fcntlInt(diagnostics.getFileDescriptor(), OsConstants.F_GETFL, 0)
                                    & OsConstants.O_ACCMODE)
                            == OsConstants.O_RDONLY
                    || report.st_dev == source.st_dev && report.st_ino == source.st_ino
                    || report.st_dev == apk.st_dev && report.st_ino == apk.st_ino) {
                throw new IllegalArgumentException(
                        "Diagnostics require a separate empty writable regular file");
            }
            Os.lseek(diagnostics.getFileDescriptor(), 0, OsConstants.SEEK_SET);
        } catch (ErrnoException invalid) {
            throw new IllegalArgumentException("Invalid diagnostics descriptor", invalid);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        clearStaleScratch(getCacheDir());
    }

    static void clearStaleScratch(File cacheRoot) {
        deleteTree(new File(cacheRoot, "active-build"));
        deleteTree(new File(cacheRoot, "toolchain-probe"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void enforceAuthorizedCaller() {
        int callingUid = Binder.getCallingUid();
        String[] packages = getPackageManager().getPackagesForUid(callingUid);
        if (packages == null) {
            throw new SecurityException("Caller UID has no packages");
        }
        for (String packageName : packages) {
            if (packageName.equals(CONTROLLER_PACKAGE)
                    && checkCallingPermission(BIND_PERMISSION)
                            == PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException("Unauthorized build worker caller");
    }

    private String runAapt2Version() {
        String executable = getApplicationInfo().nativeLibraryDir + "/libaapt2_exec.so";
        try {
            return runTrackedAapt2(
                    new ProcessBuilder(executable, "version").redirectErrorStream(true), 4096);
        } catch (IOException exception) {
            Log.e(TAG, "Could not start packaged AAPT2 at " + executable, exception);
            throw new IllegalStateException("Could not start packaged AAPT2", exception);
        }
    }

    private String runToolchainProbe() {
        File scratch = new File(getCacheDir(), "toolchain-probe");
        deleteTree(scratch);
        if (!scratch.mkdirs()) {
            throw new IllegalStateException("Could not create toolchain probe scratch directory");
        }
        try {
            File validSource = new File(scratch, "Valid.java");
            File invalidSource = new File(scratch, "Invalid.java");
            File classes = new File(scratch, "classes");
            File dex = new File(scratch, "dex");
            Files.write(
                    validSource.toPath(),
                    "final class Valid { int value() { return 1; } }\n"
                            .getBytes(StandardCharsets.UTF_8));
            Files.write(
                    invalidSource.toPath(),
                    "final class Invalid { int value( { return 1; } }\n"
                            .getBytes(StandardCharsets.UTF_8));
            if (!classes.mkdirs() || !dex.mkdirs()) {
                throw new IllegalStateException(
                        "Could not create toolchain probe output directories");
            }
            File platform = platformInput(this).verified();
            CompileResult validResult =
                    compileResult(Collections.singletonList(validSource), classes, platform);
            CompileResult invalidResult =
                    compileResult(Collections.singletonList(invalidSource), classes, platform);
            File validClass = new File(classes, "Valid.class");
            if (!validResult.success
                    || !validClass.isFile()
                    || invalidResult.success
                    || invalidResult.text.isEmpty()) {
                throw new IllegalStateException(
                        "ECJ did not produce the expected valid/invalid results");
            }
            D8.run(
                    D8Command.builder()
                            .addProgramFiles(validClass.toPath())
                            .setMinApiLevel(31)
                            .setOutput(dex.toPath(), OutputMode.DexIndexed)
                            .build());
            if (!new File(dex, "classes.dex").isFile()) {
                throw new IllegalStateException("D8 did not produce classes.dex");
            }
            File apk = packageCounter(scratch, platform);
            verifyCounterApk(apk);
            return "ECJ valid class and invalid-source diagnostic; D8 classes.dex; unsigned counter APK";
        } catch (Throwable exception) {
            throw new IllegalStateException(
                    "Toolchain probe failed: " + exception.getMessage(), exception);
        } finally {
            try {
                deleteTree(scratch);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not remove toolchain probe scratch", exception);
            }
        }
    }

    static String buildSnapshot(
            Context context, ParcelFileDescriptor snapshot, ParcelFileDescriptor artifact) {
        return buildSnapshot(context, snapshot, artifact, null);
    }

    static String buildSnapshot(
            Context context,
            ParcelFileDescriptor snapshot,
            ParcelFileDescriptor artifact,
            ParcelFileDescriptor diagnostics) {
        try {
            return buildSnapshotWithReport(context, snapshot, artifact, diagnostics);
        } finally {
            closeQuietly(diagnostics);
        }
    }

    private static String buildSnapshotWithReport(
            Context context,
            ParcelFileDescriptor snapshot,
            ParcelFileDescriptor artifact,
            ParcelFileDescriptor diagnostics) {
        File scratch = new File(context.getCacheDir(), "active-build");
        return withBuildResources(
                scratch,
                snapshot,
                artifact,
                () -> {
                    deleteTree(scratch);
                    if (!scratch.mkdirs()) {
                        throw new IllegalStateException("Could not create build scratch directory");
                    }
                    try (InputStream input =
                            new SnapshotInputStream(snapshot, SNAPSHOT_TIMEOUT_MILLIS)) {
                        List<File> libraries = libraryInputs(context).takeSelected();
                        unpackSnapshot(input, scratch);
                        File apk = packageSnapshot(context, scratch, libraries);
                        verifyCounterApk(apk);
                        if (apk.length() > MAX_ARTIFACT_BYTES) {
                            throw new IllegalStateException("Generated APK exceeds size limit");
                        }
                        try (OutputStream output =
                                        new ParcelFileDescriptor.AutoCloseOutputStream(artifact);
                                InputStream apkInput = Files.newInputStream(apk.toPath())) {
                            copy(apkInput, output, MAX_ARTIFACT_BYTES);
                        }
                        return "OK";
                    } catch (Exception exception) {
                        if (diagnostics != null && exception instanceof JavaCompileFailure) {
                            EcjDiagnostics.Report report =
                                    ((JavaCompileFailure) exception).diagnostics;
                            if (report != null) {
                                List<CompilerDiagnostics.Problem> problems = new ArrayList<>();
                                for (EcjDiagnostics.Problem problem : report.problems) {
                                    problems.add(
                                            new CompilerDiagnostics.Problem(
                                                    problem.file,
                                                    problem.id,
                                                    problem.category,
                                                    problem.line));
                                }
                                byte[] wire =
                                        CompilerDiagnostics.create(
                                                        report.sourceSha256,
                                                        problems,
                                                        report.truncated)
                                                .toBytes();
                                try (OutputStream out =
                                        new ParcelFileDescriptor.AutoCloseOutputStream(
                                                diagnostics)) {
                                    out.write(wire);
                                    out.flush();
                                } catch (IOException unavailable) {
                                    // Preserve the compile sentinel. The controller rejects a
                                    // partial report.
                                }
                            }
                        }
                        return failureResult(exception);
                    }
                });
    }

    interface BuildWork {
        String run();
    }

    static String withBuildResources(
            File scratch, Closeable snapshot, Closeable artifact, BuildWork work) {
        try {
            return work.run();
        } finally {
            closeQuietly(snapshot);
            closeQuietly(artifact);
            try {
                deleteTree(scratch);
            } catch (RuntimeException exception) {
                Log.w(TAG, "Could not remove active build scratch", exception);
            }
        }
    }

    private static void closeQuietly(Closeable descriptor) {
        try {
            descriptor.close();
        } catch (IOException | RuntimeException ignored) {
            // Closing a transferred descriptor must not replace the build result.
        }
    }

    private static void unpackSnapshot(InputStream input, File scratch) throws IOException {
        File archiveFile = new File(scratch, "incoming-snapshot.zip");
        try (OutputStream archiveOutput = Files.newOutputStream(archiveFile.toPath())) {
            copy(input, archiveOutput, MAX_SNAPSHOT_ARCHIVE_BYTES);
        }
        long total = 0;
        int files = 0;
        int projectFiles = 0;
        Set<String> seen = new HashSet<>();
        try (ZipFile archive = new ZipFile(archiveFile)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                boolean manifest = MANIFEST_NAME.equals(name);
                files++;
                if (entry.isDirectory()
                        || !isAllowedSnapshotPath(name)
                        || (!manifest && ++projectFiles > MAX_SNAPSHOT_FILES)) {
                    throw new IOException("Snapshot contains a disallowed path");
                }
                if (!seen.add(name)) {
                    throw new IOException("Snapshot contains a duplicate path");
                }
                File output = new File(scratch, name);
                if (!output.getCanonicalPath()
                        .startsWith(scratch.getCanonicalPath() + File.separator)) {
                    throw new IOException("Snapshot path escapes scratch directory");
                }
                File parent = output.getParentFile();
                if (!parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create snapshot directory");
                }
                try (InputStream contents = archive.getInputStream(entry);
                        OutputStream destination = Files.newOutputStream(output.toPath())) {
                    if (manifest) {
                        copy(contents, destination, MAX_MANIFEST_BYTES);
                    } else {
                        total +=
                                copy(
                                        contents,
                                        destination,
                                        Math.min(
                                                MAX_SNAPSHOT_FILE_BYTES,
                                                MAX_SNAPSHOT_BYTES - total));
                    }
                }
            }
        } catch (java.util.zip.ZipException malformed) {
            throw new IOException("Snapshot archive is malformed", malformed);
        }
        if (files < 3 || !new File(scratch, MANIFEST_NAME).isFile()) {
            throw new IOException("Snapshot is incomplete");
        }
        String manifest =
                new String(
                        Files.readAllBytes(new File(scratch, "AndroidManifest.xml").toPath()),
                        StandardCharsets.UTF_8);
        String packageName = snapshotPackage(manifest);
        for (String name : seen) {
            if (!isAllowedSnapshotPath(name, packageName)) {
                throw new IOException("Snapshot path does not match generated package");
            }
        }
    }

    private static final String RESOURCE_QUALIFIERS = "(?:-[A-Za-z0-9+]{1,32}){0,8}";
    private static final String RESOURCE_NAME = "[a-z][a-z0-9_]{0,99}";
    private static final Pattern RESOURCE_PATH =
            Pattern.compile(
                    "res/(?:(?:anim|animator|color|interpolator|layout|menu|navigation|transition"
                            + "|values|xml)"
                            + RESOURCE_QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "\\.xml"
                            + "|(?:drawable|mipmap)"
                            + RESOURCE_QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "(?:\\.(?:xml|jpg|jpeg|webp|gif)|(?:\\.9)?\\.png)"
                            + "|font"
                            + RESOURCE_QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "\\.(?:ttf|otf|ttc|xml)"
                            + "|raw"
                            + RESOURCE_QUALIFIERS
                            + "/"
                            + RESOURCE_NAME
                            + "(?:\\.[a-z0-9]{1,16}){0,2})");
    private static final Pattern ASSET_PATH =
            Pattern.compile("assets(?:/[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}){1,6}");
    private static final String JAVA_FILE = "(?:[a-z][a-z0-9_]*/){0,6}[A-Za-z][A-Za-z0-9_]*\\.java";
    private static final Pattern ANY_GENERATED_JAVA_PATH =
            Pattern.compile(
                    "java/dev/smallwright/generated/(?:counter|app[0-9a-f]{32})/" + JAVA_FILE);

    static boolean isAllowedSnapshotPath(String name) {
        return name != null
                && (isProjectIndependentPath(name)
                        || ANY_GENERATED_JAVA_PATH.matcher(name).matches());
    }

    static boolean isAllowedSnapshotPath(String name, String packageName) {
        if (name == null || !GENERATED_PACKAGE_PATTERN.matcher(packageName).matches()) {
            return false;
        }
        return isProjectIndependentPath(name)
                || name.matches(
                        Pattern.quote("java/" + packageName.replace('.', '/') + "/") + JAVA_FILE);
    }

    private static boolean isProjectIndependentPath(String name) {
        return MANIFEST_NAME.equals(name)
                || ASSET_PATH.matcher(name).matches()
                || RESOURCE_PATH.matcher(name).matches();
    }

    static String snapshotPackage(String manifest) throws IOException {
        Matcher start = MANIFEST_START.matcher(manifest);
        if (!start.matches() || manifest.contains("sharedUserId")) {
            throw new IOException("Snapshot manifest violates generated-app policy");
        }
        Matcher attribute = PACKAGE_ATTRIBUTE.matcher(start.group(1));
        if (!attribute.find()) throw new IOException("Snapshot manifest has no generated package");
        String packageName = attribute.group(1);
        if (attribute.find() || !GENERATED_PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IOException("Snapshot manifest violates generated-app policy");
        }
        return packageName;
    }

    private static File packageSnapshot(Context context, File scratch, List<File> libraries)
            throws Exception {
        String packageName =
                snapshotPackage(
                        new String(
                                Files.readAllBytes(
                                        new File(scratch, "AndroidManifest.xml").toPath()),
                                StandardCharsets.UTF_8));
        File sourceRoot = new File(scratch, "source");
        File sourceJava = new File(scratch, "java");
        File resourceDirectory = new File(scratch, "res");
        if (!sourceRoot.mkdirs()) {
            throw new IOException("Could not create source workspace");
        }
        Files.copy(
                new File(scratch, "AndroidManifest.xml").toPath(),
                new File(sourceRoot, "AndroidManifest.xml").toPath());
        File classes = new File(scratch, "classes");
        if (!classes.mkdirs()) {
            throw new IOException("Could not create class output directory");
        }
        List<File> sources = collect(sourceJava, ".java");
        if (sources.isEmpty()) {
            throw new IOException("Snapshot has no Java source");
        }
        SortedMap<String, File> admitted = new TreeMap<>();
        for (File source : sources)
            admitted.put(
                    scratch.toPath().relativize(source.toPath()).toString().replace('\\', '/'),
                    source);
        File platform = platformInput(context).verified();
        File compiledResources = new File(scratch, "compiled-res.zip");
        runAapt2(
                context,
                "compile",
                "--dir",
                resourceDirectory.getAbsolutePath(),
                "-o",
                compiledResources.getAbsolutePath());
        File resourceApk = new File(scratch, "resources.apk");
        File generatedJava = new File(scratch, "generated-r");
        if (!generatedJava.mkdirs()) {
            throw new IOException("Could not create generated resource source directory");
        }
        List<String> link = new ArrayList<>();
        Collections.addAll(
                link,
                "link",
                "-I",
                platform.getAbsolutePath(),
                "--manifest",
                new File(sourceRoot, "AndroidManifest.xml").getAbsolutePath());
        File assetDirectory = new File(scratch, "assets");
        if (assetDirectory.isDirectory()) {
            Collections.addAll(link, "-A", assetDirectory.getAbsolutePath());
        }
        AndroidLibraries unpacked =
                AndroidLibraries.unpack(
                        libraries,
                        new File(scratch, "libraries"),
                        packageName,
                        Arrays.asList(Build.SUPPORTED_ABIS));
        File symbols = new File(scratch, "R.txt");
        List<String> overlays = new ArrayList<>();
        for (int index = 0; index < unpacked.android.size(); index++) {
            File resources = unpacked.android.get(index).resources;
            if (resources == null) continue;
            File compiled = new File(scratch, "library-res-" + index + ".zip");
            runAapt2(
                    context,
                    "compile",
                    "--dir",
                    resources.getAbsolutePath(),
                    "-o",
                    compiled.getAbsolutePath());
            Collections.addAll(overlays, "-R", compiled.getAbsolutePath());
        }
        if (overlays.isEmpty()) {
            link.add(compiledResources.getAbsolutePath());
        } else {
            link.addAll(overlays);
            Collections.addAll(
                    link,
                    "-R",
                    compiledResources.getAbsolutePath(),
                    "--auto-add-overlay",
                    "--output-text-symbols",
                    symbols.getAbsolutePath());
        }
        Collections.addAll(
                link,
                "--java",
                generatedJava.getAbsolutePath(),
                "-o",
                resourceApk.getAbsolutePath());
        runAapt2(context, link.toArray(new String[0]));
        sources.addAll(generatedResourceSources(generatedJava, packageName));
        Map<String, File> libraryR =
                symbols.isFile()
                        ? unpacked.writeRClasses(symbols, new File(scratch, "library-r"))
                        : Collections.emptyMap();
        sources.addAll(libraryR.values());
        sources.addAll(LambdaStubs.write(context.getAssets()::open, scratch));
        compileJavaOrThrow(sources, classes, platform, admitted, unpacked.jars);
        File dex = new File(scratch, "dex");
        if (!dex.mkdirs()) {
            throw new IOException("Could not create dex output directory");
        }
        List<File> program = generatedClassFiles(classes, packageName);
        for (String library : libraryR.keySet()) program.addAll(rClassFiles(classes, library));
        dex(
                program,
                platform,
                dex,
                new LibraryDex(new File(context.getNoBackupFilesDir(), "library-dex"))
                        .dexFiles(unpacked.jars, platform));
        File apk = new File(scratch, "unsigned.apk");
        packageApk(resourceApk, dexFiles(dex), unpacked.jars, unpacked.nativeLibraries, apk);
        return apk;
    }

    /** The R classes compiled for an Android library's package, and nothing else in it. */
    static List<File> rClassFiles(File classes, String packageName) {
        List<File> found = new ArrayList<>();
        File[] files = new File(classes, packageName.replace('.', '/')).listFiles();
        if (files == null) return found;
        for (File file : files)
            if (file.isFile()
                    && (file.getName().equals("R.class")
                            || file.getName().startsWith("R$")
                                    && file.getName().endsWith(".class"))) found.add(file);
        return found;
    }

    /** D8's output files in order: classes.dex, classes2.dex and so on, with no gaps or others. */
    static List<File> dexFiles(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("D8 produced no dex output");
        List<File> dexFiles = new ArrayList<>();
        for (int index = 1; index <= files.length; index++)
            dexFiles.add(
                    new File(directory, index == 1 ? "classes.dex" : "classes" + index + ".dex"));
        for (File file : dexFiles)
            if (!file.isFile() || files.length > MAX_DEX_FILES)
                throw new IOException("D8 produced unexpected dex output");
        return dexFiles;
    }

    static void dex(List<File> classFiles, File platform, File output) throws Exception {
        dex(classFiles, platform, output, Collections.emptyList());
    }

    static void dex(List<File> classFiles, File platform, File output, List<File> libraries)
            throws Exception {
        D8Command.Builder d8 =
                D8Command.builder()
                        .setMinApiLevel(31)
                        .addLibraryFiles(platform.toPath())
                        .setOutput(output.toPath(), OutputMode.DexIndexed);
        for (File classFile : classFiles) {
            d8.addProgramFiles(classFile.toPath());
        }
        for (File library : libraries) {
            d8.addProgramFiles(library.toPath());
        }
        D8.run(d8.build());
    }

    static String failureResult(Exception exception) {
        if (exception instanceof JavaCompileFailure) return COMPILE_ERROR;
        if (exception instanceof SnapshotInputStream.Timeout) return "SNAPSHOT_TIMEOUT";
        String message = exception.getMessage();
        return BoundedDiagnostics.limit(
                "ERROR: " + (message == null ? exception.getClass().getSimpleName() : message));
    }

    static void compileJavaOrThrow(List<File> sources, File classes, File bootClasspath)
            throws JavaCompileFailure {
        compileJavaOrThrow(sources, classes, bootClasspath, Collections.emptyList());
    }

    private static void compileJavaOrThrow(
            List<File> sources, File classes, File bootClasspath, List<File> libraries)
            throws JavaCompileFailure {
        if (!compileResult(sources, classes, bootClasspath, libraries).success) {
            // ECJ diagnostics can contain source text and private workspace paths.
            throw new JavaCompileFailure();
        }
    }

    /** Reports numeric errors in the {@code admitted} project sources, keyed by project path. */
    static void compileJavaOrThrow(
            List<File> sources, File classes, File bootClasspath, SortedMap<String, File> admitted)
            throws JavaCompileFailure, IOException {
        compileJavaOrThrow(sources, classes, bootClasspath, admitted, Collections.emptyList());
    }

    /** Compiles with the {@code libraries} JAR files on the class path after the platform. */
    static void compileJavaOrThrow(
            List<File> sources,
            File classes,
            File bootClasspath,
            SortedMap<String, File> admitted,
            List<File> libraries)
            throws JavaCompileFailure, IOException {
        if (admitted.isEmpty() || !sources.containsAll(admitted.values())) {
            compileJavaOrThrow(sources, classes, bootClasspath, libraries);
            return;
        }
        BoundedDiagnostics diagnostics = new BoundedDiagnostics();
        PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(diagnostics, StandardCharsets.UTF_8), true);
        EcjDiagnostics.Result result =
                EcjDiagnostics.compile(
                        compileArguments(sources, classes, bootClasspath, libraries),
                        writer,
                        admitted);
        writer.flush();
        if (!result.success) throw new JavaCompileFailure(result.diagnostics);
    }

    private static List<File> collect(File directory, String extension) {
        List<File> results = new ArrayList<>();
        File[] children = directory.listFiles();
        if (children == null) {
            return results;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                results.addAll(collect(child, extension));
            } else if (child.getName().endsWith(extension)) {
                results.add(child);
            }
        }
        return results;
    }

    /**
     * AAPT2's R.java for the app's package, and the Manifest.java it adds when the manifest
     * declares permissions; anything else is refused.
     */
    static List<File> generatedResourceSources(File generatedJava, String packageName)
            throws IOException {
        File directory = new File(generatedJava, packageName.replace('.', '/'));
        File r = new File(directory, "R.java").getCanonicalFile();
        File manifest = new File(directory, "Manifest.java").getCanonicalFile();
        List<File> sources = new ArrayList<>();
        sources.add(r);
        boolean hasR = false;
        for (File file : collect(generatedJava, "")) {
            File canonical = file.getCanonicalFile();
            if (canonical.equals(r)) hasR = true;
            else if (canonical.equals(manifest)) sources.add(manifest);
            else throw new IOException("AAPT2 generated unexpected resource source");
        }
        for (File source : sources)
            if (!source.isFile() || source.length() > MAX_GENERATED_SOURCE_BYTES || !hasR)
                throw new IOException("AAPT2 generated unexpected resource source");
        return sources;
    }

    static List<File> generatedClassFiles(File classes) {
        return generatedClassFiles(classes, GENERATED_PACKAGE);
    }

    static List<File> generatedClassFiles(File classes, String packageName) {
        if (!GENERATED_PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("Invalid generated package");
        }
        File packageDirectory = new File(classes, packageName.replace('.', '/'));
        List<File> classFiles = collect(packageDirectory, ".class");
        if (classFiles.isEmpty()) {
            throw new IllegalStateException("ECJ produced no classes in the generated package");
        }
        return classFiles;
    }

    private static long copy(InputStream input, OutputStream output, long limit)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new IOException("Input exceeds size limit");
            }
            output.write(buffer, 0, read);
        }
        return total;
    }

    private File packageCounter(File scratch, File platform) throws Exception {
        File sourceRoot = new File(scratch, "counter-src");
        File resourceDirectory = new File(sourceRoot, "res");
        File javaDirectory = new File(sourceRoot, "java/dev/smallwright/fixture/counter");
        if (!resourceDirectory.mkdirs() || !javaDirectory.mkdirs()) {
            throw new IOException("Could not create counter source directories");
        }
        Files.write(
                new File(sourceRoot, "AndroidManifest.xml").toPath(),
                ("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                + "package=\"dev.smallwright.fixture.counter\">"
                                + "<application android:label=\"Smallwright Counter\">"
                                + "<activity android:name=\".MainActivity\" android:exported=\"true\">"
                                + "<intent-filter><action android:name=\"android.intent.action.MAIN\"/>"
                                + "<category android:name=\"android.intent.category.LAUNCHER\"/>"
                                + "</intent-filter></activity></application></manifest>\n")
                        .getBytes(StandardCharsets.UTF_8));
        File layoutDirectory = new File(resourceDirectory, "layout");
        if (!layoutDirectory.mkdirs()) {
            throw new IOException("Could not create counter layout directory");
        }
        Files.write(
                new File(layoutDirectory, "activity_main.xml").toPath(),
                ("<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                + "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" "
                                + "android:gravity=\"center\" android:text=\"Counter: 0\"/>\n")
                        .getBytes(StandardCharsets.UTF_8));
        File counterSource = new File(javaDirectory, "MainActivity.java");
        Files.write(
                counterSource.toPath(),
                ("package dev.smallwright.fixture.counter;\n"
                                + "public final class MainActivity extends android.app.Activity {\n"
                                + "  @Override public void onCreate(android.os.Bundle state) {\n"
                                + "    super.onCreate(state);\n"
                                + "    setContentView(new android.widget.TextView(this));\n"
                                + "  }\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8));
        File classes = new File(scratch, "counter-classes");
        if (!classes.mkdirs()) {
            throw new IOException("Could not create counter classes directory");
        }
        CompileResult result =
                compileResult(Collections.singletonList(counterSource), classes, platform);
        if (!result.success) {
            throw new IllegalStateException("ECJ rejected counter source: " + result.text);
        }
        File counterDex = new File(scratch, "counter-dex");
        if (!counterDex.mkdirs()) {
            throw new IOException("Could not create counter dex directory");
        }
        D8.run(
                D8Command.builder()
                        .addProgramFiles(
                                new File(
                                                classes,
                                                "dev/smallwright/fixture/counter/MainActivity.class")
                                        .toPath())
                        .setMinApiLevel(31)
                        .setOutput(counterDex.toPath(), OutputMode.DexIndexed)
                        .build());
        File compiledResources = new File(scratch, "compiled-res.zip");
        runAapt2(
                "compile",
                "--dir",
                resourceDirectory.getAbsolutePath(),
                "-o",
                compiledResources.getAbsolutePath());
        File resourceApk = new File(scratch, "resources.apk");
        runAapt2(
                "link",
                "-I",
                platform.getAbsolutePath(),
                "--manifest",
                new File(sourceRoot, "AndroidManifest.xml").getAbsolutePath(),
                "-R",
                compiledResources.getAbsolutePath(),
                "-o",
                resourceApk.getAbsolutePath());
        File apk = new File(scratch, "counter-unsigned.apk");
        packageApk(
                resourceApk,
                Collections.singletonList(new File(counterDex, "classes.dex")),
                Collections.emptyList(),
                Collections.emptyMap(),
                apk);
        return apk;
    }

    private void runAapt2(String... arguments) throws IOException {
        runAapt2(this, arguments);
    }

    private static void runAapt2(Context context, String... arguments) throws IOException {
        String executable = context.getApplicationInfo().nativeLibraryDir + "/libaapt2_exec.so";
        String[] command = new String[arguments.length + 1];
        command[0] = executable;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        runTrackedAapt2(new ProcessBuilder(command).redirectErrorStream(true), 16_384);
    }

    private static String runTrackedAapt2(ProcessBuilder command, int outputLimit)
            throws IOException {
        Process child = WorkerProcesses.startAapt2(command);
        try {
            return runBounded(child, 30_000, outputLimit);
        } finally {
            WorkerProcesses.finished(child);
        }
    }

    /**
     * The APK: AAPT2's resources, every dex file in order, the native libraries by their {@code
     * lib/<abi>/} path (stored and 16 KB-aligned, so they load from the APK on 4 KB and 16 KB page
     * devices), and the Java resources of the library JARs (the first JAR holding a path wins;
     * classes, signatures, {@code .so} files and paths the APK owns are left out). A {@code .so}
     * among AAPT2's resources or assets is refused.
     */
    static void packageApk(
            File resourceApk,
            List<File> dexFiles,
            List<File> jars,
            Map<String, File> nativeLibraries,
            File output)
            throws IOException {
        if (resourceApk.length() > MAX_ARTIFACT_BYTES) {
            throw new IOException("Generated APK input exceeds size limit");
        }
        Set<String> written = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(resourceApk.toPath()));
                CountingOutputStream counted =
                        new CountingOutputStream(
                                Files.newOutputStream(output.toPath()), MAX_ARTIFACT_BYTES);
                ZipOutputStream zip = new ZipOutputStream(counted)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                if (isNativeName(entry.getName()))
                    throw new IOException(
                            "The app's resources or assets hold native code, which only a"
                                    + " library's jni/ folder may bring: "
                                    + entry.getName());
                byte[] contents = readEntry(input, buffer);
                if (entry.getName().equals("resources.arsc"))
                    zip.putNextEntry(stored(entry.getName(), contents, counted.count, 4));
                else zip.putNextEntry(new ZipEntry(entry.getName()));
                zip.write(contents);
                zip.closeEntry();
                written.add(entry.getName());
            }
            for (File dex : dexFiles) {
                if (dex.length() > MAX_ARTIFACT_BYTES) {
                    throw new IOException("Generated APK input exceeds size limit");
                }
                zip.putNextEntry(new ZipEntry(dex.getName()));
                try (InputStream dexInput = Files.newInputStream(dex.toPath())) {
                    int read;
                    while ((read = dexInput.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            for (Map.Entry<String, File> nativeLibrary : nativeLibraries.entrySet()) {
                if (nativeLibrary.getValue().length() > MAX_ARTIFACT_BYTES)
                    throw new IOException("Generated APK input exceeds size limit");
                byte[] contents = Files.readAllBytes(nativeLibrary.getValue().toPath());
                zip.putNextEntry(
                        stored(nativeLibrary.getKey(), contents, counted.count, 16 * 1024));
                zip.write(contents);
                zip.closeEntry();
                written.add(nativeLibrary.getKey());
            }
            int resources = 0;
            for (File jar : jars) {
                try (ZipFile library = new ZipFile(jar)) {
                    Enumeration<? extends ZipEntry> entries = library.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry resource = entries.nextElement();
                        String name = resource.getName();
                        if (resource.isDirectory()
                                || !isJavaResource(name)
                                || !written.add(name)
                                || ++resources > MAX_JAVA_RESOURCES) continue;
                        zip.putNextEntry(new ZipEntry(name));
                        try (InputStream contents = library.getInputStream(resource)) {
                            zip.write(readEntry(contents, buffer));
                        }
                        zip.closeEntry();
                    }
                }
            }
        }
    }

    /** A library JAR entry the app keeps as a Java resource, such as META-INF/services files. */
    static boolean isJavaResource(String name) {
        if (name.length() > 512
                || !JAVA_RESOURCE.matcher(name).matches()
                || name.endsWith(".class")
                || name.contains("//")) return false;
        for (String segment : name.split("/"))
            if (segment.equals("..") || segment.equals(".")) return false;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return !upper.equals("META-INF/MANIFEST.MF")
                && !upper.equals("META-INF/INDEX.LIST")
                && !upper.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)")
                && !upper.startsWith("META-INF/MAVEN/")
                && !upper.startsWith("META-INF/VERSIONS/")
                && !name.equals("AndroidManifest.xml")
                && !name.equals("resources.arsc")
                && !name.matches("classes[0-9]*\\.dex")
                && !isNativeName(name)
                && !KOTLIN_METADATA.matcher(name).matches()
                && !name.startsWith("res/")
                && !name.startsWith("lib/")
                && !name.startsWith("assets/");
    }

    private static byte[] readEntry(InputStream input, byte[] buffer) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_ARTIFACT_BYTES) {
                throw new IOException("AAPT2 resource entry exceeds size limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isNativeName(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".so");
    }

    private static ZipEntry stored(String name, byte[] contents, long outputOffset, int alignment) {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(contents.length);
        entry.setCompressedSize(contents.length);
        CRC32 crc = new CRC32();
        crc.update(contents);
        entry.setCrc(crc.getValue());
        entry.setExtra(alignmentExtra(outputOffset, name, alignment));
        return entry;
    }

    /**
     * Zipalign's extra field (ID 0xD935: the alignment, then zero padding) that makes an entry
     * whose local header starts at {@code outputOffset} begin its data on {@code alignment} bytes.
     * The signer reads the same field and keeps that alignment.
     */
    static byte[] alignmentExtra(long outputOffset, String entryName, int alignment) {
        int nameLength = entryName.getBytes(StandardCharsets.UTF_8).length;
        int padding =
                (int)
                        ((alignment - ((outputOffset + 30 + nameLength + 6) % alignment))
                                % alignment);
        byte[] extra = new byte[6 + padding];
        extra[0] = (byte) 0x35;
        extra[1] = (byte) 0xd9;
        extra[2] = (byte) (2 + padding);
        extra[3] = (byte) ((2 + padding) >> 8);
        extra[4] = (byte) alignment;
        extra[5] = (byte) (alignment >> 8);
        return extra;
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        long count;
        private final long limit;

        CountingOutputStream(OutputStream output, long limit) {
            super(output);
            this.limit = limit;
        }

        @Override
        public void write(int value) throws IOException {
            if (count >= limit) throw new IOException("Generated APK exceeds size limit");
            out.write(value);
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length > limit - count) throw new IOException("Generated APK exceeds size limit");
            out.write(bytes, offset, length);
            count += length;
        }
    }

    private static void verifyCounterApk(File apk) throws IOException {
        try (ZipFile archive = new ZipFile(apk)) {
            if (archive.getEntry("AndroidManifest.xml") == null
                    || archive.getEntry("resources.arsc") == null
                    || archive.getEntry("classes.dex") == null) {
                throw new IllegalStateException(
                        "Counter APK is missing a manifest, resources, or DEX");
            }
        }
    }

    static String[] compileArguments(File source, File classes, File bootClasspath) {
        return compileArguments(Collections.singletonList(source), classes, bootClasspath);
    }

    static String[] compileArguments(List<File> sources, File classes, File bootClasspath) {
        return compileArguments(sources, classes, bootClasspath, Collections.emptyList());
    }

    static String[] compileArguments(
            List<File> sources, File classes, File bootClasspath, List<File> libraries) {
        StringBuilder classpath = new StringBuilder(bootClasspath.getAbsolutePath());
        for (File library : libraries)
            classpath.append(File.pathSeparatorChar).append(library.getAbsolutePath());
        List<String> arguments = new ArrayList<>();
        Collections.addAll(
                arguments,
                "-1.8",
                "-proc:none",
                "-bootclasspath",
                bootClasspath.getAbsolutePath(),
                "-classpath",
                classpath.toString(),
                "-d",
                classes.getAbsolutePath());
        for (File source : sources) {
            arguments.add(source.getAbsolutePath());
        }
        return arguments.toArray(new String[0]);
    }

    private static String compile(File source, File classes, File bootClasspath) {
        return compile(Collections.singletonList(source), classes, bootClasspath);
    }

    private static String compile(List<File> sources, File classes, File bootClasspath) {
        return compileResult(sources, classes, bootClasspath).text;
    }

    private static final class CompileResult {
        final boolean success;
        final String text;

        CompileResult(boolean success, String text) {
            this.success = success;
            this.text = text;
        }
    }

    private static CompileResult compileResult(
            List<File> sources, File classes, File bootClasspath) {
        return compileResult(sources, classes, bootClasspath, Collections.emptyList());
    }

    private static CompileResult compileResult(
            List<File> sources, File classes, File bootClasspath, List<File> libraries) {
        BoundedDiagnostics diagnostics = new BoundedDiagnostics();
        PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(diagnostics, StandardCharsets.UTF_8), true);
        boolean success =
                BatchCompiler.compile(
                        compileArguments(sources, classes, bootClasspath, libraries),
                        writer,
                        writer,
                        null);
        writer.flush();
        String text = diagnostics.text();
        if (sources.size() == 1) {
            String name = sources.get(0).getName();
            if (name.equals("Valid.java") && !success) {
                throw new IllegalStateException("ECJ rejected valid source: " + text);
            }
            if (name.equals("Invalid.java") && success) {
                throw new IllegalStateException("ECJ accepted malformed source");
            }
        }
        if (!success && text.isEmpty()) {
            throw new IllegalStateException("ECJ failed without diagnostics");
        }
        return new CompileResult(success, text);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            throw new IllegalStateException("Could not remove toolchain probe scratch");
        }
    }

    static String runBounded(Process process, long timeoutMillis, int outputLimit) {
        InputStream input = process.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread reader =
                new Thread(
                        () -> {
                            try {
                                copyBounded(input, output, outputLimit);
                            } catch (IOException exception) {
                                readFailure.set(exception);
                            }
                        },
                        "aapt2-output-reader");
        reader.setDaemon(true);
        reader.start();

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        try {
            while (process.isAlive() && readFailure.get() == null) {
                long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= 0) {
                    throw new IllegalStateException("Packaged AAPT2 timed out");
                }
                reader.join(Math.min(remainingMillis, 50));
            }
            IOException failure = readFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Could not read packaged AAPT2 output", failure);
            }
            while (reader.isAlive()) {
                long remainingMillis = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= 0) {
                    throw new IllegalStateException("Packaged AAPT2 timed out");
                }
                reader.join(Math.min(remainingMillis, 50));
            }
            failure = readFailure.get();
            if (failure != null) {
                throw new IllegalStateException("Could not read packaged AAPT2 output", failure);
            }
            String text = output.toString().trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Packaged AAPT2 failed: " + text);
            }
            return text;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Packaged AAPT2 was interrupted", exception);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
                // The process is being terminated below; there is no output left to preserve.
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            reader.interrupt();
        }
    }

    private static void copyBounded(InputStream input, ByteArrayOutputStream output, int limit)
            throws IOException {
        byte[] buffer = new byte[512];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > limit) {
                throw new IOException("Packaged AAPT2 output exceeds limit");
            }
            output.write(buffer, 0, read);
        }
    }
}
