package axis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AxisTheme
import axis.ui.theme.Success
import axis.ui.theme.TextTertiary
import axis.ui.theme.Warning

enum class DotStatus { ON, DEGRADED, OFF }

/**
 * 8dp status dot. DEGRADED pulses (1800ms alpha cycle); ON/OFF are steady.
 */
@Composable
fun StatusDot(status: DotStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        DotStatus.ON -> Success
        DotStatus.DEGRADED -> Warning
        DotStatus.OFF -> TextTertiary
    }
    if (status == DotStatus.DEGRADED) {
        val clock = rememberInfiniteTransition(label = "dot")
        val alpha by clock.animateFloat(
            1f, 0.35f,
            infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
    } else {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Preview
@Composable
private fun StatusDotPreview() {
    AxisTheme {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            StatusDot(DotStatus.ON)
            StatusDot(DotStatus.DEGRADED)
            StatusDot(DotStatus.OFF)
        }
    }
}
