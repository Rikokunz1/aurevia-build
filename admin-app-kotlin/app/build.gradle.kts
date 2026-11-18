plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aurevia.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurevia.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "3.0.0"
    }

    buildFeatures { compose = true }

    // Lint tidak dijalankan: aplikasi internal, dan lintVitalAnalyzeRelease
    // memakan ~250 MB sehingga daemon Gradle dibunuh di mesin 2 GB ini.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Aplikasi internal, dipasang sendiri: ditandatangani debug key supaya
            // bisa dibangun tanpa menyimpan keystore terpisah.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Mesin build ini RAM 2 GB tanpa swap: jangan strip .so, cukup dikemas apa adanya.
android {
    packaging {
        jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" }
    }
}
