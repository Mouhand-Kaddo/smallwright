package dev.smallwright.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The skills part of Settings: a GitHub link to find skills, the skills found there to tick and
 * add, and the skills added, each turned on or off or removed.
 */
@Composable
internal fun SkillsSection(ui: UiState) {
    LaunchedEffect(Unit) { ui.onShowSkills.run() }
    Heading("skills")
    BasicText(
        "Skills are instructions for the builder. Steps that need tools the builder doesn't " +
            "have are skipped.",
        Modifier.padding(top = 4.dp),
        style = Type.body.copy(color = Ink.dim),
    )
    BasicText(
        "The builder picks a skill when it fits. To start one yourself, begin your message " +
            "with / and its name, like /grill-me. To add one, paste its GitHub link here, or ask " +
            "the builder in a chat by link or by name.",
        Modifier.padding(top = 12.dp),
        style = Type.small.copy(color = Ink.dim),
    )
    TerminalField(
        ui.skillLink,
        Modifier.fillMaxWidth().padding(top = 16.dp),
        keyboard = KeyboardType.Uri,
    )
    TerminalButton(
        ui.findSkills,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
    val message = remember { BringIntoViewRequester() }
    LaunchedEffect(ui.skillsMessage) { if (ui.skillsMessage.isNotEmpty()) message.bringIntoView() }
    if (ui.skillsMessage.isNotEmpty()) {
        BasicText(
            ui.skillsMessage,
            Modifier.bringIntoViewRequester(message),
            style = Type.small.copy(color = Ink.dim),
        )
    }
    if (ui.foundSkills.isNotEmpty()) {
        for (skill in ui.foundSkills) {
            SkillLine(
                skill,
                onToggle = { ui.onToggleFoundSkill.accept(skill.name) },
                note = if (skill.replaces) "replaces the one you have" else null,
            )
        }
        if (ui.skillsReplacing.isEmpty()) {
            TerminalButton(
                ui.addSkills,
                tone = Ink.prompt,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        } else {
            BasicText(
                "These replace skills you already have: " +
                    ui.skillsReplacing.joinToString(", ") +
                    ". Replace them?",
                Modifier.padding(top = 12.dp),
                style = Type.body.copy(color = Ink.waiting),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TerminalButton(ui.replaceSkills, tone = Ink.prompt)
                TerminalButton(ui.cancelSkills, tone = Ink.dim)
            }
        }
    }
    if (ui.skillsDamaged) {
        BasicText(
            "The list of skills is damaged. Remove all skills, then add them again.",
            Modifier.padding(top = 20.dp),
            style = Type.body.copy(color = Ink.failed),
        )
        TerminalButton(
            ui.removeAllSkills,
            tone = Ink.failed,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
    if (ui.skills.isNotEmpty()) {
        BasicText(
            "added",
            Modifier.padding(top = 20.dp),
            style = Type.small.copy(color = Ink.dim),
        )
        for (skill in ui.skills) {
            SkillLine(
                skill,
                onToggle = { ui.onToggleSkill.accept(skill.name) },
                onRemove = { ui.onRemoveSkill.accept(skill.name) },
            )
        }
    }
}

/** A ticked or unticked skill with its description, and Remove for an added one. */
@Composable
private fun SkillLine(
    skill: SkillRow,
    onToggle: () -> Unit,
    note: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
        Row(
            Modifier.weight(1f)
                .toggleable(value = skill.on, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
        ) {
            BasicText(
                if (skill.on) "[x]" else "[ ]",
                Modifier.padding(end = 12.dp),
                style = Type.body.copy(color = if (skill.on) Ink.done else Ink.dim),
            )
            Column {
                BasicText(skill.name, style = Type.body.copy(color = Ink.text))
                BasicText(
                    skill.description,
                    style = Type.small.copy(color = Ink.dim),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skill.personOnly) {
                    BasicText(
                        "Starts only when you begin a message with /${skill.name}",
                        style = Type.small.copy(color = Ink.dim),
                    )
                }
                if (skill.onlyIn != null) {
                    BasicText(
                        "Only in ${skill.onlyIn}",
                        style = Type.small.copy(color = Ink.dim),
                    )
                }
                if (note != null) BasicText(note, style = Type.small.copy(color = Ink.waiting))
            }
        }
        if (onRemove != null) {
            BasicText(
                Labels.REMOVE_SKILL,
                Modifier.semantics { contentDescription = "Remove ${skill.name}" }
                    .clickable(role = Role.Button, onClick = onRemove)
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, top = 4.dp),
                style = Type.body.copy(color = Ink.failed),
            )
        }
    }
}
