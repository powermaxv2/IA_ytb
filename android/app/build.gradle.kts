plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // JVM screenshot tests (Robolectric): ./gradlew recordRoborazziDebug writes build/outputs/roborazzi/*.png
    id("io.github.takahirom.roborazzi")
}

// Android has its own version number (android/VERSION), independent from the macOS app.
val appVersionName = rootProject.file("VERSION").readText().trim()
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

// Release signing: set these environment variables (GitHub secrets in CI) for a stable signature,
// so that new versions install over old ones. Otherwise the debug key is used.
val keystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.powermaxv2.ytdownloader"
    compileSdk = 35

    defaultConfig {
        // Play Store id: different from the sideloaded app (com.powermaxv2.ytdownloader) so that both can live on one phone.
        applicationId = "com.powermaxv2.gulp"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // java.time (chat tools, memos, signature, quick replies) needs API 26; the app starts at 24.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        // FFmpeg is shipped as a native library and must be extracted on install.
        jniLibs {
            useLegacyPackaging = true
            // Play Store version: the downloader (yt-dlp) is not part of it, so its Python and QuickJS runtimes are dropped.
            excludes += listOf("**/libpython.so", "**/libpython.zip.so", "**/libqjs.so")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // First frame of a video as a thumbnail (Statuts).
    implementation("io.coil-kt:coil-video:2.7.0")
    // Markdown → HTML in the Convertisseur (BSD-2-Clause).
    implementation("org.commonmark:commonmark:0.24.0")
    // QR code tool: reading and writing codes (ZXing, Apache-2.0) and the live camera (CameraX, Apache-2.0).
    implementation("com.google.zxing:core:3.5.3")
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // Assembleur PDF: copies PDF pages as they are, text included (Apache-2.0).
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Vocaux en texte: offline speech recognition (Vosk / Kaldi, Apache-2.0; pulls JNA 5.18.1, Apache-2.0).
    implementation("com.alphacephei:vosk-android:0.3.75")

    // FFmpeg packaged for Android (GPL-3.0). Its wrapper library also carries yt-dlp's launcher, which is never started
    // here: the Python runtime is excluded above and the yt-dlp script (res/raw/ytdlp) is stripped by the resource shrinker.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // Screenshot tests of the three interfaces (src/test, Robolectric + Roborazzi).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.32.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
