package dev.smallwright.controller.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.smallwright.controller.R

/** The terminal palette. Every hue carries one meaning; nothing is decorative. */
object Ink {
    val background = Color(0xFF1A1B26)
    val panel = Color(0xFF24283B)
    val rule = Color(0xFF636C99)
    val text = Color(0xFFC0CAF5)
    val dim = Color(0xFF8A93BE)
    val prompt = Color(0xFF7AA2F7)
    val done = Color(0xFF9ECE6A)
    val waiting = Color(0xFFE0AF68)
    val failed = Color(0xFFF7768E)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun mono(weight: FontWeight) =
    Font(
        resId = R.font.cascadia_mono,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val Mono = FontFamily(mono(FontWeight.Normal), mono(FontWeight.SemiBold), mono(FontWeight.Bold))

/** One monospace family at five sizes; line height about 1.5 for reading, 1.2 for display. */
object Type {
    val small = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp)
    val body = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 20.sp)
    val action =
        TextStyle(
            fontFamily = Mono,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    val title =
        TextStyle(
            fontFamily = Mono,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
    val display =
        TextStyle(
            fontFamily = Mono,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
        )
}

@Composable fun TerminalTheme(content: @Composable () -> Unit) = content()
