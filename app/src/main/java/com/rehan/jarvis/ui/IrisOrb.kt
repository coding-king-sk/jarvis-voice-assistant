package com.rehan.jarvis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.rehan.jarvis.core.AssistantState

/**
 * Iris jaisa jeeta-jaagta orb.
 *
 * Har state ka apna rang aur apni chaal hai:
 *  IDLE      - dheere saans leta neela orb
 *  LISTENING - chamakta cyan, bahar ripple rings
 *  THINKING  - purple, tezi se ghoomta hua
 *  ACTING    - amber
 *  SPEAKING  - teal, bolne jaisi dhadkan
 */
@Composable
fun IrisOrb(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "iris")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "spin"
    )
    val counterSpin by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "counterSpin"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ripple"
    )

    // State ke hisaab se size
    val targetScale = when (state) {
        AssistantState.IDLE -> 0.88f
        AssistantState.LISTENING -> 1.10f
        AssistantState.THINKING -> 0.98f
        AssistantState.ACTING -> 1.02f
        AssistantState.SPEAKING -> 1.06f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "scale"
    )

    // State ke hisaab se rang
    val primaryTarget = when (state) {
        AssistantState.IDLE -> Color(0xFF4F7CFF)
        AssistantState.LISTENING -> Color(0xFF35E1F5)
        AssistantState.THINKING -> Color(0xFFB06CFF)
        AssistantState.ACTING -> Color(0xFFFFB020)
        AssistantState.SPEAKING -> Color(0xFF32E0A8)
    }
    val secondaryTarget = when (state) {
        AssistantState.IDLE -> Color(0xFF8A5CF6)
        AssistantState.LISTENING -> Color(0xFF4F7CFF)
        AssistantState.THINKING -> Color(0xFFFF5FA2)
        AssistantState.ACTING -> Color(0xFFFF7A45)
        AssistantState.SPEAKING -> Color(0xFF35E1F5)
    }

    val primary by animateColorAsState(primaryTarget, tween(700), label = "primary")
    val secondary by animateColorAsState(secondaryTarget, tween(700), label = "secondary")

    val showRipples = state == AssistantState.LISTENING || state == AssistantState.SPEAKING

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = (size.minDimension / 2f) * 0.52f * scale * breathe

            // 1. Bahar ki halki roshni
            softBlob(c, r * 2.4f, primary, 0.20f)
            softBlob(c, r * 1.7f, secondary, 0.18f)

            // 2. Ripple rings (sunte aur bolte waqt)
            if (showRipples) {
                for (i in 0 until 3) {
                    val p = (ripple + i / 3f) % 1f
                    drawCircle(
                        color = primary.copy(alpha = (1f - p) * 0.30f),
                        radius = r * (1f + p * 1.1f),
                        center = c,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 3. Orb ka core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        primary,
                        secondary.copy(alpha = 0.9f)
                    ),
                    center = Offset(c.x - r * 0.32f, c.y - r * 0.38f),
                    radius = r * 1.8f
                ),
                radius = r,
                center = c
            )

            // 4. Andar ghoomte hue rang ke dhabbe
            rotate(spin, c) {
                softBlob(Offset(c.x + r * 0.34f, c.y + r * 0.10f), r * 0.72f, secondary, 0.75f)
            }
            rotate(counterSpin, c) {
                softBlob(Offset(c.x - r * 0.30f, c.y + r * 0.24f), r * 0.62f, primary, 0.70f)
            }
            rotate(spin * 0.5f, c) {
                softBlob(Offset(c.x, c.y - r * 0.36f), r * 0.50f, Color.White, 0.35f)
            }

            // 5. Kinare ki chamak
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.55f),
                        primary.copy(alpha = 0.10f),
                        secondary.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.55f)
                    ),
                    center = c
                ),
                radius = r,
                center = c,
                style = Stroke(width = 1.2.dp.toPx())
            )

            // 6. Upar ki highlight (kaanch jaisa look)
            softBlob(
                Offset(c.x - r * 0.34f, c.y - r * 0.42f),
                r * 0.42f,
                Color.White,
                0.55f
            )
        }
    }
}

/** Kinare se ghulta hua rang ka gola. */
private fun DrawScope.softBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
