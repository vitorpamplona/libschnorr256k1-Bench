# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per caller on each device, from its newest run: median µs per operation.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.30GHz; machine: shared. Median µs per operation.

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 28.6 | 27.5 |
| verify.manyKeys | 29.9 | 53.2 |
| verifyFast.sameKey | 30.1 | 24.3 |
| verifyBatch200.perSig | - | 3.63 KI-1 |
| sign | 27.3 | 29.0 |
| sign.knownPub | 26.6 | 14.7 **wrong** KI-2 |
| pubkey.create | 12.9 | 14.3 |
| pubkey.compress | 0.10 | 0.00 |
| seckey.verify | 0.01 | 0.01 |
| ecdh.xOnly | 26.9 | 46.9 |
| tweak.mul | 26.8 | 48.0 |
| tweak.add | 0.02 | 0.02 |
| nostr.verify | 30.1 | 43.7 |
| nostr.verifyFast | 30.1 | 39.5 |
| nostr.verifyBatchByAuthor | - | 15.9 KI-1 |
| nostr.ecdh | 26.7 | 47.4 |

2026-11-09, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2a8798e; harness 01e496d

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 28.1 | 49.3 | 65.3 |
| verify.manyKeys | 29.8 | 52.0 | 65.1 |
| verifyFast.sameKey | 28.2 | 49.0 | 65.0 |
| sign | 68.6 | 38.7 | 81.9 |
| sign.knownPub | 69.3 | 39.5 | 82.1 |
| pubkey.create | 34.0 | 19.1 | 41.4 |
| pubkey.compress | 0.09 | 0.13 | 5.51 |
| seckey.verify | 0.01 | 0.02 | 0.01 |
| ecdh.xOnly | 26.6 | 43.6 | 46.7 |
| tweak.mul | 26.2 | 43.8 | 46.7 |
| tweak.add | 0.04 | 0.05 | 0.04 |
| nostr.verify | 30.5 | 51.9 | 65.3 |
| nostr.verifyFast | 30.3 | 52.2 | 66.1 |
| nostr.ecdh | 26.0 | 44.2 | 46.3 |

2026-11-09, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness 01e496d

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 31.6 | 158.3 | 84.5 | 70.6 | 35.3 |
| verify.manyKeys | 31.1 | 158.9 | 95.9 | 73.0 | 38.3 |
| verifyFast.sameKey | 30.8 | 154.5 | 84.0 | 68.8 | 35.6 |
| verifyBatch200.perSig | - | - | 124.5 | - | - |
| sign | 27.0 | 66.1 | 105.1 | 74.1 | 39.1 |
| sign.knownPub | 29.7 | 63.3 | 103.3 | 71.4 | 39.3 |
| pubkey.create | 13.7 | 31.4 | 40.7 | 24.4 | 19.1 |
| pubkey.compress | 0.16 | 0.24 | 0.27 | 6.19 | 0.10 |
| seckey.verify | 0.03 | 0.01 | 0.01 | 0.05 | 0.01 |
| ecdh.xOnly | 27.9 | 124.9 | 144.4 | 67.1 | 40.7 |
| tweak.mul | 26.6 | 121.4 | 171.4 | 63.6 | 33.1 |
| tweak.add | 0.05 | 0.05 | 0.03 | 0.13 | 0.03 |
| nostr.verify | 32.6 | 158.4 | 95.6 | 71.2 | 37.8 |
| nostr.verifyFast | 31.6 | 158.5 | 94.6 | 70.7 | 38.6 |
| nostr.verifyBatchByAuthor | - | - | 125.5 | - | - |
| nostr.ecdh | 28.4 | 125.3 | 148.1 | 63.7 | 40.2 |

2026-11-09, Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness 01e496d

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | kotlin (pure Kotlin) |
|---|---:|---:|
| verify.sameKey | 28.2 | 25.7 |
| verify.manyKeys | 30.7 | 52.3 |
| verifyFast.sameKey | 28.3 | 24.4 |
| verifyBatch200.perSig | - | 16.6 |
| sign | 27.4 | 29.3 |
| sign.knownPub | 27.3 | 16.1 |
| pubkey.create | 13.3 | 13.7 |
| pubkey.compress | 0.04 | 0.25 |
| seckey.verify | 0.08 | 0.01 |
| ecdh.xOnly | 27.6 | 44.5 |
| tweak.mul | 27.8 | 44.7 |
| tweak.add | 0.19 | 0.07 |
| nostr.verify | 31.5 | 44.0 |
| nostr.verifyFast | 31.0 | 41.8 |
| nostr.verifyBatchByAuthor | - | 28.1 |
| nostr.ecdh | 27.0 | 44.5 |

2026-11-09, Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1; harness 01e496d

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.0.5) | kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 27.9 | 27.8 | 45.6 | 1036.4 | 110.7 | 10791.6 |
| verify.manyKeys | 30.3 | 55.3 | 77.6 | 1048.7 | 119.9 | 11041.7 |
| verifyFast.sameKey | 28.8 | 25.3 | 41.8 | 1022.8 | 109.4 | 10887.5 |
| verifyBatch200.perSig | - | 3.77 KI-1 | 25.7 | - | - | - |
| sign | 27.0 | 30.5 | 47.4 | 6060.4 | 123.4 | 21923.9 |
| sign.knownPub | 27.2 | 16.0 **wrong** KI-2 | 24.2 | 5960.1 | 76.0 | 22266.8 |
| pubkey.create | 13.2 | 15.0 | 22.9 | 387.4 | 60.3 | - |
| pubkey.compress | 0.01 | 0.07 | 0.14 | 14.8 | 0.51 | - |
| seckey.verify | 0.04 | 0.04 | 0.01 | 0.04 | 0.03 | - |
| ecdh.xOnly | 26.7 | 48.0 | 65.5 | 494.8 | 181.0 | - |
| tweak.mul | 26.8 | 48.2 | 67.5 | 506.9 | 100.0 | - |
| tweak.add | 0.11 | 0.11 | 0.04 | 2.16 | - | - |
| nostr.verify | 30.5 | 44.6 | 66.0 | 1044.2 | 124.1 | 11025.0 |
| nostr.verifyFast | 30.0 | 39.8 | 61.3 | 1053.5 | 120.0 | 11173.4 |
| nostr.verifyBatchByAuthor | - | 17.6 KI-1 | 42.2 | - | - | - |
| nostr.ecdh | 27.9 | 47.9 | 65.6 | 543.0 | 187.5 | - |

2026-11-09, Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.0.5, kotlin schnorr256k1-kotlin v1.0.1, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness 01e496d

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 176.5 | 1127.4 | 1884.8 |
| verify.manyKeys | 183.7 | 1136.1 | 1884.8 |
| verifyFast.sameKey | 176.3 | 1110.8 | 1864.9 |
| sign | 170.9 | 1839.7 | 2285.4 |
| sign.knownPub | 171.2 | 1754.4 | 2376.2 |
| pubkey.create | 85.3 | 311.7 | 196.9 |
| pubkey.compress | 0.42 | 3.95 | 5.07 |
| seckey.verify | 0.01 | 0.38 | 1.20 |
| ecdh.xOnly | 161.5 | 1990.8 | 2259.4 |
| tweak.mul | 163.3 | 1982.8 | 2306.3 |
| tweak.add | 0.13 | 1.41 | - |
| nostr.verify | 195.0 | 1110.6 | 1902.1 |
| nostr.verifyFast | 184.5 | 1096.4 | 1907.8 |
| nostr.ecdh | 162.3 | 1961.1 | 2256.6 |

2026-11-09, Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 01e496d

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 174.4 | 336.8 | 670.7 |
| verify.manyKeys | 178.6 | 353.1 | 666.4 |
| verifyFast.sameKey | 174.2 | 344.2 | 657.0 |
| sign | 166.0 | 612.3 | 904.7 |
| sign.knownPub | 163.3 | 615.7 | 817.9 |
| pubkey.create | 79.4 | 109.0 | 84.7 |
| pubkey.compress | 0.37 | 1.55 | 4.72 |
| seckey.verify | 0.00 | 0.25 | 1.44 |
| ecdh.xOnly | 156.1 | 574.9 | 739.9 |
| tweak.mul | 156.0 | 545.8 | 757.5 |
| tweak.add | 0.12 | 0.66 | - |
| nostr.verify | 177.7 | 367.9 | 660.9 |
| nostr.verifyFast | 180.1 | 372.4 | 660.8 |
| nostr.ecdh | 158.0 | 543.2 | 732.1 |

2026-11-09, Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 01e496d

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 28.5 | 70871.0 | 859.1 | 1903.4 | 7767.2 |
| verify.manyKeys | 30.9 | 72176.0 | 880.5 | 1971.5 | 7760.6 |
| verifyFast.sameKey | 28.1 | 70295.1 | 848.7 | 1891.2 | 7755.0 |
| verifyBatch200.perSig | - | - | 708.2 | - | - |
| sign | 86.6 | 141590.6 | 1283.9 | 4018.6 | 11466.6 |
| sign.knownPub | 85.6 | 142976.3 | 1315.5 | 4101.1 | 11479.6 |
| pubkey.create | 15.3 | 36083.6 | 184.7 | 1304.9 | 1969.7 |
| pubkey.compress | 1.21 | - | 4.32 | 5.49 | 6.34 |
| seckey.verify | 0.26 | - | 0.58 | 0.38 | 0.47 |
| ecdh.xOnly | 28.3 | 36309.1 | 771.7 | - | 5843.8 |
| tweak.mul | 28.4 | - | 795.3 | - | 5845.6 |
| tweak.add | 61.2 | - | 1.24 | 1.25 | 1.44 |
| nostr.verify | 30.4 | 72931.0 | 916.9 | 2081.7 | 7728.7 |
| nostr.verifyFast | 31.6 | 73477.2 | 892.0 | 1996.2 | 7787.2 |
| nostr.verifyBatchByAuthor | - | - | 890.1 | - | - |
| nostr.ecdh | 30.0 | 36089.7 | 770.5 | - | 5852.8 |

2026-11-09, Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness 01e496d
