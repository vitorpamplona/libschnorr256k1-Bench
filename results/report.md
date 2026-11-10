# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per caller on each device, from its newest run: median µs per operation.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.10GHz; machine: shared. Median µs per operation.

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 30.5 | 26.6 |
| verify.manyKeys | 32.9 | 35.2 |
| verifyFast.sameKey | 33.1 | 24.7 **wrong** KI-4 |
| verifyBatch200.perSig | - | 10.8 |
| sign | 29.6 | 20.6 |
| sign.knownPub | 28.0 | 10.7 |
| pubkey.create | 15.0 | 10.1 |
| pubkey.compress | 0.12 | 0.05 |
| seckey.verify | 0.01 | 0.01 |
| ecdh.xOnly | 30.1 | 29.9 |
| tweak.mul | 29.5 | 31.1 |
| tweak.add | 0.02 | 0.03 |
| nostr.verify | 37.5 | 29.8 |
| nostr.verifyFast | 36.1 | 28.4 **wrong** KI-4 |
| nostr.verifyBatchByAuthor | - | 17.6 |
| nostr.ecdh | 33.2 | 26.6 |

2026-11-10, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 0942a32; harness 19c210c

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 34.4 | 55.8 | 78.5 |
| verify.manyKeys | 33.6 | 61.7 | 79.9 |
| verifyFast.sameKey | 34.1 | 58.5 | 78.7 |
| sign | 82.3 | 48.6 | 98.8 |
| sign.knownPub | 64.7 | 48.2 | 98.1 |
| pubkey.create | 40.2 | 23.2 | 47.8 |
| pubkey.compress | 0.11 | 0.17 | 6.56 |
| seckey.verify | 0.01 | 0.02 | 0.01 |
| ecdh.xOnly | 31.5 | 53.0 | 55.6 |
| tweak.mul | 32.0 | 53.7 | 55.1 |
| tweak.add | 0.05 | 0.06 | 0.05 |
| nostr.verify | 36.9 | 61.9 | 78.1 |
| nostr.verifyFast | 36.0 | 62.3 | 78.8 |
| nostr.ecdh | 30.6 | 52.2 | 53.6 |

2026-11-10, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness 19c210c

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 33.8 | 183.4 | 101.5 | 82.5 | 42.7 |
| verify.manyKeys | 38.8 | 186.4 | 109.4 | 86.6 | 42.5 |
| verifyFast.sameKey | 35.0 | 185.6 | 105.4 | 64.4 | 51.8 |
| verifyBatch200.perSig | - | - | 156.6 | - | - |
| sign | 34.9 | 72.9 | 122.8 | 86.3 | 47.3 |
| sign.knownPub | 33.5 | 76.7 | 120.0 | 82.1 | 46.5 |
| pubkey.create | 16.3 | 37.0 | 48.2 | 28.5 | 23.2 |
| pubkey.compress | 0.19 | 0.30 | 0.33 | 7.08 | 0.11 |
| seckey.verify | 0.04 | 0.02 | 0.01 | 0.06 | 0.01 |
| ecdh.xOnly | 32.1 | 149.3 | 171.3 | 75.7 | 42.5 |
| tweak.mul | 34.6 | 146.3 | 202.6 | 75.0 | 39.2 |
| tweak.add | 0.06 | 0.06 | 0.04 | 0.15 | 0.03 |
| nostr.verify | 38.5 | 167.9 | 113.7 | 85.4 | 45.0 |
| nostr.verifyFast | 40.7 | 190.1 | 113.9 | 75.9 | 44.8 |
| nostr.verifyBatchByAuthor | - | - | 150.5 | - | - |
| nostr.ecdh | 32.1 | 148.6 | 167.0 | 75.0 | 48.2 |

2026-11-10, Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness 19c210c

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | libschnorr256k1 (cinterop: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32) | libschnorr256k1-kotlin (pure Kotlin) |
|---|---:|---:|---:|
| verify.sameKey | 33.9 | 26.6 | 34.0 |
| verify.manyKeys | 36.2 | 35.2 | 47.2 |
| verifyFast.sameKey | 33.3 | 25.0 **wrong** KI-4 | 33.9 **wrong** KI-4 |
| verifyBatch200.perSig | - | 10.4 | 13.1 |
| sign | 32.8 | 20.9 | 25.8 |
| sign.knownPub | 33.9 | 10.7 | 12.9 |
| pubkey.create | 16.0 | 10.1 | 10.8 |
| pubkey.compress | 0.06 | 0.15 | 0.15 |
| seckey.verify | 0.10 | 0.04 | 0.02 |
| ecdh.xOnly | 33.6 | 29.5 | 29.5 |
| tweak.mul | 32.5 | 31.4 | 39.9 |
| tweak.add | 0.24 | 0.14 | 0.09 |
| nostr.verify | 37.8 | 30.5 | 41.2 |
| nostr.verifyFast | 36.1 | 28.6 **wrong** KI-4 | 38.1 **wrong** KI-4 |
| nostr.verifyBatchByAuthor | - | 18.0 | 24.0 |
| nostr.ecdh | 32.4 | 29.1 | 33.6 |

2026-11-10, Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32 (JitPack 4478ec1), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-29-ge49e229; harness 19c210c

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32) | libschnorr256k1-kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 33.3 | 26.3 | 46.2 | 1207.7 | 134.8 | 12587.9 |
| verify.manyKeys | 36.2 | 35.8 | 70.5 | 1186.1 | 152.7 | 12839.7 |
| verifyFast.sameKey | 33.3 | 24.5 **wrong** KI-4 | 38.8 **wrong** KI-4 | 1213.5 | 129.0 | 12697.9 |
| verifyBatch200.perSig | - | 11.1 | 23.1 | - | - | - |
| sign | 32.2 | 16.4 | 46.9 | 7017.5 | 151.5 | 25980.6 |
| sign.knownPub | 32.6 | 10.9 | 24.3 | 6973.5 | 91.5 | 26500.1 |
| pubkey.create | 16.2 | 10.7 | 23.0 | 439.0 | 74.1 | - |
| pubkey.compress | 0.02 | 0.12 | 0.11 | 18.0 | 0.61 | - |
| seckey.verify | 0.04 | 0.04 | 0.01 | 0.05 | 0.03 | - |
| ecdh.xOnly | 31.6 | 28.9 | 60.4 | 583.8 | 210.5 | - |
| tweak.mul | 32.0 | 30.8 | 65.4 | 595.1 | 117.8 | - |
| tweak.add | 0.13 | 0.12 | 0.04 | 2.36 | - | - |
| nostr.verify | 37.0 | 30.1 | 61.2 | 1213.2 | 142.8 | 12799.9 |
| nostr.verifyFast | 37.0 | 29.7 **wrong** KI-4 | 62.1 **wrong** KI-4 | 1223.7 | 147.3 | 13338.5 |
| nostr.verifyBatchByAuthor | - | 16.7 | 38.9 | - | - | - |
| nostr.ecdh | 32.6 | 29.2 | 61.2 | 592.3 | 205.3 | - |

2026-11-10, Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 + libschnorr256k1 0942a32 (JitPack 4478ec1), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-29-ge49e229, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness 19c210c

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 207.7 | 1349.5 | 2352.7 |
| verify.manyKeys | 215.1 | 1298.4 | 2360.9 |
| verifyFast.sameKey | 214.0 | 1349.6 | 2376.2 |
| sign | 204.6 | 2134.6 | 2904.8 |
| sign.knownPub | 210.9 | 2167.5 | 2938.0 |
| pubkey.create | 102.0 | 361.0 | 239.4 |
| pubkey.compress | 0.43 | 4.73 | 6.23 |
| seckey.verify | 0.01 | 0.44 | 1.60 |
| ecdh.xOnly | 183.7 | 2323.7 | 2794.7 |
| tweak.mul | 195.3 | 2339.0 | 2733.7 |
| tweak.add | 0.16 | 1.61 | - |
| nostr.verify | 213.3 | 1364.6 | 2471.7 |
| nostr.verifyFast | 213.1 | 1397.6 | 2387.9 |
| nostr.ecdh | 196.6 | 2347.9 | 2899.8 |

2026-11-10, Linux 6.18.44-fc-v338, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 19c210c

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 237.1 | 430.0 | 768.0 |
| verify.manyKeys | 247.3 | 443.7 | 796.5 |
| verifyFast.sameKey | 214.1 | 423.4 | 628.7 |
| sign | 191.7 | 833.9 | 997.0 |
| sign.knownPub | 202.4 | 784.7 | 1041.8 |
| pubkey.create | 94.0 | 146.4 | 102.7 |
| pubkey.compress | 0.46 | 1.93 | 5.95 |
| seckey.verify | 0.00 | 0.24 | 1.93 |
| ecdh.xOnly | 195.6 | 713.3 | 869.2 |
| tweak.mul | 190.4 | 731.7 | 913.9 |
| tweak.add | 0.15 | 0.78 | - |
| nostr.verify | 219.7 | 435.2 | 774.9 |
| nostr.verifyFast | 219.2 | 445.2 | 814.7 |
| nostr.ecdh | 188.4 | 725.7 | 900.6 |

2026-11-10, Linux 6.18.44-fc-v338, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 19c210c

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.3 | 82938.5 | 998.3 | 2112.3 | 8689.7 |
| verify.manyKeys | 35.3 | 89214.9 | 964.7 | 2220.1 | 9168.4 |
| verifyFast.sameKey | 34.0 | 82576.2 | 1026.5 | 2025.7 | 8684.2 |
| verifyBatch200.perSig | - | - | 719.9 | - | - |
| sign | 101.8 | 169947.8 | 1549.5 | 4609.9 | 13741.2 |
| sign.knownPub | 102.9 | 168252.7 | 1473.6 | 4709.9 | 13467.8 |
| pubkey.create | 18.2 | 40365.9 | 206.6 | 1543.6 | 2266.0 |
| pubkey.compress | 1.39 | - | 4.73 | 6.41 | 7.29 |
| seckey.verify | 0.31 | - | 0.65 | 0.43 | 0.55 |
| ecdh.xOnly | 33.3 | 44166.9 | 917.6 | - | 6822.9 |
| tweak.mul | 34.6 | - | 920.3 | - | 6919.5 |
| tweak.add | 71.5 | - | 1.49 | 1.43 | 1.47 |
| nostr.verify | 37.2 | 79605.9 | 971.7 | 2214.3 | 8977.0 |
| nostr.verifyFast | 36.9 | 88065.0 | 1037.2 | 2307.1 | 8606.1 |
| nostr.verifyBatchByAuthor | - | - | 907.7 | - | - |
| nostr.ecdh | 35.3 | 42477.1 | 925.0 | - | 6573.0 |

2026-11-10, Linux 6.18.44-fc-v338, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness 19c210c
