import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    // No linuxX64: libschnorr256k1-kmp 1.0.5 publishes no Linux Native artifact.
    macosArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.secp256k1.kmp) // libsecp256k1 (ACINQ)
                implementation(libs.schnorr256k1.kmp) // libschnorr256k1 (C)
                implementation(libs.schnorr256k1.kotlin) // pure Kotlin
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }
    }
}

// JMH needs open benchmark classes.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm")
        register("macosArm64")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "us"
        }
        // ./gradlew :bench:jvmQuickBenchmark: one short pass, to check that everything runs.
        register("quick") {
            warmups = 1
            iterations = 2
            iterationTime = 500
            iterationTimeUnit = "ms"
            mode = "avgt"
            outputTimeUnit = "us"
        }
    }
}
