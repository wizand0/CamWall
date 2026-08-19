plugins {
    alias(libs.plugins.android.application)
//    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.wizand.camwall"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.wizand.camwall"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15" // Match the version in libs.versions.toml
    }
    
    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // ConstraintLayout for Compose
    implementation(libs.androidx.constraintlayout)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security: EncryptedSharedPreferences для RTSP-URL (этап 2)
    implementation(libs.androidx.security.crypto)
    
    // Media3: используется только common/UI; RTSP-захват идёт через FFmpegKit
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)

    // FFmpegKit: захват кадров из RTSP (media3-exoplayer-rtsp не подходит,
    // см. комментарий в RtspFrameCapture)
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // CameraX + ML Kit: сканирование QR-кодов при добавлении камеры (этап A)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // JSON
    implementation(libs.gson)
    

    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    
    // Preview tools
//    debugImplementation("libs.androidx.compose.ui.tooling:1.12.0")
    implementation("androidx.compose.ui:ui-tooling:1.12.0")

}