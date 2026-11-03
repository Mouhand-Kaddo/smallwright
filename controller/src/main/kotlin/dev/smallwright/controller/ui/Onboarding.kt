package dev.smallwright.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val HOW_IT_WORKS =
    listOf(
        "you describe an app in plain words",
        "an AI model writes its code",
        "this phone builds it and installs it",
    )

/** First run: what the app does, the OpenRouter key, and the model. */
@Composable
internal fun Onboarding(ui: UiState, step: OnboardingStep) {
    BackHandler(enabled = step != OnboardingStep.WELCOME || ui.onboardingDone) {
        if (step == OnboardingStep.PLATFORM && ui.onboardingDone) leavePlatformStep(ui)
        else
            ui.onboarding =
                if (step == OnboardingStep.WELCOME) null
                else OnboardingStep.entries[step.ordinal - 1]
    }
    Column(
        Modifier.fillMaxSize()
            .background(Ink.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        BasicText(
            "step " + (step.ordinal + 1) + " of " + OnboardingStep.entries.size,
            Modifier.padding(horizontal = 24.dp).padding(top = 20.dp),
            style = Type.small.copy(color = Ink.dim),
        )
        when (step) {
            OnboardingStep.WELCOME -> Welcome(ui)
            OnboardingStep.KEY -> ConnectKey(ui)
            OnboardingStep.MODEL -> PickModel(ui)
            OnboardingStep.PLATFORM -> GetPlatformFiles(ui)
        }
    }
}

@Composable
private fun ColumnScope.Welcome(ui: UiState) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.padding(top = 48.dp))
        BasicText(
            "Describe an app.\nYour phone builds it.",
            style = Type.display.copy(color = Ink.text),
        )
        Spacer(Modifier.padding(top = 32.dp))
        TypedLines(HOW_IT_WORKS)
        BasicText(
            "Only your words and the app's code go to the AI model. Building, installing " +
                "and running the app happen on this phone.",
            Modifier.padding(top = 32.dp),
            style = Type.small.copy(color = Ink.dim),
        )
    }
    Primary("Get started") { ui.onboarding = OnboardingStep.KEY }
}

/** The welcome lines type out once, as a terminal prints them; all at once when motion is off. */
@Composable
private fun TypedLines(lines: List<String>) {
    val total = lines.sumOf { it.length }
    val animate = animationsOn()
    val typed by
        produceState(if (animate) 0 else total) {
            delay(300)
            while (value < total) {
                delay(22)
                value++
            }
        }
    var left = typed
    for (line in lines) {
        val shown = line.take(left.coerceAtLeast(0))
        left -= line.length
        Row(Modifier.padding(bottom = 10.dp)) {
            BasicText("> ", style = Type.body.copy(color = Ink.prompt))
            BasicText(shown, style = Type.body.copy(color = Ink.text))
        }
    }
}

@Composable
private fun ColumnScope.ConnectKey(ui: UiState) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.padding(top = 32.dp))
        BasicText("Connect OpenRouter", style = Type.title.copy(color = Ink.text))
        BasicText(
            "OpenRouter gives this app access to AI models. You pay OpenRouter for what you " +
                "use. Sign in to make a key for this app, or paste a key you have; it is stored " +
                "on this phone. You can also set a credit limit on the key at " +
                "openrouter.ai/keys, so it can never spend more than you choose.",
            Modifier.padding(top = 12.dp),
            style = Type.body.copy(color = Ink.dim),
        )
        if (ui.hasKey) {
            BasicText(
                "A key is saved on this phone.",
                Modifier.padding(top = 20.dp),
                style = Type.body.copy(color = Ink.done),
            )
        } else {
            OpenRouterSignIn(ui)
            TerminalField(
                ui.providerKey,
                Modifier.fillMaxWidth().padding(top = 20.dp),
                secret = true,
            )
            TerminalButton(
                ui.saveProviderKey,
                tone = Ink.prompt,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        if (ui.keyResult.isNotEmpty()) {
            BasicText(
                ui.keyResult,
                Modifier.padding(top = 8.dp),
                style = Type.small.copy(color = Ink.dim),
            )
        }
    }
    Primary(if (ui.hasKey) "Continue" else "Skip for now") {
        ui.providerKey.clear()
        ui.signInCode.clear()
        ui.keyResult = ""
        ui.onboarding = OnboardingStep.MODEL
    }
}

@Composable
private fun ColumnScope.PickModel(ui: UiState) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.padding(top = 32.dp))
        BasicText("Pick a model", style = Type.title.copy(color = Ink.text))
        BasicText(
            "This model writes your apps. You can change it later from the bottom of the screen.",
            Modifier.padding(top = 12.dp),
            style = Type.body.copy(color = Ink.dim),
        )
    }
    ModelPicker(ui) {}
    Primary("Use " + ui.model.shortName()) { ui.onboarding = OnboardingStep.PLATFORM }
}

private fun leavePlatformStep(ui: UiState) {
    ui.onboarding = null
    ui.onFinishOnboarding.run()
}

@Composable
private fun ColumnScope.GetPlatformFiles(ui: UiState) {
    val context = LocalContext.current
    val terms = remember {
        context.assets.open("android-sdk-terms.txt").bufferedReader().use { it.readText() }
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.padding(top = 32.dp))
        BasicText("Get Android's developer files", style = Type.title.copy(color = Ink.text))
        BasicText(
            "To build real apps, this phone needs Android's developer files from Google. It is " +
                "one 66 MB download, straight from Google, kept only on this phone and never " +
                "changed. Wi-Fi is best. You can chat without it; building waits for it.",
            Modifier.padding(top = 12.dp),
            style = Type.body.copy(color = Ink.dim),
        )
        PlatformProgress(ui)
        BasicText(
            "Google's Android SDK License Agreement",
            Modifier.padding(top = 28.dp),
            style = Type.action.copy(color = Ink.text),
        )
        BasicText(
            terms,
            Modifier.padding(top = 8.dp, bottom = 16.dp),
            style = Type.small.copy(color = Ink.dim),
        )
    }
    when (ui.platform) {
        PlatformState.OFFER -> {
            BasicText(
                "Tapping Accept and download means you accept the license above.",
                Modifier.padding(horizontal = 24.dp),
                style = Type.small.copy(color = Ink.dim),
            )
            PrimaryWithNotNow(ui, Labels.ACCEPT_AND_DOWNLOAD)
        }
        PlatformState.DOWNLOADING -> Primary("Downloading...", enabled = false) {}
        PlatformState.FAILED -> {
            PrimaryWithNotNow(ui, Labels.TRY_AGAIN)
        }
        PlatformState.DONE -> Primary("Done") { leavePlatformStep(ui) }
    }
}

@Composable
private fun PlatformProgress(ui: UiState) {
    when (ui.platform) {
        PlatformState.OFFER -> {}
        PlatformState.DOWNLOADING -> {
            BasicText(
                DownloadProgress.bar(ui.platformBytes, ui.platformTotal),
                Modifier.padding(top = 20.dp),
                style = Type.body.copy(color = Ink.waiting),
            )
            BasicText(
                DownloadProgress.sizes(ui.platformBytes, ui.platformTotal),
                style = Type.small.copy(color = Ink.dim),
            )
        }
        PlatformState.FAILED ->
            BasicText(
                ui.platformMessage,
                Modifier.padding(top = 20.dp),
                style = Type.body.copy(color = Ink.failed),
            )
        PlatformState.DONE ->
            BasicText(
                "The Android platform files are on this phone.",
                Modifier.padding(top = 20.dp),
                style = Type.body.copy(color = Ink.done),
            )
    }
}

@Composable
private fun PrimaryWithNotNow(ui: UiState, label: String) {
    val notNow = remember { Control(Labels.NOT_NOW).apply { isEnabled = true } }
    notNow.onClick = Runnable { leavePlatformStep(ui) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Primary(label, modifier = Modifier.weight(1f)) { ui.onGetPlatform.run() }
        TerminalButton(notNow, tone = Ink.dim, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Primary(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    onClick: () -> Unit,
) {
    val control = remember { Control(label) }
    control.isEnabled = enabled
    control.label = label
    control.onClick = Runnable(onClick)
    TerminalButton(
        control,
        tone = Ink.prompt,
        modifier = modifier,
    )
}
