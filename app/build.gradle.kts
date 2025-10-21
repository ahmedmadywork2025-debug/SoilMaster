plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"

}

android {
    namespace = "com.example.soillab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sandconetest2"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    // You might need to add composeOptions if not present in your actual file
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Use the version compatible with your Kotlin and Compose versions
    }
}

dependencies {

    // Core Android & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose Dependencies
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.5.0")
    // This is the new dependency needed for the ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")


    // UI Libraries
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("com.airbnb.android:lottie-compose:6.6.9")
    implementation("app.rive:rive-android:10.4.4")

    // Charting Library
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Other Important Libraries
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation("androidx.room:room-runtime:2.8.0")
    implementation(libs.androidx.room.ktx)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")








}

