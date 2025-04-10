plugins {
    // Use aliases from libs.versions.toml
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
//    alias(libs.plugins.kotlin.kapt)
    // KSP or kapt for Room Compiler if needed
    // id("com.google.devtools.ksp") version "..."
}

android {
    namespace = "com.example.plateocr"
    compileSdk = 34 // Or your target SDK

    defaultConfig {
        applicationId = "com.example.plateocr"
        minSdk = 24 // Or your min SDK
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8 // Or higher if needed
        targetCompatibility = JavaVersion.VERSION_1_8 // Or higher if needed
    }
    kotlinOptions {
        jvmTarget = "1.8" // Or higher if needed
    }
    buildFeatures {
        compose = true // Enable Compose feature
    }
    composeOptions {
        // Set the Compose Compiler version corresponding to your Kotlin version
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.multidex)

    // Compose - Import the BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose) // Use BOM version
    implementation(libs.androidx.ui)               // Use BOM version
    implementation(libs.androidx.ui.graphics)        // Use BOM version
    implementation(libs.androidx.ui.tooling.preview) // Use BOM version
    implementation(libs.androidx.material3)         // Use BOM version
    implementation(libs.androidx.material.icons.extended) // Use BOM version

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Use annotationProcessor or ksp for the compiler
    // annotationProcessor(libs.androidx.room.compiler)
    // or ksp(libs.androidx.room.compiler)

    // ML Kit
    implementation(libs.mlkit.text.recognition)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom)) // BOM for test dependencies too
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // Use BOM version
    debugImplementation(libs.androidx.ui.tooling)               // Use BOM version
    debugImplementation(libs.androidx.compose.ui.test.manifest) // Use BOM version
}