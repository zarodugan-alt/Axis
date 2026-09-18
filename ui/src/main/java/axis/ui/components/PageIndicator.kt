package axis.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.Motion
import axis.ui.theme.TextTertiary

/**
 * Pager dots: 6dp circles; the active dot expands to an 18dp cyan pill
 * with a [Motion.DefaultDpSpring] width animation.
 */
@Composable
fun PageIndicator(
    count: Int,
    selected: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val active = i == selected
            val width by animateDpAsState(
                if (active) 18.dp else 6.dp, Motion.DefaultDpSpring, label = "pill"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) AccentCyan else TextTertiary.copy(alpha = 0.5f)
                    )
            )
        }
    }
}

@Preview
@Composable
private fun PageIndicatorPreview() {
    AxisTheme { PageIndicator(count = 2, selected = 0) }
}
