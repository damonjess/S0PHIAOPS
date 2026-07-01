# TODO(RepoScanner): [WARNING] build: Gradle BUILD FAILED (took 5s) — Fix: See the compiler errors above this line for the root cause
# TODO(RepoScanner): [WARNING] build: Build process exited with code 1 — see specific errors above — Fix: Review the specific error lines higher in the CI log
# TODO(RepoScanner): [WARNING] build: Gradle BUILD FAILED (took 4s) — Fix: See the compiler errors above this line for the root cause
# TODO(RepoScanner): [WARNING] build: Gradle task failed: :app:compileDebugUnitTestKotlin — Fix: Check the Kotlin/Java compiler errors listed above this line in the build log
plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.9"
}

android {
    namespace = "com.sophia.ops"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sophia.ops"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")

    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // On-device LLM inference for local text generation
    implementation("com.google.mediapipe:tasks-genai:0.10.14")
}