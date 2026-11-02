package dev.smallwright.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.gson.JsonArray;
import dev.smallwright.agent.ImageClient;
import dev.smallwright.agent.ModelChoice;
import dev.smallwright.agent.ModelClient;
import dev.smallwright.agent.ReasoningLevel;
import dev.smallwright.agent.WebPages;
import dev.smallwright.controller.ui.Labels;
import dev.smallwright.controller.ui.LiveRun;
import dev.smallwright.controller.ui.Step;
import dev.smallwright.controller.ui.StepKind;
import dev.smallwright.project.ChatStore;
import dev.smallwright.project.ProjectIdentity;
import dev.smallwright.protocol.IBuildService;
import dev.smallwright.security.CredentialStore;
import dev.smallwright.skills.GitHubSkills;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Owns a project's work: the run one Send starts, manual and recovered builds, and Stop. Work goes
 * on with no screen attached; the screen that attaches observes builder steps, status and outcomes
 * through a {@link Listener}, and the run notification follows the run through {@link RunNotices}.
 * State changes and listener calls happen on the main thread.
 */
final class RunCoordinator {
    static final String READY = "Your app is ready.";

    /** What the attached screen shows. Called on the main thread. */
    interface Listener {
        /** A run started with this first builder step; its typed message, if any, was sent. */
        default void started(LiveRun live) {}

        /** The current run's builder steps, or null when none are live. */
        default void steps(LiveRun live) {}

        /** The run's messages are saved: swap its live steps for the saved chat at once. */
        default void chatSaved(String chatId) {}

        default void status(String message) {}

        /** The app Install offers now; null offers none. */
        default void offer(VerifiedArtifact artifact) {}

        /** The message's attached crash report went with it. */
        default void reportUsed() {}

        /** The saved source and revision the editor follows; null keeps what it shows. */
        default void saved(String source, Integer revision) {}

        /** The note about the saved build waiting at startup; null clears it. */
        default void recoveryNote(String message) {}

        /** The run saved its changes, but building needs the Android platform files first. */
        default void platformNeeded() {}

        /** Busy or Stop state changed. */
        default void changed() {}
    }

    private static final Listener DETACHED = new Listener() {};

    private static final String LEGACY_PROJECT = "counter-alpha";

    private final Context context;
    private final SharedPreferences versions;
    private final ChatStore chatStore;
    private final PlatformFiles platformFiles;
    private final Executor work;
    private final Executor main;
    private final RunNotices notices;
    private Supplier<IBuildService> worker = () -> null;
    private Listener listener = DETACHED;
    private ProjectIdentity running;
    private LiveRun liveSteps;

    /** SHA-256 of the context the last send approved at its tap; probes compare it. */
    private volatile String approvedContextSha256;

    private volatile ModelClient.Cancellation activeModelCall;
    private volatile StopToken activeStop;
    private volatile RunQuestion asking;
    private boolean generationInProgress;
    private boolean buildInProgress;
    private boolean recoveryQueued;
    private RunJournal.Snapshot pendingRecovery;

    RunCoordinator(
            Context context,
            SharedPreferences versions,
            ChatStore chatStore,
            PlatformFiles platformFiles,
            Executor work,
            Executor main,
            RunNotices notices) {
        this.context = context;
        this.versions = versions;
        this.chatStore = chatStore;
        this.platformFiles = platformFiles;
        this.work = work;
        this.main = main;
        this.notices = notices;
    }

    /** Shows this screen the work from now on, starting with the run's steps so far. */
    void attach(Listener screen, Supplier<IBuildService> screenWorker) {
        listener = screen;
        worker = screenWorker;
        if (liveSteps != null) screen.steps(liveSteps);
    }

    void detach(Listener screen) {
        if (listener == screen) listener = DETACHED;
    }

    /** The chat on screen now; nulls when the screen is not visible. */
    void viewing(String projectId, String chatId) {
        notices.viewing(projectId, chatId);
    }

    /** The project whose work is going on, or null when none is. */
    ProjectIdentity runningProject() {
        return isBusy() ? running : null;
    }

    /** Stop from a run notification: it stops the run that notification shows, if still working. */
    void stopFromNotice(String runId) {
        if (notices.stops(runId) && stop() != null) listener.changed();
    }

    private void showLive(LiveRun next) {
        liveSteps = next;
        listener.steps(next);
        if (next != null && !next.getSteps().isEmpty()) {
            Step last = next.getSteps().get(next.getSteps().size() - 1);
            if (last.getKind() == StepKind.ASKING) notices.asking(last.getText());
            else notices.step(last.getText());
        }
    }

    /** Takes over an opened project's saved build. */
    void restore(RunJournal.Snapshot pending) {
        pendingRecovery = pending;
    }

    RunJournal.Snapshot pendingRecovery() {
        return pendingRecovery;
    }

    void forgetPendingRecovery() {
        pendingRecovery = null;
    }

    boolean recoveryQueued() {
        return recoveryQueued;
    }

    boolean isBusy() {
        return generationInProgress || buildInProgress;
    }

    boolean canStop() {
        StopToken token = activeStop;
        return token != null && !token.requested;
    }

    /** Stops the current work as Stop does; null when there is nothing to stop. */
    StopToken.Result stop() {
        StopToken token = activeStop;
        StopToken.Result result = stopActive();
        if (result == null) return null;
        if (pendingRecovery != null
                && token.requested
                && pendingRecovery.runId.equals(token.runId)) {
            pendingRecovery = null;
            if (activeStop == token) activeStop = null;
        }
        return result;
    }

    /** Stops the current work and keeps a saved build waiting; null when nothing was stopped. */
    StopToken.Result stopActive() {
        StopToken token = activeStop;
        if (token == null || token.requested) return null;
        return token.stop();
    }

    /** The person's answer to the question the run is waiting on, if any. */
    void answerQuestion(boolean add) {
        RunQuestion asked = asking;
        if (asked != null) asked.answer(add);
    }

    /** Lets Stop end a model request its owner runs itself. */
    StopToken hold(ModelClient.Cancellation cancellation) {
        StopToken token = new StopToken(cancellation);
        activeModelCall = cancellation;
        activeStop = token;
        return token;
    }

    void release(StopToken token) {
        if (activeModelCall == token.model) activeModelCall = null;
        if (activeStop == token) activeStop = null;
    }

    int nextVersionCode(ProjectIdentity project) {
        int stored = versions.getInt(versionKey(project), 1);
        int journal = journal(project).maximumVersionCode();
        int installed = 0;
        try {
            long installedVersion =
                    context.getPackageManager()
                            .getPackageInfo(project.packageName(), 0)
                            .getLongVersionCode();
            if (installedVersion < 0 || installedVersion >= Integer.MAX_VALUE)
                throw new IllegalStateException("No generated version available");
            installed = (int) installedVersion;
        } catch (PackageManager.NameNotFoundException missing) {
            // No installed generated app is visible to this controller.
        }
        return RunRecovery.nextVersionCode(stored, journal, installed);
    }

    private static String versionKey(ProjectIdentity project) {
        return LEGACY_PROJECT.equals(project.id())
                ? "next_version"
                : "next_version:" + project.id();
    }

    private void saveNextVersion(ProjectIdentity project, int completedVersion) {
        if (completedVersion < 1 || completedVersion == Integer.MAX_VALUE)
            throw new IllegalStateException("No generated version available");
        int next = Math.max(versions.getInt(versionKey(project), 1), completedVersion + 1);
        if (!versions.edit().putInt(versionKey(project), next).commit())
            throw new IllegalStateException("Could not save generated version");
    }

    private RunJournal journal(ProjectIdentity project) {
        return new RunJournal(context.getApplicationContext(), project.id());
    }

    /** Saves the edited source as a new revision and builds it. */
    void build(
            ProjectIdentity project,
            ProjectSourceStore projectSource,
            String originatingChatId,
            String edited,
            int expectedRevision) {
        String manualBuildId = "manual_" + UUID.randomUUID().toString().replace("-", "");
        StopToken stopToken = new StopToken(null);
        activeStop = stopToken;
        buildInProgress = true;
        running = project;
        listener.offer(null);
        listener.changed();
        listener.status("Saving immutable source snapshot and compiling in worker...");
        try {
            work.execute(
                    () -> {
                        try {
                            stopToken.ensureActive();
                            int version = nextVersionCode(project);
                            int sourceRevision =
                                    projectSource.writeSource(
                                            expectedRevision,
                                            edited,
                                            originatingChatId,
                                            manualBuildId,
                                            version,
                                            chatStore);
                            main.execute(() -> listener.saved(null, sourceRevision));
                            stopToken.ensureActive();
                            VerifiedArtifact artifact =
                                    new LocalBuildCoordinator(context, projectSource, project)
                                            .buildCommittedRevision(
                                                    sourceRevision, version, worker.get());
                            stopToken.ensureActive();
                            stopToken.completion.finish(
                                    () -> !stopToken.requested,
                                    sourceRevision,
                                    artifact,
                                    (revision, verified) -> {
                                        projectSource.pinManualBuildArtifact(
                                                revision, verified, chatStore);
                                        saveNextVersion(project, version);
                                        SignedArtifactReceipt.publish(
                                                context, project, revision, version, verified);
                                    },
                                    (next, revision, buildId, tokens) -> {});
                            main.execute(
                                    () -> {
                                        if (!stopToken.requested) {
                                            listener.offer(artifact);
                                            listener.status(READY);
                                        }
                                    });
                        } catch (Exception exception) {
                            Log.w("SmallwrightBuild", "Local build failed", exception);
                            main.execute(
                                    () ->
                                            listener.status(
                                                    stopToken.requested
                                                            ? "Local build stopped. Saved source remains; no new APK is ready."
                                                            : exception
                                                                            instanceof
                                                                            JavaCompileFailure
                                                                    ? "Java source did not compile. Saved source remains; edit it and Build again. No new APK is ready."
                                                                    : exception
                                                                                    instanceof
                                                                                    ArtifactValidator
                                                                                            .Refused
                                                                            ? "Local build failed: "
                                                                                    + exception
                                                                                            .getMessage()
                                                                                    + ". No new APK is ready."
                                                                            : "Local build failed. No new APK is ready."));
                        } finally {
                            main.execute(
                                    () -> {
                                        buildInProgress = false;
                                        if (activeStop == stopToken) activeStop = null;
                                        listener.changed();
                                    });
                        }
                    });
        } catch (RuntimeException scheduling) {
            buildInProgress = false;
            if (activeStop == stopToken) activeStop = null;
            listener.changed();
            listener.status("Could not start the local build. No new APK is ready.");
        }
    }

    /** Rebuilds the revision a run committed before the process ended. */
    void resumePendingBuild(
            ProjectIdentity project,
            ProjectSourceStore projectSource,
            IBuildService connectedWorker) {
        RunJournal.Snapshot pending = pendingRecovery;
        if (pending == null || connectedWorker == null || recoveryQueued) return;
        RunJournal journal = journal(project);
        StopToken stopToken = new StopToken(null, journal, pending.runId);
        activeStop = stopToken;
        recoveryQueued = true;
        buildInProgress = true;
        running = project;
        listener.changed();
        listener.status("Rebuilding saved revision " + pending.sourceRevision + " locally...");
        try {
            work.execute(
                    () -> {
                        try {
                            stopToken.ensureActive();
                            if (projectSource.revision() != pending.sourceRevision
                                    || pending.versionCode == null)
                                throw new IllegalStateException("Saved source revision changed");
                            VerifiedArtifact artifact =
                                    new LocalBuildCoordinator(context, projectSource, project)
                                            .buildRecoveredRevision(
                                                    pending.sourceRevision,
                                                    pending.versionCode,
                                                    pending.buildId,
                                                    connectedWorker,
                                                    () -> !stopToken.requested,
                                                    () ->
                                                            main.execute(
                                                                    () -> {
                                                                        if (!stopToken.requested)
                                                                            listener.status(
                                                                                    "Waiting for the compiler to finish its current build. Stop is available.");
                                                                    }));
                            stopToken.completion.finish(
                                    () -> !stopToken.requested,
                                    pending.sourceRevision,
                                    artifact,
                                    (revision, verified) -> {
                                        saveNextVersion(project, pending.versionCode);
                                        SignedArtifactReceipt.publish(
                                                context,
                                                project,
                                                revision,
                                                pending.versionCode,
                                                verified);
                                    },
                                    (next, revision, buildId, tokens) ->
                                            journal.advance(
                                                    pending.runId,
                                                    pending.sequence,
                                                    next,
                                                    revision,
                                                    buildId,
                                                    tokens));
                            main.execute(
                                    () -> {
                                        if (!stopToken.requested) {
                                            pendingRecovery = null;
                                            listener.offer(artifact);
                                            listener.recoveryNote(null);
                                            listener.status(
                                                    "Saved revision rebuilt and verified: "
                                                            + artifact.sha256()
                                                            + ". Tap "
                                                            + Labels.INSTALL
                                                            + " to test it.");
                                        }
                                    });
                        } catch (Exception failure) {
                            try {
                                RunJournal.Snapshot current = journal.load(pending.runId);
                                if (!(failure instanceof WorkerBusyFailure)
                                        && !stopToken.completion.isFinished()
                                        && current.sequence == pending.sequence
                                        && current.state == RunJournal.State.BUILDING)
                                    journal.advance(
                                            pending.runId,
                                            current.sequence,
                                            RunJournal.State.FAILED,
                                            current.sourceRevision,
                                            null,
                                            null);
                            } catch (Exception journalFailure) {
                                failure.addSuppressed(journalFailure);
                            }
                            main.execute(
                                    () -> {
                                        boolean workerBusy =
                                                failure instanceof WorkerBusyFailure
                                                        && !stopToken.requested;
                                        pendingRecovery = workerBusy ? pending : null;
                                        String note =
                                                workerBusy
                                                        ? "The compiler is still busy. Retry the saved build, or Stop to keep the source for later."
                                                        : stopToken.requested
                                                                ? "Saved build stopped. Source remains; no APK is ready."
                                                                : stopToken.completion.isFinished()
                                                                        ? "Signed APK saved, but run history could not be completed. Restart the controller to recover the saved build."
                                                                        : "Saved source remains, but its interrupted build could not "
                                                                                + "be verified. Review the source and start a new build.";
                                        listener.recoveryNote(note);
                                        listener.status(note);
                                    });
                        } finally {
                            main.execute(
                                    () -> {
                                        buildInProgress = false;
                                        recoveryQueued = false;
                                        if (stopToken.requested && pendingRecovery == pending)
                                            pendingRecovery = null;
                                        if (activeStop == stopToken
                                                && (pendingRecovery == null || stopToken.requested))
                                            activeStop = null;
                                        listener.changed();
                                    });
                        }
                    });
        } catch (RuntimeException scheduling) {
            buildInProgress = false;
            recoveryQueued = false;
            if (activeStop == stopToken) activeStop = null;
            listener.changed();
            listener.status("Could not start recovery build. Saved source remains.");
        }
    }

    /**
     * The one send: a run that works in steps until the model answers. The tap approves the whole
     * run with this message, this model, this thinking level and the context prepared now. Throws
     * when the context cannot be prepared; nothing was sent then.
     */
    void send(
            ProjectIdentity project,
            ProjectSourceStore projectSource,
            ContextPreviewGate gate,
            String chatId,
            String request,
            String visibleRequest,
            String edited,
            int revision,
            ModelChoice model,
            ReasoningLevel reasoning,
            VerifiedArtifact offered,
            AttachedImages images)
            throws Exception {
        String refused = images.refusal(model);
        if (refused == null) refused = RunSettings.load(context).refusal(model);
        if (refused != null) throw new RunSkills.Refused(refused);
        if (!images.isEmpty()
                && chatId != null
                && chatStore.imageCount(project.id(), chatId) + images.count()
                        > ChatStore.MAX_CHAT_IMAGES)
            throw new RunSkills.Refused(
                    "This chat keeps as many pictures as it can. Start a new chat to send more."
                            + " Nothing was sent.");
        if (!isBusy() && !InterruptedRuns.writeUnsaved(journal(project), chatStore, project.id()))
            throw new RunSkills.Refused(GenerationFailure.CHAT_UNSAVED);
        ContextPreviewGate.Preview preview = gate.previewChat(chatId, request, revision, images);
        IBuildService approvedWorker = worker.get();
        if (isBusy() || approvedWorker == null) return;
        RunJournal runJournal = null;
        String runId = null;
        try {
            if (request.trim().isEmpty() || !edited.equals(projectSource.readSource()))
                throw new IllegalStateException();
            if (chatId == null) throw new IllegalStateException("Select a chat first");
            if (!chatId.equals(preview.chatId())) throw new IllegalStateException("Chat changed");
            String approved = gate.approve(preview, request, revision, images);
            approvedContextSha256 =
                    sha256Hex(approved.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            runJournal = journal(project);
            runId = "run_" + UUID.randomUUID().toString().replace("-", "");
            List<Step> thinking = List.of(new Step(StepKind.THINKING, "Thinking", ""));
            runJournal.create(
                    preview.revision(),
                    new RunJournal.Progress(
                            runId,
                            chatId,
                            visibleRequest,
                            chatStore.listMessages(project.id(), chatId).size(),
                            InterruptedRuns.steps(thinking)));
            RunJournal startedJournal = runJournal;
            String startedRunId = runId;
            ModelClient.Cancellation cancellation = new ModelClient.Cancellation();
            StopToken stopToken = new StopToken(cancellation, startedJournal, startedRunId);
            int[] buildVersion = {0};
            StepRun.Journal attempt =
                    new StepRun.Journal() {
                        private RunJournal.Snapshot snapshot = startedJournal.load(startedRunId);

                        @Override
                        public void advance(
                                RunJournal.State next,
                                int revision,
                                String buildId,
                                Integer reportedTokens) {
                            synchronized (stopToken.completion) {
                                if (next == RunJournal.State.CANCELLED) {
                                    RunJournal.Snapshot latest = startedJournal.load(startedRunId);
                                    if (latest.state == RunJournal.State.CANCELLED) {
                                        snapshot = latest;
                                        return;
                                    }
                                }
                                if (next == RunJournal.State.BUILDING && stopToken.requested)
                                    throw new IllegalStateException("Run stopped before a build");
                                if (next == RunJournal.State.READY_TO_TEST && stopToken.requested)
                                    throw new IllegalStateException(
                                            "Run stopped before artifact publication");
                                if (next == RunJournal.State.BUILDING)
                                    buildVersion[0] = nextVersionCode(project);
                                snapshot =
                                        startedJournal.advance(
                                                startedRunId,
                                                snapshot.sequence,
                                                next,
                                                revision,
                                                buildId,
                                                reportedTokens,
                                                next == RunJournal.State.BUILDING
                                                        ? buildVersion[0]
                                                        : null);
                            }
                        }
                    };
            activeModelCall = cancellation;
            activeStop = stopToken;
            generationInProgress = true;
            running = project;
            listener.offer(null);
            listener.changed();
            liveSteps = new LiveRun(chatId, visibleRequest, thinking);
            listener.started(liveSteps);
            listener.status("");
            work.execute(
                    () ->
                            runSteps(
                                    model,
                                    reasoning,
                                    preview,
                                    approved,
                                    approvedWorker,
                                    cancellation,
                                    attempt,
                                    buildVersion,
                                    stopToken,
                                    chatId,
                                    visibleRequest,
                                    project,
                                    projectSource,
                                    offered));
            notices.started(startedRunId, project.id(), project.title(), chatId);
        } catch (Exception failure) {
            if (runJournal != null && runId != null) {
                try {
                    RunJournal.Snapshot pending = runJournal.load(runId);
                    if (pending.state == RunJournal.State.DRAFT)
                        runJournal.advance(
                                runId,
                                pending.sequence,
                                RunJournal.State.FAILED,
                                pending.sourceRevision,
                                null,
                                null);
                    runJournal.saved(runId);
                } catch (Exception journalFailure) {
                    failure.addSuppressed(journalFailure);
                }
            }
            generationInProgress = false;
            activeModelCall = null;
            activeStop = null;
            listener.offer(offered);
            showLive(null);
            listener.changed();
            listener.status("Could not start the request. Check the app's code and try again.");
        }
    }

    private static String sha256Hex(byte[] bytes) throws java.security.GeneralSecurityException {
        StringBuilder hex = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            hex.append(Character.forDigit((value >> 4) & 15, 16))
                    .append(Character.forDigit(value & 15, 16));
        return hex.toString();
    }

    private void runSteps(
            ModelChoice model,
            ReasoningLevel reasoning,
            ContextPreviewGate.Preview preview,
            String approved,
            IBuildService approvedWorker,
            ModelClient.Cancellation cancellation,
            StepRun.Journal attempt,
            int[] buildVersion,
            StopToken stopToken,
            String chatId,
            String visibleRequest,
            ProjectIdentity project,
            ProjectSourceStore projectSource,
            VerifiedArtifact priorArtifact) {
        GenerationFailure.Stage[] stage = {GenerationFailure.Stage.START};
        String[] providerFailure = {null};
        RunSteps live =
                new RunSteps(
                        chatId,
                        visibleRequest,
                        "Thinking",
                        snapshot -> {
                            remember(stopToken, snapshot);
                            main.execute(() -> showLive(snapshot));
                        },
                        snapshot ->
                                main.execute(
                                        () -> {
                                            liveSteps = snapshot;
                                            listener.steps(snapshot);
                                        }));
        VerifiedArtifact[] latestBuilt = {null};
        RunQuestion question = new RunQuestion(live, () -> !stopToken.requested);
        asking = question;
        StepRun stepRun =
                new StepRun(
                        projectSource,
                        stopToken.completion,
                        new LibraryTools(
                                LibraryTools.repositories(context),
                                RunSettings.asksBeforeLibraries(context)
                                        ? question
                                        : library -> true,
                                platformFiles::verifiedJar),
                        RunSettings.load(context),
                        preview.skills(),
                        new SkillAdding(
                                new GitHubSkills(GitHubSkills.PUBLIC),
                                RunSettings.asksBeforeSkills(context) ? question::asks : null,
                                project.title()),
                        new AppIcons(LauncherIcon::square, LauncherIcon::picture),
                        RequestRetry.standard(model.provider));
        if (AppLinkSources.applies(project.packageName()))
            stepRun.appLink(
                    new AppLink(
                            new AppLinkAndroid(context, project),
                            new AppCrashReports(project.directory()),
                            project.packageName(),
                            model.readsImages,
                            model.shortName()));
        byte[] key = null;
        try {
            stage[0] = GenerationFailure.Stage.KEY;
            key =
                    (model.provider.isOpenRouter()
                                    ? CredentialStore.openRouter(context)
                                    : CredentialStore.server(context))
                            .load();
            if (key == null) throw new IOException("No saved model key");
            byte[] credential = key;
            ModelClient client = new ModelClient(model, reasoning);
            ImageClient images = new ImageClient();
            stage[0] = GenerationFailure.Stage.MODEL;
            StepRun.Model requests =
                    new StepRun.Model() {
                        @Override
                        public ModelClient.StepReply next(JsonArray messages, int promptTokens)
                                throws IOException {
                            return send(messages, promptTokens, false);
                        }

                        @Override
                        public ModelClient.StepReply summarize(JsonArray messages, int promptTokens)
                                throws IOException {
                            return send(messages, promptTokens, true);
                        }

                        @Override
                        public int contextTokens() {
                            return model.contextTokens;
                        }

                        @Override
                        public ImageClient.Image image(String prompt, ImageClient.Shape shape)
                                throws IOException {
                            if (!model.provider.isOpenRouter())
                                throw new IOException(ImageClient.NEEDS_OPENROUTER);
                            return images.make(credential, prompt, shape, cancellation);
                        }

                        @Override
                        public WebPages.Page webPage(String url) throws IOException {
                            return WebPages.PUBLIC.read(url, cancellation);
                        }

                        private ModelClient.StepReply send(
                                JsonArray messages, int promptTokens, boolean summary)
                                throws IOException {
                            stage[0] = GenerationFailure.Stage.MODEL;
                            providerFailure[0] = null;
                            try {
                                return summary
                                        ? client.completeSummary(
                                                credential,
                                                messages,
                                                promptTokens,
                                                cancellation,
                                                live::thought)
                                        : client.completeStep(
                                                credential,
                                                messages,
                                                promptTokens,
                                                cancellation,
                                                live::thought);
                            } catch (IOException modelError) {
                                providerFailure[0] =
                                        GenerationFailure.safeProviderMessage(modelError);
                                throw modelError;
                            }
                        }
                    };
            StepRun.Build localBuild =
                    (revision, buildId) -> {
                        stage[0] = GenerationFailure.Stage.BUILD;
                        if (!platformFiles.isPresent()) throw new StepRun.PlatformFilesMissing();
                        int version = buildVersion[0];
                        VerifiedArtifact built =
                                new LocalBuildCoordinator(context, projectSource, project)
                                        .buildCommittedRevision(
                                                revision, version, buildId, approvedWorker);
                        built.verifyUnchanged();
                        saveNextVersion(project, version);
                        SignedArtifactReceipt.publish(context, project, revision, version, built);
                        latestBuilt[0] = built;
                        return built;
                    };
            BooleanSupplier active =
                    () -> !stopToken.requested && !Thread.currentThread().isInterrupted();
            StepRun.Outcome outcome =
                    stepRun.run(
                            approved,
                            preview.images(),
                            preview.revision(),
                            active,
                            requests,
                            localBuild,
                            attempt,
                            live);
            if (outcome.platformNeeded) main.execute(() -> listener.platformNeeded());
            boolean chatSaved =
                    finishRun(
                            project,
                            projectSource,
                            preview.revision(),
                            chatId,
                            outcome.revision,
                            visibleRequest,
                            preview.images(),
                            live.saved(),
                            outcome.round == null ? "assistant" : ChatStore.QUESTION,
                            outcome.round == null ? outcome.reply : outcome.round.saved(),
                            outcome.artifact == null ? null : READY);
            if (chatSaved) forget(stopToken);
            VerifiedArtifact offer = outcome.artifact != null ? outcome.artifact : priorArtifact;
            main.execute(
                    () -> {
                        if (stopToken.requested) {
                            notices.stopped();
                            return;
                        }
                        listener.offer(offer);
                        listener.reportUsed();
                        listener.changed();
                        listener.status("");
                        if (outcome.artifact != null
                                && !outcome.limitReached
                                && outcome.round == null) notices.built();
                        else notices.answered(outcome.reply);
                    });
        } catch (Exception failure) {
            int ended = preview.revision();
            try {
                ended = projectSource.revision();
            } catch (IOException ignored) {
                /* The run's own revision stays the honest fallback. */
            }
            String message =
                    GenerationFailure.runEnded(
                            stopToken.requested,
                            stopToken.completion.isFinished(),
                            failure,
                            stage[0],
                            providerFailure[0]);
            live.stopUnfinished(stopToken.requested ? "Stopped" : "Did not finish");
            boolean chatSaved =
                    finishRun(
                            project,
                            projectSource,
                            preview.revision(),
                            chatId,
                            ended,
                            visibleRequest,
                            preview.images(),
                            live.saved(),
                            "status",
                            message,
                            null);
            if (chatSaved) forget(stopToken);
            VerifiedArtifact offer = latestBuilt[0] != null ? latestBuilt[0] : priorArtifact;
            main.execute(
                    () -> {
                        listener.offer(offer);
                        listener.status(
                                stopToken.requested ? GenerationFailure.RUN_STOPPED : message);
                        if (stopToken.requested) notices.stopped();
                        else notices.failed(message);
                    });
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            Log.i(
                    "SmallwrightRun",
                    "Run ended after "
                            + stepRun.requests()
                            + " model requests; reported cost "
                            + stepRun.costUsd().toPlainString()
                            + " USD; "
                            + stepRun.tokenCounts());
            if (activeModelCall == cancellation) activeModelCall = null;
            if (asking == question) asking = null;
            String latestSource = null;
            Integer latestRevision = null;
            try {
                latestSource = projectSource.readSource();
                latestRevision = projectSource.revision();
            } catch (Exception ignored) {
                /* Preserve the editor if the store is temporarily unavailable. */
            }
            String displayedSource = latestSource;
            Integer displayedRevision = latestRevision;
            main.execute(
                    () -> {
                        generationInProgress = false;
                        showLive(null);
                        if (activeStop == stopToken) activeStop = null;
                        listener.saved(displayedSource, displayedRevision);
                        listener.changed();
                    });
        }
    }

    /** Keeps the steps shown so far, so the chat can tell them if the process ends now. */
    private static void remember(StopToken run, LiveRun live) {
        try {
            run.journal.showed(run.runId, InterruptedRuns.steps(live.getSteps()));
        } catch (RuntimeException unrecorded) {
            Log.w("SmallwrightRun", "Could not record the run's steps", unrecorded);
        }
    }

    /** The run's chat holds its messages now. */
    private static void forget(StopToken run) {
        try {
            run.journal.saved(run.runId);
        } catch (RuntimeException unrecorded) {
            Log.w("SmallwrightRun", "Could not record that the run's chat is saved", unrecorded);
        }
    }

    /** Lets Undo find this run's change; without it the change just cannot be undone. */
    private void rememberChange(
            ProjectSourceStore projectSource,
            int startRevision,
            int revision,
            String chatId,
            String messageId,
            String request) {
        try {
            new BuilderChanges(projectSource, chatStore)
                    .recordRun(
                            startRevision,
                            revision,
                            chatId,
                            messageId,
                            request,
                            System.currentTimeMillis());
        } catch (IOException | RuntimeException unrecorded) {
            Log.w("SmallwrightRun", "Could not record the run's change for Undo", unrecorded);
        }
    }

    /**
     * Saves a run's messages, then swaps the live view for the saved chat in one screen update.
     * Null steps, a null closing role or a null status leave that message out. Returns false when
     * the chat could not be saved, so the run's progress stays for the next time the project opens.
     */
    private boolean finishRun(
            ProjectIdentity project,
            ProjectSourceStore projectSource,
            int startRevision,
            String chatId,
            int revision,
            String user,
            AttachedImages userImages,
            String steps,
            String closingRole,
            String closing,
            String status) {
        String closedId = null;
        boolean saved = true;
        try {
            if (user != null)
                chatStore.appendMessage(
                        project.id(), chatId, "user", user, revision, userImages.bytes());
            if (steps != null)
                chatStore.appendMessage(project.id(), chatId, "tool", steps, revision);
            if (closingRole != null)
                closedId =
                        chatStore.appendMessage(
                                        project.id(), chatId, closingRole, closing, revision)
                                .id;
            if (status != null)
                chatStore.appendMessage(project.id(), chatId, "status", status, revision);
        } catch (IOException | IllegalArgumentException ignored) {
            saved = false;
            main.execute(
                    () ->
                            listener.status(
                                    "Could not save the latest chat status. "
                                            + "The project run history remains available."));
        }
        rememberChange(projectSource, startRevision, revision, chatId, closedId, user);
        main.execute(() -> listener.chatSaved(chatId));
        return saved;
    }
}
