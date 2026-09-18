package axis.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val AxisColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = TextOnAccent,
    primaryContainer = AccentCyanDim,
    secondary = AccentViolet,
    onSecondary = TextPrimary,
    secondaryContainer = AccentVioletDim,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgElevated,
    onSurface = TextPrimary,
    surfaceVariant = BgSecondary,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = TextPrimary,
    outline = GlassBorder
)

private val AxisMaterialTypography = Typography(
    displayLarge = AxisType.Display,
    displayMedium = AxisType.Display,
    titleLarge = AxisType.Title,
    titleMedium = AxisType.Title,
    bodyLarge = AxisType.Body,
    bodyMedium = AxisType.BodyStrong,
    bodySmall = AxisType.Caption,
    labelLarge = AxisType.Button,
    labelMedium = AxisType.Button,
    labelSmall = AxisType.Caption
)

/**
 * Root theme. Material3 supplies Switch/Slider/Dialog mechanics; every
 * AXIS surface reads the custom tokens ([AxisType], colors, glass) directly.
 * Dark-only by design — there is no light theme.
 */
@Composable
fun AxisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AxisColorScheme,
        typography = AxisMaterialTypography,
        shapes = AxisShapes,
        content = content
    )
}

/**
 * Screen background recipe (spec §2.1): [BgPrimary] fill + radial gradient
 * centered 40% down the screen fading to [BgSecondary] at the edges.
 * (The optional 4% static-noise layer is a P2 asset addition.)
 */
@Composable
fun AxisBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, BgSecondary),
                            center = Offset(w * 0.5f, h * 0.4f),
                            radius = maxOf(w, h) * 0.95f
                        )
                    )
            )
        }
        content()
    }
}
