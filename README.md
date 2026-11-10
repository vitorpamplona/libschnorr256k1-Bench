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
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **27.3** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **30.9** |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.9 |
| 4 | C | `libsecp256k1` | direct | 34.3 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 35.9 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 36.7 |
| 7 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.7 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 37.5 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **37.5** |
| 10 | Go | `splitsecure` | pure Go | 45.3 |
| 11 | Rust | `k256` | pure Rust | 62.1 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **66.0** |
| 13 | Rust | `secp256kfun` | pure Rust | 79.6 |
| 14 | Go | `secp256k1-voi` | pure Go | 84.4 |
| 15 | Go | `p256k1` | pure Go | 112.5 |
| 16 | JVM | `bouncycastle` | pure Java | 142.9 |
| 17 | Go | `btcec` | pure Go | 179.0 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 210.1 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 226.8 |
| 20 | Bun | `noble-curves` | pure JavaScript | 458.2 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 866.5 |
| 22 | Python | `btclib-ecc` | pure Python | 931.8 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1205.5 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1393.5 |
| 25 | Python | `embit` | pure Python | 2243.8 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2383.7 |
| 27 | Python | `secp256k1lab` | pure Python | 9325.1 |
| 28 | JVM | `nostr-java` | pure Java | 13216.2 |
| 29 | Python | `bip340-reference` | pure Python | 87037.0 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 34.4 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.1 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **36.4** |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **36.6** |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 36.7 |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.8 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.0 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.2 |
| 9 | Go | `splitsecure` | pure Go | 43.9 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **47.1** |
| 11 | Rust | `k256` | pure Rust | 60.5 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **75.8** |
| 13 | Go | `secp256k1-voi` | pure Go | 79.6 |
| 14 | Rust | `secp256kfun` | pure Rust | 80.0 |
| 15 | Go | `p256k1` | pure Go | 113.6 |
| 16 | JVM | `bouncycastle` | pure Java | 143.2 |
| 17 | Go | `btcec` | pure Go | 188.2 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 207.6 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 221.7 |
| 20 | Bun | `noble-curves` | pure JavaScript | 421.8 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 769.1 |
| 22 | Python | `btclib-ecc` | pure Python | 1033.1 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1236.3 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1263.6 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2009.2 |
| 26 | Python | `embit` | pure Python | 2337.6 |
| 27 | Python | `secp256k1lab` | pure Python | 8581.6 |
| 28 | JVM | `nostr-java` | pure Java | 12823.9 |
| 29 | Python | `bip340-reference` | pure Python | 75691.5 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **22.1** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **26.2** |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.5 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.5 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.6 |
| 6 | C | `libsecp256k1` | direct | 34.0 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.1 |
| **8** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **34.8** |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.8 |
| 10 | Go | `splitsecure` | pure Go | 40.0 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.6** |
| 12 | Rust | `k256` | pure Rust | 55.5 |
| 13 | Go | `secp256k1-voi` | pure Go | 76.2 |
| 14 | Rust | `secp256kfun` | pure Rust | 79.3 |
| 15 | Go | `p256k1` | pure Go | 101.5 |
| 16 | JVM | `bouncycastle` | pure Java | 128.8 |
| 17 | Go | `btcec` | pure Go | 168.9 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 207.4 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.2 |
| 20 | Bun | `noble-curves` | pure JavaScript | 428.9 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 818.4 |
| 22 | Python | `btclib-ecc` | pure Python | 998.4 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1200.3 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1365.7 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2130.6 |
| 26 | Python | `embit` | pure Python | 2199.0 |
| 27 | Python | `secp256k1lab` | pure Python | 7679.7 |
| 28 | JVM | `nostr-java` | pure Java | 11712.0 |
| 29 | Python | `bip340-reference` | pure Python | 83202.5 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **17.6** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **18.0** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **24.6** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **41.1** |
| 5 | Go | `p256k1` | pure Go | 155.5 |
| 6 | Python | `btclib-ecc` | pure Python | 956.9 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.8** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **11.1** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **14.0** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **26.4** |
| 5 | Go | `p256k1` | pure Go | 148.1 |
| 6 | Python | `btclib-ecc` | pure Python | 805.3 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **29.0** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **30.0** |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 34.3 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 35.4 |
| **5** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **35.7** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 36.2 |
| 7 | C | `libsecp256k1` | direct | 36.4 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.2 |
| 9 | Go | `libsecp256k1` | cgo (the bench's own binding) | 37.9 |
| 10 | Go | `splitsecure` | pure Go | 43.3 |
| 11 | Rust | `k256` | pure Rust | 53.4 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **61.2** |
| 13 | Rust | `secp256kfun` | pure Rust | 77.9 |
| 14 | Go | `secp256k1-voi` | pure Go | 83.9 |
| 15 | Go | `p256k1` | pure Go | 110.9 |
| 16 | JVM | `bouncycastle` | pure Java | 146.2 |
| 17 | Go | `btcec` | pure Go | 187.7 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 210.6 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 212.7 |
| 20 | Bun | `noble-curves` | pure JavaScript | 446.4 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 884.9 |
| 22 | Python | `btclib-ecc` | pure Python | 964.4 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1229.7 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1336.5 |
| 25 | Python | `embit` | pure Python | 2380.3 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2501.1 |
| 27 | Python | `secp256k1lab` | pure Python | 9387.4 |
| 28 | JVM | `nostr-java` | pure Java | 12464.1 |
| 29 | Python | `bip340-reference` | pure Python | 85000.5 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **25.2** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **25.8** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **32.9** |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.2 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.3 |
| 6 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 33.4 |
| 7 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.6 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.9 |
| 9 | C | `libsecp256k1` | direct | 35.5 |
| 10 | Go | `splitsecure` | pure Go | 36.8 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **49.0** |
| 12 | Rust | `k256` | pure Rust | 60.0 |
| 13 | Go | `secp256k1-voi` | pure Go | 64.5 |
| 14 | Rust | `secp256kfun` | pure Rust | 78.7 |
| 15 | Go | `p256k1` | pure Go | 100.8 |
| 16 | JVM | `bouncycastle` | pure Java | 131.5 |
| 17 | Go | `btcec` | pure Go | 185.0 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 214.1 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 218.3 |
| 20 | Bun | `noble-curves` | pure JavaScript | 411.9 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 805.7 |
| 22 | Python | `btclib-ecc` | pure Python | 999.9 |
| 23 | JVM | `bitcoin-s` | pure Scala | 1277.9 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 1362.3 |
| 25 | Python | `embit` | pure Python | 2312.7 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2389.1 |
| 27 | Python | `secp256k1lab` | pure Python | 9003.6 |
| 28 | JVM | `nostr-java` | pure Java | 13296.7 |
| 29 | Python | `bip340-reference` | pure Python | 84095.8 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **19.7** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **20.8** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **25.7** |
| 4 | C | `libsecp256k1` | direct | 30.9 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.6 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 31.9 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.5 |
| 8 | Go | `splitsecure` | pure Go | 45.5 |
| 9 | Rust | `k256` | pure Rust | 47.5 |
| **10** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.0** |
| 11 | Go | `btcec` | pure Go | 77.3 |
| 12 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 81.6 |
| 13 | Go | `secp256k1-voi` | pure Go | 87.5 |
| 14 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 94.9 |
| 15 | Rust | `secp256kfun` | pure Rust | 99.5 |
| 16 | Go | `p256k1` | pure Go | 117.2 |
| 17 | JVM | `bouncycastle` | pure Java | 148.5 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 197.2 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 207.4 |
| 20 | Bun | `noble-curves` | pure JavaScript | 738.1 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 989.6 |
| 22 | Python | `btclib-ecc` | pure Python | 1512.1 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1904.2 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 2272.2 |
| 25 | Python | `embit` | pure Python | 4850.6 |
| 26 | JVM | `bitcoin-s` | pure Scala | 7202.6 |
| 27 | Python | `secp256k1lab` | pure Python | 13918.0 |
| 28 | JVM | `nostr-java` | pure Java | 25847.6 |
| 29 | Python | `bip340-reference` | pure Python | 163844.8 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.8** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **11.0** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **14.1** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **24.2** |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 30.0 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 30.6 |
| 7 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.0 |
| 8 | C | `libsecp256k1` | direct | 32.3 |
| 9 | Go | `splitsecure` | pure Go | 43.4 |
| 10 | Rust | `k256` | pure Rust | 47.3 |
| 11 | Go | `btcec` | pure Go | 63.3 |
| 12 | Rust | `secp256kfun` | pure Rust | 78.2 |
| 13 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 82.1 |
| 14 | Go | `secp256k1-voi` | pure Go | 84.9 |
| 15 | JVM | `bouncycastle` | pure Java | 88.4 |
| 16 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 105.4 |
| 17 | Go | `p256k1` | pure Go | 126.1 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 200.9 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 212.6 |
| 20 | Bun | `noble-curves` | pure JavaScript | 735.2 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 1004.2 |
| 22 | Python | `btclib-ecc` | pure Python | 1562.7 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 2221.4 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 2872.8 |
| 25 | Python | `embit` | pure Python | 4715.3 |
| 26 | JVM | `bitcoin-s` | pure Scala | 7231.6 |
| 27 | Python | `secp256k1lab` | pure Python | 12265.0 |
| 28 | JVM | `nostr-java` | pure Java | 25567.6 |
| 29 | Python | `bip340-reference` | pure Python | 169983.1 |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.0** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **10.3** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **11.6** |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 15.3 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 15.5 |
| 6 | C | `libsecp256k1` | direct | 15.6 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 15.7 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 18.9 |
| 9 | Go | `splitsecure` | pure Go | 22.8 |
| 10 | Rust | `k256` | pure Rust | 23.5 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **23.7** |
| 12 | Go | `secp256k1-voi` | pure Go | 28.6 |
| 13 | Go | `btcec` | pure Go | 36.7 |
| 14 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 41.1 |
| 15 | Rust | `secp256kfun` | pure Rust | 48.3 |
| 16 | Go | `p256k1` | pure Go | 48.6 |
| 17 | JVM | `bouncycastle` | pure Java | 71.1 |
| 18 | Bun | `noble-secp256k1` | pure JavaScript | 89.9 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 93.6 |
| 20 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 102.1 |
| 21 | Bun | `noble-curves` | pure JavaScript | 133.8 |
| 22 | Python | `btclib-ecc` | pure Python | 176.4 |
| 23 | Node.js | `noble-secp256k1` | pure JavaScript | 227.8 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 383.6 |
| 25 | JVM | `bitcoin-s` | pure Scala | 454.2 |
| 26 | Python | `embit` | pure Python | 1539.8 |
| 27 | Python | `secp256k1lab` | pure Python | 2420.9 |
| 28 | Python | `bip340-reference` | pure Python | 40868.1 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 30.6 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **30.8** |
| 3 | Go | `libsecp256k1` | cgo (the bench's own binding) | 31.5 |
| 4 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.5 |
| 5 | C | `libsecp256k1` | direct | 31.8 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.0** |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.4 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 36.7 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **39.7** |
| 10 | Go | `splitsecure` | pure Go | 45.9 |
| 11 | Rust | `k256` | pure Rust | 53.9 |
| 12 | Rust | `secp256kfun` | pure Rust | 56.6 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **65.3** |
| 14 | Go | `secp256k1-voi` | pure Go | 74.3 |
| 15 | Go | `p256k1` | pure Go | 150.6 |
| 16 | Go | `btcec` | pure Go | 152.0 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 194.1 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 194.6 |
| 19 | JVM | `bouncycastle` | pure Java | 224.2 |
| 20 | JVM | `bitcoin-s` | pure Scala | 612.5 |
| 21 | Bun | `noble-curves` | pure JavaScript | 725.1 |
| 22 | Python | `btclib-ecc` | pure Python | 934.8 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 1005.2 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2162.4 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2856.7 |
| 26 | Python | `secp256k1lab` | pure Python | 7121.6 |
| 27 | Python | `bip340-reference` | pure Python | 43023.9 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 28.6 |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **30.9** |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.0 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.0 |
| **5** | **C** | **`libschnorr256k1`** | **direct** | **32.2** |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.8 |
| 7 | C | `libsecp256k1` | direct | 34.0 |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.6 |
| **9** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **39.7** |
| 10 | Go | `splitsecure` | pure Go | 43.6 |
| 11 | Rust | `k256` | pure Rust | 52.2 |
| 12 | Rust | `secp256kfun` | pure Rust | 54.9 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **65.2** |
| 14 | Go | `secp256k1-voi` | pure Go | 74.6 |
| 15 | Go | `btcec` | pure Go | 141.4 |
| 16 | Go | `p256k1` | pure Go | 169.9 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 196.6 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 198.6 |
| 19 | JVM | `bouncycastle` | pure Java | 223.4 |
| 20 | JVM | `bitcoin-s` | pure Scala | 579.2 |
| 21 | Bun | `noble-curves` | pure JavaScript | 727.1 |
| 22 | Python | `btclib-ecc` | pure Python | 887.0 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 924.8 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2311.4 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2867.1 |
| 26 | Python | `secp256k1lab` | pure Python | 6895.7 |
| 27 | Python | `bip340-reference` | pure Python | 42785.8 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 28.7 |
| 2 | Go | `libsecp256k1` | cgo (the bench's own binding) | 30.3 |
| 3 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 31.7 |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **32.1** |
| 5 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 32.1 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.2 |
| 7 | C | `libsecp256k1` | direct | 33.0 |
| **8** | **C** | **`libschnorr256k1`** | **direct** | **34.6** |
| 9 | Go | `splitsecure` | pure Go | 39.3 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **40.1** |
| 11 | Rust | `k256` | pure Rust | 52.9 |
| 12 | Rust | `secp256kfun` | pure Rust | 54.9 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **59.1** |
| 14 | Go | `secp256k1-voi` | pure Go | 75.6 |
| 15 | JVM | `bouncycastle` | pure Java | 121.8 |
| 16 | Go | `btcec` | pure Go | 140.1 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 197.0 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 207.0 |
| 19 | Go | `p256k1` | pure Go | 209.1 |
| 20 | JVM | `bitcoin-s` | pure Scala | 591.5 |
| 21 | Bun | `noble-curves` | pure JavaScript | 622.4 |
| 22 | Python | `btclib-ecc` | pure Python | 933.5 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 959.6 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2160.8 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2842.9 |
| 26 | Python | `secp256k1lab` | pure Python | 7168.9 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| 2 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.04 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **0.06** |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.10 |
| 5 | Go | `splitsecure` | pure Go | 0.11 |
| 6 | C | `libsecp256k1` | direct | 0.12 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.14** |
| **8** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.15** |
| 9 | Rust | `k256` | pure Rust | 0.16 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.17 |
| 11 | Go | `btcec` | pure Go | 0.28 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.29** |
| 13 | Go | `p256k1` | pure Go | 0.33 |
| 14 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.43 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.47 |
| 16 | JVM | `bouncycastle` | pure Java | 0.58 |
| 17 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.54 |
| 18 | Bun | `noble-curves` | pure JavaScript | 2.21 |
| 19 | Node.js | `noble-curves` | pure JavaScript | 4.66 |
| 20 | Python | `btclib-ecc` | pure Python | 5.07 |
| 21 | Rust | `secp256kfun` | pure Rust | 5.52 |
| 22 | Node.js | `noble-secp256k1` | pure JavaScript | 6.07 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 6.10 |
| 24 | Python | `embit` | pure Python | 6.35 |
| 25 | Go | `secp256k1-voi` | pure Go | 6.72 |
| 26 | Python | `secp256k1lab` | pure Python | 7.34 |
| 27 | JVM | `bitcoin-s` | pure Scala | 17.5 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | C | `libsecp256k1` | direct | 0.03 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.03** |
| 3 | Go | `p256k1` | pure Go | 0.03 |
| 4 | Go | `splitsecure` | pure Go | 0.04 |
| **5** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.04** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.05 |
| 7 | Rust | `secp256kfun` | pure Rust | 0.05 |
| 8 | Rust | `k256` | pure Rust | 0.05 |
| 9 | Go | `btcec` | pure Go | 0.06 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.06 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.09** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.12** |
| 13 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.13 |
| 14 | Go | `secp256k1-voi` | pure Go | 0.15 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.15 |
| 16 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 17 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.19 |
| 18 | Bun | `noble-curves` | pure JavaScript | 1.07 |
| 19 | Python | `btclib-ecc` | pure Python | 1.24 |
| 20 | Python | `embit` | pure Python | 1.41 |
| 21 | Python | `secp256k1lab` | pure Python | 1.70 |
| 22 | Node.js | `noble-curves` | pure JavaScript | 1.70 |
| 23 | JVM | `bitcoin-s` | pure Scala | 2.48 |
| 24 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 73.3 |

### Check a secret key

`seckey.verify`: 0 < key < n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.00 |
| 2 | Go | `p256k1` | pure Go | 0.01 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **0.01** |
| 4 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.01 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.01 |
| 6 | C | `libsecp256k1` | direct | 0.01 |
| **7** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.01** |
| 8 | Go | `splitsecure` | pure Go | 0.01 |
| 9 | Rust | `secp256kfun` | pure Rust | 0.01 |
| 10 | Rust | `k256` | pure Rust | 0.02 |
| 11 | Go | `btcec` | pure Go | 0.02 |
| **12** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.03** |
| 13 | JVM | `bouncycastle` | pure Java | 0.03 |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.04 |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.1.0** | **0.04** |
| 16 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.04 |
| 17 | JVM | `bitcoin-s` | pure Scala | 0.05 |
| 18 | Go | `secp256k1-voi` | pure Go | 0.06 |
| 19 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.08 |
| 20 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.32 |
| 21 | Bun | `noble-curves` | pure JavaScript | 0.33 |
| 22 | Python | `embit` | pure Python | 0.42 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 0.45 |
| 24 | Python | `secp256k1lab` | pure Python | 0.53 |
| 25 | Python | `btclib-ecc` | pure Python | 0.67 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 1.48 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 1.96 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13
- JVM: Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
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
