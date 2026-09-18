package axis.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Motion tokens (spec §3.1). Rule: springs for ALL spatial motion;
 * linear/tween only for progress bars, shimmer and infinite pulses.
 */
object Motion {
    val DefaultSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
    val BounceSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 400f)
    val StiffSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 600f)

    /** Same tuning as [DefaultSpring] for Dp-typed animations (pills, offsets). */
    val DefaultDpSpring = spring<Dp>(dampingRatio = 0.8f, stiffness = 300f)

    const val MICRO_MS = 150
    const val STAGGER_MS = 30
    const val SHIMMER_MS = 1200
}
