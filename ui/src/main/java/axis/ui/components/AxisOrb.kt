package axis.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.Danger
import axis.ui.theme.TextPrimary
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { IDLE, LISTENING, THINKING, ACTING, ERROR }

/**
 * The signature AXIS orb (spec §3.4): 5 canvas layers driven by one infinite
 * clock, with state parameter sets crossfading over 250ms.
 *
 * - Core: breathing radial sphere (red flash on ERROR).
 * - Inner ring: 270° arc + head dot, 12s/rev (fast shimmer arc overlays
 *   when active so the ring visibly "speeds up" without clock restarts).
 * - Outer ring: dashed, counter-rotating, 20s/rev.
 * - Glow halo: breathing alpha, stronger when active.
 * - Particles: 6 slow orbiters (converge inward when LISTENING) + 3 fast
 *   ones when active; [stepPulse] (increment per agent step) fires a burst.
 *
 * Zero per-frame allocations: strokes/dashes are remembered, drawing is
 * pure float math inside scale/rotate transforms.
 */
@Composable
fun AxisOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    stepPulse: Int = 0
) {
    val clock = rememberInfiniteTransition(label = "orb")
    val breath by clock.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val innerAngle by clock.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "inner"
    )
    val outerAngle by clock.animateFloat(
        0f, -360f,
        infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "outer"
    )
    val fastAngle by clock.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "fast"
    )

    val activeW by animateFloatAsState(
        if (state == OrbState.IDLE) 0f else 1f, tween(250), label = "activeW"
    )
    val listenW by animateFloatAsState(
        if (state == OrbState.LISTENING) 1f else 0f, tween(250), label = "listenW"
    )
    val errorW by animateFloatAsState(
        if (state == OrbState.ERROR) 1f else 0f, tween(250), label = "errorW"
    )
    val coreColor by animateColorAsState(
        lerp(AccentCyan, Danger, errorW), tween(250), label = "coreColor"
    )

    val burst = remember { Animatable(0f) }
    LaunchedEffect(stepPulse) {
        if (stepPulse > 0) {
            burst.snapTo(1f)
            burst.animateTo(0f, tween(600))
        }
    }

    val density = LocalDensity.current
    val rPx = remember(size, density) { with(density) { size.toPx() / 2f } }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f) }
    val outerStroke = remember(rPx, dash) {
        Stroke(width = (rPx * 0.028f).coerceAtLeast(2f), pathEffect = dash)
    }
    val innerStroke = remember(rPx) {
        Stroke(width = (rPx * 0.05f).coerceAtLeast(3f), cap = StrokeCap.Round)
    }

    Canvas(modifier = modifier.size(size)) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val scale = breath * (1f + 0.05f * activeW) + burst.value * 0.12f
        val glowA = (0.55f + 0.45f * activeW).coerceIn(0f, 1f)

        // Glow halo (layered solid circles — no gradient brushes, no allocs).
        drawCircle(coreColor.copy(alpha = 0.10f * glowA), radius = r * 1.5f * scale)
        drawCircle(coreColor.copy(alpha = 0.14f * glowA), radius = r * 1.18f * scale)
        drawCircle(coreColor.copy(alpha = 0.18f * glowA), radius = r * 0.88f * scale)

        // Outer dashed ring, counter-rotating.
        rotate(outerAngle, pivot = center) {
            drawCircle(
                color = coreColor.copy(alpha = 0.45f),
                radius = r * 0.92f,
                style = outerStroke
            )
        }

        // Inner ring: 270° arc + head dot, with a fast shimmer arc when active.
        val ir = r * 0.74f
        val arcSize = Size(ir * 2f, ir * 2f)
        val arcTopLeft = Offset(cx - ir, cy - ir)
        rotate(innerAngle, pivot = center) {
            drawArc(
                color = coreColor.copy(alpha = 0.85f),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = innerStroke
            )
        }
        if (activeW > 0.01f) {
            rotate(fastAngle, pivot = center) {
                drawArc(
                    color = coreColor,
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = innerStroke,
                    alpha = activeW * 0.9f
                )
            }
        }
        val headAngle = Math.toRadians((innerAngle + 270f).toDouble())
        drawCircle(
            color = coreColor,
            radius = r * 0.05f,
            center = Offset(
                cx + cos(headAngle).toFloat() * ir,
                cy + sin(headAngle).toFloat() * ir
            )
        )

        // Core sphere + offset specular highlight.
        scale(scale, pivot = center) {
            drawCircle(coreColor.copy(alpha = 0.35f), radius = r * 0.66f)
            drawCircle(coreColor, radius = r * 0.52f)
            drawCircle(
                color = TextPrimary,
                radius = r * 0.16f,
                center = Offset(cx - r * 0.14f, cy - r * 0.16f),
                alpha = 0.9f
            )
        }

        // Particles.
        val pr = r * (0.98f - 0.20f * listenW) + burst.value * r * 0.4f
        for (i in 0 until 6) {
            val a = Math.toRadians((innerAngle * 0.5f + i * 60f).toDouble())
            drawCircle(
                color = coreColor.copy(alpha = 0.75f),
                radius = r * 0.032f,
                center = Offset(cx + cos(a).toFloat() * pr, cy + sin(a).toFloat() * pr)
            )
        }
        if (activeW > 0.01f) {
            for (i in 0 until 3) {
                val a = Math.toRadians((fastAngle + i * 120f).toDouble())
                drawCircle(
                    color = coreColor,
                    radius = r * 0.04f,
                    alpha = activeW,
                    center = Offset(
                        cx + cos(a).toFloat() * pr * 0.86f,
                        cy + sin(a).toFloat() * pr * 0.86f
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun OrbIdlePreview() {
    AxisTheme { AxisOrb(OrbState.IDLE) }
}

@Preview
@Composable
private fun OrbListeningPreview() {
    AxisTheme { AxisOrb(OrbState.LISTENING) }
}
