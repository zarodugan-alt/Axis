package axis.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Motion

/**
 * Launcher app icon (48dp default, 14dp radius): press physics per spec
 * §3.3 (scale 0.97 + rotationZ −1°), notification [badgeCount] pill and
 * new-app dot. Drawables are rasterized once per icon (remembered); the
 * source drawables come from `:app`'s PackageManager LRU cache.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    icon: Drawable?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    badgeCount: Int = 0,
    isNew: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val sizePx = remember(size, density) { with(density) { size.roundToPx() } }
    val bitmap = remember(icon, sizePx) {
        icon?.let { runCatching { it.toStableBitmap(sizePx) }.getOrNull() }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.97f else 1f,
        tween(Motion.MICRO_MS), label = "iconPress"
    )
    val tilt by animateFloatAsState(
        if (pressed && onClick != null) -1f else 0f,
        tween(Motion.MICRO_MS), label = "iconTilt"
    )

    val clickable = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = tilt
            }
            .then(clickable),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(AxisRadii.appIcon))
            )
        } else {
            // Rasterization failed (or no icon): letter tile fallback.
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(AxisRadii.appIcon))
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "?",
                    style = AxisType.Title,
                    color = AccentCyan
                )
            }
        }
        if (badgeCount > 0) {
            BadgeDot(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
            )
        } else if (isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .then(Modifier)
            )
        }
    }
}

/**
 * Rasterizes any launcher drawable (incl. adaptive icons, whose intrinsic
 * size is -1) onto an ARGB bitmap. Draws a defensive copy so the shared
 * cached drawable's bounds are never mutated.
 */
private fun Drawable.toStableBitmap(sizePx: Int): Bitmap {
    val drawable = (constantState?.newDrawable()?.mutate() ?: this)
    val bmp = Bitmap.createBitmap(sizePx.coerceAtLeast(1), sizePx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, bmp.width, bmp.height)
    drawable.draw(android.graphics.Canvas(bmp))
    return bmp
}

@Preview
@Composable
private fun AppIconPreview() {
    AxisTheme { AppIcon(icon = null, label = "Axis", badgeCount = 3) }
}
