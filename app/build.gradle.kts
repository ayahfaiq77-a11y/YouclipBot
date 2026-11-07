plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.youclipbot.android"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.youclipbot.android"

        minSdk = 26
        targetSdk = 35

        versionCode = 3
        versionName = "2.3"
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

    // Jetpack Compose BOM
    val composeBom =
        platform(
            "androidx.compose:compose-bom:2024.12.01"
        )

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

    // Coroutine support untuk ML Kit Task.await()
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1"
    )

    // Media3
    val media3Version = "1.11.1"

    implementation(
        "androidx.media3:media3-common:$media3Version"
    )

    implementation(
        "androidx.media3:media3-effect:$media3Version"
    )

    implementation(
        "androidx.media3:media3-transformer:$media3Version"
    )

    // Compose debug tools
    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
