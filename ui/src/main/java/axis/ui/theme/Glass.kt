package axis.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Faked glass (spec §2.4): gradient fill + 1dp tinted border. Real blur via
 * RenderEffect is API 31+ and banned on our minSdk 28 — this is the only
 * sanctioned glass recipe, so every glass surface stays consistent.
 */
fun Modifier.glass(
    corner: Dp = AxisRadii.card,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(GlassFillTop, GlassFillBottom)))
        .border(borderWidth, borderColor, shape)
}

/** Violet glass: automation/routine elements, user chat bubbles (P3). */
fun Modifier.glassViolet(corner: Dp = AxisRadii.card): Modifier =
    glass(corner = corner, borderColor = GlassBorderViolet)

/** Danger glass: kill switch, destructive confirms, error states. */
fun Modifier.glassDanger(corner: Dp = AxisRadii.card): Modifier =
    glass(corner = corner, borderColor = GlassBorderDanger)

/** Success glass: positive confirmations, success states. */
fun Modifier.glassSuccess(corner: Dp = AxisRadii.card): Modifier =
    glass(corner = corner, borderColor = GlassBorderSuccess)

/** Warning glass: caution states, warnings. */
fun Modifier.glassWarning(corner: Dp = AxisRadii.card): Modifier =
    glass(corner = corner, borderColor = GlassBorderWarning)

/** Solid glass: dialogs/sheets over busy backgrounds (legibility first). */
fun Modifier.glassSolid(corner: Dp = AxisRadii.card): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(BgElevated)
        .border(1.dp, GlassBorder, shape)
}

/** Ambient drop shadow for glass cards: 8dp, 20% black. */
fun Modifier.glassShadow(corner: Dp = AxisRadii.card, elevation: Dp = 8.dp): Modifier =
    this.shadow(elevation, RoundedCornerShape(corner), ambientColor = Color.Black.copy(alpha = 0.2f))
