package dev.smallwright.controller.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.smallwright.agent.ReasoningLevel
import kotlin.math.floor

/**
 * A square key read out as its control's label: solid like the cursor when it is the next step. It
 * gives a little under the finger.
 */
@Composable
internal fun BlockKey(
    control: Control,
    tone: Color,
    solid: Boolean,
    mark: DrawScope.(Color) -> Unit,
) {
    Rendered(control)
    val color = if (control.isEnabled) tone else Ink.dim.copy(alpha = 0.5f)
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) 0.95f else 1f,
            tween(120, easing = EaseOut),
            label = "press",
        )
    Box(
        Modifier.size(48.dp)
            .clickable(
                presses,
                LocalIndication.current,
                enabled = control.isEnabled,
                role = Role.Button,
            ) {
                control.onClick.run()
            }
            .semantics { contentDescription = control.label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(36.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (solid) Modifier.background(color) else Modifier.border(1.dp, color)),
        ) {
            mark(if (solid) Ink.background else color)
        }
    }
}

internal val ArrowUp: DrawScope.(Color) -> Unit = { color ->
    val stroke = 2.dp.toPx()
    val x = size.width / 2
    val tip = Offset(x, size.height * 0.28f)
    drawLine(color, Offset(x, size.height * 0.72f), tip, stroke, StrokeCap.Square)
    drawLine(
        color,
        Offset(x - size.width * 0.19f, size.height * 0.47f),
        tip,
        stroke,
        StrokeCap.Square,
    )
    drawLine(
        color,
        Offset(x + size.width * 0.19f, size.height * 0.47f),
        tip,
        stroke,
        StrokeCap.Square,
    )
}

internal val StopSquare: DrawScope.(Color) -> Unit = { color ->
    val side = size.width * 0.32f
    drawRect(color, Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side))
}

/** Adding pictures, as a framed hill and sun. */
@Composable
internal fun PictureKey(control: Control) {
    Rendered(control)
    val color = if (control.isEnabled) Ink.prompt else Ink.dim.copy(alpha = 0.5f)
    Box(
        Modifier.size(48.dp)
            .clickable(enabled = control.isEnabled, role = Role.Button) { control.onClick.run() }
            .semantics { contentDescription = control.label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp, 18.dp)) {
            val stroke = 1.5.dp.toPx()
            val inset = stroke / 2
            drawRect(
                color,
                Offset(inset, inset),
                Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke),
            )
            val hill =
                Path().apply {
                    moveTo(stroke * 2, size.height - stroke * 2)
                    lineTo(size.width * 0.38f, size.height * 0.48f)
                    lineTo(size.width * 0.58f, size.height * 0.72f)
                    lineTo(size.width * 0.72f, size.height * 0.58f)
                    lineTo(size.width - stroke * 2, size.height - stroke * 2)
                }
            drawPath(hill, color, style = Stroke(stroke, join = StrokeJoin.Round))
            drawCircle(color, 1.8.dp.toPx(), Offset(size.width * 0.72f, size.height * 0.32f))
        }
    }
}

/**
 * Three rising bars like a signal meter, lit up to the thinking level; off leaves all three dim.
 */
@Composable
internal fun ThinkingMeter(level: ReasoningLevel, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp, 16.dp)) {
        val bar = floor(size.width / 5)
        for (step in 0 until 3) {
            val height = floor(size.height * (step + 1) / 3)
            drawRect(
                if (step < level.ordinal) color else color.copy(alpha = 0.3f),
                Offset(step * bar * 2, size.height - height),
                Size(bar, height),
            )
        }
    }
}
