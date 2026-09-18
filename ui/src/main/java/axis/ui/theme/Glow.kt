package axis.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Two-pass radial glow (spec §2.5). Used on the orb, active chips, the
 * recording mic and the executing HUD border. NEVER on scrolling list
 * items (overdraw cost).
 */
fun Modifier.glow(color: Color, radius: Dp = 24.dp): Modifier = this.drawBehind {
    val r = radius.toPx()
    drawCircle(color.copy(alpha = 0.25f), radius = r * 2.2f)
    drawCircle(color.copy(alpha = 0.12f), radius = r * 3.5f)
}
