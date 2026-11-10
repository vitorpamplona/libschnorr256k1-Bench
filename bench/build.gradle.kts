import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
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
val bip340File = rootProject.file("vectors/bip340/test-vectors.csv")
val datasetFile = rootProject.file("datasets/nostr-v1.bin")
val datasetDigestsFile = rootProject.file("datasets/nostr-v1.txt")
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
        inputs.file(bip340File)
        inputs.file(datasetDigestsFile)
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
            // datasets/nostr-v1.txt: "file <sha256>", "events N", "pubkeys N", then the digests.
            val ds =
                datasetDigestsFile.readLines().filter { it.isNotBlank() }.associate {
                    val (k, v) = it.trim().split(" ")
                    k to v
                }
            val dsEntries =
                ds.filterKeys { it.startsWith("nostr.") }.entries.joinToString(",\n") { (k, v) -> "        \"$k\" to \"$v\"" }
            val dsOut = generatedVectors.get().file("com/vitorpamplona/schnorr256k1/bench/DatasetV1.kt").asFile
            dsOut.parentFile.mkdirs()
            dsOut.writeText(
                """
                |// Generated from datasets/nostr-v1.txt by :bench:generateVectors. Do not edit.
                |package com.vitorpamplona.schnorr256k1.bench
                |
                |object DatasetV1 {
                |    const val FILE_SHA256 = "${ds.getValue("file")}"
                |    const val EVENTS = ${ds.getValue("events")}
                |
                |    val digests: Map<String, String> = mapOf(
                |$dsEntries,
                |    )
                |}
                |
                """.trimMargin(),
            )
            // vectors/bip340/test-vectors.csv: its rows' first seven fields (the comment is dropped).
            val bipBytes = bip340File.readBytes()
            val bipSha = MessageDigest.getInstance("SHA-256").digest(bipBytes).joinToString("") { "%02x".format(it) }
            val bipRows =
                String(bipBytes).lines().drop(1).filter { it.isNotBlank() }.joinToString(",\n") { line ->
                    val f = line.trimEnd('\r').split(",", limit = 8)
                    require(f.size == 8) { "a BIP-340 vector line has too few fields: $line" }
                    "        arrayOf(" + f.take(7).joinToString(", ") { "\"$it\"" } + ")"
                }
            val bipOut = generatedVectors.get().file("com/vitorpamplona/schnorr256k1/bench/Bip340Csv.kt").asFile
            bipOut.parentFile.mkdirs()
            bipOut.writeText(
                """
                |// Generated from vectors/bip340/test-vectors.csv by :bench:generateVectors. Do not edit.
                |package com.vitorpamplona.schnorr256k1.bench
                |
                |object Bip340Csv {
                |    const val FILE_SHA256 = "$bipSha"
                |
                |    /** index, secret key, public key, aux_rand, message, signature, verification result */
                |    val rows: List<Array<String>> = listOf(
                |$bipRows,
                |    )
                |}
                |
                """.trimMargin(),
            )
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

        // libschnorr256k1-kmp, from JitPack (settings.gradle.kts) until Maven Central takes releases
        // again. JitPack builds on Linux, so the JVM, Android and linuxX64 layers have it (JNI on
        // the first two, cinterop on linuxX64); macOS gets it back, with cMain, from the first
        // Maven Central release after 1.0.5.
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
                // JVM-only implementations (bench/README-jvm-libraries.md). Bouncy Castle is
                // pinned on its own: bitcoin-s and nostr-java run on the same resolved bcprov.
                implementation(libs.bouncycastle.bcprov)
                implementation(libs.bitcoin.s.crypto)
                implementation(libs.nostr.java.core)
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
        getByName("linuxX64Main") {
            dependsOn(nativeMain)
            dependsOn(cMain)
            dependencies {
                // JitPack's rewrite of the module metadata drops the classifier from the cinterop
                // klib, so both linuxX64 files are named after the Kotlin klib and the cinterop one
                // (secp256k1c_init and the rest of the C API) never downloads. Ask for it by name.
                val kmp = libs.schnorr256k1.kmp.get()
                implementation("${kmp.group}:${kmp.name}-linuxx64:${kmp.version}:cinterop-schnorr256k1@klib")
            }
        }
        getByName("macosArm64Main") {
            dependsOn(nativeMain)
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

// The tests read the dataset from the checkout (the Android device tests from their APK's assets).
// DISABLE_SECP256K1: bitcoin-s runs its own BIP-340 on Bouncy Castle, not its libsecp256k1 JNI
// (BitcoinSImpl checks it).
tasks.withType<Test>().configureEach {
    environment("BENCH_DATASET", datasetFile.absolutePath)
    environment("DISABLE_SECP256K1", "true")
}
tasks.withType<KotlinNativeTest>().configureEach {
    environment("BENCH_DATASET", datasetFile.absolutePath)
}

// The JVM layer: the runner as a plain JVM process (no JMH), like the native executables.
tasks.register<JavaExec>("runJvm") {
    group = "benchmark"
    description = "Runs the JVM layer and prints a schema-v2 result. -Pargs=\"--device X --machine Y\""
    val jvm = kotlin.jvm()
    val main = jvm.compilations.getByName("main")
    classpath(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("com.vitorpamplona.schnorr256k1.bench.MainKt")
    providers.gradleProperty("args").orNull?.let { args(Regex("""'[^']*'|\S+""").findAll(it).map { m -> m.value.trim('\'') }.toList()) }
    jvmArgs("-Xmx1g")
    environment("BENCH_DATASET", datasetFile.absolutePath)
    environment("DISABLE_SECP256K1", "true")
}
