package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Normal controller UI and real phone builds; only newly created projects are changed. */
final class PersistentChatsAcceptanceProbe {
    private static final String PREFS = "persistent-chats-acceptance-v1";
    private static String stage = "GUARD";

    private PersistentChatsAcceptanceProbe() {}

    static String failureStage() {
        return stage;
    }

    static String run(Instrumentation instrumentation, String observationId, boolean approved)
            throws Exception {
        stage = "GUARD";
        Context context = instrumentation.getTargetContext();
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                || !context.getSystemService(PowerManager.class).isInteractive())
            throw new AssertionError("Unlock required before normal controller UI");
        SharedPreferences pins = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!approved
                || observationId == null
                || !observationId.matches("[0-9a-f]{32}")
                || pins.contains("observationId"))
            throw new AssertionError("One-shot local acceptance guard");
        if (!pins.edit().putString("observationId", observationId).commit())
            throw new AssertionError("Could not reserve acceptance observation");
        stage = "OPEN_CONTROLLER";
        Intent launch =
                new Intent(instrumentation.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(launch);
        ControllerUi.awaitProjectBinding(instrumentation, activity);
        if (value(instrumentation, activity, "pendingRecovery", Object.class) != null
                || value(instrumentation, activity, "startupBlocked", Boolean.class)
                || !value(instrumentation, activity, "newProject", Control.class).isEnabled()) {
            Activity blocked = activity;
            instrumentation.runOnMainSync(blocked::finish);
            throw new AssertionError("Existing controller work prevents owned project creation");
        }
        String previousProject =
                value(instrumentation, activity, "activeProject", ProjectIdentity.class).id();
        String previousChat =
                value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
        List<OwnedProject> owned = new ArrayList<>();
        String suffix = observationId;
        Throwable originalFailure = null;
        try {
            for (int index = 0; index < 2; index++) {
                stage = "CREATE_PROJECT_" + index;
                ProjectIdentity project =
                        createProject(
                                instrumentation,
                                activity,
                                "Chat acceptance " + suffix + " " + index);
                OwnedProject state = new OwnedProject(project, "CHAT_SCOPE_" + index);
                owned.add(state);
                if (!pins.edit().putString("project" + index, project.id()).commit())
                    throw new AssertionError("Could not pin owned project");
                ChatStore store = value(instrumentation, activity, "chatStore", ChatStore.class);
                state.firstChat =
                        value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
                store.appendMessage(
                        project.id(), state.firstChat, "user", state.marker + "_FIRST", 0);
                stage = "FIRST_BUILD_" + index;
                state.first = build(instrumentation, activity, state.marker + "_SOURCE_ONE");
                state.firstRevision = new ProjectSourceStore(project).revision();
                ContextPreviewGate gate =
                        value(instrumentation, activity, "contextGate", ContextPreviewGate.class);
                ContextPreviewGate.Preview oldApproval =
                        gate.previewChat(
                                state.firstChat,
                                "Keep the first chat request",
                                state.firstRevision);

                stage = "NEW_CHAT_" + index;
                Ui.openMenu(instrumentation, activity);
                click(instrumentation, activity, Labels.NEW_CHAT);
                state.secondChat =
                        value(instrumentation, activity, "selectedChat", ChatStore.Chat.class).id;
                if (state.firstChat.equals(state.secondChat)
                        || store.listChats(project.id()).size() != 2)
                    throw new AssertionError("New chat did not create a separate conversation");
                assertReceipt(instrumentation, activity, state, state.firstRevision, state.first);
                store.appendMessage(
                        project.id(),
                        state.secondChat,
                        "user",
                        state.marker + "_SECOND",
                        state.firstRevision);
                stage = "SECOND_BUILD_" + index;
                state.second = build(instrumentation, activity, state.marker + "_SOURCE_TWO");
                state.revision = new ProjectSourceStore(project).revision();
                state.source = new ProjectSourceStore(project).readSource();
                if (state.revision != state.firstRevision + 1
                        || state.second.versionCode != state.first.versionCode + 1
                        || !state.first.signerFingerprint.equals(state.second.signerFingerprint)
                        || state.first.apk.equals(state.second.apk))
                    throw new AssertionError(
                            "Chats did not share one increasing app/build lineage");
                stage = "STALE_REJECTION_" + index;
                selectChat(instrumentation, activity, state.firstChat);
                assertHistory(instrumentation, activity, state.marker + "_FIRST");
                try {
                    gate.approve(oldApproval, "Keep the first chat request", state.firstRevision);
                    throw new AssertionError(
                            "Older chat source approval survived a newer revision");
                } catch (IllegalStateException expected) {
                    /* exact source approval refused */
                }
                try {
                    ProjectSourceStore stale = new ProjectSourceStore(project);
                    stale.commit(
                            state.firstRevision,
                            java.util.Map.of(
                                    stale.mainPath(),
                                    "// stale change must never become current\n"
                                            .getBytes(StandardCharsets.UTF_8)),
                            java.util.Set.of());
                    throw new AssertionError("Older chat proposal overwrote newer source");
                } catch (IllegalStateException expected) {
                    /* real immutable store refused */
                }
                assertSource(state);
                assertReceipt(instrumentation, activity, state, state.revision, state.second);
                state.first.artifact.verifyUnchanged();
            }
            if (owned.get(0).project.packageName().equals(owned.get(1).project.packageName())
                    || owned.get(0)
                            .second
                            .signerFingerprint
                            .equals(owned.get(1).second.signerFingerprint))
                throw new AssertionError("Separate projects shared an app or signer identity");
            stage = "REOPEN_CONTROLLER";
            Activity firstActivity = activity;
            instrumentation.runOnMainSync(firstActivity::finish);
            instrumentation.waitForIdleSync();
            activity = instrumentation.startActivitySync(launch);
            ControllerUi.awaitProjectBinding(instrumentation, activity);
            if (!owned.get(1)
                            .project
                            .id()
                            .equals(
                                    value(
                                                    instrumentation,
                                                    activity,
                                                    "activeProject",
                                                    ProjectIdentity.class)
                                            .id())
                    || !owned.get(1)
                            .firstChat
                            .equals(
                                    value(
                                                    instrumentation,
                                                    activity,
                                                    "selectedChat",
                                                    ChatStore.Chat.class)
                                            .id))
                throw new AssertionError("Selected project/chat did not survive controller reopen");
            for (OwnedProject state : owned) {
                stage = "REOPEN_SCOPE_" + state.marker;
                selectProject(instrumentation, activity, state.project.id());
                if (value(instrumentation, activity, "chatPicker", Picker.class).getCount() != 2)
                    throw new AssertionError("Two persistent chats were not recovered");
                selectChat(instrumentation, activity, state.firstChat);
                assertHistory(instrumentation, activity, state.marker + "_FIRST");
                selectChat(instrumentation, activity, state.secondChat);
                assertHistory(instrumentation, activity, state.marker + "_SECOND");
                assertSource(state);
                assertReceipt(instrumentation, activity, state, state.revision, state.second);
                state.first.artifact.verifyUnchanged();
            }
            StringBuilder evidence =
                    new StringBuilder(
                            "Persistent chats: normal New project/New chat UI; two projects/two chats each; "
                                    + "seeded histories remain scoped after activity reopen; four real phone local builds; "
                                    + "older chat approval and parsed source proposal refused at stale revision; "
                                    + "package/signer/retained APK lineage unchanged by chat creation/switching. "
                                    + "No model call or installation; no live model/run-origin or process-death claim.\n");
            for (OwnedProject state : owned)
                evidence.append("project=")
                        .append(state.project.id())
                        .append(" package=")
                        .append(state.project.packageName())
                        .append(" source=")
                        .append(state.revision)
                        .append(" sourceSha256=")
                        .append(hexSha256(state.source.getBytes(StandardCharsets.UTF_8)))
                        .append(" version=")
                        .append(state.second.versionCode)
                        .append(" signer=")
                        .append(state.second.signerFingerprint)
                        .append(" firstApk=")
                        .append(state.first.apk.getAbsolutePath())
                        .append(" firstSha256=")
                        .append(state.first.sha256)
                        .append(" currentApk=")
                        .append(state.second.apk.getAbsolutePath())
                        .append(" currentSha256=")
                        .append(state.second.sha256)
                        .append('\n');
            stage = "VERIFIED";
            if (!pins.edit()
                    .putString("evidence", evidence.toString())
                    .putBoolean("verified", true)
                    .commit()) throw new AssertionError("Could not retain acceptance evidence");
            return evidence.toString();
        } catch (Exception | AssertionError failure) {
            originalFailure = failure;
            throw failure;
        } finally {
            // Existing source/history/keys never enter this probe. Preserve all owned test data
            // too.
            if (!activity.isDestroyed()) {
                try {
                    // Do not Stop, retry, or change project binding while an owned build runs.
                    while (!PhoneAlphaCounterProbe.awaitIdle(instrumentation, activity, 30)) {
                        instrumentation.waitForIdleSync();
                    }
                    Activity closing = activity;
                    try {
                        selectProject(instrumentation, activity, previousProject);
                        selectChat(instrumentation, activity, previousChat);
                        if (!previousProject.equals(
                                        value(
                                                        instrumentation,
                                                        activity,
                                                        "activeProject",
                                                        ProjectIdentity.class)
                                                .id())
                                || !previousChat.equals(
                                        value(
                                                        instrumentation,
                                                        activity,
                                                        "selectedChat",
                                                        ChatStore.Chat.class)
                                                .id))
                            throw new AssertionError(
                                    "Previous project/chat selection was not restored");
                        if (!pins.edit().putBoolean("selectionRestored", true).commit())
                            throw new AssertionError("Could not record selection restoration");
                    } finally {
                        instrumentation.runOnMainSync(closing::finish);
                    }
                } catch (Exception | AssertionError restorationFailure) {
                    if (originalFailure != null) originalFailure.addSuppressed(restorationFailure);
                    else throw restorationFailure;
                }
            }
        }
    }

    private static ProjectIdentity createProject(
            Instrumentation instrumentation, Activity activity, String title) throws Exception {
        CompilerMeasurementProbe.createProject(instrumentation, activity, title);
        ProjectIdentity project =
                value(instrumentation, activity, "activeProject", ProjectIdentity.class);
        if (!title.equals(project.title()))
            throw new AssertionError("New project selected another project");
        return project;
    }

    private static SignedArtifactReceipt.Record build(
            Instrumentation instrumentation, Activity activity, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (value(instrumentation, activity, "worker", Object.class) == null) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Local worker did not bind");
            Thread.sleep(50);
        }
        ProjectIdentity project =
                value(instrumentation, activity, "activeProject", ProjectIdentity.class);
        ProjectSourceStore source = new ProjectSourceStore(project);
        String changed = source.readSource() + "\n// " + marker + "\n";
        ActiveWorkerStopProbe.showTools(instrumentation, activity);
        instrumentation.runOnMainSync(
                () -> {
                    TextBox editor = uncheckedField(activity, "source", TextBox.class);
                    if (!editor.isEnabled())
                        throw new AssertionError("Normal source editor unavailable");
                    editor.setText(changed);
                });
        click(instrumentation, activity, Labels.BUILD_SOURCE);
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (value(instrumentation, activity, "buildInProgress", Boolean.class)) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError("Local build did not settle");
            Thread.sleep(50);
        }
        if (value(instrumentation, activity, "signedArtifact", VerifiedArtifact.class) == null)
            throw new AssertionError("Real local build did not publish a verified signed artifact");
        return SignedArtifactReceipt.read(
                project.directory(), project.id(), project.packageName(), source.revision());
    }

    private static void assertSource(OwnedProject state) throws Exception {
        ProjectSourceStore source = new ProjectSourceStore(state.project);
        if (source.revision() != state.revision || !state.source.equals(source.readSource()))
            throw new AssertionError("Stale edit or navigation changed scoped source");
    }

    private static void assertReceipt(
            Instrumentation instrumentation,
            Activity activity,
            OwnedProject state,
            int revision,
            SignedArtifactReceipt.Record expected)
            throws Exception {
        ProjectIdentity selected =
                value(instrumentation, activity, "activeProject", ProjectIdentity.class);
        ProjectSourceStore source =
                value(instrumentation, activity, "sourceStore", ProjectSourceStore.class);
        if (!selected.equals(state.project)
                || !state.project.id().equals(source.projectId())
                || !state.project.packageName().equals(source.packageName())
                || source.revision() != revision)
            throw new AssertionError("Chat navigation mixed source/app identity");
        String displayedSource =
                PhoneAlphaCounterProbe.mainValue(
                        instrumentation,
                        () ->
                                uncheckedField(activity, "source", TextBox.class)
                                        .getText()
                                        .toString());
        if (!source.readSource().equals(displayedSource))
            throw new AssertionError("Controller editor showed another project revision");
        SignedArtifactReceipt.Record actual =
                SignedArtifactReceipt.read(
                        state.project.directory(),
                        state.project.id(),
                        state.project.packageName(),
                        revision);
        if (!actual.apk.equals(expected.apk)
                || actual.versionCode != expected.versionCode
                || !actual.sha256.equals(expected.sha256)
                || !actual.signerFingerprint.equals(expected.signerFingerprint))
            throw new AssertionError("Chat navigation changed the exact signed build receipt");
        SignedArtifactReceipt.restore(activity, state.project, revision).verifyUnchanged();
        VerifiedArtifact offered =
                value(instrumentation, activity, "signedArtifact", VerifiedArtifact.class);
        if (offered == null || !offered.sha256().equals(expected.sha256))
            throw new AssertionError(
                    "Controller did not offer the current verified build after navigation");
    }

    private static void assertHistory(
            Instrumentation instrumentation, Activity activity, String marker) throws Exception {
        String rendered =
                PhoneAlphaCounterProbe.mainValue(
                        instrumentation,
                        () ->
                                uncheckedField(activity, "chatHistory", TextBox.class)
                                        .getText()
                                        .toString());
        for (String candidate :
                List.of(
                        "CHAT_SCOPE_0_FIRST",
                        "CHAT_SCOPE_0_SECOND",
                        "CHAT_SCOPE_1_FIRST",
                        "CHAT_SCOPE_1_SECOND")) {
            if (rendered.contains(candidate) != candidate.equals(marker))
                throw new AssertionError("Rendered conversation mixed project/chat histories");
        }
    }

    private static void selectProject(Instrumentation instrumentation, Activity activity, String id)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<ProjectIdentity> projects =
                value(instrumentation, activity, "projectList", List.class);
        int position = -1;
        for (int index = 0; index < projects.size(); index++)
            if (projects.get(index).id().equals(id)) position = index;
        if (position < 0) throw new AssertionError("Project unavailable in normal picker");
        select(instrumentation, activity, "projectPicker", position);
        ControllerUi.awaitProjectBinding(instrumentation, activity);
    }

    private static void selectChat(Instrumentation instrumentation, Activity activity, String id)
            throws Exception {
        @SuppressWarnings("unchecked")
        List<ChatStore.Chat> chats = value(instrumentation, activity, "chats", List.class);
        int position = -1;
        for (int index = 0; index < chats.size(); index++)
            if (chats.get(index).id.equals(id)) position = index;
        if (position < 0) throw new AssertionError("Chat unavailable in normal picker");
        select(instrumentation, activity, "chatPicker", position);
    }

    private static void select(
            Instrumentation instrumentation, Activity activity, String field, int position)
            throws Exception {
        instrumentation.runOnMainSync(
                () -> {
                    Picker picker = uncheckedField(activity, field, Picker.class);
                    if (!picker.isEnabled())
                        throw new AssertionError("Normal navigation picker disabled");
                    picker.select(position);
                });
        instrumentation.waitForIdleSync();
    }

    private static void click(Instrumentation instrumentation, Activity activity, String text)
            throws Exception {
        ActiveWorkerStopProbe.visible(instrumentation, activity, text);
    }

    private static <T> T value(
            Instrumentation instrumentation, Activity activity, String name, Class<T> type) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(uncheckedField(activity, name, type)));
        return result.get();
    }

    private static <T> T uncheckedField(Activity activity, String name, Class<T> type) {
        try {
            return type.cast(Ui.field(activity, name));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String hexSha256(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            hex.append(String.format(java.util.Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private static final class OwnedProject {
        final ProjectIdentity project;
        final String marker;
        String firstChat;
        String secondChat;
        int firstRevision;
        int revision;
        String source;
        SignedArtifactReceipt.Record first;
        SignedArtifactReceipt.Record second;

        OwnedProject(ProjectIdentity project, String marker) {
            this.project = project;
            this.marker = marker;
        }
    }
}
