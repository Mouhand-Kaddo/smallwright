package dev.smallwright.controller.ui

/**
 * One line saying what a group of builder steps did: [done] in plain words, then [failure] when the
 * group ended badly. Either part may be empty; [line] joins them.
 */
data class StepSummary(val done: String, val failure: String) {
    val line: String
        get() = listOf(done, failure).filter { it.isNotEmpty() }.joinToString(", ")
}

/** Counts a group of builder steps by what each one did, from the texts the run gives them. */
object StepSummaries {
    private val BUILDS =
        listOf(
            "Built on this phone",
            "Building on this phone",
            "The build failed",
            "Could not build",
            "Could not save the changes",
            "The manifest was refused",
        )

    private val ADDED_SKILLS = Regex("""Added \d+ skills( for this project)?""")

    private val UNFINISHED =
        listOf(
            "Thinking",
            "Building on this phone",
            "Summarizing earlier steps",
            "Adding library ",
            "Add the library ",
            "Adding the skill ",
            "Adding skills from ",
            "Add the skill ",
            "Add skills from ",
            "Making an app icon",
            "Making a picture",
            "Looking at the app",
            "Reading ",
        )

    /**
     * The summary of [steps]. A group that [ended] says so when its last build failed or its last
     * step went wrong; a group still working never does.
     */
    @JvmStatic
    fun of(steps: List<Step>, ended: Boolean): StepSummary {
        val usedSkills = mutableSetOf<String>()
        val addedSkills = mutableSetOf<String>()
        val readSkills = mutableSetOf<String>()
        var listed = 0
        val read = mutableSetOf<String>()
        var searched = 0
        val changed = mutableSetOf<String>()
        val deleted = mutableSetOf<String>()
        var librarySearches = 0
        var librariesAdded = 0
        var librariesRemoved = 0
        var builds = 0
        var lastBuildFailed = false
        var errorsRead = 0
        var iconMade = false
        var pictures = 0
        var briefWritten = false
        var crashReports = 0
        var looks = 0
        var webSearches = 0
        val pages = mutableSetOf<String>()
        var lookups = 0
        for (step in steps) {
            val text = step.text
            val done = step.kind == StepKind.DONE
            when {
                BUILDS.any { text.startsWith(it) } -> {
                    builds++
                    lastBuildFailed = step.kind == StepKind.FAILED
                }
                !done -> Unit
                text == "Read the build errors" -> errorsRead++
                text == "Made an app icon" -> iconMade = true
                text == "Made a picture" -> pictures++
                text == "Wrote the project brief" -> briefWritten = true
                text == "Read the crash report" -> crashReports++
                text == "Looked at the app" -> looks++
                text == "Searched the web" ->
                    webSearches += step.detail.removeSuffix(" searches").toIntOrNull() ?: 1
                text.startsWith("Looked up ") -> lookups++
                text.startsWith("Read ") && step.detail.startsWith("https://") ->
                    pages += step.detail
                text == "Listed the project's files" -> listed++
                text.startsWith("Searched libraries for ") -> librarySearches++
                text.startsWith("Searched for ") -> searched++
                text.startsWith("Added skill ") ->
                    addedSkills +=
                        text.removePrefix("Added skill ").removeSuffix(" for this project")
                ADDED_SKILLS.matches(text) -> addedSkills += step.detail.split(", ")
                text.startsWith("Using skill ") -> usedSkills += text.removePrefix("Using skill ")
                text.startsWith("Read skill ") ->
                    readSkills += text.removePrefix("Read skill ").substringBefore(", ")
                text.startsWith("Read ") -> read += text.removePrefix("Read ")
                text.startsWith("Created ") -> changed += text.removePrefix("Created ")
                text.startsWith("Wrote ") -> changed += text.removePrefix("Wrote ")
                text.startsWith("Edited ") -> changed += text.removePrefix("Edited ")
                text.startsWith("Deleted ") -> deleted += text.removePrefix("Deleted ")
                text.startsWith("Added library ") || text.startsWith("Changed library ") ->
                    librariesAdded++
                text.startsWith("Removed library ") -> librariesRemoved++
            }
        }
        val parts =
            listOfNotNull(
                "made an app icon".takeIf { iconMade },
                counted("made", pictures, "picture", "pictures"),
                "wrote the project brief".takeIf { briefWritten },
                skills("added", addedSkills),
                skills("used", usedSkills),
                skills("read", readSkills),
                times("read the crash report", crashReports, once = "read the crash report"),
                times("looked at the app", looks, once = "looked at the app"),
                "listed the files".takeIf { listed > 0 },
                counted("read", read.size, "file", "files"),
                times("searched", searched),
                times("searched the web", webSearches),
                counted("read", pages.size, "web page", "web pages"),
                times("looked up code", lookups),
                counted("changed", changed.size, "file", "files"),
                counted("deleted", deleted.size, "file", "files"),
                times("searched for libraries", librarySearches),
                counted("added", librariesAdded, "library", "libraries"),
                counted("removed", librariesRemoved, "library", "libraries"),
                times("built", builds),
                times("read the build errors", errorsRead, once = "read the build errors"),
            )
        val done = capitalized(parts.joinToString(", "))
        val last = steps.lastOrNull()
        val failure =
            when {
                !ended || last == null -> ""
                last.kind == StepKind.FAILED && UNFINISHED.any { last.text.startsWith(it) } ->
                    "did not finish"
                lastBuildFailed -> if (builds == 1) "the build failed" else "the last build failed"
                last.kind != StepKind.FAILED -> ""
                else -> lowered(last.text)
            }
        return when {
            done.isNotEmpty() -> StepSummary(done, failure)
            failure.isNotEmpty() -> StepSummary("", capitalized(failure))
            else -> StepSummary(last?.text.orEmpty(), "")
        }
    }

    private fun counted(verb: String, count: Int, one: String, many: String): String? =
        if (count == 0) null else "$verb $count ${if (count == 1) one else many}"

    private fun skills(verb: String, names: Set<String>): String? =
        when (names.size) {
            0 -> null
            1 -> "$verb skill ${names.first()}"
            else -> "$verb ${names.size} skills"
        }

    private fun times(verb: String, count: Int, once: String = "$verb once"): String? =
        when (count) {
            0 -> null
            1 -> once
            2 -> "$verb twice"
            else -> "$verb $count times"
        }

    private fun capitalized(text: String) = text.replaceFirstChar { it.uppercaseChar() }

    private fun lowered(text: String) =
        if (text.length > 1 && text[1].isUpperCase()) text
        else text.replaceFirstChar { it.lowercaseChar() }
}
