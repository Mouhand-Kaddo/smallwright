package dev.smallwright.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One line of the menu's project tree, from the top. */
internal sealed interface TreeLine {
    data class Project(val position: Int, val name: String, val current: Boolean) : TreeLine

    data class Chat(val position: Int, val name: String, val current: Boolean) : TreeLine

    data object NewChat : TreeLine
}

/**
 * Every project in the order kept on the phone. The current one is open, its chats then New chat
 * under it, unless it is still [opening] and the chats listed are those of the project left.
 */
internal fun projectTree(projects: Picker, chats: Picker, opening: Boolean): List<TreeLine> =
    buildList {
        projects.items.forEachIndexed { position, name ->
            val current = position == projects.selectedItemPosition
            add(TreeLine.Project(position, name, current))
            if (current && !opening) {
                chats.items.forEachIndexed { chat, title ->
                    add(TreeLine.Chat(chat, title, chat == chats.selectedItemPosition))
                }
                add(TreeLine.NewChat)
            }
        }
    }

private val slide = tween<Float>(200, easing = FastOutSlowInEasing)
private val treeX = 24.dp
private val disabled = Ink.dim.copy(alpha = 0.5f)

/**
 * The menu: a panel that slides in from the left over the chat, which stays in sight behind a
 * scrim. Projects are folders and the current one is open with its chats. A tap outside, Back, the
 * close key or a drag to the left closes it.
 */
@Composable
internal fun Sidebar(ui: UiState) {
    val animate = animationsOn()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = min(maxWidth * 0.86f, 360.dp)
        val widthPx = with(LocalDensity.current) { width.toPx() }
        val offset = remember { Animatable(if (animate) -widthPx else 0f) }
        LaunchedEffect(Unit) {
            ui.menuClosing = false
            offset.animateTo(0f, slide)
        }
        val close: () -> Unit = {
            if (startClosing(ui))
                scope.launch {
                    try {
                        if (animate) offset.animateTo(-widthPx, slide)
                    } finally {
                        ui.menuOpen = false
                        ui.menuClosing = false
                    }
                }
        }
        val leave: () -> Unit = { ui.menuOpen = false }
        BackHandler(onBack = close)
        Box(
            Modifier.fillMaxSize()
                .drawBehind {
                    drawRect(Ink.background, alpha = 0.6f * (1f + offset.value / widthPx))
                }
                .pointerInput(Unit) { detectTapGestures { close() } }
                .semantics {
                    contentDescription = Labels.CLOSE_MENU
                    onClick(Labels.CLOSE_MENU) {
                        close()
                        true
                    }
                },
        )
        Column(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }
                .width(width)
                .fillMaxHeight()
                .background(Ink.background)
                .drawBehind {
                    val x = size.width - 0.5.dp.toPx()
                    drawLine(Ink.rule, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                .draggable(
                    rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-widthPx, 0f))
                        }
                    },
                    Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        if (offset.value < -widthPx / 3 || velocity < -1000f) close()
                        else if (animate) offset.animateTo(0f, slide) else offset.snapTo(0f)
                    },
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Vertical + WindowInsetsSides.Start,
                    ),
                ),
        ) {
            Header(close)
            Rule()
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Rendered(ui.newProject)
                Line(Modifier.action(ui.newProject.isEnabled) { runFromMenu(ui, ui.newProject) }) {
                    Glyph("+", tone(Ink.prompt, ui.newProject.isEnabled))
                    Name(ui.newProject.label, tone(Ink.prompt, ui.newProject.isEnabled))
                }
                for (line in projectTree(ui.projectPicker, ui.chatPicker, ui.openingProject)) {
                    when (line) {
                        is TreeLine.Project ->
                            ProjectLine(
                                line,
                                enabled = ui.projectPicker.isEnabled,
                                stem = !ui.openingProject,
                                onOpen = {
                                    if (
                                        pickFromMenu(ui, ui.projectPicker, line.position) &&
                                            ui.projectPicker.selectedItemPosition != line.position
                                    )
                                        leave()
                                },
                                onTools = {
                                    leave()
                                    ui.toolsOpen = true
                                },
                            )
                        is TreeLine.Chat ->
                            ChatLine(line, ui.chatPicker.isEnabled) {
                                if (pickFromMenu(ui, ui.chatPicker, line.position)) close()
                            }
                        TreeLine.NewChat -> NewChatLine(ui.newChat) { runFromMenu(ui, ui.newChat) }
                    }
                }
            }
            Rule()
            Column(Modifier.padding(vertical = 4.dp)) {
                if (ui.platform != PlatformState.DONE)
                    FooterLine(Labels.GET_PLATFORM, Ink.waiting) {
                        leave()
                        ui.onboarding = OnboardingStep.PLATFORM
                    }
                FooterLine(Labels.SETTINGS) {
                    leave()
                    ui.settingsOpen = true
                }
                FooterLine(Labels.HOW_IT_WORKS) {
                    leave()
                    ui.onboarding = OnboardingStep.WELCOME
                }
            }
        }
    }
}

/**
 * Closes the menu, then runs [control] once. A second tap that lands before the menu is gone does
 * nothing, so a double tap on New chat makes one chat.
 */
internal fun runFromMenu(ui: UiState, control: Control) {
    if (!ui.menuOpen || ui.menuClosing || !control.isEnabled) return
    ui.menuOpen = false
    control.onClick.run()
}

/**
 * Picks [position] in [picker] while the menu is open and still enabled for it; a second tap that
 * lands while the menu slides shut, or after the first pick disabled the picker, does nothing.
 */
internal fun pickFromMenu(ui: UiState, picker: Picker, position: Int): Boolean {
    if (!ui.menuOpen || ui.menuClosing || !picker.isEnabled) return false
    picker.select(position)
    return true
}

/** Marks the menu as sliding shut; false when it already is or is gone. */
internal fun startClosing(ui: UiState): Boolean {
    if (!ui.menuOpen || ui.menuClosing) return false
    ui.menuClosing = true
    return true
}

private fun tone(color: Color, enabled: Boolean) = if (enabled) color else disabled

private fun Modifier.action(enabled: Boolean, onClick: () -> Unit) =
    clickable(enabled = enabled, role = Role.Button, onClick = onClick)

@Composable
private fun Header(close: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            "Projects",
            Modifier.weight(1f).semantics { heading() },
            style = Type.title.copy(color = Ink.text),
        )
        Box(
            Modifier.size(48.dp).action(true, close).semantics {
                contentDescription = Labels.CLOSE_MENU
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(14.dp).drawBehind {
                    val stroke = 2.dp.toPx()
                    drawLine(Ink.dim, Offset.Zero, Offset(size.width, size.height), stroke)
                    drawLine(Ink.dim, Offset(size.width, 0f), Offset(0f, size.height), stroke)
                },
            )
        }
    }
}

/**
 * A folder: closed with a right-pointing mark, open with a down-pointing one, Project tools and,
 * when its chats are listed, a [stem] down to them. A switch the controller refuses closes the menu
 * so its reason shows.
 */
@Composable
private fun ProjectLine(
    line: TreeLine.Project,
    enabled: Boolean,
    stem: Boolean,
    onOpen: () -> Unit,
    onTools: () -> Unit,
) {
    val ink = tone(Ink.text, line.current || enabled)
    Line(
        if (line.current)
            Modifier.drawBehind {
                if (!stem) return@drawBehind
                val x = treeX.toPx()
                drawLine(
                    Ink.rule,
                    Offset(x, size.height / 2 + 10.dp.toPx()),
                    Offset(x, size.height),
                    1.dp.toPx(),
                )
            }
        else
            Modifier.selectable(
                selected = false,
                enabled = enabled,
                role = Role.Button,
                onClick = onOpen,
            ),
    ) {
        Row(
            if (line.current)
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    selected = true
                    heading()
                }
            else Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(if (line.current) "▾" else "▸", ink)
            Name(line.name, ink, weight = FontWeight.SemiBold)
        }
        if (line.current) MoreKey(onTools)
    }
}

/** Three dots that open the project tools, read out as Project tools. */
@Composable
private fun MoreKey(onClick: () -> Unit) {
    Row(
        Modifier.size(48.dp).action(true, onClick).semantics { contentDescription = Labels.TOOLS },
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { Box(Modifier.size(3.dp).background(Ink.text)) }
    }
}

/** A chat under its project. The current one is the one inverted line in the menu. */
@Composable
private fun ChatLine(line: TreeLine.Chat, enabled: Boolean, onClick: () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    if (line.current) LaunchedEffect(line) { requester.bringIntoView() }
    val ink = if (line.current) Ink.background else tone(Ink.text, enabled)
    Branch(
        last = false,
        current = line.current,
        modifier =
            Modifier.bringIntoViewRequester(requester)
                .selectable(
                    selected = line.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
    ) {
        Name(line.name, ink, weight = if (line.current) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun NewChatLine(control: Control, onClick: () -> Unit) {
    Rendered(control)
    val ink = tone(Ink.prompt, control.isEnabled)
    Branch(last = true, current = false, modifier = Modifier.action(control.isEnabled, onClick)) {
        Glyph("+", ink, 16.dp)
        Name(control.label, ink)
    }
}

/** A line under the open project, joined to it by tree lines: ├─ or, for the last one, └─. */
@Composable
private fun Branch(
    last: Boolean,
    current: Boolean,
    modifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val lines = if (current) Ink.background else Ink.rule
    Row(
        modifier
            .fillMaxWidth()
            .then(if (current) Modifier.background(Ink.prompt) else Modifier)
            .drawBehind {
                val x = treeX.toPx()
                val middle = size.height / 2
                val stroke = 1.dp.toPx()
                drawLine(lines, Offset(x, 0f), Offset(x, if (last) middle else size.height), stroke)
                drawLine(lines, Offset(x, middle), Offset(x + 12.dp.toPx(), middle), stroke)
            }
            .heightIn(min = 48.dp)
            .padding(start = treeX + 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Line(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A mark drawn as text but not read out; the line's words say what it is. */
@Composable
private fun Glyph(mark: String, color: Color, width: Dp = treeX) {
    BasicText(
        mark,
        Modifier.width(width).clearAndSetSemantics {},
        style = Type.action.copy(color = color, textAlign = TextAlign.Center),
    )
}

@Composable
private fun Name(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
) {
    BasicText(
        text,
        modifier.padding(vertical = 14.dp),
        style = Type.body.copy(color = color, fontWeight = weight),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FooterLine(label: String, color: Color = Ink.text, onClick: () -> Unit) {
    BasicText(
        label,
        Modifier.fillMaxWidth()
            .action(true, onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        style = Type.body.copy(color = color),
    )
}
