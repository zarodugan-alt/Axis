package axis.ui.circuit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.BgPrimary
import axis.ui.theme.BgSecondary
import axis.ui.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The PCB substrate: a circuit-board texture drawn procedurally (traces,
 * pads, vias, silkscreen grid) plus animated "current" pulses travelling
 * along the trace bus. Everything is Canvas — no bitmaps — so it stays crisp
 * at any density and costs one draw pass.
 *
 * This is deliberately the *home* identity: AXIS is a machine, and the home
 * screen is its board rather than a grid of someone else's app icons.
 */
@Composable
fun CircuitBoard(
    modifier: Modifier = Modifier,
    pulseCount: Int = 7,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit
) {
    val clock = rememberInfiniteTransition(label = "board")
    val phase by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBoard(phase, pulseCount, intensity)
        }
        content()
    }
}

private fun DrawScope.drawBoard(phase: Float, pulseCount: Int, intensity: Float) {
    val w = size.width
    val h = size.height

    // Substrate: faint diagonal weave (fibreglass) + cooler edge vignette.
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(BgPrimary, BgSecondary, BgPrimary),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
    )

    val grid = 34f
    var x = 0f
    while (x < w) {
        drawLine(
            color = AccentCyan.copy(alpha = 0.030f * intensity),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1f
        )
        x += grid
    }
    var y = 0f
    while (y < h) {
        drawLine(
            color = AccentCyan.copy(alpha = 0.030f * intensity),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f
        )
        y += grid
    }

    // A grounded copper pour down the left bus and along the bottom.
    val busX = w * 0.075f
    val busY = h * 0.90f
    drawLine(
        color = AccentCyan.copy(alpha = 0.10f * intensity),
        start = Offset(busX, h * 0.06f),
        end = Offset(busX, busY),
        strokeWidth = 3f
    )
    drawLine(
        color = AccentCyan.copy(alpha = 0.10f * intensity),
        start = Offset(busX, busY),
        end = Offset(w * 0.94f, busY),
        strokeWidth = 3f
    )

    // Trace runs: orthogonal, 45° cornered, in the board's two copper colours.
    val runs = traceRuns(w, h)
    runs.forEachIndexed { index, run ->
        val color = if (index % 3 == 0) AccentViolet else AccentCyan
        val path = Path().apply {
            moveTo(run.start.x, run.start.y)
            var cur = run.start
            run.corners.forEach { c ->
                // 45-degree dog-leg: go halfway on X, then cut the corner.
                val midX = (cur.x + c.x) / 2f
                lineTo(midX, cur.y)
                lineTo(c.x, c.y)
                cur = c
            }
            lineTo(run.end.x, run.end.y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.16f * intensity),
            style = Stroke(width = 2.4f)
        )
    }

    // Vias: small rings at trace junctions.
    runs.forEach { run ->
        (run.corners + run.start + run.end).forEach { p ->
            drawCircle(color = BgPrimary, radius = 5.5f, center = p)
            drawCircle(
                color = AccentCyan.copy(alpha = 0.28f * intensity),
                radius = 5.5f,
                center = p,
                style = Stroke(width = 1.6f)
            )
        }
    }

    // Solder pads along the bus (the classic pin header look).
    var padY = h * 0.10f
    while (padY < busY - 20f) {
        drawRoundRect(
            color = AccentCyan.copy(alpha = 0.22f * intensity),
            topLeft = Offset(busX - 9f, padY),
            size = Size(18f, 8f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )
        padY += 46f
    }

    // Animated current: bright dots running the full bus loop.
    for (i in 0 until pulseCount) {
        val offset = (phase + i.toFloat() / pulseCount) % 1f
        val travelled = offset * (busY - h * 0.06f)
        val dotY = h * 0.06f + travelled
        drawCircle(
            color = AccentCyan.copy(alpha = 0.85f * intensity),
            radius = 2.6f,
            center = Offset(busX, dotY)
        )
        drawCircle(
            color = AccentCyan.copy(alpha = 0.16f * intensity),
            radius = 9f,
            center = Offset(busX, dotY)
        )
    }

    // Silkscreen legend, bottom-right: board name + revision.
    drawLine(
        color = TextTertiary.copy(alpha = 0.35f),
        start = Offset(w * 0.62f, h - 14f),
        end = Offset(w * 0.93f, h - 14f),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))
    )

    // Corner registration marks.
    listOf(
        Offset(18f, 18f), Offset(w - 18f, 18f),
        Offset(18f, h - 18f), Offset(w - 18f, h - 18f)
    ).forEach { c ->
        drawCircle(color = AccentCyan.copy(alpha = 0.35f), radius = 2.5f, center = c)
        drawCircle(
            color = AccentCyan.copy(alpha = 0.18f),
            radius = 8f,
            center = c,
            style = Stroke(width = 1f)
        )
    }

    // A faint scanline sweep so the board feels powered, not printed.
    val sweepY = ((phase * 1.6f) % 1f) * h
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, AccentCyan.copy(alpha = 0.05f), Color.Transparent),
            startY = sweepY - 60f,
            endY = sweepY + 60f
        )
    )
}

private data class TraceRun(val start: Offset, val corners: List<Offset>, val end: Offset)

/** Deterministic trace geometry: same board every launch, no RNG churn. */
private fun traceRuns(w: Float, h: Float): List<TraceRun> = listOf(
    TraceRun(
        Offset(w * 0.075f, h * 0.16f),
        listOf(Offset(w * 0.20f, h * 0.16f), Offset(w * 0.20f, h * 0.30f)),
        Offset(w * 0.42f, h * 0.30f)
    ),
    TraceRun(
        Offset(w * 0.075f, h * 0.40f),
        listOf(Offset(w * 0.30f, h * 0.40f), Offset(w * 0.30f, h * 0.52f)),
        Offset(w * 0.52f, h * 0.52f)
    ),
    TraceRun(
        Offset(w * 0.075f, h * 0.64f),
        listOf(Offset(w * 0.24f, h * 0.64f), Offset(w * 0.24f, h * 0.74f)),
        Offset(w * 0.46f, h * 0.74f)
    ),
    TraceRun(
        Offset(w * 0.93f, h * 0.20f),
        listOf(Offset(w * 0.78f, h * 0.20f), Offset(w * 0.78f, h * 0.36f)),
        Offset(w * 0.58f, h * 0.36f)
    ),
    TraceRun(
        Offset(w * 0.93f, h * 0.48f),
        listOf(Offset(w * 0.72f, h * 0.48f), Offset(w * 0.72f, h * 0.60f)),
        Offset(w * 0.54f, h * 0.60f)
    ),
    TraceRun(
        Offset(w * 0.93f, h * 0.72f),
        listOf(Offset(w * 0.80f, h * 0.72f), Offset(w * 0.80f, h * 0.82f)),
        Offset(w * 0.60f, h * 0.82f)
    )
)

/**
 * Sine ripple used by the HUD waveform strip: [samples] values in 0..1,
 * amplitude scaled by [level]. Real data (audio level, notification rate)
 * drives [level], the shape is cosmetic.
 */
fun waveformSamples(samples: Int, level: Float, timeMs: Float): List<Float> =
    List(samples) { i ->
        val t = i.toFloat() / samples
        val a = sin((t * 6.28318f * 2f) + timeMs / 260f)
        val b = sin((t * 6.28318f * 5f) + timeMs / 130f) * 0.4f
        val envelope = 1f - abs(t - 0.5f) * 1.4f
        (((a + b) * 0.5f * envelope) + 0.5f) * level.coerceIn(0f, 1f)
    }

/** Distance helper used by the home board for trace hit-testing. */
internal fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)
