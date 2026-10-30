import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.gradule"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        // Store listing ID. Must be unique on Google Play and can't change after the first upload.
        applicationId = "com.kodalabs.skydule"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode=<run number + 100> so every uploaded bundle is higher than the last (Play requires it).
        // The fallback is the code of the last bundle uploaded to Play by hand (see CHANGELOG.md); raise it with each upload.
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 21
        versionName = "1.0.0"
    }

    // A fixed debug keystore is committed so every CI build has the same signature
    // and can be installed over the previous one on your phone.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Store releases are signed with an upload key that is NOT in the repo. Put its details in
    // keystore.properties (git-ignored) next to the root build.gradle.kts:
    //   storeFile=/path/to/upload.jks   storePassword=...   keyAlias=...   keyPassword=...
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Personal sideload build: the weather pill can open the Gradient Weather app.
            buildConfigField("boolean", "GRADIENT_WEATHER_LINK", "true")
        }
        release {
            // Store build: no references to another app's brand.
            buildConfigField("boolean", "GRADIENT_WEATHER_LINK", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The only native code is AndroidX's (graphics-path, datastore). Putting its symbol table in the bundle
            // lets Play Console read native crash stacks, and clears its "no debug symbols" warning.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            // Without keystore.properties the release build is signed with the debug key so it still builds
            // locally, but such a build must never be uploaded to the store.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
}
