# libschnorr256k1-bench

Benchmarks and cross-checks of BIP-340 Schnorr / secp256k1 for Kotlin Multiplatform. Every
library runs the same operations on the same inputs through one interface (`Impl.kt`):

| `impl` | Library | How it runs |
|---|---|---|
| `acinq` | [libsecp256k1](https://github.com/bitcoin-core/secp256k1) via [ACINQ's secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) | JNI / cinterop |
| `libschnorr256k1` | [libschnorr256k1](https://github.com/vitorpamplona/libschnorr256k1) via [libschnorr256k1-kmp](https://github.com/vitorpamplona/libschnorr256k1-kmp) | JNI / cinterop |
| `kotlin` | [libschnorr256k1-kotlin](https://github.com/vitorpamplona/libschnorr256k1-kotlin) | pure Kotlin |

Targets: JVM and macOS arm64 (Kotlin/Native). libschnorr256k1-kmp 1.0.5 publishes no Linux
Native artifact, so there is no Linux Native target yet.

## Results

### JVM, 2026-11-07

Temurin 21.0.12, Intel Xeon @ 2.10 GHz, 4 vCPUs, a **shared cloud VM** (run-to-run noise about
±15%; the ± is JMH's 99.9% interval over 5 iterations). Microseconds per call, lower is better.
libschnorr256k1-kotlin at `b736254`.

| Operation | acinq | libschnorr256k1 | kotlin | kotlin vs acinq |
|---|---:|---:|---:|---:|
| verify, same key | 38.5 ± 7.6 | 38.2 ± 9.1 | 55.5 ± 8.5 | 1.4x slower |
| verify, 4096 keys | 40.2 ± 6.3 | 70.2 ± 9.8 | 108.4 ± 16.4 | 2.7x slower |
| verifyFast, same key¹ | 38.9 ± 11.4 | 32.5 ± 6.5 | 58.1 ± 7.0 | 1.5x slower |
| batch verify, 200 sigs, per sig | n/a | 4.9 | 9.7 | |
| sign | 33.7 ± 5.7 | 37.7 ± 3.7 | 61.1 ± 7.8 | 1.8x slower |
| sign, public key known² | 35.6 ± 5.8 | 20.7 ± 3.2 | 32.9 ± 11.3 | about even |
| pubkeyCreate | 16.5 ± 3.1 | 18.6 ± 1.0 | 29.4 ± 3.8 | 1.8x slower |
| ecdhXOnly³ | 33.6 ± 7.5 | 61.6 ± 3.8 | 84.6 ± 11.5 | 2.5x slower |
| tweakMul | 37.7 ± 10.2 | 60.9 ± 8.0 | 88.2 ± 13.6 | 2.3x slower |
| tweakAdd | 0.16 | 0.15 | 0.045 | 3.5x faster |

1. libsecp256k1 has no fast verify; its row is a full verify.
2. acinq: full sign (libsecp256k1 always derives the key pair). libschnorr256k1:
   `schnorrSignXOnly`, which is **invalid for odd-y keys** (see below), so its time is not
   comparable. kotlin: `schnorrSignWithPubKey`, with the 33-byte compressed key.
3. acinq has no x-only ECDH; it runs a tweak-mul of `02 || x`, as Quartz does.

What it says:

- **Kotlin's slowest operations are the variable-base multiplications:** ECDH, tweak-mul, and
  verify across many keys. libschnorr256k1 is slow at the same ones.
- **The per-key caches cost more than they save when keys change.** libschnorr256k1 and the
  Kotlin library build and cache a table per public key. That takes verify from 38 / 55 µs with
  one key to 70 / 108 µs across 4096 keys, while libsecp256k1, which caches nothing, stays at
  40 µs.
- **Same-key verification:** libschnorr256k1 matches libsecp256k1, and Kotlin is 1.4x behind.
  Batch verification is the fastest per signature, but it has a soundness bug (below).
- **JNI's fixed cost dominates the cheap operations.** tweakAdd is 3.5x faster in pure Kotlin.

## Known issues (`KnownIssuesTest`)

The test prints whether each issue still reproduces; it never fails the build. As of the
results above:

| Issue | libschnorr256k1 | kotlin |
|---|---|---|
| Batch verify accepts two invalid signatures that cancel (no random coefficients) | yes | yes |
| `signXOnly` gives an invalid signature for odd-y keys | yes (209/209) | no |
| Verify rejects a valid signature after a tweak-mul of the odd-y form of its key (shared x-keyed table cache) | yes (100/100) | no |
| A `null` aux signs differently from libsecp256k1 (which treats null as 32 zero bytes); both are valid | differs | differs |

## Run

```bash
git submodule update --init
./gradlew :bench:jvmTest                 # CrossCheckTest + KnownIssuesTest
./gradlew :bench:jvmBenchmark            # results in bench/build/reports/benchmarks
./gradlew :bench:jvmQuickBenchmark       # one short pass
./gradlew :bench:macosArm64Test :bench:macosArm64Benchmark   # on Apple silicon
```

Not here yet: Android (a device benchmark with `androidx.benchmark`), and the C libraries called
directly from C (libschnorr256k1's own `bench/` compares it with libsecp256k1).
