// Root project — plugin versions live in gradle/libs.versions.toml.
// Each module declares its android{} block explicitly (no build-logic magic)
// so the scaffold stays greppable for newcomers.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
