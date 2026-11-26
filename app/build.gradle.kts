import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Play Store bundles are built with -Pdaypurse.play=true (see scripts/build-release.sh).
val playBuild = providers.gradleProperty("daypurse.play").map(String::toBoolean).getOrElse(false)

android {
    namespace = "bizappz.daypurse"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "in.bizappz.daypurse"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    // Two editions from one code base:
    // - full: everything, including optional payment spotting (Google Play, F-Droid, GitHub)
    // - lite: no notification access, so Play Protect won't block a directly downloaded APK (GitHub)
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            isDefault = true
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
        }
    }

    androidResources {
        // The app's own text is English-only, so drop libraries' translations (~70 locales).
        // Add a locale here when translating the app.
        localeFilters += listOf("en")
    }

    // Release signing comes from keystore.properties (git-ignored). Without it, release builds
    // are produced unsigned, so the project still builds on any machine.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        debug {
            // Installs next to the release build, as "DayPurse (debug)".
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = releaseSigning
            optimization {
                // R8 shrinks and obfuscates all code (app + libraries) and removes unused resources.
                enable = true
                // R8's optimizer produces slightly different code from run to run, which breaks
                // reproducible builds (F-Droid ships the developer-signed APK only if its own build
                // matches it byte for byte). So F-Droid and GitHub builds skip optimization; the
                // Play bundle, which nobody needs to reproduce, keeps it.
                if (!playBuild) keepRules { files.add(file("reproducible.pro")) }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    dependenciesInfo {
        // APKs carry no encrypted dependency block (F-Droid requires this); the Play bundle does.
        includeInApk = false
        includeInBundle = true
    }
}

ksp {
    // Exported schemas are committed so future migrations can be written and tested against them.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}