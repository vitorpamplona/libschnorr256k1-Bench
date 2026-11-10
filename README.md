# libschnorr256k1-bench

BIP-340 Schnorr and secp256k1 libraries, timed on every layer an app calls them from: C, the JVM,
Kotlin/Native, Android, Rust, Go, Node.js, Bun and Python. Every library gets the same
[inputs](vectors/README.md) and the same [real Nostr events](datasets/README.md), and its outputs
must match the same digests before it is timed. Which call each library makes for each operation,
and how they are timed, is in [results/SCHEMA.md](results/SCHEMA.md); each layer's libraries are in
its README ([jvm](bench/README-jvm-libraries.md), [rust](rust/README.md), [go](go/README.md),
[node and bun](js/README.md), [python](python/README.md)).

## Results

Ours are in bold: [libschnorr256k1](https://github.com/vitorpamplona/libschnorr256k1) (C), called
directly or through [libschnorr256k1-kmp](https://github.com/vitorpamplona/libschnorr256k1-kmp),
and [libschnorr256k1-kotlin](https://github.com/vitorpamplona/libschnorr256k1-kotlin). **Caller**
is the runtime the app's code runs on; **via** is how it reaches the library.

<!-- results:begin: scripts/table.py --readme writes this section; do not edit it by hand -->

Measured on Cloud VM (4 vCPU) (Intel(R) Xeon(R) Processor @ 2.10GHz), **a shared machine** (a cloud VM: other work may have run beside it, so the numbers are noisier than a dedicated run's, which will replace them), single thread, 2026-11-09 to 2026-11-10. Each table ranks one operation by its median µs per call. A wrong result is listed last, unranked; KI-n marks a known issue ([results/SCHEMA.md](results/SCHEMA.md#known-issues)).

### Verify real Nostr events

`nostr.verify`: the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **30.6** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **31.0** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **31.1** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.1 |
| 5 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.4 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.7 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 37.9 |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 38.1 |
| 9 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 38.1 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.2 |
| 11 | C | `libsecp256k1` | direct | 38.2 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **41.4** |
| 13 | Go | `splitsecure` | pure Go | 46.2 |
| **14** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **53.8** |
| 15 | Rust | `k256` | pure Rust | 62.3 |
| **16** | **JVM** | **`kotlin`** | **pure Kotlin** | **62.8** |
| **17** | **JVM** | **`kotlin`** | **pure Kotlin** | **76.2** |
| 18 | Rust | `secp256kfun` | pure Rust | 80.0 |
| 19 | Go | `secp256k1-voi` | pure Go | 84.0 |
| 20 | Go | `p256k1` | pure Go | 113.0 |
| 21 | JVM | `bouncycastle` | pure Java | 139.5 |
| 22 | JVM | `bouncycastle` | pure Java | 144.3 |
| 23 | Go | `btcec` | pure Go | 191.5 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.0 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 220.3 |
| 26 | Bun | `noble-curves` | pure JavaScript | 507.6 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 972.2 |
| 28 | JVM | `bitcoin-s` | pure Scala | 1046.2 |
| 29 | Python | `btclib-ecc` | pure Python | 1055.3 |
| 30 | JVM | `bitcoin-s` | pure Scala | 1265.3 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 1391.5 |
| 32 | Node.js | `noble-secp256k1` | pure JavaScript | 2386.1 |
| 33 | Python | `embit` | pure Python | 2406.1 |
| 34 | Python | `secp256k1lab` | pure Python | 9146.8 |
| 35 | JVM | `nostr-java` | pure Java | 12989.6 |
| 36 | JVM | `nostr-java` | pure Java | 13167.7 |
| 37 | Python | `bip340-reference` | pure Python | 88496.2 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **28.0** |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.0 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **36.1** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.5 |
| 5 | C | `libsecp256k1` | direct | 36.8 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 36.9 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **37.1** |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.5 |
| 9 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 37.6 |
| 10 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.7 |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 39.9 |
| 12 | Go | `splitsecure` | pure Go | 45.7 |
| **13** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **48.8** |
| 14 | Rust | `k256` | pure Rust | 62.2 |
| **15** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **65.0** |
| **16** | **JVM** | **`kotlin`** | **pure Kotlin** | **73.8** |
| 17 | Rust | `secp256kfun` | pure Rust | 79.3 |
| 18 | Go | `secp256k1-voi` | pure Go | 85.0 |
| **19** | **JVM** | **`kotlin`** | **pure Kotlin** | **92.9** |
| 20 | Go | `p256k1` | pure Go | 113.0 |
| 21 | JVM | `bouncycastle` | pure Java | 142.1 |
| 22 | JVM | `bouncycastle` | pure Java | 143.9 |
| 23 | Go | `btcec` | pure Go | 195.0 |
| 24 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 217.0 |
| 25 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 224.3 |
| 26 | Bun | `noble-curves` | pure JavaScript | 483.1 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 1000.7 |
| 28 | Python | `btclib-ecc` | pure Python | 1091.0 |
| 29 | JVM | `bitcoin-s` | pure Scala | 1214.0 |
| 30 | JVM | `bitcoin-s` | pure Scala | 1340.7 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 1375.6 |
| 32 | Python | `embit` | pure Python | 2411.1 |
| 33 | Node.js | `noble-secp256k1` | pure JavaScript | 2452.0 |
| 34 | Python | `secp256k1lab` | pure Python | 9285.3 |
| 35 | JVM | `nostr-java` | pure Java | 13235.4 |
| 36 | JVM | `nostr-java` | pure Java | 13439.9 |
| 37 | Python | `bip340-reference` | pure Python | 90700.2 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **25.7** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **26.1** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **26.6** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **31.1** |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.1 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.2 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.0 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 34.0 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **34.3** |
| 10 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 34.4 |
| 11 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 35.9 |
| 12 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.9 |
| 13 | C | `libsecp256k1` | direct | 36.2 |
| 14 | Go | `splitsecure` | pure Go | 42.2 |
| **15** | **JVM** | **`kotlin`** | **pure Kotlin** | **49.6** |
| **16** | **JVM** | **`kotlin`** | **pure Kotlin** | **51.1** |
| 17 | Rust | `k256` | pure Rust | 58.9 |
| 18 | Rust | `secp256kfun` | pure Rust | 79.6 |
| 19 | Go | `secp256k1-voi` | pure Go | 83.4 |
| 20 | Go | `p256k1` | pure Go | 102.1 |
| 21 | JVM | `bouncycastle` | pure Java | 128.4 |
| 22 | JVM | `bouncycastle` | pure Java | 130.6 |
| 23 | Go | `btcec` | pure Go | 189.3 |
| 24 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 198.2 |
| 25 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 212.0 |
| 26 | Bun | `noble-curves` | pure JavaScript | 449.5 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 954.5 |
| 28 | Python | `btclib-ecc` | pure Python | 1069.0 |
| 29 | JVM | `bitcoin-s` | pure Scala | 1195.3 |
| 30 | JVM | `bitcoin-s` | pure Scala | 1273.1 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 1308.9 |
| 32 | Python | `embit` | pure Python | 2378.2 |
| 33 | Node.js | `noble-secp256k1` | pure JavaScript | 2437.3 |
| 34 | Python | `secp256k1lab` | pure Python | 9130.6 |
| 35 | JVM | `nostr-java` | pure Java | 12633.0 |
| 36 | JVM | `nostr-java` | pure Java | 13102.1 |
| 37 | Python | `bip340-reference` | pure Python | 86412.6 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **16.7** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **17.8** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **18.9** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **24.8** |
| **5** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **34.2** |
| **6** | **JVM** | **`kotlin`** | **pure Kotlin** | **38.8** |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.7** |
| 8 | Go | `p256k1` | pure Go | 153.9 |
| 9 | Python | `btclib-ecc` | pure Python | 1001.4 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.3** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.8** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.9** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **14.1** |
| **5** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **21.0** |
| **6** | **JVM** | **`kotlin`** | **pure Kotlin** | **22.8** |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **30.8** |
| 8 | Go | `p256k1` | pure Go | 146.1 |
| 9 | Python | `btclib-ecc` | pure Python | 817.5 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **29.3** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **29.4** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **30.6** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 35.2 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.6 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 37.0 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.2 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 38.0 |
| 9 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 38.2 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **38.3** |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.4 |
| 12 | C | `libsecp256k1` | direct | 38.8 |
| 13 | Go | `splitsecure` | pure Go | 46.0 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.6** |
| **15** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **52.0** |
| 16 | Rust | `k256` | pure Rust | 59.4 |
| **17** | **JVM** | **`kotlin`** | **pure Kotlin** | **74.0** |
| 18 | Rust | `secp256kfun` | pure Rust | 77.7 |
| 19 | Go | `secp256k1-voi` | pure Go | 85.4 |
| 20 | Go | `p256k1` | pure Go | 115.3 |
| 21 | JVM | `bouncycastle` | pure Java | 136.8 |
| 22 | JVM | `bouncycastle` | pure Java | 144.3 |
| 23 | Go | `btcec` | pure Go | 193.6 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.5 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 221.1 |
| 26 | Bun | `noble-curves` | pure JavaScript | 484.4 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 996.3 |
| 28 | Python | `btclib-ecc` | pure Python | 1062.9 |
| 29 | JVM | `bitcoin-s` | pure Scala | 1196.6 |
| 30 | JVM | `bitcoin-s` | pure Scala | 1253.1 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 1354.2 |
| 32 | Node.js | `noble-secp256k1` | pure JavaScript | 2357.4 |
| 33 | Python | `embit` | pure Python | 2439.6 |
| 34 | Python | `secp256k1lab` | pure Python | 9545.3 |
| 35 | JVM | `nostr-java` | pure Java | 13291.8 |
| 36 | JVM | `nostr-java` | pure Java | 13296.0 |
| 37 | Python | `bip340-reference` | pure Python | 84412.2 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **19.9** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **24.8** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **25.7** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **29.6** |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.1 |
| **6** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **32.5** |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.5 |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.7 |
| 9 | C | `libsecp256k1` | direct | 33.8 |
| 10 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.2 |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.3 |
| 12 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 35.3 |
| 13 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 36.1 |
| 14 | Go | `splitsecure` | pure Go | 43.1 |
| **15** | **JVM** | **`kotlin`** | **pure Kotlin** | **46.7** |
| **16** | **JVM** | **`kotlin`** | **pure Kotlin** | **48.5** |
| 17 | Rust | `k256` | pure Rust | 59.9 |
| 18 | Rust | `secp256kfun` | pure Rust | 78.2 |
| 19 | Go | `secp256k1-voi` | pure Go | 85.9 |
| 20 | Go | `p256k1` | pure Go | 100.5 |
| 21 | JVM | `bouncycastle` | pure Java | 126.4 |
| 22 | JVM | `bouncycastle` | pure Java | 133.4 |
| 23 | Go | `btcec` | pure Go | 186.1 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 208.2 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.4 |
| 26 | Bun | `noble-curves` | pure JavaScript | 466.1 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 922.0 |
| 28 | Python | `btclib-ecc` | pure Python | 1044.8 |
| 29 | JVM | `bitcoin-s` | pure Scala | 1197.2 |
| 30 | JVM | `bitcoin-s` | pure Scala | 1274.6 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 1314.5 |
| 32 | Python | `embit` | pure Python | 2347.3 |
| 33 | Node.js | `noble-secp256k1` | pure JavaScript | 2484.1 |
| 34 | Python | `secp256k1lab` | pure Python | 9215.8 |
| 35 | JVM | `nostr-java` | pure Java | 12529.3 |
| 36 | JVM | `nostr-java` | pure Java | 12974.1 |
| 37 | Python | `bip340-reference` | pure Python | 89153.3 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **21.0** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **21.1** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **21.5** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **27.7** |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.9 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.1 |
| 7 | C | `libsecp256k1` | direct | 33.1 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.2 |
| 9 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.9 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.3 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **37.0** |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **45.5** |
| 13 | Rust | `k256` | pure Rust | 46.8 |
| 14 | Go | `splitsecure` | pure Go | 47.3 |
| **15** | **JVM** | **`kotlin`** | **pure Kotlin** | **55.7** |
| 16 | Go | `btcec` | pure Go | 78.4 |
| 17 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 84.6 |
| 18 | Go | `secp256k1-voi` | pure Go | 92.2 |
| 19 | Rust | `secp256kfun` | pure Rust | 95.7 |
| 20 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 104.6 |
| 21 | Go | `p256k1` | pure Go | 123.5 |
| 22 | JVM | `bouncycastle` | pure Java | 151.7 |
| 23 | JVM | `bouncycastle` | pure Java | 162.5 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.1 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 204.4 |
| 26 | Bun | `noble-curves` | pure JavaScript | 843.7 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 1273.4 |
| 28 | Python | `btclib-ecc` | pure Python | 1557.0 |
| 29 | Node.js | `noble-curves` | pure JavaScript | 2112.5 |
| 30 | Node.js | `noble-secp256k1` | pure JavaScript | 2913.4 |
| 31 | Python | `embit` | pure Python | 4789.2 |
| 32 | JVM | `bitcoin-s` | pure Scala | 6303.9 |
| 33 | JVM | `bitcoin-s` | pure Scala | 7248.0 |
| 34 | Python | `secp256k1lab` | pure Python | 13953.7 |
| 35 | JVM | `nostr-java` | pure Java | 25856.0 |
| 36 | JVM | `nostr-java` | pure Java | 26677.3 |
| 37 | Python | `bip340-reference` | pure Python | 177101.4 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.3** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.9** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **11.2** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **15.2** |
| **5** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **20.2** |
| **6** | **JVM** | **`kotlin`** | **pure Kotlin** | **23.9** |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **28.4** |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.9 |
| 9 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.9 |
| 10 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.1 |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.3 |
| 12 | C | `libsecp256k1` | direct | 34.5 |
| 13 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 35.4 |
| 14 | Rust | `k256` | pure Rust | 47.8 |
| 15 | Go | `splitsecure` | pure Go | 50.3 |
| 16 | Go | `btcec` | pure Go | 83.6 |
| 17 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 84.4 |
| 18 | Go | `secp256k1-voi` | pure Go | 89.6 |
| 19 | JVM | `bouncycastle` | pure Java | 89.8 |
| 20 | JVM | `bouncycastle` | pure Java | 93.3 |
| 21 | Rust | `secp256kfun` | pure Rust | 98.7 |
| 22 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 106.0 |
| 23 | Go | `p256k1` | pure Go | 125.7 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 198.6 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 209.1 |
| 26 | Bun | `noble-curves` | pure JavaScript | 892.0 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 1270.0 |
| 28 | Python | `btclib-ecc` | pure Python | 1524.7 |
| 29 | Node.js | `noble-curves` | pure JavaScript | 2162.1 |
| 30 | Node.js | `noble-secp256k1` | pure JavaScript | 2938.6 |
| 31 | Python | `embit` | pure Python | 4710.2 |
| 32 | JVM | `bitcoin-s` | pure Scala | 6630.5 |
| 33 | JVM | `bitcoin-s` | pure Scala | 7174.7 |
| 34 | Python | `secp256k1lab` | pure Python | 13802.8 |
| 35 | JVM | `nostr-java` | pure Java | 25810.6 |
| 36 | JVM | `nostr-java` | pure Java | 26620.8 |
| 37 | Python | `bip340-reference` | pure Python | 172260.3 |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.2** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.3** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **10.6** |
| **4** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **12.0** |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 12.3 |
| 6 | C | `libsecp256k1` | direct | 15.4 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 15.5 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 15.8 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **16.2** |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 16.3 |
| 11 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 16.6 |
| 12 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 18.7 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **22.7** |
| 14 | Rust | `k256` | pure Rust | 23.1 |
| 15 | Go | `splitsecure` | pure Go | 24.2 |
| **16** | **JVM** | **`kotlin`** | **pure Kotlin** | **27.1** |
| 17 | Go | `secp256k1-voi` | pure Go | 28.8 |
| 18 | Go | `btcec` | pure Go | 39.9 |
| 19 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 41.9 |
| 20 | Rust | `secp256kfun` | pure Rust | 48.5 |
| 21 | Go | `p256k1` | pure Go | 50.2 |
| 22 | JVM | `bouncycastle` | pure Java | 68.1 |
| 23 | JVM | `bouncycastle` | pure Java | 72.8 |
| 24 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 97.8 |
| 25 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 99.6 |
| 26 | Bun | `noble-secp256k1` | pure JavaScript | 123.7 |
| 27 | Bun | `noble-curves` | pure JavaScript | 156.5 |
| 28 | Python | `btclib-ecc` | pure Python | 221.8 |
| 29 | Node.js | `noble-secp256k1` | pure JavaScript | 250.2 |
| 30 | JVM | `bitcoin-s` | pure Scala | 388.5 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 388.7 |
| 32 | JVM | `bitcoin-s` | pure Scala | 465.7 |
| 33 | Python | `embit` | pure Python | 1553.9 |
| 34 | Python | `secp256k1lab` | pure Python | 2300.2 |
| 35 | Python | `bip340-reference` | pure Python | 43286.0 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.7 |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **31.4** |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.3 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.4 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.8 |
| **6** | **C** | **`libschnorr256k1`** | **direct** | **33.0** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.1 |
| 8 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.2 |
| **9** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **33.6** |
| 10 | C | `libsecp256k1` | direct | 33.6 |
| 11 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.0 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **36.9** |
| 13 | Go | `splitsecure` | pure Go | 49.4 |
| 14 | Rust | `k256` | pure Rust | 53.1 |
| **15** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **54.6** |
| 16 | Rust | `secp256kfun` | pure Rust | 55.5 |
| **17** | **JVM** | **`kotlin`** | **pure Kotlin** | **64.9** |
| 18 | Go | `secp256k1-voi` | pure Go | 76.4 |
| **19** | **JVM** | **`kotlin`** | **pure Kotlin** | **79.8** |
| 20 | Go | `btcec` | pure Go | 150.7 |
| 21 | Go | `p256k1` | pure Go | 172.0 |
| 22 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 189.4 |
| 23 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.2 |
| 24 | JVM | `bouncycastle` | pure Java | 203.5 |
| 25 | JVM | `bouncycastle` | pure Java | 205.6 |
| 26 | JVM | `bitcoin-s` | pure Scala | 569.2 |
| 27 | JVM | `bitcoin-s` | pure Scala | 622.3 |
| 28 | Bun | `noble-curves` | pure JavaScript | 773.4 |
| 29 | Python | `btclib-ecc` | pure Python | 931.1 |
| 30 | Bun | `noble-secp256k1` | pure JavaScript | 1107.2 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 2391.0 |
| 32 | Node.js | `noble-secp256k1` | pure JavaScript | 2891.7 |
| 33 | Python | `secp256k1lab` | pure Python | 7163.0 |
| 34 | Python | `bip340-reference` | pure Python | 45191.6 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 29.5 |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **31.5** |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.6 |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.1** |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.3 |
| **6** | **C** | **`libschnorr256k1`** | **direct** | **32.3** |
| 7 | C | `libsecp256k1` | direct | 32.4 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.9 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.0 |
| 10 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.2 |
| 11 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.5 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **41.3** |
| 13 | Go | `splitsecure` | pure Go | 49.3 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.5** |
| 15 | Rust | `k256` | pure Rust | 52.3 |
| **16** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **53.7** |
| 17 | Rust | `secp256kfun` | pure Rust | 56.3 |
| 18 | Go | `secp256k1-voi` | pure Go | 76.3 |
| **19** | **JVM** | **`kotlin`** | **pure Kotlin** | **78.6** |
| 20 | Go | `btcec` | pure Go | 150.8 |
| 21 | Go | `p256k1` | pure Go | 172.2 |
| 22 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 189.5 |
| 23 | JVM | `bouncycastle` | pure Java | 204.2 |
| 24 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 205.0 |
| 25 | JVM | `bouncycastle` | pure Java | 216.1 |
| 26 | JVM | `bitcoin-s` | pure Scala | 559.2 |
| 27 | JVM | `bitcoin-s` | pure Scala | 655.1 |
| 28 | Bun | `noble-curves` | pure JavaScript | 792.5 |
| 29 | Python | `btclib-ecc` | pure Python | 938.0 |
| 30 | Bun | `noble-secp256k1` | pure JavaScript | 1093.6 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 2367.0 |
| 32 | Node.js | `noble-secp256k1` | pure JavaScript | 2899.5 |
| 33 | Python | `secp256k1lab` | pure Python | 7042.6 |
| 34 | Python | `bip340-reference` | pure Python | 42572.0 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **30.6** |
| 2 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 31.5 |
| 3 | C | `libsecp256k1` | direct | 31.6 |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **31.9** |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.9 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.0 |
| **7** | **C** | **`libschnorr256k1`** | **direct** | **32.2** |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.2 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.0 |
| 10 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.2 |
| 11 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.1 |
| 12 | Go | `splitsecure` | pure Go | 39.4 |
| **13** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **39.6** |
| 14 | Rust | `k256` | pure Rust | 52.7 |
| 15 | Rust | `secp256kfun` | pure Rust | 55.3 |
| **16** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **55.6** |
| **17** | **JVM** | **`kotlin`** | **pure Kotlin** | **60.7** |
| 18 | Go | `secp256k1-voi` | pure Go | 76.7 |
| **19** | **JVM** | **`kotlin`** | **pure Kotlin** | **78.9** |
| 20 | JVM | `bouncycastle` | pure Java | 116.4 |
| 21 | JVM | `bouncycastle` | pure Java | 118.5 |
| 22 | Go | `btcec` | pure Go | 149.6 |
| 23 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 192.0 |
| 24 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 196.0 |
| 25 | Go | `p256k1` | pure Go | 203.5 |
| 26 | JVM | `bitcoin-s` | pure Scala | 534.0 |
| 27 | JVM | `bitcoin-s` | pure Scala | 609.5 |
| 28 | Bun | `noble-curves` | pure JavaScript | 804.9 |
| 29 | Python | `btclib-ecc` | pure Python | 943.3 |
| 30 | Bun | `noble-secp256k1` | pure JavaScript | 1102.9 |
| 31 | Node.js | `noble-curves` | pure JavaScript | 2488.2 |
| 32 | Node.js | `noble-secp256k1` | pure JavaScript | 2972.4 |
| 33 | Python | `secp256k1lab` | pure Python | 7036.6 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.05 |
| **4** | **C** | **`libschnorr256k1`** | **direct** | **0.06** |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.09 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.11 |
| 7 | Go | `splitsecure` | pure Go | 0.12 |
| 8 | C | `libsecp256k1` | direct | 0.13 |
| **9** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.14** |
| **10** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.15** |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.15** |
| 12 | Rust | `k256` | pure Rust | 0.15 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.17** |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.20 |
| **15** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.29** |
| 16 | Go | `btcec` | pure Go | 0.32 |
| 17 | Go | `p256k1` | pure Go | 0.33 |
| **18** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.45** |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.47 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.50 |
| 21 | JVM | `bouncycastle` | pure Java | 0.51 |
| 22 | JVM | `bouncycastle` | pure Java | 0.61 |
| 23 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.51 |
| 24 | Bun | `noble-curves` | pure JavaScript | 2.31 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 4.86 |
| 26 | Python | `btclib-ecc` | pure Python | 5.24 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 5.96 |
| 28 | Python | `embit` | pure Python | 6.49 |
| 29 | Rust | `secp256kfun` | pure Rust | 6.59 |
| 30 | Bun | `noble-secp256k1` | pure JavaScript | 6.70 |
| 31 | Go | `secp256k1-voi` | pure Go | 6.72 |
| 32 | Python | `secp256k1lab` | pure Python | 7.45 |
| 33 | JVM | `bitcoin-s` | pure Scala | 17.6 |
| 34 | JVM | `bitcoin-s` | pure Scala | 18.0 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **0.02** |
| 2 | C | `libsecp256k1` | direct | 0.03 |
| 3 | Go | `p256k1` | pure Go | 0.03 |
| 4 | Go | `splitsecure` | pure Go | 0.04 |
| **5** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.04** |
| **6** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.04** |
| 7 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.05 |
| 8 | Rust | `secp256kfun` | pure Rust | 0.05 |
| 9 | Rust | `k256` | pure Rust | 0.05 |
| 10 | Go | `btcec` | pure Go | 0.06 |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.06 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.08** |
| **13** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.11** |
| **14** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.13** |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.13** |
| 16 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.13 |
| 17 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.13 |
| 18 | Go | `secp256k1-voi` | pure Go | 0.15 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 21 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.21 |
| 22 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.32 |
| 23 | Bun | `noble-curves` | pure JavaScript | 1.24 |
| 24 | Python | `embit` | pure Python | 1.44 |
| 25 | Python | `btclib-ecc` | pure Python | 1.47 |
| 26 | Python | `secp256k1lab` | pure Python | 1.64 |
| 27 | Node.js | `noble-curves` | pure JavaScript | 1.86 |
| 28 | JVM | `bitcoin-s` | pure Scala | 2.48 |
| 29 | JVM | `bitcoin-s` | pure Scala | 2.52 |
| 30 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 73.3 |

### Check a secret key

`seckey.verify`: 0 < key < n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.00 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.01** |
| 3 | Go | `p256k1` | pure Go | 0.01 |
| 4 | C | `libsecp256k1` | direct | 0.01 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.01 |
| 6 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.01 |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.01** |
| **8** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.01** |
| 9 | Go | `splitsecure` | pure Go | 0.01 |
| 10 | Rust | `secp256kfun` | pure Rust | 0.01 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.02** |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.02** |
| 13 | Go | `btcec` | pure Go | 0.02 |
| 14 | Rust | `k256` | pure Rust | 0.02 |
| 15 | JVM | `bouncycastle` | pure Java | 0.03 |
| 16 | JVM | `bouncycastle` | pure Java | 0.03 |
| 17 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.04 |
| **18** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.04** |
| 19 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.04 |
| **20** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.04** |
| 21 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.04 |
| 22 | JVM | `bitcoin-s` | pure Scala | 0.05 |
| 23 | JVM | `bitcoin-s` | pure Scala | 0.05 |
| 24 | Go | `secp256k1-voi` | pure Go | 0.06 |
| 25 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.08 |
| 26 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.12 |
| 27 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.31 |
| 28 | Bun | `noble-curves` | pure JavaScript | 0.40 |
| 29 | Python | `embit` | pure Python | 0.42 |
| 30 | Node.js | `noble-curves` | pure JavaScript | 0.49 |
| 31 | Python | `secp256k1lab` | pure Python | 0.54 |
| 32 | Python | `btclib-ecc` | pure Python | 0.71 |
| 33 | Node.js | `noble-secp256k1` | pure JavaScript | 1.52 |
| 34 | Bun | `noble-secp256k1` | pure JavaScript | 2.05 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415
- Kotlin/Native: Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-19-gc5b6cdf
- JVM: Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
- JVM: Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), kotlin schnorr256k1-kotlin v1.0.1-19-gc5b6cdf, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
- Node.js: Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Bun: Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Python: Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored)

Every operation, layer and device: [results/report.md](results/report.md).

<!-- results:end -->

## Run

```bash
git submodule update --init --recursive
scripts/run-host.sh --device "MacBook Pro M3 Pro" --machine dedicated   # c, jvm, native, rust, go, node, bun, python
scripts/run-android.sh --machine dedicated                              # the android layer, on the device adb sees
scripts/run-android-c.sh --machine dedicated                            # the c layer on that device (needs the NDK)
scripts/table.py --readme                                               # this README's Results and results/report.md
```

`--check` on any script runs a layer's checks without timing (what CI runs); `--quick` is a smoke
run, never published. Commit the result JSON files, the README and the report together.
