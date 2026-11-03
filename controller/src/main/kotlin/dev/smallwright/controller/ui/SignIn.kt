package dev.smallwright.controller.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Sign in on OpenRouter's page, then paste the one-time code it shows. */
@Composable
internal fun OpenRouterSignIn(ui: UiState) {
    TerminalButton(
        ui.signIn,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    )
    if (!ui.signInStarted) return
    TerminalField(ui.signInCode, Modifier.fillMaxWidth().padding(top = 12.dp))
    TerminalButton(
        ui.useSignInCode,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}
