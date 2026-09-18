package axis.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisRadii
import axis.ui.theme.AxisSpacing
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.Motion
import axis.ui.theme.TextPrimary
import axis.ui.theme.glass
import axis.ui.theme.glassDanger
import axis.ui.theme.glassShadow
import axis.ui.theme.glassViolet
import androidx.compose.material3.Text

enum class GlassAccent { NONE, CYAN, VIOLET, DANGER }

/**
 * The workhorse surface: glass fill + ambient shadow + press physics
 * (scale to 0.98 on [Motion.StiffSpring]). Tappable only when [onClick]
 * is set; disabled state fades to 50%.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: GlassAccent = GlassAccent.NONE,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    corner: Dp = AxisRadii.card,
    contentPadding: PaddingValues = PaddingValues(AxisSpacing.card),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null && enabled) 0.98f else 1f,
        Motion.StiffSpring,
        label = "press"
    )
    val glassMod = when (accent) {
        GlassAccent.NONE -> Modifier.glass(corner)
        GlassAccent.CYAN -> Modifier.glass(corner, borderColor = AccentCyan.copy(alpha = 0.45f))
        GlassAccent.VIOLET -> Modifier.glassViolet(corner)
        GlassAccent.DANGER -> Modifier.glassDanger(corner)
    }
    val clickable = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null, // press physics replaces ripples on glass
            onClick = onClick
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .glassShadow(corner)
            .then(glassMod)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(clickable)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Preview
@Composable
private fun GlassCardPreview() {
    AxisTheme {
        GlassCard(accent = GlassAccent.CYAN) {
            Text("Glass card", style = AxisType.Title, color = TextPrimary)
        }
    }
}
