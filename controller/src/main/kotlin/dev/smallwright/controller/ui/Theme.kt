package dev.smallwright.controller.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.smallwright.controller.R
import kotlinx.coroutines.launch

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

/** Fast at the start, so motion answers the touch at once. For things arriving or leaving. */
internal val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Steep then settling, like a sheet sliding in from a screen edge. */
internal val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** How a folded list opens under the row that was tapped, and folds back into it. */
internal val Reveal =
    expandVertically(tween(200, easing = EaseOut), Alignment.Top) +
        fadeIn(tween(200, easing = EaseOut))
internal val Fold =
    shrinkVertically(tween(150, easing = EaseOut), Alignment.Top) +
        fadeOut(tween(150, easing = EaseOut))

@Composable
fun TerminalTheme(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalIndication provides PressWash, content = content)

private const val PRESSED = 0.12f
private const val FOCUSED = 0.08f

/**
 * Lightens whatever is pressed: at once when the finger lands, fading out over 150 ms as it lifts.
 * Focus and hover leave a fainter wash, for keyboards and mice.
 */
private data object PressWash : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressWashNode(interactionSource)
}

private class PressWashNode(private val interactions: InteractionSource) :
    Modifier.Node(), DrawModifierNode {
    private val wash = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            val held = mutableListOf<Interaction>()
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press,
                    is FocusInteraction.Focus,
                    is HoverInteraction.Enter -> held += interaction
                    is PressInteraction.Release -> held -= interaction.press
                    is PressInteraction.Cancel -> held -= interaction.press
                    is FocusInteraction.Unfocus -> held -= interaction.focus
                    is HoverInteraction.Exit -> held -= interaction.enter
                }
                val target =
                    when {
                        held.any { it is PressInteraction.Press } -> PRESSED
                        held.isNotEmpty() -> FOCUSED
                        else -> 0f
                    }
                launch {
                    if (target >= wash.value) wash.snapTo(target)
                    else wash.animateTo(target, tween(150, easing = EaseOut))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (wash.value > 0f) drawRect(Ink.text, alpha = wash.value)
    }
}
