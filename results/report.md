# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per caller on each device, from its newest run: median µs per operation.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.10GHz; machine: shared. Median µs per operation.

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 36.2 | 26.1 |
| verify.manyKeys | 36.8 | 36.1 |
| verifyFast.sameKey | 33.8 | 24.8 |
| verifyBatch200.perSig | - | 10.8 |
| sign | 33.1 | 21.5 |
| sign.knownPub | 34.5 | 10.9 |
| pubkey.create | 15.4 | 10.6 |
| pubkey.compress | 0.13 | 0.06 |
| seckey.verify | 0.01 | 0.01 |
| ecdh.xOnly | 32.4 | 32.3 |
| tweak.mul | 31.6 | 32.2 |
| tweak.add | 0.03 | 0.02 |
| nostr.verify | 38.2 | 31.1 |
| nostr.verifyFast | 38.8 | 30.6 |
| nostr.verifyBatchByAuthor | - | 18.9 |
| nostr.ecdh | 33.6 | 33.0 |

2026-11-09, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2f4a13d; harness bf57b52

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 34.4 | 58.9 | 79.6 |
| verify.manyKeys | 37.6 | 62.2 | 79.3 |
| verifyFast.sameKey | 35.3 | 59.9 | 78.2 |
| sign | 84.6 | 46.8 | 95.7 |
| sign.knownPub | 84.4 | 47.8 | 98.7 |
| pubkey.create | 41.9 | 23.1 | 48.5 |
| pubkey.compress | 0.11 | 0.15 | 6.59 |
| seckey.verify | 0.01 | 0.02 | 0.01 |
| ecdh.xOnly | 29.5 | 52.3 | 56.3 |
| tweak.mul | 31.5 | 52.7 | 55.3 |
| tweak.add | 0.05 | 0.05 | 0.05 |
| nostr.verify | 38.1 | 62.3 | 80.0 |
| nostr.verifyFast | 37.0 | 59.4 | 77.7 |
| nostr.ecdh | 33.2 | 53.1 | 55.5 |

2026-11-09, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness bf57b52

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 35.9 | 189.3 | 102.1 | 83.4 | 42.2 |
| verify.manyKeys | 39.9 | 195.0 | 113.0 | 85.0 | 45.7 |
| verifyFast.sameKey | 35.3 | 186.1 | 100.5 | 85.9 | 43.1 |
| verifyBatch200.perSig | - | - | 146.1 | - | - |
| sign | 34.3 | 78.4 | 123.5 | 92.2 | 47.3 |
| sign.knownPub | 33.3 | 83.6 | 125.7 | 89.6 | 50.3 |
| pubkey.create | 16.3 | 39.9 | 50.2 | 28.8 | 24.2 |
| pubkey.compress | 0.20 | 0.32 | 0.33 | 6.72 | 0.12 |
| seckey.verify | 0.04 | 0.02 | 0.01 | 0.06 | 0.01 |
| ecdh.xOnly | 33.0 | 150.8 | 172.2 | 76.3 | 49.3 |
| tweak.mul | 33.0 | 149.6 | 203.5 | 76.7 | 39.4 |
| tweak.add | 0.06 | 0.06 | 0.03 | 0.15 | 0.04 |
| nostr.verify | 38.2 | 191.5 | 113.0 | 84.0 | 46.2 |
| nostr.verifyFast | 38.4 | 193.6 | 115.3 | 85.4 | 46.0 |
| nostr.verifyBatchByAuthor | - | - | 153.9 | - | - |
| nostr.ecdh | 32.8 | 150.7 | 172.0 | 76.4 | 49.4 |

2026-11-09, Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness bf57b52

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | kotlin (pure Kotlin) |
|---|---:|---:|
| verify.sameKey | 35.9 | 31.1 |
| verify.manyKeys | 37.7 | 65.0 |
| verifyFast.sameKey | 33.7 | 29.6 |
| verifyBatch200.perSig | - | 21.0 |
| sign | 33.9 | 37.0 |
| sign.knownPub | 32.9 | 20.2 |
| pubkey.create | 16.6 | 16.2 |
| pubkey.compress | 0.09 | 0.45 |
| seckey.verify | 0.12 | 0.02 |
| ecdh.xOnly | 32.3 | 53.7 |
| tweak.mul | 33.2 | 55.6 |
| tweak.add | 0.32 | 0.11 |
| nostr.verify | 38.1 | 53.8 |
| nostr.verifyFast | 37.2 | 52.0 |
| nostr.verifyBatchByAuthor | - | 34.2 |
| nostr.ecdh | 32.3 | 54.6 |

2026-11-09, Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415; harness bf57b52

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.1.0) | kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 33.1 | 25.7 | 51.1 | 1273.1 | 130.6 | 13102.1 |
| verify.manyKeys | 36.0 | 37.1 | 92.9 | 1340.7 | 143.9 | 13235.4 |
| verifyFast.sameKey | 33.5 | 25.7 | 48.5 | 1274.6 | 133.4 | 12974.1 |
| verifyBatch200.perSig | - | 10.9 | 30.8 | - | - | - |
| sign | 32.9 | 21.1 | 55.7 | 7248.0 | 162.5 | 26677.3 |
| sign.knownPub | 33.1 | 11.2 | 28.4 | 7174.7 | 93.3 | 26620.8 |
| pubkey.create | 15.8 | 10.3 | 27.1 | 465.7 | 72.8 | - |
| pubkey.compress | 0.02 | 0.14 | 0.17 | 18.0 | 0.61 | - |
| seckey.verify | 0.04 | 0.04 | 0.01 | 0.05 | 0.03 | - |
| ecdh.xOnly | 32.9 | 32.1 | 78.6 | 655.1 | 204.2 | - |
| tweak.mul | 32.0 | 31.9 | 78.9 | 609.5 | 118.5 | - |
| tweak.add | 0.13 | 0.13 | 0.04 | 2.52 | - | - |
| nostr.verify | 37.9 | 31.0 | 76.2 | 1265.3 | 144.3 | 13167.7 |
| nostr.verifyFast | 36.6 | 29.4 | 74.0 | 1253.1 | 144.3 | 13296.0 |
| nostr.verifyBatchByAuthor | - | 17.8 | 50.7 | - | - | - |
| nostr.ecdh | 32.4 | 33.6 | 79.8 | 622.3 | 203.5 | - |

2026-11-09, Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.1.0 (JitPack f19cabc), kotlin schnorr256k1-kotlin v1.0.1-9-g4c74415, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness 6bffc47

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 198.2 | 1308.9 | 2437.3 |
| verify.manyKeys | 217.0 | 1375.6 | 2452.0 |
| verifyFast.sameKey | 213.4 | 1314.5 | 2484.1 |
| sign | 204.4 | 2112.5 | 2913.4 |
| sign.knownPub | 209.1 | 2162.1 | 2938.6 |
| pubkey.create | 99.6 | 388.7 | 250.2 |
| pubkey.compress | 0.50 | 4.86 | 5.96 |
| seckey.verify | 0.01 | 0.49 | 1.52 |
| ecdh.xOnly | 205.0 | 2367.0 | 2899.5 |
| tweak.mul | 196.0 | 2488.2 | 2972.4 |
| tweak.add | 0.16 | 1.86 | - |
| nostr.verify | 220.3 | 1391.5 | 2386.1 |
| nostr.verifyFast | 221.1 | 1354.2 | 2357.4 |
| nostr.ecdh | 189.4 | 2391.0 | 2891.7 |

2026-11-09, Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness bf57b52

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 212.0 | 449.5 | 954.5 |
| verify.manyKeys | 224.3 | 483.1 | 1000.7 |
| verifyFast.sameKey | 208.2 | 466.1 | 922.0 |
| sign | 195.1 | 843.7 | 1273.4 |
| sign.knownPub | 198.6 | 892.0 | 1270.0 |
| pubkey.create | 97.8 | 156.5 | 123.7 |
| pubkey.compress | 0.47 | 2.31 | 6.70 |
| seckey.verify | 0.00 | 0.40 | 2.05 |
| ecdh.xOnly | 189.5 | 792.5 | 1093.6 |
| tweak.mul | 192.0 | 804.9 | 1102.9 |
| tweak.add | 0.16 | 1.24 | - |
| nostr.verify | 219.0 | 507.6 | 972.2 |
| nostr.verifyFast | 219.5 | 484.4 | 996.3 |
| nostr.ecdh | 195.2 | 773.4 | 1107.2 |

2026-11-09, Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness bf57b52

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 34.0 | 86412.6 | 1069.0 | 2378.2 | 9130.6 |
| verify.manyKeys | 36.9 | 90700.2 | 1091.0 | 2411.1 | 9285.3 |
| verifyFast.sameKey | 34.2 | 89153.3 | 1044.8 | 2347.3 | 9215.8 |
| verifyBatch200.perSig | - | - | 817.5 | - | - |
| sign | 104.6 | 177101.4 | 1557.0 | 4789.2 | 13953.7 |
| sign.knownPub | 106.0 | 172260.3 | 1524.7 | 4710.2 | 13802.8 |
| pubkey.create | 18.7 | 43286.0 | 221.8 | 1553.9 | 2300.2 |
| pubkey.compress | 1.51 | - | 5.24 | 6.49 | 7.45 |
| seckey.verify | 0.31 | - | 0.71 | 0.42 | 0.54 |
| ecdh.xOnly | 35.5 | 42572.0 | 938.0 | - | 7042.6 |
| tweak.mul | 35.1 | - | 943.3 | - | 7036.6 |
| tweak.add | 73.3 | - | 1.47 | 1.44 | 1.64 |
| nostr.verify | 37.4 | 88496.2 | 1055.3 | 2406.1 | 9146.8 |
| nostr.verifyFast | 38.0 | 84412.2 | 1062.9 | 2439.6 | 9545.3 |
| nostr.verifyBatchByAuthor | - | - | 1001.4 | - | - |
| nostr.ecdh | 35.0 | 45191.6 | 931.1 | - | 7163.0 |

2026-11-09, Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness bf57b52
