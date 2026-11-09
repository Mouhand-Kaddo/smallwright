package dev.smallwright.controller.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.smallwright.agent.RunPlan

/** The steps without the run's plan, which [PlanChecklist] shows on its own. */
internal fun withoutPlan(steps: List<Step>): List<Step> = steps.filter { it.kind != StepKind.PLAN }

/**
 * The newest plan among [steps] as one line, a cell per step and how many are done; tapping it
 * lists the steps with their marks. Shows nothing when the run made no plan.
 */
@Composable
internal fun PlanChecklist(steps: List<Step>) {
    val trace = steps.lastOrNull { it.kind == StepKind.PLAN }?.trace ?: return
    val plan = remember(trace) { RunPlan.fromSaved(trace) } ?: return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = planDescription(plan)
                    stateDescription = if (open) "expanded" else "collapsed"
                }
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (open) "Fold the plan" else "Show the plan",
                ) {
                    open = !open
                }
                .heightIn(min = 48.dp)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                if (open) "▾" else "▸",
                Modifier.width(20.dp).clearAndSetSemantics {},
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText(
                buildAnnotatedString {
                    for (item in plan.items) {
                        val todo = item.status == RunPlan.Status.TODO
                        withStyle(SpanStyle(color = if (todo) Ink.rule else tone(item.status))) {
                            append(if (todo) "░" else "█")
                        }
                    }
                    append("  ")
                    withStyle(SpanStyle(color = Ink.text)) { append(plan.progress()) }
                },
                Modifier.clearAndSetSemantics {},
                style = Type.body,
            )
        }
        AnimatedVisibility(open, enter = Reveal, exit = Fold) {
            Column(
                Modifier.padding(start = 20.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (item in plan.items) {
                    Row(
                        Modifier.clearAndSetSemantics {
                            contentDescription = stepDescription(item)
                        },
                    ) {
                        BasicText(
                            item.status.mark,
                            Modifier.padding(end = 8.dp),
                            style = Type.body.copy(color = tone(item.status)),
                        )
                        BasicText(
                            item.text,
                            style =
                                Type.body.copy(
                                    color =
                                        if (item.status == RunPlan.Status.DONE) Ink.dim
                                        else Ink.text,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private fun tone(status: RunPlan.Status): Color =
    when (status) {
        RunPlan.Status.DONE -> Ink.done
        RunPlan.Status.DOING -> Ink.waiting
        RunPlan.Status.TODO -> Ink.dim
    }

/** What a screen reader says for the plan's line. */
fun planDescription(plan: RunPlan): String = "Plan, " + plan.progress()

/** What a screen reader says for one step of the plan. */
fun stepDescription(item: RunPlan.Item): String =
    when (item.status) {
        RunPlan.Status.DONE -> "Done: "
        RunPlan.Status.DOING -> "Doing now: "
        RunPlan.Status.TODO -> "To do: "
    } + item.text
