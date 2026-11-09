package dev.smallwright.controller

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.smallwright.agent.ModelChoice
import dev.smallwright.agent.ReasoningLevel
import dev.smallwright.controller.ui.ControllerScreen
import dev.smallwright.controller.ui.LiveRun
import dev.smallwright.controller.ui.Step
import dev.smallwright.controller.ui.StepKind
import dev.smallwright.controller.ui.StepLog
import dev.smallwright.controller.ui.Turn
import dev.smallwright.controller.ui.UiState
import dev.smallwright.controller.ui.WorkerState
import dev.smallwright.project.ChatStore
import java.math.BigDecimal

/**
 * Debug only: the controller screen filled with fixed data through [UiState], the same boundary the
 * controller writes, for checking layout and motion without a model or a worker.
 *
 * `adb shell am start -n dev.smallwright.controller/.UiFixtureActivity --es data worst --ez menu
 * true`, where data is demo, worst, empty or huge.
 */
class UiFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui =
            when (intent.getStringExtra("data")) {
                "worst" -> worst()
                "empty" -> empty()
                "huge" -> huge()
                else -> demo()
            }
        ui.menuOpen = intent.getBooleanExtra("menu", false)
        setContent { ControllerScreen(ui) }
    }
}

private fun ready(): UiState =
    UiState().apply {
        status.setText("Smallwright\n")
        generate.isEnabled = true
        attachImages.isEnabled = true
        undo.isEnabled = true
    }

private fun UiState.projects(selected: Int, vararg names: String) {
    projectPicker.items = names.toList()
    projectPicker.setSelection(selected)
}

private fun UiState.chats(selected: Int, vararg titles: String) {
    chatPicker.items = titles.toList()
    chatPicker.setSelection(selected)
    selectedChatId = "chat-$selected"
}

private fun steps(vararg steps: Step) = Turn("tool", 1, StepLog.encode(steps.toList()))

private fun plan(vararg items: String) = Step(StepKind.PLAN, "", trace = items.joinToString("\n"))

private fun demo(): UiState =
    ready().apply {
        projects(0, "Water tracker", "Habit streaks", "Recipe box")
        chats(0, "Bigger buttons", "First version")
        transcript =
            listOf(
                Turn("user", 1, "A water tracker that counts glasses per day"),
                steps(
                    plan("[x] Write the screen", "[x] Count glasses", "[x] Build and install"),
                    Step(StepKind.DONE, "Thought for 4s", trace = "The person wants a counter."),
                    Step(StepKind.CALLED, "Wrote MainActivity.java", "62 lines"),
                    Step(StepKind.DONE, "Built the app", "4.1 s"),
                ),
                Turn("assistant", 1, "Done. The app counts glasses.\n- Tap + to add one\n- Long press to reset"),
                Turn("user", 2, "Make the buttons bigger"),
            )
        live =
            LiveRun(
                "chat-0",
                null,
                listOf(
                    Step(StepKind.DONE, "Read MainActivity.java"),
                    Step(StepKind.THINKING, "Thinking", trace = "The buttons are 48dp; make them 72dp"),
                ),
            )
        stop.isEnabled = true
        generate.isEnabled = false
    }

private const val ARABIC_PROJECT = "متتبع شرب الماء اليومي"
private const val LONG_PROJECT =
    "Family chore chart with weekly rotations, allowance totals and a parent lock screen"

private fun worst(): UiState =
    ready().apply {
        status.setText(
            "Smallwright\nThe worker could not start the build because another build is still " +
                "finishing; it will try again when that one is done.",
        )
        worker = WorkerState.MISSING
        platformNeededNote = true
        projects(1, "X", LONG_PROJECT, ARABIC_PROJECT, "📚 Reading list ✨")
        chats(
            1,
            "Use the colours from the screenshot and keep the counter when the phone restarts",
            "اجعل الأزرار أكبر 🌙",
            "🙂",
        )
        model =
            ModelChoice(
                "nvidia/llama-3.3-nemotron-super-49b-v1.5:free",
                "nvidia/llama-3.3-nemotron-super-49b-v1.5:free",
                "NVIDIA: Llama 3.3 Nemotron Super 49B v1.5 (free)",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                131_072,
                true,
                false,
                0,
            )
        reasoning = ReasoningLevel.entries.last()
        attachments = List(4) { picture(0xFF7AA2F7.toInt() - it * 0x101010) }
        transcript =
            listOf(
                Turn(
                    "user",
                    1,
                    "Use the colours from https://github.com/Mouhand-Kaddo/smallwright/blob/main/" +
                        "docs/validation/demo-recording-a33-20261107.md#colours-and-spacing",
                    listOf(picture(0xFF9ECE6A.toInt())),
                ),
                steps(
                    plan(
                        *Array(20) {
                            (if (it < 7) "[x] " else if (it == 7) "[>] " else "[ ] ") +
                                "Step ${it + 1}: move the allowance totals into their own " +
                                "screen with a weekly summary"
                        },
                    ),
                    *Array(38) {
                        Step(StepKind.CALLED, "Wrote app/src/main/java/ChoreRotation$it.java", "${it * 7} lines")
                    },
                    Step(
                        StepKind.FAILED,
                        "Build failed",
                        "app/src/main/java/dev/smallwright/generated/familychorechart/" +
                            "AllowanceSummaryViewModelFactory.java:212: error: cannot find symbol " +
                            "WeeklyRotationScheduleRepository; did you mean " +
                            "WeeklyRotationScheduleRepositoryImpl? 1 error",
                    ),
                ),
                Turn(
                    "assistant",
                    1,
                    "The build failed in dev.smallwright.generated.familychorechart." +
                        "AllowanceSummaryViewModelFactory.\n1. Rename the class\n2. Build again",
                ),
                Turn("user", 2, "اجعل الأزرار أكبر وأضف وضعاً ليلياً 🌙 مع حفظ العدد بعد إعادة تشغيل الهاتف"),
                Turn("assistant", 2, "👍"),
                Turn(ChatStore.QUESTION, 2, QUESTIONS),
            )
        modelRequest.setText(
            "Also add a widget for the home screen that shows today's chores for each child, " +
                "sorted by who has the most left, and a monthly chart of allowance paid out " +
                "with the totals per child, and make every screen work in Arabic too: " +
                "النص العربي يجب أن يظهر من اليمين إلى اليسار",
        )
    }

private const val QUESTIONS =
    """{"intro":"Before I start, three choices.","questions":[""" +
        """{"question":"Where should the allowance totals live: on the chore chart itself or on a separate screen that a parent opens with the lock code?","recommended":"A separate screen behind the parent lock code","other_choices":["On the chore chart under each child's name","Both, with the chart showing only this week"],"reason":"Children see the chart all day; totals behind the lock keep arguments about money out of the kitchen and are easy to add later"},""" +
        """{"question":"شكل الأزرار؟","recommended":"دائرية","other_choices":["مربعة"]},""" +
        """{"question":"Emoji?","recommended":"✅","other_choices":["❌"]}]}"""

private fun empty(): UiState =
    ready().apply {
        projectPicker.items = emptyList()
        chatPicker.items = emptyList()
    }

private fun huge(): UiState =
    ready().apply {
        projects(41, *Array(80) { "Project ${it + 1}" })
        chats(149, *Array(150) { "Chat ${it + 1}: fix the layout on small phones" })
        transcript =
            List(120) {
                if (it % 2 == 0) Turn("user", it, "Change number ${it / 2 + 1}: make the counter red")
                else Turn("assistant", it, "Done. The counter is red now and the build passed.")
            }
    }

private fun picture(color: Int): Bitmap =
    Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
