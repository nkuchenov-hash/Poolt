plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing material lives only in GitHub Actions Secrets (never in this repository).
val permanentKeystore = System.getenv("POOLTOPHONE_SIGNING_KEYSTORE")
val permanentStorePassword = System.getenv("POOLTOPHONE_SIGNING_STORE_PASSWORD")
val permanentKeyPassword = System.getenv("POOLTOPHONE_SIGNING_KEY_PASSWORD")
val permanentKeyAlias = System.getenv("POOLTOPHONE_SIGNING_KEY_ALIAS")

android {
    namespace = "app.poolt"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.poolt"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.22.0"
    }
    signingConfigs {
        if (!permanentKeystore.isNullOrBlank()) {
            create("pooltPermanent") {
                storeFile = file(permanentKeystore)
                storeType = "pkcs12"
                storePassword = permanentStorePassword
                keyPassword = permanentKeyPassword
                keyAlias = permanentKeyAlias
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!permanentKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("pooltPermanent")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
