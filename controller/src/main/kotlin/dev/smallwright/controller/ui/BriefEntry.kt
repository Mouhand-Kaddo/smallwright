package dev.smallwright.controller.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * The project brief the builder wrote, read-only: one row that opens to show it, or a plain line
 * saying there is none yet.
 */
@Composable
internal fun BriefEntry(brief: String) {
    if (brief.isBlank()) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText("No project brief yet", style = Type.body.copy(color = Ink.dim))
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .semantics { stateDescription = if (open) "expanded" else "collapsed" }
                .clickable(role = Role.Button) { open = !open }
                .heightIn(min = 48.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                if (open) "▾" else "▸",
                Modifier.width(20.dp).clearAndSetSemantics {},
                style = Type.body.copy(color = Ink.dim),
            )
            BasicText("Project brief", style = Type.body.copy(color = Ink.text))
        }
        if (open) Box(Modifier.padding(start = 20.dp, bottom = 12.dp)) { BuilderReply(brief) }
    }
}
