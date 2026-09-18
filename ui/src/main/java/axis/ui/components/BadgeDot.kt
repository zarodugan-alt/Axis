package axis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.TextOnAccent

/**
 * Notification count pill (cyan). Appears with a [Motion.BounceSpring]
 * scale pop; hidden when [count] is zero. Counts arrive from the
 * NotificationListener in P2 — P1 shows it only in previews/fallbacks.
 */
@Composable
fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = count > 0,
        modifier = modifier,
        enter = scaleIn(animationSpec = Motion.BounceSpring, initialScale = 0f) + fadeIn(),
        exit = scaleOut(animationSpec = Motion.BounceSpring) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .clip(CircleShape)
                .background(AccentCyan)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = AxisType.Caption.copy(color = TextOnAccent, fontSize = 11.sp)
            )
        }
    }
}

@Preview
@Composable
private fun BadgeDotPreview() {
    AxisTheme { BadgeDot(count = 3) }
}
