// Spike M5 · motor GeckoView con extensiones WebExtension.
// No comparte código con :app a propósito: es una prueba de motor aislada.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.antena.tv.spike"
    // Última GeckoView compatible con el toolchain del proyecto (Kotlin 2.1.20,
    // AGP 8.11.1, compileSdk 35): a partir de la 142 exige Kotlin ≥2.2 y de la
    // 154 androidx.core 1.19 → AGP 9.1 + SDK 37.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.antena.tv.spike"
        // GeckoView moderno exige minSdk 26 (PLAN §18.4: subir de 24 a 26+).
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "spike-2"

        ndk {
            // Cambiar a setOf("arm64-v8a") para medir el APK del TV real.
            abiFilters += setOf("x86_64") // emulador
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Canal release de Mozilla (el más estable para el spike).
    implementation("org.mozilla.geckoview:geckoview:141.0.20250806102122")
}
