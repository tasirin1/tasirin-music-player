plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** Nomor build: run number CI (monotonik), fallback 1 untuk lokal. */
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.tasirin.musicplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tasirin.musicplayer"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0"
        // Hanya butuh bahasa Indonesia (+ Inggris cadangan): resource locale lain dibuang.
        resConfigs("id", "en")
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "android"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            // R8: buang kode mati & resource tak terpakai (APK sekecil mungkin)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs {
            // Kompres .so di dalam APK (lebih kecil untuk diunduh; diekstrak saat instal).
            useLegacyPackaging = true
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose Material3 (BOM 2024.06.00 = UI 1.6.8 + Material3 1.2.1)
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // MediaSession + notifikasi media (MediaStyle)
    implementation("androidx.media:media:1.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
