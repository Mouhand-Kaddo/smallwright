package dev.smallwright.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.smallwright.agent.QuestionRound
import dev.smallwright.project.ChatStore

private const val SOMETHING_ELSE = "Something else"
private const val SEND_ANSWERS = "Send answers"

/**
 * The person's picks for one round: the recommended answer of each question until they pick
 * another, or the words they type for something else.
 */
class RoundAnswers(val round: QuestionRound) {
    private val picks = mutableStateListOf<Int>().apply { repeat(round.questions.size) { add(0) } }
    val own: List<TextBox> = List(round.questions.size) { TextBox(hint = "Your answer") }

    fun picked(question: Int): Int = picks[question]

    /** Picks a choice; the index just past the last choice is something else. */
    fun pick(question: Int, choice: Int) {
        picks[question] = choice
    }

    fun typing(question: Int): Boolean = picks[question] == round.questions[question].choices.size

    val complete: Boolean
        get() = round.questions.indices.all { !typing(it) || own[it].getText().isNotBlank() }

    /** The answers, then the person's own words from [prompt]. */
    fun message(prompt: String): String {
        val given =
            round.questions.indices.map {
                if (typing(it)) own[it].getText() else round.questions[it].choices[picks[it]]
            }
        return round.answers(given, prompt)
    }
}

/**
 * Sends the answers with the person's words after them, leaving the prompt as they typed it: a
 * refused Send can be tried again without the answers ever being in the prompt twice.
 */
internal fun sendAnswers(ui: UiState, answers: RoundAnswers) {
    ui.sendAnswers(answers.message(ui.modelRequest.getText()))
}

/** The index of the round still waiting for answers: the latest, when nothing was sent after it. */
fun openRound(turns: List<Turn>, live: Boolean): Int {
    if (live) return -1
    val asked = turns.indexOfLast { it.role == ChatStore.QUESTION }
    return if (asked > turns.indexOfLast { it.role == "user" }) asked else -1
}

/** A round from the builder: answerable while [open], plain text once answered. */
@Composable
internal fun QuestionTurn(ui: UiState, saved: String, open: Boolean) {
    val round = remember(saved) { QuestionRound.fromSaved(saved) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (round.intro.isNotEmpty()) BuilderReply(round.intro)
        if (open) QuestionCard(ui, remember(round) { RoundAnswers(round) })
        else AnsweredRound(round)
    }
}

@Composable
private fun QuestionCard(ui: UiState, answers: RoundAnswers) {
    val idle = ui.modelRequest.isEnabled
    Ruled(Ink.prompt) {
        answers.round.questions.forEachIndexed { number, question ->
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                QuestionText(number, question.text)
                question.choices.forEachIndexed { choice, text ->
                    Choice(
                        text,
                        if (choice == 0) recommended(question.reason) else "",
                        answers.picked(number) == choice,
                        idle,
                    ) {
                        answers.pick(number, choice)
                    }
                }
                Choice(SOMETHING_ELSE, "", answers.typing(number), idle) {
                    answers.pick(number, question.choices.size)
                }
                if (answers.typing(number)) {
                    val own = answers.own[number]
                    LaunchedEffect(idle) { own.isEnabled = idle }
                    TerminalField(own, Modifier.fillMaxWidth().padding(start = 28.dp, top = 4.dp))
                }
            }
        }
        SendAnswers(ui.generate.isEnabled && answers.complete) { sendAnswers(ui, answers) }
    }
}

private fun recommended(reason: String) =
    if (reason.isEmpty()) "Recommended" else "Recommended: $reason"

@Composable
private fun AnsweredRound(round: QuestionRound) {
    Ruled(Ink.rule) {
        round.questions.forEachIndexed { number, question ->
            Column {
                QuestionText(number, question.text)
                BasicText(
                    question.choices.first() +
                        " (recommended)" +
                        question.choices.drop(1).joinToString("") { ", $it" },
                    Modifier.padding(start = 28.dp, top = 4.dp),
                    style = Type.small.copy(color = Ink.dim),
                )
            }
        }
    }
}

/** A round's questions behind one vertical rule in [tone]. */
@Composable
private fun Ruled(tone: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                val x = fromStart(1.dp.toPx())
                drawLine(tone, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
            .padding(start = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun QuestionText(number: Int, text: String) {
    Row(Modifier.semantics(mergeDescendants = true) { heading() }) {
        BasicText(
            "${number + 1}",
            Modifier.width(28.dp),
            style = Type.body.copy(color = Ink.dim),
        )
        BasicText(text, style = Type.action.copy(color = Ink.text))
    }
}

/** One answer to tap: a round mark, filled in the prompt colour while picked. */
@Composable
private fun Choice(
    text: String,
    note: String,
    picked: Boolean,
    enabled: Boolean,
    onPick: () -> Unit,
) {
    val tone = if (picked) Ink.prompt else Ink.text
    Row(
        Modifier.fillMaxWidth()
            .selectable(picked, enabled = enabled, role = Role.RadioButton, onClick = onPick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(28.dp).clearAndSetSemantics {},
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.size(16.dp)
                    .clip(CircleShape)
                    .border(1.dp, if (picked) Ink.prompt else Ink.rule, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.prompt))
            }
        }
        Column {
            BasicText(
                text,
                style = Type.body.copy(color = if (enabled) tone else Ink.dim),
            )
            if (note.isNotEmpty()) BasicText(note, style = Type.small.copy(color = Ink.dim))
        }
    }
}

@Composable
private fun SendAnswers(enabled: Boolean, onSend: () -> Unit) {
    val color = if (enabled) Ink.prompt else Ink.dim.copy(alpha = 0.5f)
    Box(
        Modifier.fillMaxWidth()
            .border(1.dp, color)
            .clickable(enabled = enabled, role = Role.Button, onClick = onSend)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(SEND_ANSWERS, style = Type.action.copy(color = color))
    }
}
