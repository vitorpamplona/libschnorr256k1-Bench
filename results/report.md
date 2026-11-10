# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per caller on each device, from its newest run: median µs per operation.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.10GHz; machine: shared. Median µs per operation.

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 34.1 | 26.0 |
| verify.manyKeys | 37.2 | 35.2 |
| verifyFast.sameKey | 32.5 | 24.5 **wrong** KI-4 |
| verifyBatch200.perSig | - | 9.88 |
| sign | 31.7 | 20.5 |
| sign.knownPub | 32.1 | 9.57 |
| pubkey.create | 15.3 | 10.1 |
| pubkey.compress | 0.13 | 0.06 |
| seckey.verify | 0.01 | 0.01 |
| ecdh.xOnly | 31.1 | 30.2 |
| tweak.mul | 31.6 | 31.4 |
| tweak.add | 0.02 | 0.03 |
| nostr.verify | 34.0 | 29.5 |
| nostr.verifyFast | 34.4 | 28.8 **wrong** KI-4 |
| nostr.verifyBatchByAuthor | - | 17.2 |
| nostr.ecdh | 31.7 | 30.1 |

2026-11-10, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d; harness e2ec416

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 32.2 | 60.8 | 85.3 |
| verify.manyKeys | 36.5 | 63.6 | 77.5 |
| verifyFast.sameKey | 33.8 | 57.7 | 78.4 |
| sign | 81.7 | 46.4 | 98.1 |
| sign.knownPub | 82.7 | 48.3 | 98.5 |
| pubkey.create | 40.3 | 23.3 | 49.1 |
| pubkey.compress | 0.11 | 0.18 | 6.59 |
| seckey.verify | 0.01 | 0.02 | 0.01 |
| ecdh.xOnly | 32.0 | 52.6 | 55.1 |
| tweak.mul | 32.1 | 53.1 | 55.7 |
| tweak.add | 0.05 | 0.06 | 0.05 |
| nostr.verify | 35.9 | 62.7 | 79.3 |
| nostr.verifyFast | 36.3 | 62.0 | 61.9 |
| nostr.ecdh | 29.6 | 51.9 | 46.0 |

2026-11-10, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness e2ec416

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.8 | 186.1 | 100.1 | 71.4 | 42.4 |
| verify.manyKeys | 37.0 | 159.4 | 119.0 | 84.3 | 46.6 |
| verifyFast.sameKey | 35.3 | 184.4 | 101.9 | 80.8 | 42.5 |
| verifyBatch200.perSig | - | - | 151.0 | - | - |
| sign | 32.9 | 77.0 | 129.7 | 89.2 | 46.6 |
| sign.knownPub | 33.0 | 76.6 | 126.9 | 92.0 | 46.7 |
| pubkey.create | 16.4 | 35.2 | 49.1 | 29.3 | 23.2 |
| pubkey.compress | 0.19 | 0.30 | 0.34 | 6.77 | 0.12 |
| seckey.verify | 0.04 | 0.02 | 0.01 | 0.06 | 0.01 |
| ecdh.xOnly | 34.0 | 150.1 | 174.3 | 74.9 | 48.4 |
| tweak.mul | 33.9 | 153.3 | 208.9 | 75.8 | 40.3 |
| tweak.add | 0.06 | 0.06 | 0.03 | 0.15 | 0.03 |
| nostr.verify | 37.8 | 193.1 | 116.0 | 84.5 | 46.3 |
| nostr.verifyFast | 38.2 | 189.4 | 114.4 | 84.9 | 45.4 |
| nostr.verifyBatchByAuthor | - | - | 156.1 | - | - |
| nostr.ecdh | 32.8 | 149.3 | 169.3 | 78.5 | 50.4 |

2026-11-10, Linux 6.18.44-fc-v338, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness e2ec416

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | libschnorr256k1 (cinterop: schnorr256k1-kmp 1.1.0) | libschnorr256k1-kotlin (pure Kotlin) |
|---|---:|---:|---:|
| verify.sameKey | 33.6 | 26.6 | 33.4 |
| verify.manyKeys | 34.8 | 36.2 | 47.4 |
| verifyFast.sameKey | 34.3 | 23.1 **wrong** KI-4 | 32.4 **wrong** KI-4 |
| verifyBatch200.perSig | - | 10.8 | 13.8 |
| sign | 32.7 | 21.3 | 25.8 |
| sign.knownPub | 32.2 | 10.8 | 14.1 |
| pubkey.create | 16.1 | 10.3 | 9.62 |
| pubkey.compress | 0.05 | 0.17 | 0.28 |
| seckey.verify | 0.09 | 0.04 | 0.02 |
| ecdh.xOnly | 32.6 | 32.0 | 38.3 |
| tweak.mul | 32.0 | 31.7 | 39.8 |
| tweak.add | 0.21 | 0.15 | 0.09 |
| nostr.verify | 36.6 | 30.4 | 32.3 |
| nostr.verifyFast | 29.8 | 28.6 **wrong** KI-4 | 37.9 **wrong** KI-4 |
| nostr.verifyBatchByAuthor | - | 18.4 | 24.4 |
| nostr.ecdh | 32.3 | 30.5 | 39.0 |

2026-11-10, Linux 6.18.44-fc-v338, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13; harness e2ec416

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.1.0) | libschnorr256k1-kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 31.2 | 25.9 | 50.5 | 1251.8 | 129.1 | 12608.4 |
| verify.manyKeys | 36.7 | 36.7 | 79.7 | 1230.8 | 138.4 | 13058.4 |
| verifyFast.sameKey | 33.9 | 25.8 **wrong** KI-4 | 48.7 **wrong** KI-4 | 1237.7 | 130.0 | 12716.8 |
| verifyBatch200.perSig | - | 10.9 | 24.0 | - | - | - |
| sign | 32.3 | 21.1 | 47.5 | 7008.4 | 147.7 | 26901.4 |
| sign.knownPub | 32.3 | 10.5 | 24.2 | 7027.5 | 91.4 | 26092.3 |
| pubkey.create | 15.8 | 10.2 | 23.3 | 475.0 | 68.2 | - |
| pubkey.compress | 0.02 | 0.15 | 0.15 | 17.6 | 0.59 | - |
| seckey.verify | 0.05 | 0.04 | 0.01 | 0.05 | 0.03 | - |
| ecdh.xOnly | 31.2 | 32.2 | 65.1 | 584.1 | 216.4 | - |
| tweak.mul | 30.8 | 32.1 | 65.8 | 576.5 | 118.3 | - |
| tweak.add | 0.12 | 0.12 | 0.04 | 2.54 | - | - |
| nostr.verify | 36.2 | 30.3 | 63.2 | 1242.8 | 145.8 | 12868.3 |
| nostr.verifyFast | 36.8 | 29.0 **wrong** KI-4 | 60.2 **wrong** KI-4 | 1229.8 | 144.8 | 13221.6 |
| nostr.verifyBatchByAuthor | - | 17.3 | 39.6 | - | - | - |
| nostr.ecdh | 31.7 | 32.2 | 63.0 | 588.8 | 208.0 | - |

2026-11-10, Linux 6.18.44-fc-v338, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), libschnorr256k1-kotlin schnorr256k1-kotlin v1.0.1-24-g867bb13, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness e2ec416

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 209.0 | 1226.5 | 2371.4 |
| verify.manyKeys | 215.4 | 1267.3 | 2377.4 |
| verifyFast.sameKey | 219.4 | 1462.0 | 2423.7 |
| sign | 200.3 | 2519.3 | 2872.2 |
| sign.knownPub | 203.6 | 2375.9 | 2834.4 |
| pubkey.create | 98.0 | 506.0 | 244.2 |
| pubkey.compress | 0.49 | 6.30 | 4.99 |
| seckey.verify | 0.01 | 0.49 | 1.47 |
| ecdh.xOnly | 194.1 | 2526.5 | 2818.0 |
| tweak.mul | 193.7 | 2436.9 | 2879.9 |
| tweak.add | 0.15 | 1.80 | - |
| nostr.verify | 208.8 | 1413.5 | 1880.4 |
| nostr.verifyFast | 213.0 | 1447.8 | 1987.1 |
| nostr.ecdh | 153.0 | 2463.2 | 2919.4 |

2026-11-10, Linux 6.18.44-fc-v338, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness e2ec416

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 175.0 | 415.4 | 884.6 |
| verify.manyKeys | 209.2 | 455.1 | 870.6 |
| verifyFast.sameKey | 213.2 | 406.3 | 831.8 |
| sign | 201.0 | 770.0 | 1119.4 |
| sign.knownPub | 199.8 | 808.3 | 1127.5 |
| pubkey.create | 99.6 | 130.9 | 99.0 |
| pubkey.compress | 0.48 | 2.06 | 6.22 |
| seckey.verify | 0.00 | 0.36 | 1.97 |
| ecdh.xOnly | 194.2 | 679.1 | 940.7 |
| tweak.mul | 195.9 | 707.7 | 991.1 |
| tweak.add | 0.16 | 1.03 | - |
| nostr.verify | 211.3 | 463.4 | 890.1 |
| nostr.verifyFast | 214.7 | 468.9 | 908.7 |
| nostr.ecdh | 190.5 | 757.2 | 960.9 |

2026-11-10, Linux 6.18.44-fc-v338, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness e2ec416

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.3 | 84463.2 | 1027.7 | 2319.6 | 8911.0 |
| verify.manyKeys | 35.4 | 86072.6 | 1129.3 | 2498.8 | 9197.8 |
| verifyFast.sameKey | 33.5 | 84012.3 | 1027.5 | 2386.6 | 9783.9 |
| verifyBatch200.perSig | - | - | 818.3 | - | - |
| sign | 104.2 | 174106.0 | 1554.5 | 4784.7 | 14676.7 |
| sign.knownPub | 110.8 | 175893.8 | 1550.8 | 4754.9 | 13632.8 |
| pubkey.create | 19.5 | 44195.7 | 210.7 | 1515.8 | 2283.7 |
| pubkey.compress | 1.46 | - | 5.42 | 6.49 | 7.60 |
| seckey.verify | 0.30 | - | 0.70 | 0.46 | 0.56 |
| ecdh.xOnly | 35.1 | 42451.9 | 953.1 | - | 6953.8 |
| tweak.mul | 35.7 | - | 942.8 | - | 6326.0 |
| tweak.add | 72.1 | - | 1.43 | 1.44 | 1.67 |
| nostr.verify | 37.0 | 86445.2 | 1078.8 | 2361.8 | 9344.5 |
| nostr.verifyFast | 35.8 | 87676.6 | 1088.1 | 2377.0 | 9446.9 |
| nostr.verifyBatchByAuthor | - | - | 845.5 | - | - |
| nostr.ecdh | 35.5 | 42412.9 | 955.4 | - | 7087.6 |

2026-11-10, Linux 6.18.44-fc-v338, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness e2ec416
