# Results schema, v3

Frozen. Renaming an operation, changing what it measures or how a statistic is computed, or
adding a column, is schema v4. Results with different schema versions, vector digests or
datasets are never put in the same table.

v3 (2026-11-09) adds the `rust`, `go`, `node`, `bun` and `python` layers, and libraries beyond
the first three on them and on `jvm` (Layers and libraries, below). The operations, inputs,
columns and statistics are v2's. No v2 result was ever recorded.

v2 (2026-11-07) added the `nostr.*` operations on the nostr-v1 dataset (`datasets/README.md`) and
the `dataset` and `datasetSha256` fields. No v1 result was ever recorded.

## Files

One JSON file per run, `results/<layer>/<yyyy-mm-dd>-<device>.json` (`-quick` before `.json`
for a `--quick` run), written by `scripts/run-host.sh`, `scripts/run-android.sh` and
`scripts/run-android-c.sh`; `scripts/table.py` renders them as comparison tables.

```json
{
  "schema": 3,
  "vectors": "v1",
  "vectorsSha256": "<SHA-256 of vectors/v1.txt>",
  "dataset": "nostr-v1",
  "datasetSha256": "<SHA-256 of datasets/nostr-v1.bin>",
  "layer": "c | jvm | native | android | rust | go | node | bun | python",
  "quick": false,
  "context": {
    "date": "2026-11-07",
    "device": "Pixel 8 | MacBook Pro M3 | ...",
    "cpu": "...",
    "os": "...",
    "runtime": "e.g. OpenJDK 64-Bit Server VM 21.0.12 / ART 2.1.0 / Kotlin/Native 2.4.21 / C, 13.3.0, ...",
    "machine": "dedicated | shared",
    "harness": "<libschnorr256k1-bench commit>[-dirty]",
    "libraries": { "libsecp256k1": "6e2c8bc | secp256k1-kmp 0.24.0", "libschnorr256k1": "2a8798e | schnorr256k1-kmp 1.0.5", "kotlin": "schnorr256k1-kotlin 0a37aee" }
  },
  "rows": [
    {
      "op": "verify.sameKey",
      "library": "libsecp256k1 | libschnorr256k1 | kotlin | k256 | btcec | noble-curves | ...",
      "binding": "c | jni | cinterop | kotlin | wasm | cgo | rust-secp256k1 | cffi | java | rust | go | js | python | ...",
      "median_ns": 38400,
      "p5_ns": 37100,
      "p95_ns": 41900,
      "samples": 20,
      "correct": true,
      "known_issues": []
    }
  ],
  "known_issues_observed": {
    "libschnorr256k1": { "KI-1": true, "KI-2": true, "KI-3": true },
    "libsecp256k1": { "KI-1": null, "KI-2": false, "KI-3": false }
  }
}
```

## Layers and libraries

`library` names the implementation, `binding` how the layer reaches it: for a binding, the
binding package or mechanism; for a pure implementation, its language. Every binding to
bitcoin-core's libsecp256k1 is `libsecp256k1`, one per layer, so each layer's ratios are against
the reference as that ecosystem calls it. Each layer's README gives every library's exact call
for every operation, and what it lacks.

| Layer | Runner | Libraries (`library` / `binding`) |
|---|---|---|
| `c` | `c/main.c` | libsecp256k1 / c, libschnorr256k1 / c |
| `jvm` | `bench/` (`Runner.kt`) | libsecp256k1 / jni (ACINQ), libschnorr256k1 / jni, kotlin / kotlin, bouncycastle / java, bitcoin-s / scala, nostr-java / java ([bench/README-jvm-libraries.md](../bench/README-jvm-libraries.md)) |
| `native` | `bench/` | libsecp256k1 / cinterop, libschnorr256k1 / cinterop (Apple only), kotlin / kotlin |
| `android` | `bench-android/` | as `jvm`'s first three |
| `rust` | `rust/` ([README](../rust/README.md)) | libsecp256k1 / rust-secp256k1, k256 / rust, secp256kfun / rust |
| `go` | `go/` ([README](../go/README.md)) | libsecp256k1 / cgo, btcec / go, p256k1 / go, secp256k1-voi / go, splitsecure / go |
| `node`, `bun` | `js/` ([README](../js/README.md)) | libsecp256k1 / wasm (tiny-secp256k1), noble-curves / js, noble-secp256k1 / js |
| `python` | `python/` ([README](../python/README.md)) | libsecp256k1 / cffi (coincurve), btclib-ecc / python, embit / python, secp256k1lab / python, bip340-reference / python (BIP-340's own reference.py) |

Every runner is a twin of `c/main.c`: the same derivation, checks, operations, inputs, timing,
probes and JSON. Where a library lacks an operation, its row is composed only from that
library's own public calls (its README says how), or it has no row; the harness never does
curve or scalar arithmetic for a library. The derived inputs (`pub65`, `sig`, ...) come from the
layer's libsecp256k1, and every library's outputs are checked against the digests before timing.
A layer may run each library in its own process (the JIT runtimes `node` and `bun` do, so one
library's compiled code and heap do not shape another's numbers).

A layer has a row for every operation each of its libraries has (a library without a fast or
batch verify has no row for it), and a `libraries` entry for each library it ran: the C
libraries' commits on the `c` layer, the binding's version through a binding, the submodule's
commit (`-dirty` with local changes) for the pure-Kotlin library.

## Columns

| Column | Definition |
|---|---|
| `median_ns` | Median over samples of (sample time ÷ operations in the sample). One operation is one call; for `verifyBatch200.perSig`, one call ÷ 200. |
| `p5_ns`, `p95_ns` | 5th and 95th percentile of the same per-sample values (nearest rank). |
| `samples` | Number of samples behind the statistics. A sample is one timed batch of calls lasting at least 100 ms, on every layer: one runner method (`c/main.c`, and its twin `bench/.../Runner.kt` for the Kotlin layers) after 1 s of warm-up, 20 samples. No JMH or Jetpack Microbenchmark, so no layer measures differently. |
| `correct` | The library's outputs matched `vectors/v1.txt` (and the direct checks in `vectors/README.md`) before timing. A row with `false` is reported but never compared. |
| `known_issues` | IDs from the table below that affect this operation for this library, as observed in this run. |

Run-level fields:

| Field | Definition |
|---|---|
| `quick` | `--quick`: 0.2 s warm-up, five samples of at least 20 ms. A smoke run: its numbers are never compared or published. (CI times nothing; it runs only the checks.) |
| `known_issues_observed` | Per library, whether each known issue reproduced in this run's probes (on keys apart from the vectors, after the timing): `true`, `false`, or `null` when the library lacks the operation. |
| `context.machine` | `dedicated`: nothing else ran (a laptop on AC power, a phone charged, cool and in flight mode). `shared`: anything else, a cloud VM or CI runner included. Dedicated runs are what gets published; a full shared run stands in, labelled, until there is one. Quick runs never are. |

Deliberately not in v1, because they are not reliable on every layer (Android in particular):
cycles and instructions per operation (need root on Android), and bytes allocated per operation
(not measurable on ART). Allocation behaviour is tested inside each library instead.

## Operations

Inputs are the vectors (`vectors/README.md`). "Cycling" means call `k` uses entry `k mod N`.
The calls below are the first three libraries'; the others' are in their layers' READMEs.

| Op | Input | libsecp256k1 call | libschnorr256k1 call | Kotlin call |
|---|---|---|---|---|
| `verify.sameKey` | entry 0 every call | `xonly_pubkey_parse` + `schnorrsig_verify` | `schnorr_verify` | `schnorrVerify` |
| `verify.manyKeys` | cycling | same | same | same |
| `verifyFast.sameKey` | entry 0 | full verify (no fast variant) | `schnorr_verify_fast` | `schnorrVerifyFast` |
| `verifyBatch200.perSig` | `batchSig[0..199]`, key 0 | n/a | `schnorr_verify_batch` | `schnorrVerifyBatch` |
| `sign` | cycling | `keypair_create` + `schnorrsig_sign32` | `schnorr_sign` | `schnorrSign` |
| `sign.knownPub` | cycling | same as `sign` (always derives the key pair) | `schnorr_sign_xonly` (KI-2) | `schnorrSignWithPubKey` (33-byte key) |
| `pubkey.create` | cycling | `ec_pubkey_create` + serialize (65) | `pubkey_create` | `pubkeyCreate` |
| `pubkey.compress` | cycling | `ec_pubkey_parse` (65) + serialize (33) | `pubkey_compress` | `pubkeyCompress` |
| `seckey.verify` | cycling | `ec_seckey_verify` | `seckey_verify` | `seckeyVerify` |
| `ecdh.xOnly` | cycling, peer `(i + 1) mod N` | parse `02 ‖ x` + `ec_pubkey_tweak_mul` + serialize, take x (as Quartz does through ACINQ) | `ecdh_xonly` | `ecdhXOnly` |
| `tweak.mul` | cycling: `tpub33[i]` times `seckey[i]` | `ec_pubkey_parse` + `ec_pubkey_tweak_mul` + serialize (33) | `pubkey_tweak_mul` | `pubkeyTweakMul` |
| `tweak.add` | cycling | `ec_seckey_tweak_add` on a copy | `privkey_tweak_add` | `privkeyTweakAdd` |
| `nostr.verify` | dataset, cycling through the events in corpus order | as `verify.sameKey` | same | same |
| `nostr.verifyFast` | same | full verify | `schnorr_verify_fast` | `schnorrVerifyFast` |
| `nostr.verifyBatchByAuthor` | dataset pages of 200 events; each page's events grouped by author; per event | n/a | `schnorr_verify_batch` per group of 2 or more, `schnorr_verify` for the rest | `schnorrVerifyBatch` / `schnorrVerify` likewise |
| `nostr.ecdh` | the dataset's 4,926 distinct pubkeys, cycling; key `d` with `seckey[d mod N]` | as `ecdh.xOnly` | `ecdh_xonly` | `ecdhXOnly` |

The `nostr.*` operations are what a client does with what relays send: the corpus mixes a few
prolific authors with thousands seen once (83% of its events are by an author seen earlier in
the stream), which neither `verify.sameKey` (one key) nor `verify.manyKeys` (4096 keys in strict
rotation) shows. Their correctness: every signature verifies and every one with the last byte of
s incremented does not (`nostr.verify`, `nostr.verify.tampered` in `datasets/nostr-v1.txt`),
every author group passes, and the ECDH outputs hash to `nostr.ecdh`.

Through a binding (JNI, cinterop), the call is the binding's equivalent, made exactly as an app
would: ACINQ's `Secp256k1` methods, libschnorr256k1-kmp's `Schnorr256k1` methods.

## Known issues

| ID | Library | Issue |
|---|---|---|
| KI-1 | libschnorr256k1, kotlin (fixed in libschnorr256k1-kotlin `a7d9cba`) | `verifyBatch` has no random coefficients: two invalid signatures that cancel are accepted. It marks `verifyBatch200.perSig` and `nostr.verifyBatchByAuthor`, whose times are then not comparable with a weighted batch. |
| KI-2 | libschnorr256k1 | `schnorr_sign_xonly` returns invalid signatures for keys whose public key has odd y. Its `sign.knownPub` row is `correct: false`. |
| KI-3 | libschnorr256k1 | `ecmult` shares an x-keyed table cache with verify: after a tweak-mul on the odd-y form of a key, verify rejects valid signatures for its x-only form, and after a verify the odd-y tweak-mul is negated. No row is affected: `tweak.mul` runs on points no verification uses (`tpub33`). |
