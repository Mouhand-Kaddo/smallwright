package dev.smallwright.controller;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import dev.smallwright.security.CredentialStoreProbe;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Uses real compiled APK manifests and Android's PackageManager archive parser. */
public final class ArtifactPolicyInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments != null && arguments.getString("dialogResourceFreshPhase") != null) {
                try {
                    if (arguments.getString("dialogResourcePhase") != null)
                        throw new AssertionError("One independent dialog route required");
                    result.putString(
                            "stream",
                            DialogResourceProbe.runFresh(
                                    this,
                                    arguments.getString("dialogResourceFreshPhase"),
                                    arguments.getString("observationId"),
                                    Boolean.parseBoolean(
                                            arguments.getString(
                                                    "approveLocalDialogFixture", "false")),
                                    arguments.getString("expectedCompile"),
                                    arguments.getString("expectedWorkerSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Dialog native2 FAILED stage="
                                    + DialogResourceProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("dialogResourcePhase") != null) {
                try {
                    result.putString(
                            "stream",
                            DialogResourceProbe.run(
                                    this,
                                    arguments.getString("dialogResourcePhase"),
                                    arguments.getString("observationId"),
                                    Boolean.parseBoolean(
                                            arguments.getString(
                                                    "approveLocalDialogFixture", "false")),
                                    arguments.getString("expectedCompile"),
                                    arguments.getString("expectedWorkerSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Dialog resource FAILED stage="
                                    + DialogResourceProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("interruptedDelete") != null) {
                try {
                    result.putString("stream", InterruptedDeleteProbe.run(this));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Interrupted delete FAILED "
                                    + failure.getClass().getSimpleName()
                                    + ": "
                                    + failure.getMessage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33SuiteReset") != null) {
                try {
                    result.putString(
                            "stream",
                            A33CounterFeedbackProbe.runReset(
                                    this,
                                    arguments.getString("a33SuiteReset"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 suite reset FAILED stage="
                                    + A33CounterFeedbackProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33CounterLayoutFeedback") != null) {
                try {
                    result.putString(
                            "stream",
                            A33CounterFeedbackProbe.runLayout(
                                    this,
                                    arguments.getString("a33CounterLayoutFeedback"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 counter feedback FAILED stage="
                                    + A33CounterFeedbackProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33CounterFeedback") != null) {
                try {
                    result.putString(
                            "stream",
                            A33CounterFeedbackProbe.run(
                                    this,
                                    arguments.getString("a33CounterFeedback"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 counter feedback FAILED stage="
                                    + A33CounterFeedbackProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33SuiteCounter") != null) {
                try {
                    result.putString(
                            "stream",
                            A33SuiteCounterProbe.run(
                                    this,
                                    arguments.getString("a33SuiteCounter"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 suite counter FAILED stage="
                                    + A33SuiteCounterProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33Counter05") != null) {
                try {
                    result.putString(
                            "stream",
                            A33Counter05Probe.run(
                                    this,
                                    arguments.getString("a33Counter05"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 counter05 FAILED stage="
                                    + A33Counter05Probe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("foreignUi") != null) {
                try {
                    result.putString(
                            "stream",
                            ForeignUiDriver.run(
                                            this,
                                            arguments.getString("foreignUi"),
                                            arguments.getString("target", ""),
                                            arguments.getString("value", ""))
                                    + "\n");
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString("stream", "Foreign UI FAILED " + failure.getMessage() + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33Suite") != null) {
                try {
                    result.putString(
                            "stream",
                            A33SuiteProbe.run(
                                    this,
                                    arguments.getString("a33Suite"),
                                    arguments.getString("promptId"),
                                    arguments.getString("runId"),
                                    arguments.getString("continueRun"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256"),
                                    arguments.getString("checklistRecord"),
                                    arguments.getString("observationB64") == null
                                            ? null
                                            : new String(
                                                    android.util.Base64.decode(
                                                            arguments.getString("observationB64"),
                                                            android.util.Base64.DEFAULT),
                                                    java.nio.charset.StandardCharsets.UTF_8),
                                    arguments.getString("baselineFixture"),
                                    arguments.getString("approvedModel"),
                                    arguments.getString("productionRun")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 suite FAILED stage="
                                    + A33SuiteProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + " frames="
                                    + projectFrames(failure)
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33Counter04") != null) {
                try {
                    result.putString(
                            "stream",
                            A33Counter04Probe.run(
                                    this,
                                    arguments.getString("a33Counter04"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 counter04 FAILED stage="
                                    + A33Counter04Probe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + " frames="
                                    + projectFrames(failure)
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("a33Counter03") != null) {
                try {
                    result.putString(
                            "stream",
                            A33Counter03Probe.run(
                                    this,
                                    arguments.getString("a33Counter03"),
                                    Boolean.parseBoolean(
                                            arguments.getString("freeOnlyApproved", "false")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "A33 counter03 FAILED stage="
                                    + A33Counter03Probe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("ownPackageStorage") != null) {
                try {
                    result.putString(
                            "stream",
                            dev.smallwright.testing.OwnPackageStorage.run(
                                    this,
                                    arguments.getString("observationId"),
                                    arguments.getString("storagePhase", "read")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Own package storage FAILED stage="
                                    + dev.smallwright.testing.OwnPackageStorage.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("testHandoff") != null) {
                try {
                    result.putString(
                            "stream",
                            TestHandoffProbe.run(
                                    this,
                                    arguments.getString("testHandoff"),
                                    "true".equals(arguments.getString("testHandoffApproved"))));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Test handoff FAILED stage="
                                    + TestHandoffProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("detachedBuild") != null) {
                try {
                    String phase = arguments.getString("detachedBuild");
                    result.putString(
                            "stream",
                            phase.endsWith("2")
                                    ? DetachedBuildProbe.runSecond(this, phase)
                                    : DetachedBuildProbe.run(this, phase));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Detached build FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + DetachedBuildProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("sustainedResourcesOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            BuildResourcesProbe.run(
                                    this,
                                    arguments.getString("sustainedResourcesOnly"),
                                    arguments.getString("observationId"),
                                    true,
                                    true));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Sustained resources FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + BuildResourcesProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("providerCancellation") != null) {
                try {
                    result.putString(
                            "stream",
                            ProviderCancellationProbe.run(
                                    this,
                                    arguments.getString("providerCancellation"),
                                    "true".equals(arguments.getString("freeOnlyApproved")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Provider cancellation FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + ProviderCancellationProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("kernelResourcesOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            BuildResourcesProbe.run(
                                    this,
                                    arguments.getString("kernelResourcesOnly"),
                                    arguments.getString("observationId"),
                                    true,
                                    true,
                                    true,
                                    arguments.getString("kernelResourcesCase")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Kernel resources FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + BuildResourcesProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("workspaceResourcesOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            BuildResourcesProbe.run(
                                    this,
                                    arguments.getString("workspaceResourcesOnly"),
                                    arguments.getString("observationId"),
                                    true));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Workspace resources FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + BuildResourcesProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("buildResourcesOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            BuildResourcesProbe.run(
                                    this,
                                    arguments.getString("buildResourcesOnly"),
                                    arguments.getString("observationId")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Build resources FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + BuildResourcesProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("ecjStopOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            EcjPhaseStopProbe.run(
                                    this,
                                    arguments.getString("ecjStopOnly"),
                                    arguments.getString("observationId")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "ECJ Stop FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + EcjPhaseStopProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("compilerMeasurementOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            CompilerMeasurementProbe.run(
                                    this, arguments.getString("compilerMeasurementOnly")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Compiler measurement FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + CompilerMeasurementProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("screenLockRecoveryOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            ScreenLockRecoveryProbe.run(
                                    this, arguments.getString("screenLockRecoveryOnly")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Screen lock recovery FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + ScreenLockRecoveryProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("workerDeathRecoveryOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            WorkerDeathRecoveryProbe.run(
                                    this, arguments.getString("workerDeathRecoveryOnly")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Worker death recovery FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + WorkerDeathRecoveryProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("receiptWriteRecoveryOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            ReceiptWriteRecoveryProbe.run(
                                    this, arguments.getString("receiptWriteRecoveryOnly")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Receipt recovery FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + ReceiptWriteRecoveryProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("busyRecoveryOnly") != null) {
                try {
                    result.putString(
                            "stream",
                            BusyRecoveryProbe.run(this, arguments.getString("busyRecoveryOnly")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Busy recovery FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + BusyRecoveryProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("activeInterruption") != null) {
                try {
                    result.putString(
                            "stream",
                            ActiveBuildInterruptionProbe.run(
                                    this, arguments.getString("activeInterruption")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Active interruption FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + ActiveBuildInterruptionProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("activeWorkerStop") != null) {
                try {
                    result.putString(
                            "stream",
                            ActiveWorkerStopProbe.run(
                                    this, arguments.getString("activeWorkerStop")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Active worker Stop FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + " stage="
                                    + ActiveWorkerStopProbe.failureStage()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("diagnosticBinder") != null) {
                result.putString(
                        "stream",
                        WorkerBinderFailureProbe.runDiagnostics(
                                getTargetContext(),
                                "lifecycle".equals(arguments.getString("diagnosticBinder"))));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("phoneAlphaReset") != null) {
                try {
                    result.putString(
                            "stream",
                            PhoneAlphaResetProbe.run(
                                            this,
                                            arguments.getString("phoneAlphaReset"),
                                            "true".equals(arguments.getString("freeOnlyApproved")),
                                            arguments.getString("approvedContextSha256"))
                                    + "\n");
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Reset alpha FAILED: " + failure.getClass().getSimpleName() + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("phoneOfflineBuild") != null) {
                try {
                    result.putString(
                            "stream",
                            PhoneOfflineBuildProbe.run(
                                    this,
                                    arguments.getString("phoneOfflineBuild"),
                                    "true".equals(arguments.getString("offlineConfirmed"))));
                    finish(Activity.RESULT_OK, result);
                } catch (PhoneOfflineBuildProbe.OfflineGuardFailure refused) {
                    result.putString(
                            "stream", "Phone offline guard FAILED: " + refused.getMessage() + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Phone offline probe FAILED: "
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("persistentChatsAcceptanceOnly"))) {
                try {
                    result.putString(
                            "stream",
                            PersistentChatsAcceptanceProbe.run(
                                    this,
                                    arguments.getString("observationId"),
                                    "true"
                                            .equals(
                                                    arguments.getString(
                                                            "approveLocalChatAcceptance"))));
                    finish(Activity.RESULT_OK, result);
                } catch (Exception | AssertionError failure) {
                    result.putString(
                            "stream",
                            "Persistent chats acceptance FAILED stage="
                                    + PersistentChatsAcceptanceProbe.failureStage()
                                    + " type="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("liveLayoutPhase") != null) {
                try {
                    result.putString(
                            "stream",
                            LiveLayoutUpdateProbe.run(
                                    this,
                                    arguments.getString("liveLayoutPhase"),
                                    "true".equals(arguments.getString("freeOnlyApproved")),
                                    arguments.getString("approvedContextSha256")));
                    finish(Activity.RESULT_OK, result);
                } catch (Throwable failure) {
                    result.putString(
                            "stream",
                            "Live layout FAILED stage="
                                    + LiveLayoutUpdateProbe.failureStage()
                                    + " category="
                                    + failure.getClass().getSimpleName()
                                    + "\n");
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("manualBuildOriginPhase") != null) {
                try {
                    result.putString(
                            "stream",
                            ManualBuildOriginProbe.run(
                                    this,
                                    arguments.getString("observationId"),
                                    arguments.getString("manualBuildOriginPhase"),
                                    "true"
                                            .equals(
                                                    arguments.getString(
                                                            "approveLocalManualOrigin"))));
                    finish(Activity.RESULT_OK, result);
                } catch (Exception | AssertionError failure) {
                    result.putString(
                            "stream",
                            "Manual Build origin FAILED stage="
                                    + ManualBuildOriginProbe.failureStage()
                                    + " type="
                                    + failure.getClass().getSimpleName()
                                    + "\n"
                                    + ManualBuildOriginProbe.failureEvidence());
                    finish(Activity.RESULT_CANCELED, result);
                }
                return;
            }
            if (arguments != null && arguments.getString("phoneConversation") != null) {
                result.putString(
                        "stream",
                        PhoneConversationProbe.run(
                                this,
                                arguments.getString("phoneConversation"),
                                "true".equals(arguments.getString("approveFreeOnly"))));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("conversationLayoutOnly"))) {
                result.putString("stream", ConversationLayoutProbe.run(this));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("projectBindingResponsiveOnly"))) {
                result.putString("stream", ProjectBindingResponsivenessProbe.run(this));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("workerSnapshotDeadlineOnly"))) {
                result.putString(
                        "stream", WorkerBinderFailureProbe.runHeldOpen(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("visibleGeneratedPackage") != null) {
                String name = arguments.getString("visibleGeneratedPackage");
                if (!name.matches("dev\\.smallwright\\.generated\\.app[0-9a-f]{32}"))
                    throw new AssertionError("Invalid visibility probe package");
                android.content.pm.PackageInfo installed =
                        getTargetContext()
                                .getPackageManager()
                                .getPackageInfo(
                                        name,
                                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (installed.signingInfo == null || installed.getLongVersionCode() < 1)
                    throw new AssertionError("Installed generated project is not visible");
                result.putString(
                        "stream", "Installed generated project is visible to controller\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("chatUiOnly"))) {
                ChatUiProbe.run(this);
                result.putString("stream", "Project chats: create and reopen passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("projectNavigationOnly"))) {
                ProjectNavigationProbe.run(this);
                result.putString(
                        "stream", "Project navigation: create, switch, chat, reopen passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("projectInstallOnly"))) {
                result.putString("stream", ProjectInstallProbe.run(this));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("noteOnly"))) {
                result.putString("stream", ProjectInstallProbe.runNote(this));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("projectRegistryOnly"))) {
                ProjectRegistryProbe.run(getTargetContext());
                result.putString(
                        "stream", "Project registry: Android metadata and directory sync passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("statusVisibleOnly"))) {
                StatusVisibilityProbe.run(this);
                result.putString("stream", "Generation status remains visible beside Generate\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("readyControlsOnly"))) {
                StatusVisibilityProbe.readyControls(this);
                result.putString(
                        "stream", "Build, Generate, and New chat are enabled after startup\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("stopBeforeBuildOnly"))) {
                StopUiProbe.stopBeforeQueuedBuild(this);
                result.putString(
                        "stream", "Stop: queued build left source untouched and offered no APK\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("backgroundKeepsBuildOnly"))) {
                StopUiProbe.backgroundKeepsQueuedBuild(this);
                result.putString(
                        "stream",
                        "Background: queued build kept going; Stop then left source unchanged\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("screenshotPickerOnly"))) {
                ScreenshotPickerProbe.run(getTargetContext());
                result.putString(
                        "stream", "Screenshot picker: intent and bounded image intake passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "prepare".equals(arguments.getString("recoveryRestart"))) {
                result.putString(
                        "stream",
                        "Recovery restart ID: "
                                + RecoveryRestartProbe.prepare(getTargetContext())
                                + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "verify".equals(arguments.getString("recoveryRestart"))) {
                result.putString(
                        "stream",
                        RecoveryRestartProbe.verify(
                                        getTargetContext(), arguments.getString("recoveryProbeId"))
                                + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("feedbackAvailableOnly"))) {
                StatusVisibilityProbe.feedbackAvailableAfterRestart(this);
                result.putString("stream", "Feedback action available after controller restart\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("runJournalOnly"))) {
                RunJournalProbe.run(getTargetContext());
                result.putString(
                        "stream", "Run journal: SQLite persistence and corruption checks passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "prepare".equals(arguments.getString("receiptRestart"))) {
                result.putString("stream", ReceiptRestartProbe.prepare(getTargetContext()) + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "verify".equals(arguments.getString("receiptRestart"))) {
                result.putString("stream", ReceiptRestartProbe.verifyAndTamper(this) + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "verifyTampered".equals(arguments.getString("receiptRestart"))) {
                result.putString(
                        "stream", ReceiptRestartProbe.verifyTamperedAndCleanup(this) + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("secondProjectBuildOnly"))) {
                result.putString("stream", SecondProjectBuildProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("platformBuild") != null) {
                result.putString(
                        "stream",
                        PlatformBuildProbe.run(this, arguments.getString("platformBuild")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("manifestBuild") != null) {
                result.putString(
                        "stream",
                        ManifestBuildProbe.run(
                                getTargetContext(), arguments.getString("manifestBuild")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("runInterruption") != null) {
                result.putString(
                        "stream",
                        RunInterruptionProbe.run(
                                this,
                                arguments.getString("runInterruption"),
                                arguments.getString("selectProject")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("libraryBuild") != null) {
                result.putString(
                        "stream",
                        LibraryBuildProbe.run(
                                getTargetContext(), arguments.getString("libraryBuild")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("nativeLibraryBuild") != null) {
                result.putString("stream", NativeLibraryBuildProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("compactionRun") != null) {
                result.putString(
                        "stream",
                        CompactionRunProbe.run(
                                this,
                                arguments.getString("compactionRun"),
                                Integer.parseInt(arguments.getString("contextTokens")),
                                new String(
                                        android.util.Base64.decode(
                                                arguments.getString("messageB64"),
                                                android.util.Base64.DEFAULT),
                                        StandardCharsets.UTF_8)));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("appIconBuild") != null) {
                result.putString(
                        "stream", AppIconBuildProbe.run(this, arguments.getString("appIconBuild")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("manyFilesBuild") != null) {
                result.putString(
                        "stream",
                        ManyFilesBuildProbe.run(
                                getTargetContext(), arguments.getString("manyFilesBuild")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("noteRuntimeOnly"))) {
                result.putString("stream", NoteRuntimeProbe.build(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("localityProofOnly"))) {
                result.putString("stream", LocalityProofProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("workerBinderFailureOnly"))) {
                result.putString("stream", WorkerBinderFailureProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("workerBinderOverlapOnly"))) {
                result.putString(
                        "stream", WorkerBinderFailureProbe.runConcurrent(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("signingLineageOnly"))) {
                result.putString("stream", SigningLineageProbe.run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("rotationApksOnly"))) {
                result.putString("stream", SigningRotationApkProbe.prepare(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("rotationPortableOnly"))) {
                result.putString(
                        "stream", SigningRotationApkProbe.preparePortable(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("rotationProductOnly"))) {
                result.putString(
                        "stream", SigningRotationApkProbe.prepareProduct(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("rotationInstalledProductV1Only"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.prepareInstalledProductV1(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("rotationInstalledProductV2Only"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.prepareInstalledProductV2(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true"
                            .equals(
                                    arguments.getString(
                                            "rotationInstalledProductV2ForRecoveryOnly"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.prepareInstalledProductV2ForRecovery(
                                getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("recoveryInstalledFixture"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.exportInstalledRecovery(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("rotationProductPreflightOnly"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.rejectUnsafeProductActivation(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("rotationProductSigningOnly"))) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.rejectUnsafeProductSigning(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("recoveryEnvelopeOnly"))) {
                result.putString("stream", RecoveryEnvelopeProbe.run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("portableSignerOnly"))) {
                result.putString("stream", PortableSigningIdentityProbe.run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null
                    && "true".equals(arguments.getString("portableSignerStoreOnly"))) {
                result.putString("stream", PortableSignerStoreProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("recoveryBundleOnly"))) {
                result.putString("stream", RecoveryBundleProbe.run());
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("recoveryExportOnly"))) {
                result.putString("stream", RecoveryExportProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "prepare".equals(arguments.getString("recoveryAdoption"))) {
                result.putString(
                        "stream",
                        "Recovery adoption ID: "
                                + RecoveryAdoptionProbe.prepare(getTargetContext())
                                + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "verify".equals(arguments.getString("recoveryAdoption"))) {
                result.putString(
                        "stream",
                        RecoveryAdoptionProbe.verify(
                                getTargetContext(), arguments.getString("recoveryProbeId")));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("recoveryImportDocument"))) {
                result.putString(
                        "stream",
                        "Recovery UI file ready: "
                                + RecoveryImportDocumentProbe.create(getTargetContext())
                                + "\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && arguments.getString("rotationInstalledVersion") != null) {
                result.putString(
                        "stream",
                        SigningRotationApkProbe.installed(
                                getTargetContext(),
                                Integer.parseInt(arguments.getString("rotationInstalledVersion"))));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("updateContinuityOnly"))) {
                result.putString("stream", UpdateContinuityProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("crashBuildOnly"))) {
                result.putString("stream", GeneratedCrashBuildProbe.run(getTargetContext()));
                finish(Activity.RESULT_OK, result);
                return;
            }
            if (arguments != null && "true".equals(arguments.getString("credentialStoreOnly"))) {
                CredentialStoreProbe.run(getTargetContext());
                result.putString("stream", "Credential store: 1 check passed\n");
                finish(Activity.RESULT_OK, result);
                return;
            }
            StringBuilder failures = new StringBuilder();
            int checks = 0;
            int accepted = 0;
            int rejected = 0;
            try (BufferedReader index =
                    new BufferedReader(
                            new InputStreamReader(
                                    getContext().getAssets().open("fixtures.tsv"),
                                    StandardCharsets.UTF_8))) {
                String row;
                while ((row = index.readLine()) != null) {
                    String[] fields = row.split("\t", -1);
                    if (fields.length != 2
                            || !(fields[0].equals("accept") || fields[0].equals("reject"))
                            || !fields[1].matches("[a-z0-9-]+\\.apk")) {
                        throw new AssertionError("Invalid fixture index row: " + row);
                    }
                    boolean shouldAccept = fields[0].equals("accept");
                    File apk = fixture(fields[1]);
                    if (getTargetContext()
                                    .getPackageManager()
                                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0)
                            == null) {
                        throw new AssertionError("Fixture is not a parseable APK: " + fields[1]);
                    }
                    try {
                        ArtifactValidator.validateUnsigned(getTargetContext(), apk, 1);
                        if (!shouldAccept) failures.append(fields[1]).append(' ');
                    } catch (IOException failure) {
                        if (shouldAccept)
                            throw new AssertionError(
                                    "Valid fixture rejected: " + fields[1], failure);
                    }
                    checks++;
                    if (shouldAccept) accepted++;
                    else rejected++;
                }
            }
            if (accepted == 0 || rejected == 0)
                throw new AssertionError("Fixture index lacks policy cases");
            if (failures.length() != 0)
                throw new AssertionError("Unexpected manifests accepted: " + failures);
            result.putString("stream", "Artifact policy: " + checks + " checks passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "Artifact policy FAILED: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private File fixture(String name) throws IOException {
        File result = new File(getTargetContext().getCacheDir(), "policy-test-" + name);
        try (InputStream input = getContext().getAssets().open(name);
                FileOutputStream output = new FileOutputStream(result)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return result;
    }

    private static String projectFrames(Throwable failure) {
        StringBuilder frames = new StringBuilder();
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (!frame.getClassName().startsWith("dev.smallwright.")) continue;
            if (frames.length() > 0) frames.append(',');
            frames.append(frame.getClassName().substring("dev.smallwright.".length()))
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
            if (frames.length() > 400) break;
        }
        return frames.toString();
    }
}
