package axis.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import axis.ui.R

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium)
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium)
)

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

/** Type styles per spec §2.2. */
object AxisType {
    val Display = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Medium,
        color = TextPrimary
    )
    val Title = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
        color = TextPrimary
    )
    /** Section labels ("NEXT UP") — call sites apply .uppercase(). */
    val Section = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp,
        color = TextSecondary
    )
    val Body = TextStyle(
        fontFamily = Inter,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary
    )
    val BodyStrong = TextStyle(
        fontFamily = Inter,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        color = TextPrimary
    )
    val Caption = TextStyle(
        fontFamily = Inter,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = TextSecondary
    )
    val Telemetry = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = TextSecondary
    )
    val Button = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    )
}
