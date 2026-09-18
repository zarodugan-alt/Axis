package axis.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Danger
import axis.ui.theme.Motion
import axis.ui.theme.TextOnAccent
import axis.ui.theme.TextPrimary
import axis.ui.theme.glass

enum class AxisButtonStyle { PRIMARY, SECONDARY, DANGER }

/**
 * Pill button (56dp): PRIMARY is cyan with dark text (wizard CTAs, confirms),
 * SECONDARY is glass, DANGER is red (STOP / kill switch). Press physics on
 * [Motion.StiffSpring]; disabled fades to 50%.
 */
@Composable
fun AxisButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AxisButtonStyle = AxisButtonStyle.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f, Motion.StiffSpring, label = "btn"
    )
    var mod = modifier
        .height(56.dp)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(50.dp))
    mod = when (style) {
        AxisButtonStyle.PRIMARY -> mod.background(AccentCyan)
        AxisButtonStyle.SECONDARY -> mod.glass(corner = 50.dp)
        AxisButtonStyle.DANGER -> mod.background(Danger)
    }
    val fg = when (style) {
        AxisButtonStyle.PRIMARY -> TextOnAccent
        AxisButtonStyle.SECONDARY -> TextPrimary
        AxisButtonStyle.DANGER -> TextPrimary
    }
    Row(
        modifier = mod
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp)
            )
        }
        Text(text, style = AxisType.Button, color = fg)
    }
}

@Preview
@Composable
private fun AxisButtonPreview() {
    AxisTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AxisButton("Let's go", onClick = {})
            AxisButton("Skip for now", onClick = {}, style = AxisButtonStyle.SECONDARY)
            AxisButton("STOP", onClick = {}, style = AxisButtonStyle.DANGER)
        }
    }
}
