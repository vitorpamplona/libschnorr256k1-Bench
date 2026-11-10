# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per caller on each device, from its newest run: median µs per operation.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.10GHz; machine: shared. Median µs per operation.

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 34.0 | 26.2 |
| verify.manyKeys | 34.4 | 36.4 |
| verifyFast.sameKey | 35.5 | 25.8 |
| verifyBatch200.perSig | - | 10.8 |
| sign | 30.9 | 20.8 |
| sign.knownPub | 32.3 | 10.8 |
| pubkey.create | 15.6 | 10.0 |
| pubkey.compress | 0.12 | 0.06 |
| seckey.verify | 0.01 | 0.01 |
| ecdh.xOnly | 34.0 | 32.2 |
| tweak.mul | 33.0 | 34.6 |
| tweak.add | 0.03 | 0.03 |
| nostr.verify | 34.3 | 30.9 |
| nostr.verifyFast | 36.4 | 29.0 |
| nostr.verifyBatchByAuthor | - | 18.0 |
| nostr.ecdh | 31.8 | 30.8 |

2026-11-10, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d; harness be96320

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 33.6 | 55.5 | 79.3 |
| verify.manyKeys | 36.8 | 60.5 | 80.0 |
| verifyFast.sameKey | 33.6 | 60.0 | 78.7 |
| sign | 81.6 | 47.5 | 99.5 |
| sign.knownPub | 82.1 | 47.3 | 78.2 |
| pubkey.create | 41.1 | 23.5 | 48.3 |
| pubkey.compress | 0.10 | 0.16 | 5.52 |
| seckey.verify | 0.01 | 0.02 | 0.01 |
| ecdh.xOnly | 28.6 | 52.2 | 54.9 |
| tweak.mul | 31.7 | 52.9 | 54.9 |
| tweak.add | 0.05 | 0.05 | 0.05 |
| nostr.verify | 35.9 | 62.1 | 79.6 |
| nostr.verifyFast | 36.2 | 53.4 | 77.9 |
| nostr.ecdh | 30.6 | 53.9 | 56.6 |

2026-11-10, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness be96320

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.8 | 168.9 | 101.5 | 76.2 | 40.0 |
| verify.manyKeys | 36.7 | 188.2 | 113.6 | 79.6 | 43.9 |
| verifyFast.sameKey | 33.9 | 185.0 | 100.8 | 64.5 | 36.8 |
| verifyBatch200.perSig | - | - | 148.1 | - | - |
| sign | 31.9 | 77.3 | 117.2 | 87.5 | 45.5 |
| sign.knownPub | 32.0 | 63.3 | 126.1 | 84.9 | 43.4 |
| pubkey.create | 15.5 | 36.7 | 48.6 | 28.6 | 22.8 |
| pubkey.compress | 0.17 | 0.28 | 0.33 | 6.72 | 0.11 |
| seckey.verify | 0.04 | 0.02 | 0.01 | 0.06 | 0.01 |
| ecdh.xOnly | 32.8 | 141.4 | 169.9 | 74.6 | 43.6 |
| tweak.mul | 30.3 | 140.1 | 209.1 | 75.6 | 39.3 |
| tweak.add | 0.06 | 0.06 | 0.03 | 0.15 | 0.04 |
| nostr.verify | 37.5 | 179.0 | 112.5 | 84.4 | 45.3 |
| nostr.verifyFast | 37.9 | 187.7 | 110.9 | 83.9 | 43.3 |
| nostr.verifyBatchByAuthor | - | - | 155.5 | - | - |
| nostr.ecdh | 31.5 | 152.0 | 150.6 | 74.3 | 45.9 |

2026-11-10, Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness be96320

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | kotlin (pure Kotlin) |
|---|---:|---:|
| verify.sameKey | 33.5 | 34.8 |
| verify.manyKeys | 37.0 | 47.1 |
| verifyFast.sameKey | 33.2 | 32.9 |
| verifyBatch200.perSig | - | 14.0 |
| sign | 32.5 | 25.7 |
| sign.knownPub | 30.0 | 14.1 |
| pubkey.create | 15.7 | 11.6 |
| pubkey.compress | 0.04 | 0.29 |
| seckey.verify | 0.08 | 0.03 |
| ecdh.xOnly | 32.0 | 39.7 |
| tweak.mul | 28.7 | 40.1 |
| tweak.add | 0.19 | 0.09 |
| nostr.verify | 32.9 | 37.5 |
| nostr.verifyFast | 35.4 | 35.7 |
| nostr.verifyBatchByAuthor | - | 24.6 |
| nostr.ecdh | 32.4 | 39.7 |

2026-11-10, Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13; harness be96320

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.1.0) | kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 30.5 | 22.1 | 50.6 | 1200.3 | 128.8 | 11712.0 |
| verify.manyKeys | 36.1 | 36.6 | 75.8 | 1236.3 | 143.2 | 12823.9 |
| verifyFast.sameKey | 33.3 | 25.2 | 49.0 | 1277.9 | 131.5 | 13296.7 |
| verifyBatch200.perSig | - | 11.1 | 26.4 | - | - | - |
| sign | 31.6 | 19.7 | 50.0 | 7202.6 | 148.5 | 25847.6 |
| sign.knownPub | 30.6 | 11.0 | 24.2 | 7231.6 | 88.4 | 25567.6 |
| pubkey.create | 15.3 | 10.3 | 23.7 | 454.2 | 71.1 | - |
| pubkey.compress | 0.02 | 0.14 | 0.15 | 17.5 | 0.58 | - |
| seckey.verify | 0.04 | 0.04 | 0.01 | 0.05 | 0.03 | - |
| ecdh.xOnly | 32.0 | 30.9 | 65.2 | 579.2 | 223.4 | - |
| tweak.mul | 32.2 | 32.1 | 59.1 | 591.5 | 121.8 | - |
| tweak.add | 0.13 | 0.12 | 0.04 | 2.48 | - | - |
| nostr.verify | 36.7 | 27.3 | 66.0 | 1205.5 | 142.9 | 13216.2 |
| nostr.verifyFast | 34.3 | 30.0 | 61.2 | 1229.7 | 146.2 | 12464.1 |
| nostr.verifyBatchByAuthor | - | 17.6 | 41.1 | - | - | - |
| nostr.ecdh | 31.5 | 32.0 | 65.3 | 612.5 | 224.2 | - |

2026-11-10, Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness be96320

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 213.2 | 1365.7 | 2130.6 |
| verify.manyKeys | 221.7 | 1263.6 | 2009.2 |
| verifyFast.sameKey | 218.3 | 1362.3 | 2389.1 |
| sign | 207.4 | 1904.2 | 2272.2 |
| sign.knownPub | 212.6 | 2221.4 | 2872.8 |
| pubkey.create | 102.1 | 383.6 | 227.8 |
| pubkey.compress | 0.47 | 4.66 | 6.07 |
| seckey.verify | 0.01 | 0.45 | 1.48 |
| ecdh.xOnly | 198.6 | 2311.4 | 2867.1 |
| tweak.mul | 197.0 | 2160.8 | 2842.9 |
| tweak.add | 0.15 | 1.70 | - |
| nostr.verify | 226.8 | 1393.5 | 2383.7 |
| nostr.verifyFast | 210.6 | 1336.5 | 2501.1 |
| nostr.ecdh | 194.1 | 2162.4 | 2856.7 |

2026-11-10, Linux 6.18.44-fc-v338, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness be96320

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 207.4 | 428.9 | 818.4 |
| verify.manyKeys | 207.6 | 421.8 | 769.1 |
| verifyFast.sameKey | 214.1 | 411.9 | 805.7 |
| sign | 197.2 | 738.1 | 989.6 |
| sign.knownPub | 200.9 | 735.2 | 1004.2 |
| pubkey.create | 93.6 | 133.8 | 89.9 |
| pubkey.compress | 0.43 | 2.21 | 6.10 |
| seckey.verify | 0.00 | 0.33 | 1.96 |
| ecdh.xOnly | 196.6 | 727.1 | 924.8 |
| tweak.mul | 207.0 | 622.4 | 959.6 |
| tweak.add | 0.16 | 1.07 | - |
| nostr.verify | 210.1 | 458.2 | 866.5 |
| nostr.verifyFast | 212.7 | 446.4 | 884.9 |
| nostr.ecdh | 194.6 | 725.1 | 1005.2 |

2026-11-10, Linux 6.18.44-fc-v338, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness be96320

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.1 | 83202.5 | 998.4 | 2199.0 | 7679.7 |
| verify.manyKeys | 37.2 | 75691.5 | 1033.1 | 2337.6 | 8581.6 |
| verifyFast.sameKey | 33.4 | 84095.8 | 999.9 | 2312.7 | 9003.6 |
| verifyBatch200.perSig | - | - | 805.3 | - | - |
| sign | 94.9 | 163844.8 | 1512.1 | 4850.6 | 13918.0 |
| sign.knownPub | 105.4 | 169983.1 | 1562.7 | 4715.3 | 12265.0 |
| pubkey.create | 18.9 | 40868.1 | 176.4 | 1539.8 | 2420.9 |
| pubkey.compress | 1.54 | - | 5.07 | 6.35 | 7.34 |
| seckey.verify | 0.32 | - | 0.67 | 0.42 | 0.53 |
| ecdh.xOnly | 34.6 | 42785.8 | 887.0 | - | 6895.7 |
| tweak.mul | 32.1 | - | 933.5 | - | 7168.9 |
| tweak.add | 73.3 | - | 1.24 | 1.41 | 1.70 |
| nostr.verify | 36.7 | 87037.0 | 931.8 | 2243.8 | 9325.1 |
| nostr.verifyFast | 37.2 | 85000.5 | 964.4 | 2380.3 | 9387.4 |
| nostr.verifyBatchByAuthor | - | - | 956.9 | - | - |
| nostr.ecdh | 36.7 | 43023.9 | 934.8 | - | 7121.6 |

2026-11-10, Linux 6.18.44-fc-v338, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness be96320
