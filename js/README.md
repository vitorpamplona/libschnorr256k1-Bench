# The node and bun layers

The BIP-340 libraries JavaScript apps run, checked and timed on two runtimes by one harness, the
twin of `c/main.c`: Node.js (layer `node`) and Bun (layer `bun`). Both run the same files on the
same `node_modules`, installed from `package-lock.json`.

```bash
js/run.sh --runtime node --device "ThinkPad X1 Gen 11" --machine dedicated --out results/node/2026-11-08-thinkpad-x1-gen-11.json
js/run.sh --runtime bun  --device "ThinkPad X1 Gen 11" --machine dedicated --out results/bun/2026-11-08-thinkpad-x1-gen-11.json
js/run.sh --runtime node --quick --device "CI runner" --machine shared --out /tmp/node.json   # smoke run
js/run.sh --runtime node --check      # cross-check, no timing: exit 1 on a wrong result no known issue explains
js/run.sh --runtime bun --check
```

`--library NAME` runs one library. `run.sh` runs `npm ci` the first time (it needs npm, so Node,
even for the bun layer); `context.harness` is `scripts/common.sh`'s `harness_id`. The checks
dominate the run time (the dataset's 59,228 verifications, with each pure-JS library): on a
2.3 GHz Xeon a `--quick` run took 6 minutes on Node and 2.5 on Bun, whose BigInt arithmetic is
about three times faster here; `--quick` shortens only the timing, never the checks.

Deno is not covered.

## Libraries

| `library` | `binding` | Package (`context.libraries`) | License | What it is |
|---|---|---|---|---|
| `libsecp256k1` | `wasm` | `tiny-secp256k1` 2.2.5 | MIT (`LICENSE`, bitcoinjs contributors) | bitcoinjs' binding: rust-secp256k1's `secp256k1-sys` 0.8.1 (libsecp256k1's C, the `rustsecp256k1_v0_8_1_` symbols in `lib/secp256k1.wasm`) compiled to WebAssembly, with a thin JS layer that validates arguments and copies them into the module's memory. What bitcoinjs-lib, ecpair and bip32 users load. The layer's reference: it derives the vectors' `pub65`, `sig`, ... and signs the KI probes. |
| `noble-curves` | `js` | `@noble/curves` 2.4.0 | MIT (`LICENSE`, Paul Miller) | Pure JS (BigInt) secp256k1 with GLV and BIP-340 `schnorr`. What nostr-tools (which pins 2.0.1), NDK through it, @scure/btc-signer and @bitcoinerlab/secp256k1 call. |
| `noble-secp256k1` | `js` | `@noble/secp256k1` 3.2.0 | MIT (`LICENSE`, Paul Miller) | The same author's separate, single-file 5 KB library (its own code, no GLV, an 8-bit window table for G), with BIP-340 `schnorr` since v2. Its synchronous calls need a SHA-256: `hashes.sha256 = sha256` from `@noble/hashes` 2.4.0 (MIT), as its README sets it up. |

None has a fast or a batch verify, so no library has a `verifyBatch200.perSig` or
`nostr.verifyBatchByAuthor` row, `verifyFast.sameKey` and `nostr.verifyFast` time the full
verify (as libsecp256k1's rows do on the other layers), and KI-1 and KI-4 are `null`. None takes the public
key when signing, so `sign.knownPub` is `sign` (as libsecp256k1's) and KI-2 is `false`.

### Calls

Every input and output is a `Uint8Array`. A library that throws on an invalid input is caught
in the adapter (`src/impls.js`) and returns `null`/`false`, as an app wraps it. `02 ‖ x` is
written into a 33-byte scratch array the adapter reuses.

| Op | `libsecp256k1` (tiny-secp256k1) | `noble-curves` | `noble-secp256k1` |
|---|---|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `verifySchnorr(msg, xonly, sig)` | `schnorr.verify(sig, msg, xonly)` | `schnorr.verify(sig, msg, xonly)` |
| `verifyBatch200.perSig`, `nostr.verifyBatchByAuthor` | no row | no row | no row |
| `sign`, `sign.knownPub` | `signSchnorr(msg, seckey, aux)` | `schnorr.sign(msg, seckey, aux)` | `schnorr.sign(msg, seckey, aux)` |
| `pubkey.create` | `pointFromScalar(seckey, false)` | `secp256k1.getPublicKey(seckey, false)` | `getPublicKey(seckey, false)` |
| `pubkey.compress` | `pointCompress(pub65, true)` | `Point.fromBytes(pub65).toBytes(true)` | `Point.fromBytes(pub65).toBytes(true)` |
| `seckey.verify` | `isPrivate(seckey)` (a JS byte compare, no call into the wasm) | `secp256k1.utils.isValidSecretKey(seckey)` | `utils.isValidSecretKey(seckey)` |
| `ecdh.xOnly`, `nostr.ecdh` | `pointMultiply(02 ‖ x, seckey, true)`, take x (libsecp256k1's composition on every layer) | `secp256k1.getSharedSecret(seckey, 02 ‖ x, true)`, take x (as nostr-tools' NIP-04) | `getSharedSecret(seckey, 02 ‖ x, true)`, take x |
| `tweak.mul` | `pointMultiply(pub33, tweak, true)` | `Point.fromBytes(pub33).multiply(Point.Fn.fromBytes(tweak)).toBytes(true)` | `Point.fromBytes(pub33).multiply(etc.bytesToNumberBE(tweak)).toBytes(true)` |
| `tweak.add` | `privateAdd(seckey, tweak)` | `Fn.toBytes(Fn.add(Fn.fromBytes(seckey), Fn.fromBytes(tweak)))` with `Fn = Point.Fn`, `null` unless `Fn.isValidNot0` (as @bitcoinerlab/secp256k1's `privateAdd`) | no row: the package has no scalar addition (only `etc.mod`, which would leave the addition itself to the harness) |

## Method

`src/main.js run` runs each library in its own process (`main.js measure --library NAME`, the
same runtime binary), so one library's JIT profile, inline caches and heap (noble-secp256k1's
precomputed table, for one) cannot reach another's numbers; the parent prints the result. Each
process derives the vectors, checks the library and times it exactly as `c/main.c`: every v1
digest and the direct checks (`sign.knownPub` equals `sign`, `seckey.verify` accepts every
vector key and rejects 0 and n), the nostr-v1 digests, then the sixteen operations in order
(1 s warm-up in calls of 16, a call count doubled until one sample lasts 100 ms, 20 samples;
`--quick`: 0.2 s, 20 ms, 5), the same cycling of inputs, and median, p5 and p95 by nearest rank
of per-sample time ÷ calls. The clock is `process.hrtime.bigint()`. Every call's output feeds an
accumulator (`out[7]`, or 1 per accepted signature) added to a module-level `sink` that the
process prints, as `c/main.c`'s `sink`. The KI probes run after the timing, on the
`schnorr256k1-bench/v1/probe/<i>` keys, signed by the reference. SHA-256 for the derivation and
the digests is `node:crypto`'s (both runtimes have it).

The result is `"schema": 4`, otherwise schema v2's format; `layer` is the runtime.

## Skipped

| Package | Why |
|---|---|
| `secp256k1` 5.0.2 (cryptocoinjs, libsecp256k1 N-API addon) | No BIP-340: ECDSA, ECDH and key tweaks only. |
| `nostr-tools` 2.26.0 | Calls `@noble/curves` (pinned at 2.0.1). Its `nostr-tools/wasm` path is `nostr-wasm`, below. |
| `nostr-wasm` 0.1.0 | libsecp256k1 in WebAssembly (a second binding; tiny-secp256k1 is the one most used), and its API takes whole Nostr events (it computes the id from the JSON) and exposes no ECDH or tweaks, so it cannot take the dataset's id ‖ pubkey ‖ sig. |
| `@bitcoinerlab/secp256k1` 2.0.0 | A tiny-secp256k1-compatible wrapper over `@noble/curves`. |
| `@cmdcode/crypto-tools` 2.8.0 | A wrapper over `@noble/curves` 1.x (license CC-BY-1.0). |
| `@scure/btc-signer`, `bitcoinjs-lib`, `ecpair` | Use `@noble/curves` or take an injected tiny-secp256k1-style library. |
| `@bitcoin-js/tiny-secp256k1-asmjs` 2.2.5 | The same tiny-secp256k1 compiled to asm.js, for runtimes without WebAssembly. |
| `@rust-nostr/nostr-sdk` 0.44.0 | rust-secp256k1 (libsecp256k1) in WebAssembly: a second libsecp256k1 binding, about 400 downloads a week. |
| `@vulpemventures/secp256k1-zkp` 3.2.1 | libsecp256k1-zkp, a fork of libsecp256k1, through Emscripten. |
| `bip-schnorr` 0.6.7 (guggero) | Its README calls it educational ("only be used for educational purposes"), no release since 2023-03, and it runs on `bigi`/`ecurve` at about 20 ms per verify (sign and verify measured here on both runtimes), so the dataset's checks alone would take about 20 minutes per runtime. It does pass BIP-340: its signature matched tiny-secp256k1's. It also has a batch verify with random coefficients. |
| `bcrypto` 5.5.2 (bcoin) | No release since 2023-11. Its default Node backend is a fork of libsecp256k1 built as an addon (a second libsecp256k1); its own implementations (libtorsion, or pure JS) need `BCRYPTO_FORCE_TORSION=1` or `NODE_BACKEND=js`. Its signature matched tiny-secp256k1's in all three backends, on Node and Bun. |
| `elliptic` 6.6.1 | No BIP-340. |
| Node's `crypto`, Bun's | secp256k1 ECDSA through OpenSSL/BoringSSL; no BIP-340. |
