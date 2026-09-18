package axis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.TextPrimary
import axis.ui.theme.Warning

data class TelemetryValue(
    val label: String,
    val value: String,
    /** Warning state (e.g. level > 85%) renders the value amber. */
    val warning: Boolean = false
)

/**
 * JetBrains Mono label/value telemetry block (Side-Car SYSTEMS panel).
 */
@Composable
fun TelemetryReadout(
    values: List<TelemetryValue>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { v ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(v.label, style = AxisType.Caption)
                Text(
                    v.value,
                    style = AxisType.Telemetry.copy(
                        color = if (v.warning) Warning else TextPrimary
                    )
                )
            }
        }
    }
}

/**
 * Hand-rolled HUD sparkline (Canvas): normalized polyline + translucent
 * area fill. Deliberately not Vico in P1 — closer to the HUD aesthetic,
 * zero extra dependencies (see README).
 */
@Composable
fun Sparkline(
    data: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = AccentCyan
) {
    Canvas(modifier = modifier.height(40.dp).fillMaxWidth()) {
        if (data.size < 2) return@Canvas
        val min = data.minOrNull() ?: return@Canvas
        val max = data.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val w = size.width
        val h = size.height
        val stepX = w / (data.size - 1)
        val points = data.mapIndexed { i, v ->
            Offset(i * stepX, h - ((v - min) / span) * h * 0.9f - h * 0.05f)
        }
        val fill = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.15f))
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Preview
@Composable
private fun TelemetryPreview() {
    AxisTheme {
        Column {
            TelemetryReadout(
                listOf(
                    TelemetryValue("RAM", "61%"),
                    TelemetryValue("BAT", "67%"),
                    TelemetryValue("DRAIN", "3.1%/h", warning = true)
                )
            )
            Sparkline(listOf(0.2f, 0.4f, 0.35f, 0.6f, 0.55f, 0.8f, 0.7f, 0.9f))
        }
    }
}
