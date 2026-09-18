package axis.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Radii per spec §2.3. */
object AxisRadii {
    val card = 20.dp
    val small = 16.dp
    val field = 16.dp
    val sheetTop = 28.dp
    val appIcon = 14.dp
}

/** 4dp-grid spacing per spec §2.3. */
object AxisSpacing {
    val screen = 20.dp
    val card = 16.dp
    val cardGap = 12.dp
    val section = 24.dp
    val xs = 4.dp
    val sm = 8.dp
}

internal val AxisShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(AxisRadii.appIcon),
    small = RoundedCornerShape(AxisRadii.field),
    medium = RoundedCornerShape(AxisRadii.small),
    large = RoundedCornerShape(AxisRadii.card),
    extraLarge = RoundedCornerShape(AxisRadii.card)
)
