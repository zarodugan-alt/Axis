plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// P3 module — stub in P1 (see README.md in this directory).
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
    implementation(project(":kernel"))
    implementation(libs.kotlinx.coroutines.android)
}
