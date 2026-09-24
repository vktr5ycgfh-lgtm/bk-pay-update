plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.riderspay.consumer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.riderspay.consumer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-beta"
        manifestPlaceholders["MAPS_API_KEY"] = providers.gradleProperty("MAPS_API_KEY").orNull ?: ""
        buildConfigField("String", "MAPS_API_KEY", "\"${providers.gradleProperty("MAPS_API_KEY").orNull ?: ""}\"")
        buildConfigField("String", "UPI_ID", "\"${providers.gradleProperty("UPI_ID").orNull ?: ""}\"")
        buildConfigField("String", "DEFAULT_STAND_ID", "\"${providers.gradleProperty("DEFAULT_STAND_ID").orNull ?: "stand_unassigned"}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.google.maps.android:maps-compose:6.4.1")
    implementation("com.google.android.gms:play-services-maps:19.1.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.libraries.places:places:4.2.0")
    implementation("com.google.firebase:firebase-auth-ktx:23.2.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.2")
    implementation("com.google.firebase:firebase-functions-ktx:21.1.0")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:18.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")
    implementation("com.google.maps.android:android-maps-utils:3.10.0")
    implementation("com.google.zxing:core:3.5.3")
}
