package dev.smallwright.controller.ui

import android.graphics.Bitmap
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.smallwright.controller.PictureShrink
import dev.smallwright.project.ChatStore
import kotlinx.coroutines.delay

/** Ideas for an empty chat. Tapping one puts it in the prompt; nothing is sent until Send. */
private val IDEAS =
    listOf(
        "A water tracker that counts glasses per day",
        "A checklist I can tick off and clear",
        "Notes with titles, search and delete",
    )

/** The chat: your words in bubbles on the right, the builder's steps and replies on the left. */
@Composable
internal fun Conversation(ui: UiState, scroll: ScrollState, modifier: Modifier) {
    val turns = ui.transcript
    val live = ui.live?.takeIf { it.chatId == ui.selectedChatId }
    LaunchedEffect(turns, live, ui.testHandoff.getText()) {
        withFrameNanos {}
        scroll.scrollTo(scroll.maxValue)
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (turns.isEmpty() && live == null) Ideas(ui)
        val open = openRound(turns, live != null)
        for ((index, turn) in turns.withIndex()) {
            if (turn.role == "tool" && turns.getOrNull(index - 1)?.role == "tool") continue
            key(ui.selectedChatId, index) {
                when (turn.role) {
                    "user" -> YourBubble(turn.text.trim(), turn.images)
                    "tool" -> {
                        val logs =
                            turns
                                .subList(index, turns.size)
                                .takeWhile { it.role == "tool" }
                                .map {
                                    it.text
                                }
                        val steps = remember(logs) { logs.flatMap { StepLog.decode(it) } }
                        PlanChecklist(steps)
                        StepGroup(withoutPlan(steps), true)
                    }
                    ChatStore.QUESTION -> QuestionTurn(ui, turn.text, index == open)
                    ChatStore.UNDO -> UndoNote(turn.text)
                    else -> BuilderReply(turn.text)
                }
                if (index == ui.undoUnder) UndoUnderReply(ui.undo)
            }
        }
        if (live != null) {
            live.request?.let { YourBubble(it.trim(), ui.sending) }
            PlanChecklist(live.steps)
            val steps = withoutPlan(live.steps)
            StepGroup(steps.dropLast(1), ended = false)
            steps.lastOrNull()?.let { StepRow(it) }
            if (live.steps.lastOrNull()?.kind == StepKind.ASKING) LibraryAnswer(ui)
        }
        PrimaryAction(ui)
    }
}

@Composable
private fun Ideas(ui: UiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        BasicText("What should we build?", style = Type.title.copy(color = Ink.text))
        BasicText(
            "Describe it in your own words, or start from one of these.",
            Modifier.padding(top = 6.dp, bottom = 8.dp),
            style = Type.body.copy(color = Ink.dim),
        )
        for (idea in IDEAS) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = ui.modelRequest.isEnabled, role = Role.Button) {
                        ui.modelRequest.setText(idea)
                    }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("›", Modifier.width(20.dp), style = Type.body.copy(color = Ink.prompt))
                BasicText(idea, style = Type.body.copy(color = Ink.text))
            }
        }
    }
}

@Composable
private fun YourBubble(text: String, images: List<Bitmap>) {
    if (images.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            images.forEachIndexed { index, image ->
                Thumbnail(image, "Picture ${index + 1} you sent")
            }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        BasicText(
            text,
            Modifier.widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 4.dp,
                    ),
                )
                .background(Ink.panel)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            style = Type.body.copy(color = Ink.text),
        )
    }
}

@Composable
internal fun BuilderReply(text: String) {
    Marked(Ink.text) {
        Column {
            val blocks = remember(text) { ReplyText.parse(text) }
            for ((index, block) in blocks.withIndex()) {
                val itemAfterItem =
                    block.marker.isNotEmpty() && blocks[maxOf(index - 1, 0)].marker.isNotEmpty()
                Row(
                    Modifier.padding(
                        top = if (index == 0) 0.dp else if (itemAfterItem) 4.dp else 10.dp,
                    ),
                ) {
                    if (block.marker.isNotEmpty()) {
                        BasicText(
                            block.marker,
                            Modifier.padding(end = 8.dp),
                            style = Type.body.copy(color = Ink.dim),
                        )
                    }
                    BasicText(block.text, style = Type.body.copy(color = Ink.text))
                }
            }
        }
    }
}

/** The note left by an undo, quieter than the builder's words. */
@Composable
private fun UndoNote(text: String) {
    Marked(Ink.dim) { BasicText(text, style = Type.body.copy(color = Ink.dim)) }
}

/** A plain Undo under the summary of the run that made the latest change. */
@Composable
private fun UndoUnderReply(undo: Control) {
    if (!undo.isEnabled) return
    Rendered(undo)
    Box(
        Modifier.padding(start = 8.dp)
            .semantics { contentDescription = "Undo the builder's last change" }
            .clickable(role = Role.Button) { undo.onClick.run() }
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Undo", style = Type.action.copy(color = Ink.dim))
    }
}

/** Two or more steps as one bordered row that says what they did; tapping it lists them. */
@Composable
private fun StepGroup(steps: List<Step>, ended: Boolean) {
    if (steps.size <= 1) {
        steps.forEach { StepRow(it) }
        return
    }
    var open by remember { mutableStateOf(false) }
    val summary = remember(steps, ended) { StepSummaries.of(steps, ended) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .border(1.dp, Ink.rule)
                .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                .clickable(role = Role.Button) { open = !open }
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            BasicText(
                if (open) "▾" else "▸",
                Modifier.width(20.dp).clearAndSetSemantics {},
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText(
                buildAnnotatedString {
                    append(summary.done)
                    if (summary.done.isNotEmpty() && summary.failure.isNotEmpty()) append(", ")
                    withStyle(SpanStyle(color = Ink.failed)) { append(summary.failure) }
                },
                style = Type.body.copy(color = Ink.text),
            )
        }
        AnimatedVisibility(open, enter = Reveal, exit = Fold) {
            Column(
                Modifier.padding(start = 12.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                steps.forEach { StepRow(it) }
            }
        }
        val picture = steps.lastOrNull { it.picture != null }?.picture
        if (picture != null)
            AnimatedVisibility(!open, enter = Reveal, exit = Fold) { LookPicture(picture) }
    }
}

/**
 * A dot in the step's hue, its words, and an indented detail line when there is one. A detail
 * longer than three lines is cut there; tapping the step shows all of it.
 */
@Composable
private fun StepRow(step: Step) {
    val tone =
        when (step.kind) {
            StepKind.THINKING,
            StepKind.RUNNING,
            StepKind.ASKING -> Ink.waiting
            StepKind.CALLED,
            StepKind.PLAN -> Ink.prompt
            StepKind.DONE -> Ink.done
            StepKind.FAILED -> Ink.failed
        }
    val working = step.kind == StepKind.THINKING || step.kind == StepKind.RUNNING
    var full by remember { mutableStateOf(false) }
    var clipped by remember { mutableStateOf(false) }
    Marked(tone, pulse = working) {
        Column(
            if (clipped || full)
                Modifier.semantics { stateDescription = if (full) "expanded" else "collapsed" }
                    .clickable(role = Role.Button) { full = !full }
            else Modifier,
        ) {
            Row {
                BasicText(step.text, style = Type.body.copy(color = Ink.text))
                if (working) BasicText(movingDots(), style = Type.body.copy(color = Ink.dim))
            }
            if (step.detail.isNotEmpty()) {
                BasicText(
                    "└ " + step.detail,
                    style = Type.small.copy(color = Ink.dim),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = if (full) Int.MAX_VALUE else 3,
                    onTextLayout = { if (!full) clipped = it.hasVisualOverflow },
                )
            }
            if (step.trace.isNotEmpty())
                ThinkingTrace(step.trace, live = step.kind == StepKind.THINKING)
            step.picture?.let { LookPicture(it) }
        }
    }
}

/** The picture of the app a look sent to the model, small and whole. */
@Composable
private fun LookPicture(jpeg: ByteArray) {
    val image = remember(jpeg) { PictureShrink.thumbnail(jpeg) } ?: return
    Image(
        image.asImageBitmap(),
        "The picture of the app the builder sent",
        Modifier.padding(top = 6.dp).height(160.dp).border(1.dp, Ink.rule),
        contentScale = ContentScale.Fit,
    )
}

/**
 * The model's thinking in dim text, folded to two lines: its opening ones once finished, its newest
 * while the model still thinks. Tapping opens or folds it.
 */
@Composable
private fun ThinkingTrace(trace: String, live: Boolean) {
    var open by remember { mutableStateOf(false) }
    val style = Type.small.copy(color = Ink.dim)
    Box(
        Modifier.padding(top = 4.dp)
            .semantics {
                contentDescription = traceDescription(trace, open, live)
                stateDescription = if (open) "expanded" else "collapsed"
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (open) "Fold the thinking" else "Show all the thinking",
            ) {
                open = !open
            }
            .heightIn(min = 48.dp),
    ) {
        if (live && !open) {
            val twoLines = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }
            Box(
                Modifier.heightIn(max = twoLines).clipToBounds(),
                contentAlignment = Alignment.BottomStart,
            ) {
                BasicText(
                    newestPart(trace),
                    Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true),
                    style = style,
                )
            }
        } else {
            BasicText(
                trace,
                style = style,
                overflow = TextOverflow.Ellipsis,
                maxLines = if (open) Int.MAX_VALUE else 2,
            )
        }
    }
}

/** Enough of the end of a trace to fill two lines, so a long live trace is cheap to lay out. */
private fun newestPart(trace: String): String {
    if (trace.length <= 400) return trace
    var start = trace.length - 400
    if (Character.isLowSurrogate(trace[start])) start++
    return trace.substring(start)
}

/**
 * What a screen reader says for a trace: all of it when open; folded, its opening words, or its
 * newest while the model still thinks.
 */
@JvmOverloads
fun traceDescription(trace: String, open: Boolean, live: Boolean = false): String {
    if (open) return "Thinking: $trace"
    val count = trace.codePointCount(0, trace.length)
    val shown = count.coerceAtMost(160)
    if (live) {
        val start = trace.offsetByCodePoints(0, count - shown)
        return "Thinking so far: " + (if (start > 0) "…" else "") + trace.substring(start)
    }
    val end = trace.offsetByCodePoints(0, shown)
    return "Thinking, folded: " + trace.substring(0, end) + if (end < trace.length) "…" else ""
}

@Composable
private fun LibraryAnswer(ui: UiState) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TerminalButton(ui.addLibrary, tone = Ink.prompt, modifier = Modifier.weight(1f))
        TerminalButton(ui.skipLibrary, tone = Ink.dim, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Marked(tone: Color, pulse: Boolean = false, content: @Composable () -> Unit) {
    val alpha =
        if (pulse && animationsOn()) {
            rememberInfiniteTransition(label = "pulse")
                .animateFloat(
                    1f,
                    0.3f,
                    infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                    label = "dot",
                )
                .value
        } else 1f
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.padding(top = 7.dp, end = 12.dp)
                .size(8.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(tone),
        )
        content()
    }
}

/** "", ".", "..", "..." in turn, padded so the line does not shift; static when motion is off. */
@Composable
internal fun movingDots(): String {
    if (!animationsOn()) return "..."
    val count by
        produceState(0) {
            while (true) {
                delay(400)
                value = (value + 1) % 4
            }
        }
    return ".".repeat(count).padEnd(3)
}

@Composable
internal fun animationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

/** The one thing to do next, or null when the prompt is it. The harness state decides. */
internal fun primaryAction(ui: UiState): Control? =
    when {
        ui.acceptTest.isEnabled -> ui.acceptTest
        ui.install.isEnabled -> ui.install
        ui.build.isEnabled && ui.build.label != Labels.BUILD_SOURCE -> ui.build
        else -> null
    }

/** Once words are in the prompt, Send is the outlined action and the state action is plain. */
internal fun typing(ui: UiState): Boolean = ui.modelRequest.getText().isNotBlank()

/**
 * Send looks like the next step when there are words to send, or when nothing else waits: no
 * primary action and no question round to answer.
 */
internal fun sendIsNext(ui: UiState): Boolean =
    typing(ui) || primaryAction(ui) == null && openRound(ui.transcript, ui.live != null) < 0

@Composable
private fun PrimaryAction(ui: UiState) {
    val control = primaryAction(ui) ?: return
    TerminalButton(
        control,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        outlined = !typing(ui),
    )
}

/** A picture as a small square with a one-pixel rule, read out as [description]. */
@Composable
internal fun Thumbnail(image: Bitmap, description: String) {
    Image(
        image.asImageBitmap(),
        description,
        Modifier.size(64.dp).border(1.dp, Ink.rule),
        contentScale = ContentScale.Crop,
    )
}
