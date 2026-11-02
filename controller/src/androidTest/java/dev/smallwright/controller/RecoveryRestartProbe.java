package dev.smallwright.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import dev.smallwright.protocol.IBuildService;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;

/** Two invocations separated by an external force-stop exercise real SQLite and Binder recovery. */
final class RecoveryRestartProbe {
    private static final String MARKER = "RecoveredAfterProcessKill";

    private RecoveryRestartProbe() {}

    static String prepare(Context target) throws Exception {
        String id = "probe_" + UUID.randomUUID().toString().replace("-", "");
        Context isolated = isolated(target, id);
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        source.initialize();
        int initial = source.revision();
        RunJournal journal = new RunJournal(isolated);
        journal.create(id, initial);
        RunJournal.Snapshot attempt =
                journal.advance(id, 0, RunJournal.State.PLANNING, initial, null, null);
        attempt =
                journal.advance(
                        id, attempt.sequence, RunJournal.State.EDITING, initial, null, null);
        attempt =
                journal.advance(
                        id, attempt.sequence, RunJournal.State.APPLYING, initial, null, null);
        int committed =
                source.writeSource(initial, source.readSource().replace("Increment", MARKER));
        String buildId = "build_" + id;
        journal.advance(id, attempt.sequence, RunJournal.State.BUILDING, committed, buildId, 7, 1);
        File artifacts = new File(isolated.getFilesDir(), "projects/counter-alpha/artifacts");
        File current = RunRecovery.directory(artifacts, buildId);
        Files.write(
                new File(current, "unsigned.partial.apk").toPath(),
                "interrupted build".getBytes(StandardCharsets.UTF_8));
        File prior = RunRecovery.directory(artifacts, "prior_" + id);
        Files.write(
                new File(prior, "app.apk").toPath(),
                "earlier complete artifact".getBytes(StandardCharsets.UTF_8));
        return id;
    }

    static String verify(Context target, String id) throws Exception {
        if (id == null || !id.matches("probe_[0-9a-f]{32}"))
            throw new AssertionError("Invalid recovery probe identity");
        Context isolated = isolated(target, id);
        ProjectSourceStore source = new ProjectSourceStore(isolated);
        RunJournal journal = new RunJournal(isolated);
        int revision = source.revision();
        if (revision != 1 || !source.readSource().contains(MARKER))
            throw new AssertionError("Committed source was not preserved across process kill");
        if (journal.recover(revision) != 0)
            throw new AssertionError("Committed BUILDING attempt was closed");
        RunJournal.Snapshot pending = journal.pendingBuild(revision);
        if (pending == null
                || !id.equals(pending.runId)
                || pending.versionCode == null
                || pending.versionCode != 1
                || pending.sourceRevision != revision)
            throw new AssertionError("Committed build was not resumable");
        File artifacts = new File(isolated.getFilesDir(), "projects/counter-alpha/artifacts");
        File partial = new File(new File(artifacts, pending.buildId), "unsigned.partial.apk");
        if (!partial.isFile()) throw new AssertionError("Interrupted partial is missing");
        File prior = new File(new File(artifacts, "prior_" + id), "app.apk");
        byte[] priorBytes = Files.readAllBytes(prior.toPath());

        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<IBuildService> remote = new AtomicReference<>();
        ServiceConnection connection =
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        remote.set(IBuildService.Stub.asInterface(binder));
                        connected.countDown();
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        remote.set(null);
                    }
                };
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(
                                        "dev.smallwright.worker",
                                        "dev.smallwright.worker.BuildService"));
        if (!target.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw new AssertionError("Worker bind refused");
        try {
            if (!connected.await(15, TimeUnit.SECONDS) || remote.get() == null)
                throw new AssertionError("Worker unavailable");
            if (remote.get().handshake(1) == null)
                throw new AssertionError("Worker handshake failed");
            VerifiedArtifact artifact =
                    new LocalBuildCoordinator(isolated, source)
                            .buildCommittedRevision(
                                    revision, pending.versionCode, pending.buildId, remote.get());
            artifact.verifyUnchanged();
            journal.advance(
                    id, pending.sequence, RunJournal.State.READY_TO_TEST, revision, null, null);
            if (journal.load(id).state != RunJournal.State.READY_TO_TEST
                    || journal.pendingBuild(revision) != null
                    || partial.exists())
                throw new AssertionError("Recovery did not complete and discard partial output");
            if (!java.util.Arrays.equals(priorBytes, Files.readAllBytes(prior.toPath())))
                throw new AssertionError("Recovery changed an earlier artifact");
            try (ZipFile apk =
                    new ZipFile(new File(new File(artifacts, pending.buildId), "app.apk"))) {
                String dex =
                        new String(
                                apk.getInputStream(apk.getEntry("classes.dex")).readAllBytes(),
                                StandardCharsets.ISO_8859_1);
                if (!dex.contains(MARKER))
                    throw new AssertionError("Recovered APK omitted committed source");
            }
            return "Recovered committed revision after process kill; verified APK "
                    + artifact.sha256()
                    + "; no model request or installation";
        } finally {
            target.unbindService(connection);
        }
    }

    private static Context isolated(Context target, String id) throws Exception {
        File root = new File(target.getNoBackupFilesDir(), "recovery-restart-" + id);
        if (!root.mkdirs() && !root.isDirectory())
            throw new AssertionError("Recovery probe storage unavailable");
        File cache = new File(root, "cache");
        if (!cache.mkdirs() && !cache.isDirectory())
            throw new AssertionError("Recovery probe cache unavailable");
        return new ContextWrapper(target) {
            @Override
            public File getFilesDir() {
                return root;
            }

            @Override
            public File getNoBackupFilesDir() {
                return root;
            }

            @Override
            public File getCacheDir() {
                return cache;
            }
        };
    }
}
