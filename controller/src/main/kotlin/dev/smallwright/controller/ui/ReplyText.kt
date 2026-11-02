package dev.smallwright.controller.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** One paragraph or list item of a reply. [marker] is empty for a paragraph. */
data class ReplyBlock(val marker: String, val text: AnnotatedString)

/**
 * Reads the small Markdown subset builders write: bold, italic, inline code, bullet and numbered
 * lists and paragraphs. The marks are dropped; anything else stays exactly as written.
 */
object ReplyText {
    private val BULLET = Regex("""^\s*[-*] +(.*)$""")
    private val NUMBERED = Regex("""^\s*(\d{1,3}[.)]) +(.*)$""")
    private val DIVIDER = Regex("""^\s*(?:-{3,}|\*{3,}|_{3,})\s*$""")
    private val CODE = SpanStyle(fontFamily = Mono, color = Ink.prompt, background = Ink.panel)
    private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
    private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)

    @JvmStatic
    fun parse(text: String): List<ReplyBlock> {
        val blocks = mutableListOf<ReplyBlock>()
        val paragraph = mutableListOf<String>()

        fun endParagraph() {
            if (paragraph.isNotEmpty())
                blocks += ReplyBlock("", inline(paragraph.joinToString("\n")))
            paragraph.clear()
        }
        for (line in text.trim().lines()) {
            val bullet = BULLET.matchEntire(line)
            val numbered = NUMBERED.matchEntire(line)
            when {
                line.isBlank() || DIVIDER.matches(line) -> endParagraph()
                bullet != null -> {
                    endParagraph()
                    blocks += ReplyBlock("•", inline(bullet.groupValues[1]))
                }
                numbered != null -> {
                    endParagraph()
                    blocks += ReplyBlock(numbered.groupValues[1], inline(numbered.groupValues[2]))
                }
                else -> paragraph += line
            }
        }
        endParagraph()
        return blocks
    }

    private fun inline(text: String): AnnotatedString = buildAnnotatedString {
        var at = 0
        while (at < text.length) {
            val mark =
                when {
                    text.startsWith("**", at) -> "**"
                    text[at] == '`' -> "`"
                    text[at] == '*' -> "*"
                    else -> null
                }
            val close = mark?.let { text.indexOf(it, at + it.length) } ?: -1
            val inner = if (close < 0) "" else text.substring(at + mark!!.length, close)
            val looseItalic = mark == "*" && (inner.isBlank() || inner.trim() != inner)
            if (mark == null || inner.isEmpty() || '\n' in inner || looseItalic) {
                append(text[at++])
            } else if (mark == "`") {
                withStyle(CODE) { append(inner) }
                at = close + 1
            } else if (mark == "*") {
                withStyle(ITALIC) { append(inline(inner)) }
                at = close + 1
            } else {
                withStyle(BOLD) { append(inline(inner)) }
                at = close + 2
            }
        }
    }
}
