# Results schema, v2

Frozen. Renaming an operation, changing what it measures or how a statistic is computed, or
adding a column, is schema v3. Results with different schema versions, vector digests or
datasets are never put in the same table.

v2 (2026-11-07) adds the `nostr.*` operations on the nostr-v1 dataset (`datasets/README.md`) and
the `dataset` and `datasetSha256` fields. No v1 result was ever recorded.

## Files

One JSON file per run, `results/<layer>/<yyyy-mm-dd>-<device>.json` (`-quick` before `.json`
for a `--quick` run), written by `scripts/run-host.sh`, `scripts/run-android.sh` and
`scripts/run-android-c.sh`; `scripts/table.py` renders them as comparison tables.

```json
{
  "schema": 2,
  "vectors": "v1",
  "vectorsSha256": "<SHA-256 of vectors/v1.txt>",
  "dataset": "nostr-v1",
  "datasetSha256": "<SHA-256 of datasets/nostr-v1.bin>",
  "layer": "c | jvm | native | android",
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
      "library": "libsecp256k1 | libschnorr256k1 | kotlin",
      "binding": "c | jni | cinterop | kotlin",
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
| `quick` | `--quick`: 0.2 s warm-up, five samples of at least 20 ms. A smoke run (CI): its numbers are never compared or published. |
| `known_issues_observed` | Per library, whether each known issue reproduced in this run's probes (on keys apart from the vectors, after the timing): `true`, `false`, or `null` when the library lacks the operation. |
| `context.machine` | `dedicated`: nothing else ran (a laptop on AC power, a phone charged, cool and in flight mode). `shared`: anything else, a cloud VM or CI runner included. Only dedicated runs are published. |

Deliberately not in v1, because they are not reliable on every layer (Android in particular):
cycles and instructions per operation (need root on Android), and bytes allocated per operation
(not measurable on ART). Allocation behaviour is tested inside each library instead.

## Operations

Inputs are the vectors (`vectors/README.md`). "Cycling" means call `k` uses entry `k mod N`.

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
