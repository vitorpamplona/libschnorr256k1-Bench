// The Android (ART) layer: the same runner as :bench (Runner.kt), as a release instrumentation
// test on a device. Nothing here is published.
//
//   ./gradlew :bench-android:connectedReleaseAndroidTest                # a physical device, for numbers
//   scripts/run-android.sh                                               # the same, then pulls the JSON
//
// Release code, because a debuggable APK runs ART with its optimisations held back; the
// androidx.benchmark runner keeps the CPU in its sustained-performance mode and refuses an
// emulator, a debuggable build or a low battery unless told otherwise (CI dry-runs it).
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.androidxBenchmark)
}

android {
    namespace = "com.vitorpamplona.schnorr256k1.bench.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    testBuildType = "release"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    androidTestImplementation(project(":bench"))
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}
