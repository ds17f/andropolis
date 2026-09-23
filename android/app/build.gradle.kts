plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "micropolis.port"
    compileSdk = 35
    ndkVersion = "30.0.16248370"

    defaultConfig {
        // Permanent store identity. The Kotlin package (namespace) stays micropolis.port:
        // the JNI symbol names depend on it.
        applicationId = "io.github.ds17f.micropolis"
        minSdk = 24
        targetSdk = 35
        // Bumped by scripts/release.sh; F-Droid reads these literals at each tag.
        versionCode = 1
        versionName = "0.1.0"

        // Build for the emulator (x86_64) and real 64-bit devices (arm64).
        ndk { abiFilters += listOf("x86_64", "arm64-v8a") }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_shared") }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Release signing comes from the environment (CI secrets or a local keystore).
    // Without it the release build is unsigned — which is what F-Droid wants (it signs itself).
    val keystorePath = System.getenv("MICROPOLIS_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MICROPOLIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MICROPOLIS_KEY_ALIAS")
                keyPassword = System.getenv("MICROPOLIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"      // installs next to the release app
            versionNameSuffix = "-debug"
        }
    }

    // F-Droid / reproducible builds: no Google dependency-metadata blob in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
