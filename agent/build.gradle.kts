plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// P3 module — LLM gateway + agent loop (see README.md).
android {
    namespace = "axis.agent"
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
    lint {
        abortOnError = true
        checkOnly += "NewApi"
    }
}

dependencies {
    // Pure-Kotlin module: HTTP + protocol translation + the agent loop.
    // Deliberately has NO Android or Hilt dependencies (no KSP here) so all
    // of the gateway logic is testable on the JVM.
    implementation(project(":kernel"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
