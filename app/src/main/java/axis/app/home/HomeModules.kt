package axis.app.home

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import axis.ui.components.DotStatus
import axis.ui.components.GlassChip
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
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = AxisType.Title.copy(color = TextPrimary),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(caption, style = AxisType.Caption, maxLines = 1)
    }
}

/** Small drawn glyph (vector, ~22dp). */
@Composable
fun Glyph(glyph: ChipGlyph, accent: Color = AccentCyan, size: Int = 22) {
    Canvas(modifier = Modifier.size(size.dp)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.09f)
        when (glyph) {
            ChipGlyph.CORE -> {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(s * 0.18f, s * 0.18f),
                    size = androidx.compose.ui.geometry.Size(s * 0.64f, s * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.12f),
                    style = stroke
                )
                drawCircle(color = accent, radius = s * 0.12f, center = Offset(s / 2, s / 2))
            }
            ChipGlyph.SENSE -> {
                drawCircle(color = accent, radius = s * 0.10f, center = Offset(s / 2, s * 0.72f))
                drawArc(
                    color = accent,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(s * 0.20f, s * 0.26f),
                    size = androidx.compose.ui.geometry.Size(s * 0.60f, s * 0.60f),
                    style = stroke
                )
                drawArc(
                    color = accent.copy(alpha = 0.5f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(s * 0.05f, s * 0.10f),
                    size = androidx.compose.ui.geometry.Size(s * 0.90f, s * 0.90f),
                    style = stroke
                )
            }
            ChipGlyph.LOGIC -> {
                val path = Path().apply {
                    moveTo(s * 0.20f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.70f)
                    lineTo(s * 0.80f, s * 0.70f)
                }
                drawPath(path, accent, style = stroke)
                drawCircle(color = accent, radius = s * 0.08f, center = Offset(s * 0.20f, s * 0.30f))
                drawCircle(color = accent, radius = s * 0.08f, center = Offset(s * 0.80f, s * 0.70f))
            }
            ChipGlyph.SAFE -> {
                val path = Path().apply {
                    moveTo(s * 0.5f, s * 0.12f)
                    lineTo(s * 0.84f, s * 0.28f)
                    lineTo(s * 0.84f, s * 0.56f)
                    quadraticBezierTo(s * 0.84f, s * 0.86f, s * 0.5f, s * 0.92f)
                    quadraticBezierTo(s * 0.16f, s * 0.86f, s * 0.16f, s * 0.56f)
                    lineTo(s * 0.16f, s * 0.28f)
                    close()
                }
                drawPath(path, accent, style = stroke)
            }
            ChipGlyph.VOICE -> {
                drawLine(accent, Offset(s * 0.25f, s * 0.55f), Offset(s * 0.25f, s * 0.45f), s * 0.08f)
                drawLine(accent, Offset(s * 0.42f, s * 0.70f), Offset(s * 0.42f, s * 0.30f), s * 0.08f)
                drawLine(accent, Offset(s * 0.58f, s * 0.80f), Offset(s * 0.58f, s * 0.20f), s * 0.08f)
                drawLine(accent, Offset(s * 0.75f, s * 0.62f), Offset(s * 0.75f, s * 0.38f), s * 0.08f)
            }
            ChipGlyph.VAULT -> {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(s * 0.22f, s * 0.40f),
                    size = androidx.compose.ui.geometry.Size(s * 0.56f, s * 0.46f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
                    style = stroke
                )
                drawArc(
                    color = accent,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(s * 0.32f, s * 0.16f),
                    size = androidx.compose.ui.geometry.Size(s * 0.36f, s * 0.36f),
                    style = stroke
                )
            }
            ChipGlyph.TOOL -> {
                drawLine(accent, Offset(s * 0.24f, s * 0.76f), Offset(s * 0.74f, s * 0.26f), s * 0.14f)
                drawCircle(color = accent, radius = s * 0.13f, center = Offset(s * 0.76f, s * 0.24f), style = stroke)
            }
            ChipGlyph.WAVE -> {
                val path = Path().apply {
                    moveTo(s * 0.10f, s * 0.55f)
                    quadraticBezierTo(s * 0.28f, s * 0.10f, s * 0.46f, s * 0.55f)
                    quadraticBezierTo(s * 0.64f, s * 1.0f, s * 0.90f, s * 0.45f)
                }
                drawPath(path, accent, style = stroke)
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

/** Header band with the AXIS silkscreen text and live clock. */
@Composable
fun BoardHeader(clock: String, status: String, accent: Color = AccentCyan) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph = ChipGlyph.CORE, accent = accent, size = 18)
            Spacer(Modifier.width(8.dp))
            Text("AXIS // CORE", style = AxisType.Telemetry.copy(color = accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, style = AxisType.Telemetry.copy(color = TextSecondary))
            Spacer(Modifier.width(10.dp))
            Text(clock, style = AxisType.Telemetry.copy(color = TextPrimary))
        }
    }
}

/** Small inline pill used for subsystem state in headers. */
@Composable
fun StatePill(text: String, color: Color) {
    Box(modifier = Modifier.glass(corner = 50.dp, borderColor = color.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = AxisType.Caption.copy(color = color))
    }
}

/** Shared accent for dangerous subsystems (kill switch armed, failures). */
internal val DangerAccent = Danger
internal val VioletAccent = AccentViolet
