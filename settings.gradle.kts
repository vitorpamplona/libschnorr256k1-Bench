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
    }
}

rootProject.name = "libschnorr256k1-bench"
include(":bench")

// libschnorr256k1-kotlin is not on Maven Central yet: build it from the submodule
// (git submodule update --init). Pass -Pschnorr256k1KotlinDir=<path> to use another checkout.
includeBuild(providers.gradleProperty("schnorr256k1KotlinDir").getOrElse("third_party/libschnorr256k1-kotlin")) {
    dependencySubstitution {
        substitute(module("com.vitorpamplona.schnorr256k1:schnorr256k1-kotlin")).using(project(":schnorr256k1-kotlin"))
    }
}
