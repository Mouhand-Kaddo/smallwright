package dev.smallwright.worker;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** Queued ART test: real ECJ capture and unchanged production snapshot failure status. */
final class CompileDiagnosticsProbe {
    static String run(Context worker) throws Exception {
        if (Process.myUid() != worker.getApplicationInfo().uid) {
            throw new AssertionError("Compiler probe must run under worker UID");
        }
        File root =
                Files.createTempDirectory(
                                worker.getCacheDir().toPath(), "compile-diagnostics-probe-")
                        .toFile();
        try {
            File main = new File(root, "MainActivity.java");
            byte[] source =
                    ("package dev.smallwright.generated.counter;\n"
                                    + "public class MainActivity extends android.app.Activity {\n"
                                    + "  PRIVATE_MISSING_TYPE value;\n}\n")
                            .getBytes(StandardCharsets.UTF_8);
            Files.write(main.toPath(), source);
            File boot = BuildService.platformInput(worker).verified();
            List<File> sources = Arrays.asList(main);
            File classes = new File(root, "failed-classes");
            if (!classes.mkdir()) throw new IOException("Could not create probe class output");
            try {
                BuildService.compileJavaOrThrow(sources, classes, boot, admitted(main));
                throw new AssertionError("Real unresolved type compiled");
            } catch (BuildService.JavaCompileFailure failure) {
                EcjDiagnostics.Report report = failure.diagnostics;
                if (!"COMPILE_ERROR".equals(BuildService.failureResult(failure))
                        || report == null
                        || report.truncated
                        || report.problems.isEmpty()
                        || report.problems.size()
                                > dev.smallwright.protocol.CompilerDiagnostics.MAX_PROBLEMS
                        || !report.sourceSha256.equals(
                                dev.smallwright.protocol.CompilerDiagnostics.sourceSetSha256(
                                        new java.util.TreeMap<>(java.util.Map.of("main", source))))
                        || report.problems.stream()
                                .noneMatch(
                                        problem ->
                                                problem.id == 16777218
                                                        && problem.category == 40
                                                        && problem.line == 3)) {
                    throw new AssertionError("Missing exact numeric compiler error");
                }
            }
            if (!Arrays.equals(source, Files.readAllBytes(main.toPath()))) {
                throw new AssertionError("Compilation changed source");
            }

            File buildCache = new File(root, "build-cache");
            if (!buildCache.mkdir()) throw new IOException("Could not create probe build cache");
            Context disposable =
                    new ContextWrapper(worker) {
                        @Override
                        public File getCacheDir() {
                            return buildCache;
                        }
                    };
            File snapshot = new File(root, "malformed.zip");
            ProductionSnapshotProbe.snapshot(snapshot, source);
            File artifact = new File(root, "rejected.apk");
            try (ParcelFileDescriptor input =
                            ParcelFileDescriptor.open(
                                    snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
                    ParcelFileDescriptor output =
                            ParcelFileDescriptor.open(
                                    artifact,
                                    ParcelFileDescriptor.MODE_CREATE
                                            | ParcelFileDescriptor.MODE_TRUNCATE
                                            | ParcelFileDescriptor.MODE_READ_WRITE)) {
                String result = BuildService.buildSnapshot(disposable, input, output);
                if (!"COMPILE_ERROR".equals(result)
                        || artifact.length() != 0
                        || new File(buildCache, "active-build").exists()) {
                    throw new AssertionError("Compile failure violated snapshot cleanup or status");
                }
            }
            Files.write(
                    main.toPath(),
                    ("package dev.smallwright.generated.counter;\n"
                                    + "public class MainActivity extends android.app.Activity {}\n")
                            .getBytes(StandardCharsets.UTF_8));
            File recovered = new File(root, "recovered-classes");
            if (!recovered.mkdir()) throw new IOException("Could not create recovery class output");
            BuildService.compileJavaOrThrow(sources, recovered, boot, admitted(main));
            if (!new File(recovered, "dev/smallwright/generated/counter/MainActivity.class")
                    .isFile()) {
                throw new AssertionError("Delegate failed to emit real classes");
            }
            return "ECJ diagnostics: actual source hash/type ID/category/line, exact COMPILE_ERROR, "
                    + "zero failed artifact, scratch cleanup and valid class recovery\n";
        } finally {
            removeOwnTree(root);
        }
    }

    private static void removeOwnTree(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) removeOwnTree(child);
        if (!file.delete()) throw new IOException("Could not remove probe-owned compiler scratch");
    }

    private static java.util.SortedMap<String, File> admitted(File main) {
        return new java.util.TreeMap<>(java.util.Map.of("main", main));
    }
}
