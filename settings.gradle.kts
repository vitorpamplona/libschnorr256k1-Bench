pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // libschnorr256k1-kmp 1.1.0 could not reach Maven Central (Sonatype's monthly size limit), so
        // the bench takes JitPack's build of its main branch: gradle/libs.versions.toml.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.vitorpamplona.libschnorr256k1-kmp") }
        }
    }
}

rootProject.name = "libschnorr256k1-bench"
include(":bench")

// Android (ART): the same runner as :bench, as a release instrumentation test on a device.
include(":bench-android")

// libschnorr256k1-kotlin is not on Maven Central yet: build it from the submodule
// (git submodule update --init). Pass -Pschnorr256k1KotlinDir=<path> to use another checkout.
includeBuild(providers.gradleProperty("schnorr256k1KotlinDir").getOrElse("third_party/libschnorr256k1-kotlin")) {
    dependencySubstitution {
        substitute(module("com.vitorpamplona.schnorr256k1:schnorr256k1-kotlin")).using(project(":schnorr256k1-kotlin"))
    }
}
