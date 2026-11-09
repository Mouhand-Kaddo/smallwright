package dev.smallwright.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.smallwright.agent.ReasoningLevel

@Composable
fun ControllerScreen(ui: UiState) {
    TerminalTheme {
        Box(Modifier.fillMaxSize().background(Ink.background)) {
            val transcriptScroll = rememberScrollState()
            val onboarding = ui.onboarding
            when {
                onboarding != null -> Onboarding(ui, onboarding)
                ui.modelPageOpen -> ModelPage(ui)
                ui.toolsOpen -> ToolsPage(ui)
                ui.settingsOpen -> SettingsPage(ui)
                else -> {
                    Home(ui, transcriptScroll, covered = ui.menuOpen)
                    if (ui.menuOpen) Sidebar(ui)
                }
            }
        }
    }
}

/**
 * The home screen. It is not composed while a page is open; its scroll position is kept outside so
 * coming back lands where the person left. While the menu [covered] it, it stays in sight but is
 * not read out.
 */
@Composable
private fun Home(ui: UiState, transcriptScroll: ScrollState, covered: Boolean) {
    Column(
        Modifier.fillMaxSize()
            .then(if (covered) Modifier.clearAndSetSemantics {} else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        TopBar(ui)
        StatusLine(ui)
        WorkerNeeded(ui)
        PlatformNeeded(ui)
        Rule()
        Conversation(ui, transcriptScroll, Modifier.weight(1f))
        Rule()
        PromptLine(ui)
    }
}

/**
 * The menu icon, then the project name. Tapping the name opens the menu; a long press opens the
 * project tools.
 */
@Composable
private fun TopBar(ui: UiState) {
    val picker = ui.projectPicker
    val name = picker.items.getOrNull(picker.selectedItemPosition) ?: "no project"
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon { ui.menuOpen = true }
        BasicText(
            name,
            Modifier.weight(1f)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = Labels.TOOLS,
                    onLongClick = { ui.toolsOpen = true },
                    onClick = { ui.menuOpen = true },
                )
                .padding(vertical = 8.dp),
            style = Type.title.copy(color = Ink.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Three bars, read out as Menu. */
@Composable
private fun MenuIcon(onClick: () -> Unit) {
    Column(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = Labels.MENU
        },
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) { Box(Modifier.size(20.dp, 2.dp).background(Ink.text)) }
    }
}

/** The harness message, word for word, under the project name. */
@Composable
private fun StatusLine(ui: UiState) {
    val message = ui.status.getText().substringAfter('\n', "")
    if (message.isBlank()) return
    BasicText(
        message,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
        style = Type.small.copy(color = Ink.dim),
    )
}

/** Offers the build worker this app carries while it is missing or older than this app. */
@Composable
private fun WorkerNeeded(ui: UiState) {
    val label =
        when (ui.worker) {
            WorkerState.MISSING -> Labels.INSTALL_WORKER
            WorkerState.OUTDATED -> Labels.UPDATE_WORKER
            WorkerState.READY,
            WorkerState.OTHER_KEY -> return
        }
    BasicText(
        "$label  >",
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button) { ui.onInstallWorker.run() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        style = Type.body.copy(color = Ink.waiting),
    )
}

/** Shown after a Send that needed the Android platform files, until they are on the phone. */
@Composable
private fun PlatformNeeded(ui: UiState) {
    if (!ui.platformNeededNote || ui.platform == PlatformState.DONE) return
    BasicText(
        Labels.GET_PLATFORM + "  >",
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button) { ui.onboarding = OnboardingStep.PLATFORM }
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        style = Type.body.copy(color = Ink.waiting),
    )
}

@Composable
internal fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.rule))
}

/**
 * [x] from the start edge: from the left, or from the right when the screen reads right to left.
 */
internal fun DrawScope.fromStart(x: Float) =
    if (layoutDirection == LayoutDirection.Rtl) size.width - x else x

/** The mark of a folded row, pointing the way the screen reads: right, or left in Arabic. */
@Composable
internal fun foldedMark(): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "◂" else "▸"

/**
 * The prompt. The pictures attached so far, a blue chevron and the person's words, then one row of
 * keys: add pictures, how much the model thinks, the model that will answer, and Send or Stop.
 */
@Composable
private fun PromptLine(ui: UiState) {
    Attachments(ui)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(
            ">",
            Modifier.padding(end = 10.dp, top = 2.dp),
            style = Type.title.copy(color = Ink.prompt),
        )
        Box(Modifier.weight(1f)) { TerminalField(ui.modelRequest, minLines = 1, prompt = true) }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PictureKey(ui.attachImages)
        if (ui.model.reasoning)
            Box(
                Modifier.size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Change thinking") {
                        ui.reasoningOpen = !ui.reasoningOpen
                    }
                    .semantics { contentDescription = "Thinking: " + ui.reasoning.label() },
                contentAlignment = Alignment.Center,
            ) {
                ThinkingMeter(ui.reasoning, Ink.prompt)
            }
        ModelKey(ui, Modifier.weight(1f))
        if (ui.stop.isEnabled) BlockKey(ui.stop, Ink.failed, solid = false, mark = StopSquare)
        else
            BlockKey(
                ui.generate,
                Ink.prompt,
                solid = sendIsNext(ui),
                mark = ArrowUp,
            )
    }
    AnimatedVisibility(ui.reasoningOpen && ui.model.reasoning, enter = Reveal, exit = Fold) {
        ThinkingChoice(ui)
    }
}

/**
 * The model that will answer. A long name is cut short, but never the mark that says it is free,
 * which tells it from a paid model of the same name. The name and its mark keep their English order
 * when the screen reads right to left.
 */
@Composable
private fun ModelKey(ui: UiState, modifier: Modifier) {
    val style = Type.small.copy(color = Ink.dim)
    Row(
        modifier
            .clickable(role = Role.Button, onClickLabel = "Change model") {
                ui.modelPageOpen = true
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Absolute.Left,
    ) {
        BasicText(
            ui.model.shortName(),
            Modifier.weight(1f, fill = false),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (ui.model.isFree()) BasicText(" (free)", style = style, maxLines = 1)
    }
}

/** The four thinking levels in a row; picking one keeps it for the next Send. */
@Composable
private fun ThinkingChoice(ui: UiState) {
    BackHandler { ui.reasoningOpen = false }
    ChoiceRow(ReasoningLevel.entries.map { it.label() }, ui.reasoning.ordinal) {
        ui.onPickReasoning.accept(ReasoningLevel.entries[it])
    }
}

/** Labels in a row, one of them picked; read out as radio buttons. */
@Composable
internal fun ChoiceRow(labels: List<String>, picked: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val current = index == picked
            BasicText(
                label,
                Modifier.weight(1f)
                    .selectable(selected = current, role = Role.RadioButton) { onPick(index) }
                    .then(if (current) Modifier.background(Ink.panel) else Modifier)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 14.dp),
                style =
                    Type.small.copy(
                        color = if (current) Ink.prompt else Ink.text,
                        textAlign = TextAlign.Center,
                    ),
            )
        }
    }
}

/** The pictures picked for the next message, each with a cross that takes it off. */
@Composable
private fun Attachments(ui: UiState) {
    if (ui.attachments.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ui.attachments.forEachIndexed { index, image ->
            Box {
                Thumbnail(image, "Picture ${index + 1}")
                Box(
                    Modifier.align(Alignment.TopEnd)
                        .size(48.dp)
                        .clickable(enabled = ui.modelRequest.isEnabled, role = Role.Button) {
                            ui.onRemoveAttachment.accept(index)
                        }
                        .semantics { contentDescription = Labels.REMOVE_PICTURE + " ${index + 1}" },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    BasicText(
                        "×",
                        Modifier.background(Ink.background).border(1.dp, Ink.rule).size(24.dp),
                        style = Type.small.copy(color = Ink.text, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

/**
 * A rectangle with a one-pixel rule, in the hue of what it does. [square] draws a small filled
 * square before the label, which is not read out.
 */
@Composable
fun TerminalButton(
    control: Control,
    tone: Color,
    modifier: Modifier = Modifier,
    outlined: Boolean = true,
    square: Boolean = false,
) {
    Rendered(control)
    val color = if (control.isEnabled) tone else Ink.dim.copy(alpha = 0.5f)
    Box(
        modifier
            .then(if (outlined) Modifier.border(1.dp, color) else Modifier)
            .clickable(enabled = control.isEnabled, role = Role.Button) { control.onClick.run() }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (square) Box(Modifier.padding(end = 8.dp).size(8.dp).background(color))
            BasicText(control.label, style = Type.action.copy(color = color))
        }
    }
}

@Composable
fun TerminalField(
    field: TextBox,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 8,
    prompt: Boolean = false,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val style = Type.body.copy(color = if (field.isEnabled) Ink.text else Ink.dim)
    val decorator =
        androidx.compose.foundation.text.input.TextFieldDecorator { inner ->
            Box(
                if (prompt) modifier.padding(vertical = 4.dp)
                else modifier.border(1.dp, Ink.rule).padding(12.dp),
            ) {
                if (field.getText().isEmpty()) {
                    Row {
                        BasicText(field.hint, style = style.copy(color = Ink.dim))
                        if (prompt) BlockCursor(blink = field.isEnabled)
                    }
                }
                inner()
            }
        }
    if (secret) {
        BasicSecureTextField(
            state = field.state,
            modifier = Modifier.fillMaxWidth(),
            enabled = field.isEnabled,
            textStyle = style,
            cursorBrush = SolidColor(Ink.prompt),
            textObfuscationMode = TextObfuscationMode.RevealLastTyped,
            decorator = decorator,
        )
    } else {
        BasicTextField(
            state = field.state,
            modifier = Modifier.fillMaxWidth(),
            enabled = field.isEnabled,
            textStyle = style,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            lineLimits =
                TextFieldLineLimits.MultiLine(
                    minHeightInLines = minLines,
                    maxHeightInLines = maxLines,
                ),
            cursorBrush =
                SolidColor(
                    if (prompt && field.getText().isEmpty()) Color.Transparent else Ink.prompt,
                ),
            decorator = decorator,
        )
    }
}

/** The idle prompt's block cursor. It blinks unless the phone has animations turned off. */
@Composable
private fun BlockCursor(blink: Boolean) {
    val alpha =
        if (blink && animationsOn()) {
            rememberInfiniteTransition(label = "cursor")
                .animateFloat(
                    1f,
                    0f,
                    infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse),
                    label = "blink",
                )
        } else null
    Box(
        Modifier.padding(start = 2.dp, top = 3.dp)
            .size(9.dp, 18.dp)
            .graphicsLayer { this.alpha = alpha?.value ?: 1f }
            .background(Ink.prompt),
    )
}

@Composable
internal fun Rendered(control: Control) {
    DisposableEffect(control) {
        control.rendered()
        onDispose { control.disposed() }
    }
}

@Composable
private fun Overlay(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Ink.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        PageHeader(title, onClose)
        Rule()
        Column(
            Modifier.fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun PageHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, Modifier.weight(1f), style = Type.title.copy(color = Ink.text))
        val back = remember { Control(Labels.CLOSE).apply { isEnabled = true } }
        back.onClick = Runnable(onClose)
        TerminalButton(back, tone = Ink.dim, outlined = false)
    }
}

/** The source editor and the rarely needed actions, out of the way of the main flow. */
@Composable
private fun ToolsPage(ui: UiState) {
    BackHandler { ui.toolsOpen = false }
    Overlay(title = "project tools", onClose = { ui.toolsOpen = false }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            BasicText(
                "source",
                Modifier.padding(top = 18.dp, bottom = 6.dp),
                style = Type.small.copy(color = Ink.dim),
            )
            TerminalField(ui.source, Modifier.fillMaxWidth(), minLines = 8, maxLines = 24)
            TerminalButton(
                ui.build,
                tone = Ink.text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            BriefEntry(ui.projectBrief)
            projectTools(ui).forEach { ToolRow(it) }
            BasicText(
                "Selected screenshots stay on this phone for preview. They are not included in model requests.",
                Modifier.padding(top = 12.dp),
                style = Type.small.copy(color = Ink.dim),
            )
            ui.screenshot?.let { bitmap ->
                Image(
                    bitmap.asImageBitmap(),
                    "selected screenshot",
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            ToolRow(ui.deleteProject, tone = Ink.failed)
        }
    }
}

/**
 * The project tools under the source editor. Install is here, not only beside Send, because once
 * the app is installed and untested, It works, keep it takes that place.
 */
internal fun projectTools(ui: UiState): List<Control> =
    listOf(
        ui.install,
        ui.undo,
        ui.previewContext,
        ui.exportApk,
        ui.exportSource,
        ui.exportRecovery,
        ui.importRecovery,
        ui.selectScreenshot,
        ui.importCrashReport,
        ui.clearCrashReport,
    )

@Composable
internal fun ToolRow(control: Control, tone: Color = Ink.text) {
    Rendered(control)
    val color = if (control.isEnabled) tone else Ink.dim.copy(alpha = 0.5f)
    Box(
        Modifier.fillMaxWidth()
            .clickable(enabled = control.isEnabled, role = Role.Button) { control.onClick.run() }
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(control.label, style = Type.body.copy(color = color))
    }
}

/**
 * Settings: the OpenRouter key, the model server's address and key, the optional limits for each
 * run, whether run notifications can show, and skills. Leaving the page empties the key fields and
 * keeps the limits that are valid.
 */
@Composable
private fun SettingsPage(ui: UiState) {
    val close = {
        ui.providerKey.clear()
        ui.signInCode.clear()
        ui.serverKey.clear()
        ui.settingsOpen = false
    }
    DisposableEffect(Unit) {
        onDispose {
            ui.providerKey.clear()
            ui.signInCode.clear()
            ui.serverKey.clear()
            ui.keyResult = ""
            ui.serverResult = ""
            ui.onSettingsClosed.run()
        }
    }
    BackHandler(onBack = close)
    Overlay(title = "settings", onClose = close) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Heading("openrouter key")
            BasicText(
                "Your OpenRouter key lets this app use AI models. It is stored on this phone " +
                    "with Android Keystore and sent only to OpenRouter.",
                Modifier.padding(top = 4.dp),
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText(
                "To cap what the key can spend, set a credit limit on it at openrouter.ai/keys.",
                Modifier.padding(top = 12.dp),
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText(
                if (ui.hasKey) "A key is saved on this phone." else "No key is saved yet.",
                Modifier.padding(top = 12.dp),
                style = Type.body.copy(color = if (ui.hasKey) Ink.done else Ink.waiting),
            )
            OpenRouterSignIn(ui)
            TerminalField(
                ui.providerKey,
                Modifier.fillMaxWidth().padding(top = 16.dp),
                secret = true,
            )
            TerminalButton(
                ui.saveProviderKey,
                tone = Ink.prompt,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            if (ui.keyResult.isNotEmpty()) {
                BasicText(ui.keyResult, style = Type.small.copy(color = Ink.dim))
            }
            ToolRow(ui.removeProviderKey)

            ServerSection(ui)

            Heading("limits for each run")
            BasicText(
                "A run goes on until it is done. You can stop it sooner. Nothing is limited " +
                    "unless you fill it in.",
                Modifier.padding(top = 4.dp),
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText(
                "Most steps in a run",
                Modifier.padding(top = 16.dp, bottom = 6.dp),
                style = Type.body.copy(color = Ink.text),
            )
            TerminalField(ui.stepLimit, Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
            BasicText(
                "One step is one round of the builder thinking, then doing what it decided.",
                Modifier.padding(top = 6.dp),
                style = Type.small.copy(color = Ink.dim),
            )
            BasicText(
                "Most to spend in a run, in dollars",
                Modifier.padding(top = 16.dp, bottom = 6.dp),
                style = Type.body.copy(color = Ink.text),
            )
            TerminalField(ui.costLimit, Modifier.fillMaxWidth(), keyboard = KeyboardType.Decimal)
            TerminalButton(
                ui.saveLimits,
                tone = Ink.prompt,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (ui.limitsResult.isNotEmpty()) {
                BasicText(
                    ui.limitsResult,
                    Modifier.padding(top = 6.dp),
                    style = Type.small.copy(color = Ink.dim),
                )
            }
            SwitchRow(Labels.STOP_WHEN_STUCK, ui.stopWhenStuck) { ui.onToggleStopWhenStuck.run() }
            BasicText(
                "Ends a run when the same build error comes back after 3 fixes in a row.",
                style = Type.small.copy(color = Ink.dim),
            )
            SwitchRow(Labels.ASK_BEFORE_LIBRARIES, ui.askBeforeLibraries) {
                ui.onToggleAskBeforeLibraries.run()
            }
            BasicText(
                "The builder asks in the chat before it downloads a library.",
                style = Type.small.copy(color = Ink.dim),
            )
            SwitchRow(Labels.ASK_BEFORE_SKILLS, ui.askBeforeSkills) {
                ui.onToggleAskBeforeSkills.run()
            }
            BasicText(
                "The builder asks in the chat before it adds a skill you asked for.",
                style = Type.small.copy(color = Ink.dim),
            )

            Heading("notifications")
            BasicText(
                if (ui.notificationsAllowed) "Run notifications are on."
                else
                    "Run notifications are off. You will not see when a run is done or needs " +
                        "you while you are in another app.",
                Modifier.padding(top = 4.dp),
                style =
                    Type.body.copy(color = if (ui.notificationsAllowed) Ink.done else Ink.waiting),
            )
            if (!ui.notificationsAllowed) ToolRow(ui.openNotificationSettings, tone = Ink.prompt)

            SkillsSection(ui)
        }
    }
}

/** The OpenAI-compatible server: its address and key, saved together. */
@Composable
private fun ServerSection(ui: UiState) {
    Heading("openai-compatible server")
    BasicText(
        if (ui.hasServerKey) "A key is saved for " + ui.serverSaved + "."
        else "No server key is saved yet.",
        Modifier.padding(top = 4.dp),
        style = Type.body.copy(color = if (ui.hasServerKey) Ink.done else Ink.waiting),
    )
    TerminalField(
        ui.serverBase,
        Modifier.fillMaxWidth().padding(top = 16.dp),
        maxLines = 1,
        keyboard = KeyboardType.Uri,
    )
    TerminalField(ui.serverKey, Modifier.fillMaxWidth().padding(top = 8.dp), secret = true)
    TerminalButton(
        ui.saveServer,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
    if (ui.serverResult.isNotEmpty()) {
        BasicText(ui.serverResult, style = Type.small.copy(color = Ink.dim))
    }
    if (ui.hasServerKey) ToolRow(ui.removeServerKey)
}

@Composable
internal fun Heading(name: String) {
    BasicText(name, Modifier.padding(top = 28.dp), style = Type.small.copy(color = Ink.dim))
}

/** A box that is filled when on, read out as a switch. */
@Composable
private fun SwitchRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .heightIn(min = 48.dp)
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            if (on) "[x]" else "[ ]",
            Modifier.padding(end = 12.dp),
            style = Type.body.copy(color = if (on) Ink.done else Ink.dim),
        )
        BasicText(label, style = Type.body.copy(color = Ink.text))
    }
}
