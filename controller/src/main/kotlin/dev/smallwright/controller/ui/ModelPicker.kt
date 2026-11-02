package dev.smallwright.controller.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.smallwright.agent.ModelChoice
import dev.smallwright.agent.ModelSections

/**
 * The model page: pick the model that writes your apps from OpenRouter's models or from the
 * OpenAI-compatible server's.
 */
@Composable
internal fun ModelPage(ui: UiState) {
    BackHandler { ui.modelPageOpen = false }
    var atServer by remember { mutableStateOf(!ui.model.provider.isOpenRouter) }
    Column(
        Modifier.fillMaxSize()
            .background(Ink.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        PageHeader("model") { ui.modelPageOpen = false }
        Rule()
        ChoiceRow(listOf("openrouter", "openai-compatible"), if (atServer) 1 else 0) {
            atServer = it == 1
        }
        if (atServer) ServerModels(ui) else ModelPicker(ui) { ui.modelPageOpen = false }
    }
}

/**
 * The server's models: a name typed or picked from its list, which loads on first show. A server
 * that lists none still takes a typed name.
 */
@Composable
private fun ColumnScope.ServerModels(ui: UiState) {
    if (!ui.hasServerKey) {
        Note("Add the server's address and key in Settings first.")
        val settings = remember { Control(Labels.SETTINGS).apply { isEnabled = true } }
        settings.onClick = Runnable {
            ui.modelPageOpen = false
            ui.settingsOpen = true
        }
        TerminalButton(settings, tone = Ink.prompt, modifier = Modifier.padding(horizontal = 20.dp))
        return
    }
    LaunchedEffect(ui.serverSaved) {
        if (ui.serverModelList == ModelListState.IDLE) ui.onLoadServerModels.run()
    }
    TerminalField(
        ui.serverModel,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
        maxLines = 1,
    )
    TerminalButton(
        ui.useServerModel,
        tone = Ink.prompt,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    )
    if (ui.serverResult.isNotEmpty()) Note(ui.serverResult)
    val query = ui.serverModel.getText()
    val shown = remember(ui.serverModels, query) { ui.serverModels.filter { it.matches(query) } }
    when (ui.serverModelList) {
        ModelListState.IDLE,
        ModelListState.LOADING -> Note("Loading the server's models" + movingDots())
        ModelListState.FAILED -> Note("The server did not list its models. Type the model's name.")
        ModelListState.LOADED -> if (shown.isEmpty()) Note("No listed model matches.")
    }
    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        items(shown, key = { it.id }) { model ->
            ModelRow(model, current = model == ui.model) {
                ui.onPickModel.accept(model)
                ui.modelPageOpen = false
            }
        }
    }
}

/** Search field and list. Loads the list on first show; a failed load waits for Try again. */
@Composable
internal fun ColumnScope.ModelPicker(ui: UiState, onPicked: () -> Unit) {
    LaunchedEffect(Unit) { if (ui.modelList == ModelListState.IDLE) ui.onLoadModels.run() }
    TerminalField(
        ui.modelSearch,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        maxLines = 1,
    )
    val query = ui.modelSearch.getText()
    val sections =
        remember(ui.models, ui.model, query) {
            if (query.isBlank()) ModelSections.of(ui.models, ui.model).map { it.title to it.models }
            else {
                val found = ui.models.filter { it.matches(query) }
                val shown =
                    if (
                        ui.model in found ||
                            !ui.model.provider.isOpenRouter ||
                            !ui.model.matches(query)
                    )
                        found
                    else listOf(ui.model) + found
                if (shown.isEmpty()) emptyList() else listOf(null to shown)
            }
        }
    when (ui.modelList) {
        ModelListState.IDLE,
        ModelListState.LOADING -> Note("Loading models from OpenRouter" + movingDots())
        ModelListState.FAILED -> {
            Note("Could not load the model list. Check the internet connection and try again.")
            val retry = remember { Control("Try again").apply { isEnabled = true } }
            retry.onClick = ui.onLoadModels
            TerminalButton(
                retry,
                tone = Ink.prompt,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        ModelListState.LOADED ->
            if (sections.isEmpty()) Note("No model matches \"" + query.trim() + "\".")
    }
    val list = rememberLazyListState()
    LaunchedEffect(query) { list.scrollToItem(0) }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list) {
        sections.forEach { (title, models) ->
            title?.let { item(key = "section:$it") { SectionTitle(it) } }
            items(models, key = { it.id }) { model ->
                ModelRow(model, current = model == ui.model) {
                    ui.onPickModel.accept(model)
                    onPicked()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    BasicText(
        title,
        Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
        style = Type.small.copy(color = Ink.prompt),
    )
}

@Composable
private fun ModelRow(model: ModelChoice, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (current) Modifier.background(Ink.panel) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp)
                .height(32.dp)
                .background(if (current) Ink.prompt else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            BasicText(model.nameMarkedFree(), style = Type.body.copy(color = Ink.text))
            val detail =
                listOf(model.maker(), model.priceLabel())
                    .filter { it.isNotEmpty() }
                    .joinToString("  ·  ")
            if (detail.isNotEmpty()) BasicText(detail, style = Type.small.copy(color = Ink.dim))
        }
    }
}

@Composable
private fun Note(text: String) {
    BasicText(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        style = Type.body.copy(color = Ink.dim),
    )
}
