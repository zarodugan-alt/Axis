package axis.kernel.model

/**
 * UI-facing model for one launchable app. Icons are deliberately NOT here —
 * drawables are served on demand by `:app`'s AppRepository icon cache
 * (avoids leaking Resources into flows and keeps this class unit-testable).
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val launchCount: Long = 0,
    val lastLaunchedAt: Long = 0L,
    val isSystem: Boolean = false
) {
    /** "New app" badge shows until the first launch through AXIS. */
    val isNew: Boolean get() = launchCount == 0L
}
