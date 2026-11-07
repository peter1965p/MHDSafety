plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Die Version steht nur in gradle.properties (APP_VERSION=1.2.3).
// versionCode wird daraus berechnet: 1.2.3 -> 10203.
val appVersion: String = providers.gradleProperty("APP_VERSION").get()
val appVersionCode: Int = appVersion.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// GitHub-Repo, in dem die App nach Updates sucht.
val githubRepo = "peter1965p/MHDSafety"

// Signier-Schlüssel liegt außerhalb des Repos, Zugangsdaten in ~/.gradle/gradle.properties.
val keystoreFile: String? = providers.gradleProperty("MHD_KEYSTORE_FILE").orNull

android {
    namespace = "com.paeffgen.mhdsafety"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.paeffgen.mhdsafety"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.gradleProperty("MHD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("MHD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("MHD_KEYSTORE_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Debug-Version kann neben der echten App installiert sein.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            proguardFiles("proguard-rules.pro")
            // Nur Handy-Prozessoren (spart ~35 MB Texterkennung für x86).
            // Zum Testen im Emulator: ./gradlew assembleRelease -PallAbis
            if (!providers.gradleProperty("allAbis").isPresent) {
                ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
