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
| **1** | **C** | **`libschnorr256k1`** | **direct** | **29.8** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **30.1** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **30.5** |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.9 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 37.0 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.2 |
| 7 | C | `libsecp256k1` | direct | 37.5 |
| 8 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.8 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.5 |
| **10** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **41.2** |
| 11 | Go | `splitsecure` | pure Go | 45.0 |
| **12** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **61.2** |
| 13 | Rust | `k256` | pure Rust | 61.9 |
| 14 | Rust | `secp256kfun` | pure Rust | 78.1 |
| 15 | Go | `secp256k1-voi` | pure Go | 85.4 |
| 16 | Go | `p256k1` | pure Go | 113.7 |
| 17 | JVM | `bouncycastle` | pure Java | 142.8 |
| 18 | Go | `btcec` | pure Go | 167.9 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.3 |
| 20 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.7 |
| 21 | Bun | `noble-curves` | pure JavaScript | 435.2 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 774.9 |
| 23 | Python | `btclib-ecc` | pure Python | 971.7 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1213.2 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 1364.6 |
| 26 | Python | `embit` | pure Python | 2214.3 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 2471.7 |
| 28 | Python | `secp256k1lab` | pure Python | 8977.0 |
| 29 | JVM | `nostr-java` | pure Java | 12799.9 |
| 30 | Python | `bip340-reference` | pure Python | 79605.9 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 32.9 |
| 2 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.6 |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **35.2** |
| **4** | **C** | **`libschnorr256k1`** | **direct** | **35.2** |
| 5 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.3 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **35.8** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 36.2 |
| 8 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.2 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.8 |
| 10 | Go | `splitsecure` | pure Go | 42.5 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **47.2** |
| 12 | Rust | `k256` | pure Rust | 61.7 |
| **13** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **70.5** |
| 14 | Rust | `secp256kfun` | pure Rust | 79.9 |
| 15 | Go | `secp256k1-voi` | pure Go | 86.6 |
| 16 | Go | `p256k1` | pure Go | 109.4 |
| 17 | JVM | `bouncycastle` | pure Java | 152.7 |
| 18 | Go | `btcec` | pure Go | 186.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 215.1 |
| 20 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 247.3 |
| 21 | Bun | `noble-curves` | pure JavaScript | 443.7 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 796.5 |
| 23 | Python | `btclib-ecc` | pure Python | 964.7 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1186.1 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 1298.4 |
| 26 | Python | `embit` | pure Python | 2220.1 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 2360.9 |
| 28 | Python | `secp256k1lab` | pure Python | 9168.4 |
| 29 | JVM | `nostr-java` | pure Java | 12839.7 |
| 30 | Python | `bip340-reference` | pure Python | 89214.9 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **26.3** |
| **2** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **26.6** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **26.6** |
| 4 | C | `libsecp256k1` | direct | 30.5 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.3 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.8 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.9 |
| **8** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **34.0** |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.3 |
| 10 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 34.4 |
| 11 | Go | `splitsecure` | pure Go | 42.7 |
| **12** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **46.2** |
| 13 | Rust | `k256` | pure Rust | 55.8 |
| 14 | Rust | `secp256kfun` | pure Rust | 78.5 |
| 15 | Go | `secp256k1-voi` | pure Go | 82.5 |
| 16 | Go | `p256k1` | pure Go | 101.5 |
| 17 | JVM | `bouncycastle` | pure Java | 134.8 |
| 18 | Go | `btcec` | pure Go | 183.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 207.7 |
| 20 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 237.1 |
| 21 | Bun | `noble-curves` | pure JavaScript | 430.0 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 768.0 |
| 23 | Python | `btclib-ecc` | pure Python | 998.3 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1207.7 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 1349.5 |
| 26 | Python | `embit` | pure Python | 2112.3 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 2352.7 |
| 28 | Python | `secp256k1lab` | pure Python | 8689.7 |
| 29 | JVM | `nostr-java` | pure Java | 12587.9 |
| 30 | Python | `bip340-reference` | pure Python | 82938.5 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **16.7** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **17.6** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **18.0** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **24.0** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **38.9** |
| 6 | Go | `p256k1` | pure Go | 150.5 |
| 7 | Python | `btclib-ecc` | pure Python | 907.7 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **10.4** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.8** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **11.1** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **13.1** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **23.1** |
| 6 | Go | `p256k1` | pure Go | 156.6 |
| 7 | Python | `btclib-ecc` | pure Python | 719.9 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.0 |
| 2 | C | `libsecp256k1` | direct | 36.1 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 36.1 |
| 4 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 36.9 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 37.0 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 40.7 |
| 7 | Go | `splitsecure` | pure Go | 44.8 |
| 8 | Rust | `k256` | pure Rust | 62.3 |
| 9 | Go | `secp256k1-voi` | pure Go | 75.9 |
| 10 | Rust | `secp256kfun` | pure Rust | 78.8 |
| 11 | Go | `p256k1` | pure Go | 113.9 |
| 12 | JVM | `bouncycastle` | pure Java | 147.3 |
| 13 | Go | `btcec` | pure Go | 190.1 |
| 14 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.1 |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.2 |
| 16 | Bun | `noble-curves` | pure JavaScript | 445.2 |
| 17 | Bun | `noble-secp256k1` | pure JavaScript | 814.7 |
| 18 | Python | `btclib-ecc` | pure Python | 1037.2 |
| 19 | JVM | `bitcoin-s` | pure Scala | 1223.7 |
| 20 | Node.js | `noble-curves` | pure JavaScript | 1397.6 |
| 21 | Python | `embit` | pure Python | 2307.1 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 2387.9 |
| 23 | Python | `secp256k1lab` | pure Python | 8606.1 |
| 24 | JVM | `nostr-java` | pure Java | 13338.5 |
| 25 | Python | `bip340-reference` | pure Python | 88065.0 |
| – | **C** | **`libschnorr256k1`** | **direct** | **28.4** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **28.6** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **38.1** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **29.7** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **62.1** **wrong** KI-4 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 33.1 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.3 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.3 |
| 4 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.0 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 34.1 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.0 |
| 7 | Go | `splitsecure` | pure Go | 51.8 |
| 8 | Rust | `k256` | pure Rust | 58.5 |
| 9 | Go | `secp256k1-voi` | pure Go | 64.4 |
| 10 | Rust | `secp256kfun` | pure Rust | 78.7 |
| 11 | Go | `p256k1` | pure Go | 105.4 |
| 12 | JVM | `bouncycastle` | pure Java | 129.0 |
| 13 | Go | `btcec` | pure Go | 185.6 |
| 14 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 214.0 |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 214.1 |
| 16 | Bun | `noble-curves` | pure JavaScript | 423.4 |
| 17 | Bun | `noble-secp256k1` | pure JavaScript | 628.7 |
| 18 | Python | `btclib-ecc` | pure Python | 1026.5 |
| 19 | JVM | `bitcoin-s` | pure Scala | 1213.5 |
| 20 | Node.js | `noble-curves` | pure JavaScript | 1349.6 |
| 21 | Python | `embit` | pure Python | 2025.7 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 2376.2 |
| 23 | Python | `secp256k1lab` | pure Python | 8684.2 |
| 24 | JVM | `nostr-java` | pure Java | 12697.9 |
| 25 | Python | `bip340-reference` | pure Python | 82576.2 |
| – | **C** | **`libschnorr256k1`** | **direct** | **24.7** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **25.0** **wrong** KI-4 |
| – | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **33.9** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **24.5** **wrong** KI-4 |
| – | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **38.8** **wrong** KI-4 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **16.4** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **20.6** |
| **3** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **20.9** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **25.8** |
| 5 | C | `libsecp256k1` | direct | 29.6 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.2 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.8 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.9 |
| **9** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **46.9** |
| 10 | Go | `splitsecure` | pure Go | 47.3 |
| 11 | Rust | `k256` | pure Rust | 48.6 |
| 12 | Go | `btcec` | pure Go | 72.9 |
| 13 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 82.3 |
| 14 | Go | `secp256k1-voi` | pure Go | 86.3 |
| 15 | Rust | `secp256kfun` | pure Rust | 98.8 |
| 16 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 101.8 |
| 17 | Go | `p256k1` | pure Go | 122.8 |
| 18 | JVM | `bouncycastle` | pure Java | 151.5 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 191.7 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 204.6 |
| 21 | Bun | `noble-curves` | pure JavaScript | 833.9 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 997.0 |
| 23 | Python | `btclib-ecc` | pure Python | 1549.5 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2134.6 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2904.8 |
| 26 | Python | `embit` | pure Python | 4609.9 |
| 27 | JVM | `bitcoin-s` | pure Scala | 7017.5 |
| 28 | Python | `secp256k1lab` | pure Python | 13741.2 |
| 29 | JVM | `nostr-java` | pure Java | 25980.6 |
| 30 | Python | `bip340-reference` | pure Python | 169947.8 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.7** |
| **2** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **10.7** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **10.9** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **12.9** |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **24.3** |
| 6 | C | `libsecp256k1` | direct | 28.0 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.6 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.5 |
| 9 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.9 |
| 10 | Go | `splitsecure` | pure Go | 46.5 |
| 11 | Rust | `k256` | pure Rust | 48.2 |
| 12 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 64.7 |
| 13 | Go | `btcec` | pure Go | 76.7 |
| 14 | Go | `secp256k1-voi` | pure Go | 82.1 |
| 15 | JVM | `bouncycastle` | pure Java | 91.5 |
| 16 | Rust | `secp256kfun` | pure Rust | 98.1 |
| 17 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 102.9 |
| 18 | Go | `p256k1` | pure Go | 120.0 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 202.4 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 210.9 |
| 21 | Bun | `noble-curves` | pure JavaScript | 784.7 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 1041.8 |
| 23 | Python | `btclib-ecc` | pure Python | 1473.6 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2167.5 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2938.0 |
| 26 | Python | `embit` | pure Python | 4709.9 |
| 27 | JVM | `bitcoin-s` | pure Scala | 6973.5 |
| 28 | Python | `secp256k1lab` | pure Python | 13467.8 |
| 29 | JVM | `nostr-java` | pure Java | 26500.1 |
| 30 | Python | `bip340-reference` | pure Python | 168252.7 |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **10.1** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.1** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **10.7** |
| **4** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **10.8** |
| 5 | C | `libsecp256k1` | direct | 15.0 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 16.0 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 16.2 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 16.3 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 18.2 |
| **10** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **23.0** |
| 11 | Go | `splitsecure` | pure Go | 23.2 |
| 12 | Rust | `k256` | pure Rust | 23.2 |
| 13 | Go | `secp256k1-voi` | pure Go | 28.5 |
| 14 | Go | `btcec` | pure Go | 37.0 |
| 15 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 40.2 |
| 16 | Rust | `secp256kfun` | pure Rust | 47.8 |
| 17 | Go | `p256k1` | pure Go | 48.2 |
| 18 | JVM | `bouncycastle` | pure Java | 74.1 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 94.0 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 102.0 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 102.7 |
| 22 | Bun | `noble-curves` | pure JavaScript | 146.4 |
| 23 | Python | `btclib-ecc` | pure Python | 206.6 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 239.4 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 361.0 |
| 26 | JVM | `bitcoin-s` | pure Scala | 439.0 |
| 27 | Python | `embit` | pure Python | 1543.6 |
| 28 | Python | `secp256k1lab` | pure Python | 2266.0 |
| 29 | Python | `bip340-reference` | pure Python | 40365.9 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **26.6** |
| **2** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **29.1** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **29.2** |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 30.6 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.1 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.4 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.6 |
| 8 | C | `libsecp256k1` | direct | 33.2 |
| **9** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **33.6** |
| 10 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.3 |
| 11 | Go | `splitsecure` | pure Go | 48.2 |
| 12 | Rust | `k256` | pure Rust | 52.2 |
| 13 | Rust | `secp256kfun` | pure Rust | 53.6 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **61.2** |
| 15 | Go | `secp256k1-voi` | pure Go | 75.0 |
| 16 | Go | `btcec` | pure Go | 148.6 |
| 17 | Go | `p256k1` | pure Go | 167.0 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 188.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 196.6 |
| 20 | JVM | `bouncycastle` | pure Java | 205.3 |
| 21 | JVM | `bitcoin-s` | pure Scala | 592.3 |
| 22 | Bun | `noble-curves` | pure JavaScript | 725.7 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 900.6 |
| 24 | Python | `btclib-ecc` | pure Python | 925.0 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2347.9 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2899.8 |
| 27 | Python | `secp256k1lab` | pure Python | 6573.0 |
| 28 | Python | `bip340-reference` | pure Python | 42477.1 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **28.9** |
| **2** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **29.5** |
| **3** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **29.5** |
| **4** | **C** | **`libschnorr256k1`** | **direct** | **29.9** |
| 5 | C | `libsecp256k1` | direct | 30.1 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 31.5 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.6 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.1 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 33.3 |
| 10 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.6 |
| 11 | Go | `splitsecure` | pure Go | 42.5 |
| 12 | Rust | `k256` | pure Rust | 53.0 |
| 13 | Rust | `secp256kfun` | pure Rust | 55.6 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **60.4** |
| 15 | Go | `secp256k1-voi` | pure Go | 75.7 |
| 16 | Go | `btcec` | pure Go | 149.3 |
| 17 | Go | `p256k1` | pure Go | 171.3 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 183.7 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.6 |
| 20 | JVM | `bouncycastle` | pure Java | 210.5 |
| 21 | JVM | `bitcoin-s` | pure Scala | 583.8 |
| 22 | Bun | `noble-curves` | pure JavaScript | 713.3 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 869.2 |
| 24 | Python | `btclib-ecc` | pure Python | 917.6 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2323.7 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2794.7 |
| 27 | Python | `secp256k1lab` | pure Python | 6822.9 |
| 28 | Python | `bip340-reference` | pure Python | 44166.9 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 29.5 |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **30.8** |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **31.1** |
| **4** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **31.4** |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 32.0 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.0 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.5 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.6 |
| 9 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.6 |
| 10 | Go | `splitsecure` | pure Go | 39.2 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **39.9** |
| 12 | Rust | `k256` | pure Rust | 53.7 |
| 13 | Rust | `secp256kfun` | pure Rust | 55.1 |
| **14** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **65.4** |
| 15 | Go | `secp256k1-voi` | pure Go | 75.0 |
| 16 | JVM | `bouncycastle` | pure Java | 117.8 |
| 17 | Go | `btcec` | pure Go | 146.3 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 190.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.3 |
| 20 | Go | `p256k1` | pure Go | 202.6 |
| 21 | JVM | `bitcoin-s` | pure Scala | 595.1 |
| 22 | Bun | `noble-curves` | pure JavaScript | 731.7 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 913.9 |
| 24 | Python | `btclib-ecc` | pure Python | 920.3 |
| 25 | Node.js | `noble-curves` | pure JavaScript | 2339.0 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2733.7 |
| 27 | Python | `secp256k1lab` | pure Python | 6919.5 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.05** |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.06 |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.11 |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.11** |
| 6 | Go | `splitsecure` | pure Go | 0.11 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.12** |
| 8 | C | `libsecp256k1` | direct | 0.12 |
| **9** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.15** |
| **10** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.15** |
| 11 | Rust | `k256` | pure Rust | 0.17 |
| 12 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.19 |
| 13 | Go | `btcec` | pure Go | 0.30 |
| 14 | Go | `p256k1` | pure Go | 0.33 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.43 |
| 16 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.46 |
| 17 | JVM | `bouncycastle` | pure Java | 0.61 |
| 18 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.39 |
| 19 | Bun | `noble-curves` | pure JavaScript | 1.93 |
| 20 | Python | `btclib-ecc` | pure Python | 4.73 |
| 21 | Node.js | `noble-curves` | pure JavaScript | 4.73 |
| 22 | Bun | `noble-secp256k1` | pure JavaScript | 5.95 |
| 23 | Node.js | `noble-secp256k1` | pure JavaScript | 6.23 |
| 24 | Python | `embit` | pure Python | 6.41 |
| 25 | Rust | `secp256kfun` | pure Rust | 6.56 |
| 26 | Go | `secp256k1-voi` | pure Go | 7.08 |
| 27 | Python | `secp256k1lab` | pure Python | 7.29 |
| 28 | JVM | `bitcoin-s` | pure Scala | 18.0 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 0.02 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.03** |
| 3 | Go | `splitsecure` | pure Go | 0.03 |
| 4 | Go | `p256k1` | pure Go | 0.04 |
| **5** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.04** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.05 |
| 7 | Rust | `secp256kfun` | pure Rust | 0.05 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.06 |
| 9 | Go | `btcec` | pure Go | 0.06 |
| 10 | Rust | `k256` | pure Rust | 0.06 |
| **11** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.09** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.12** |
| 13 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.13 |
| **14** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.14** |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.15 |
| 16 | Go | `secp256k1-voi` | pure Go | 0.15 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 18 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.24 |
| 19 | Bun | `noble-curves` | pure JavaScript | 0.78 |
| 20 | Python | `embit` | pure Python | 1.43 |
| 21 | Python | `secp256k1lab` | pure Python | 1.47 |
| 22 | Python | `btclib-ecc` | pure Python | 1.49 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1.61 |
| 24 | JVM | `bitcoin-s` | pure Scala | 2.36 |
| 25 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 71.5 |

### Check a secret key

`seckey.verify`: 0 < key < n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.00 |
| 2 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.01 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **0.01** |
| 4 | Go | `p256k1` | pure Go | 0.01 |
| 5 | C | `libsecp256k1` | direct | 0.01 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.01 |
| **7** | **JVM** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.01** |
| 8 | Go | `splitsecure` | pure Go | 0.01 |
| 9 | Rust | `secp256kfun` | pure Rust | 0.01 |
| **10** | **Kotlin/Native** | **`libschnorr256k1-kotlin`** | **pure Kotlin** | **0.02** |
| 11 | Go | `btcec` | pure Go | 0.02 |
| 12 | Rust | `k256` | pure Rust | 0.02 |
| 13 | JVM | `bouncycastle` | pure Java | 0.03 |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.04 |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.04** |
| 16 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.04 |
| **17** | **Kotlin/Native** | **`libschnorr256k1`** | **cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32** | **0.04** |
| 18 | JVM | `bitcoin-s` | pure Scala | 0.05 |
| 19 | Go | `secp256k1-voi` | pure Go | 0.06 |
| 20 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.10 |
| 21 | Bun | `noble-curves` | pure JavaScript | 0.24 |
| 22 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.31 |
| 23 | Python | `embit` | pure Python | 0.43 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 0.44 |
| 25 | Python | `secp256k1lab` | pure Python | 0.55 |
| 26 | Python | `btclib-ecc` | pure Python | 0.65 |
| 27 | Node.js | `noble-secp256k1` | pure JavaScript | 1.60 |
| 28 | Bun | `noble-secp256k1` | pure JavaScript | 1.93 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 0942a32
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32 (JitPack 4478ec1), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-29-ge49e229
- JVM: Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32 (JitPack 4478ec1), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-29-ge49e229, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
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
