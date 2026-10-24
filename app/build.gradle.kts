import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Both development Macs must import the same key; never use a machine-generated fallback.
val sharedDebugKeystore = rootProject.file(".signing/debug.keystore")
val debugCertificateFingerprint = rootProject.file("signing/debug-certificate.sha256")
val verifyDebugSigning by tasks.registering {
    group = "verification"
    description = "Checks that Debug APKs use the shared RideDiary signing certificate."
    doLast {
        if (!sharedDebugKeystore.isFile) {
            throw GradleException("Shared Debug key missing. Import the existing key with: python3 scripts/import-debug-signing.py /path/to/debug.keystore (see docs/signing.zh-CN.md). Do not generate a new key.")
        }
        val password = "android".toCharArray()
        val store = try {
            KeyStore.getInstance(sharedDebugKeystore, password)
        } catch (e: Exception) {
            throw GradleException("Cannot open the shared Debug key. Import the original keystore; see docs/signing.zh-CN.md.", e)
        }
        if (!store.isKeyEntry("androiddebugkey") || store.getKey("androiddebugkey", password) == null) {
            throw GradleException("Shared Debug keystore must contain the androiddebugkey private key.")
        }
        val expected = debugCertificateFingerprint.readText().trim().lowercase()
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(store.getCertificate("androiddebugkey").encoded).joinToString("") { "%02x".format(it) }
        if (!Regex("[0-9a-f]{64}").matches(expected) || actual != expected) {
            throw GradleException("Wrong Debug signing certificate. Import the shared RideDiary key; see docs/signing.zh-CN.md.")
        }
        logger.lifecycle("RideDiary Debug signing certificate verified: $actual")
    }
}
tasks.matching { it.name == "validateSigningDebug" || it.name == "validateSigningDebugAndroidTest" }.configureEach {
    dependsOn(verifyDebugSigning)
}
android {
    namespace = "com.ridediary"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ridediary"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("test").java.srcDir("src/testShared/java")
        getByName("androidTest").java.srcDir("src/testShared/java")
    }
    buildFeatures { compose = true }
    // Both languages must remain available offline after an in-app language switch.
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    signingConfigs {
        getByName("debug") {
            storeFile = sharedDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // New Android releases removed InputManager.getInstance; use its supported replacement.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
