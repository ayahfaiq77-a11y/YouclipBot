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

        minSdk = 23
        targetSdk = 35

        versionCode = 4
        versionName = "2.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
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

    // Android / Compose
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(
        platform(
            "androidx.compose:compose-bom:2024.10.01"
        )
    )

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    // Coroutines
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"
    )

    // Dibutuhkan untuk Task.await() pada ML Kit
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0"
    )

    // ML Kit - Face Detection
    implementation(
        "com.google.mlkit:face-detection:16.1.7"
    )

    // ========================================
    // Media3 - YouClipBot v2.4
    // ========================================

    // MediaItem, MimeTypes, dan API dasar Media3
    implementation(
        "androidx.media3:media3-common:1.11.1"
    )

    // Crop, scaling, presentation, dan video effects
    implementation(
        "androidx.media3:media3-effect:1.11.1"
    )

    // Trim + transform + export MP4
    implementation(
        "androidx.media3:media3-transformer:1.11.1"
    )
}
