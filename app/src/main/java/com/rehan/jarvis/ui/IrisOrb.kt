package com.rehan.jarvis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.rehan.jarvis.core.AssistantState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Jarvis ka chehra — ek zinda glowing orb.
 *
 * @param state assistant abhi kya kar raha hai
 * @param level mic ka live level (0 se 1) — isse asli waveform banta hai
 */
@Composable
fun IrisOrb(
    state: AssistantState,
    level: Float = 0f,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "spin"
    )
    val counterSpin by transition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "counterSpin"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.93f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val ripple by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ripple"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "wavePhase"
    )

    val listening = state == AssistantState.LISTENING
    val speaking = state == AssistantState.SPEAKING

    // Mic level ko smooth karo, warna waveform jhatke maarega
    val smoothLevel by animateFloatAsState(
        targetValue = if (listening) level.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "level"
    )

    val scale by animateFloatAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> 0.88f
            AssistantState.LISTENING -> 1.10f
            AssistantState.THINKING -> 0.98f
            AssistantState.ACTING -> 1.02f
            AssistantState.SPEAKING -> 1.06f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "scale"
    )

    val primary by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> Color(0xFF4F7CFF)
            AssistantState.LISTENING -> Color(0xFF35E1F5)
            AssistantState.THINKING -> Color(0xFFB06CFF)
            AssistantState.ACTING -> Color(0xFFFFB020)
            AssistantState.SPEAKING -> Color(0xFF32E0A8)
        },
        animationSpec = tween(700), label = "primary"
    )
    val secondary by animateColorAsState(
        targetValue = when (state) {
            AssistantState.IDLE -> Color(0xFF8A5CF6)
            AssistantState.LISTENING -> Color(0xFF4F7CFF)
            AssistantState.THINKING -> Color(0xFFFF5FA2)
            AssistantState.ACTING -> Color(0xFFFF7A45)
            AssistantState.SPEAKING -> Color(0xFF35E1F5)
        },
        animationSpec = tween(700), label = "secondary"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = minOf(size.width, size.height) / 2f
            val radius = base * 0.52f * scale * breathe

            // ---- Bahar failti ripple rings ----
            if (listening || speaking) {
                for (i in 0..2) {
                    val p = (ripple + i / 3f) % 1f
                    drawCircle(
                        color = primary.copy(alpha = 0.22f * (1f - p)),
                        radius = radius * (1f + p * 0.85f),
                        center = center,
                        style = Stroke(width = dp(2f))
                    )
                }
            }

            // ---- Bahari glow ----
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = radius * 2.1f
                ),
                radius = radius * 2.1f,
                center = center
            )

            // ---- Andar ke ghoomte huye rang ----
            rotate(spin, center) {
                softBlob(center + Offset(radius * 0.30f, 0f), radius * 0.78f, primary, 0.55f)
                softBlob(
                    center - Offset(radius * 0.26f, radius * 0.18f),
                    radius * 0.70f, secondary, 0.50f
                )
            }
            rotate(counterSpin, center) {
                softBlob(center + Offset(0f, radius * 0.30f), radius * 0.62f, Color.White, 0.10f)
            }

            // ---- Asli voice waveform ----
            if (listening && smoothLevel > 0.02f) {
                drawWaveform(
                    center = center,
                    radius = radius * 1.12f,
                    level = smoothLevel,
                    phase = wavePhase,
                    color = primary
                )
            }

            // ---- Rim (kinare ki chamak) ----
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(primary, secondary, primary),
                    center = center
                ),
                radius = radius,
                center = center,
                style = Stroke(width = dp(2.5f))
            )

            // ---- Kaanch jaisi highlight ----
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    center = center - Offset(radius * 0.35f, radius * 0.40f),
                    radius = radius * 0.85f
                ),
                radius = radius,
                center = center
            )
        }
    }
}

/**
 * Orb ke chaaro taraf awaaz ki lehrein.
 * Har bar ki lambai mic level + ek chalti hui sine wave se banti hai,
 * isliye ye asli awaaz ke saath hilti hai.
 */
private fun DrawScope.drawWaveform(
    center: Offset,
    radius: Float,
    level: Float,
    phase: Float,
    color: Color
) {
    val bars = 72
    val maxLength = radius * 0.42f * level

    for (i in 0 until bars) {
        val angle = (2f * PI.toFloat() / bars) * i

        // Do alag speed ki lehrein mila kar natural dikhta hai
        val wave = sin(angle * 3f + phase) * 0.6f + sin(angle * 7f - phase * 1.6f) * 0.4f
        val length = maxLength * (0.35f + 0.65f * ((wave + 1f) / 2f))

        val inner = Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius
        )
        val outer = Offset(
            center.x + cos(angle) * (radius + length),
            center.y + sin(angle) * (radius + length)
        )

        drawLine(
            color = color.copy(alpha = 0.35f + 0.5f * level),
            start = inner,
            end = outer,
            strokeWidth = dp(2f)
        )
    }
}

/** Halka sa dhundhla rang ka dhabba. */
private fun DrawScope.softBlob(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
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

/** dp ko pixel me badalne ka chhota helper. */
private fun DrawScope.dp(value: Float): Float = value * density
