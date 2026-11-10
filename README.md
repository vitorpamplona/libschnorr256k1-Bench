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

Measured on Cloud VM (4 vCPU) (Intel(R) Xeon(R) Processor @ 2.10GHz), **a shared machine** (a cloud VM: other work may have run beside it, so the numbers are noisier than a dedicated run's, which will replace them), single thread, 2026-11-10. Each table ranks one operation by its median µs per call. A wrong result is listed last, unranked; KI-n marks a known issue ([results/SCHEMA.md](results/SCHEMA.md#known-issues)).

### Verify real Nostr events

`nostr.verify`: the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **29.5** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **30.3** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **30.4** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **32.3** |
| 5 | C | `libsecp256k1` | direct | 34.0 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 35.9 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.2 |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 36.6 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.0 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 37.8 |
| 11 | Go | `splitsecure` | pure Go | 46.3 |
| 12 | Rust | `k256` | pure Rust | 62.7 |
| **13** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **63.2** |
| 14 | Rust | `secp256kfun` | pure Rust | 79.3 |
| 15 | Go | `secp256k1-voi` | pure Go | 84.5 |
| 16 | Go | `p256k1` | pure Go | 116.0 |
| 17 | JVM | `bouncycastle` | pure Java | 145.8 |
| 18 | Go | `btcec` | pure Go | 193.1 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 208.8 |
| 20 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 211.3 |
| 21 | Bun | `noble-curves` | pure JavaScript | 463.4 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 890.1 |
| 23 | Python | `btclib-ecc` | pure Python | 1078.8 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1242.8 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 1413.5 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 1880.4 |
| 27 | Python | `embit` | pure Python | 2361.8 |
| 28 | Python | `secp256k1lab` | pure Python | 9344.5 |
| 29 | JVM | `nostr-java` | pure Java | 12868.3 |
| 30 | Python | `bip340-reference` | pure Python | 86445.2 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 34.8 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **35.2** |
| 3 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.4 |
| **4** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **36.2** |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.5 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **36.7** |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.7 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 37.0 |
| 9 | C | `libsecp256k1` | direct | 37.2 |
| 10 | Go | `splitsecure` | pure Go | 46.6 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **47.4** |
| 12 | Rust | `k256` | pure Rust | 63.6 |
| 13 | Rust | `secp256kfun` | pure Rust | 77.5 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **79.7** |
| 15 | Go | `secp256k1-voi` | pure Go | 84.3 |
| 16 | Go | `p256k1` | pure Go | 119.0 |
| 17 | JVM | `bouncycastle` | pure Java | 138.4 |
| 18 | Go | `btcec` | pure Go | 159.4 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 209.2 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 215.4 |
| 21 | Bun | `noble-curves` | pure JavaScript | 455.1 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 870.6 |
| 23 | Python | `btclib-ecc` | pure Python | 1129.3 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1230.8 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 1267.3 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2377.4 |
| 27 | Python | `embit` | pure Python | 2498.8 |
| 28 | Python | `secp256k1lab` | pure Python | 9197.8 |
| 29 | JVM | `nostr-java` | pure Java | 13058.4 |
| 30 | Python | `bip340-reference` | pure Python | 86072.6 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **25.9** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **26.0** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **26.6** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.2 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 32.2 |
| **6** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **33.4** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.6 |
| 8 | C | `libsecp256k1` | direct | 34.1 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.3 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.8 |
| 11 | Go | `splitsecure` | pure Go | 42.4 |
| **12** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **50.5** |
| 13 | Rust | `k256` | pure Rust | 60.8 |
| 14 | Go | `secp256k1-voi` | pure Go | 71.4 |
| 15 | Rust | `secp256kfun` | pure Rust | 85.3 |
| 16 | Go | `p256k1` | pure Go | 100.1 |
| 17 | JVM | `bouncycastle` | pure Java | 129.1 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 175.0 |
| 19 | Go | `btcec` | pure Go | 186.1 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 209.0 |
| 21 | Bun | `noble-curves` | pure JavaScript | 415.4 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 884.6 |
| 23 | Python | `btclib-ecc` | pure Python | 1027.7 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1226.5 |
| 25 | JVM | `bitcoin-s` | pure Scala | 1251.8 |
| 26 | Python | `embit` | pure Python | 2319.6 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 2371.4 |
| 28 | Python | `secp256k1lab` | pure Python | 8911.0 |
| 29 | JVM | `nostr-java` | pure Java | 12608.4 |
| 30 | Python | `bip340-reference` | pure Python | 84463.2 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **17.2** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **17.3** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **18.4** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **24.4** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **39.6** |
| 6 | Go | `p256k1` | pure Go | 156.1 |
| 7 | Python | `btclib-ecc` | pure Python | 845.5 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **9.88** |
| **2** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **10.8** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.9** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **13.8** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **24.0** |
| 6 | Go | `p256k1` | pure Go | 151.0 |
| 7 | Python | `btclib-ecc` | pure Python | 818.3 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 29.8 |
| 2 | C | `libsecp256k1` | direct | 34.4 |
| 3 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.8 |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.3 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.8 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.2 |
| 7 | Go | `splitsecure` | pure Go | 45.4 |
| 8 | Rust | `secp256kfun` | pure Rust | 61.9 |
| 9 | Rust | `k256` | pure Rust | 62.0 |
| 10 | Go | `secp256k1-voi` | pure Go | 84.9 |
| 11 | Go | `p256k1` | pure Go | 114.4 |
| 12 | JVM | `bouncycastle` | pure Java | 144.8 |
| 13 | Go | `btcec` | pure Go | 189.4 |
| 14 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.0 |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 214.7 |
| 16 | Bun | `noble-curves` | pure JavaScript | 468.9 |
| 17 | Bun | `noble-secp256k1` | pure JavaScript | 908.7 |
| 18 | Python | `btclib-ecc` | pure Python | 1088.1 |
| 19 | JVM | `bitcoin-s` | pure Scala | 1229.8 |
| 20 | Node.js | `noble-curves` | pure JavaScript | 1447.8 |
| 21 | Node.js | `noble-secp256k1` | pure JavaScript | 1987.1 |
| 22 | Python | `embit` | pure Python | 2377.0 |
| 23 | Python | `secp256k1lab` | pure Python | 9446.9 |
| 24 | JVM | `nostr-java` | pure Java | 13221.6 |
| 25 | Python | `bip340-reference` | pure Python | 87676.6 |
| – | **C** | **`libschnorr256k1`** | **direct** | **28.8** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **28.6** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **37.9** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **29.0** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **60.2** **wrong** KI-4 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 32.5 |
| 2 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 33.5 |
| 3 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.8 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.9 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 34.3 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.3 |
| 7 | Go | `splitsecure` | pure Go | 42.5 |
| 8 | Rust | `k256` | pure Rust | 57.7 |
| 9 | Rust | `secp256kfun` | pure Rust | 78.4 |
| 10 | Go | `secp256k1-voi` | pure Go | 80.8 |
| 11 | Go | `p256k1` | pure Go | 101.9 |
| 12 | JVM | `bouncycastle` | pure Java | 130.0 |
| 13 | Go | `btcec` | pure Go | 184.4 |
| 14 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.2 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.4 |
| 16 | Bun | `noble-curves` | pure JavaScript | 406.3 |
| 17 | Bun | `noble-secp256k1` | pure JavaScript | 831.8 |
| 18 | Python | `btclib-ecc` | pure Python | 1027.5 |
| 19 | JVM | `bitcoin-s` | pure Scala | 1237.7 |
| 20 | Node.js | `noble-curves` | pure JavaScript | 1462.0 |
| 21 | Python | `embit` | pure Python | 2386.6 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 2423.7 |
| 23 | Python | `secp256k1lab` | pure Python | 9783.9 |
| 24 | JVM | `nostr-java` | pure Java | 12716.8 |
| 25 | Python | `bip340-reference` | pure Python | 84012.3 |
| – | **C** | **`libschnorr256k1`** | **direct** | **24.5** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **23.1** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **32.4** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **25.8** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **48.7** **wrong** KI-4 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **20.5** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **21.1** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **21.3** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **25.8** |
| 5 | C | `libsecp256k1` | direct | 31.7 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.3 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.7 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.9 |
| 9 | Rust | `k256` | pure Rust | 46.4 |
| 10 | Go | `splitsecure` | pure Go | 46.6 |
| **11** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **47.5** |
| 12 | Go | `btcec` | pure Go | 77.0 |
| 13 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 81.7 |
| 14 | Go | `secp256k1-voi` | pure Go | 89.2 |
| 15 | Rust | `secp256kfun` | pure Rust | 98.1 |
| 16 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 104.2 |
| 17 | Go | `p256k1` | pure Go | 129.7 |
| 18 | JVM | `bouncycastle` | pure Java | 147.7 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 200.3 |
| 20 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 201.0 |
| 21 | Bun | `noble-curves` | pure JavaScript | 770.0 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 1119.4 |
| 23 | Python | `btclib-ecc` | pure Python | 1554.5 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2519.3 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2872.2 |
| 26 | Python | `embit` | pure Python | 4784.7 |
| 27 | JVM | `bitcoin-s` | pure Scala | 7008.4 |
| 28 | Python | `secp256k1lab` | pure Python | 14676.7 |
| 29 | JVM | `nostr-java` | pure Java | 26901.4 |
| 30 | Python | `bip340-reference` | pure Python | 174106.0 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **9.57** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.5** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **10.8** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **14.1** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **24.2** |
| 6 | C | `libsecp256k1` | direct | 32.1 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.2 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.3 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.0 |
| 10 | Go | `splitsecure` | pure Go | 46.7 |
| 11 | Rust | `k256` | pure Rust | 48.3 |
| 12 | Go | `btcec` | pure Go | 76.6 |
| 13 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 82.7 |
| 14 | JVM | `bouncycastle` | pure Java | 91.4 |
| 15 | Go | `secp256k1-voi` | pure Go | 92.0 |
| 16 | Rust | `secp256kfun` | pure Rust | 98.5 |
| 17 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 110.8 |
| 18 | Go | `p256k1` | pure Go | 126.9 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 199.8 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 203.6 |
| 21 | Bun | `noble-curves` | pure JavaScript | 808.3 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 1127.5 |
| 23 | Python | `btclib-ecc` | pure Python | 1550.8 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2375.9 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2834.4 |
| 26 | Python | `embit` | pure Python | 4754.9 |
| 27 | JVM | `bitcoin-s` | pure Scala | 7027.5 |
| 28 | Python | `secp256k1lab` | pure Python | 13632.8 |
| 29 | JVM | `nostr-java` | pure Java | 26092.3 |
| 30 | Python | `bip340-reference` | pure Python | 175893.8 |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **9.62** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.1** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.2** |
| **4** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **10.3** |
| 5 | C | `libsecp256k1` | direct | 15.3 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 15.8 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 16.1 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 16.4 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 19.5 |
| 10 | Go | `splitsecure` | pure Go | 23.2 |
| **11** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **23.3** |
| 12 | Rust | `k256` | pure Rust | 23.3 |
| 13 | Go | `secp256k1-voi` | pure Go | 29.3 |
| 14 | Go | `btcec` | pure Go | 35.2 |
| 15 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 40.3 |
| 16 | Go | `p256k1` | pure Go | 49.1 |
| 17 | Rust | `secp256kfun` | pure Rust | 49.1 |
| 18 | JVM | `bouncycastle` | pure Java | 68.2 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 98.0 |
| 20 | Bun | `noble-secp256k1` | pure JavaScript | 99.0 |
| 21 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 99.6 |
| 22 | Bun | `noble-curves` | pure JavaScript | 130.9 |
| 23 | Python | `btclib-ecc` | pure Python | 210.7 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 244.2 |
| 25 | JVM | `bitcoin-s` | pure Scala | 475.0 |
| 26 | Node.js | `noble-curves` | pure JavaScript | 506.0 |
| 27 | Python | `embit` | pure Python | 1515.8 |
| 28 | Python | `secp256k1lab` | pure Python | 2283.7 |
| 29 | Python | `bip340-reference` | pure Python | 44195.7 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 29.6 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **30.1** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **30.5** |
| 4 | C | `libsecp256k1` | direct | 31.7 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.7 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.2** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.3 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.8 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.5 |
| **10** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **39.0** |
| 11 | Rust | `secp256kfun` | pure Rust | 46.0 |
| 12 | Go | `splitsecure` | pure Go | 50.4 |
| 13 | Rust | `k256` | pure Rust | 51.9 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **63.0** |
| 15 | Go | `secp256k1-voi` | pure Go | 78.5 |
| 16 | Go | `btcec` | pure Go | 149.3 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 153.0 |
| 18 | Go | `p256k1` | pure Go | 169.3 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 190.5 |
| 20 | JVM | `bouncycastle` | pure Java | 208.0 |
| 21 | JVM | `bitcoin-s` | pure Scala | 588.8 |
| 22 | Bun | `noble-curves` | pure JavaScript | 757.2 |
| 23 | Python | `btclib-ecc` | pure Python | 955.4 |
| 24 | Bun | `noble-secp256k1` | pure JavaScript | 960.9 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2463.2 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2919.4 |
| 27 | Python | `secp256k1lab` | pure Python | 7087.6 |
| 28 | Python | `bip340-reference` | pure Python | 42412.9 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **30.2** |
| 2 | C | `libsecp256k1` | direct | 31.1 |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.2 |
| **4** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **32.0** |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 32.0 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.2** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.6 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.0 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.1 |
| **10** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **38.3** |
| 11 | Go | `splitsecure` | pure Go | 48.4 |
| 12 | Rust | `k256` | pure Rust | 52.6 |
| 13 | Rust | `secp256kfun` | pure Rust | 55.1 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **65.1** |
| 15 | Go | `secp256k1-voi` | pure Go | 74.9 |
| 16 | Go | `btcec` | pure Go | 150.1 |
| 17 | Go | `p256k1` | pure Go | 174.3 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 194.1 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 194.2 |
| 20 | JVM | `bouncycastle` | pure Java | 216.4 |
| 21 | JVM | `bitcoin-s` | pure Scala | 584.1 |
| 22 | Bun | `noble-curves` | pure JavaScript | 679.1 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 940.7 |
| 24 | Python | `btclib-ecc` | pure Python | 953.1 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2526.5 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2818.0 |
| 27 | Python | `secp256k1lab` | pure Python | 6953.8 |
| 28 | Python | `bip340-reference` | pure Python | 42451.9 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.8 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **31.4** |
| 3 | C | `libsecp256k1` | direct | 31.6 |
| **4** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **31.7** |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.0 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 32.1 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.1** |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.9 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.7 |
| **10** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **39.8** |
| 11 | Go | `splitsecure` | pure Go | 40.3 |
| 12 | Rust | `k256` | pure Rust | 53.1 |
| 13 | Rust | `secp256kfun` | pure Rust | 55.7 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **65.8** |
| 15 | Go | `secp256k1-voi` | pure Go | 75.8 |
| 16 | JVM | `bouncycastle` | pure Java | 118.3 |
| 17 | Go | `btcec` | pure Go | 153.3 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 193.7 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.9 |
| 20 | Go | `p256k1` | pure Go | 208.9 |
| 21 | JVM | `bitcoin-s` | pure Scala | 576.5 |
| 22 | Bun | `noble-curves` | pure JavaScript | 707.7 |
| 23 | Python | `btclib-ecc` | pure Python | 942.8 |
| 24 | Bun | `noble-secp256k1` | pure JavaScript | 991.1 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2436.9 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2879.9 |
| 27 | Python | `secp256k1lab` | pure Python | 6326.0 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| 2 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.05 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **0.06** |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.11 |
| 5 | Go | `splitsecure` | pure Go | 0.12 |
| 6 | C | `libsecp256k1` | direct | 0.13 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.15** |
| **8** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.15** |
| **9** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **0.17** |
| 10 | Rust | `k256` | pure Rust | 0.18 |
| 11 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.19 |
| **12** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.28** |
| 13 | Go | `btcec` | pure Go | 0.30 |
| 14 | Go | `p256k1` | pure Go | 0.34 |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.48 |
| 16 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.49 |
| 17 | JVM | `bouncycastle` | pure Java | 0.59 |
| 18 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.46 |
| 19 | Bun | `noble-curves` | pure JavaScript | 2.06 |
| 20 | Node.js | `noble-secp256k1` | pure JavaScript | 4.99 |
| 21 | Python | `btclib-ecc` | pure Python | 5.42 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 6.22 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 6.30 |
| 24 | Python | `embit` | pure Python | 6.49 |
| 25 | Rust | `secp256kfun` | pure Rust | 6.59 |
| 26 | Go | `secp256k1-voi` | pure Go | 6.77 |
| 27 | Python | `secp256k1lab` | pure Python | 7.60 |
| 28 | JVM | `bitcoin-s` | pure Scala | 17.6 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 0.02 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.03** |
| 3 | Go | `p256k1` | pure Go | 0.03 |
| 4 | Go | `splitsecure` | pure Go | 0.03 |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.04** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.05 |
| 7 | Rust | `secp256kfun` | pure Rust | 0.05 |
| 8 | Go | `btcec` | pure Go | 0.06 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.06 |
| 10 | Rust | `k256` | pure Rust | 0.06 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.09** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.12** |
| 13 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.12 |
| **14** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **0.15** |
| 15 | Go | `secp256k1-voi` | pure Go | 0.15 |
| 16 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.15 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 18 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.21 |
| 19 | Bun | `noble-curves` | pure JavaScript | 1.03 |
| 20 | Python | `btclib-ecc` | pure Python | 1.43 |
| 21 | Python | `embit` | pure Python | 1.44 |
| 22 | Python | `secp256k1lab` | pure Python | 1.67 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1.80 |
| 24 | JVM | `bitcoin-s` | pure Scala | 2.54 |
| 25 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 72.1 |

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
| **7** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.01** |
| 8 | Go | `splitsecure` | pure Go | 0.01 |
| 9 | Rust | `secp256kfun` | pure Rust | 0.01 |
| 10 | Go | `btcec` | pure Go | 0.02 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.02** |
| 12 | Rust | `k256` | pure Rust | 0.02 |
| 13 | JVM | `bouncycastle` | pure Java | 0.03 |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.04 |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.04** |
| **16** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0** | **0.04** |
| 17 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.05 |
| 18 | JVM | `bitcoin-s` | pure Scala | 0.05 |
| 19 | Go | `secp256k1-voi` | pure Go | 0.06 |
| 20 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.09 |
| 21 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.30 |
| 22 | Bun | `noble-curves` | pure JavaScript | 0.36 |
| 23 | Python | `embit` | pure Python | 0.46 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 0.49 |
| 25 | Python | `secp256k1lab` | pure Python | 0.56 |
| 26 | Python | `btclib-ecc` | pure Python | 0.70 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 1.47 |
| 28 | Bun | `noble-secp256k1` | pure JavaScript | 1.97 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13
- JVM: Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
- Node.js: Linux 6.18.44-fc-v338, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Bun: Linux 6.18.44-fc-v338, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0
- Python: Linux 6.18.44-fc-v338, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored)

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
