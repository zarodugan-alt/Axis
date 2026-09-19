package axis.app.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import axis.ui.components.DotStatus
import axis.ui.components.StatusDot
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Success
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.Warning
import axis.ui.theme.glass
import axis.ui.theme.glow

/** Glyph vocabulary for the board — drawn, not icon-fonted, to match the PCB. */
enum class ChipGlyph { CORE, SENSE, LOGIC, SAFE, VOICE, VAULT, TOOL, WAVE }

/**
 * A subsystem module on the home board. Not an app icon: these are AXIS's own
 * capabilities, each reporting live state, each with a solder-pad header and
 * a status LED.
 */
@Composable
fun CircuitModule(
    label: String,
    value: String,
    caption: String,
    glyph: ChipGlyph,
    status: DotStatus,
    accent: Color = AccentCyan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .glass(corner = AxisRadii.card, borderColor = accent.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        // Silkscreen header: label + LED
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status)
            Spacer(Modifier.width(6.dp))
            Text(label, style = AxisType.Caption.copy(color = TextSecondary))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph = glyph, accent = accent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    value,
                    style = AxisType.Title.copy(color = TextPrimary),
                    maxLines = 1
                )
                Text(
                    caption,
                    style = AxisType.Telemetry.copy(color = accent.copy(alpha = 0.85f)),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TraceStub(accent)
    }
}

/** A short circuit trace with a pad at the end — the board motif. */
@Composable
fun TraceStub(accent: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val y = size.height / 2f
        drawLine(
            color = accent.copy(alpha = 0.55f),
            start = Offset(0f, y),
            end = Offset(size.width * 0.72f, y),
            strokeWidth = 2f
        )
        drawCircle(color = accent.copy(alpha = 0.85f), radius = 3.5f, center = Offset(size.width * 0.78f, y))
        drawLine(
            color = accent.copy(alpha = 0.35f),
            start = Offset(size.width * 0.84f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5f
        )
    }
}

/**
 * Hand-drawn glyph set. Drawing them (instead of an icon font) keeps the
 * board's line weight consistent with the traces and avoids the 2000-icon
 * dependency on the home path.
 */
@Composable
fun Glyph(glyph: ChipGlyph, accent: Color = AccentCyan, size: Int = 22) {
    val pulse by rememberInfiniteTransition(label = "glyph").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.085f)
        val c = Offset(w / 2f, h / 2f)
        when (glyph) {
            ChipGlyph.CORE -> {
                drawRect(
                    color = accent,
                    topLeft = Offset(w * 0.22f, h * 0.22f),
                    size = Size(w * 0.56f, h * 0.56f),
                    style = stroke
                )
                drawCircle(color = accent.copy(alpha = pulse), radius = w * 0.12f, center = c)
                for (i in 0..2) {
                    val off = w * (0.3f + i * 0.2f)
                    drawLine(accent, Offset(off, 0f), Offset(off, h * 0.2f), strokeWidth = w * 0.06f)
                    drawLine(accent, Offset(off, h * 0.8f), Offset(off, h), strokeWidth = w * 0.06f)
                }
            }
            ChipGlyph.SENSE -> {
                drawArc(
                    color = accent,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.05f, h * 0.28f),
                    size = Size(w * 0.9f, h * 0.9f),
                    style = stroke
                )
                drawArc(
                    color = accent.copy(alpha = 0.7f),
                    startAngle = 220f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.42f),
                    size = Size(w * 0.56f, h * 0.62f),
                    style = stroke
                )
                drawCircle(color = accent, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.78f))
            }
            ChipGlyph.LOGIC -> {
                drawLine(accent, Offset(0f, h * 0.5f), Offset(w * 0.28f, h * 0.5f), strokeWidth = w * 0.08f)
                drawRect(
                    color = accent,
                    topLeft = Offset(w * 0.28f, h * 0.24f),
                    size = Size(w * 0.44f, h * 0.52f),
                    style = stroke
                )
                drawLine(accent, Offset(w * 0.72f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = w * 0.08f)
                drawLine(
                    accent.copy(alpha = 0.7f),
                    Offset(w * 0.44f, h * 0.38f),
                    Offset(w * 0.44f, h * 0.62f),
                    strokeWidth = w * 0.06f
                )
            }
            ChipGlyph.SAFE -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.12f)
                    lineTo(w * 0.86f, h * 0.3f)
                    lineTo(w * 0.86f, h * 0.55f)
                    quadraticBezierTo(w * 0.86f, h * 0.86f, w * 0.5f, h * 0.94f)
                    quadraticBezierTo(w * 0.14f, h * 0.86f, w * 0.14f, h * 0.55f)
                    lineTo(w * 0.14f, h * 0.3f)
                    close()
                }
                drawPath(path, color = accent, style = stroke)
                drawCircle(color = accent.copy(alpha = pulse), radius = w * 0.08f, center = c)
            }
            ChipGlyph.VOICE -> {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(w * 0.36f, h * 0.1f),
                    size = Size(w * 0.28f, h * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
                    style = stroke
                )
                drawArc(
                    color = accent,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.2f, h * 0.3f),
                    size = Size(w * 0.6f, h * 0.5f),
                    style = stroke
                )
                drawLine(accent, Offset(w * 0.5f, h * 0.8f), Offset(w * 0.5f, h * 0.94f), strokeWidth = w * 0.08f)
            }
            ChipGlyph.VAULT -> {
                drawRect(
                    color = accent,
                    topLeft = Offset(w * 0.18f, h * 0.34f),
                    size = Size(w * 0.64f, h * 0.5f),
                    style = stroke
                )
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.32f, h * 0.1f),
                    size = Size(w * 0.36f, h * 0.4f),
                    style = stroke
                )
                drawCircle(color = accent.copy(alpha = pulse), radius = w * 0.07f, center = c)
            }
            ChipGlyph.TOOL -> {
                drawLine(accent, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.8f, h * 0.2f), strokeWidth = w * 0.12f)
                drawCircle(color = accent, radius = w * 0.14f, center = Offset(w * 0.78f, h * 0.22f), style = stroke)
                drawCircle(color = accent, radius = w * 0.14f, center = Offset(w * 0.22f, h * 0.78f), style = stroke)
            }
            ChipGlyph.WAVE -> {
                val path = Path().apply {
                    moveTo(0f, h * 0.5f)
                    cubicTo(w * 0.2f, h * 0.05f, w * 0.3f, h * 0.95f, w * 0.5f, h * 0.5f)
                    cubicTo(w * 0.7f, h * 0.05f, w * 0.8f, h * 0.95f, w, h * 0.5f)
                }
                drawPath(path, color = accent, style = stroke)
            }
        }
    }
}

/**
 * A tool switch on the bottom rail: a physical-looking toggle with an LED,
 * the current state as text, and a tap action. Deliberately chunky (48dp+)
 * because it is reachable one-handed from the bottom of a launcher.
 */
@Composable
fun ToolTile(
    label: String,
    stateLabel: String,
    on: Boolean,
    glyph: ChipGlyph,
    enabled: Boolean = true,
    warn: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = when {
        !enabled -> TextSecondary
        warn -> Warning
        on -> Success
        else -> AccentCyan
    }
    Column(
        modifier = modifier
            .glass(corner = 14.dp, borderColor = accent.copy(alpha = if (on) 0.6f else 0.28f))
            .let { if (on) it.glow(accent, radius = 10.dp) else it }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Glyph(glyph = glyph, accent = accent, size = 20)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = AxisType.Caption.copy(color = if (enabled) TextPrimary else TextSecondary),
            maxLines = 1
        )
        Text(
            stateLabel,
            style = AxisType.Telemetry.copy(color = accent),
            maxLines = 1
        )
    }
}

/** Status strip row: label left, mono value right (silkscreen style). */
@Composable
fun HudRow(label: String, value: String, accent: Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AxisType.Caption)
        Text(value, style = AxisType.Telemetry.copy(color = accent))
    }
}

/**
 * The board's top rail: AXIS identity, live clock, and a status string
 * (network · battery · charging). Silkscreen everywhere.
 */
@Composable
fun BoardHeader(clock: String, status: String, accent: Color = AccentCyan) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AXIS",
                    style = AxisType.Telemetry.copy(color = accent)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .glow(accent, radius = 6.dp)
                ) {
                    Canvas(Modifier.size(6.dp)) {
                        drawCircle(color = accent, radius = size.minDimension / 2f)
                    }
                }
            }
            Text(status, style = AxisType.Caption.copy(color = TextSecondary))
        }
        Text(clock, style = AxisType.Telemetry.copy(color = TextPrimary))
    }
}

/** Small status pill used by the telemetry strip (LED + label). */
@Composable
fun StatePill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .glass(corner = 50.dp, borderColor = color.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(6.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = AxisType.Telemetry.copy(color = color))
    }
}

/** Accent for the kill-switch state, shared by the board sections. */
internal fun killAccent(killSwitch: Boolean): Color = if (killSwitch) Danger else AccentCyan

/** Violet accent for automation surfaces. */
internal val AutomationAccent: Color = AccentViolet
