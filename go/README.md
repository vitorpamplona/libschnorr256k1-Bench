# The go layer

Go's BIP-340 implementations, and libsecp256k1 through cgo, timed by `main.go`, a twin of
`c/main.c`: the same derivation (`vectors.go`; SHA-256 is Go's `crypto/sha256`), the same digest,
BIP-340 vector (`bip340.go`) and dataset checks before any timing, the same sixteen operations on the same inputs, the same
timing (1 s warm-up, then 20 samples of at least 100 ms; `--quick`: 0.2 s, 5 of at least 20 ms;
median and nearest-rank p5/p95 of per-sample time ÷ operations), the same known-issue probes
after the timing, and the same JSON with `"schema": 4`, `"layer": "go"`. None has a fast verify, so
KI-4 is `null`.

The runner is one goroutine (`GOMAXPROCS` is left alone); results go into a package-level sink.
It calls `runtime.GC()` before each operation's warm-up (outside every timed sample), so one
operation's garbage is not collected inside the next one's samples.

```bash
go/run.sh --quick --device "Cloud VM (4 vCPU)" --machine shared --out results/go/2026-11-08-cloud-vm-4-vcpu-quick.json
go/run.sh --device "ThinkPad X1 Gen 11" --machine dedicated --out results/go/2026-11-08-thinkpad-x1-gen-11.json
go/run.sh --check                 # the cross-check: no timing; exit 1 on a wrong result no known issue explains
go/run.sh --check --library btcec # one library (also on a run)
```

`run.sh` initializes `third_party/secp256k1` if it is missing, builds `build/go/bench` with
`CGO_ENABLED=1` and runs it. It needs Go and a C compiler; `go.mod` says `go 1.26.10` (btcec
v2.5.1 requires it), so an older `go` downloads that toolchain (`GOTOOLCHAIN=auto`, the
default). `context.runtime` is the Go version and the C compiler cgo used, e.g.
`Go go1.26.10 (gc, linux/amd64), cgo: gcc 13.3.0 -O2`. Versions in `context.libraries` come from
the binary's build info (go.sum's exact versions), and the submodule's commit for libsecp256k1.

## Libraries

| `library` | `binding` | Version | License | What |
|---|---|---|---|---|
| `libsecp256k1` | `cgo` | bitcoin-core/secp256k1 `6e2c8bc` (v0.8.0, the submodule) | MIT | The reference, compiled into the binary by cgo (`libsecp256k1/`) with the c layer's CMake defines (`COMB_BLOCKS=43 COMB_TEETH=6 ECMULT_WINDOW_SIZE=15`, extrakeys and schnorrsig, `USE_ASM_X86_64` on amd64, `-O2`), one cgo call per libsecp256k1 function. |
| `btcec` | `go` | github.com/btcsuite/btcd/btcec/v2 v2.5.1 | ISC | btcd's; its curve math is decred's dcrd/dcrec/secp256k1/v4 v4.4.1 (ISC). What go-nostr and fiatjaf.com/nostr (and so most Go Nostr software) and lnd run. |
| `p256k1` | `go` | p256k1.mleku.dev v1.1.7 (served from git.mleku.dev/mleku/p256k1, through a `replace` as ORLY does) | MIT (libsecp256k1's notice) | mleku's pure-Go port of libsecp256k1, with amd64 assembly; what the ORLY relay ships (through `git.mleku.dev/mleku/nostr`). Deps: purego (Apache-2.0), klauspost/cpuid (MIT), minio/sha256-simd (Apache-2.0). |
| `secp256k1-voi` | `go` | gitlab.com/yawning/secp256k1-voi v0.0.0-20230925100816-f2616030848b | BSD-3-Clause | Yawning Angel's: fiat-crypto field and scalar arithmetic, complete addition formulas, constant time. BIP-340 in `secec/bitcoin`. Last commit 2023-09. |
| `splitsecure` | `go` | github.com/splitsecure/secp256k1-go v0.1.0 | MIT (libsecp256k1's notice; its s2n-bignum assembly: Apache-2.0 OR ISC OR MIT-0) | A constant-time pure-Go transcription of libsecp256k1, field multiply in assembly translated from AWS s2n-bignum, differentially tested against the C library. |

No GPL code: every module in `go.sum` is MIT, ISC, BSD or Apache-2.0.

## The calls

Every adapter (`adapters.go`) uses only its library's public API; the harness copies bytes and
nothing else. Outputs are the exact-size slices the C backends write.

### libsecp256k1 (`cgo`)

As `c/backend_libsecp256k1.c`, call for call: one `SECP256K1_CONTEXT_NONE` context, not
randomized.

| Op | Call |
|---|---|
| `verify.*`, `nostr.verify` | `xonly_pubkey_parse` + `schnorrsig_verify` |
| `verifyFast.sameKey`, `nostr.verifyFast` | full verify (no fast variant) |
| `sign`, `sign.knownPub` | `keypair_create` + `schnorrsig_sign32` (always derives the key pair) |
| `pubkey.create` | `ec_pubkey_create` + `ec_pubkey_serialize` (65) |
| `pubkey.compress` | `ec_pubkey_parse` (65) + `ec_pubkey_serialize` (33) |
| `seckey.verify` | `ec_seckey_verify` |
| `ecdh.xOnly`, `nostr.ecdh` | `ec_pubkey_parse(02 ‖ x)` + `ec_pubkey_tweak_mul` + serialize (33), take x |
| `tweak.mul` | `ec_pubkey_parse` (33) + `ec_pubkey_tweak_mul` + serialize (33) |
| `tweak.add` | `ec_seckey_tweak_add` on a copy |
| batch rows | none (no batch verify) |

### btcec

| Op | Call |
|---|---|
| `verify.*`, `nostr.verify` | `schnorr.ParsePubKey(x)` + `schnorr.ParseSignature(sig)` + `sig.Verify(msg, pub)`, as go-nostr's `CheckSignature` |
| `verifyFast.*` | full verify (no fast variant) |
| `sign`, `sign.knownPub` | `ModNScalar.SetByteSlice(sk)` (rejecting overflow and zero) + `btcec.PrivKeyFromScalar` + `schnorr.Sign(priv, msg, schnorr.CustomNonce(aux), schnorr.FastSign())`.Serialize(). `CustomNonce` is what makes btcec derive the nonce from aux as BIP-340 does (without it btcec uses RFC 6979, and the signatures differ from the vectors); `FastSign` skips btcec's verify-after-sign, which libsecp256k1 does not do either (go-nostr signs with `FastSign` too). `Sign` derives P itself, so there is no known-pub variant. |
| `pubkey.create` | `PrivKeyFromScalar(k).PubKey().SerializeUncompressed()` |
| `pubkey.compress` | `btcec.ParsePubKey(pub65).SerializeCompressed()` |
| `seckey.verify` | `ModNScalar.SetByteSlice(sk)`: not overflowed and `!IsZero()` (btcec has no single call) |
| `ecdh.xOnly`, `nostr.ecdh` | `schnorr.ParsePubKey(x)` (lift_x) + `btcec.GenerateSharedSecret(PrivKeyFromScalar(k), pub)`, which returns x |
| `tweak.mul` | `btcec.ParsePubKey(pub33)` + `AsJacobian` + `btcec.ScalarMultNonConst(k, P, R)` + `R.ToAffine()` + `btcec.NewPublicKey(R.X, R.Y).SerializeCompressed()` |
| `tweak.add` | `ModNScalar.SetByteSlice` of both (secret: no overflow, not zero; tweak: no overflow) + `k.Add(t)`, fail if zero, `PutBytesUnchecked` |
| batch rows | none |

### p256k1

Its root package mirrors libsecp256k1's C API, and these are those calls.

| Op | Call |
|---|---|
| `verify.*`, `nostr.verify` | `XOnlyPubkeyParse(x)` + `SchnorrVerify(sig, msg, pk)` |
| `verifyFast.*` | full verify (no fast variant) |
| `verifyBatch200.perSig`, `nostr.verifyBatchByAuthor` | `XOnlyPubkeyParse(x)` once, then `SchnorrBatchVerify(items)` with one `BatchSchnorrItem{pk, msg, sig}` per signature (the items slice is reused). Random-looking coefficients from a hash of the batch, so KI-1 does not reproduce. |
| `sign`, `sign.knownPub` | `KeyPairCreate(sk)` + `SchnorrSign(sig, msg, kp, aux)` (no constructor takes a known public key) |
| `pubkey.create` | `ECPubkeyCreate` + `ECPubkeySerialize(…, ECUncompressed)` |
| `pubkey.compress` | `ECPubkeyParse` (65) + `ECPubkeySerialize(…, ECCompressed)` |
| `seckey.verify` | `ECSeckeyVerify` |
| `ecdh.xOnly`, `nostr.ecdh` | `ECPubkeyParse(02 ‖ x)` + `ECDHXOnly(out, pk, sk)` |
| `tweak.mul` | `ECPubkeyParse` (33) + `ECPubkeyTweakMul` + `ECPubkeySerialize(…, ECCompressed)` |
| `tweak.add` | `ECSeckeyTweakAdd` on a copy |

### secp256k1-voi

| Op | Call |
|---|---|
| `verify.*`, `nostr.verify` | `bitcoin.NewSchnorrPublicKey(x)` + `pk.Verify(msg, sig)` |
| `verifyFast.*` | full verify (no fast variant) |
| `sign`, `sign.knownPub` | `bitcoin.NewSchnorrPrivateKey(sk)` + `k.Sign(reader, msg, nil)`, the reader holding aux (`Sign` reads its 32 bytes of auxiliary randomness from it). `Sign` always checks its signature (with a base-point multiply, not a full verify); there is no option to skip it, and no known-pub variant. |
| `pubkey.create` | `secec.NewPrivateKey(sk).PublicKey().Bytes()` (uncompressed) |
| `pubkey.compress` | `secec.NewPublicKey(pub65).CompressedBytes()` |
| `seckey.verify` | `secp256k1.NewScalarFromBytes(sk)`: not reduced and `IsZero() == 0`, the check `secec.NewPrivateKey` makes before deriving the public key |
| `ecdh.xOnly`, `nostr.ecdh` | `bitcoin.NewSchnorrPublicKey(x).Point()` (lift_x) + `NewIdentityPoint().ScalarMult(s, P).XBytes()`, with `s` parsed as for `seckey.verify`. This is the body of `secec.PrivateKey.ECDH`, without the public-key derivation `secec.NewPrivateKey` would add. |
| `tweak.mul` | `secp256k1.NewPointFromBytes(pub33)` + `NewIdentityPoint().ScalarMult(t, P).CompressedBytes()` |
| `tweak.add` | `NewScalarFromBytes` of both (as above) + `NewScalar().Add(k, t)`, fail if zero, `Bytes()` |
| batch rows | none (it has `MultiScalarMultVartime`, but no batch verify) |

### splitsecure

Its API mirrors libsecp256k1's too.

| Op | Call |
|---|---|
| `verify.*`, `nostr.verify` | `ParseXOnlyPublicKey(x)` + `schnorr.Verify(pk, msg, sig)` |
| `verifyFast.*` | full verify (no fast variant) |
| `sign`, `sign.knownPub` | `NewKeypair(&sk)` + `schnorr.Sign32(kp, msg, aux)` (no known-pub variant) |
| `pubkey.create` | `SecretKey.PublicKey()` + `SerializeUncompressed()` |
| `pubkey.compress` | `ParsePublicKey(pub65)` + `SerializeCompressed()` |
| `seckey.verify` | `SecretKey.IsValid()` |
| `ecdh.xOnly`, `nostr.ecdh` | `ParseXOnlyPublicKey(x).PublicKey()` + `ecdh.ECDHWithHash(&sk, pk, kdf)` with a kdf that returns x, as libsecp256k1's `secp256k1_ecdh` with a custom hash function |
| `tweak.mul` | `ParsePublicKey(pub33)` + `TweakMul(t)` + `SerializeCompressed()` |
| `tweak.add` | `SecretKey.TweakAdd(t)` on a copy |
| batch rows | none |

## Candidates left out

| Candidate | Why |
|---|---|
| github.com/decred/dcrd/dcrec/secp256k1/v4/schnorr | EC-Schnorr-DCRv0 (BLAKE-256 challenge), not BIP-340. Its curve code is in the layer, under btcec. |
| go-ethereum `crypto/secp256k1`, github.com/erigontech/secp256k1 | ECDSA (and recovery) only. |
| github.com/nbd-wtf/go-nostr, fiatjaf.com/nostr | No Schnorr of their own: btcec by default; the `libsecp256k1` build tag compiles a vendored libsecp256k1 through cgo, but only behind `Event.Sign`/`CheckSignature` (it hashes the event JSON), so it cannot run the operations. The `cgo` binding here calls libsecp256k1 the same way. |
| A maintained cgo binding package to libsecp256k1 with BIP-340 | None exists: github.com/vulpemventures/go-secp256k1-zkp (secp256k1-zkp, last release 2023), github.com/kaspanet/go-secp256k1 (Kaspa's fork, 2021), github.com/btccom/secp256k1-go (2017), github.com/toxeus/go-secp256k1 (2018), github.com/ipsn/go-secp256k1 (2018) are stale; github.com/guibressan/secp256k1 binds ECDSA only; github.com/setavenger/go-libsecp256k1 binds only point and scalar ops for silent payments. Hence the harness's own cgo calls, as go-nostr makes them. |
| p8k.mleku.dev | A second binding to libsecp256k1 (purego, `dlopen` of a prebuilt `.so` it ships): one binding per library per layer, and the C library here is built from the bench's submodule. |
| github.com/allocz/secp256k1 | A hybrid: libsecp256k1 through cgo, or decred-derived code with btcec's Schnorr under `CGO_ENABLED=0`; both already in the layer. |
| ec.mleku.dev/v2 (github.com/mleku/btcec), github.com/KarpelesLab/secp256k1 and the many dcrd forks on pkg.go.dev | Forks of btcec/dcrd. |
| github.com/consensys/gnark-crypto | secp256k1 has ECDSA only. |
| github.com/bronlabs/bron-crypto | A threshold-signature (MPC) framework whose `bip340` is a variant of its generic Schnorr over its generic algebra; no Nostr or Bitcoin software runs it. |
| UltrafastSecp256k1 | A C++ library; its Go binding (`bindings/go`) needs the C++ build and is not on the Go module proxy. A C/C++ library belongs in a C layer. |
| github.com/fiatjaf/bip340, github.com/ngchain/go-schnorr, github.com/pedroalbanese/bip0340, github.com/primefactor-io/ecc | Unmaintained (last versions 2022 and 2020) or single-author repositories with no stars and no known users. |
