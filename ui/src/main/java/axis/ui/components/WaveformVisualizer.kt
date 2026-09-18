package axis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AccentViolet
import axis.ui.theme.AxisTheme
import androidx.compose.ui.graphics.lerp

/**
 * Voice waveform (spec §3.5): 48 vertical bars, 3dp wide, 2dp gap, centered
 * baseline, cyan→violet gradient by height. Driven by mic RMS in P4 (Voice
 * HUD); TTS playback amplitude while speaking.
 */
@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.height(64.dp).fillMaxWidth()) {
        if (amplitudes.isEmpty()) return@Canvas
        val barW = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val n = amplitudes.size
        val totalW = n * barW + (n - 1) * gap
        var x = (size.width - totalW) / 2f
        val midY = size.height / 2f
        amplitudes.forEach { raw ->
            val a = raw.coerceIn(0f, 1f)
            val h = (a * size.height * 0.9f).coerceAtLeast(4f)
            drawRoundRect(
                color = lerp(AccentCyan, AccentViolet, a),
                topLeft = Offset(x, midY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
            x += barW + gap
        }
    }
}

@Preview
@Composable
private fun WaveformPreview() {
    AxisTheme {
        WaveformVisualizer(
            List(48) { i -> (0.15f + 0.75f * kotlin.math.abs(kotlin.math.sin(i * 0.35f))) }
        )
    }
}
