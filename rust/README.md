# The rust layer

BIP-340 libraries called from Rust: the twin of the c layer (`c/main.c`) in a cargo binary
crate. Same inputs (`vectors/v1.txt`, BIP-340's `vectors/bip340/test-vectors.csv`,
`datasets/nostr-v1.bin`), the same checks before any
timing, the same sixteen operations with the same inputs and counting, the same timing (1 s
warm-up, 20 samples of at least 100 ms; `--quick`: 0.2 s, 5 samples of at least 20 ms; median
and nearest-rank 5th/95th percentile of per-sample time ÷ operations), the same known-issue
probes and the same JSON, with `"schema": 4` and `"layer": "rust"`.

```bash
rust/run.sh --device "ThinkPad X1 Gen 11" --machine dedicated --out results/rust/2026-11-08-thinkpad-x1-gen-11.json
rust/run.sh --quick --device "CI runner" --machine shared --out /tmp/rust.json
rust/run.sh --check      # the checks and probes, no timing: exits 1 on a wrong result no observed known issue explains
```

`--library NAME` runs one library. `run.sh` builds with `cargo build --release --locked`, so
the versions are the ones in `Cargo.lock`. It needs cargo (tested with rustc 1.97.0), a C
compiler (for libsecp256k1) and python3 (to read `cargo metadata`).

## Libraries

| `library` | `binding` | Crate (version in `Cargo.lock`) | License | What it is |
|---|---|---|---|---|
| `libsecp256k1` | `rust-secp256k1` | [`secp256k1`](https://crates.io/crates/secp256k1) 0.33.1, over `secp256k1-sys` 0.14.1, which bundles libsecp256k1 `6e2c8bc` (the commit the c layer's submodule is at) | CC0-1.0 (the crates); MIT (libsecp256k1, `depend/secp256k1/COPYING`) | rust-bitcoin's binding to bitcoin-core's libsecp256k1, the one `bitcoin`, `nostr` (rust-nostr) and the apps on them use (mostly at 0.29 / 0.30 today: see below) |
| `k256` | `rust` | [`k256`](https://crates.io/crates/k256) 0.14.0, features `schnorr`, `ecdh` (on top of the defaults) | Apache-2.0 OR MIT | RustCrypto's pure-Rust secp256k1 (precomputed basepoint tables, GLV), with BIP-340 in `k256::schnorr` |
| `secp256kfun` | `rust` | [`schnorr_fun`](https://crates.io/crates/schnorr_fun) 0.13.0 over [`secp256kfun`](https://crates.io/crates/secp256kfun) 0.12.1; `sha2` 0.10.9 as its hash | 0BSD (`schnorr_fun/LICENSE`, the crates' metadata); secp256kfun's `src/vendor/` is Apache-2.0 OR MIT | LLFourn's pure-Rust secp256k1 and BIP-340 (also MuSig2, FROST). Its field and scalar arithmetic is vendored from k256 0.11 (`src/vendor/k256`); point multiplication, encodings and the Schnorr code are its own, and it has no basepoint tables |

`context.libraries` gives each crate's exact version (from `Cargo.lock`, through `build.rs`)
and, for libsecp256k1, the commit `secp256k1-sys` bundles (`depend/secp256k1-HEAD-revision.txt`).

None of the three has a fast or a batch verify, so `verifyFast.*` time the full verify (as for
libsecp256k1 everywhere) and there are no `verifyBatch200.perSig` or
`nostr.verifyBatchByAuthor` rows, and KI-1 and KI-4 are `null`.

### Not included

- `libsecp256k1` (paritytech's pure-Rust port, 0.7.2, Apache-2.0): no BIP-340 (ECDSA only).
- `bitcoin`, `nostr`, `secp256k1-zkp`, `secp` (conduition), `ic-secp256k1`, `frost-secp256k1-tr`,
  `musig2`, `nostr-verify`: wrappers over `secp256k1` or `k256`, or a fork of libsecp256k1.
- `blvm-secp256k1` 0.1.15 (MIT): a pure-Rust port of libsecp256k1, with a batch verify. Left out
  for now: 0.1.x, used only by its own `blvm-*` crates (about 5k downloads), its README's
  examples do not match its API, and its build script links a CUDA verifier whenever `nvcc` is
  on the build machine, so the same run would measure different code on different hosts.
- `crrl` (research library, 2024), `purecrypto` (BIP-340-style adaptor signatures only),
  `neco-secp` (a Nostr signer with its own curve code, a few hundred downloads): not a
  maintained, used BIP-340 implementation.

## Build

Cargo's release profile with nothing changed (`opt-level = 3`, no LTO, 16 codegen units,
`panic = "unwind"`), and no `RUSTFLAGS`: no `-C target-cpu=native`, as the c layer's CMake has no
`-march`. Every crate uses its defaults plus the features its API needs (above); `secp256k1`
has no `rand` feature.

`secp256k1-sys` compiles libsecp256k1 with the `cc` crate, not libsecp256k1's CMake:
`-O3 -march=x86-64` (cc follows cargo's opt-level), `ECMULT_WINDOW_SIZE=15`, `COMB_BLOCKS=43`,
`COMB_TEETH=6` (the CMake defaults the c layer has), but no `USE_ASM_X86_64`: where the c layer's
CMake (`SECP256K1_ASM=AUTO`) uses libsecp256k1's x86-64 assembly, this layer uses its portable C
(`__int128`). With Ubuntu's gcc the crate's first compile fails (its `-Dprintf(...)=` collides
with glibc's fortified `stdio.h`) and `cc` retries with the crate's own minimal libc headers
(`wasm/wasm-sysroot`); the library code is the same.

## The calls

Every call starts from bytes and ends in bytes, as in the c layer: keys, signatures and points
are parsed on every call. A library without a call for an operation gets it composed only from
its own public API, as noted.

### libsecp256k1 (`secp256k1` 0.33.1)

The crate has no context argument any more: every call borrows a thread-local context, and
since 0.33 every call that takes a secret (`Keypair::from_secret_bytes`,
`PublicKey::from_secret_key`, `schnorr::sign_*`) re-randomizes that context afterwards, seeded
from the secret. A re-randomization costs about one basepoint multiplication (21 µs here, against
13 µs for `ec_pubkey_create` itself), so `sign` and `pubkey.create` cost more than through C or
JNI: that is what a Rust app using the crate pays.

This is the crate's latest release, chosen because it bundles the same libsecp256k1 commit as
the c layer, so the two differ only by the binding. Most of the crate's users are still on 0.30
(rust-nostr 0.45.7 pins it) or 0.29 (`bitcoin` 0.32): those take an explicit context, do not
re-randomize per call, and bundle an older libsecp256k1 (`secp256k1-sys` 0.10.1: `1ad5185`).

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.sameKey`, `nostr.verify`, `nostr.verifyFast` | `XOnlyPublicKey::from_byte_array` + `schnorr::verify` (no fast variant) |
| `sign`, `sign.knownPub` | `Keypair::from_secret_bytes` + `schnorr::sign_with_aux_rand` (the API always derives the key pair) |
| `pubkey.create` | `SecretKey::from_secret_bytes` + `PublicKey::from_secret_key` + `serialize_uncompressed` |
| `pubkey.compress` | `PublicKey::from_byte_array_uncompressed` + `serialize` |
| `seckey.verify` | `SecretKey::from_secret_bytes` (`ec_seckey_verify`) |
| `ecdh.xOnly`, `nostr.ecdh` | `PublicKey::from_byte_array_compressed(02 ‖ x)` + `mul_tweak` + `serialize`, take x: the c layer's composition. (rust-nostr uses `ecdh::shared_secret_point` instead, the same multiplication through libsecp256k1's ECDH module.) |
| `tweak.mul` | `PublicKey::from_byte_array_compressed` + `Scalar::from_be_bytes` + `mul_tweak` + `serialize` |
| `tweak.add` | `SecretKey::from_secret_bytes` + `Scalar::from_be_bytes` + `add_tweak` |
| batch | no row |

### k256 (0.14.0)

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.sameKey`, `nostr.verify`, `nostr.verifyFast` | `schnorr::VerifyingKey::from_bytes` + `Signature::from_bytes` + `verify_prehash` (BIP-340 on the 32-byte message as given; `Verifier::verify` would SHA-256 it first) |
| `sign`, `sign.knownPub` | `schnorr::SigningKey::from_bytes` (derives the public key) + `sign_raw(msg, aux)`, the call that takes the aux bytes (the `RandomizedPrehashSigner` impl draws them from an RNG and calls it). No signing key from a known public key. |
| `pubkey.create` | `SecretKey::from_bytes` + `public_key()` + `to_sec1_point(false)` |
| `pubkey.compress` | `PublicKey::from_sec1_bytes` (65) + `to_sec1_point(true)` |
| `seckey.verify` | `SecretKey::from_bytes` |
| `ecdh.xOnly`, `nostr.ecdh` | `PublicKey::from_sec1_bytes(02 ‖ x)` + `NonZeroScalar::from_repr` + `ecdh::diffie_hellman` (its shared secret is the x coordinate) |
| `tweak.mul` | `PublicKey::from_sec1_bytes` + `NonZeroScalar::from_repr` + `ProjectivePoint * Scalar` + `PublicKey::from_affine` + `to_sec1_point(true)`: k256 has no tweak function; its point operator is how a k256 app tweaks |
| `tweak.add` | `NonZeroScalar::from_repr` (key) + `Scalar::from_repr` (tweak) + `Scalar + Scalar` + `NonZeroScalar::new` (rejects 0) + `to_repr`: the same, with its scalar operator (as the `bip32` crate does) |
| batch | no row |

### secp256kfun (`schnorr_fun` 0.13.0, `secp256kfun` 0.12.1)

`Schnorr<Sha256, Synthetic<Sha256, AuxRng>>`: schnorr_fun's synthetic nonces are BIP-340's
(tagged hash of aux, xor the key, then the nonce hash of that, the x-only key and the message),
with the 32 aux bytes from a `NonceRng`; `AuxRng` is a `NonceRng` that hands over the call's aux.
Secret scalars keep secp256kfun's default `Secret` marker, so multiplications by them take its
constant-time path, as an app's would.

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.sameKey`, `nostr.verify`, `nostr.verifyFast` | `Point::<EvenY>::from_xonly_bytes` + `Signature::from_bytes` (which lifts R to a point, a square root more than libsecp256k1 does) + `Schnorr::verify(.., Message::raw(msg), ..)` |
| `sign`, `sign.knownPub` | `Scalar::from_bytes` + `KeyPair::new_xonly` (derives the key; no constructor from a known one) + `Schnorr::sign(.., Message::raw(msg))` |
| `pubkey.create` | `Scalar::from_bytes` + `g!(sk * G).normalize()` + `to_bytes_uncompressed` |
| `pubkey.compress` | `Point::from_bytes_uncompressed` (which decompresses x and compares y: a square root) + `to_bytes` |
| `seckey.verify` | `Scalar::<Secret, NonZero>::from_bytes` |
| `ecdh.xOnly`, `nostr.ecdh` | `Point::<EvenY>::from_xonly_bytes` + `Scalar::from_bytes` + `g!(sk * P).normalize()`, take x: secp256kfun has no ECDH function; `g!` is its API for point arithmetic |
| `tweak.mul` | `Point::from_bytes` + `Scalar::from_bytes` + `g!(t * P).normalize()` + `to_bytes` |
| `tweak.add` | `Scalar::<Secret, NonZero>::from_bytes` (key) + `Scalar::<Secret, Zero>::from_bytes` (tweak) + `s!(sk + t)` + `non_zero()` + `to_bytes` |
| batch | no row |

## Layout

- `src/main.rs`: the twin of `c/main.c` (dataset, digests, checks, operations, timing, probes,
  JSON, and the `check` command).
- `src/vectors.rs`: the twin of `c/vectors.c` (its own SHA-256 and the v1 derivation).
- `src/backend.rs`: the twin of `c/backend.h`; one `*_backend.rs` / `libsecp256k1.rs` per
  library.
- `build.rs`: the locked versions, the rustc version and the commit, for the result's context.

The derived values (`pub65`, `sig`, …) come from libsecp256k1, the layer's reference; the probes
use it as the c layer does. One timing loop calls the library through `&dyn Backend`, as the c
layer calls through function pointers, and feeds a byte of each output into a sum that goes
through `std::hint::black_box`.
