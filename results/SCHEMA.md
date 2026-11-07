# Results schema, v1

Frozen. Renaming an operation, changing what it measures or how a statistic is computed, or
adding a column, is schema v2. Results with different schema versions or vector digests are
never put in the same table.

## Files

One JSON file per run, `results/<layer>/<yyyy-mm-dd>-<device>.json`:

```json
{
  "schema": 1,
  "vectors": "v1",
  "vectorsSha256": "<SHA-256 of vectors/v1.txt>",
  "layer": "c | jvm | native | android",
  "context": {
    "date": "2026-11-07",
    "device": "Pixel 8 | MacBook Pro M3 | ...",
    "cpu": "...",
    "os": "...",
    "runtime": "e.g. Temurin 21.0.12 / ART API 35 / Kotlin/Native 2.4.21 / clang 18 -O2",
    "machine": "dedicated | shared",
    "harness": "<libschnorr256k1-bench commit>",
    "libraries": { "libsecp256k1": "6e2c8bc", "libschnorr256k1": "2a8798e", "schnorr256k1-kotlin": "0a37aee", "...": "..." }
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
  ]
}
```

## Columns

| Column | Definition |
|---|---|
| `median_ns` | Median over samples of (sample time ÷ operations in the sample). One operation is one call; for `verifyBatch200.perSig`, one call ÷ 200. |
| `p5_ns`, `p95_ns` | 5th and 95th percentile of the same per-sample values (nearest rank). |
| `samples` | Number of samples behind the statistics. A sample is one timed batch of calls lasting at least 100 ms (C), one JMH iteration (JVM, Kotlin/Native) or one Microbenchmark run (Android). |
| `correct` | The library's outputs matched `vectors/v1.txt` (and the direct checks in `vectors/README.md`) before timing. A row with `false` is reported but never compared. |
| `known_issues` | IDs from the table below that affect this operation for this library. |

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

Through a binding (JNI, cinterop), the call is the binding's equivalent, made exactly as an app
would: ACINQ's `Secp256k1` methods, libschnorr256k1-kmp's `Schnorr256k1` methods.

## Known issues

| ID | Library | Issue |
|---|---|---|
| KI-1 | libschnorr256k1, kotlin | `verifyBatch` has no random coefficients: two invalid signatures that cancel are accepted. |
| KI-2 | libschnorr256k1 | `schnorr_sign_xonly` returns invalid signatures for keys whose public key has odd y. Its `sign.knownPub` row is `correct: false`. |
| KI-3 | libschnorr256k1 | `ecmult` shares an x-keyed table cache with verify: after a tweak-mul on the odd-y form of a key, verify rejects valid signatures for its x-only form, and after a verify the odd-y tweak-mul is negated. No row is affected: `tweak.mul` runs on points no verification uses (`tpub33`). |
