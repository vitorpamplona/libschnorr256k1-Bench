# libschnorr256k1-bench

Benchmarks and cross-checks of BIP-340 Schnorr / secp256k1 implementations for Kotlin
Multiplatform, run through one interface (`bench/src/commonMain/.../Impl.kt`):

| `impl` | Library |
|---|---|
| `acinq` | libsecp256k1 through ACINQ's secp256k1-kmp (the reference) |
| `libschnorr256k1` | libschnorr256k1 (C) through libschnorr256k1-kmp |
| `kotlin` | libschnorr256k1-kotlin (pure Kotlin), built from the submodule |

## Layout

- `bench/src/commonMain/`: `Impl.kt` (one adapter per library), `Fixture.kt` (deterministic keys
  and signatures, made with libsecp256k1), `SchnorrBenchmark.kt` (kotlinx-benchmark, every
  operation, `@Param impl`).
- `bench/src/commonTest/`: `CrossCheckTest` (all libraries give identical results; fails the
  build) and `KnownIssuesTest` (prints whether each known bug still reproduces; never fails).
- `libschnorr256k1-kotlin/`: git submodule, substituted for the Maven artifact in
  `settings.gradle.kts` until it is published.

## Rules

- **Same inputs, same work.** Every implementation gets the same bytes and is asked for the same
  output format. When a library lacks an operation, its adapter says how it is emulated (ACINQ's
  x-only ECDH is a tweak-mul of 02||x) or returns null (no batch verify).
- **A known bug goes to KnownIssuesTest, not out of CrossCheckTest silently.** CrossCheckTest
  skips exactly the inputs a known issue covers, and says so in a comment.
- **Benchmark numbers come from a quiet machine.** Report the JVM version, CPU and whether the
  machine was shared; CI only smoke-runs the benchmarks.

## Run

```bash
git submodule update --init
./gradlew :bench:jvmTest                 # cross-checks + known-issues report (also :bench:macosArm64Test)
./gradlew :bench:jvmBenchmark            # JMH; results in bench/build/reports/benchmarks
./gradlew :bench:jvmQuickBenchmark       # one short pass
./gradlew :bench:macosArm64Benchmark     # Kotlin/Native on Apple silicon
```

## Remotes and pull requests

The repo is on GitHub, `github.com/vitorpamplona/libschnorr256k1-bench`. Open a PR only when the
human you work for asks.
