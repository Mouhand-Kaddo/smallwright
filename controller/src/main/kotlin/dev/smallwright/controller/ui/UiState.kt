package dev.smallwright.controller.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import dev.smallwright.agent.ModelChoice
import dev.smallwright.agent.ReasoningLevel
import java.util.function.Consumer

/**
 * Text the screen shows or lets the person edit. Mirrors the EditText calls the controller makes.
 */
class TextBox(initial: String = "", hint: String = "") {
    val state = TextFieldState(initial)
    var hint by mutableStateOf(hint)
    var isEnabled by mutableStateOf(true)

    fun getText(): String = state.text.toString()

    fun setText(text: CharSequence) = state.setTextAndPlaceCursorAtEnd(text.toString())

    /** The characters as the field holds them. */
    fun chars(): CharSequence = state.text

    /** Empties the field and its undo history. */
    @OptIn(ExperimentalFoundationApi::class)
    fun clear() {
        state.clearText()
        state.undoState.clearHistory()
    }
}

/** One action the person can take. Mirrors the Button calls the controller makes. */
class Control(label: String) {
    var label by mutableStateOf(label)
    var isEnabled by mutableStateOf(false)
    private var shownCount by mutableIntStateOf(0)
    /** True while the screen is rendering this control somewhere. */
    val isShown: Boolean
        get() = shownCount > 0

    var onClick: Runnable = Runnable {}

    fun getText(): String = label

    fun setText(text: String) {
        label = text
    }

    fun setOnClickListener(listener: Runnable) {
        onClick = listener
    }

    fun rendered() {
        shownCount++
    }

    fun disposed() {
        shownCount--
    }
}

/** A choice among named items. Mirrors the Spinner calls the controller makes. */
class Picker {
    var items by mutableStateOf(listOf<String>())
    var selectedItemPosition by mutableStateOf(-1)
    var isEnabled by mutableStateOf(true)
    var onSelect: (Int) -> Unit = {}

    fun getCount(): Int = items.size

    /** Moves the selection without notifying, as Spinner.setSelection does before its listener. */
    fun setSelection(position: Int) {
        selectedItemPosition = position
    }

    fun select(position: Int) {
        if (position < 0 || position >= items.size) return
        selectedItemPosition = position
        onSelect(position)
    }
}

/** One entry of the chat transcript, with small copies of the pictures sent with it. */
data class Turn
@JvmOverloads
constructor(
    val role: String,
    val revision: Int,
    val text: String,
    val images: List<Bitmap> = emptyList(),
)

/**
 * One line of builder activity, the way a coding agent reports what it is doing. [trace] is the
 * model's thinking behind a "Thought for" step, empty when it returned none; on a [StepKind.PLAN]
 * step it is the plan as [dev.smallwright.agent.RunPlan.saved] writes it. [picture] is the JPEG of
 * the app a look sent to the model, kept only while the run shows and never saved.
 */
data class Step
@JvmOverloads
constructor(
    val kind: StepKind,
    val text: String,
    val detail: String = "",
    val trace: String = "",
    val picture: ByteArray? = null,
)

enum class StepKind {
    /** Waiting for the model; drawn with moving dots. */
    THINKING,
    /** A local step under way, such as a build. */
    RUNNING,
    /** A question for the person; the run waits for the answer. */
    ASKING,
    CALLED,
    DONE,
    FAILED,
    /** The run's to-do list; its text is the progress, such as "3 of 7 done". */
    PLAN,
}

/** The run going on in one chat: the words just sent, if any, and the steps so far. */
data class LiveRun(val chatId: String, val request: String?, val steps: List<Step>)

/**
 * Builder steps as one chat message: a line per step, with tab-separated kind, text, detail and,
 * when there is one, the trace with its backslashes, line breaks and tabs escaped.
 */
object StepLog {
    @JvmStatic
    fun encode(steps: List<Step>): String =
        steps.joinToString("\n") { step ->
            val kind =
                when (step.kind) {
                    StepKind.THINKING,
                    StepKind.RUNNING,
                    StepKind.ASKING -> StepKind.FAILED
                    else -> step.kind
                }
            val fields = listOf(kind.name.lowercase(), clean(step.text), clean(step.detail))
            (if (step.trace.isEmpty()) fields else fields + escaped(step.trace)).joinToString("\t")
        }

    @JvmStatic
    fun decode(text: String): List<Step> =
        text
            .lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split('\t', limit = 4)
                val kind = StepKind.entries.firstOrNull { it.name.lowercase() == parts[0] }
                if (kind == null || parts.size < 2) Step(StepKind.DONE, line.trim())
                else
                    Step(
                        kind,
                        parts[1],
                        parts.getOrElse(2) { "" },
                        unescaped(parts.getOrElse(3) { "" }),
                    )
            }

    private fun clean(text: String) = text.replace(Regex("[\\t\\r\\n]+"), " ").trim()

    private fun escaped(text: String) =
        text.replace("\r", "").replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescaped(text: String) = buildString {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            if (char != '\\' || index == text.length) {
                append(char)
                continue
            }
            when (val next = text[index++]) {
                'n' -> append('\n')
                't' -> append('\t')
                else -> append(next)
            }
        }
    }
}

/** A skill in Settings: one found at a link, ticked to add, or one added, turned on. */
data class SkillRow(
    val name: String,
    val description: String,
    val on: Boolean,
    val replaces: Boolean = false,
    val personOnly: Boolean = false,
    val onlyIn: String? = null,
)

/** First-run screens, in order. */
enum class OnboardingStep {
    WELCOME,
    KEY,
    MODEL,
    PLATFORM,
}

/** Where the Android platform files are: not asked for, downloading, failed, or kept. */
enum class PlatformState {
    OFFER,
    DOWNLOADING,
    FAILED,
    DONE,
}

enum class ModelListState {
    IDLE,
    LOADING,
    LOADED,
    FAILED,
}

/** Everything the screen renders. The controller owns and updates it; the screen only reads it. */
class UiState {
    val status = TextBox("Smallwright\nConnecting to isolated compiler worker...")
    val source = TextBox(hint = "The app's main source file")
    val modelRequest = TextBox(hint = "Message the builder")
    val providerKey = TextBox(hint = "sk-or-...")
    val serverBase = TextBox(hint = "https://api.example.com/v1")
    val serverKey = TextBox(hint = "API key")
    val serverModel = TextBox(hint = "Model name")
    val modelSearch = TextBox(hint = "Search models")
    val testHandoff = TextBox("Build an app to begin testing.")
    val chatHistory = TextBox("Start a chat to work on this project.")
    val stepLimit = TextBox(hint = "No limit")
    val costLimit = TextBox(hint = "No limit")
    val skillLink = TextBox(hint = "GitHub link")

    val stop = Control(Labels.STOP)
    val newProject = Control(Labels.NEW_PROJECT)
    val newChat = Control(Labels.NEW_CHAT)
    val generate = Control(Labels.GENERATE)
    val install = Control(Labels.INSTALL)
    val acceptTest = Control(Labels.ACCEPT)
    val build = Control(Labels.BUILD_SOURCE)
    val undo = Control(Labels.UNDO)
    val previewContext = Control(Labels.PREVIEW_CONTEXT)
    val exportApk = Control(Labels.EXPORT_APK)
    val exportSource = Control(Labels.EXPORT_SOURCE)
    val exportRecovery = Control(Labels.EXPORT_RECOVERY)
    val importRecovery = Control(Labels.IMPORT_RECOVERY)
    val selectScreenshot = Control(Labels.SELECT_SCREENSHOT)
    val attachImages = Control(Labels.ATTACH_IMAGES)
    val importCrashReport = Control(Labels.IMPORT_CRASH)
    val clearCrashReport = Control(Labels.CLEAR_CRASH)
    val deleteProject = Control(Labels.DELETE_PROJECT)
    val saveProviderKey = Control(Labels.SAVE_KEY)
    val removeProviderKey = Control(Labels.REMOVE_KEY)
    val saveServer = Control(Labels.SAVE_SERVER)
    val removeServerKey = Control(Labels.REMOVE_SERVER_KEY)
    val useServerModel = Control(Labels.USE_MODEL)
    val saveLimits = Control(Labels.SAVE_LIMITS)
    val addLibrary = Control(Labels.ADD_LIBRARY)
    val skipLibrary = Control(Labels.SKIP_LIBRARY)
    val openNotificationSettings = Control(Labels.OPEN_NOTIFICATION_SETTINGS)
    val findSkills = Control(Labels.FIND_SKILLS)
    val addSkills = Control("")
    val replaceSkills = Control(Labels.REPLACE_SKILLS)
    val cancelSkills = Control(Labels.CANCEL_SKILLS)
    val removeAllSkills = Control(Labels.REMOVE_ALL_SKILLS)

    val projectPicker = Picker()
    val chatPicker = Picker()

    var transcript by mutableStateOf(listOf<Turn>())
    var selectedChatId by mutableStateOf<String?>(null)
    /** The transcript index of the builder's summary that a small Undo sits under, or -1. */
    var undoUnder by mutableIntStateOf(-1)
    var live by mutableStateOf<LiveRun?>(null)
    var screenshot by mutableStateOf<Bitmap?>(null)
    /** Small copies of the pictures attached to the next message, in the order picked. */
    var attachments by mutableStateOf(listOf<Bitmap>())
    /** Small copies of the pictures sent with the message the run is working on. */
    var sending by mutableStateOf(listOf<Bitmap>())
    var keyResult by mutableStateOf("")
    var hasKey by mutableStateOf(false)
    /** The model server's saved address, empty when none is saved. */
    var serverSaved by mutableStateOf("")
    var hasServerKey by mutableStateOf(false)
    var serverResult by mutableStateOf("")
    var limitsResult by mutableStateOf("")
    var stopWhenStuck by mutableStateOf(false)
    var askBeforeLibraries by mutableStateOf(false)
    var askBeforeSkills by mutableStateOf(false)
    var notificationsAllowed by mutableStateOf(true)
    var foundSkills by mutableStateOf(listOf<SkillRow>())
    var skillsReplacing by mutableStateOf(listOf<String>())
    var skills by mutableStateOf(listOf<SkillRow>())
    var skillsMessage by mutableStateOf("")
    var skillsDamaged by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    /** True while the open menu slides shut; taps on it then do nothing. */
    var menuClosing by mutableStateOf(false)
    /** Sends the answers to an open question round as the next message. */
    var sendAnswers: (String) -> Unit = {}
    /** True from picking another project until its chats are loaded. */
    var openingProject by mutableStateOf(false)
    var toolsOpen by mutableStateOf(false)
    /** The open project's brief, empty when the builder has written none. */
    var projectBrief by mutableStateOf("")
    var settingsOpen by mutableStateOf(false)
    var modelPageOpen by mutableStateOf(false)
    var onboarding by mutableStateOf<OnboardingStep?>(null)
    var onboardingDone by mutableStateOf(false)
    var platform by mutableStateOf(PlatformState.OFFER)
    var platformBytes by mutableLongStateOf(0L)
    var platformTotal by mutableLongStateOf(1L)
    var platformMessage by mutableStateOf("")
    var platformNeededNote by mutableStateOf(false)

    var model by mutableStateOf(ModelChoice.DEFAULT, referentialEqualityPolicy())
    var models by mutableStateOf(listOf<ModelChoice>())
    var modelList by mutableStateOf(ModelListState.IDLE)
    var onLoadModels: Runnable = Runnable {}
    var serverModels by mutableStateOf(listOf<ModelChoice>())
    var serverModelList by mutableStateOf(ModelListState.IDLE)
    var onLoadServerModels: Runnable = Runnable {}
    var onPickModel: Consumer<ModelChoice> = Consumer {}
    var reasoning by mutableStateOf(ReasoningLevel.DEFAULT)
    var reasoningOpen by mutableStateOf(false)
    var onPickReasoning: Consumer<ReasoningLevel> = Consumer {}
    var onFinishOnboarding: Runnable = Runnable {}
    var onGetPlatform: Runnable = Runnable {}
    var onToggleStopWhenStuck: Runnable = Runnable {}
    var onToggleAskBeforeLibraries: Runnable = Runnable {}
    var onToggleAskBeforeSkills: Runnable = Runnable {}
    var onShowSkills: Runnable = Runnable {}
    var onSettingsClosed: Runnable = Runnable {}
    var onToggleFoundSkill: Consumer<String> = Consumer {}
    var onToggleSkill: Consumer<String> = Consumer {}
    var onRemoveSkill: Consumer<String> = Consumer {}
    var onRemoveAttachment: Consumer<Int> = Consumer {}

    init {
        newProject.isEnabled = true
        newChat.isEnabled = true
        previewContext.isEnabled = true
        selectScreenshot.isEnabled = true
        importCrashReport.isEnabled = true
        clearCrashReport.isEnabled = true
        saveProviderKey.isEnabled = true
        removeProviderKey.isEnabled = true
        saveServer.isEnabled = true
        removeServerKey.isEnabled = true
        useServerModel.isEnabled = true
        saveLimits.isEnabled = true
        addLibrary.isEnabled = true
        skipLibrary.isEnabled = true
        openNotificationSettings.isEnabled = true
        findSkills.isEnabled = true
        replaceSkills.isEnabled = true
        cancelSkills.isEnabled = true
        removeAllSkills.isEnabled = true
    }
}
