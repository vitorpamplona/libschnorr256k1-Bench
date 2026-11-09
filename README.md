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

To compare with what other Nostr and Bitcoin software runs, every other maintained BIP-340
implementation we found runs too, each in its own language, with that ecosystem's libsecp256k1
binding as the reference beside it:

| Layer | Runtime | Libraries |
|---|---|---|
| `jvm` | JVM 21 | [Bouncy Castle](https://www.bouncycastle.org/) `BIP340Signer`, [bitcoin-s](https://github.com/bitcoin-s/bitcoin-s), [nostr-java](https://github.com/tcheeric/nostr-java) ([details](bench/README-jvm-libraries.md)) |
| `rust` | Rust | [rust-secp256k1](https://github.com/rust-bitcoin/rust-secp256k1) (libsecp256k1), [k256](https://github.com/RustCrypto/elliptic-curves), [secp256kfun](https://github.com/LLFourn/secp256kfun) ([details](rust/README.md)) |
| `go` | Go | libsecp256k1 (cgo), [btcec](https://github.com/btcsuite/btcd), p256k1 (ORLY's), [secp256k1-voi](https://gitlab.com/yawning/secp256k1-voi), [splitsecure](https://github.com/splitsecure/secp256k1-go) ([details](go/README.md)) |
| `node`, `bun` | Node.js, Bun | [tiny-secp256k1](https://github.com/bitcoinjs/tiny-secp256k1) (libsecp256k1 as WebAssembly), [@noble/curves](https://github.com/paulmillr/noble-curves), [@noble/secp256k1](https://github.com/paulmillr/noble-secp256k1) ([details](js/README.md)) |
| `python` | CPython | [coincurve](https://github.com/ofek/coincurve) (libsecp256k1), [btclib](https://github.com/btclib-org/btclib) (`ecc`), [embit](https://github.com/diybitcoinhardware/embit), [secp256k1lab](https://github.com/secp256k1lab/secp256k1lab), BIP-340's [reference.py](https://github.com/bitcoin/bips/blob/master/bip-0340/reference.py) ([details](python/README.md)) |

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

One table per operation, verification first: a Nostr client verifies every event every relay
sends. Each table ranks every library on every layer, with libschnorr256k1-kotlin, the library
this bench was built for, in bold. Runs from a dedicated machine are what the tables are for;
until there is one, a full run on a shared cloud VM stands in, labelled as such, and the first
dedicated run replaces it. The newest device's runs are here, and every device's in
[results/report.md](results/report.md). The runs are the JSON
files in `results/<layer>/`; `scripts/table.py --readme` writes this section and the report from
them, and CI fails when either is stale.

<!-- results:begin: scripts/table.py --readme writes this section; do not edit it by hand -->

Measured on Cloud VM (4 vCPU) (Intel(R) Xeon(R) Processor @ 2.30GHz), **a shared machine** (a cloud VM: other work may have run beside it, so the numbers are noisier than a dedicated run's, which will replace them), single thread, 2026-11-09. Each table ranks one operation: median µs per call, and its ratio to libsecp256k1 in the same layer (below 1x is faster). A wrong result is listed last, unranked; KI-n marks a known issue (below).

### Verify real Nostr events

`nostr.verify`: the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libsecp256k1` | c | 30.1 | – |
| 2 | python | `libsecp256k1` | cffi | 30.4 | – |
| 3 | rust | `libsecp256k1` | rust-secp256k1 | 30.5 | – |
| 4 | jvm | `libsecp256k1` | jni | 30.5 | – |
| 5 | native | `libsecp256k1` | cinterop | 31.5 | – |
| 6 | go | `libsecp256k1` | cgo | 32.6 | – |
| 7 | go | `splitsecure` | go | 37.8 | 1.16x |
| 8 | c | `libschnorr256k1` | c | 43.7 | 1.45x |
| **9** | **native** | **`kotlin`** | **kotlin** | **44.0** | **1.40x** |
| 10 | jvm | `libschnorr256k1` | jni | 44.6 | 1.46x |
| 11 | rust | `k256` | rust | 51.9 | 1.70x |
| 12 | rust | `secp256kfun` | rust | 65.3 | 2.14x |
| **13** | **jvm** | **`kotlin`** | **kotlin** | **66.0** | **2.17x** |
| 14 | go | `secp256k1-voi` | go | 71.2 | 2.19x |
| 15 | go | `p256k1` | go | 95.6 | 2.93x |
| 16 | jvm | `bouncycastle` | java | 124.1 | 4.07x |
| 17 | go | `btcec` | go | 158.4 | 4.86x |
| 18 | bun | `libsecp256k1` | wasm | 177.7 | – |
| 19 | node | `libsecp256k1` | wasm | 195.0 | – |
| 20 | bun | `noble-curves` | js | 367.9 | 2.07x |
| 21 | bun | `noble-secp256k1` | js | 660.9 | 3.72x |
| 22 | python | `btclib-ecc` | python | 916.9 | 30.11x |
| 23 | jvm | `bitcoin-s` | scala | 1044.2 | 34.28x |
| 24 | node | `noble-curves` | js | 1110.6 | 5.70x |
| 25 | node | `noble-secp256k1` | js | 1902.1 | 9.75x |
| 26 | python | `embit` | python | 2081.7 | 68.37x |
| 27 | python | `secp256k1lab` | python | 7728.7 | 253.82x |
| 28 | jvm | `nostr-java` | java | 11025.0 | 361.92x |
| 29 | python | `bip340-reference` | python | 72931.0 | 2395.11x |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | rust | `libsecp256k1` | rust-secp256k1 | 29.8 | – |
| 2 | c | `libsecp256k1` | c | 29.9 | – |
| 3 | jvm | `libsecp256k1` | jni | 30.3 | – |
| 4 | native | `libsecp256k1` | cinterop | 30.7 | – |
| 5 | python | `libsecp256k1` | cffi | 30.9 | – |
| 6 | go | `libsecp256k1` | cgo | 31.1 | – |
| 7 | go | `splitsecure` | go | 38.3 | 1.23x |
| 8 | rust | `k256` | rust | 52.0 | 1.75x |
| **9** | **native** | **`kotlin`** | **kotlin** | **52.3** | **1.70x** |
| 10 | c | `libschnorr256k1` | c | 53.2 | 1.78x |
| 11 | jvm | `libschnorr256k1` | jni | 55.3 | 1.83x |
| 12 | rust | `secp256kfun` | rust | 65.1 | 2.19x |
| 13 | go | `secp256k1-voi` | go | 73.0 | 2.34x |
| **14** | **jvm** | **`kotlin`** | **kotlin** | **77.6** | **2.56x** |
| 15 | go | `p256k1` | go | 95.9 | 3.08x |
| 16 | jvm | `bouncycastle` | java | 119.9 | 3.96x |
| 17 | go | `btcec` | go | 158.9 | 5.10x |
| 18 | bun | `libsecp256k1` | wasm | 178.6 | – |
| 19 | node | `libsecp256k1` | wasm | 183.7 | – |
| 20 | bun | `noble-curves` | js | 353.1 | 1.98x |
| 21 | bun | `noble-secp256k1` | js | 666.4 | 3.73x |
| 22 | python | `btclib-ecc` | python | 880.5 | 28.46x |
| 23 | jvm | `bitcoin-s` | scala | 1048.7 | 34.64x |
| 24 | node | `noble-curves` | js | 1136.1 | 6.18x |
| 25 | node | `noble-secp256k1` | js | 1884.8 | 10.26x |
| 26 | python | `embit` | python | 1971.5 | 63.72x |
| 27 | python | `secp256k1lab` | python | 7760.6 | 250.84x |
| 28 | jvm | `nostr-java` | java | 11041.7 | 364.68x |
| 29 | python | `bip340-reference` | python | 72176.0 | 2332.88x |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| **1** | **native** | **`kotlin`** | **kotlin** | **25.7** | **0.91x** |
| 2 | c | `libschnorr256k1` | c | 27.5 | 0.96x |
| 3 | jvm | `libschnorr256k1` | jni | 27.8 | 1.00x |
| 4 | jvm | `libsecp256k1` | jni | 27.9 | – |
| 5 | rust | `libsecp256k1` | rust-secp256k1 | 28.1 | – |
| 6 | native | `libsecp256k1` | cinterop | 28.2 | – |
| 7 | python | `libsecp256k1` | cffi | 28.5 | – |
| 8 | c | `libsecp256k1` | c | 28.6 | – |
| 9 | go | `libsecp256k1` | cgo | 31.6 | – |
| 10 | go | `splitsecure` | go | 35.3 | 1.12x |
| **11** | **jvm** | **`kotlin`** | **kotlin** | **45.6** | **1.63x** |
| 12 | rust | `k256` | rust | 49.3 | 1.75x |
| 13 | rust | `secp256kfun` | rust | 65.3 | 2.32x |
| 14 | go | `secp256k1-voi` | go | 70.6 | 2.24x |
| 15 | go | `p256k1` | go | 84.5 | 2.68x |
| 16 | jvm | `bouncycastle` | java | 110.7 | 3.97x |
| 17 | go | `btcec` | go | 158.3 | 5.02x |
| 18 | bun | `libsecp256k1` | wasm | 174.4 | – |
| 19 | node | `libsecp256k1` | wasm | 176.5 | – |
| 20 | bun | `noble-curves` | js | 336.8 | 1.93x |
| 21 | bun | `noble-secp256k1` | js | 670.7 | 3.85x |
| 22 | python | `btclib-ecc` | python | 859.1 | 30.11x |
| 23 | jvm | `bitcoin-s` | scala | 1036.4 | 37.19x |
| 24 | node | `noble-curves` | js | 1127.4 | 6.39x |
| 25 | node | `noble-secp256k1` | js | 1884.8 | 10.68x |
| 26 | python | `embit` | python | 1903.4 | 66.71x |
| 27 | python | `secp256k1lab` | python | 7767.2 | 272.22x |
| 28 | jvm | `nostr-java` | java | 10791.6 | 387.23x |
| 29 | python | `bip340-reference` | python | 70871.0 | 2483.87x |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libschnorr256k1` | c | 15.9 KI-1 | – |
| 2 | jvm | `libschnorr256k1` | jni | 17.6 KI-1 | – |
| **3** | **native** | **`kotlin`** | **kotlin** | **28.1** | – |
| **4** | **jvm** | **`kotlin`** | **kotlin** | **42.2** | – |
| 5 | go | `p256k1` | go | 125.5 | – |
| 6 | python | `btclib-ecc` | python | 890.1 | – |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libschnorr256k1` | c | 3.63 KI-1 | – |
| 2 | jvm | `libschnorr256k1` | jni | 3.77 KI-1 | – |
| **3** | **native** | **`kotlin`** | **kotlin** | **16.6** | – |
| **4** | **jvm** | **`kotlin`** | **kotlin** | **25.7** | – |
| 5 | go | `p256k1` | go | 124.5 | – |
| 6 | python | `btclib-ecc` | python | 708.2 | – |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | jvm | `libsecp256k1` | jni | 30.0 | – |
| 2 | c | `libsecp256k1` | c | 30.1 | – |
| 3 | rust | `libsecp256k1` | rust-secp256k1 | 30.3 | – |
| 4 | native | `libsecp256k1` | cinterop | 31.0 | – |
| 5 | go | `libsecp256k1` | cgo | 31.6 | – |
| 6 | python | `libsecp256k1` | cffi | 31.6 | – |
| 7 | go | `splitsecure` | go | 38.6 | 1.22x |
| 8 | c | `libschnorr256k1` | c | 39.5 | 1.31x |
| 9 | jvm | `libschnorr256k1` | jni | 39.8 | 1.33x |
| **10** | **native** | **`kotlin`** | **kotlin** | **41.8** | **1.35x** |
| 11 | rust | `k256` | rust | 52.2 | 1.72x |
| **12** | **jvm** | **`kotlin`** | **kotlin** | **61.3** | **2.04x** |
| 13 | rust | `secp256kfun` | rust | 66.1 | 2.18x |
| 14 | go | `secp256k1-voi` | go | 70.7 | 2.24x |
| 15 | go | `p256k1` | go | 94.6 | 3.00x |
| 16 | jvm | `bouncycastle` | java | 120.0 | 4.00x |
| 17 | go | `btcec` | go | 158.5 | 5.02x |
| 18 | bun | `libsecp256k1` | wasm | 180.1 | – |
| 19 | node | `libsecp256k1` | wasm | 184.5 | – |
| 20 | bun | `noble-curves` | js | 372.4 | 2.07x |
| 21 | bun | `noble-secp256k1` | js | 660.8 | 3.67x |
| 22 | python | `btclib-ecc` | python | 892.0 | 28.23x |
| 23 | jvm | `bitcoin-s` | scala | 1053.5 | 35.08x |
| 24 | node | `noble-curves` | js | 1096.4 | 5.94x |
| 25 | node | `noble-secp256k1` | js | 1907.8 | 10.34x |
| 26 | python | `embit` | python | 1996.2 | 63.17x |
| 27 | python | `secp256k1lab` | python | 7787.2 | 246.44x |
| 28 | jvm | `nostr-java` | java | 11173.4 | 372.02x |
| 29 | python | `bip340-reference` | python | 73477.2 | 2325.30x |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libschnorr256k1` | c | 24.3 | 0.81x |
| **2** | **native** | **`kotlin`** | **kotlin** | **24.4** | **0.86x** |
| 3 | jvm | `libschnorr256k1` | jni | 25.3 | 0.88x |
| 4 | python | `libsecp256k1` | cffi | 28.1 | – |
| 5 | rust | `libsecp256k1` | rust-secp256k1 | 28.2 | – |
| 6 | native | `libsecp256k1` | cinterop | 28.3 | – |
| 7 | jvm | `libsecp256k1` | jni | 28.8 | – |
| 8 | c | `libsecp256k1` | c | 30.1 | – |
| 9 | go | `libsecp256k1` | cgo | 30.8 | – |
| 10 | go | `splitsecure` | go | 35.6 | 1.16x |
| **11** | **jvm** | **`kotlin`** | **kotlin** | **41.8** | **1.45x** |
| 12 | rust | `k256` | rust | 49.0 | 1.74x |
| 13 | rust | `secp256kfun` | rust | 65.0 | 2.30x |
| 14 | go | `secp256k1-voi` | go | 68.8 | 2.23x |
| 15 | go | `p256k1` | go | 84.0 | 2.73x |
| 16 | jvm | `bouncycastle` | java | 109.4 | 3.80x |
| 17 | go | `btcec` | go | 154.5 | 5.02x |
| 18 | bun | `libsecp256k1` | wasm | 174.2 | – |
| 19 | node | `libsecp256k1` | wasm | 176.3 | – |
| 20 | bun | `noble-curves` | js | 344.2 | 1.98x |
| 21 | bun | `noble-secp256k1` | js | 657.0 | 3.77x |
| 22 | python | `btclib-ecc` | python | 848.7 | 30.24x |
| 23 | jvm | `bitcoin-s` | scala | 1022.8 | 35.56x |
| 24 | node | `noble-curves` | js | 1110.8 | 6.30x |
| 25 | node | `noble-secp256k1` | js | 1864.9 | 10.58x |
| 26 | python | `embit` | python | 1891.2 | 67.38x |
| 27 | python | `secp256k1lab` | python | 7755.0 | 276.29x |
| 28 | jvm | `nostr-java` | java | 10887.5 | 378.51x |
| 29 | python | `bip340-reference` | python | 70295.1 | 2504.39x |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | go | `libsecp256k1` | cgo | 27.0 | – |
| 2 | jvm | `libsecp256k1` | jni | 27.0 | – |
| 3 | c | `libsecp256k1` | c | 27.3 | – |
| 4 | native | `libsecp256k1` | cinterop | 27.4 | – |
| 5 | c | `libschnorr256k1` | c | 29.0 | 1.06x |
| **6** | **native** | **`kotlin`** | **kotlin** | **29.3** | **1.07x** |
| 7 | jvm | `libschnorr256k1` | jni | 30.5 | 1.13x |
| 8 | rust | `k256` | rust | 38.7 | 0.56x |
| 9 | go | `splitsecure` | go | 39.1 | 1.45x |
| **10** | **jvm** | **`kotlin`** | **kotlin** | **47.4** | **1.76x** |
| 11 | go | `btcec` | go | 66.1 | 2.45x |
| 12 | rust | `libsecp256k1` | rust-secp256k1 | 68.6 | – |
| 13 | go | `secp256k1-voi` | go | 74.1 | 2.75x |
| 14 | rust | `secp256kfun` | rust | 81.9 | 1.19x |
| 15 | python | `libsecp256k1` | cffi | 86.6 | – |
| 16 | go | `p256k1` | go | 105.1 | 3.90x |
| 17 | jvm | `bouncycastle` | java | 123.4 | 4.57x |
| 18 | bun | `libsecp256k1` | wasm | 166.0 | – |
| 19 | node | `libsecp256k1` | wasm | 170.9 | – |
| 20 | bun | `noble-curves` | js | 612.3 | 3.69x |
| 21 | bun | `noble-secp256k1` | js | 904.7 | 5.45x |
| 22 | python | `btclib-ecc` | python | 1283.9 | 14.83x |
| 23 | node | `noble-curves` | js | 1839.7 | 10.77x |
| 24 | node | `noble-secp256k1` | js | 2285.4 | 13.37x |
| 25 | python | `embit` | python | 4018.6 | 46.41x |
| 26 | jvm | `bitcoin-s` | scala | 6060.4 | 224.44x |
| 27 | python | `secp256k1lab` | python | 11466.6 | 132.44x |
| 28 | jvm | `nostr-java` | java | 21923.9 | 811.93x |
| 29 | python | `bip340-reference` | python | 141590.6 | 1635.33x |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| **1** | **native** | **`kotlin`** | **kotlin** | **16.1** | **0.59x** |
| **2** | **jvm** | **`kotlin`** | **kotlin** | **24.2** | **0.89x** |
| 3 | c | `libsecp256k1` | c | 26.6 | – |
| 4 | jvm | `libsecp256k1` | jni | 27.2 | – |
| 5 | native | `libsecp256k1` | cinterop | 27.3 | – |
| 6 | go | `libsecp256k1` | cgo | 29.7 | – |
| 7 | go | `splitsecure` | go | 39.3 | 1.32x |
| 8 | rust | `k256` | rust | 39.5 | 0.57x |
| 9 | go | `btcec` | go | 63.3 | 2.13x |
| 10 | rust | `libsecp256k1` | rust-secp256k1 | 69.3 | – |
| 11 | go | `secp256k1-voi` | go | 71.4 | 2.41x |
| 12 | jvm | `bouncycastle` | java | 76.0 | 2.79x |
| 13 | rust | `secp256kfun` | rust | 82.1 | 1.18x |
| 14 | python | `libsecp256k1` | cffi | 85.6 | – |
| 15 | go | `p256k1` | go | 103.3 | 3.48x |
| 16 | bun | `libsecp256k1` | wasm | 163.3 | – |
| 17 | node | `libsecp256k1` | wasm | 171.2 | – |
| 18 | bun | `noble-curves` | js | 615.7 | 3.77x |
| 19 | bun | `noble-secp256k1` | js | 817.9 | 5.01x |
| 20 | python | `btclib-ecc` | python | 1315.5 | 15.36x |
| 21 | node | `noble-curves` | js | 1754.4 | 10.25x |
| 22 | node | `noble-secp256k1` | js | 2376.2 | 13.88x |
| 23 | python | `embit` | python | 4101.1 | 47.89x |
| 24 | jvm | `bitcoin-s` | scala | 5960.1 | 219.02x |
| 25 | python | `secp256k1lab` | python | 11479.6 | 134.04x |
| 26 | jvm | `nostr-java` | java | 22266.8 | 818.24x |
| 27 | python | `bip340-reference` | python | 142976.3 | 1669.48x |
| – | c | `libschnorr256k1` | c | 14.7 **wrong** KI-2 | 0.55x |
| – | jvm | `libschnorr256k1` | jni | 16.0 **wrong** KI-2 | 0.59x |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libsecp256k1` | c | 12.9 | – |
| 2 | jvm | `libsecp256k1` | jni | 13.2 | – |
| 3 | native | `libsecp256k1` | cinterop | 13.3 | – |
| **4** | **native** | **`kotlin`** | **kotlin** | **13.7** | **1.03x** |
| 5 | go | `libsecp256k1` | cgo | 13.7 | – |
| 6 | c | `libschnorr256k1` | c | 14.3 | 1.11x |
| 7 | jvm | `libschnorr256k1` | jni | 15.0 | 1.14x |
| 8 | python | `libsecp256k1` | cffi | 15.3 | – |
| 9 | rust | `k256` | rust | 19.1 | 0.56x |
| 10 | go | `splitsecure` | go | 19.1 | 1.39x |
| **11** | **jvm** | **`kotlin`** | **kotlin** | **22.9** | **1.73x** |
| 12 | go | `secp256k1-voi` | go | 24.4 | 1.78x |
| 13 | go | `btcec` | go | 31.4 | 2.29x |
| 14 | rust | `libsecp256k1` | rust-secp256k1 | 34.0 | – |
| 15 | go | `p256k1` | go | 40.7 | 2.97x |
| 16 | rust | `secp256kfun` | rust | 41.4 | 1.22x |
| 17 | jvm | `bouncycastle` | java | 60.3 | 4.57x |
| 18 | bun | `libsecp256k1` | wasm | 79.4 | – |
| 19 | bun | `noble-secp256k1` | js | 84.7 | 1.07x |
| 20 | node | `libsecp256k1` | wasm | 85.3 | – |
| 21 | bun | `noble-curves` | js | 109.0 | 1.37x |
| 22 | python | `btclib-ecc` | python | 184.7 | 12.09x |
| 23 | node | `noble-secp256k1` | js | 196.9 | 2.31x |
| 24 | node | `noble-curves` | js | 311.7 | 3.66x |
| 25 | jvm | `bitcoin-s` | scala | 387.4 | 29.35x |
| 26 | python | `embit` | python | 1304.9 | 85.43x |
| 27 | python | `secp256k1lab` | python | 1969.7 | 128.96x |
| 28 | python | `bip340-reference` | python | 36083.6 | 2362.46x |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | rust | `libsecp256k1` | rust-secp256k1 | 26.0 | – |
| 2 | c | `libsecp256k1` | c | 26.7 | – |
| 3 | native | `libsecp256k1` | cinterop | 27.0 | – |
| 4 | jvm | `libsecp256k1` | jni | 27.9 | – |
| 5 | go | `libsecp256k1` | cgo | 28.4 | – |
| 6 | python | `libsecp256k1` | cffi | 30.0 | – |
| 7 | go | `splitsecure` | go | 40.2 | 1.42x |
| 8 | rust | `k256` | rust | 44.2 | 1.70x |
| **9** | **native** | **`kotlin`** | **kotlin** | **44.5** | **1.65x** |
| 10 | rust | `secp256kfun` | rust | 46.3 | 1.78x |
| 11 | c | `libschnorr256k1` | c | 47.4 | 1.77x |
| 12 | jvm | `libschnorr256k1` | jni | 47.9 | 1.72x |
| 13 | go | `secp256k1-voi` | go | 63.7 | 2.25x |
| **14** | **jvm** | **`kotlin`** | **kotlin** | **65.6** | **2.35x** |
| 15 | go | `btcec` | go | 125.3 | 4.42x |
| 16 | go | `p256k1` | go | 148.1 | 5.22x |
| 17 | bun | `libsecp256k1` | wasm | 158.0 | – |
| 18 | node | `libsecp256k1` | wasm | 162.3 | – |
| 19 | jvm | `bouncycastle` | java | 187.5 | 6.72x |
| 20 | jvm | `bitcoin-s` | scala | 543.0 | 19.48x |
| 21 | bun | `noble-curves` | js | 543.2 | 3.44x |
| 22 | bun | `noble-secp256k1` | js | 732.1 | 4.63x |
| 23 | python | `btclib-ecc` | python | 770.5 | 25.65x |
| 24 | node | `noble-curves` | js | 1961.1 | 12.08x |
| 25 | node | `noble-secp256k1` | js | 2256.6 | 13.91x |
| 26 | python | `secp256k1lab` | python | 5852.8 | 194.87x |
| 27 | python | `bip340-reference` | python | 36089.7 | 1201.62x |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | rust | `libsecp256k1` | rust-secp256k1 | 26.6 | – |
| 2 | jvm | `libsecp256k1` | jni | 26.7 | – |
| 3 | c | `libsecp256k1` | c | 26.9 | – |
| 4 | native | `libsecp256k1` | cinterop | 27.6 | – |
| 5 | go | `libsecp256k1` | cgo | 27.9 | – |
| 6 | python | `libsecp256k1` | cffi | 28.3 | – |
| 7 | go | `splitsecure` | go | 40.7 | 1.46x |
| 8 | rust | `k256` | rust | 43.6 | 1.64x |
| **9** | **native** | **`kotlin`** | **kotlin** | **44.5** | **1.61x** |
| 10 | rust | `secp256kfun` | rust | 46.7 | 1.76x |
| 11 | c | `libschnorr256k1` | c | 46.9 | 1.75x |
| 12 | jvm | `libschnorr256k1` | jni | 48.0 | 1.80x |
| **13** | **jvm** | **`kotlin`** | **kotlin** | **65.5** | **2.45x** |
| 14 | go | `secp256k1-voi` | go | 67.1 | 2.40x |
| 15 | go | `btcec` | go | 124.9 | 4.47x |
| 16 | go | `p256k1` | go | 144.4 | 5.17x |
| 17 | bun | `libsecp256k1` | wasm | 156.1 | – |
| 18 | node | `libsecp256k1` | wasm | 161.5 | – |
| 19 | jvm | `bouncycastle` | java | 181.0 | 6.78x |
| 20 | jvm | `bitcoin-s` | scala | 494.8 | 18.53x |
| 21 | bun | `noble-curves` | js | 574.9 | 3.68x |
| 22 | bun | `noble-secp256k1` | js | 739.9 | 4.74x |
| 23 | python | `btclib-ecc` | python | 771.7 | 27.29x |
| 24 | node | `noble-curves` | js | 1990.8 | 12.32x |
| 25 | node | `noble-secp256k1` | js | 2259.4 | 13.99x |
| 26 | python | `secp256k1lab` | python | 5843.8 | 206.68x |
| 27 | python | `bip340-reference` | python | 36309.1 | 1284.18x |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | rust | `libsecp256k1` | rust-secp256k1 | 26.2 | – |
| 2 | go | `libsecp256k1` | cgo | 26.6 | – |
| 3 | c | `libsecp256k1` | c | 26.8 | – |
| 4 | jvm | `libsecp256k1` | jni | 26.8 | – |
| 5 | native | `libsecp256k1` | cinterop | 27.8 | – |
| 6 | python | `libsecp256k1` | cffi | 28.4 | – |
| 7 | go | `splitsecure` | go | 33.1 | 1.25x |
| 8 | rust | `k256` | rust | 43.8 | 1.67x |
| **9** | **native** | **`kotlin`** | **kotlin** | **44.7** | **1.61x** |
| 10 | rust | `secp256kfun` | rust | 46.7 | 1.78x |
| 11 | c | `libschnorr256k1` | c | 48.0 | 1.79x |
| 12 | jvm | `libschnorr256k1` | jni | 48.2 | 1.80x |
| 13 | go | `secp256k1-voi` | go | 63.6 | 2.40x |
| **14** | **jvm** | **`kotlin`** | **kotlin** | **67.5** | **2.52x** |
| 15 | jvm | `bouncycastle` | java | 100.0 | 3.72x |
| 16 | go | `btcec` | go | 121.4 | 4.57x |
| 17 | bun | `libsecp256k1` | wasm | 156.0 | – |
| 18 | node | `libsecp256k1` | wasm | 163.3 | – |
| 19 | go | `p256k1` | go | 171.4 | 6.45x |
| 20 | jvm | `bitcoin-s` | scala | 506.9 | 18.88x |
| 21 | bun | `noble-curves` | js | 545.8 | 3.50x |
| 22 | bun | `noble-secp256k1` | js | 757.5 | 4.85x |
| 23 | python | `btclib-ecc` | python | 795.3 | 27.99x |
| 24 | node | `noble-curves` | js | 1982.8 | 12.14x |
| 25 | node | `noble-secp256k1` | js | 2306.3 | 14.12x |
| 26 | python | `secp256k1lab` | python | 5845.6 | 205.75x |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libschnorr256k1` | c | 0.00 | 0.02x |
| 2 | jvm | `libsecp256k1` | jni | 0.01 | – |
| 3 | native | `libsecp256k1` | cinterop | 0.04 | – |
| 4 | jvm | `libschnorr256k1` | jni | 0.07 | 5.08x |
| 5 | rust | `libsecp256k1` | rust-secp256k1 | 0.09 | – |
| 6 | go | `splitsecure` | go | 0.10 | 0.60x |
| 7 | c | `libsecp256k1` | c | 0.10 | – |
| 8 | rust | `k256` | rust | 0.13 | 1.42x |
| **9** | **jvm** | **`kotlin`** | **kotlin** | **0.14** | **9.78x** |
| 10 | go | `libsecp256k1` | cgo | 0.16 | – |
| 11 | go | `btcec` | go | 0.24 | 1.53x |
| **12** | **native** | **`kotlin`** | **kotlin** | **0.25** | **5.76x** |
| 13 | go | `p256k1` | go | 0.27 | 1.70x |
| 14 | bun | `libsecp256k1` | wasm | 0.37 | – |
| 15 | node | `libsecp256k1` | wasm | 0.42 | – |
| 16 | jvm | `bouncycastle` | java | 0.51 | 37.04x |
| 17 | python | `libsecp256k1` | cffi | 1.21 | – |
| 18 | bun | `noble-curves` | js | 1.55 | 4.15x |
| 19 | node | `noble-curves` | js | 3.95 | 9.42x |
| 20 | python | `btclib-ecc` | python | 4.32 | 3.57x |
| 21 | bun | `noble-secp256k1` | js | 4.72 | 12.65x |
| 22 | node | `noble-secp256k1` | js | 5.07 | 12.07x |
| 23 | python | `embit` | python | 5.49 | 4.54x |
| 24 | rust | `secp256kfun` | rust | 5.51 | 61.13x |
| 25 | go | `secp256k1-voi` | go | 6.19 | 38.74x |
| 26 | python | `secp256k1lab` | python | 6.34 | 5.24x |
| 27 | jvm | `bitcoin-s` | scala | 14.8 | 1061.19x |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | c | `libschnorr256k1` | c | 0.02 | 0.96x |
| 2 | c | `libsecp256k1` | c | 0.02 | – |
| 3 | go | `p256k1` | go | 0.03 | 0.56x |
| 4 | go | `splitsecure` | go | 0.03 | 0.59x |
| **5** | **jvm** | **`kotlin`** | **kotlin** | **0.04** | **0.32x** |
| 6 | rust | `libsecp256k1` | rust-secp256k1 | 0.04 | – |
| 7 | rust | `secp256kfun` | rust | 0.04 | 1.12x |
| 8 | rust | `k256` | rust | 0.05 | 1.16x |
| 9 | go | `libsecp256k1` | cgo | 0.05 | – |
| 10 | go | `btcec` | go | 0.05 | 1.01x |
| **11** | **native** | **`kotlin`** | **kotlin** | **0.07** | **0.39x** |
| 12 | jvm | `libschnorr256k1` | jni | 0.11 | 0.96x |
| 13 | jvm | `libsecp256k1` | jni | 0.11 | – |
| 14 | bun | `libsecp256k1` | wasm | 0.12 | – |
| 15 | node | `libsecp256k1` | wasm | 0.13 | – |
| 16 | go | `secp256k1-voi` | go | 0.13 | 2.67x |
| 17 | native | `libsecp256k1` | cinterop | 0.19 | – |
| 18 | bun | `noble-curves` | js | 0.66 | 5.33x |
| 19 | python | `btclib-ecc` | python | 1.24 | 0.02x |
| 20 | python | `embit` | python | 1.25 | 0.02x |
| 21 | node | `noble-curves` | js | 1.41 | 10.83x |
| 22 | python | `secp256k1lab` | python | 1.44 | 0.02x |
| 23 | jvm | `bitcoin-s` | scala | 2.16 | 19.18x |
| 24 | python | `libsecp256k1` | cffi | 61.2 | – |

### Check a secret key

`seckey.verify`: 0 < key < n.

| # | layer | library | binding | µs | vs libsecp256k1 |
|---:|---|---|---|---:|---:|
| 1 | bun | `libsecp256k1` | wasm | 0.00 | – |
| 2 | c | `libschnorr256k1` | c | 0.01 | 0.82x |
| 3 | go | `p256k1` | go | 0.01 | 0.18x |
| 4 | rust | `libsecp256k1` | rust-secp256k1 | 0.01 | – |
| 5 | node | `libsecp256k1` | wasm | 0.01 | – |
| 6 | c | `libsecp256k1` | c | 0.01 | – |
| **7** | **jvm** | **`kotlin`** | **kotlin** | **0.01** | **0.23x** |
| 8 | go | `splitsecure` | go | 0.01 | 0.27x |
| 9 | rust | `secp256kfun` | rust | 0.01 | 1.76x |
| **10** | **native** | **`kotlin`** | **kotlin** | **0.01** | **0.17x** |
| 11 | go | `btcec` | go | 0.01 | 0.45x |
| 12 | rust | `k256` | rust | 0.02 | 2.56x |
| 13 | jvm | `bouncycastle` | java | 0.03 | 0.73x |
| 14 | go | `libsecp256k1` | cgo | 0.03 | – |
| 15 | jvm | `libschnorr256k1` | jni | 0.04 | 0.98x |
| 16 | jvm | `libsecp256k1` | jni | 0.04 | – |
| 17 | jvm | `bitcoin-s` | scala | 0.04 | 1.23x |
| 18 | go | `secp256k1-voi` | go | 0.05 | 1.58x |
| 19 | native | `libsecp256k1` | cinterop | 0.08 | – |
| 20 | bun | `noble-curves` | js | 0.25 | 73.56x |
| 21 | python | `libsecp256k1` | cffi | 0.26 | – |
| 22 | node | `noble-curves` | js | 0.38 | 54.00x |
| 23 | python | `embit` | python | 0.38 | 1.45x |
| 24 | python | `secp256k1lab` | python | 0.47 | 1.81x |
| 25 | python | `btclib-ecc` | python | 0.58 | 2.24x |
| 26 | node | `noble-secp256k1` | js | 1.20 | 171.01x |
| 27 | bun | `noble-secp256k1` | js | 1.44 | 424.71x |

### Versions

- c: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2a8798e
- rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- go: Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- native: Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1
- jvm: Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.0.5, kotlin schnorr256k1-kotlin v1.0.1, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
- node: Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- bun: Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- python: Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored)

Every operation, layer and device: [results/report.md](results/report.md).

<!-- results:end -->

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
scripts/table.py --readme
```

- `run-host.sh` runs the c, jvm and native layers, and the rust, go, node, bun and python
  layers whose toolchain is installed (it says which it skipped). It needs cmake, a C compiler
  and a JDK; `--layers "c rust"` picks layers.
- `run-android.sh` runs the android layer on the device adb sees.
- `run-android-c.sh` builds `c/` with the NDK (`ANDROID_NDK_HOME`) and runs it on that device.
- `table.py --readme` writes this README's Results and `results/report.md` from the dedicated
  runs under `results/`, or, while there are none, the full shared ones. Commit the JSON files, the README and the report together. Without
  `--readme`, `table.py` prints the tables of any results you give it, quick and shared runs
  included.
- `--quick` makes any of them a smoke run, whose numbers are never comparable.
- CI times nothing. On Linux, macOS and an Android emulator it runs each layer's checks
  (`build/c/bench check`, `:bench:jvmTest`, `<layer>/run.sh --check`, `run-android*.sh --check`):
  every library's outputs against the vectors and the dataset, and the known-issue probes.
- A dedicated run means a machine doing nothing else: a laptop on AC power, a phone charged,
  cool and in flight mode.
