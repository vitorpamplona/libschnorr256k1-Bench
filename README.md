# libschnorr256k1-bench

Benchmarks and cross-checks of BIP-340 Schnorr signatures and secp256k1 for Kotlin
Multiplatform, measured on each layer an app can call them from:

| Layer | What runs | libsecp256k1 | libschnorr256k1 | libschnorr256k1-kotlin |
|---|---|---|---|---|
| `c` | C calling C: Linux, macOS, Android (NDK, over adb) | direct | direct | |
| `jvm` | a plain JVM process | ACINQ secp256k1-kmp (JNI) | libschnorr256k1-kmp (JNI) | pure Kotlin |
| `native` | Kotlin/Native executable: linuxX64, macosArm64 | ACINQ (cinterop) | libschnorr256k1-kmp (cinterop; macOS only) | pure Kotlin |
| `android` | a release instrumentation test on ART | ACINQ (JNI) | libschnorr256k1-kmp (JNI; arm64-v8a, x86_64) | pure Kotlin |

The libraries: [libsecp256k1](https://github.com/bitcoin-core/secp256k1) (the reference, at the
commit [ACINQ's secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp) 0.24.0 bundles),
[libschnorr256k1](https://github.com/vitorpamplona/libschnorr256k1) through
[libschnorr256k1-kmp](https://github.com/vitorpamplona/libschnorr256k1-kmp), and
[libschnorr256k1-kotlin](https://github.com/vitorpamplona/libschnorr256k1-kotlin).

To compare with what other Nostr and Bitcoin software runs, every other maintained BIP-340
implementation we found runs too, each in its own language, with that ecosystem's libsecp256k1
binding as the reference beside it:

| Layer | Runtime | Libraries |
|---|---|---|
| `jvm` | JVM 21 | [Bouncy Castle](https://www.bouncycastle.org/) `BIP340Signer`, [bitcoin-s](https://github.com/bitcoin-s/bitcoin-s), [nostr-java](https://github.com/tcheeric/nostr-java) ([details](bench/README-jvm-libraries.md)) |
| `rust` | Rust | [rust-secp256k1](https://github.com/rust-bitcoin/rust-secp256k1) (libsecp256k1), [k256](https://github.com/RustCrypto/elliptic-curves), [secp256kfun](https://github.com/LLFourn/secp256kfun) ([details](rust/README.md)) |
| `go` | Go | libsecp256k1 (cgo), [btcec](https://github.com/btcsuite/btcd), p256k1 (ORLY's), [secp256k1-voi](https://gitlab.com/yawning/secp256k1-voi), [splitsecure](https://github.com/splitsecure/secp256k1-go) ([details](go/README.md)) |
| `node`, `bun` | Node.js, Bun | [tiny-secp256k1](https://github.com/bitcoinjs/tiny-secp256k1) (libsecp256k1 as WebAssembly), [@noble/curves](https://github.com/paulmillr/noble-curves), [@noble/secp256k1](https://github.com/paulmillr/noble-secp256k1) ([details](js/README.md)) |
| `python` | CPython | [coincurve](https://github.com/ofek/coincurve) (libsecp256k1), [btclib](https://github.com/btclib-org/btclib) (`ecc`), [embit](https://github.com/diybitcoinhardware/embit), [secp256k1lab](https://github.com/secp256k1lab/secp256k1lab), BIP-340's [reference.py](https://github.com/bitcoin/bips/blob/master/bip-0340/reference.py) ([details](python/README.md)) |

## How it measures

- **One set of inputs:** [`vectors/`](vectors/README.md), 4096 keys, messages and tweaks
  derived from SHA-256 of fixed labels. Before timing, every library's outputs must hash to the
  digests in `vectors/v1.txt`; a library that differs is still timed, but its row says
  `correct: false`.
- **Real traffic too:** [`datasets/`](datasets/README.md), the 29,614 signed events of an
  Amethyst startup session in 2024 (id, pubkey, sig), by 4,926 authors. The `nostr.*`
  operations verify them in the order relays sent them, verify them in pages batched by author,
  and run ECDH against their authors' keys. Every library must accept every signature.
- **One method:** the same runner in C (`c/main.c`) and Kotlin (`bench/.../Runner.kt`): 1 s
  warm-up per operation, then 20 samples of at least 100 ms each, median and 5th/95th percentile
  per call. No JMH or Jetpack Microbenchmark, so the layers are comparable.
- **One format:** [`results/SCHEMA.md`](results/SCHEMA.md), frozen. It covers the sixteen
  operations, the exact call each library makes for each one, and the known issues the runners
  probe for.

## Results

One table per operation, verification first: a Nostr client verifies every event every relay
sends. Each table ranks every library on every layer, with libschnorr256k1-kotlin, the library
this bench was built for, in bold. Runs from a dedicated machine are what the tables are for;
until there is one, a full run on a shared cloud VM stands in, labelled as such, and the first
dedicated run replaces it. The newest device's runs are here, and every device's in
[results/report.md](results/report.md). The runs are the JSON
files in `results/<layer>/`; `scripts/table.py --readme` writes this section and the report from
them, and CI fails when either is stale.

<!-- results:begin: scripts/table.py --readme writes this section; do not edit it by hand -->

No runs yet.

<!-- results:end -->

## Known issues

Every run probes for these and records what it saw (`known_issues_observed`):

| ID | Library | Issue |
|---|---|---|
| KI-1 | libschnorr256k1, kotlin | Batch verify has no random coefficients: two invalid signatures that cancel are accepted. |
| KI-2 | libschnorr256k1 | `schnorr_sign_xonly` returns invalid signatures for keys whose public key has odd y. |
| KI-3 | libschnorr256k1 | A tweak-mul on the odd-y form of a key poisons the x-keyed table that verify uses: valid signatures are rejected. |

## Run

```bash
git submodule update --init --recursive
./gradlew :bench:jvmTest :bench:linuxX64Test       # cross-checks, no timing (macOS: :bench:macosArm64Test)
```

```bash
scripts/run-host.sh --device "MacBook Pro M3 Pro" --machine dedicated
```

```bash
scripts/run-android.sh --machine dedicated
```

```bash
scripts/run-android-c.sh --machine dedicated
```

```bash
scripts/table.py --readme
```

- `run-host.sh` runs the c, jvm and native layers, and the rust, go, node, bun and python
  layers whose toolchain is installed (it says which it skipped). It needs cmake, a C compiler
  and a JDK; `--layers "c rust"` picks layers.
- `run-android.sh` runs the android layer on the device adb sees.
- `run-android-c.sh` builds `c/` with the NDK (`ANDROID_NDK_HOME`) and runs it on that device.
- `table.py --readme` writes this README's Results and `results/report.md` from the dedicated
  runs under `results/`, or, while there are none, the full shared ones. Commit the JSON files, the README and the report together. Without
  `--readme`, `table.py` prints the tables of any results you give it, quick and shared runs
  included.
- `--quick` makes any of them a smoke run, whose numbers are never comparable.
- CI times nothing. On Linux, macOS and an Android emulator it runs each layer's checks
  (`build/c/bench check`, `:bench:jvmTest`, `<layer>/run.sh --check`, `run-android*.sh --check`):
  every library's outputs against the vectors and the dataset, and the known-issue probes.
- A dedicated run means a machine doing nothing else: a laptop on AC power, a phone charged,
  cool and in flight mode.
