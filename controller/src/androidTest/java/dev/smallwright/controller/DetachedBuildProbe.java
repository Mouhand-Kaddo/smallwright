package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import com.android.apksig.ApkVerifier;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import java.io.File;
import org.json.JSONObject;

/** Preparation and post-reconnect verification only. Never builds, installs or reads keys. */
final class DetachedBuildProbe {
    private static final String PREFS = "detached-a33-fixture-v1";
    private static final String TITLE = "A33 detached local build";
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of("prepare", "armed", "verify", "restoreSelection").contains(phase))
            throw new AssertionError("Unknown detached phase");
        Context context = inst.getTargetContext();
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if ("prepare".equals(phase)) {
            if (pins.contains("reserved")) throw new AssertionError("One-shot fixture reserved");
            save(
                    pins.edit()
                            .putBoolean("reserved", true)
                            .putString(
                                    "previousProject",
                                    context.getSharedPreferences(
                                                    "MainActivity", Context.MODE_PRIVATE)
                                            .getString("active-project", null)));
        } else if (!pins.contains("project")) throw new AssertionError("No prepared fixture");
        if ("restoreSelection".equals(phase)) {
            if (!pins.contains("verified")
                    || !pins.getString("project", "")
                            .equals(
                                    context.getSharedPreferences(
                                                    "MainActivity", Context.MODE_PRIVATE)
                                            .getString("active-project", null)))
                throw new AssertionError("Fixture not verified or selection changed");
            String previous = pins.getString("previousProject", null);
            if (previous != null)
                new ProjectRegistry(
                                new File(context.getFilesDir(), "projects"), context.getFilesDir())
                        .get(previous);
            save(
                    context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                            .edit()
                            .putString("active-project", previous));
            return "Detached selection restored; owned project/source/artifacts retained\n";
        }
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui);
            onMain(
                    inst,
                    () -> {
                        requireIdle(ui);
                        if (field(ui, "pendingRecovery") != null)
                            throw new AssertionError("Existing work must settle");
                    });
            if ("prepare".equals(phase)) {
                stage = "CREATE";
                CompilerMeasurementProbe.createProject(inst, ui, TITLE);
                ProjectIdentity project =
                        mainValue(inst, () -> (ProjectIdentity) field(ui, "activeProject"));
                if (project == null || !TITLE.equals(project.title()))
                    throw new AssertionError("Wrong project");
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName()));
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("New source not pristine");
                String original = source.readSource();
                if (original.indexOf("\"Increment\"") < 0
                        || original.indexOf("\"Increment\"")
                                != original.lastIndexOf("\"Increment\""))
                    throw new AssertionError("Default label changed");
                stage = "SAVE_SOURCE";
                String edited = original.replace("\"Increment\"", "\"Detached add one\"");
                if (source.writeSource(0, edited) != 1)
                    throw new AssertionError("Unexpected source revision");
                save(
                        pins.edit()
                                .putString(
                                        "sourceSha256", ActiveBuildInterruptionProbe.hash(edited))
                                .putString(
                                        "snapshotSha256",
                                        snapshotHash(source.readRecoverySources(1))));
                // No Build click: the user will open this committed revision without USB.
                return "Detached prepare PASS "
                        + summary(project, pins)
                                .put("sourceRevision", 1)
                                .put("buildPerformed", false)
                                .put("modelRequestMade", false)
                        + "\n";
            }
            stage = "OWN_SOURCE";
            ProjectIdentity project =
                    mainValue(inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            if (project == null
                    || !TITLE.equals(project.title())
                    || !project.id().equals(pins.getString("project", "")))
                throw new AssertionError("Wrong owned project");
            ProjectSourceStore source = new ProjectSourceStore(project);
            String sha = pins.getString("sourceSha256", "");
            if (!sha.equals(ActiveBuildInterruptionProbe.hash(source.readSource())))
                throw new AssertionError("Prepared content changed");
            if ("armed".equals(phase)) {
                if (source.revision() != 1
                        || !pins.getString("snapshotSha256", "")
                                .equals(snapshotHash(source.readRecoverySources(1)))
                        || new File(project.directory(), "artifacts/verified-artifact.properties")
                                .exists()
                        || new File(project.directory(), "artifacts/app-v1.apk").exists())
                    throw new AssertionError("Detached fixture already built or changed");
                onMain(
                        inst,
                        () -> {
                            if ((int) field(ui, "editorRevision") != 1
                                    || field(ui, "signedArtifact") != null
                                    || !source.readSource()
                                            .contentEquals(
                                                    ((TextBox) field(ui, "source")).getText()))
                                throw new AssertionError("Saved editor differs");
                        });
                return "Detached armed PASS "
                        + summary(project, pins)
                                .put("sourceRevision", 1)
                                .put("noArtifactBeforeDetachment", true)
                        + "\n";
            }
            stage = "VERIFY_READY";
            if (source.revision() != 2) throw new AssertionError("Expected one user Build");
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), 2);
            if (receipt.versionCode != 1) throw new AssertionError("Unexpected build version");
            receipt.artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(context, receipt.apk, 1, project.packageName());
            if (!new ApkVerifier.Builder(receipt.apk).build().verify().isVerified()
                    || !receipt.signerFingerprint.equals(
                            new ProjectSigner(project).verifyExisting(context, receipt.apk)))
                throw new AssertionError("Actual signature or project signer differs");
            awaitReady(inst, ui, receipt.sha256);
            JSONObject verified =
                    summary(project, pins)
                            .put("sourceRevision", 2)
                            .put("version", 1)
                            .put("apkSha256", receipt.sha256)
                            .put("apkBytes", receipt.apk.length())
                            .put("apkPath", receipt.apk.getAbsolutePath())
                            .put("signerSha256", receipt.signerFingerprint)
                            .put("detachedExecutionProvenByThisProbe", false);
            save(pins.edit().putString("verified", verified.toString()));
            return "Detached saved artifact verification PASS " + verified + "\n";
        } finally {
            while (!awaitIdle(inst, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString("stream", "Detached original work pending; no Stop/retry\n");
                inst.sendStatus(0, pending);
            }
            onMain(inst, ui::finish);
        }
    }

    /** A separately pinned second detached-build fixture; the original four phases stay intact. */
    static String runSecond(Instrumentation inst, String phase) throws Exception {
        stage = "GUARD";
        if (!java.util.List.of("prepare2", "armed2", "verify2", "restoreSelection2")
                .contains(phase)) throw new AssertionError("Unknown detached phase 2");
        Context context = inst.getTargetContext();
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required");
        SharedPreferences pins = context.getSharedPreferences(PREFS + "-2", Context.MODE_PRIVATE);
        SharedPreferences selection =
                context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
        if ("prepare2".equals(phase)) {
            if (pins.contains("reserved")) throw new AssertionError("Fixture 2 is one-shot");
            save(
                    pins.edit()
                            .putBoolean("reserved", true)
                            .putBoolean(
                                    "previousProjectWasNull",
                                    selection.getString("active-project", null) == null)
                            .putString(
                                    "previousProject",
                                    selection.getString("active-project", null)));
        } else if (!pins.contains("reserved") || !pins.contains("project")) {
            throw new AssertionError("No prepared fixture 2");
        }
        if ("restoreSelection2".equals(phase)) {
            if (!pins.contains("verified") || pins.contains("restored"))
                throw new AssertionError("Fixture 2 is not verified or restore already ran");
            ProjectIdentity project = fixture2(context, pins);
            verifyRecordedArtifact(context, project, pins);
            if (!project.id().equals(selection.getString("active-project", null)))
                throw new AssertionError("Fixture 2 selection changed");
            String previous =
                    pins.getBoolean("previousProjectWasNull", false)
                            ? null
                            : pins.getString("previousProject", null);
            if (previous != null)
                new ProjectRegistry(
                                new File(context.getFilesDir(), "projects"), context.getFilesDir())
                        .get(previous);
            save(selection.edit().putString("active-project", previous));
            save(pins.edit().putBoolean("restored", true));
            return "Detached fixture 2 selection restored; owned project/source/artifacts retained\n";
        }
        Activity ui =
                inst.startActivitySync(
                        new Intent(context, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            ControllerUi.awaitProjectBinding(inst, ui);
            ActiveWorkerStopProbe.awaitWorker(inst, ui);
            onMain(
                    inst,
                    () -> {
                        requireIdle(ui);
                        if (field(ui, "pendingRecovery") != null)
                            throw new AssertionError("Existing work must settle");
                    });
            if ("prepare2".equals(phase)) {
                stage = "CREATE2";
                CompilerMeasurementProbe.createProject(inst, ui, TITLE + " 2");
                ProjectIdentity project =
                        mainValue(inst, () -> (ProjectIdentity) field(ui, "activeProject"));
                if (project == null || !(TITLE + " 2").equals(project.title()))
                    throw new AssertionError("Wrong fixture 2 project");
                String firstProject =
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .getString("project", null);
                if (project.id().equals(firstProject))
                    throw new AssertionError("Fixture 2 inherited the original project");
                assertPackageAbsent(context, project.packageName());
                ProjectSourceStore source = new ProjectSourceStore(project);
                if (source.revision() != 0 || !source.isPristineDefaultSnapshot())
                    throw new AssertionError("Fixture 2 source is not a new default snapshot");
                assertNoArtifact(project);
                String original = source.readSource();
                if (original.indexOf("\"Increment\"") < 0
                        || original.indexOf("\"Increment\"")
                                != original.lastIndexOf("\"Increment\""))
                    throw new AssertionError("Fixture 2 default label changed");
                String edited = original.replace("\"Increment\"", "\"Detached add one 2\"");
                if (source.writeSource(0, edited) != 1)
                    throw new AssertionError("Unexpected fixture 2 source revision");
                save(
                        pins.edit()
                                .putString("project", project.id())
                                .putString("package", project.packageName())
                                .putString(
                                        "sourceSha256", ActiveBuildInterruptionProbe.hash(edited))
                                .putString(
                                        "snapshotSha256",
                                        snapshotHash(source.readRecoverySources(1)))
                                .putBoolean("noPreArtifact", true));
                return "Detached prepare 2 PASS "
                        + summary2(project, pins)
                                .put("sourceRevision", 1)
                                .put("buildPerformed", false)
                                .put("modelRequestMade", false)
                                .put("noPreArtifact", true)
                        + "\n";
            }
            ProjectIdentity project =
                    mainValue(inst, () -> (ProjectIdentity) field(ui, "activeProject"));
            requireFixture2Selection(project, selection, pins);
            ProjectSourceStore source = new ProjectSourceStore(project);
            int currentRevision = source.revision();
            String sourceSha = pins.getString("sourceSha256", "");
            if (!sourceSha.equals(ActiveBuildInterruptionProbe.hash(source.readSource()))
                    || !pins.getString("snapshotSha256", "")
                            .equals(snapshotHash(source.readRecoverySources(currentRevision))))
                throw new AssertionError("Fixture 2 saved source changed");
            if ("armed2".equals(phase)) {
                if (pins.contains("armed")
                        || pins.contains("verified")
                        || !pins.getBoolean("noPreArtifact", false)
                        || currentRevision != 1
                        || !pins.getString("snapshotSha256", "")
                                .equals(snapshotHash(source.readRecoverySources(1))))
                    throw new AssertionError("Fixture 2 is not at its one-shot unbuilt revision");
                assertNoArtifact(project);
                assertPackageAbsent(context, project.packageName());
                onMain(
                        inst,
                        () -> {
                            if ((int) field(ui, "editorRevision") != 1
                                    || field(ui, "signedArtifact") != null
                                    || !source.readSource()
                                            .contentEquals(
                                                    ((TextBox) field(ui, "source")).getText()))
                                throw new AssertionError("Fixture 2 saved editor differs");
                        });
                save(pins.edit().putBoolean("armed", true));
                return "Detached armed 2 PASS "
                        + summary2(project, pins)
                                .put("sourceRevision", 1)
                                .put("noArtifactBeforeDetachment", true)
                        + "\n";
            }
            if (!pins.getBoolean("armed", false) || pins.contains("verified"))
                throw new AssertionError("Fixture 2 verification is not armed or already ran");
            stage = "VERIFY_READY2";
            if (source.revision() != 2) throw new AssertionError("Expected one fixture 2 Build");
            SignedArtifactReceipt.Record receipt =
                    SignedArtifactReceipt.read(
                            project.directory(), project.id(), project.packageName(), 2);
            if (receipt.versionCode != 1) throw new AssertionError("Unexpected fixture 2 version");
            receipt.artifact.verifyUnchanged();
            ArtifactValidator.validateUnsigned(context, receipt.apk, 1, project.packageName());
            if (!new ApkVerifier.Builder(receipt.apk).build().verify().isVerified()
                    || !receipt.signerFingerprint.equals(
                            new ProjectSigner(project).verifyExisting(context, receipt.apk)))
                throw new AssertionError("Fixture 2 signature or project signer differs");
            awaitReady(inst, ui, receipt.sha256);
            JSONObject verified =
                    summary2(project, pins)
                            .put("sourceRevision", 2)
                            .put("version", 1)
                            .put("apkSha256", receipt.sha256)
                            .put("apkBytes", receipt.apk.length())
                            .put("apkPath", receipt.apk.getAbsolutePath())
                            .put("signerSha256", receipt.signerFingerprint)
                            .put("detachedExecutionProvenByThisProbe", false);
            save(pins.edit().putString("verified", verified.toString()));
            return "Detached saved artifact verification 2 PASS " + verified + "\n";
        } finally {
            while (!awaitIdle(inst, ui, 30)) {
                android.os.Bundle pending = new android.os.Bundle();
                pending.putString(
                        "stream", "Detached fixture 2 original work pending; no Stop/retry\n");
                inst.sendStatus(0, pending);
            }
            onMain(inst, ui::finish);
        }
    }

    private static ProjectIdentity fixture2(Context context, SharedPreferences pins)
            throws Exception {
        ProjectIdentity project =
                new ProjectRegistry(
                                new File(context.getFilesDir(), "projects"), context.getFilesDir())
                        .get(pins.getString("project", ""));
        if (!project.id().equals(pins.getString("project", ""))
                || !project.packageName().equals(pins.getString("package", ""))
                || !(TITLE + " 2").equals(project.title()))
            throw new AssertionError("Fixture 2 identity changed");
        ProjectSourceStore source = new ProjectSourceStore(project);
        int currentRevision = source.revision();
        if (currentRevision < 1
                || !pins.getString("sourceSha256", "")
                        .equals(ActiveBuildInterruptionProbe.hash(source.readSource()))
                || !pins.getBoolean("noPreArtifact", false)
                || !pins.getString("snapshotSha256", "")
                        .equals(snapshotHash(source.readRecoverySources(currentRevision))))
            throw new AssertionError("Fixture 2 saved source changed");
        return project;
    }

    private static void requireFixture2Selection(
            ProjectIdentity project, SharedPreferences selection, SharedPreferences pins) {
        if (project == null
                || !project.id().equals(pins.getString("project", ""))
                || !project.packageName().equals(pins.getString("package", ""))
                || !(TITLE + " 2").equals(project.title())
                || !project.id().equals(selection.getString("active-project", null)))
            throw new AssertionError("Wrong fixture 2 project selection");
    }

    private static void assertNoArtifact(ProjectIdentity project) {
        File artifacts = new File(project.directory(), "artifacts");
        if (new File(artifacts, "verified-artifact.properties").exists()
                || new File(artifacts, "app-v1.apk").exists())
            throw new AssertionError("Fixture 2 already has a build artifact");
    }

    private static void assertPackageAbsent(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            throw new AssertionError("Fixture 2 package is already installed");
        } catch (android.content.pm.PackageManager.NameNotFoundException expected) {
            // A new project must not inherit an installed package.
        }
    }

    private static void verifyRecordedArtifact(
            Context context, ProjectIdentity project, SharedPreferences pins) throws Exception {
        ProjectSourceStore source = new ProjectSourceStore(project);
        if (source.revision() != 2) throw new AssertionError("Fixture 2 revision changed");
        JSONObject expected = new JSONObject(pins.getString("verified", "{}"));
        SignedArtifactReceipt.Record receipt =
                SignedArtifactReceipt.read(
                        project.directory(), project.id(), project.packageName(), 2);
        receipt.artifact.verifyUnchanged();
        ArtifactValidator.validateUnsigned(context, receipt.apk, 1, project.packageName());
        if (!expected.getString("project").equals(project.id())
                || !expected.getString("package").equals(project.packageName())
                || !expected.getString("title").equals(project.title())
                || !expected.getString("sourceSha256").equals(pins.getString("sourceSha256", ""))
                || expected.getBoolean("previousProjectWasNull")
                        != pins.getBoolean("previousProjectWasNull", false)
                || !expected.optString("previousProject", "")
                        .equals(pins.getString("previousProject", ""))
                || !expected.getBoolean("noPreArtifact")
                || receipt.versionCode != 1
                || !new ApkVerifier.Builder(receipt.apk).build().verify().isVerified()
                || !receipt.signerFingerprint.equals(
                        new ProjectSigner(project).verifyExisting(context, receipt.apk))
                || !expected.getString("apkSha256").equals(receipt.sha256)
                || expected.getLong("apkBytes") != receipt.apk.length()
                || !expected.getString("apkPath").equals(receipt.apk.getAbsolutePath())
                || !expected.getString("signerSha256").equals(receipt.signerFingerprint))
            throw new AssertionError("Fixture 2 exact artifact or signer changed");
    }

    private static JSONObject summary2(ProjectIdentity project, SharedPreferences pins)
            throws Exception {
        return new JSONObject()
                .put("project", project.id())
                .put("package", project.packageName())
                .put("title", project.title())
                .put("sourceSha256", pins.getString("sourceSha256", ""))
                .put("previousProjectWasNull", pins.getBoolean("previousProjectWasNull", false))
                .put("previousProject", pins.getString("previousProject", null))
                .put("noPreArtifact", pins.getBoolean("noPreArtifact", false));
    }

    private static JSONObject summary(ProjectIdentity project, SharedPreferences pins)
            throws Exception {
        return new JSONObject()
                .put("project", project.id())
                .put("package", project.packageName())
                .put("title", TITLE)
                .put("sourceSha256", pins.getString("sourceSha256", ""));
    }

    private static void save(SharedPreferences.Editor edit) {
        if (!edit.commit()) throw new AssertionError("Fixture pins not saved");
    }
}
