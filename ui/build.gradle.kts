plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "axis.ui"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    lint {
        abortOnError = true
        checkOnly += "NewApi"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// OFL fonts (SIL Open Font License — bundling the TTFs is allowed).
// The binaries are NOT committed; this task fetches the three variable fonts
// from the google/fonts mirror on first build (CI and Android Studio both
// have network). Weights are selected per family via fontVariationSettings
// in src/main/res/font/*.xml (API 26+ supports this; our minSdk is 28).
// Run standalone once to silence IDE "missing font" errors:
//     gradle :ui:downloadFonts
// ---------------------------------------------------------------------------
val fontsDir = layout.projectDirectory.dir("src/main/res/font")
val fontDownloads = mapOf(
    "space_grotesk_var.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf",
    "inter_var.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf",
    "jetbrains_mono_var.ttf" to
        "https://raw.githubusercontent.com/google/fonts/main/ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"
)

val downloadFonts by tasks.registering {
    group = "axis"
    description = "Downloads OFL variable fonts into src/main/res/font (skipped if present)."
    outputs.dir(fontsDir)
    outputs.upToDateWhen {
        fontDownloads.keys.all { fontsDir.asFile.resolve(it).exists() }
    }
    doLast {
        val dir = fontsDir.asFile.apply { mkdirs() }
        fontDownloads.forEach { (name, url) ->
            val out = dir.resolve(name)
            if (out.exists() && out.length() > 0) {
                logger.lifecycle("font already present: $name")
                return@forEach
            }
            logger.lifecycle("downloading font: $name")
            try {
                java.net.URI(url).toURL().openStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                out.delete()
                throw GradleException(
                    "Could not download $name from $url. " +
                        "Check your network connection and retry. (${e.message})"
                )
            }
        }
    }
}

// Fonts must exist before resource merging, or aapt2 fails on the
// font-family XML references.
tasks.named("preBuild") { dependsOn(downloadFonts) }
