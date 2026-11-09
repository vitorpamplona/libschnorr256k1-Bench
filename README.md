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

Measured on Cloud VM (4 vCPU) (Intel(R) Xeon(R) Processor @ 2.10GHz), **a shared machine** (a cloud VM: other work may have run beside it, so the numbers are noisier than a dedicated run's, which will replace them), single thread, 2026-11-09. Each table ranks one operation by its median µs per call. A wrong result is listed last, unranked; KI-n marks a known issue ([results/SCHEMA.md](results/SCHEMA.md#known-issues)).

### Verify real Nostr events

`nostr.verify`: the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **31.1** |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.9 |
| 3 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 37.4 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 38.1 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 38.1 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.2 |
| 7 | C | `libsecp256k1` | direct | 38.2 |
| 8 | Go | `splitsecure` | pure Go | 46.2 |
| **9** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **52.8** |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **53.8** |
| 11 | Rust | `k256` | pure Rust | 62.3 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **78.0** |
| 13 | Rust | `secp256kfun` | pure Rust | 80.0 |
| 14 | Go | `secp256k1-voi` | pure Go | 84.0 |
| 15 | Go | `p256k1` | pure Go | 113.0 |
| 16 | JVM | `bouncycastle` | pure Java | 153.5 |
| 17 | Go | `btcec` | pure Go | 191.5 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.0 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 220.3 |
| 20 | Bun | `noble-curves` | pure JavaScript | 507.6 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 972.2 |
| 22 | Python | `btclib-ecc` | pure Python | 1055.3 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1391.5 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1450.0 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2386.1 |
| 26 | Python | `embit` | pure Python | 2406.1 |
| 27 | Python | `secp256k1lab` | pure Python | 9146.8 |
| 28 | JVM | `nostr-java` | pure Java | 13271.3 |
| 29 | Python | `bip340-reference` | pure Python | 88496.2 |

### Verify, a new key each time

`verify.manyKeys`: 4,096 keys in strict rotation, so no per-key cache helps.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **36.1** |
| 2 | C | `libsecp256k1` | direct | 36.8 |
| 3 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 36.9 |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 37.6 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.7 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 38.5 |
| 7 | Go | `libsecp256k1` | cgo (the bench's own binding) | 39.9 |
| 8 | Go | `splitsecure` | pure Go | 45.7 |
| 9 | Rust | `k256` | pure Rust | 62.2 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **65.0** |
| **11** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **65.5** |
| 12 | Rust | `secp256kfun` | pure Rust | 79.3 |
| 13 | Go | `secp256k1-voi` | pure Go | 85.0 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **96.5** |
| 15 | Go | `p256k1` | pure Go | 113.0 |
| 16 | JVM | `bouncycastle` | pure Java | 154.5 |
| 17 | Go | `btcec` | pure Go | 195.0 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 217.0 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 224.3 |
| 20 | Bun | `noble-curves` | pure JavaScript | 483.1 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 1000.7 |
| 22 | Python | `btclib-ecc` | pure Python | 1091.0 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1375.6 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1436.1 |
| 25 | Python | `embit` | pure Python | 2411.1 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2452.0 |
| 27 | Python | `secp256k1lab` | pure Python | 9285.3 |
| 28 | JVM | `nostr-java` | pure Java | 13742.0 |
| 29 | Python | `bip340-reference` | pure Python | 90700.2 |

### Verify, one key

`verify.sameKey`: the same key every call: the best case for a per-key cache.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **26.1** |
| **2** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **31.1** |
| **3** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **33.0** |
| 4 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.0 |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 34.4 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 35.3 |
| 7 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 35.9 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.9 |
| 9 | C | `libsecp256k1` | direct | 36.2 |
| 10 | Go | `splitsecure` | pure Go | 42.2 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **53.6** |
| 12 | Rust | `k256` | pure Rust | 58.9 |
| 13 | Rust | `secp256kfun` | pure Rust | 79.6 |
| 14 | Go | `secp256k1-voi` | pure Go | 83.4 |
| 15 | Go | `p256k1` | pure Go | 102.1 |
| 16 | JVM | `bouncycastle` | pure Java | 138.8 |
| 17 | Go | `btcec` | pure Go | 189.3 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 198.2 |
| 19 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 212.0 |
| 20 | Bun | `noble-curves` | pure JavaScript | 449.5 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 954.5 |
| 22 | Python | `btclib-ecc` | pure Python | 1069.0 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1308.9 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1355.3 |
| 25 | Python | `embit` | pure Python | 2378.2 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2437.3 |
| 27 | Python | `secp256k1lab` | pure Python | 9130.6 |
| 28 | JVM | `nostr-java` | pure Java | 13617.9 |
| 29 | Python | `bip340-reference` | pure Python | 86412.6 |

### Batch verify, real events

`nostr.verifyBatchByAuthor`: the dataset in pages of 200, each page's events batched by author; per event.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **18.9** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **19.5 KI-1** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **34.2** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **60.9** |
| 5 | Go | `p256k1` | pure Go | 153.9 |
| 6 | Python | `btclib-ecc` | pure Python | 1001.4 |

### Batch verify, 200 signatures by one key

`verifyBatch200.perSig`: per signature.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **4.49 KI-1** |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **10.8** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **21.0** |
| **4** | **JVM** | **`kotlin`** | **pure Kotlin** | **31.9** |
| 5 | Go | `p256k1` | pure Go | 146.1 |
| 6 | Python | `btclib-ecc` | pure Python | 817.5 |

### Fast verify, real events

`nostr.verifyFast`: a library's fast verify where it has one, else its full verify.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **30.6** |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 36.5 |
| 3 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 37.0 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 37.2 |
| 5 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 38.0 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 38.4 |
| 7 | C | `libsecp256k1` | direct | 38.8 |
| 8 | Go | `splitsecure` | pure Go | 46.0 |
| **9** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **51.5** |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **52.0** |
| 11 | Rust | `k256` | pure Rust | 59.4 |
| 12 | Rust | `secp256kfun` | pure Rust | 77.7 |
| **13** | **JVM** | **`kotlin`** | **pure Kotlin** | **79.4** |
| 14 | Go | `secp256k1-voi` | pure Go | 85.4 |
| 15 | Go | `p256k1` | pure Go | 115.3 |
| 16 | JVM | `bouncycastle` | pure Java | 190.1 |
| 17 | Go | `btcec` | pure Go | 193.6 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 219.5 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 221.1 |
| 20 | Bun | `noble-curves` | pure JavaScript | 484.4 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 996.3 |
| 22 | Python | `btclib-ecc` | pure Python | 1062.9 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1354.2 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1415.1 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2357.4 |
| 26 | Python | `embit` | pure Python | 2439.6 |
| 27 | Python | `secp256k1lab` | pure Python | 9545.3 |
| 28 | JVM | `nostr-java` | pure Java | 13378.2 |
| 29 | Python | `bip340-reference` | pure Python | 84412.2 |

### Fast verify, one key

`verifyFast.sameKey`: likewise.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **24.8** |
| **2** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **29.6** |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **29.6** |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.7 |
| 5 | C | `libsecp256k1` | direct | 33.8 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 34.0 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 34.2 |
| 8 | Go | `libsecp256k1` | cgo (the bench's own binding) | 35.3 |
| 9 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 35.3 |
| 10 | Go | `splitsecure` | pure Go | 43.1 |
| **11** | **JVM** | **`kotlin`** | **pure Kotlin** | **50.3** |
| 12 | Rust | `k256` | pure Rust | 59.9 |
| 13 | Rust | `secp256kfun` | pure Rust | 78.2 |
| 14 | Go | `secp256k1-voi` | pure Go | 85.9 |
| 15 | Go | `p256k1` | pure Go | 100.5 |
| 16 | JVM | `bouncycastle` | pure Java | 135.6 |
| 17 | Go | `btcec` | pure Go | 186.1 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 208.2 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 213.4 |
| 20 | Bun | `noble-curves` | pure JavaScript | 466.1 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 922.0 |
| 22 | Python | `btclib-ecc` | pure Python | 1044.8 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 1314.5 |
| 24 | JVM | `bitcoin-s` | pure Scala | 1360.4 |
| 25 | Python | `embit` | pure Python | 2347.3 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 2484.1 |
| 27 | Python | `secp256k1lab` | pure Python | 9215.8 |
| 28 | JVM | `nostr-java` | pure Java | 13635.1 |
| 29 | Python | `bip340-reference` | pure Python | 89153.3 |

### Sign

`sign`: BIP-340 with aux randomness, from the secret key.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **21.5** |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.9 |
| 3 | C | `libsecp256k1` | direct | 33.1 |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.9 |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 34.3 |
| **6** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **36.1** |
| **7** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **37.0** |
| 8 | Rust | `k256` | pure Rust | 46.8 |
| 9 | Go | `splitsecure` | pure Go | 47.3 |
| **10** | **JVM** | **`kotlin`** | **pure Kotlin** | **56.8** |
| 11 | Go | `btcec` | pure Go | 78.4 |
| 12 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 84.6 |
| 13 | Go | `secp256k1-voi` | pure Go | 92.2 |
| 14 | Rust | `secp256kfun` | pure Rust | 95.7 |
| 15 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 104.6 |
| 16 | Go | `p256k1` | pure Go | 123.5 |
| 17 | JVM | `bouncycastle` | pure Java | 161.7 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.1 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 204.4 |
| 20 | Bun | `noble-curves` | pure JavaScript | 843.7 |
| 21 | Bun | `noble-secp256k1` | pure JavaScript | 1273.4 |
| 22 | Python | `btclib-ecc` | pure Python | 1557.0 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 2112.5 |
| 24 | Node.js | `noble-secp256k1` | pure JavaScript | 2913.4 |
| 25 | Python | `embit` | pure Python | 4789.2 |
| 26 | JVM | `bitcoin-s` | pure Scala | 7978.5 |
| 27 | Python | `secp256k1lab` | pure Python | 13953.7 |
| 28 | JVM | `nostr-java` | pure Java | 27671.1 |
| 29 | Python | `bip340-reference` | pure Python | 177101.4 |

### Sign with a known public key

`sign.knownPub`: where the library can skip deriving it.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.9** |
| **2** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **20.2** |
| **3** | **JVM** | **`kotlin`** | **pure Kotlin** | **29.9** |
| 4 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.9 |
| 5 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 33.2 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.3 |
| 7 | C | `libsecp256k1` | direct | 34.5 |
| 8 | Rust | `k256` | pure Rust | 47.8 |
| 9 | Go | `splitsecure` | pure Go | 50.3 |
| 10 | Go | `btcec` | pure Go | 83.6 |
| 11 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 84.4 |
| 12 | Go | `secp256k1-voi` | pure Go | 89.6 |
| 13 | JVM | `bouncycastle` | pure Java | 96.7 |
| 14 | Rust | `secp256kfun` | pure Rust | 98.7 |
| 15 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 106.0 |
| 16 | Go | `p256k1` | pure Go | 125.7 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 198.6 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 209.1 |
| 19 | Bun | `noble-curves` | pure JavaScript | 892.0 |
| 20 | Bun | `noble-secp256k1` | pure JavaScript | 1270.0 |
| 21 | Python | `btclib-ecc` | pure Python | 1524.7 |
| 22 | Node.js | `noble-curves` | pure JavaScript | 2162.1 |
| 23 | Node.js | `noble-secp256k1` | pure JavaScript | 2938.6 |
| 24 | Python | `embit` | pure Python | 4710.2 |
| 25 | JVM | `bitcoin-s` | pure Scala | 7837.3 |
| 26 | Python | `secp256k1lab` | pure Python | 13802.8 |
| 27 | JVM | `nostr-java` | pure Java | 28212.5 |
| 28 | Python | `bip340-reference` | pure Python | 172260.3 |
| – | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **18.6 **wrong** KI-2** |

### Create a public key

`pubkey.create`: uncompressed, 65 bytes.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **10.6** |
| 2 | C | `libsecp256k1` | direct | 15.4 |
| **3** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **16.2** |
| 4 | Go | `libsecp256k1` | cgo (the bench's own binding) | 16.3 |
| 5 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 16.6 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 16.8 |
| **7** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **18.0** |
| 8 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 18.7 |
| 9 | Rust | `k256` | pure Rust | 23.1 |
| 10 | Go | `splitsecure` | pure Go | 24.2 |
| 11 | Go | `secp256k1-voi` | pure Go | 28.8 |
| **12** | **JVM** | **`kotlin`** | **pure Kotlin** | **29.8** |
| 13 | Go | `btcec` | pure Go | 39.9 |
| 14 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 41.9 |
| 15 | Rust | `secp256kfun` | pure Rust | 48.5 |
| 16 | Go | `p256k1` | pure Go | 50.2 |
| 17 | JVM | `bouncycastle` | pure Java | 80.8 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 97.8 |
| 19 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 99.6 |
| 20 | Bun | `noble-secp256k1` | pure JavaScript | 123.7 |
| 21 | Bun | `noble-curves` | pure JavaScript | 156.5 |
| 22 | Python | `btclib-ecc` | pure Python | 221.8 |
| 23 | Node.js | `noble-secp256k1` | pure JavaScript | 250.2 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 388.7 |
| 25 | JVM | `bitcoin-s` | pure Scala | 500.9 |
| 26 | Python | `embit` | pure Python | 1553.9 |
| 27 | Python | `secp256k1lab` | pure Python | 2300.2 |
| 28 | Python | `bip340-reference` | pure Python | 43286.0 |

### ECDH with real authors' keys

`nostr.ecdh`: x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44).

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.3 |
| 2 | Go | `libsecp256k1` | cgo (the bench's own binding) | 32.8 |
| **3** | **C** | **`libschnorr256k1`** | **direct** | **33.0** |
| 4 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 33.2 |
| 5 | C | `libsecp256k1` | direct | 33.6 |
| 6 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 34.0 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.0 |
| 8 | Go | `splitsecure` | pure Go | 49.4 |
| 9 | Rust | `k256` | pure Rust | 53.1 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **54.6** |
| 11 | Rust | `secp256kfun` | pure Rust | 55.5 |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **58.1** |
| 13 | Go | `secp256k1-voi` | pure Go | 76.4 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **86.1** |
| 15 | Go | `btcec` | pure Go | 150.7 |
| 16 | Go | `p256k1` | pure Go | 172.0 |
| 17 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 189.4 |
| 18 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 195.2 |
| 19 | JVM | `bouncycastle` | pure Java | 232.0 |
| 20 | JVM | `bitcoin-s` | pure Scala | 645.5 |
| 21 | Bun | `noble-curves` | pure JavaScript | 773.4 |
| 22 | Python | `btclib-ecc` | pure Python | 931.1 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 1107.2 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2391.0 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2891.7 |
| 26 | Python | `secp256k1lab` | pure Python | 7163.0 |
| 27 | Python | `bip340-reference` | pure Python | 45191.6 |

### ECDH

`ecdh.xOnly`: x-only, against 4,096 keys.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 29.5 |
| 2 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 31.4 |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 32.3 |
| **4** | **C** | **`libschnorr256k1`** | **direct** | **32.3** |
| 5 | C | `libsecp256k1` | direct | 32.4 |
| 6 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.0 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.5 |
| 8 | Go | `splitsecure` | pure Go | 49.3 |
| 9 | Rust | `k256` | pure Rust | 52.3 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **53.7** |
| 11 | Rust | `secp256kfun` | pure Rust | 56.3 |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **57.6** |
| 13 | Go | `secp256k1-voi` | pure Go | 76.3 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **83.1** |
| 15 | Go | `btcec` | pure Go | 150.8 |
| 16 | Go | `p256k1` | pure Go | 172.2 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 189.5 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 205.0 |
| 19 | JVM | `bouncycastle` | pure Java | 232.3 |
| 20 | JVM | `bitcoin-s` | pure Scala | 633.9 |
| 21 | Bun | `noble-curves` | pure JavaScript | 792.5 |
| 22 | Python | `btclib-ecc` | pure Python | 938.0 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 1093.6 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2367.0 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2899.5 |
| 26 | Python | `secp256k1lab` | pure Python | 7042.6 |
| 27 | Python | `bip340-reference` | pure Python | 42572.0 |

### Tweak-multiply a public key

`tweak.mul`: a compressed point times a scalar.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 31.5 |
| 2 | C | `libsecp256k1` | direct | 31.6 |
| 3 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 32.1 |
| **4** | **C** | **`libschnorr256k1`** | **direct** | **32.2** |
| 5 | Go | `libsecp256k1` | cgo (the bench's own binding) | 33.0 |
| 6 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 33.2 |
| 7 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 35.1 |
| 8 | Go | `splitsecure` | pure Go | 39.4 |
| 9 | Rust | `k256` | pure Rust | 52.7 |
| 10 | Rust | `secp256kfun` | pure Rust | 55.3 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **55.6** |
| **12** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **57.2** |
| 13 | Go | `secp256k1-voi` | pure Go | 76.7 |
| **14** | **JVM** | **`kotlin`** | **pure Kotlin** | **82.5** |
| 15 | JVM | `bouncycastle` | pure Java | 122.4 |
| 16 | Go | `btcec` | pure Go | 149.6 |
| 17 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 192.0 |
| 18 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 196.0 |
| 19 | Go | `p256k1` | pure Go | 203.5 |
| 20 | JVM | `bitcoin-s` | pure Scala | 648.5 |
| 21 | Bun | `noble-curves` | pure JavaScript | 804.9 |
| 22 | Python | `btclib-ecc` | pure Python | 943.3 |
| 23 | Bun | `noble-secp256k1` | pure JavaScript | 1102.9 |
| 24 | Node.js | `noble-curves` | pure JavaScript | 2488.2 |
| 25 | Node.js | `noble-secp256k1` | pure JavaScript | 2972.4 |
| 26 | Python | `secp256k1lab` | pure Python | 7036.6 |

### Compress a public key

`pubkey.compress`: 65 bytes to 33.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| 1 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.02 |
| **2** | **C** | **`libschnorr256k1`** | **direct** | **0.06** |
| 3 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.09 |
| **4** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.09** |
| 5 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.11 |
| 6 | Go | `splitsecure` | pure Go | 0.12 |
| 7 | C | `libsecp256k1` | direct | 0.13 |
| 8 | Rust | `k256` | pure Rust | 0.15 |
| **9** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.19** |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.20 |
| 11 | Go | `btcec` | pure Go | 0.32 |
| 12 | Go | `p256k1` | pure Go | 0.33 |
| **13** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.45** |
| 14 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.47 |
| 15 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.50 |
| 16 | JVM | `bouncycastle` | pure Java | 0.66 |
| 17 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 1.51 |
| 18 | Bun | `noble-curves` | pure JavaScript | 2.31 |
| 19 | Node.js | `noble-curves` | pure JavaScript | 4.86 |
| 20 | Python | `btclib-ecc` | pure Python | 5.24 |
| 21 | Node.js | `noble-secp256k1` | pure JavaScript | 5.96 |
| 22 | Python | `embit` | pure Python | 6.49 |
| 23 | Rust | `secp256kfun` | pure Rust | 6.59 |
| 24 | Bun | `noble-secp256k1` | pure JavaScript | 6.70 |
| 25 | Go | `secp256k1-voi` | pure Go | 6.72 |
| 26 | Python | `secp256k1lab` | pure Python | 7.45 |
| 27 | JVM | `bitcoin-s` | pure Scala | 18.6 |

### Tweak-add a secret key

`tweak.add`: (secret + tweak) mod n.

| # | caller | library | via | µs |
|---:|---|---|---|---:|
| **1** | **C** | **`libschnorr256k1`** | **direct** | **0.02** |
| 2 | C | `libsecp256k1` | direct | 0.03 |
| 3 | Go | `p256k1` | pure Go | 0.03 |
| 4 | Go | `splitsecure` | pure Go | 0.04 |
| **5** | **JVM** | **`kotlin`** | **pure Kotlin** | **0.04** |
| 6 | Rust | `libsecp256k1` | FFI: secp256k1 0.33.1 | 0.05 |
| 7 | Rust | `secp256kfun` | pure Rust | 0.05 |
| 8 | Rust | `k256` | pure Rust | 0.05 |
| 9 | Go | `btcec` | pure Go | 0.06 |
| 10 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.06 |
| **11** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.11** |
| 12 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.13 |
| **13** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.14** |
| 14 | Go | `secp256k1-voi` | pure Go | 0.15 |
| 15 | Bun | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 16 | Node.js | `libsecp256k1` | wasm: tiny-secp256k1 2.2.5 | 0.16 |
| 17 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.32 |
| 18 | Bun | `noble-curves` | pure JavaScript | 1.24 |
| 19 | Python | `embit` | pure Python | 1.44 |
| 20 | Python | `btclib-ecc` | pure Python | 1.47 |
| 21 | Python | `secp256k1lab` | pure Python | 1.64 |
| 22 | Node.js | `noble-curves` | pure JavaScript | 1.86 |
| 23 | JVM | `bitcoin-s` | pure Scala | 2.84 |
| 24 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 73.3 |

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
| 8 | Go | `splitsecure` | pure Go | 0.01 |
| 9 | Rust | `secp256kfun` | pure Rust | 0.01 |
| **10** | **Kotlin/Native** | **`kotlin`** | **pure Kotlin** | **0.02** |
| 11 | Go | `btcec` | pure Go | 0.02 |
| 12 | Rust | `k256` | pure Rust | 0.02 |
| 13 | JVM | `bouncycastle` | pure Java | 0.03 |
| 14 | Go | `libsecp256k1` | cgo (the bench's own binding) | 0.04 |
| **15** | **JVM** | **`libschnorr256k1`** | **JNI: schnorr256k1-kmp 1.0.5** | **0.04** |
| 16 | JVM | `libsecp256k1` | JNI: secp256k1-kmp 0.24.0 | 0.05 |
| 17 | JVM | `bitcoin-s` | pure Scala | 0.06 |
| 18 | Go | `secp256k1-voi` | pure Go | 0.06 |
| 19 | Kotlin/Native | `libsecp256k1` | cinterop: secp256k1-kmp 0.24.0 | 0.12 |
| 20 | Python | `libsecp256k1` | CFFI: coincurve 21.0.0 | 0.31 |
| 21 | Bun | `noble-curves` | pure JavaScript | 0.40 |
| 22 | Python | `embit` | pure Python | 0.42 |
| 23 | Node.js | `noble-curves` | pure JavaScript | 0.49 |
| 24 | Python | `secp256k1lab` | pure Python | 0.54 |
| 25 | Python | `btclib-ecc` | pure Python | 0.71 |
| 26 | Node.js | `noble-secp256k1` | pure JavaScript | 1.52 |
| 27 | Bun | `noble-secp256k1` | pure JavaScript | 2.05 |

### Versions

- C: Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d
- Rust: Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0
- Go: Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0
- Kotlin/Native: Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415
- JVM: Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.0.5, kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2
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
