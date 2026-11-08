# libschnorr256k1-bench

Benchmarks and cross-checks of BIP-340 Schnorr signatures and secp256k1 for Kotlin
Multiplatform, measured on each layer an app can call them from:

| Layer | What runs | libsecp256k1 | libschnorr256k1 | libschnorr256k1-kotlin |
|---|---|---|---|---|
| `c` | C calling C: Linux, macOS, Android (NDK, over adb) | direct | direct | |
| `jvm` | a plain JVM process | ACINQ secp256k1-kmp (JNI) | libschnorr256k1-kmp (JNI) | pure Kotlin |
| `native` | Kotlin/Native executable: linuxX64, macosArm64 | ACINQ (cinterop) | libschnorr256k1-kmp (cinterop; macOS only) | pure Kotlin |
| `android` | a release instrumentation test on ART | ACINQ (JNI) | libschnorr256k1-kmp (JNI; arm64-v8a, x86_64) | pure Kotlin |

The libraries: [libsecp256k1](https://github.com/bitcoin-core/secp256k1) (the reference, at the
commit [ACINQ's secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) 0.24.0 bundles),
[libschnorr256k1](https://github.com/vitorpamplona/libschnorr256k1) through
[libschnorr256k1-kmp](https://github.com/vitorpamplona/libschnorr256k1-kmp), and
[libschnorr256k1-kotlin](https://github.com/vitorpamplona/libschnorr256k1-kotlin).

## How it measures

- **One set of inputs:** [`vectors/`](vectors/README.md), 4096 keys, messages and tweaks
  derived from SHA-256 of fixed labels. Before timing, every library's outputs must hash to the
  digests in `vectors/v1.txt`; a library that differs is still timed, but its row says
  `correct: false`.
- **Real traffic too:** [`datasets/`](datasets/README.md), the 29,614 signed events of an
  Amethyst startup session in 2024 (id, pubkey, sig), by 4,926 authors. The `nostr.*`
  operations verify them in the order relays sent them, verify them in pages batched by author,
  and run ECDH against their authors' keys. Every library must accept every signature.
- **One method:** the same runner in C (`c/main.c`) and Kotlin (`bench/.../Runner.kt`): 1 s
  warm-up per operation, then 20 samples of at least 100 ms each, median and 5th/95th percentile
  per call. No JMH or Jetpack Microbenchmark, so the layers are comparable.
- **One format:** [`results/SCHEMA.md`](results/SCHEMA.md), frozen. It covers the sixteen
  operations, the exact call each library makes for each one, and the known issues the runners
  probe for.

## Results

Runs go in `results/<layer>/`, one JSON file each; `scripts/table.py` turns them into one
table per device. Only runs from a dedicated machine are recorded here, and there are none yet.

## Known issues

Every run probes for these and records what it saw (`known_issues_observed`):

| ID | Library | Issue |
|---|---|---|
| KI-1 | libschnorr256k1, kotlin | Batch verify has no random coefficients: two invalid signatures that cancel are accepted. |
| KI-2 | libschnorr256k1 | `schnorr_sign_xonly` returns invalid signatures for keys whose public key has odd y. |
| KI-3 | libschnorr256k1 | A tweak-mul on the odd-y form of a key poisons the x-keyed table that verify uses: valid signatures are rejected. |

## Run

```bash
git submodule update --init --recursive
./gradlew :bench:jvmTest :bench:linuxX64Test       # cross-checks, no timing (macOS: :bench:macosArm64Test)
```

```bash
scripts/run-host.sh --device "MacBook Pro M3 Pro" --machine dedicated
```

```bash
scripts/run-android.sh --machine dedicated
```

```bash
scripts/run-android-c.sh --machine dedicated
```

```bash
scripts/table.py
```

- `run-host.sh` runs the c, jvm and native layers. It needs cmake, a C compiler and a JDK.
- `run-android.sh` runs the android layer on the device adb sees.
- `run-android-c.sh` builds `c/` with the NDK (`ANDROID_NDK_HOME`) and runs it on that device.
- `--quick` makes any of them a smoke run. CI uses it on Linux, macOS and an Android emulator;
  its numbers are never comparable.
- A dedicated run means a machine doing nothing else: a laptop on AC power, a phone charged,
  cool and in flight mode.
