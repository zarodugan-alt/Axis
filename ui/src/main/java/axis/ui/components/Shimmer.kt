package axis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyanDim
import axis.ui.theme.BgElevated
import axis.ui.theme.Motion
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Diagonal shimmer sweep (spec §3.6): 1200ms loop of [AccentCyanDim]
 * over [BgElevated]. Caller supplies size via [modifier].
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val clock = rememberInfiniteTransition(label = "shimmer")
    val x by clock.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(Motion.SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val brush = Brush.linearGradient(
                    colors = listOf(BgElevated, AccentCyanDim, BgElevated),
                    start = Offset(x * (w + h) - h, h),
                    end = Offset(x * (w + h), 0f)
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}
