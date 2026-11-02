package dev.smallwright.controller;

import static dev.smallwright.controller.PhoneAlphaCounterProbe.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.util.List;

/** Uses only the owned live XML test project. Acceptance taps are synthetic test input. */
final class TestHandoffProbe {
    private static String stage = "GUARD";

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation in, String phase, boolean approved) throws Exception {
        stage = "GUARD";
        if (!List.of("inspect", "accept", "verify").contains(phase)
                || "accept".equals(phase) && !approved) throw new AssertionError("Guard");
        Context target = in.getTargetContext();
        if (target.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !target.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Phone unavailable");
        SharedPreferences baseline = target.getSharedPreferences("layout-edit-acceptance-v1", 0);
        SharedPreferences live = target.getSharedPreferences("live-layout-update-v1", 0);
        SharedPreferences pins = target.getSharedPreferences("test-handoff-probe-v1", 0);
        if (!live.getBoolean("completed", false)
                || "accept".equals(phase) && pins.getBoolean("accepted", false))
            throw new AssertionError("Baseline/duplicate");
        Activity ui =
                in.startActivitySync(
                        new Intent(target, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ControllerUi.awaitProjectBinding(in, ui);
        String previousProject =
                mainValue(in, () -> ((ProjectIdentity) field(ui, "activeProject")).id());
        String previousChat = mainValue(in, () -> ((ChatStore.Chat) field(ui, "selectedChat")).id);
        boolean dialog = false;
        try {
            stage = "SELECT";
            selectProject(in, ui, baseline.getString("project", ""));
            ProjectIdentity project =
                    mainValue(in, () -> (ProjectIdentity) field(ui, "activeProject"));
            ProjectSourceStore source = new ProjectSourceStore(project);
            SignedArtifactReceipt.Record receipt = verifiedRecord(target, project, 4);
            if (receipt.versionCode != 4
                    || !receipt.sha256.equals(live.getString("apkSha256", ""))
                    || !snapshotHash(source.readRecoverySources(4))
                            .equals(live.getString("finalSnapshot", "")))
                throw new AssertionError("Saved identity changed");
            android.content.pm.PackageInfo installed =
                    target.getPackageManager().getPackageInfo(project.packageName(), 0);
            if (installed.getLongVersionCode() != 4
                    || installed.applicationInfo == null
                    || !VerifiedArtifact.capture(
                                    new java.io.File(installed.applicationInfo.sourceDir))
                            .sha256()
                            .equals(receipt.sha256))
                throw new AssertionError("Installed identity changed");
            awaitReady(in, ui, receipt.sha256);
            stage = "ADAPTER";
            if (!InstalledArtifact.matches(target, project, receipt.artifact))
                throw new AssertionError("Exact installation not recognized");
            VerifiedArtifact previous =
                    VerifiedArtifact.capture(new java.io.File(baseline.getString("apk", "")));
            if (InstalledArtifact.matches(target, project, previous))
                throw new AssertionError("Installed v4 recognized as older v3");
            AppTestStatus.State stored;
            try {
                stored =
                        new AppTestStatus(project.directory(), target.getFilesDir())
                                .observe(receipt.sha256, receipt.sha256);
            } catch (java.io.IOException failure) {
                stage =
                        "STORE_"
                                + ("Linked test acceptance path".equals(failure.getMessage())
                                        ? "LINK"
                                        : "Invalid test acceptance record"
                                                        .equals(failure.getMessage())
                                                ? "FORMAT"
                                                : "OTHER");
                throw failure;
            }
            if ("inspect".equals(phase) && stored != AppTestStatus.State.INSTALLED_UNTESTED)
                throw new AssertionError("Unexpected stored status");
            stage = "OBSERVE";
            String expected =
                    "verify".equals(phase)
                            ? "User accepted this installed build."
                            : "Installed — awaiting your test.";
            awaitText(in, ui, expected);
            if ("accept".equals(phase)) {
                stage = "CANCEL";
                onMain(
                        in,
                        () -> {
                            reveal(button(ui, Labels.ACCEPT));
                            visibleClick(button(ui, Labels.ACCEPT));
                        });
                waitNode(in, target, "android.widget.Button", "Accept this build");
                dialog = true;
                click(in, target, "Cancel");
                dialog = false;
                awaitText(in, ui, "Installed — awaiting your test.");
                stage = "ACCEPT";
                onMain(in, () -> visibleClick(button(ui, Labels.ACCEPT)));
                waitNode(in, target, "android.widget.Button", "Accept this build");
                dialog = true;
                click(in, target, "Accept this build");
                dialog = false;
                awaitText(in, ui, "User accepted this installed build.");
                if (!pins.edit()
                        .putBoolean("accepted", true)
                        .putString("apkSha256", receipt.sha256)
                        .commit()) throw new AssertionError("Pin write");
            }
            if (source.revision() != 4
                    || !snapshotHash(source.readRecoverySources(4))
                            .equals(live.getString("finalSnapshot", "")))
                throw new AssertionError("Source changed");
            boolean platformAncestorLink = false;
            java.nio.file.Path base = target.getFilesDir().toPath().toAbsolutePath().normalize();
            for (java.nio.file.Path part =
                            project.directory().toPath().toAbsolutePath().normalize();
                    part != null;
                    part = part.getParent())
                if (!part.startsWith(base) && java.nio.file.Files.isSymbolicLink(part))
                    platformAncestorLink = true;
            return "Test handoff "
                    + phase
                    + " PASS apkSha256="
                    + receipt.sha256
                    + " sourceRevision=4 version=4 platformAncestorLinkOutsidePrivate="
                    + platformAncestorLink
                    + " testAssistedAcceptance="
                    + "accept".equals(phase)
                    + "\n";
        } finally {
            if (dialog) click(in, target, "Cancel");
            String originalStage = stage;
            boolean restored = false;
            try {
                stage = "RESTORE";
                selectProject(in, ui, previousProject);
                selectChat(in, ui, previousChat);
                if (!mainValue(
                        in,
                        () ->
                                previousProject.equals(
                                                ((ProjectIdentity) field(ui, "activeProject")).id())
                                        && previousChat.equals(
                                                ((ChatStore.Chat) field(ui, "selectedChat")).id)))
                    throw new AssertionError("Selection restoration failed");
                if (!pins.edit().putBoolean("selectionRestored", true).commit())
                    throw new AssertionError("Restoration pin failed");
                restored = true;
            } finally {
                if (restored) stage = originalStage;
                onMain(in, ui::finish);
            }
        }
    }

    private static void awaitText(Instrumentation in, Activity ui, String text) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < end) {
            if (mainValue(in, () -> hasText(ui.getWindow().getDecorView(), text))) return;
            Thread.sleep(50);
        }
        String actual =
                mainValue(in, () -> ((TextBox) field(ui, "testHandoff")).getText().toString());
        stage =
                "OBSERVE_"
                        + (actual.equals("Build an app to begin testing.")
                                ? "NO_ARTIFACT"
                                : actual.equals("Checking installed app...")
                                        ? "PENDING"
                                        : actual.startsWith("Could not verify")
                                                ? "UNAVAILABLE"
                                                : actual.startsWith("Ready to install")
                                                        ? "READY"
                                                        : actual.startsWith("User accepted")
                                                                ? "ACCEPTED"
                                                                : "OTHER");
        throw new AssertionError("Expected handoff unavailable");
    }

    private static boolean hasText(View view, String text) {
        if (view instanceof TextView value && text.contentEquals(value.getText())) return true;
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                if (hasText(group.getChildAt(i), text)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void selectProject(Instrumentation in, Activity ui, String id) throws Exception {
        List<ProjectIdentity> projects =
                mainValue(in, () -> (List<ProjectIdentity>) field(ui, "projectList"));
        for (int i = 0; i < projects.size(); i++)
            if (id.equals(projects.get(i).id())) {
                int n = i;
                onMain(in, () -> ((Picker) field(ui, "projectPicker")).select(n));
                in.waitForIdleSync();
                ControllerUi.awaitProjectBinding(in, ui);
                return;
            }
        throw new AssertionError("Project missing");
    }

    @SuppressWarnings("unchecked")
    private static void selectChat(Instrumentation in, Activity ui, String id) throws Exception {
        List<ChatStore.Chat> chats = mainValue(in, () -> (List<ChatStore.Chat>) field(ui, "chats"));
        for (int i = 0; i < chats.size(); i++)
            if (id.equals(chats.get(i).id)) {
                int n = i;
                onMain(in, () -> ((Picker) field(ui, "chatPicker")).select(n));
                in.waitForIdleSync();
                return;
            }
        throw new AssertionError("Chat missing");
    }
}
