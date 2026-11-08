plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.p2pstream"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.p2pstream"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { useLegacyPackaging = true } // libtorrent4j için
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // libtorrent4j (aynı)
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-33")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-33")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-33")
    implementation("org.libtorrent4j:libtorrent4j-android-x86:2.1.0-33")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-33")
}