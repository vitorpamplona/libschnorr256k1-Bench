import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.security.MessageDigest

// The Kotlin layers (results/SCHEMA.md "layer": jvm, native; android through :bench-android):
// one runner (Runner.kt, the twin of c/main.c) over every library this platform has.
//
//   ./gradlew :bench:runJvm -Pargs="--device 'MacBook Pro M3' --machine dedicated"
//   bench/build/bin/<target>/releaseExecutable/bench.kexe --device ... --machine ...
// scripts/run-host.sh runs both and writes results/<layer>/; tests: :bench:jvmTest, :bench:linuxX64Test.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

// vectors/v1.txt as Kotlin, so no layer needs file I/O: the digests, and the file's own SHA-256
// (results/SCHEMA.md "vectorsSha256"), exactly as c/main.c computes it over the file's bytes.
// The included build pins nothing, so its commit is recorded the way c/ records the C libraries'.
val vectorsFile = rootProject.file("vectors/v1.txt")
val generatedVectors = layout.buildDirectory.dir("generated/vectors/kotlin")
// The pure-Kotlin library's commit (-dirty if its checkout has changes), for "libraries" in results.
val kotlinLibCommit =
    providers
        .exec {
            commandLine("git", "-C", rootProject.file("third_party/libschnorr256k1-kotlin").path, "describe", "--always", "--dirty", "--abbrev=7")
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim().ifEmpty { "unknown" } }
val generateVectors =
    tasks.register("generateVectors") {
        inputs.file(vectorsFile)
        inputs.property("kotlinLibCommit", kotlinLibCommit)
        outputs.dir(generatedVectors)
        doLast {
            val commit = kotlinLibCommit.get()
            val bytes = vectorsFile.readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val entries =
                String(bytes).lines().filter { it.isNotBlank() }.joinToString(",\n") {
                    val (k, v) = it.trim().split(" ")
                    "        \"$k\" to \"$v\""
                }
            val out = generatedVectors.get().file("com/vitorpamplona/schnorr256k1/bench/VectorsV1.kt").asFile
            out.parentFile.mkdirs()
            out.writeText(
                """
                |// Generated from vectors/v1.txt by :bench:generateVectors. Do not edit.
                |package com.vitorpamplona.schnorr256k1.bench
                |
                |object VectorsV1 {
                |    const val FILE_SHA256 = "$sha"
                |
                |    /** third_party/libschnorr256k1-kotlin's commit at build time. */
                |    const val KOTLIN_LIB_COMMIT = "$commit"
                |
                |    val digests: Map<String, String> = mapOf(
                |$entries,
                |    )
                |}
                |
                """.trimMargin(),
            )
        }
    }

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "com.vitorpamplona.schnorr256k1.bench"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    linuxX64()
    macosArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.executable {
            entryPoint = "com.vitorpamplona.schnorr256k1.bench.main"
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateVectors)
            dependencies {
                implementation(libs.crypto) // SHA-256 for the input derivation
                implementation(libs.secp256k1.kmp) // libsecp256k1 (ACINQ): the reference
                implementation(libs.schnorr256k1.kotlin) // pure Kotlin
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        // libschnorr256k1-kmp, on the targets it publishes (JVM, Android, Apple; not Linux Native).
        val cMain =
            create("cMain") {
                dependsOn(commonMain.get())
                dependencies {
                    implementation(libs.schnorr256k1.kmp)
                }
            }

        jvmMain {
            dependsOn(cMain)
            dependencies {
                implementation(libs.secp256k1.kmp.jni.jvm)
            }
        }
        androidMain {
            dependsOn(cMain)
            dependencies {
                implementation(libs.secp256k1.kmp.jni.android)
            }
        }

        val nativeMain =
            create("nativeMain") {
                dependsOn(commonMain.get())
            }
        getByName("linuxX64Main").dependsOn(nativeMain)
        getByName("macosArm64Main") {
            dependsOn(nativeMain)
            dependsOn(cMain)
        }

        val nativeTest =
            create("nativeTest") {
                dependsOn(commonTest.get())
            }
        getByName("linuxX64Test").dependsOn(nativeTest)
        getByName("macosArm64Test").dependsOn(nativeTest)
        jvmTest { }
    }
}

// The JVM layer: the runner as a plain JVM process (no JMH), like the native executables.
tasks.register<JavaExec>("runJvm") {
    group = "benchmark"
    description = "Runs the JVM layer and prints a schema-v1 result. -Pargs=\"--device X --machine Y\""
    val jvm = kotlin.jvm()
    val main = jvm.compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.vitorpamplona.schnorr256k1.bench.MainKt")
    providers.gradleProperty("args").orNull?.let { args(Regex("""'[^']*'|\S+""").findAll(it).map { m -> m.value.trim('\'') }.toList()) }
    jvmArgs("-Xmx1g")
}
