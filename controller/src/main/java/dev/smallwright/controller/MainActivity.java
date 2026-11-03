package dev.smallwright.controller;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import dev.smallwright.agent.ModelCatalog;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelProvider;
import dev.smallwright.controller.ui.Control;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Picker;
import dev.smallwright.controller.ui.TextBox;
import dev.smallwright.controller.ui.Turn;
import dev.smallwright.controller.ui.UiState;
import dev.smallwright.controller.ui.WorkerState;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.project.ProjectRegistry;
import dev.smallwright.project.ProjectStore;
import dev.smallwright.protocol.IBuildService;
import dev.smallwright.security.CredentialStore;
import dev.smallwright.security.ModelKeyInput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends androidx.activity.ComponentActivity {
    private static final String LEGACY_PROJECT = "counter-alpha";
    private static final String PROJECT_SELECTION = "active-project";
    private static final String ONBOARDING = "onboarding";
    private static final String PLATFORM_OFFERED = "platform-offered";

    private static final ExecutorService CATALOG_TASKS = Executors.newSingleThreadExecutor();

    private static final ExecutorService PLATFORM_TASKS = Executors.newSingleThreadExecutor();
    private static final ExecutorService WORKER_INSTALL_TASKS = Executors.newSingleThreadExecutor();

    private static final ExecutorService PICTURE_TASKS = Executors.newSingleThreadExecutor();

    /** Serialized across Activity recreation; committed recovery must outlive one screen. */
    private static final ExecutorService RECOVERY_TASKS = Executors.newSingleThreadExecutor();

    private static final int SCREENSHOT_PICK_REQUEST = 7101;
    private static final int APK_EXPORT_REQUEST = 7102;
    private static final int SOURCE_EXPORT_REQUEST = 7103;
    private static final int CRASH_IMPORT_REQUEST = 7104;
    private static final int RECOVERY_EXPORT_REQUEST = 7105;
    private static final int RECOVERY_IMPORT_REQUEST = 7106;
    private static final int NOTIFICATIONS_REQUEST = 7107;

    /** Shared by every screen, so work a screen started can finish after it is gone. */
    private static final ExecutorService TASKS = Executors.newSingleThreadExecutor();

    private static final String NOTIFICATIONS_ASKED = "notifications-asked";
    private final ExecutorService executor = TASKS;
    private PlatformFiles platformFiles;
    private ProjectSourceStore sourceStore;
    private ProjectRegistry projects;
    private ProjectIdentity activeProject;
    private final UiState ui = new UiState();
    private Picker projectPicker;
    private Control newProject;
    private final List<ProjectIdentity> projectList = new ArrayList<>();
    private boolean changingProject;
    private ChatStore chatStore;
    private ModelChoiceStore modelChoices;
    private String startupModelNote;
    private RunCoordinator runs;
    private RunCoordinator.Listener screen;
    private boolean visible;

    private ChatStore.Chat selectedChat;
    private final List<ChatStore.Chat> chats = new ArrayList<>();
    private Picker chatPicker;
    private Control newChat;
    private TextBox chatHistory;
    private volatile int editorRevision;
    private TextBox status;
    private Control stop;
    private TextBox source;
    private Control build;
    private Control undo;
    private BuilderChanges.Undoable undoable;
    private boolean undoInProgress;
    private boolean buildAfterUndo;
    private int undoSequence;
    private List<String> shownMessageIds = new ArrayList<>();
    private Control install;
    private Control exportApk;
    private Control exportSource;
    private Control deleteProject;
    private Control exportRecovery;
    private Control importRecovery;
    private TextBox modelRequest;
    private Control previewContext;
    private Control generate;
    private Control selectScreenshot;
    private Control importCrashReport;
    private Control clearCrashReport;
    private String selectedCrashReport;
    private String crashDraftProject;
    private Bitmap selectedScreenshot;
    private final List<byte[]> attached = new ArrayList<>();
    private long attachSequence;
    private int picturesPending;
    private final ActivityResultLauncher<PickVisualMediaRequest> pickPictures =
            registerForActivityResult(
                    new ActivityResultContracts.PickMultipleVisualMedia(AttachedImages.MAX_COUNT),
                    this::attachPicked);
    private long screenshotSelectionSequence;
    private final java.util.Map<String, List<Bitmap>> thumbnailCache = new java.util.HashMap<>();
    private boolean exportInProgress;
    private boolean recoveryImportInProgress;
    private boolean recoveryImportPending;
    private RecoveryAdoption recoveryAdoption;
    private ContextPreviewGate contextGate;
    private SkillSettings skills;
    private TextBox providerKey;
    private Control saveProviderKey;
    private Control removeProviderKey;
    private volatile boolean destroyed;
    private IBuildService worker;
    private boolean bound;
    private VerifiedArtifact signedArtifact;
    private VerifiedArtifact handoffArtifact;
    private TextBox testHandoff;
    private Control acceptTest;
    private AppTestStatus.State testState;
    private int handoffSequence;
    private boolean testAcceptanceInProgress;
    private VerifiedArtifact pendingApkExport;
    private SourceExport pendingSourceExport;
    private byte[] pendingRecoveryExport;
    private int pendingRecoveryRevision;
    private InstallCoordinator installs;
    private boolean startupBlocked;
    private boolean projectBindingInProgress;
    private int projectBindingSequence;
    private String startupRecoveryMessage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        projects = new ProjectRegistry(new File(getFilesDir(), "projects"), getFilesDir());
        chatStore = new ChatStore(new File(getFilesDir(), "chats"), getFilesDir());
        skills = new SkillSettings(this, ui);
        platformFiles =
                new PlatformFiles(
                        new File(getNoBackupFilesDir(), "platform"), PlatformFiles.GOOGLE_36_R02);
        runs =
                RunService.coordinator(
                        () ->
                                new RunCoordinator(
                                        this,
                                        getPreferences(0),
                                        chatStore,
                                        platformFiles,
                                        executor,
                                        this::runOnUiThread,
                                        new RunNotices(
                                                new RunService.Shade(getApplicationContext()))));
        screen =
                new RunCoordinator.Listener() {
                    @Override
                    public void started(LiveRun live) {
                        ui.setLive(live);
                        if (live.getRequest() != null) {
                            modelRequest.setText("");
                            ui.setSending(ui.getAttachments());
                            clearAttachments();
                        }
                    }

                    @Override
                    public void steps(LiveRun live) {
                        ui.setLive(live);
                    }

                    @Override
                    public void chatSaved(String chatId) {
                        ui.setLive(null);
                        ui.setSending(new ArrayList<>());
                        if (selectedChat != null && selectedChat.id.equals(chatId))
                            showTranscript();
                    }

                    @Override
                    public void status(String message) {
                        setStatus(message);
                    }

                    @Override
                    public void offer(VerifiedArtifact artifact) {
                        signedArtifact = artifact;
                    }

                    @Override
                    public void reportUsed() {
                        selectedCrashReport = null;
                    }

                    @Override
                    public void saved(String text, Integer revision) {
                        if (text != null) source.setText(text);
                        if (revision != null) editorRevision = revision;
                        refreshUndo();
                    }

                    @Override
                    public void recoveryNote(String message) {
                        startupRecoveryMessage = message;
                    }

                    @Override
                    public void platformNeeded() {
                        ui.setPlatformNeededNote(true);
                    }

                    @Override
                    public void changed() {
                        refreshControls();
                    }
                };
        status = ui.getStatus();
        stop = ui.getStop();
        source = ui.getSource();
        build = ui.getBuild();
        undo = ui.getUndo();
        install = ui.getInstall();
        exportApk = ui.getExportApk();
        exportSource = ui.getExportSource();
        deleteProject = ui.getDeleteProject();
        exportRecovery = ui.getExportRecovery();
        importRecovery = ui.getImportRecovery();
        modelRequest = ui.getModelRequest();
        previewContext = ui.getPreviewContext();
        generate = ui.getGenerate();
        selectScreenshot = ui.getSelectScreenshot();
        importCrashReport = ui.getImportCrashReport();
        clearCrashReport = ui.getClearCrashReport();
        providerKey = ui.getProviderKey();
        saveProviderKey = ui.getSaveProviderKey();
        removeProviderKey = ui.getRemoveProviderKey();
        projectPicker = ui.getProjectPicker();
        newProject = ui.getNewProject();
        chatPicker = ui.getChatPicker();
        newChat = ui.getNewChat();
        chatHistory = ui.getChatHistory();
        testHandoff = ui.getTestHandoff();
        acceptTest = ui.getAcceptTest();
        acceptTest.setOnClickListener(() -> confirmTestAcceptance());
        runs.attach(screen, () -> worker);
        if (OpenProjectActivity.fromBuilder(getIntent())) {
            openNoticeChat(getIntent());
            openCrashProject(getIntent());
        }
        setUpModelAndOnboarding();
        dev.smallwright.controller.ui.ControllerHost.show(this, ui);
        build.setOnClickListener(() -> startBuild());
        undo.setOnClickListener(() -> confirmUndo());
        stop.setOnClickListener(() -> stopCurrentRun());
        ui.getAddLibrary().setOnClickListener(() -> runs.answerQuestion(true));
        ui.getSkipLibrary().setOnClickListener(() -> runs.answerQuestion(false));
        install.setOnClickListener(() -> startInstall());
        exportApk.setOnClickListener(() -> showApkExportPreview());
        exportSource.setOnClickListener(() -> showSourceExportPreview());
        deleteProject.setOnClickListener(() -> confirmDeleteProject());
        exportRecovery.setOnClickListener(() -> showRecoveryExportPreview());
        importRecovery.setOnClickListener(() -> showRecoveryImportIntro());
        previewContext.setOnClickListener(() -> showContextPreview());
        generate.setOnClickListener(() -> sendRequest(null));
        ui.setSendAnswers(
                answers -> {
                    sendRequest(answers);
                    return kotlin.Unit.INSTANCE;
                });
        selectScreenshot.setOnClickListener(
                () -> {
                    try {
                        startActivityForResult(
                                ScreenshotPicker.createIntent(), SCREENSHOT_PICK_REQUEST);
                    } catch (Exception unavailable) {
                        setStatus("System image picker is unavailable on this phone.");
                    }
                });
        ui.getAttachImages().setOnClickListener(() -> choosePictures());
        ui.setOnRemoveAttachment(
                index -> {
                    if (isBusy() || index < 0 || index >= attached.size()) return;
                    attached.remove((int) index);
                    List<Bitmap> shown = new ArrayList<>(ui.getAttachments());
                    shown.remove((int) index);
                    ui.setAttachments(shown);
                    refreshControls();
                });
        importCrashReport.setOnClickListener(() -> chooseCrashReport());
        clearCrashReport.setOnClickListener(
                () -> {
                    selectedCrashReport = null;
                    refreshControls();
                    setStatus("Crash report removed from your next message.");
                });
        saveProviderKey.setOnClickListener(() -> saveOpenRouterKey());
        ui.getSaveServer().setOnClickListener(this::saveServer);
        ui.getRemoveServerKey().setOnClickListener(this::confirmRemoveServerKey);
        ui.getUseServerModel().setOnClickListener(this::useServerModel);
        showSavedLimits();
        ui.getSaveLimits().setOnClickListener(() -> saveLimits());
        ui.setOnToggleStopWhenStuck(this::toggleStopWhenStuck);
        ui.setOnToggleAskBeforeLibraries(this::toggleAskBeforeLibraries);
        ui.setOnToggleAskBeforeSkills(this::toggleAskBeforeSkills);
        ui.setOnSettingsClosed(this::settingsClosed);
        ui.getOpenNotificationSettings().setOnClickListener(this::openNotificationSettings);
        removeProviderKey.setOnClickListener(() -> confirmRemoveOpenRouterKey());
        newChat.setOnClickListener(() -> createChat());
        newProject.setOnClickListener(() -> showNewProjectDialog());
        projectPicker.setOnSelect(
                position -> {
                    if (!changingProject
                            && position >= 0
                            && position < projectList.size()
                            && activeProject != null
                            && !projectList.get(position).id().equals(activeProject.id()))
                        selectProject(projectList.get(position));
                    return kotlin.Unit.INSTANCE;
                });
        chatPicker.setOnSelect(
                position -> {
                    if (position >= 0 && position < chats.size()) {
                        ChatStore.Chat next = chats.get(position);
                        if (selectedChat != null && !selectedChat.id.equals(next.id)) {
                            modelRequest.setText("");
                            clearAttachments();
                        }
                        boolean changed = selectedChat == null || !selectedChat.id.equals(next.id);
                        selectedChat = next;
                        if (changed) saveSelectedChat();
                        showSelectedChat();
                        refreshControls();
                    }
                    return kotlin.Unit.INSTANCE;
                });
        installs =
                new InstallCoordinator(
                        this,
                        message ->
                                runOnUiThread(
                                        () -> {
                                            setStatus(message);
                                            connectWorker();
                                            if (!isBusy()) checkTestHandoff(false);
                                            refreshUndo();
                                        }));
        installs.register();
        try {
            bindProject(null);
        } catch (Exception exception) {
            startupBlocked = true;
            startupRecoveryMessage =
                    "Could not verify local project and run history. "
                            + "Build and generation are disabled to protect saved work.";
            setStatus(startupRecoveryMessage);
        }
        ui.setOnInstallWorker(this::installWorker);
        String workerProblem = workerProblem(connectWorker());
        if (workerProblem != null && startupRecoveryMessage == null) setStatus(workerProblem);
    }

    private WorkerState connectWorker() {
        WorkerState state = BuildWorker.state(this);
        ui.setWorker(state);
        if (!bound && (state == WorkerState.READY || state == WorkerState.OUTDATED))
            bound = BuildWorker.bind(this, connection);
        return state;
    }

    private String workerProblem(WorkerState state) {
        switch (state) {
            case MISSING:
                return "The build worker is not installed. Tap "
                        + Labels.INSTALL_WORKER
                        + " to add it.";
            case OUTDATED:
                return "The build worker is older than this app. Tap "
                        + Labels.UPDATE_WORKER
                        + " before building.";
            case OTHER_KEY:
                return "The installed build worker is signed with a different key. "
                        + "Uninstall Smallwright Worker, then come back here to install it.";
            case READY:
            default:
                return bound ? null : "The build worker refused the connection.";
        }
    }

    private void installWorker() {
        if (destroyed || !installsAllowed(Labels.INSTALL_WORKER)) return;
        setStatus("Opening Android's installer...");
        WORKER_INSTALL_TASKS.execute(
                () -> {
                    try {
                        installs.installWorker();
                    } catch (Exception exception) {
                        runOnUiThread(
                                () ->
                                        setStatus(
                                                "Could not start installation: "
                                                        + exception.getMessage()));
                    }
                });
    }

    /** Opens Android's setting when this app may not install apps yet; then it needs a new tap. */
    private boolean installsAllowed(String retryLabel) {
        if (getPackageManager().canRequestPackageInstalls()) return true;
        setStatus(
                "Android needs 'Allow from this source' for Smallwright. "
                        + "Enable it in Settings, return here, then tap "
                        + retryLabel
                        + " again.");
        startActivity(
                new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
        return false;
    }

    private static final String UNVERIFIED_INSTALL =
            "Could not verify installed app. Try reopening this project.";

    private boolean isLegacyProject() {
        return activeProject != null && LEGACY_PROJECT.equals(activeProject.id());
    }

    /** Disk and recovery work remain serialized while the activity thread stays responsive. */
    private void bindProject(ProjectIdentity requested) {
        bindProject(requested, null);
    }

    private void bindProject(ProjectIdentity requested, ProjectIdentity deleted) {
        int sequence = ++projectBindingSequence;
        ProjectIdentity running = runs.runningProject();
        projectBindingInProgress = true;
        startupBlocked = true;
        recoveryImportPending = false;
        signedArtifact = null;
        buildAfterUndo = false;
        startupRecoveryMessage = "Opening saved project and checking recovery...";
        setStatus(startupRecoveryMessage);
        refreshControls();
        try {
            RECOVERY_TASKS.execute(
                    () -> {
                        try {
                            if (deleted != null) deleteProjectData(deleted);
                            ProjectIdentity identity =
                                    requested != null
                                            ? requested
                                            : running != null
                                                    ? projects.get(running.id())
                                                    : initialProject();
                            PreparedProject prepared =
                                    prepareProject(identity, identity.equals(running));
                            runOnUiThread(
                                    () -> {
                                        if (destroyed || sequence != projectBindingSequence) return;
                                        try {
                                            applyProject(prepared);
                                            projectBindingInProgress = false;
                                            refreshProjectPicker();
                                            refreshControls();
                                            if (startupRecoveryMessage != null)
                                                setStatus(startupRecoveryMessage);
                                            else
                                                setStatus(
                                                        "Project: "
                                                                + identity.title()
                                                                + ". Source revision "
                                                                + editorRevision
                                                                + (worker == null
                                                                        ? "; connecting to compiler."
                                                                        : " ready."));
                                            if (runs.pendingRecovery() != null)
                                                resumePendingBuild();
                                            else if (!startupBlocked) restoreSignedArtifact();
                                        } catch (Exception failure) {
                                            failProjectBinding(sequence, failure);
                                        }
                                    });
                        } catch (Exception failure) {
                            runOnUiThread(() -> failProjectBinding(sequence, failure));
                        }
                    });
        } catch (RuntimeException scheduling) {
            failProjectBinding(sequence, scheduling);
        }
    }

    private void failProjectBinding(int sequence, Exception failure) {
        Log.w("SmallwrightProject", "Could not open the project", failure);
        if (destroyed || sequence != projectBindingSequence) return;
        projectBindingInProgress = false;
        startupBlocked = true;
        startupRecoveryMessage =
                "Could not verify local project and run history. "
                        + "Build and generation are disabled to protect saved work. Restart to retry.";
        refreshControls();
        setStatus(startupRecoveryMessage);
    }

    private ProjectIdentity initialProject() throws Exception {
        ProjectIdentity legacy = projects.ensureLegacyProject();
        for (String unfinished : projects.unpublished()) {
            try {
                finishDeletion(unfinished);
            } catch (Exception retriedOnNextLaunch) {
            }
        }
        String selectedId = getPreferences(0).getString(PROJECT_SELECTION, LEGACY_PROJECT);
        ProjectIdentity selected;
        try {
            selected = projects.get(selectedId);
        } catch (IllegalArgumentException | IOException missing) {
            selected = legacy;
        }
        for (ProjectIdentity candidate : projects.list()) {
            if (!candidate.id().equals(selected.id())
                    && new RunJournal(getApplicationContext(), candidate.id()).hasActiveAttempt()) {
                selected = candidate;
                break;
            }
        }
        return selected;
    }

    private static final class PreparedProject {
        final ProjectIdentity identity;
        final ProjectSourceStore source;
        final RecoveryAdoption adoption;
        final RecoveryAdoption.Status importStatus;
        final int revision;
        final String savedSource;
        final int recovered;
        final RunJournal.Snapshot pending;

        PreparedProject(
                ProjectIdentity identity,
                ProjectSourceStore source,
                RecoveryAdoption adoption,
                RecoveryAdoption.Status importStatus,
                int revision,
                String savedSource,
                int recovered,
                RunJournal.Snapshot pending) {
            this.identity = identity;
            this.source = source;
            this.adoption = adoption;
            this.importStatus = importStatus;
            this.revision = revision;
            this.savedSource = savedSource;
            this.recovered = recovered;
            this.pending = pending;
        }
    }

    /** Opens a project; a project whose run is still working keeps its run history as it is. */
    private PreparedProject prepareProject(ProjectIdentity identity, boolean working)
            throws Exception {
        ProjectSourceStore nextSource = new ProjectSourceStore(identity);
        nextSource.initialize();
        RecoveryAdoption nextAdoption = null;
        RecoveryAdoption.Status importStatus = null;
        if (LEGACY_PROJECT.equals(identity.id())) {
            nextAdoption = createRecoveryAdoption(nextSource);
            importStatus = nextAdoption.resume();
        }
        int revision = nextSource.revision();
        String savedSource = nextSource.readSource();
        RunJournal journal = new RunJournal(getApplicationContext(), identity.id());
        boolean settled = !working && importStatus != RecoveryAdoption.Status.PENDING_REIMPORT;
        int recovered =
                settled ? InterruptedRuns.reopen(journal, chatStore, identity.id(), revision) : 0;
        RunJournal.Snapshot pending = settled ? journal.pendingBuild(revision) : null;
        if (!getPreferences(0).edit().putString(PROJECT_SELECTION, identity.id()).commit())
            throw new IOException("Could not save active project");
        return new PreparedProject(
                identity,
                nextSource,
                nextAdoption,
                importStatus,
                revision,
                savedSource,
                recovered,
                pending);
    }

    private void applyProject(PreparedProject prepared) throws Exception {
        ProjectIdentity identity = prepared.identity;
        ProjectSourceStore nextSource = prepared.source;
        RecoveryAdoption nextAdoption = prepared.adoption;
        RecoveryAdoption.Status importStatus = prepared.importStatus;
        int revision = prepared.revision;
        String savedSource = prepared.savedSource;
        int recovered = prepared.recovered;
        RunJournal.Snapshot pending = prepared.pending;
        activeProject = identity;
        sourceStore = nextSource;
        recoveryAdoption = nextAdoption;
        contextGate = new ContextPreviewGate(nextSource, chatStore, identity.id(), skills.store);
        editorRevision = revision;
        source.setText(savedSource);
        modelRequest.setText("");
        if (identity.id().equals(crashDraftProject)) showCrashDraft();
        clearAttachments();
        selectedCrashReport = null;
        screenshotSelectionSequence++;
        ui.setScreenshot(null);
        if (selectedScreenshot != null) selectedScreenshot.recycle();
        selectedScreenshot = null;
        signedArtifact = null;
        if (!runs.isBusy()) runs.restore(pending);
        startupBlocked = importStatus == RecoveryAdoption.Status.PENDING_REIMPORT;
        recoveryImportPending = startupBlocked;
        startupRecoveryMessage =
                startupBlocked
                        ? "Recovery import is waiting for the same encrypted file and phrase."
                        : pending != null
                                ? "A committed revision is saved. Reconnecting to rebuild it locally."
                                : recovered > 0
                                        ? "An interrupted run was closed at its last saved"
                                                + " version. Send a message to continue."
                                        : null;
        loadChats();
    }

    private void refreshProjectPicker() throws IOException {
        changingProject = true;
        try {
            projectList.clear();
            projectList.addAll(projects.list());
            List<String> names = new ArrayList<>();
            int selected = 0;
            for (int i = 0; i < projectList.size(); i++) {
                ProjectIdentity project = projectList.get(i);
                names.add(project.title());
                if (activeProject != null && project.id().equals(activeProject.id())) selected = i;
            }
            projectPicker.setItems(names);
            projectPicker.setSelection(selected);
        } finally {
            changingProject = false;
        }
    }

    private void selectProject(ProjectIdentity next) {
        if (activeProject == null || next.id().equals(activeProject.id())) return;
        ProjectIdentity origin = activeProject;
        if (!canLeaveProject(origin)) {
            restoreProjectPicker();
            setStatus("Finish current work and save source with Build before switching projects.");
            return;
        }
        switchProject(next, origin);
    }

    private boolean canLeaveProject(ProjectIdentity origin) {
        return origin != null
                && origin.equals(activeProject)
                && !isBusy()
                && runs.pendingRecovery() == null
                && !runs.recoveryQueued()
                && !startupBlocked
                && pendingApkExport == null
                && pendingSourceExport == null
                && pendingRecoveryExport == null
                && source.getText().toString().equals(savedSource());
    }

    private void restoreProjectPicker() {
        try {
            refreshProjectPicker();
        } catch (IOException ignored) {
        }
    }

    private void switchProject(ProjectIdentity next, ProjectIdentity origin) {
        if (!canLeaveProject(origin)) {
            restoreProjectPicker();
            setStatus("Project changed while switching. Review the current project and retry.");
            return;
        }
        bindProject(next);
    }

    private void confirmDeleteProject() {
        ProjectIdentity doomed = activeProject;
        if (LEGACY_PROJECT.equals(doomed.id())) return;
        if (!canLeaveProject(doomed)) {
            setStatus("Finish the current work before deleting this project.");
            return;
        }
        ProjectIdentity next = null;
        for (ProjectIdentity candidate : projectList) {
            if (!candidate.id().equals(doomed.id())) {
                next = candidate;
                break;
            }
        }
        if (next == null) return;
        ProjectIdentity destination = next;
        new AlertDialog.Builder(this)
                .setTitle("Delete " + doomed.title() + "?")
                .setMessage(
                        "This removes the project's source, builds, chats and signing key from this"
                                + " phone. "
                                + "An installed copy of its app stays installed but can no longer"
                                + " be updated. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Delete project",
                        (dialog, which) -> deleteProjectAndSwitch(doomed, destination))
                .show();
    }

    private void deleteProjectAndSwitch(ProjectIdentity doomed, ProjectIdentity next) {
        if (!canLeaveProject(doomed)) {
            setStatus("Project changed before deletion. Review it and retry.");
            return;
        }
        ui.setToolsOpen(false);
        bindProject(next, doomed);
    }

    private void deleteProjectData(ProjectIdentity doomed) throws Exception {
        projects.unpublish(doomed.id());
        finishDeletion(doomed.id());
    }

    private void finishDeletion(String projectId) throws Exception {
        chatStore.deleteProject(projectId);
        File repairOrigins = new File(getNoBackupFilesDir(), "repair-origins/" + projectId);
        if (Files.isDirectory(repairOrigins.toPath(), LinkOption.NOFOLLOW_LINKS))
            ProjectStore.deleteTree(repairOrigins.toPath());
        ProjectSigner.deleteKeys(getApplicationContext(), projectId);
        projects.purge(projectId);
    }

    private String savedSource() {
        try {
            return sourceStore.readSource();
        } catch (Exception failure) {
            return null;
        }
    }

    private void showNewProjectDialog() {
        if (activeProject == null
                || isBusy()
                || runs.pendingRecovery() != null
                || startupBlocked
                || !source.getText().toString().equals(savedSource())) return;
        ProjectIdentity origin = activeProject;
        EditText title = new EditText(this);
        title.setHint("Project name");
        title.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("New project")
                .setView(title)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Create",
                        (dialog, which) -> {
                            try {
                                if (!canLeaveProject(origin))
                                    throw new IllegalStateException(
                                            "Project changed; open New project again");
                                String requestedTitle = title.getText().toString().trim();
                                createAndSelectProject(requestedTitle, origin);
                            } catch (Exception failure) {
                                setStatus("Could not create project: " + failure.getMessage());
                            }
                        })
                .show();
    }

    private void createAndSelectProject(String title, ProjectIdentity origin) {
        if (!canLeaveProject(origin)) {
            setStatus("Project changed before creation. Open New project again.");
            return;
        }
        try {
            ProjectIdentity created = projects.create(title);
            switchProject(created, origin);
        } catch (Exception failure) {
            setStatus("Could not create project: " + failure.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        reportViewing();
    }

    @Override
    protected void onStop() {
        visible = false;
        runs.viewing(null, null);
        super.onStop();
    }

    private void reportViewing() {
        if (visible && activeProject != null && selectedChat != null)
            runs.viewing(activeProject.id(), selectedChat.id);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!OpenProjectActivity.fromBuilder(intent)) return;
        String crashed = openCrashProject(intent);
        if (crashed != null && activeProject != null) {
            if (crashed.equals(activeProject.id())) showCrashDraft();
            else
                try {
                    selectProject(projects.get(crashed));
                } catch (Exception gone) {
                    setStatus("That project is no longer on this phone.");
                }
            return;
        }
        String projectId = openNoticeChat(intent);
        if (projectId == null || activeProject == null) return;
        if (!projectId.equals(activeProject.id())) {
            try {
                selectProject(projects.get(projectId));
            } catch (Exception gone) {
                setStatus("That project is no longer on this phone.");
            }
            return;
        }
        try {
            loadChats();
            refreshControls();
        } catch (IOException unreadable) {
            setStatus("Could not open that chat. Chat history remains saved.");
        }
    }

    /**
     * Makes the project whose app sent a crash report the one that opens next, with the message
     * that asks the builder to fix it typed in; returns its id, or null for other intents.
     */
    private String openCrashProject(Intent intent) {
        String projectId = intent.getStringExtra(AppReportActivity.EXTRA_PROJECT);
        if (projectId == null) return null;
        intent.removeExtra(AppReportActivity.EXTRA_PROJECT);
        try {
            projects.get(projectId);
        } catch (Exception unknown) {
            return null;
        }
        if (activeProject == null
                && !getPreferences(0).edit().putString(PROJECT_SELECTION, projectId).commit())
            return null;
        crashDraftProject = projectId;
        return projectId;
    }

    private void showCrashDraft() {
        crashDraftProject = null;
        if (modelRequest.getText().toString().trim().isEmpty())
            modelRequest.setText(AppReportActivity.DRAFT);
    }

    /**
     * Makes the chat a run notification names the one that opens next; returns its project's id, or
     * null when the intent came from elsewhere.
     */
    private String openNoticeChat(Intent intent) {
        String projectId = intent.getStringExtra(RunService.EXTRA_PROJECT);
        String chatId = intent.getStringExtra(RunService.EXTRA_CHAT);
        if (projectId == null || chatId == null) return null;
        intent.removeExtra(RunService.EXTRA_PROJECT);
        intent.removeExtra(RunService.EXTRA_CHAT);
        try {
            projects.get(projectId);
        } catch (Exception unknown) {
            return null;
        }
        boolean saved =
                getSharedPreferences("chat-selection", MODE_PRIVATE)
                        .edit()
                        .putString(projectId, chatId)
                        .commit();
        if (activeProject == null)
            saved &= getPreferences(0).edit().putString(PROJECT_SELECTION, projectId).commit();
        return saved ? projectId : null;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        runs.detach(screen);
        RunService.release(runs);
        installs.unregister();
        if (pendingRecoveryExport != null) Arrays.fill(pendingRecoveryExport, (byte) 0);
        pendingRecoveryExport = null;
        ui.setScreenshot(null);
        if (selectedScreenshot != null) selectedScreenshot.recycle();
        selectedScreenshot = null;
        selectedCrashReport = null;
        if (bound) unbindService(connection);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RECOVERY_IMPORT_REQUEST) {
            if (isLegacyProject()
                    && resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null) showRecoveryImportPhrase(data.getData());
            return;
        }
        if (requestCode == APK_EXPORT_REQUEST) {
            VerifiedArtifact selected = pendingApkExport;
            pendingApkExport = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                exportApkToSelectedDocument(selected, data.getData());
            return;
        }
        if (requestCode == SOURCE_EXPORT_REQUEST) {
            SourceExport selected = pendingSourceExport;
            pendingSourceExport = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                exportSourceToSelectedDocument(selected, data.getData());
            return;
        }
        if (requestCode == RECOVERY_EXPORT_REQUEST) {
            byte[] selected = pendingRecoveryExport;
            int revision = pendingRecoveryRevision;
            pendingRecoveryExport = null;
            if (isLegacyProject()
                    && resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null) {
                exportRecoveryToSelectedDocument(selected, revision, data.getData());
            } else {
                if (selected != null) Arrays.fill(selected, (byte) 0);
                exportInProgress = false;
                refreshControls();
                setStatus(
                        "Recovery file was not saved. The signing identity may already have "
                                + "changed; retry export before relying on a backup.");
            }
            return;
        }
        if (requestCode == CRASH_IMPORT_REQUEST) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                previewCrashReport(data.getData());
            return;
        }
        if (requestCode != SCREENSHOT_PICK_REQUEST
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) return;
        android.net.Uri selected = data.getData();
        long sequence = ++screenshotSelectionSequence;
        setStatus("Reading selected screenshot for local preview...");
        executor.execute(
                () -> {
                    try {
                        Bitmap preview =
                                ScreenshotPicker.readPreview(getContentResolver(), selected);
                        runOnUiThread(
                                () -> {
                                    if (destroyed || sequence != screenshotSelectionSequence) {
                                        preview.recycle();
                                        return;
                                    }
                                    ui.setScreenshot(null);
                                    if (selectedScreenshot != null) selectedScreenshot.recycle();
                                    selectedScreenshot = preview;
                                    ui.setScreenshot(preview);
                                    setStatus(
                                            "Screenshot preview ready on this phone. It is not sent to the model.");
                                });
                    } catch (Exception failure) {
                        runOnUiThread(
                                () -> {
                                    if (!destroyed)
                                        setStatus(
                                                "Could not preview selected screenshot. "
                                                        + "Choose an image smaller than 8 MiB.");
                                });
                    }
                });
    }

    private void startBuild() {
        if (worker == null || isBusy() || destroyed || startupBlocked) return;
        if (runs.pendingRecovery() != null) {
            resumePendingBuild();
            return;
        }
        if (selectedChat == null) {
            setStatus("Select a chat before building.");
            return;
        }
        runs.build(
                activeProject,
                sourceStore,
                selectedChat.id,
                source.getText().toString(),
                editorRevision);
    }

    private void stopCurrentRun() {
        StopToken.Result result = destroyed ? null : runs.stop();
        if (result == null) return;
        refreshControls();
        setStatus(
                result == StopToken.Result.ALREADY_FINISHED
                        ? "The run has already finished. Waiting for its result."
                        : result == StopToken.Result.HISTORY_UNCERTAIN
                                ? "Stop requested and model connection closed. Run history could not be confirmed; "
                                        + "review saved source after this step settles."
                                : "Stop requested. A local build already running may finish or time out; "
                                        + "its result will not be offered for installation. Provider usage may already have occurred.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotificationsAllowed();
        connectWorker();
        if (testHandoff != null && !isBusy()) checkTestHandoff(false);
    }

    private void checkTestHandoff(boolean accept) {
        int sequence = ++handoffSequence;
        VerifiedArtifact artifact = signedArtifact;
        ProjectIdentity project = activeProject;
        handoffArtifact = artifact;
        testState = null;
        acceptTest.setEnabled(false);
        if (artifact == null || project == null) {
            testHandoff.setText("Build an app to begin testing.");
            return;
        }
        testHandoff.setText("Checking installed app...");
        try {
            executor.execute(
                    () -> {
                        AppTestStatus.State next = null;
                        try {
                            boolean installed = InstalledArtifact.matches(this, project, artifact);
                            AppTestStatus record =
                                    new AppTestStatus(project.directory(), getFilesDir());
                            String installedHash = installed ? artifact.sha256() : null;
                            if (accept) record.accept(artifact.sha256(), installedHash);
                            next = record.observe(artifact.sha256(), installedHash);
                        } catch (Exception unavailable) {
                            // Unknown identity or unreadable self-report never grants acceptance.
                        }
                        AppTestStatus.State observed = next;
                        runOnUiThread(
                                () -> {
                                    if (destroyed
                                            || sequence != handoffSequence
                                            || artifact != signedArtifact
                                            || !project.equals(activeProject)) return;
                                    testAcceptanceInProgress = false;
                                    testState = observed;
                                    if (observed == null) setStatus(UNVERIFIED_INSTALL);
                                    testHandoff.setText(
                                            observed == null
                                                    ? UNVERIFIED_INSTALL
                                                    : switch (observed) {
                                                        case READY_TO_INSTALL ->
                                                                "Ready to install — this build is not installed.";
                                                        case INSTALLED_UNTESTED ->
                                                                "Installed. Try it, then tell the builder what to change, or tap "
                                                                        + Labels.ACCEPT
                                                                        + ".";
                                                        case USER_ACCEPTED ->
                                                                "User accepted this installed build.";
                                                    });
                                    refreshControls();
                                });
                    });
        } catch (RuntimeException unavailable) {
            testAcceptanceInProgress = false;
            testHandoff.setText(UNVERIFIED_INSTALL);
            setStatus(UNVERIFIED_INSTALL);
            refreshControls();
        }
    }

    private void confirmTestAcceptance() {
        if (isBusy()
                || destroyed
                || testState != AppTestStatus.State.INSTALLED_UNTESTED
                || signedArtifact == null) return;
        VerifiedArtifact artifact = signedArtifact;
        ProjectIdentity project = activeProject;
        new AlertDialog.Builder(this)
                .setTitle("Did this build pass your test?")
                .setMessage(
                        "Only accept after opening the installed app and checking the requested behavior "
                                + "and saved data. This records your result for this exact build; "
                                + "compilation and installation do not prove it works.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Accept this build",
                        (dialog, which) -> {
                            if (isBusy()
                                    || destroyed
                                    || artifact != signedArtifact
                                    || !project.equals(activeProject)) return;
                            testAcceptanceInProgress = true;
                            checkTestHandoff(true);
                            refreshControls();
                        })
                .show();
    }

    private void startInstall() {
        if (signedArtifact == null || isBusy() || destroyed) return;
        ui.setToolsOpen(false);
        try {
            if (!installsAllowed(Labels.INSTALL)) return;
            installs.install(signedArtifact, activeProject);
            setStatus("Opening Android's installer...");
        } catch (Exception exception) {
            setStatus("Could not start installation: " + exception.getMessage());
        }
    }

    private void showApkExportPreview() {
        VerifiedArtifact artifact = signedArtifact;
        if (artifact == null || isBusy() || destroyed || startupBlocked) return;
        String details =
                "Save the app file ("
                        + Math.max(1, artifact.length() / 1024)
                        + " KB) to a place you choose. It does not include your model keys, "
                        + "the project's signing key or the app's code. Cancel saves nothing.";
        new AlertDialog.Builder(this)
                .setTitle("Export app file")
                .setMessage(details)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Choose location",
                        (dialog, which) -> {
                            if (signedArtifact != artifact || isBusy() || destroyed) return;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/vnd.android.package-archive");
                            intent.putExtra(
                                    Intent.EXTRA_TITLE,
                                    "smallwright-app-"
                                            + artifact.sha256().substring(0, 12)
                                            + ".apk");
                            pendingApkExport = artifact;
                            try {
                                startActivityForResult(intent, APK_EXPORT_REQUEST);
                            } catch (Exception unavailable) {
                                pendingApkExport = null;
                                setStatus("Android's document picker is unavailable.");
                            }
                        })
                .show();
    }

    private void showSourceExportPreview() {
        if (isBusy() || destroyed || startupBlocked) return;
        try {
            int revision = sourceStore.revision();
            if (revision != editorRevision
                    || !source.getText().toString().equals(sourceStore.readSource())) {
                setStatus("Save source with Build before exporting it.");
                return;
            }
            SourceExport selected =
                    SourceExport.prepare(
                            revision,
                            sourceStore.readCurrentFiles(revision),
                            ProjectSourceStore.exportManifest(activeProject.packageName()),
                            activeProject.packageName());
            new AlertDialog.Builder(this)
                    .setTitle("Review source export")
                    .setMessage(selected.preview())
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                            "Choose location",
                            (dialog, which) -> {
                                if (isBusy()
                                        || destroyed
                                        || !selected.packageName()
                                                .equals(activeProject.packageName())) return;
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("application/zip");
                                intent.putExtra(
                                        Intent.EXTRA_TITLE,
                                        "smallwright-source-revision-"
                                                + selected.revision()
                                                + ".zip");
                                pendingSourceExport = selected;
                                try {
                                    startActivityForResult(intent, SOURCE_EXPORT_REQUEST);
                                } catch (Exception unavailable) {
                                    pendingSourceExport = null;
                                    setStatus("Android's document picker is unavailable.");
                                }
                            })
                    .show();
        } catch (Exception failure) {
            setStatus("Could not prepare current source export. Check the saved revision.");
        }
    }

    private void showRecoveryExportPreview() {
        if (!isLegacyProject() || isBusy() || destroyed || startupBlocked) return;
        final int revision;
        try {
            revision = sourceStore.revision();
            if (revision != editorRevision
                    || !source.getText().toString().equals(sourceStore.readSource())) {
                setStatus("Save source with Build before creating a recovery file.");
                return;
            }
        } catch (Exception failure) {
            setStatus("Could not read the saved project for recovery export.");
            return;
        }
        EditText phraseInput = recoveryPhraseInput("Recovery phrase (12 characters or more)");
        EditText confirmationInput = recoveryPhraseInput("Repeat recovery phrase");
        LinearLayout inputs = new LinearLayout(this);
        inputs.setOrientation(LinearLayout.VERTICAL);
        inputs.addView(phraseInput);
        inputs.addView(confirmationInput);
        String explanation =
                "This encrypts all saved source files and the project's update signing "
                        + "key. If an older generated app is installed, future builds move to a new key "
                        + "with Android signing lineage. Keep both the file and phrase: neither can be "
                        + "recovered if lost. Generated app data and the model API key are not included. "
                        + "Android will ask where to save the file.";
        new AlertDialog.Builder(this)
                .setTitle("Create encrypted recovery file")
                .setMessage(explanation)
                .setView(inputs)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Encrypt",
                        (dialog, which) -> {
                            char[] phrase = readSecret(phraseInput);
                            char[] confirmation = readSecret(confirmationInput);
                            phraseInput.setText("");
                            confirmationInput.setText("");
                            try {
                                if (phrase.length < 12
                                        || phrase.length > 1024
                                        || !Arrays.equals(phrase, confirmation)) {
                                    Arrays.fill(phrase, '\0');
                                    setStatus(
                                            "Recovery phrases must match and have 12-1024 characters.");
                                    return;
                                }
                                prepareRecoveryExport(revision, phrase);
                            } finally {
                                Arrays.fill(confirmation, '\0');
                            }
                        })
                .show();
    }

    private EditText recoveryPhraseInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSaveEnabled(false);
        return input;
    }

    private static char[] readSecret(EditText input) {
        Editable value = input.getText();
        char[] characters = new char[value.length()];
        value.getChars(0, value.length(), characters, 0);
        return characters;
    }

    private RecoveryAdoption createRecoveryAdoption(ProjectSourceStore recoveringSource) {
        Context app = getApplicationContext();
        PortableSignerStore portable = new PortableSignerStore(app, "counter-alpha");
        RecoveryAdoption.Project project =
                new RecoveryAdoption.Project() {
                    @Override
                    public int revision() throws Exception {
                        return recoveringSource.revision();
                    }

                    @Override
                    public boolean isPristineDefaultSnapshot() throws Exception {
                        return recoveringSource.isPristineDefaultSnapshot();
                    }

                    @Override
                    public int stageExactSnapshot(int expected, java.util.Map<String, byte[]> files)
                            throws Exception {
                        return recoveringSource.stageRecoverySources(expected, files);
                    }

                    @Override
                    public byte[] validatedStagedSnapshotDigest(int expected, int staged)
                            throws Exception {
                        return recoveringSource.validatedStagedRecoveryDigest(expected, staged);
                    }

                    @Override
                    public int promoteStagedSnapshot(int expected, int staged) throws Exception {
                        return recoveringSource.promoteRecoverySources(expected, staged);
                    }
                };
        RecoveryAdoption.Signer signer =
                new RecoveryAdoption.Signer() {
                    @Override
                    public RecoveryAdoption.StoredSigner load() throws Exception {
                        PortableSignerStore.StoredIdentity stored = portable.load();
                        return stored == null
                                ? null
                                : new RecoveryAdoption.StoredSigner(
                                        stored.signer().certificate().getEncoded(),
                                        stored.publicLineage());
                    }

                    @Override
                    public void saveNew(PortableSigningIdentity identity, byte[] lineage)
                            throws Exception {
                        portable.saveNew(identity, lineage);
                    }

                    @Override
                    public void saveForPendingRecovery(
                            PortableSigningIdentity identity, byte[] lineage) throws Exception {
                        portable.saveForPendingRecovery(identity, lineage);
                    }
                };
        RecoveryAdoption.Preconditions preconditions =
                () -> {
                    if (runs.isBusy() || exportInProgress)
                        throw new IllegalStateException("Finish the current work before restoring");
                    KeyStore old = KeyStore.getInstance("AndroidKeyStore");
                    old.load(null);
                    if (old.containsAlias("generated-counter-alpha"))
                        throw new SecurityException("An older project signer already exists");
                    if (new RunJournal(app).hasAnyAttempt())
                        throw new SecurityException("Generation history already exists");
                };
        RecoveryAdoption.InstalledApp installedApp =
                (savedVersion, certificate, lineage) -> {
                    PackageInfo installed;
                    try {
                        installed =
                                getPackageManager()
                                        .getPackageInfo(
                                                ProjectSourceStore.PACKAGE_NAME,
                                                PackageManager.GET_SIGNING_CERTIFICATES);
                    } catch (PackageManager.NameNotFoundException absent) {
                        return RecoveryInstalledApp.nextVersionCode(
                                savedVersion, certificate, lineage, null, 0);
                    }
                    Signature[] current =
                            installed.signingInfo == null
                                    ? null
                                    : installed.signingInfo.getApkContentsSigners();
                    if (current == null || current.length != 1)
                        throw new SecurityException("Installed app signer is unavailable");
                    return RecoveryInstalledApp.nextVersionCode(
                            savedVersion,
                            certificate,
                            lineage,
                            current[0].toByteArray(),
                            installed.getLongVersionCode());
                };
        RecoveryAdoption.VersionFloor floor =
                new RecoveryAdoption.VersionFloor() {
                    @Override
                    public int nextVersion() {
                        return getPreferences(0).getInt("next_version", 1);
                    }

                    @Override
                    public void raiseToAtLeast(int next) {
                        if (next < 2 || next >= Integer.MAX_VALUE)
                            throw new IllegalArgumentException("Invalid recovered version");
                        int current = nextVersion();
                        if (current < 1 || current >= Integer.MAX_VALUE)
                            throw new SecurityException("Invalid saved version floor");
                        if (!getPreferences(0)
                                .edit()
                                .putInt("next_version", Math.max(current, next))
                                .commit())
                            throw new IllegalStateException(
                                    "Could not save recovered version floor");
                    }
                };
        return new RecoveryAdoption(
                new File(app.getNoBackupFilesDir(), "recovery-adoption.marker"),
                project,
                signer,
                installedApp,
                preconditions,
                floor);
    }

    private void showRecoveryImportIntro() {
        if (!isLegacyProject() || isBusy() || destroyed || startupBlocked && !recoveryImportPending)
            return;
        new AlertDialog.Builder(this)
                .setTitle("Restore encrypted recovery file")
                .setMessage(
                        "Restore the saved source and its update signing identity into a "
                                + "clean controller project on this phone. An existing generated app "
                                + "must have a compatible signing identity. Saved project edits, another "
                                + "signer, or generation history block restore. It does not restore model "
                                + "credentials, run history or generated "
                                + "app data. You will review the file before adopting it.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Choose file",
                        (dialog, which) -> {
                            if (isBusy() || destroyed) return;
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/octet-stream");
                            try {
                                startActivityForResult(intent, RECOVERY_IMPORT_REQUEST);
                            } catch (Exception unavailable) {
                                setStatus(
                                        "Android's document picker is unavailable for recovery import.");
                            }
                        })
                .show();
    }

    private void showRecoveryImportPhrase(android.net.Uri document) {
        if (!isLegacyProject() || isBusy() || destroyed || startupBlocked && !recoveryImportPending)
            return;
        if (!"content".equals(document.getScheme())) {
            setStatus("Select a recovery document through Android's file picker.");
            return;
        }
        EditText input = recoveryPhraseInput("Recovery phrase");
        new AlertDialog.Builder(this)
                .setTitle("Unlock recovery file")
                .setMessage(
                        "The file and phrase are read on this phone. Nothing is changed "
                                + "until you review the contents and tap Restore.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Open & review",
                        (dialog, which) -> {
                            char[] phrase = readSecret(input);
                            input.setText("");
                            if (phrase.length < 12 || phrase.length > 1024) {
                                Arrays.fill(phrase, '\0');
                                setStatus("Recovery phrase must have 12-1024 characters.");
                                return;
                            }
                            openRecoveryForReview(document, phrase);
                        })
                .show();
    }

    private void openRecoveryForReview(android.net.Uri document, char[] phrase) {
        if (!isLegacyProject() || isBusy() || destroyed) {
            Arrays.fill(phrase, '\0');
            return;
        }
        recoveryImportInProgress = true;
        refreshControls();
        setStatus("Opening encrypted recovery file on this phone...");
        try {
            executor.execute(
                    () -> {
                        try (InputStream input = getContentResolver().openInputStream(document)) {
                            RecoveryBundle bundle = RecoveryFileReader.open(input, phrase);
                            byte[] certificate = bundle.signer().certificate().getEncoded();
                            String fingerprint =
                                    shortHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(certificate));
                            runOnUiThread(() -> showRecoveryImportReview(bundle, fingerprint));
                        } catch (Exception failure) {
                            runOnUiThread(
                                    () -> {
                                        recoveryImportInProgress = false;
                                        if (!destroyed) {
                                            refreshControls();
                                            setStatus(
                                                    "Recovery file could not be opened. Check the file and phrase.");
                                        }
                                    });
                        } finally {
                            Arrays.fill(phrase, '\0');
                        }
                    });
        } catch (RuntimeException unavailable) {
            Arrays.fill(phrase, '\0');
            recoveryImportInProgress = false;
            refreshControls();
            setStatus("Could not start recovery file verification.");
        }
    }

    private void showRecoveryImportReview(RecoveryBundle bundle, String fingerprint) {
        if (destroyed) return;
        String details =
                "Project: "
                        + bundle.packageName()
                        + "\nSaved source revision: "
                        + bundle.revision()
                        + "\nSaved app version: "
                        + bundle.versionCode()
                        + "\nSource files: "
                        + bundle.sources().size()
                        + "\nSigner SHA-256 prefix: "
                        + fingerprint
                        + "\n\nRestore will preserve the existing default revision, add these exact "
                        + "source files as a new revision and save this signing identity on the phone. "
                        + "No APK is installed. Cancel leaves the project unchanged.";
        new AlertDialog.Builder(this)
                .setTitle("Review recovery import")
                .setMessage(details)
                .setNegativeButton(
                        "Cancel",
                        (dialog, which) -> {
                            recoveryImportInProgress = false;
                            refreshControls();
                            setStatus(
                                    "Recovery import cancelled. No project change was requested.");
                        })
                .setPositiveButton("Restore", (dialog, which) -> adoptRecovery(bundle))
                .setOnCancelListener(
                        dialog -> {
                            recoveryImportInProgress = false;
                            refreshControls();
                        })
                .show();
    }

    private void adoptRecovery(RecoveryBundle bundle) {
        if (!isLegacyProject() || destroyed) return;
        setStatus("Restoring the saved project and signer on this phone...");
        try {
            RECOVERY_TASKS.execute(
                    () -> {
                        int before = editorRevision;
                        try {
                            if (recoveryAdoption.adopt(bundle) != RecoveryAdoption.Status.COMPLETE)
                                throw new IllegalStateException(
                                        "Recovery import needs the same file again");
                            runOnUiThread(this::finishRecoveryImport);
                        } catch (Exception failure) {
                            settleRecoveryImportFailure(before);
                        }
                    });
        } catch (RuntimeException unavailable) {
            recoveryImportInProgress = false;
            refreshControls();
            setStatus("Could not start recovery import.");
        }
    }

    private void finishRecoveryImport() {
        try {
            editorRevision = sourceStore.revision();
            source.setText(sourceStore.readSource());
            signedArtifact = null;
            runs.forgetPendingRecovery();
            startupBlocked = false;
            recoveryImportPending = false;
            startupRecoveryMessage = null;
            recoveryImportInProgress = false;
            refreshControls();
            setStatus(
                    "Recovery source and signing identity restored. Review the source, then "
                            + "build locally. Installation still requires your approval.");
        } catch (Exception failure) {
            recoveryImportInProgress = false;
            startupBlocked = true;
            refreshControls();
            setStatus("Recovery was saved, but the editor could not verify it. Reopen the app.");
        }
    }

    private void settleRecoveryImportFailure(int before) {
        try {
            RecoveryAdoption.Status status = recoveryAdoption.resume();
            int after = sourceStore.revision();
            runOnUiThread(() -> showRecoveryImportSettlement(before, after, status));
        } catch (Exception unresolved) {
            runOnUiThread(
                    () -> {
                        startupBlocked = true;
                        recoveryImportPending = false;
                        startupRecoveryMessage =
                                "Recovery import could not be verified. Builds are disabled "
                                        + "to protect saved work.";
                        setStatus(startupRecoveryMessage);
                        recoveryImportInProgress = false;
                        refreshControls();
                    });
        }
    }

    private void showRecoveryImportSettlement(
            int before, int after, RecoveryAdoption.Status status) {
        try {
            if (status == RecoveryAdoption.Status.PENDING_REIMPORT) {
                recoveryImportPending = true;
                startupBlocked = true;
                startupRecoveryMessage =
                        "Recovery import is waiting for the same encrypted file "
                                + "and phrase. Builds are disabled until it finishes.";
                setStatus(startupRecoveryMessage);
            } else if (after != before) {
                finishRecoveryImport();
                return;
            } else {
                setStatus(
                        "Recovery was not adopted. Use an unchanged clean project and check "
                                + "that any installed generated app has a compatible signing identity.");
            }
        } finally {
            recoveryImportInProgress = false;
            refreshControls();
        }
    }

    private static String shortHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[16];
        for (int i = 0; i < 8; i++) {
            result[2 * i] = digits[(bytes[i] & 255) >>> 4];
            result[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    private void prepareRecoveryExport(int revision, char[] phrase) {
        if (!isLegacyProject()
                || isBusy()
                || destroyed
                || startupBlocked
                || revision != editorRevision) {
            Arrays.fill(phrase, '\0');
            return;
        }
        final int lastVersion;
        try {
            lastVersion = Math.max(1, runs.nextVersionCode(activeProject) - 1);
        } catch (Exception failure) {
            Arrays.fill(phrase, '\0');
            setStatus("Could not verify the generated app version for recovery export.");
            return;
        }
        exportInProgress = true;
        refreshControls();
        setStatus("Encrypting the saved project and signing identity on this phone...");
        try {
            executor.execute(
                    () -> {
                        byte[] sealed = null;
                        try {
                            PortableSignerStore portable =
                                    new PortableSignerStore(
                                            getApplicationContext(), "counter-alpha");
                            sealed =
                                    RecoveryExport.prepare(
                                            getApplicationContext(),
                                            sourceStore,
                                            new ProjectSigner(),
                                            portable,
                                            revision,
                                            lastVersion,
                                            phrase);
                            byte[] ready = sealed;
                            sealed = null;
                            runOnUiThread(() -> chooseRecoveryDestination(ready, revision));
                        } catch (Exception failure) {
                            runOnUiThread(
                                    () -> {
                                        if (!destroyed)
                                            setStatus(
                                                    "Could not create a recovery file. "
                                                            + "The signer may already have changed; check saved source and retry.");
                                        exportInProgress = false;
                                        if (!destroyed) refreshControls();
                                    });
                        } finally {
                            Arrays.fill(phrase, '\0');
                            if (sealed != null) Arrays.fill(sealed, (byte) 0);
                        }
                    });
        } catch (RuntimeException unavailable) {
            Arrays.fill(phrase, '\0');
            exportInProgress = false;
            refreshControls();
            setStatus("Could not start recovery export.");
        }
    }

    private void chooseRecoveryDestination(byte[] sealed, int revision) {
        if (destroyed || !isLegacyProject() || sourceStore == null) {
            Arrays.fill(sealed, (byte) 0);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "smallwright-recovery-revision-" + revision + ".plbr");
        pendingRecoveryExport = sealed;
        pendingRecoveryRevision = revision;
        try {
            startActivityForResult(intent, RECOVERY_EXPORT_REQUEST);
        } catch (Exception unavailable) {
            Arrays.fill(sealed, (byte) 0);
            pendingRecoveryExport = null;
            exportInProgress = false;
            refreshControls();
            setStatus(
                    "Android's document picker is unavailable. The signer may already "
                            + "have changed; retry recovery export later.");
        }
    }

    private void exportRecoveryToSelectedDocument(
            byte[] sealed, int revision, android.net.Uri destination) {
        if (sealed == null
                || destroyed
                || !isLegacyProject()
                || destination == null
                || !"content".equals(destination.getScheme())) {
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
            exportInProgress = false;
            refreshControls();
            setStatus("Android did not return a document destination for recovery export.");
            return;
        }
        try {
            executor.execute(
                    () -> {
                        try {
                            if (sourceStore.revision() != revision || destroyed) {
                                throw new IOException("Saved source revision changed");
                            }
                            try (OutputStream output =
                                    getContentResolver().openOutputStream(destination, "wt")) {
                                if (output == null)
                                    throw new IOException("Selected destination is unavailable");
                                output.write(sealed);
                            }
                            verifyRecoveryDocument(destination, sealed);
                            runOnUiThread(
                                    () -> {
                                        if (!destroyed)
                                            setStatus(
                                                    "Encrypted recovery file saved "
                                                            + "and verified. Keep its phrase separately.");
                                    });
                        } catch (Exception failure) {
                            runOnUiThread(
                                    () -> {
                                        if (!destroyed)
                                            setStatus(
                                                    "Could not verify the recovery file. "
                                                            + "The selected document may be incomplete; remove it and retry.");
                                    });
                        } finally {
                            Arrays.fill(sealed, (byte) 0);
                            runOnUiThread(
                                    () -> {
                                        exportInProgress = false;
                                        if (!destroyed) refreshControls();
                                    });
                        }
                    });
        } catch (RuntimeException unavailable) {
            Arrays.fill(sealed, (byte) 0);
            exportInProgress = false;
            refreshControls();
            setStatus("Could not start recovery file write.");
        }
    }

    private void verifyRecoveryDocument(android.net.Uri destination, byte[] expected)
            throws IOException {
        try (InputStream input = getContentResolver().openInputStream(destination)) {
            if (input == null) throw new IOException("Recovery destination cannot be read back");
            byte[] buffer = new byte[4096];
            int offset = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > expected.length - offset)
                    throw new IOException("Recovery file length changed");
                for (int i = 0; i < count; i++) {
                    if (buffer[i] != expected[offset + i]) {
                        throw new IOException("Recovery file contents changed");
                    }
                }
                offset += count;
            }
            if (offset != expected.length) throw new IOException("Recovery file is incomplete");
        }
    }

    private void exportSourceToSelectedDocument(
            SourceExport selected, android.net.Uri destination) {
        if (selected == null
                || isBusy()
                || destroyed
                || startupBlocked
                || !selected.packageName().equals(activeProject.packageName())) return;
        ProjectIdentity project = activeProject;
        ProjectSourceStore projectSource = sourceStore;
        if (destination == null || !"content".equals(destination.getScheme())) {
            setStatus("Android did not return a document destination for source export.");
            return;
        }
        try {
            if (sourceStore.revision() != selected.revision()
                    || editorRevision != selected.revision()
                    || !source.getText().toString().equals(sourceStore.readSource())) {
                setStatus("Source changed; review the export again.");
                return;
            }
        } catch (Exception failure) {
            setStatus("Could not verify the saved source revision for export.");
            return;
        }
        exportInProgress = true;
        refreshControls();
        executor.execute(
                () -> {
                    try {
                        if (projectSource.revision() != selected.revision()
                                || destroyed
                                || !project.equals(activeProject))
                            throw new IOException("Source revision changed");
                        try (OutputStream output =
                                getContentResolver().openOutputStream(destination, "wt")) {
                            if (output == null)
                                throw new IOException("Selected destination is unavailable");
                            output.write(selected.bytes());
                        }
                        runOnUiThread(
                                () -> {
                                    if (!destroyed)
                                        setStatus(
                                                "Source revision "
                                                        + selected.revision()
                                                        + " exported.");
                                });
                    } catch (Exception failure) {
                        runOnUiThread(
                                () -> {
                                    if (!destroyed)
                                        setStatus(
                                                "Could not export source. "
                                                        + "The selected document may be incomplete; remove it before retrying.");
                                });
                    } finally {
                        runOnUiThread(
                                () -> {
                                    exportInProgress = false;
                                    if (!destroyed) refreshControls();
                                });
                    }
                });
    }

    private void exportApkToSelectedDocument(
            VerifiedArtifact artifact, android.net.Uri destination) {
        if (artifact == null || artifact != signedArtifact || isBusy() || destroyed) {
            setStatus("APK export canceled because the verified build changed.");
            return;
        }
        if (destination == null || !"content".equals(destination.getScheme())) {
            setStatus("Android did not return a document destination for APK export.");
            return;
        }
        exportInProgress = true;
        refreshControls();
        setStatus("Rechecking signed APK before export...");
        executor.execute(
                () -> {
                    try {
                        byte[] staged = ArtifactExport.prepare(artifact);
                        if (artifact != signedArtifact || destroyed)
                            throw new IOException("Verified build changed");
                        try (OutputStream output =
                                getContentResolver().openOutputStream(destination, "wt")) {
                            if (output == null)
                                throw new IOException("Selected destination is unavailable");
                            output.write(staged);
                        }
                        runOnUiThread(
                                () -> {
                                    if (!destroyed) setStatus("App file exported.");
                                });
                    } catch (Exception failure) {
                        runOnUiThread(
                                () -> {
                                    if (!destroyed)
                                        setStatus(
                                                "Could not export verified APK. "
                                                        + "The selected document may be incomplete; remove it before retrying.");
                                });
                    } finally {
                        runOnUiThread(
                                () -> {
                                    exportInProgress = false;
                                    if (!destroyed) refreshControls();
                                });
                    }
                });
    }

    private void chooseCrashReport() {
        if (isBusy() || destroyed || startupBlocked) return;
        selectedCrashReport = null;
        refreshControls();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        try {
            startActivityForResult(intent, CRASH_IMPORT_REQUEST);
        } catch (Exception unavailable) {
            setStatus("Android's document picker is unavailable.");
        }
    }

    private void previewCrashReport(android.net.Uri uri) {
        if (isBusy()
                || destroyed
                || startupBlocked
                || uri == null
                || !"content".equals(uri.getScheme())) return;
        exportInProgress = true;
        refreshControls();
        setStatus("Reading selected report locally...");
        executor.execute(
                () -> {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        String report = CrashReportImport.read(input);
                        runOnUiThread(
                                () -> {
                                    exportInProgress = false;
                                    if (destroyed) return;
                                    refreshControls();
                                    TextView preview = new TextView(this);
                                    preview.setText(
                                            "Only the report below goes with your next message. "
                                                    + "Cancel keeps it on this phone.\n\n"
                                                    + report);
                                    preview.setTextIsSelectable(true);
                                    preview.setPadding(24, 16, 24, 16);
                                    ScrollView scroll = new ScrollView(this);
                                    scroll.addView(preview);
                                    new AlertDialog.Builder(this)
                                            .setTitle("Review crash report on phone")
                                            .setView(scroll)
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Add to my next message",
                                                    (dialog, which) -> {
                                                        selectedCrashReport = report;
                                                        refreshControls();
                                                        setStatus(
                                                                "Crash report added. It goes with your next message.");
                                                    })
                                            .show();
                                });
                    } catch (Exception failure) {
                        runOnUiThread(
                                () -> {
                                    exportInProgress = false;
                                    if (!destroyed) {
                                        refreshControls();
                                        setStatus(
                                                "Could not import a generated app crash report. Select its text export.");
                                    }
                                });
                    }
                });
    }

    private void restoreSignedArtifact() {
        ProjectIdentity project = activeProject;
        ProjectSourceStore projectSource = sourceStore;
        try {
            executor.execute(
                    () -> {
                        try {
                            VerifiedArtifact artifact =
                                    SignedArtifactReceipt.restore(
                                            this, project, projectSource.revision());
                            runOnUiThread(
                                    () -> {
                                        if (!destroyed
                                                && project.equals(activeProject)
                                                && !startupBlocked
                                                && !isBusy()
                                                && runs.pendingRecovery() == null
                                                && signedArtifact == null) {
                                            signedArtifact = artifact;
                                            refreshControls();
                                            setStatus("");
                                        }
                                    });
                        } catch (Exception invalidOrMissing) {
                            // No receipt, or it no longer authorizes an APK. Install/export stay
                            // disabled.
                        }
                    });
        } catch (RuntimeException ignored) {
            // Startup remains usable without a restored artifact.
        }
    }

    private void resumePendingBuild() {
        if (startupBlocked || destroyed) return;
        runs.resumePendingBuild(activeProject, sourceStore, worker);
    }

    private boolean isBusy() {
        return projectBindingInProgress
                || runs.isBusy()
                || exportInProgress
                || recoveryImportInProgress
                || testAcceptanceInProgress
                || undoInProgress;
    }

    private void refreshControls() {
        boolean idle = !isBusy() && !destroyed && !startupBlocked;
        boolean canSwitch =
                idle
                        && runs.pendingRecovery() == null
                        && !runs.recoveryQueued()
                        && pendingApkExport == null
                        && pendingSourceExport == null
                        && pendingRecoveryExport == null;
        boolean canBrowseChats =
                !projectBindingInProgress
                        && !destroyed
                        && !startupBlocked
                        && !exportInProgress
                        && !recoveryImportInProgress;
        projectPicker.setEnabled(canSwitch);
        newProject.setEnabled(canSwitch && source.getText().toString().equals(savedSource()));
        newChat.setEnabled(idle);
        chatPicker.setEnabled(canBrowseChats);
        ui.setOpeningProject(projectBindingInProgress);
        stop.setEnabled(
                (isBusy() || runs.pendingRecovery() != null) && runs.canStop() && !destroyed);
        source.setEnabled(idle && runs.pendingRecovery() == null);
        modelRequest.setEnabled(idle);
        build.setText(
                runs.pendingRecovery() != null
                        ? Labels.RETRY_SAVED_BUILD
                        : buildAfterUndo && signedArtifact == null
                                ? Labels.BUILD_APP
                                : Labels.BUILD_SOURCE);
        build.setEnabled(idle && worker != null);
        undo.setEnabled(
                idle
                        && runs.pendingRecovery() == null
                        && BuilderChanges.refusal(false, unsavedEdits(), undoable) == null);
        generate.setEnabled(idle && worker != null && runs.pendingRecovery() == null);
        previewContext.setEnabled(idle);
        providerKey.setEnabled(idle);
        saveProviderKey.setEnabled(idle);
        removeProviderKey.setEnabled(!isBusy() && !destroyed);
        ui.getServerBase().setEnabled(idle);
        ui.getServerKey().setEnabled(idle);
        ui.getSaveServer().setEnabled(idle);
        ui.getRemoveServerKey().setEnabled(!isBusy() && !destroyed);
        install.setEnabled(idle && signedArtifact != null);
        if (handoffArtifact != signedArtifact) checkTestHandoff(false);
        acceptTest.setEnabled(
                idle
                        && signedArtifact != null
                        && testState == AppTestStatus.State.INSTALLED_UNTESTED);
        exportApk.setEnabled(idle && signedArtifact != null);
        exportSource.setEnabled(idle);
        deleteProject.setEnabled(
                canSwitch
                        && activeProject != null
                        && !LEGACY_PROJECT.equals(activeProject.id())
                        && projectList.size() > 1
                        && source.getText().toString().equals(savedSource()));
        exportRecovery.setEnabled(idle && isLegacyProject());
        importRecovery.setEnabled(
                !isBusy()
                        && !destroyed
                        && (!startupBlocked || recoveryImportPending)
                        && isLegacyProject());
        importCrashReport.setEnabled(idle);
        clearCrashReport.setEnabled(idle && selectedCrashReport != null);
        ui.getAttachImages()
                .setEnabled(
                        idle
                                && selectedChat != null
                                && attached.size() + picturesPending < AttachedImages.MAX_COUNT);
    }

    /** Opens Android's photo picker; it needs no storage permission. */
    private void choosePictures() {
        int room = AttachedImages.MAX_COUNT - attached.size() - picturesPending;
        if (isBusy() || destroyed || room < 1) return;
        try {
            pickPictures.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
        } catch (RuntimeException unavailable) {
            setStatus("The photo picker is not available on this phone.");
        }
    }

    /**
     * Shrinks the picked pictures off the screen's thread, keeping as many as there is room for.
     */
    private void attachPicked(List<Uri> picked) {
        if (destroyed || picked == null || picked.isEmpty()) return;
        int room = AttachedImages.MAX_COUNT - attached.size() - picturesPending;
        List<Uri> taken =
                new ArrayList<>(picked.subList(0, Math.max(0, Math.min(room, picked.size()))));
        boolean dropped = taken.size() < picked.size();
        if (taken.isEmpty()) {
            setStatus("A message can have up to " + AttachedImages.MAX_COUNT + " pictures.");
            return;
        }
        long sequence = attachSequence;
        picturesPending += taken.size();
        refreshControls();
        setStatus("Getting your pictures ready...");
        android.content.ContentResolver resolver = getContentResolver();
        PICTURE_TASKS.execute(
                () -> {
                    List<byte[]> ready = new ArrayList<>();
                    List<Bitmap> small = new ArrayList<>();
                    int failed = 0;
                    for (Uri uri : taken) {
                        try {
                            byte[] jpeg = PictureShrink.jpeg(resolver, uri);
                            Bitmap thumbnail = PictureShrink.thumbnail(jpeg);
                            if (thumbnail == null) throw new IOException("No thumbnail");
                            ready.add(jpeg);
                            small.add(thumbnail);
                        } catch (IOException failure) {
                            failed++;
                        }
                    }
                    int unread = failed;
                    runOnUiThread(
                            () -> {
                                if (sequence == attachSequence) picturesPending -= taken.size();
                                if (destroyed || sequence != attachSequence) return;
                                attached.addAll(ready);
                                List<Bitmap> shown = new ArrayList<>(ui.getAttachments());
                                shown.addAll(small);
                                ui.setAttachments(shown);
                                refreshControls();
                                setStatus(
                                        unread > 0
                                                ? "Could not read "
                                                        + unread
                                                        + " of the pictures. Try another one."
                                                : dropped
                                                        ? "A message can have up to "
                                                                + AttachedImages.MAX_COUNT
                                                                + " pictures. The first ones were kept."
                                                        : "");
                            });
                });
    }

    /** Takes every picture off the next message, including ones still being shrunk. */
    private void clearAttachments() {
        attachSequence++;
        picturesPending = 0;
        attached.clear();
        ui.setAttachments(new ArrayList<>());
    }

    /** Small copies of the pictures saved with a message; none when they cannot be read. */
    private List<Bitmap> thumbnails(ChatStore.Message message) {
        if (!"user".equals(message.role)) return List.of();
        String key = activeProject.id() + "/" + selectedChat.id + "/" + message.id;
        List<Bitmap> cached = thumbnailCache.get(key);
        if (cached != null) return cached;
        List<Bitmap> small = new ArrayList<>();
        try {
            for (byte[] jpeg : chatStore.images(activeProject.id(), selectedChat.id, message.id)) {
                Bitmap thumbnail = PictureShrink.thumbnail(jpeg);
                if (thumbnail != null) small.add(thumbnail);
            }
        } catch (IOException | RuntimeException unreadable) {
            return List.of();
        }
        thumbnailCache.put(key, small);
        return small;
    }

    private void loadChats() throws IOException {
        chats.clear();
        chats.addAll(chatStore.listChats(activeProject.id()));
        if (chats.isEmpty()) chats.add(chatStore.createChat(activeProject.id(), "App ideas"));
        List<String> titles = new ArrayList<>();
        for (ChatStore.Chat chat : chats) titles.add(chat.title);
        chatPicker.setItems(titles);
        String savedId =
                getSharedPreferences("chat-selection", MODE_PRIVATE)
                        .getString(activeProject.id(), null);
        int selectedIndex = 0;
        for (int i = 0; i < chats.size(); i++) {
            if (chats.get(i).id.equals(savedId)) {
                selectedIndex = i;
                break;
            }
        }
        selectedChat = chats.get(selectedIndex);
        chatPicker.setSelection(selectedIndex);
        showSelectedChat();
    }

    private void createChat() {
        if (isBusy() || startupBlocked || destroyed) return;
        try {
            ChatStore.Chat created =
                    chatStore.createChat(activeProject.id(), "Chat " + (chats.size() + 1));
            chats.add(0, created);
            List<String> titles = new ArrayList<>(chatPicker.getItems());
            titles.add(0, created.title);
            chatPicker.setItems(titles);
            chatPicker.setSelection(0);
            selectedChat = created;
            saveSelectedChat();
            showSelectedChat();
            modelRequest.setText("");
            clearAttachments();
            refreshControls();
        } catch (IOException | IllegalArgumentException failure) {
            setStatus("Could not create a chat. Saved project source was not changed.");
        }
    }

    private void saveSelectedChat() {
        if (selectedChat != null
                && !getSharedPreferences("chat-selection", MODE_PRIVATE)
                        .edit()
                        .putString(activeProject.id(), selectedChat.id)
                        .commit()) {
            setStatus("Could not save the selected chat. Chat history remains saved.");
        }
    }

    private void showSelectedChat() {
        refreshUndo();
        showTranscript();
    }

    /** Redraws the chat; a run's end leaves the undo check to the revision it reports next. */
    private void showTranscript() {
        if (selectedChat == null) return;
        ui.setSelectedChatId(selectedChat.id);
        reportViewing();
        try {
            ui.setProjectBrief(sourceStore.brief().read());
        } catch (IOException unreadable) {
            ui.setProjectBrief("");
        }
        try {
            StringBuilder history = new StringBuilder();
            List<Turn> turns = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            List<ChatStore.Message> messages =
                    chatStore.listMessages(activeProject.id(), selectedChat.id);
            int newest = editorRevision;
            for (ChatStore.Message message : messages)
                newest = Math.max(newest, message.sourceRevision);
            for (ChatStore.Message message : messages) {
                if (message.sourceRevision < newest
                        && "status".equals(message.role)
                        && RunCoordinator.READY.equals(message.text)) continue;
                ids.add(message.id);
                turns.add(
                        new Turn(
                                message.role,
                                message.sourceRevision,
                                message.text,
                                thumbnails(message)));
                history.append(message.role.equals("user") ? "You" : "Builder")
                        .append(" · revision ")
                        .append(message.sourceRevision)
                        .append("\n")
                        .append(message.text)
                        .append("\n\n");
            }
            chatHistory.setText(
                    history.length() == 0
                            ? "Start a chat to work on this project."
                            : history.toString());
            shownMessageIds = ids;
            ui.setTranscript(turns);
        } catch (IOException | IllegalArgumentException failure) {
            shownMessageIds = new ArrayList<>();
            ui.setTranscript(new ArrayList<>());
            chatHistory.setText("Could not read this chat. Project source was not changed.");
        }
    }

    private boolean unsavedEdits() {
        return !source.getText().toString().equals(savedSource());
    }

    /** Finds what Undo would undo now, and which summary in this chat shows a small Undo. */
    private void refreshUndo() {
        ProjectIdentity project = activeProject;
        ProjectSourceStore projectSource = sourceStore;
        String chatId = selectedChat == null ? null : selectedChat.id;
        int sequence = ++undoSequence;
        if (project == null || projectSource == null) return;
        try {
            executor.execute(
                    () -> {
                        BuilderChanges.Undoable found = null;
                        boolean undoneHead = false;
                        try {
                            BuilderChanges changes = new BuilderChanges(projectSource, chatStore);
                            int head = projectSource.revision();
                            found = changes.undoable(head);
                            undoneHead = changes.undidAt(head);
                        } catch (Exception unavailable) {
                            Log.w(
                                    "SmallwrightUndo",
                                    "Could not read the change history",
                                    unavailable);
                        }
                        BuilderChanges.Undoable result = found;
                        boolean buildNext = undoneHead;
                        runOnUiThread(
                                () -> {
                                    if (destroyed
                                            || sequence != undoSequence
                                            || !project.equals(activeProject)) return;
                                    undoable = result;
                                    buildAfterUndo = buildNext;
                                    ui.setUndoUnder(
                                            result != null
                                                            && result.newest()
                                                            && result.change()
                                                                    .chatId()
                                                                    .equals(chatId)
                                                    ? shownMessageIds.indexOf(
                                                            result.change().messageId())
                                                    : -1);
                                    refreshControls();
                                });
                    });
        } catch (RuntimeException rejected) {
            Log.w("SmallwrightUndo", "Could not look for a change to undo", rejected);
        }
    }

    /** Asks before undoing, because the app on the phone changes only at the next install. */
    private void confirmUndo() {
        String refused =
                BuilderChanges.refusal(
                        isBusy() || runs.pendingRecovery() != null, unsavedEdits(), undoable);
        if (refused != null || destroyed || startupBlocked) {
            if (refused != null) setStatus(refused);
            return;
        }
        BuilderChanges.Change change = undoable.change();
        int revision = editorRevision;
        String sent =
                android.text.format.DateUtils.formatDateTime(
                        this,
                        change.atMillis(),
                        android.text.format.DateUtils.FORMAT_SHOW_DATE
                                | android.text.format.DateUtils.FORMAT_SHOW_TIME
                                | android.text.format.DateUtils.FORMAT_ABBREV_MONTH);
        new AlertDialog.Builder(this)
                .setTitle("Undo the builder's last change?")
                .setMessage(
                        "Your app goes back to how it was before \""
                                + BuilderChanges.quoted(change)
                                + "\", sent "
                                + sent
                                + ". The app on this phone stays as it is until you install"
                                + " again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Undo", (dialog, which) -> undoLastChange(revision, change))
                .show();
    }

    private void undoLastChange(int revision, BuilderChanges.Change shown) {
        if (isBusy() || destroyed || startupBlocked || unsavedEdits()) return;
        ProjectIdentity project = activeProject;
        ProjectSourceStore projectSource = sourceStore;
        undoInProgress = true;
        refreshControls();
        try {
            executor.execute(
                    () -> {
                        BuilderChanges.Undone undone = null;
                        VerifiedArtifact kept = null;
                        String restored = null;
                        String failed = null;
                        try {
                            undone =
                                    new BuilderChanges(projectSource, chatStore)
                                            .undo(revision, shown);
                            try {
                                restored = projectSource.readSource();
                            } catch (IOException | RuntimeException unreadable) {
                                Log.w(
                                        "SmallwrightUndo",
                                        "Could not read the restored source",
                                        unreadable);
                            }
                            if (undone.artifactKept())
                                kept =
                                        SignedArtifactReceipt.restore(
                                                this, project, undone.revision());
                        } catch (BuilderChanges.Refused refused) {
                            failed = refused.getMessage();
                        } catch (IllegalStateException stale) {
                            failed = "The project changed since you asked. Nothing was undone.";
                        } catch (Exception failure) {
                            Log.w("SmallwrightUndo", "Undo did not finish", failure);
                            failed =
                                    undone == null
                                            ? "Could not undo. Nothing was changed."
                                            : "Undone, but the app on this phone needs a new"
                                                    + " build.";
                        }
                        BuilderChanges.Undone result = undone;
                        VerifiedArtifact offer = kept;
                        String text = restored;
                        String problem = failed;
                        runOnUiThread(
                                () -> {
                                    undoInProgress = false;
                                    if (destroyed || !project.equals(activeProject)) return;
                                    if (result != null) {
                                        if (text != null) {
                                            source.setText(text);
                                            editorRevision = result.revision();
                                        }
                                        signedArtifact = offer;
                                        buildAfterUndo = offer == null;
                                        ui.setToolsOpen(false);
                                        showSelectedChat();
                                    }
                                    refreshControls();
                                    setStatus(
                                            problem != null
                                                    ? problem
                                                    : text == null
                                                            ? "Undone, but the restored source"
                                                                    + " could not be shown. Open"
                                                                    + " the project again before"
                                                                    + " you build."
                                                            : !result.noted()
                                                                    ? "Undone. This chat is full, so no"
                                                                            + " note was added to it."
                                                                    : offer != null
                                                                            ? "Undone. Install it to put"
                                                                                    + " this version on"
                                                                                    + " your phone."
                                                                            : "Undone. Build the app,"
                                                                                    + " then install it,"
                                                                                    + " to put this"
                                                                                    + " version on your"
                                                                                    + " phone.");
                                });
                    });
        } catch (RuntimeException scheduling) {
            undoInProgress = false;
            refreshControls();
            setStatus("Could not undo. Nothing was changed.");
        }
    }

    /** Opens the key page instead of starting a run that cannot reach the model. */
    private boolean needsKey() {
        if (ui.getModel().provider.isOpenRouter()) {
            if (ui.getHasKey()) return false;
            keyResult("Add your OpenRouter key first. Nothing was sent.");
        } else {
            if (ui.getHasServerKey()) return false;
            serverResult("Add the server's key first. Nothing was sent.");
        }
        ui.setSettingsOpen(true);
        return true;
    }

    /**
     * The one send: the model answers, builds, or changes the app. {@code answers} is a question
     * round's answers with the person's words already after them, or null to send the prompt.
     */
    private void sendRequest(String answers) {
        if (isBusy()
                || destroyed
                || worker == null
                || startupBlocked
                || runs.pendingRecovery() != null
                || needsKey()) return;
        try {
            String typed = answers != null ? answers : modelRequest.getText().toString();
            if (typed.trim().isEmpty()) {
                setStatus("Type a message first.");
                return;
            }
            if (!source.getText().toString().equals(sourceStore.readSource())) {
                setStatus("Save source with Build before sending a message.");
                return;
            }
            String request =
                    selectedCrashReport == null
                            ? typed
                            : CrashReportImport.composeFeedback(typed, selectedCrashReport);
            if (picturesPending > 0) {
                setStatus("Getting your pictures ready. Send again in a moment.");
                return;
            }
            runs.send(
                    activeProject,
                    sourceStore,
                    contextGate,
                    selectedChat.id,
                    request,
                    typed,
                    source.getText().toString(),
                    editorRevision,
                    ui.getModel(),
                    ui.getReasoning(),
                    signedArtifact,
                    new AttachedImages(attached));
        } catch (RunSkills.Refused refused) {
            setStatus(refused.getMessage());
        } catch (Exception failure) {
            setStatus("Could not send your message. Check the app's code and try again.");
        }
    }

    /** Shows the first request a Send would make now; it sends nothing. */
    private void showContextPreview() {
        if (isBusy() || destroyed) return;
        try {
            int revision = editorRevision;
            if (!source.getText().toString().equals(sourceStore.readSource())) {
                throw new IllegalStateException("Save source with Build before previewing it");
            }
            ContextPreviewGate.Preview preview =
                    contextGate.previewChat(
                            selectedChat == null ? null : selectedChat.id,
                            modelRequest.getText().toString(),
                            revision,
                            new AttachedImages(attached));
            TextView payload = new TextView(this);
            payload.setText(
                    "This is what the builder sends first: its instructions, your message with"
                            + " the chat so far, and the list of the app's files. It then reads"
                            + " only the files it needs.\n\n"
                            + (attached.isEmpty()
                                    ? ""
                                    : "With "
                                            + attached.size()
                                            + " picture(s) after your words.\n\n")
                            + preview.payload());
            payload.setTextIsSelectable(true);
            payload.setPadding(24, 16, 24, 16);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(payload);
            new AlertDialog.Builder(this)
                    .setTitle("Model context for revision " + preview.revision())
                    .setView(scroll)
                    .setPositiveButton("Close", null)
                    .show();
        } catch (Exception exception) {
            setStatus("Could not preview model context: " + exception.getMessage());
        }
    }

    private void saveOpenRouterKey() {
        CharSequence entered = providerKey.chars();
        if (entered.length() < 20 || entered.length() > 256) {
            providerKey.clear();
            keyResult("That does not look like an OpenRouter key. Nothing was saved.");
            return;
        }
        char[] characters = new char[entered.length()];
        for (int i = 0; i < entered.length(); i++) characters[i] = entered.charAt(i);
        providerKey.clear();
        byte[] key;
        try {
            key = ModelKeyInput.openRouterKey(characters);
        } catch (IllegalArgumentException exception) {
            keyResult("That does not look like an OpenRouter key. Nothing was saved.");
            return;
        }
        saveProviderKey.setEnabled(false);
        executor.execute(
                () -> {
                    try {
                        CredentialStore.openRouter(this).save(key);
                        runOnUiThread(
                                () -> {
                                    ui.setHasKey(true);
                                    keyResult("Key saved on this phone.");
                                    if (ui.getOnboarding()
                                            == dev.smallwright.controller.ui.OnboardingStep.KEY)
                                        ui.setOnboarding(
                                                dev.smallwright.controller.ui.OnboardingStep.MODEL);
                                });
                    } catch (Exception exception) {
                        runOnUiThread(() -> keyResult("Could not save the key. Try again."));
                    } finally {
                        Arrays.fill(key, (byte) 0);
                        runOnUiThread(
                                () -> {
                                    if (!destroyed) refreshControls();
                                });
                    }
                });
    }

    private void confirmRemoveOpenRouterKey() {
        new AlertDialog.Builder(this)
                .setTitle("Remove saved OpenRouter key?")
                .setMessage(
                        "The key will be removed from this phone. Your OpenRouter account "
                                + "and its key on the provider website are not changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Remove",
                        (dialog, which) ->
                                executor.execute(
                                        () -> {
                                            try {
                                                CredentialStore.openRouter(this).clear();
                                                runOnUiThread(
                                                        () -> {
                                                            ui.setHasKey(false);
                                                            keyResult(
                                                                    "Key removed from this phone.");
                                                        });
                                            } catch (Exception exception) {
                                                runOnUiThread(
                                                        () ->
                                                                keyResult(
                                                                        "Could not remove the key."));
                                            }
                                        }))
                .show();
    }

    private void setUpModelAndOnboarding() {
        modelChoices = new ModelChoiceStore(this);
        ui.setModel(modelChoices.load());
        ui.setReasoning(modelChoices.loadReasoning());
        if (modelChoices.needsRefresh()) loadModels();
        if (modelChoices.damaged)
            startupModelNote =
                    "The saved model choice could not be read. "
                            + ModelChoice.DEFAULT.shortName()
                            + " is selected; pick another from the model list.";
        boolean keySaved = CredentialStore.openRouter(this).exists();
        ui.setHasKey(keySaved);
        ModelProvider server = modelChoices.server();
        if (server != null) {
            ui.setServerSaved(server.baseUrl);
            ui.getServerBase().setText(server.baseUrl);
        }
        ui.setHasServerKey(server != null && CredentialStore.server(this).exists());
        SharedPreferences onboarding = getSharedPreferences(ONBOARDING, MODE_PRIVATE);
        if (keySaved) onboarding.edit().putBoolean("done", true).apply();
        boolean done = keySaved || onboarding.getBoolean("done", false);
        ui.setOnboardingDone(done);
        if (!done) ui.setOnboarding(dev.smallwright.controller.ui.OnboardingStep.WELCOME);
        ui.setOnFinishOnboarding(
                () -> {
                    ui.setOnboardingDone(true);
                    getSharedPreferences(ONBOARDING, MODE_PRIVATE)
                            .edit()
                            .putBoolean("done", true)
                            .putBoolean(PLATFORM_OFFERED, true)
                            .apply();
                    askForNotificationsOnce();
                });
        ui.setPlatform(
                platformFiles.isPresent()
                        ? dev.smallwright.controller.ui.PlatformState.DONE
                        : dev.smallwright.controller.ui.PlatformState.OFFER);
        ui.setOnGetPlatform(this::downloadPlatformFiles);
        if (done
                && !onboarding.getBoolean(PLATFORM_OFFERED, false)
                && ui.getPlatform() != dev.smallwright.controller.ui.PlatformState.DONE)
            ui.setOnboarding(dev.smallwright.controller.ui.OnboardingStep.PLATFORM);
        if (done && ui.getOnboarding() == null) askForNotificationsOnce();
        ui.setOnPickModel(
                model -> {
                    ui.setReasoningOpen(false);
                    if (modelChoices.save(model)) ui.setModel(model);
                    else setStatus("Could not save the model choice. The previous model stays.");
                });
        ui.setOnPickReasoning(
                level -> {
                    ui.setReasoningOpen(false);
                    if (modelChoices.saveReasoning(level)) ui.setReasoning(level);
                    else setStatus("Could not save the thinking level. The previous one stays.");
                });
        ui.setOnLoadModels(this::loadModels);
        ui.setOnLoadServerModels(this::loadServerModels);
    }

    /** Setup asks once whether runs may notify; a refusal changes nothing else. */
    private void askForNotificationsOnce() {
        SharedPreferences onboarding = getSharedPreferences(ONBOARDING, MODE_PRIVATE);
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
                || onboarding.getBoolean(NOTIFICATIONS_ASKED, false)) return;
        onboarding.edit().putBoolean(NOTIFICATIONS_ASKED, true).apply();
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            requestPermissions(
                    new String[] {android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATIONS_REQUEST);
    }

    private void downloadPlatformFiles() {
        dev.smallwright.controller.ui.PlatformState now = ui.getPlatform();
        if (now == dev.smallwright.controller.ui.PlatformState.DOWNLOADING
                || now == dev.smallwright.controller.ui.PlatformState.DONE) return;
        ui.setPlatformBytes(0);
        ui.setPlatformMessage("");
        ui.setPlatform(dev.smallwright.controller.ui.PlatformState.DOWNLOADING);
        PLATFORM_TASKS.execute(
                () -> {
                    dev.smallwright.controller.ui.PlatformState result =
                            dev.smallwright.controller.ui.PlatformState.FAILED;
                    String message;
                    try {
                        platformFiles.recordAcceptance(
                                java.time.LocalDate.now().toString(), PlatformFiles.TERMS_VERSION);
                        long[] lastShown = {0};
                        platformFiles.download(
                                (bytes, total) -> {
                                    long moment = android.os.SystemClock.elapsedRealtime();
                                    if (moment - lastShown[0] < 200 && bytes < total) return;
                                    lastShown[0] = moment;
                                    runOnUiThread(
                                            () -> {
                                                ui.setPlatformTotal(total);
                                                ui.setPlatformBytes(bytes);
                                            });
                                });
                        result = dev.smallwright.controller.ui.PlatformState.DONE;
                        message = "";
                    } catch (PlatformFiles.Refused refused) {
                        message =
                                "The file did not match what Google published, so it was thrown"
                                        + " away. Try again.";
                    } catch (IOException stopped) {
                        message =
                                "The download stopped. What arrived is kept, so trying again"
                                        + " carries on from there.";
                    }
                    String shown = message;
                    dev.smallwright.controller.ui.PlatformState finished = result;
                    runOnUiThread(
                            () -> {
                                ui.setPlatformMessage(shown);
                                ui.setPlatform(finished);
                            });
                });
    }

    private void loadModels() {
        if (ui.getModelList() == dev.smallwright.controller.ui.ModelListState.LOADING) return;
        ui.setModelList(dev.smallwright.controller.ui.ModelListState.LOADING);
        CATALOG_TASKS.execute(
                () -> {
                    List<ModelChoice> models;
                    try {
                        models = ModelCatalog.fetch();
                    } catch (IOException unavailable) {
                        models = null;
                    }
                    List<ModelChoice> loaded = models;
                    runOnUiThread(
                            () -> {
                                if (destroyed) return;
                                if (loaded == null) {
                                    ui.setModelList(
                                            dev.smallwright.controller.ui.ModelListState.FAILED);
                                    return;
                                }
                                ui.setModels(loaded);
                                ui.setModelList(
                                        dev.smallwright.controller.ui.ModelListState.LOADED);
                                for (ModelChoice listed : loaded)
                                    if (listed.equals(ui.getModel())) {
                                        if (modelChoices.save(listed)) ui.setModel(listed);
                                        break;
                                    }
                            });
                });
    }

    /**
     * Saves the server's address and key together. A new address first removes the old key, so a
     * key is never sent to an address it was not saved for.
     */
    private void saveServer() {
        ModelProvider server;
        try {
            server = ModelProvider.openAiCompatible(ui.getServerBase().getText());
        } catch (IllegalArgumentException refused) {
            ui.getServerKey().clear();
            serverResult(refused.getMessage() + " Nothing was saved.");
            return;
        }
        CharSequence entered = ui.getServerKey().chars();
        char[] characters = new char[entered.length()];
        for (int i = 0; i < entered.length(); i++) characters[i] = entered.charAt(i);
        ui.getServerKey().clear();
        byte[] key;
        try {
            key = ModelKeyInput.serverKey(characters);
        } catch (IllegalArgumentException refused) {
            serverResult("That does not look like an API key. Nothing was saved.");
            return;
        }
        ui.getSaveServer().setEnabled(false);
        executor.execute(
                () -> {
                    String result = "Saved on this phone.";
                    try {
                        CredentialStore keys = CredentialStore.server(this);
                        if (!server.equals(modelChoices.server())) {
                            keys.clear();
                            if (!modelChoices.saveServer(server))
                                throw new IOException("Address not saved");
                        }
                        keys.save(key);
                    } catch (Exception failed) {
                        result = "Could not save. Try again.";
                    } finally {
                        Arrays.fill(key, (byte) 0);
                    }
                    String shown = result;
                    ModelProvider saved = modelChoices.server();
                    boolean hasKey = saved != null && CredentialStore.server(this).exists();
                    runOnUiThread(
                            () -> {
                                if (destroyed) return;
                                ui.setServerSaved(saved == null ? "" : saved.baseUrl);
                                if (saved != null) ui.getServerBase().setText(saved.baseUrl);
                                ui.setHasServerKey(hasKey);
                                ui.setServerModels(List.of());
                                ui.setServerModelList(
                                        dev.smallwright.controller.ui.ModelListState.IDLE);
                                if (!ui.getModel().provider.isOpenRouter())
                                    ui.setModel(modelChoices.load());
                                serverResult(shown);
                                refreshControls();
                            });
                });
    }

    private void confirmRemoveServerKey() {
        new AlertDialog.Builder(this)
                .setTitle("Remove the server key?")
                .setMessage(
                        "The key will be removed from this phone. The account on the server is"
                                + " not changed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Remove",
                        (dialog, which) ->
                                executor.execute(
                                        () -> {
                                            String result = "Key removed from this phone.";
                                            try {
                                                CredentialStore.server(this).clear();
                                            } catch (Exception failed) {
                                                result = "Could not remove the key.";
                                            }
                                            String shown = result;
                                            boolean hasKey = CredentialStore.server(this).exists();
                                            runOnUiThread(
                                                    () -> {
                                                        ui.setHasServerKey(hasKey);
                                                        serverResult(shown);
                                                    });
                                        }))
                .show();
    }

    private void loadServerModels() {
        ModelProvider server = modelChoices.server();
        if (ui.getServerModelList() == dev.smallwright.controller.ui.ModelListState.LOADING
                || server == null) return;
        ui.setServerModelList(dev.smallwright.controller.ui.ModelListState.LOADING);
        CATALOG_TASKS.execute(
                () -> {
                    List<ModelChoice> models = null;
                    byte[] key = null;
                    try {
                        key = CredentialStore.server(this).load();
                        models = ModelCatalog.fetch(server, key);
                    } catch (Exception unavailable) {
                        models = null;
                    } finally {
                        if (key != null) Arrays.fill(key, (byte) 0);
                    }
                    List<ModelChoice> loaded = models;
                    runOnUiThread(
                            () -> {
                                if (destroyed) return;
                                if (!server.baseUrl.equals(ui.getServerSaved())) {
                                    ui.setServerModelList(
                                            dev.smallwright.controller.ui.ModelListState.IDLE);
                                    return;
                                }
                                ui.setServerModels(loaded == null ? List.of() : loaded);
                                ui.setServerModelList(
                                        loaded == null
                                                ? dev.smallwright.controller.ui.ModelListState
                                                        .FAILED
                                                : dev.smallwright.controller.ui.ModelListState
                                                        .LOADED);
                            });
                });
    }

    /** Uses the model named in the field: the listed one, or else by that name. */
    private void useServerModel() {
        ModelProvider server = modelChoices.server();
        String typed = ui.getServerModel().getText().trim();
        if (server == null) {
            serverResult("Add the server's address and key in Settings first.");
            return;
        }
        if (typed.isEmpty()) {
            serverResult("Type the model's name first.");
            return;
        }
        ModelChoice model = null;
        for (ModelChoice listed : ui.getServerModels()) if (listed.id.equals(typed)) model = listed;
        try {
            if (model == null)
                model = ModelChoice.atServer(server, typed, ModelChoice.UNKNOWN_CONTEXT_TOKENS);
        } catch (IllegalArgumentException refused) {
            serverResult("That is not a model name the builder can send.");
            return;
        }
        serverResult("");
        ui.getOnPickModel().accept(model);
        ui.setModelPageOpen(false);
    }

    private void serverResult(String message) {
        ui.setServerResult(message);
        if (!message.isEmpty()) setStatus(message);
    }

    private void showSavedLimits() {
        RunLimits saved = RunSettings.load(this);
        ui.getStepLimit().setText(saved.maxSteps == 0 ? "" : String.valueOf(saved.maxSteps));
        ui.getCostLimit()
                .setText(
                        saved.maxCostUsd == null
                                ? ""
                                : saved.maxCostUsd.stripTrailingZeros().toPlainString());
        ui.setStopWhenStuck(saved.stopWhenStuck);
        ui.setAskBeforeLibraries(RunSettings.asksBeforeLibraries(this));
        ui.setAskBeforeSkills(RunSettings.asksBeforeSkills(this));
    }

    private boolean saveLimits() {
        try {
            RunLimits next =
                    new RunLimits(
                            RunLimits.parseSteps(ui.getStepLimit().getText()),
                            RunLimits.parseCostUsd(ui.getCostLimit().getText()),
                            RunSettings.load(this).stopWhenStuck);
            if (!RunSettings.save(this, next)) {
                ui.setLimitsResult("Could not save the limits. Try again.");
                return false;
            }
            ui.setLimitsResult(
                    "Saved. Steps: "
                            + (next.maxSteps == 0 ? "no limit" : next.maxSteps)
                            + ". Spending: "
                            + (next.maxCostUsd == null
                                    ? "no limit"
                                    : RunLimits.money(next.maxCostUsd))
                            + ".");
            return true;
        } catch (IllegalArgumentException invalid) {
            ui.setLimitsResult(invalid.getMessage());
            return false;
        }
    }

    private void settingsClosed() {
        if (!saveLimits()) showSavedLimits();
        ui.setLimitsResult("");
    }

    private void toggleStopWhenStuck() {
        RunLimits saved = RunSettings.load(this);
        RunLimits next = new RunLimits(saved.maxSteps, saved.maxCostUsd, !saved.stopWhenStuck);
        if (RunSettings.save(this, next)) ui.setStopWhenStuck(next.stopWhenStuck);
        else ui.setLimitsResult("Could not save the setting. Try again.");
    }

    private void toggleAskBeforeLibraries() {
        boolean next = !RunSettings.asksBeforeLibraries(this);
        if (RunSettings.saveAsksBeforeLibraries(this, next)) ui.setAskBeforeLibraries(next);
        else ui.setLimitsResult("Could not save the setting. Try again.");
    }

    private void toggleAskBeforeSkills() {
        boolean next = !RunSettings.asksBeforeSkills(this);
        if (RunSettings.saveAsksBeforeSkills(this, next)) ui.setAskBeforeSkills(next);
        else ui.setLimitsResult("Could not save the setting. Try again.");
    }

    private void refreshNotificationsAllowed() {
        android.app.NotificationManager manager =
                getSystemService(android.app.NotificationManager.class);
        android.app.NotificationChannel channel =
                manager.getNotificationChannel(RunService.CHANNEL);
        ui.setNotificationsAllowed(
                manager.areNotificationsEnabled()
                        && (channel == null
                                || channel.getImportance()
                                        != android.app.NotificationManager.IMPORTANCE_NONE));
    }

    private void openNotificationSettings() {
        try {
            startActivity(
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (android.content.ActivityNotFoundException unavailable) {
            setStatus("This phone cannot open its notification settings from here.");
        }
    }

    private void keyResult(String message) {
        ui.setKeyResult(message);
        setStatus(message);
    }

    private void setStatus(String message) {
        status.setText("Smallwright\n" + message);
    }

    private final ServiceConnection connection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    worker = IBuildService.Stub.asInterface(service);
                    try {
                        String workerStatus = worker.handshake(1);
                        if (startupRecoveryMessage == null)
                            setStatus(
                                    workerStatus
                                            + "\nReady to build controller-owned Java source.");
                        else setStatus(startupRecoveryMessage + "\n" + workerStatus);
                        if (startupModelNote != null) {
                            setStatus(startupModelNote);
                            startupModelNote = null;
                        }
                        refreshControls();
                        resumePendingBuild();
                    } catch (Exception exception) {
                        worker = null;
                        refreshControls();
                        if (startupRecoveryMessage == null)
                            setStatus("Worker authorization failed.");
                        else setStatus(startupRecoveryMessage + "\nWorker authorization failed.");
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    worker = null;
                    refreshControls();
                    if (startupRecoveryMessage == null)
                        setStatus(
                                "Compiler worker disconnected. Reconnecting; retry the local build when ready.");
                    else
                        setStatus(
                                startupRecoveryMessage
                                        + "\nCompiler worker disconnected; reconnecting.");
                }

                /** Android ends a binding for good when the worker is updated or removed. */
                @Override
                public void onBindingDied(ComponentName name) {
                    worker = null;
                    if (bound) unbindService(this);
                    bound = false;
                    refreshControls();
                    if (!destroyed) connectWorker();
                }
            };
}
