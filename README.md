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
| `node`, `bun` | Node.js, Bun | [tiny-secp256k1](https://github.com/bitcoinjs/tiny-secp256k1) (libsecp256k1 as wasm), [@noble/curves](https://github.com/paulmillr/noble-curves), [@noble/secp256k1](https://github.com/paulmillr/noble-secp256k1) ([details](js/README.md)) |
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

One table per operation, verification first: a Nostr client verifies every event every relay sends.
Each table ranks every library from every caller. Ours are in bold: libschnorr256k1-kotlin, and
libschnorr256k1 (C), called directly or through libschnorr256k1-kmp. **Caller** is the runtime the
app's code runs on; **via** is how it reaches the library: directly, through a binding package
(named, with its version), or by being written in the caller's own language ("pure"). Runs from a
dedicated machine are what the tables are for; until there is one, a full run on a shared cloud VM
stands in, labelled as such, and the first dedicated run replaces it. The newest device's runs are
here, and every device's in [results/report.md](results/report.md). The runs are the JSON files in
`results/<layer>/`; `scripts/table.py --readme` writes this section and the report from them, and CI
fails when either is stale.

<!-- results:begin: scripts/table.py --readme writes this section; do not edit it by hand -->

Measured on Cloud VM (4 vCPU) (Intel(R) Xeon(R) Processor @ 2.30GHz), **a shared machine** (a cloud VM: other work may have run beside it, so the numbers are noisier than a dedicated run's, which will replace them), single thread, 2026-11-09. Each table ranks one operation by its median µs per call. A wrong result is listed last, unranked; KI-n marks a known issue (below).

### Verify real Nostr events

`nostr.verify`: the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 30.1 |
| 2 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 30.4 |
| 3 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 30.5 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.5 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 31.5 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.6 |
| 7 | Go | `splitsecure` | pure Go | 37.8 |
| **8** | **C** | **`libschnorr256k1`** | **direct** | **43.7** |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **44.0** |
| **10** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **44.6** |
| 11 | Rust | `k256` | pure Rust | 51.9 |
| 12 | Rust | `secp256kfun` | pure Rust | 65.3 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **66.0** |
| 14 | Go | `secp256k1-voi` | pure Go | 71.2 |
| 15 | Go | `p256k1` | pure Go | 95.6 |
| 16 | JVM | `bouncycastle` | pure Java | 124.1 |
| 17 | Go | `btcec` | pure Go | 158.4 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 177.7 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.0 |
| 20 | Bun | `noble-curves` | pure JavaScript | 367.9 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 660.9 |
| 22 | Python | `btclib-ecc` | pure Python | 916.9 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1044.2 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1110.6 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 1902.1 |
| 26 | Python | `embit` | pure Python | 2081.7 |
| 27 | Python | `secp256k1lab` | pure Python | 7728.7 |
| 28 | JVM | `nostr-java` | pure Java | 11025.0 |
| 29 | Python | `bip340-reference` | pure Python | 72931.0 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 29.8 |
| 2 | C | `libsecp256k1` | direct | 29.9 |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.3 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 30.7 |
| 5 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 30.9 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 31.1 |
| 7 | Go | `splitsecure` | pure Go | 38.3 |
| 8 | Rust | `k256` | pure Rust | 52.0 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **52.3** |
| **10** | **C** | **`libschnorr256k1`** | **direct** | **53.2** |
| **11** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **55.3** |
| 12 | Rust | `secp256kfun` | pure Rust | 65.1 |
| 13 | Go | `secp256k1-voi` | pure Go | 73.0 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **77.6** |
| 15 | Go | `p256k1` | pure Go | 95.9 |
| 16 | JVM | `bouncycastle` | pure Java | 119.9 |
| 17 | Go | `btcec` | pure Go | 158.9 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 178.6 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 183.7 |
| 20 | Bun | `noble-curves` | pure JavaScript | 353.1 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 666.4 |
| 22 | Python | `btclib-ecc` | pure Python | 880.5 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1048.7 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1136.1 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 1884.8 |
| 26 | Python | `embit` | pure Python | 1971.5 |
| 27 | Python | `secp256k1lab` | pure Python | 7760.6 |
| 28 | JVM | `nostr-java` | pure Java | 11041.7 |
| 29 | Python | `bip340-reference` | pure Python | 72176.0 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **25.7** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **27.5** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **27.8** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 27.9 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 28.1 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 28.2 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 28.5 |
| 8 | C | `libsecp256k1` | direct | 28.6 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 31.6 |
| 10 | Go | `splitsecure` | pure Go | 35.3 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **45.6** |
| 12 | Rust | `k256` | pure Rust | 49.3 |
| 13 | Rust | `secp256kfun` | pure Rust | 65.3 |
| 14 | Go | `secp256k1-voi` | pure Go | 70.6 |
| 15 | Go | `p256k1` | pure Go | 84.5 |
| 16 | JVM | `bouncycastle` | pure Java | 110.7 |
| 17 | Go | `btcec` | pure Go | 158.3 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 174.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 176.5 |
| 20 | Bun | `noble-curves` | pure JavaScript | 336.8 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 670.7 |
| 22 | Python | `btclib-ecc` | pure Python | 859.1 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1036.4 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1127.4 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 1884.8 |
| 26 | Python | `embit` | pure Python | 1903.4 |
| 27 | Python | `secp256k1lab` | pure Python | 7767.2 |
| 28 | JVM | `nostr-java` | pure Java | 10791.6 |
| 29 | Python | `bip340-reference` | pure Python | 70871.0 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **15.9 KI-1** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **17.6 KI-1** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **28.1** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **42.2** |
| 5 | Go | `p256k1` | pure Go | 125.5 |
| 6 | Python | `btclib-ecc` | pure Python | 890.1 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **3.63 KI-1** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **3.77 KI-1** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **16.6** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **25.7** |
| 5 | Go | `p256k1` | pure Go | 124.5 |
| 6 | Python | `btclib-ecc` | pure Python | 708.2 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.0 |
| 2 | C | `libsecp256k1` | direct | 30.1 |
| 3 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 30.3 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 31.0 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 31.6 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 31.6 |
| 7 | Go | `splitsecure` | pure Go | 38.6 |
| **8** | **C** | **`libschnorr256k1`** | **direct** | **39.5** |
| **9** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **39.8** |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **41.8** |
| 11 | Rust | `k256` | pure Rust | 52.2 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **61.3** |
| 13 | Rust | `secp256kfun` | pure Rust | 66.1 |
| 14 | Go | `secp256k1-voi` | pure Go | 70.7 |
| 15 | Go | `p256k1` | pure Go | 94.6 |
| 16 | JVM | `bouncycastle` | pure Java | 120.0 |
| 17 | Go | `btcec` | pure Go | 158.5 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 180.1 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 184.5 |
| 20 | Bun | `noble-curves` | pure JavaScript | 372.4 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 660.8 |
| 22 | Python | `btclib-ecc` | pure Python | 892.0 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1053.5 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1096.4 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 1907.8 |
| 26 | Python | `embit` | pure Python | 1996.2 |
| 27 | Python | `secp256k1lab` | pure Python | 7787.2 |
| 28 | JVM | `nostr-java` | pure Java | 11173.4 |
| 29 | Python | `bip340-reference` | pure Python | 73477.2 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **24.3** |
| **2** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **24.4** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **25.3** |
| 4 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 28.1 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 28.2 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 28.3 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 28.8 |
| 8 | C | `libsecp256k1` | direct | 30.1 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 30.8 |
| 10 | Go | `splitsecure` | pure Go | 35.6 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **41.8** |
| 12 | Rust | `k256` | pure Rust | 49.0 |
| 13 | Rust | `secp256kfun` | pure Rust | 65.0 |
| 14 | Go | `secp256k1-voi` | pure Go | 68.8 |
| 15 | Go | `p256k1` | pure Go | 84.0 |
| 16 | JVM | `bouncycastle` | pure Java | 109.4 |
| 17 | Go | `btcec` | pure Go | 154.5 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 174.2 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 176.3 |
| 20 | Bun | `noble-curves` | pure JavaScript | 344.2 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 657.0 |
| 22 | Python | `btclib-ecc` | pure Python | 848.7 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1022.8 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1110.8 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 1864.9 |
| 26 | Python | `embit` | pure Python | 1891.2 |
| 27 | Python | `secp256k1lab` | pure Python | 7755.0 |
| 28 | JVM | `nostr-java` | pure Java | 10887.5 |
| 29 | Python | `bip340-reference` | pure Python | 70295.1 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Go | `libsecp256k1` | cgo (the bench's own binding) | 27.0 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 27.0 |
| 3 | C | `libsecp256k1` | direct | 27.3 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 27.4 |
| **5** | **C** | **`libschnorr256k1`** | **direct** | **29.0** |
| **6** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **29.3** |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **30.5** |
| 8 | Rust | `k256` | pure Rust | 38.7 |
| 9 | Go | `splitsecure` | pure Go | 39.1 |
| **10** | **JVM** | **`kotlin`** | **pure Kotlin** | **47.4** |
| 11 | Go | `btcec` | pure Go | 66.1 |
| 12 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 68.6 |
| 13 | Go | `secp256k1-voi` | pure Go | 74.1 |
| 14 | Rust | `secp256kfun` | pure Rust | 81.9 |
| 15 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 86.6 |
| 16 | Go | `p256k1` | pure Go | 105.1 |
| 17 | JVM | `bouncycastle` | pure Java | 123.4 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 166.0 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 170.9 |
| 20 | Bun | `noble-curves` | pure JavaScript | 612.3 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 904.7 |
| 22 | Python | `btclib-ecc` | pure Python | 1283.9 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1839.7 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 2285.4 |
| 25 | Python | `embit` | pure Python | 4018.6 |
| 26 | JVM | `bitcoin-s` | pure Scala | 6060.4 |
| 27 | Python | `secp256k1lab` | pure Python | 11466.6 |
| 28 | JVM | `nostr-java` | pure Java | 21923.9 |
| 29 | Python | `bip340-reference` | pure Python | 141590.6 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **16.1** |
| **2** | **JVM** | **`kotlin`** | **pure Kotlin** | **24.2** |
| 3 | C | `libsecp256k1` | direct | 26.6 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 27.2 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 27.3 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 29.7 |
| 7 | Go | `splitsecure` | pure Go | 39.3 |
| 8 | Rust | `k256` | pure Rust | 39.5 |
| 9 | Go | `btcec` | pure Go | 63.3 |
| 10 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 69.3 |
| 11 | Go | `secp256k1-voi` | pure Go | 71.4 |
| 12 | JVM | `bouncycastle` | pure Java | 76.0 |
| 13 | Rust | `secp256kfun` | pure Rust | 82.1 |
| 14 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 85.6 |
| 15 | Go | `p256k1` | pure Go | 103.3 |
| 16 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 163.3 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 171.2 |
| 18 | Bun | `noble-curves` | pure JavaScript | 615.7 |
| 19 | Bun | `noble-secp256k1` | pure JavaScript | 817.9 |
| 20 | Python | `btclib-ecc` | pure Python | 1315.5 |
| 21 | Node.js | `noble-curves` | pure JavaScript | 1754.4 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 2376.2 |
| 23 | Python | `embit` | pure Python | 4101.1 |
| 24 | JVM | `bitcoin-s` | pure Scala | 5960.1 |
| 25 | Python | `secp256k1lab` | pure Python | 11479.6 |
| 26 | JVM | `nostr-java` | pure Java | 22266.8 |
| 27 | Python | `bip340-reference` | pure Python | 142976.3 |
| – | **C** | **`libschnorr256k1`** | **direct** | **14.7 **wrong** KI-2** |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **16.0 **wrong** KI-2** |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 12.9 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 13.2 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 13.3 |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **13.7** |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 13.7 |
| **6** | **C** | **`libschnorr256k1`** | **direct** | **14.3** |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **15.0** |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 15.3 |
| 9 | Rust | `k256` | pure Rust | 19.1 |
| 10 | Go | `splitsecure` | pure Go | 19.1 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **22.9** |
| 12 | Go | `secp256k1-voi` | pure Go | 24.4 |
| 13 | Go | `btcec` | pure Go | 31.4 |
| 14 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 34.0 |
| 15 | Go | `p256k1` | pure Go | 40.7 |
| 16 | Rust | `secp256kfun` | pure Rust | 41.4 |
| 17 | JVM | `bouncycastle` | pure Java | 60.3 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 79.4 |
| 19 | Bun | `noble-secp256k1` | pure JavaScript | 84.7 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 85.3 |
| 21 | Bun | `noble-curves` | pure JavaScript | 109.0 |
| 22 | Python | `btclib-ecc` | pure Python | 184.7 |
| 23 | Node.js | `noble-secp256k1` | pure JavaScript | 196.9 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 311.7 |
| 25 | JVM | `bitcoin-s` | pure Scala | 387.4 |
| 26 | Python | `embit` | pure Python | 1304.9 |
| 27 | Python | `secp256k1lab` | pure Python | 1969.7 |
| 28 | Python | `bip340-reference` | pure Python | 36083.6 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 26.0 |
| 2 | C | `libsecp256k1` | direct | 26.7 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 27.0 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 27.9 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 28.4 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 30.0 |
| 7 | Go | `splitsecure` | pure Go | 40.2 |
| 8 | Rust | `k256` | pure Rust | 44.2 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **44.5** |
| 10 | Rust | `secp256kfun` | pure Rust | 46.3 |
| **11** | **C** | **`libschnorr256k1`** | **direct** | **47.4** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **47.9** |
| 13 | Go | `secp256k1-voi` | pure Go | 63.7 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **65.6** |
| 15 | Go | `btcec` | pure Go | 125.3 |
| 16 | Go | `p256k1` | pure Go | 148.1 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 158.0 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 162.3 |
| 19 | JVM | `bouncycastle` | pure Java | 187.5 |
| 20 | JVM | `bitcoin-s` | pure Scala | 543.0 |
| 21 | Bun | `noble-curves` | pure JavaScript | 543.2 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 732.1 |
| 23 | Python | `btclib-ecc` | pure Python | 770.5 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1961.1 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2256.6 |
| 26 | Python | `secp256k1lab` | pure Python | 5852.8 |
| 27 | Python | `bip340-reference` | pure Python | 36089.7 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 26.6 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 26.7 |
| 3 | C | `libsecp256k1` | direct | 26.9 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 27.6 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 27.9 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 28.3 |
| 7 | Go | `splitsecure` | pure Go | 40.7 |
| 8 | Rust | `k256` | pure Rust | 43.6 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **44.5** |
| 10 | Rust | `secp256kfun` | pure Rust | 46.7 |
| **11** | **C** | **`libschnorr256k1`** | **direct** | **46.9** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **48.0** |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **65.5** |
| 14 | Go | `secp256k1-voi` | pure Go | 67.1 |
| 15 | Go | `btcec` | pure Go | 124.9 |
| 16 | Go | `p256k1` | pure Go | 144.4 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 156.1 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 161.5 |
| 19 | JVM | `bouncycastle` | pure Java | 181.0 |
| 20 | JVM | `bitcoin-s` | pure Scala | 494.8 |
| 21 | Bun | `noble-curves` | pure JavaScript | 574.9 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 739.9 |
| 23 | Python | `btclib-ecc` | pure Python | 771.7 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1990.8 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2259.4 |
| 26 | Python | `secp256k1lab` | pure Python | 5843.8 |
| 27 | Python | `bip340-reference` | pure Python | 36309.1 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 26.2 |
| 2 | Go | `libsecp256k1` | cgo (the bench's own binding) | 26.6 |
| 3 | C | `libsecp256k1` | direct | 26.8 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 26.8 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 27.8 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 28.4 |
| 7 | Go | `splitsecure` | pure Go | 33.1 |
| 8 | Rust | `k256` | pure Rust | 43.8 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **44.7** |
| 10 | Rust | `secp256kfun` | pure Rust | 46.7 |
| **11** | **C** | **`libschnorr256k1`** | **direct** | **48.0** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **48.2** |
| 13 | Go | `secp256k1-voi` | pure Go | 63.6 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **67.5** |
| 15 | JVM | `bouncycastle` | pure Java | 100.0 |
| 16 | Go | `btcec` | pure Go | 121.4 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 156.0 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 163.3 |
| 19 | Go | `p256k1` | pure Go | 171.4 |
| 20 | JVM | `bitcoin-s` | pure Scala | 506.9 |
| 21 | Bun | `noble-curves` | pure JavaScript | 545.8 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 757.5 |
| 23 | Python | `btclib-ecc` | pure Python | 795.3 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1982.8 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2306.3 |
| 26 | Python | `secp256k1lab` | pure Python | 5845.6 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **0.00** |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.01 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.04 |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.07** |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.09 |
| 6 | Go | `splitsecure` | pure Go | 0.10 |
| 7 | C | `libsecp256k1` | direct | 0.10 |
| 8 | Rust | `k256` | pure Rust | 0.13 |
| **9** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.14** |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.16 |
| 11 | Go | `btcec` | pure Go | 0.24 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.25** |
| 13 | Go | `p256k1` | pure Go | 0.27 |
| 14 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.37 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.42 |
| 16 | JVM | `bouncycastle` | pure Java | 0.51 |
| 17 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.21 |
| 18 | Bun | `noble-curves` | pure JavaScript | 1.55 |
| 19 | Node.js | `noble-curves` | pure JavaScript | 3.95 |
| 20 | Python | `btclib-ecc` | pure Python | 4.32 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 4.72 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 5.07 |
| 23 | Python | `embit` | pure Python | 5.49 |
| 24 | Rust | `secp256kfun` | pure Rust | 5.51 |
| 25 | Go | `secp256k1-voi` | pure Go | 6.19 |
| 26 | Python | `secp256k1lab` | pure Python | 6.34 |
| 27 | JVM | `bitcoin-s` | pure Scala | 14.8 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **0.02** |
| 2 | C | `libsecp256k1` | direct | 0.02 |
| 3 | Go | `p256k1` | pure Go | 0.03 |
| 4 | Go | `splitsecure` | pure Go | 0.03 |
| **5** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.04** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.04 |
| 7 | Rust | `secp256kfun` | pure Rust | 0.04 |
| 8 | Rust | `k256` | pure Rust | 0.05 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.05 |
| 10 | Go | `btcec` | pure Go | 0.05 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.07** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.11** |
| 13 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.11 |
| 14 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.12 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.13 |
| 16 | Go | `secp256k1-voi` | pure Go | 0.13 |
| 17 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.19 |
| 18 | Bun | `noble-curves` | pure JavaScript | 0.66 |
| 19 | Python | `btclib-ecc` | pure Python | 1.24 |
| 20 | Python | `embit` | pure Python | 1.25 |
| 21 | Node.js | `noble-curves` | pure JavaScript | 1.41 |
| 22 | Python | `secp256k1lab` | pure Python | 1.44 |
| 23 | JVM | `bitcoin-s` | pure Scala | 2.16 |
| 24 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 61.2 |

### Check a secret key

`seckey.verify`: 0 < key < n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.00 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.01** |
| 3 | Go | `p256k1` | pure Go | 0.01 |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.01 |
| 5 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.01 |
| 6 | C | `libsecp256k1` | direct | 0.01 |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.01** |
| 8 | Go | `splitsecure` | pure Go | 0.01 |
| 9 | Rust | `secp256kfun` | pure Rust | 0.01 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.01** |
| 11 | Go | `btcec` | pure Go | 0.01 |
| 12 | Rust | `k256` | pure Rust | 0.02 |
| 13 | JVM | `bouncycastle` | pure Java | 0.03 |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.03 |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.04** |
| 16 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.04 |
| 17 | JVM | `bitcoin-s` | pure Scala | 0.04 |
| 18 | Go | `secp256k1-voi` | pure Go | 0.05 |
| 19 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.08 |
| 20 | Bun | `noble-curves` | pure JavaScript | 0.25 |
| 21 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.26 |
| 22 | Node.js | `noble-curves` | pure JavaScript | 0.38 |
| 23 | Python | `embit` | pure Python | 0.38 |
| 24 | Python | `secp256k1lab` | pure Python | 0.47 |
| 25 | Python | `btclib-ecc` | pure Python | 0.58 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 1.20 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 1.44 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2a8798e
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1
- JVM: Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.0.5, kotlin schnorr256k1-kotlin v1.0.1, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
- Node.js: Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Bun: Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Python: Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored)

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
