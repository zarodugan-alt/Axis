package axis.app.nav

/** Navigation routes (spec Part 4). Stack destinations build out phase by phase. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val ROOT = "root"

    const val CHAT = "chat" // P3
    const val ROUTINES = "routine_gallery" // P4
    const val ROUTINE_DETAIL = "routine_detail/{id}" // P4
    fun routineDetail(id: String) = "routine_detail/$id"

    const val SETTINGS = "settings"
    const val SETTINGS_PROVIDERS = "settings/providers"
    const val SETTINGS_PROVIDER_DETAIL = "settings/providers/{id}"
    fun providerDetail(id: String) = "settings/providers/$id"

    const val SETTINGS_APPEARANCE = "settings/appearance" // P1 (real)
    const val SETTINGS_AUTOMATIONS = "settings/automations" // P3
    const val SETTINGS_NOTIFICATIONS = "settings/notifications" // P2
    const val SETTINGS_VOICE = "settings/voice" // P4
    const val SETTINGS_SAFETY = "settings/safety" // P3
    const val SETTINGS_USAGE = "settings/usage" // P4
    const val SETTINGS_ADVANCED = "settings/advanced" // P4
    const val SETTINGS_ABOUT = "settings/about" // P2
    const val HIDDEN_APPS = "settings/appearance/hidden" // P1 (real)

    const val FLOW_STUDIO = "flow_studio" // P4
}
