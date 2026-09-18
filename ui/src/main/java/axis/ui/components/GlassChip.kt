package axis.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import axis.ui.theme.AccentCyan
import axis.ui.theme.AxisTheme
import axis.ui.theme.AxisType
import axis.ui.theme.GlassBorder
import axis.ui.theme.TextPrimary
import axis.ui.theme.TextSecondary
import axis.ui.theme.glass
import axis.ui.theme.glow

/**
 * Pill chip (48dp hit target). Active chips get a bright border + glow;
 * inactive chips are plain glass. Used for mode toggles, routine chips,
 * and (P3) agent action chips.
 */
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    accent: Color = AccentCyan,
    onClick: (() -> Unit)? = null
) {
    val border = if (active) accent.copy(alpha = 0.6f) else GlassBorder
    var chipMod = modifier
        .height(48.dp)
        .glass(corner = 50.dp, borderColor = border)
    if (active) chipMod = chipMod.glow(accent, radius = 12.dp)
    if (onClick != null) {
        chipMod = chipMod.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
    Row(
        modifier = chipMod.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) accent else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp)
            )
        }
        Text(
            text = label,
            style = if (compact) AxisType.Caption else AxisType.Button,
            color = TextPrimary
        )
    }
}

@Preview
@Composable
private fun GlassChipPreview() {
    AxisTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Focus", active = true)
            GlassChip("Sleep")
        }
    }
}
