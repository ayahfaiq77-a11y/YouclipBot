plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.youclipbot.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.youclipbot.android"

        minSdk = 26
        targetSdk = 35

        versionCode = 2
        versionName = "2.1"
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
}

dependencies {

    val composeBom =
        platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)

    // Jetpack Compose
    implementation(
        "androidx.activity:activity-compose:1.10.0"
    )

    implementation(
        "androidx.compose.material3:material3"
    )

    implementation(
        "androidx.compose.ui:ui"
    )

    implementation(
        "androidx.compose.ui:ui-tooling-preview"
    )

    // ML Kit Face Detection
    implementation(
        "com.google.mlkit:face-detection:16.1.7"
    )

    // Supaya ML Kit Task bisa memakai .await()
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
