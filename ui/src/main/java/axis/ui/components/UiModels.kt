package axis.ui.components

import android.graphics.drawable.Drawable

/**
 * Small UI-owned models. `:ui` stays decoupled from `:kernel` models so the
 * design system can be previewed and reused without engine dependencies;
 * `:app` maps engine models onto these.
 */
data class SuggestionItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)
