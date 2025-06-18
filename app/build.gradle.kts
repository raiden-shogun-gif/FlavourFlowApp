plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.todo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.todo"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
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
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:31.0.0")) // Check for the latest version
    implementation("com.google.firebase:firebase-analytics-ktx") // Example for Firebase Analytics
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.room:room-runtime:2.5.0")
    implementation("com.google.android.material:material:1.12.0")
 // Use the latest version
    kapt("androidx.room:room-compiler:2.5.0")
    implementation("com.google.android.material:material:1.8.0")
    // For OkHttp (networking)
    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Or latest version
    // For Gson (JSON parsing)
    implementation("com.google.code.gson:gson:2.10.1") // Or latest version
    // AndroidX libraries for UI components, Room, etc.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}