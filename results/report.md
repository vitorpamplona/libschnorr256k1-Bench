# Results

Written by `scripts/table.py --readme` from the dedicated runs under `results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per device: each layer's newest run, median µs per operation and its ratio to libsecp256k1 in the same layer.

### Cloud VM (4 vCPU)

Intel(R) Xeon(R) Processor @ 2.30GHz; machine: shared. Median µs per operation (ratio to libsecp256k1 from the same caller).

#### C

| op | libsecp256k1 (direct) | libschnorr256k1 (direct) |
|---|---:|---:|
| verify.sameKey | 28.6 | 27.5 (0.96x) |
| verify.manyKeys | 29.9 | 53.2 (1.78x) |
| verifyFast.sameKey | 30.1 | 24.3 (0.81x) |
| verifyBatch200.perSig | - | 3.63 KI-1 |
| sign | 27.3 | 29.0 (1.06x) |
| sign.knownPub | 26.6 | 14.7 (0.55x) **wrong** KI-2 |
| pubkey.create | 12.9 | 14.3 (1.11x) |
| pubkey.compress | 0.10 | 0.00 (0.02x) |
| seckey.verify | 0.01 | 0.01 (0.82x) |
| ecdh.xOnly | 26.9 | 46.9 (1.75x) |
| tweak.mul | 26.8 | 48.0 (1.79x) |
| tweak.add | 0.02 | 0.02 (0.96x) |
| nostr.verify | 30.1 | 43.7 (1.45x) |
| nostr.verifyFast | 30.1 | 39.5 (1.31x) |
| nostr.verifyBatchByAuthor | - | 15.9 KI-1 |
| nostr.ecdh | 26.7 | 47.4 (1.77x) |

2026-11-09, Linux, C, 13.3.0, each library with its own CMake flags, x86_64, Release; libsecp256k1 6e2c8bc, libschnorr256k1 2a8798e; harness 01e496d

#### Rust

| op | libsecp256k1 (FFI: secp256k1 0.33.1) | k256 (pure Rust) | secp256kfun (pure Rust) |
|---|---:|---:|---:|
| verify.sameKey | 28.1 | 49.3 (1.75x) | 65.3 (2.32x) |
| verify.manyKeys | 29.8 | 52.0 (1.75x) | 65.1 (2.19x) |
| verifyFast.sameKey | 28.2 | 49.0 (1.74x) | 65.0 (2.30x) |
| sign | 68.6 | 38.7 (0.56x) | 81.9 (1.19x) |
| sign.knownPub | 69.3 | 39.5 (0.57x) | 82.1 (1.18x) |
| pubkey.create | 34.0 | 19.1 (0.56x) | 41.4 (1.22x) |
| pubkey.compress | 0.09 | 0.13 (1.42x) | 5.51 (61.13x) |
| seckey.verify | 0.01 | 0.02 (2.56x) | 0.01 (1.76x) |
| ecdh.xOnly | 26.6 | 43.6 (1.64x) | 46.7 (1.76x) |
| tweak.mul | 26.2 | 43.8 (1.67x) | 46.7 (1.78x) |
| tweak.add | 0.04 | 0.05 (1.16x) | 0.04 (1.12x) |
| nostr.verify | 30.5 | 51.9 (1.70x) | 65.3 (2.14x) |
| nostr.verifyFast | 30.3 | 52.2 (1.72x) | 66.1 (2.18x) |
| nostr.ecdh | 26.0 | 44.2 (1.70x) | 46.3 (1.78x) |

2026-11-09, Linux, Rust, rustc 1.97.0 (2d8144b78 2026-07-07), x86_64, release profile, opt-level 3; libsecp256k1 secp256k1 0.33.1 (secp256k1-sys 0.14.1, libsecp256k1 6e2c8bc), k256 k256 0.14.0, secp256kfun secp256kfun 0.12.1 + schnorr_fun 0.13.0; harness 01e496d

#### Go

| op | libsecp256k1 (cgo (the bench's own binding)) | btcec (pure Go) | p256k1 (pure Go) | secp256k1-voi (pure Go) | splitsecure (pure Go) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 31.6 | 158.3 (5.02x) | 84.5 (2.68x) | 70.6 (2.24x) | 35.3 (1.12x) |
| verify.manyKeys | 31.1 | 158.9 (5.10x) | 95.9 (3.08x) | 73.0 (2.34x) | 38.3 (1.23x) |
| verifyFast.sameKey | 30.8 | 154.5 (5.02x) | 84.0 (2.73x) | 68.8 (2.23x) | 35.6 (1.16x) |
| verifyBatch200.perSig | - | - | 124.5 | - | - |
| sign | 27.0 | 66.1 (2.45x) | 105.1 (3.90x) | 74.1 (2.75x) | 39.1 (1.45x) |
| sign.knownPub | 29.7 | 63.3 (2.13x) | 103.3 (3.48x) | 71.4 (2.41x) | 39.3 (1.32x) |
| pubkey.create | 13.7 | 31.4 (2.29x) | 40.7 (2.97x) | 24.4 (1.78x) | 19.1 (1.39x) |
| pubkey.compress | 0.16 | 0.24 (1.53x) | 0.27 (1.70x) | 6.19 (38.74x) | 0.10 (0.60x) |
| seckey.verify | 0.03 | 0.01 (0.45x) | 0.01 (0.18x) | 0.05 (1.58x) | 0.01 (0.27x) |
| ecdh.xOnly | 27.9 | 124.9 (4.47x) | 144.4 (5.17x) | 67.1 (2.40x) | 40.7 (1.46x) |
| tweak.mul | 26.6 | 121.4 (4.57x) | 171.4 (6.45x) | 63.6 (2.40x) | 33.1 (1.25x) |
| tweak.add | 0.05 | 0.05 (1.01x) | 0.03 (0.56x) | 0.13 (2.67x) | 0.03 (0.59x) |
| nostr.verify | 32.6 | 158.4 (4.86x) | 95.6 (2.93x) | 71.2 (2.19x) | 37.8 (1.16x) |
| nostr.verifyFast | 31.6 | 158.5 (5.02x) | 94.6 (3.00x) | 70.7 (2.24x) | 38.6 (1.22x) |
| nostr.verifyBatchByAuthor | - | - | 125.5 | - | - |
| nostr.ecdh | 28.4 | 125.3 (4.42x) | 148.1 (5.22x) | 63.7 (2.25x) | 40.2 (1.42x) |

2026-11-09, Linux 6.18.44-fc-v310, Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2; libsecp256k1 bitcoin-core/secp256k1 6e2c8bc, btcec github.com/btcsuite/btcd/btcec/v2 v2.5.1, p256k1 p256k1.mleku.dev v1.1.7 (git.mleku.dev/mleku/p256k1), secp256k1-voi gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b, splitsecure github.com/splitsecure/secp256k1-go v0.1.0; harness 01e496d

#### Kotlin/Native

| op | libsecp256k1 (cinterop: secp256k1-kmp 0.24.0) | kotlin (pure Kotlin) |
|---|---:|---:|
| verify.sameKey | 28.2 | 25.7 (0.91x) |
| verify.manyKeys | 30.7 | 52.3 (1.70x) |
| verifyFast.sameKey | 28.3 | 24.4 (0.86x) |
| verifyBatch200.perSig | - | 16.6 |
| sign | 27.4 | 29.3 (1.07x) |
| sign.knownPub | 27.3 | 16.1 (0.59x) |
| pubkey.create | 13.3 | 13.7 (1.03x) |
| pubkey.compress | 0.04 | 0.25 (5.76x) |
| seckey.verify | 0.08 | 0.01 (0.17x) |
| ecdh.xOnly | 27.6 | 44.5 (1.61x) |
| tweak.mul | 27.8 | 44.7 (1.61x) |
| tweak.add | 0.19 | 0.07 (0.39x) |
| nostr.verify | 31.5 | 44.0 (1.40x) |
| nostr.verifyFast | 31.0 | 41.8 (1.35x) |
| nostr.verifyBatchByAuthor | - | 28.1 |
| nostr.ecdh | 27.0 | 44.5 (1.65x) |

2026-11-09, Linux 6.18.44-fc-v310, Kotlin/Native 2.4.21; libsecp256k1 secp256k1-kmp 0.24.0, kotlin schnorr256k1-kotlin v1.0.1; harness 01e496d

#### JVM

| op | libsecp256k1 (JNI: secp256k1-kmp 0.24.0) | libschnorr256k1 (JNI: schnorr256k1-kmp 1.0.5) | kotlin (pure Kotlin) | bitcoin-s (pure Scala) | bouncycastle (pure Java) | nostr-java (pure Java) |
|---|---:|---:|---:|---:|---:|---:|
| verify.sameKey | 27.9 | 27.8 (1.00x) | 45.6 (1.63x) | 1036.4 (37.19x) | 110.7 (3.97x) | 10791.6 (387.23x) |
| verify.manyKeys | 30.3 | 55.3 (1.83x) | 77.6 (2.56x) | 1048.7 (34.64x) | 119.9 (3.96x) | 11041.7 (364.68x) |
| verifyFast.sameKey | 28.8 | 25.3 (0.88x) | 41.8 (1.45x) | 1022.8 (35.56x) | 109.4 (3.80x) | 10887.5 (378.51x) |
| verifyBatch200.perSig | - | 3.77 KI-1 | 25.7 | - | - | - |
| sign | 27.0 | 30.5 (1.13x) | 47.4 (1.76x) | 6060.4 (224.44x) | 123.4 (4.57x) | 21923.9 (811.93x) |
| sign.knownPub | 27.2 | 16.0 (0.59x) **wrong** KI-2 | 24.2 (0.89x) | 5960.1 (219.02x) | 76.0 (2.79x) | 22266.8 (818.24x) |
| pubkey.create | 13.2 | 15.0 (1.14x) | 22.9 (1.73x) | 387.4 (29.35x) | 60.3 (4.57x) | - |
| pubkey.compress | 0.01 | 0.07 (5.08x) | 0.14 (9.78x) | 14.8 (1061.19x) | 0.51 (37.04x) | - |
| seckey.verify | 0.04 | 0.04 (0.98x) | 0.01 (0.23x) | 0.04 (1.23x) | 0.03 (0.73x) | - |
| ecdh.xOnly | 26.7 | 48.0 (1.80x) | 65.5 (2.45x) | 494.8 (18.53x) | 181.0 (6.78x) | - |
| tweak.mul | 26.8 | 48.2 (1.80x) | 67.5 (2.52x) | 506.9 (18.88x) | 100.0 (3.72x) | - |
| tweak.add | 0.11 | 0.11 (0.96x) | 0.04 (0.32x) | 2.16 (19.18x) | - | - |
| nostr.verify | 30.5 | 44.6 (1.46x) | 66.0 (2.17x) | 1044.2 (34.28x) | 124.1 (4.07x) | 11025.0 (361.92x) |
| nostr.verifyFast | 30.0 | 39.8 (1.33x) | 61.3 (2.04x) | 1053.5 (35.08x) | 120.0 (4.00x) | 11173.4 (372.02x) |
| nostr.verifyBatchByAuthor | - | 17.6 KI-1 | 42.2 | - | - | - |
| nostr.ecdh | 27.9 | 47.9 (1.72x) | 65.6 (2.35x) | 543.0 (19.48x) | 187.5 (6.72x) | - |

2026-11-09, Linux 6.18.44-fc-v310, Ubuntu OpenJDK 64-Bit Server VM 21.0.12.1+1-1-24.04.4-Ubuntu; libsecp256k1 secp256k1-kmp 0.24.0, libschnorr256k1 schnorr256k1-kmp 1.0.5, kotlin schnorr256k1-kotlin v1.0.1, bouncycastle bcprov-jdk18on 1.86, bitcoin-s bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86), nostr-java nostr-java-core 2.4.2; harness 01e496d

#### Node.js

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 176.5 | 1127.4 (6.39x) | 1884.8 (10.68x) |
| verify.manyKeys | 183.7 | 1136.1 (6.18x) | 1884.8 (10.26x) |
| verifyFast.sameKey | 176.3 | 1110.8 (6.30x) | 1864.9 (10.58x) |
| sign | 170.9 | 1839.7 (10.77x) | 2285.4 (13.37x) |
| sign.knownPub | 171.2 | 1754.4 (10.25x) | 2376.2 (13.88x) |
| pubkey.create | 85.3 | 311.7 (3.66x) | 196.9 (2.31x) |
| pubkey.compress | 0.42 | 3.95 (9.42x) | 5.07 (12.07x) |
| seckey.verify | 0.01 | 0.38 (54.00x) | 1.20 (171.01x) |
| ecdh.xOnly | 161.5 | 1990.8 (12.32x) | 2259.4 (13.99x) |
| tweak.mul | 163.3 | 1982.8 (12.14x) | 2306.3 (14.12x) |
| tweak.add | 0.13 | 1.41 (10.83x) | - |
| nostr.verify | 195.0 | 1110.6 (5.70x) | 1902.1 (9.75x) |
| nostr.verifyFast | 184.5 | 1096.4 (5.94x) | 1907.8 (10.34x) |
| nostr.ecdh | 162.3 | 1961.1 (12.08x) | 2256.6 (13.91x) |

2026-11-09, Linux 6.18.44-fc-v310, Node.js v22.23.4 (V8 12.4.254.21-node.57); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 01e496d

#### Bun

| op | libsecp256k1 (wasm: tiny-secp256k1 2.2.5) | noble-curves (pure JavaScript) | noble-secp256k1 (pure JavaScript) |
|---|---:|---:|---:|
| verify.sameKey | 174.4 | 336.8 (1.93x) | 670.7 (3.85x) |
| verify.manyKeys | 178.6 | 353.1 (1.98x) | 666.4 (3.73x) |
| verifyFast.sameKey | 174.2 | 344.2 (1.98x) | 657.0 (3.77x) |
| sign | 166.0 | 612.3 (3.69x) | 904.7 (5.45x) |
| sign.knownPub | 163.3 | 615.7 (3.77x) | 817.9 (5.01x) |
| pubkey.create | 79.4 | 109.0 (1.37x) | 84.7 (1.07x) |
| pubkey.compress | 0.37 | 1.55 (4.15x) | 4.72 (12.65x) |
| seckey.verify | 0.00 | 0.25 (73.56x) | 1.44 (424.71x) |
| ecdh.xOnly | 156.1 | 574.9 (3.68x) | 739.9 (4.74x) |
| tweak.mul | 156.0 | 545.8 (3.50x) | 757.5 (4.85x) |
| tweak.add | 0.12 | 0.66 (5.33x) | - |
| nostr.verify | 177.7 | 367.9 (2.07x) | 660.9 (3.72x) |
| nostr.verifyFast | 180.1 | 372.4 (2.07x) | 660.8 (3.67x) |
| nostr.ecdh | 158.0 | 543.2 (3.44x) | 732.1 (4.63x) |

2026-11-09, Linux 6.18.44-fc-v310, Bun 1.4.4 (JavaScriptCore d6375c72affe0f70222cdcb45012b10f0bf3a9d5); libsecp256k1 tiny-secp256k1 2.2.5, noble-curves @noble/curves 2.4.0, noble-secp256k1 @noble/secp256k1 3.2.0; harness 01e496d

#### Python

| op | libsecp256k1 (CFFI: coincurve 21.0.0) | bip340-reference (pure Python) | btclib-ecc (pure Python) | embit (pure Python) | secp256k1lab (pure Python) |
|---|---:|---:|---:|---:|---:|
| verify.sameKey | 28.5 | 70871.0 (2483.87x) | 859.1 (30.11x) | 1903.4 (66.71x) | 7767.2 (272.22x) |
| verify.manyKeys | 30.9 | 72176.0 (2332.88x) | 880.5 (28.46x) | 1971.5 (63.72x) | 7760.6 (250.84x) |
| verifyFast.sameKey | 28.1 | 70295.1 (2504.39x) | 848.7 (30.24x) | 1891.2 (67.38x) | 7755.0 (276.29x) |
| verifyBatch200.perSig | - | - | 708.2 | - | - |
| sign | 86.6 | 141590.6 (1635.33x) | 1283.9 (14.83x) | 4018.6 (46.41x) | 11466.6 (132.44x) |
| sign.knownPub | 85.6 | 142976.3 (1669.48x) | 1315.5 (15.36x) | 4101.1 (47.89x) | 11479.6 (134.04x) |
| pubkey.create | 15.3 | 36083.6 (2362.46x) | 184.7 (12.09x) | 1304.9 (85.43x) | 1969.7 (128.96x) |
| pubkey.compress | 1.21 | - | 4.32 (3.57x) | 5.49 (4.54x) | 6.34 (5.24x) |
| seckey.verify | 0.26 | - | 0.58 (2.24x) | 0.38 (1.45x) | 0.47 (1.81x) |
| ecdh.xOnly | 28.3 | 36309.1 (1284.18x) | 771.7 (27.29x) | - | 5843.8 (206.68x) |
| tweak.mul | 28.4 | - | 795.3 (27.99x) | - | 5845.6 (205.75x) |
| tweak.add | 61.2 | - | 1.24 (0.02x) | 1.25 (0.02x) | 1.44 (0.02x) |
| nostr.verify | 30.4 | 72931.0 (2395.11x) | 916.9 (30.11x) | 2081.7 (68.37x) | 7728.7 (253.82x) |
| nostr.verifyFast | 31.6 | 73477.2 (2325.30x) | 892.0 (28.23x) | 1996.2 (63.17x) | 7787.2 (246.44x) |
| nostr.verifyBatchByAuthor | - | - | 890.1 | - | - |
| nostr.ecdh | 30.0 | 36089.7 (1201.62x) | 770.5 (25.65x) | - | 5852.8 (194.87x) |

2026-11-09, Linux 6.18.44-fc-v310, CPython 3.13.18 (Clang 23.1.3); libsecp256k1 coincurve 21.0.0 (libsecp256k1 v0.6.0, 0cdc758), btclib-ecc ecc 2026.11.7, embit embit 0.8.0 (embit.util.py_secp256k1), secp256k1lab secp256k1lab 7ed1812 (vendored), bip340-reference bips a34f044 bip-0340/reference.py (vendored); harness 01e496d
