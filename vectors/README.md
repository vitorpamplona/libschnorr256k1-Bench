# Input vectors, v1

Every layer (C, JVM, Kotlin/Native, Android) builds the same inputs from this rule, so nothing
large is checked in and no layer needs file I/O. `v1.txt` holds only the SHA-256 of each
operation's outputs over all inputs, computed with libsecp256k1; every layer recomputes them with
the library under test and compares **before timing anything**.

This file is frozen: changing a rule, a label or N is a new version (`v2`), with new digests.

## Derivation

`H(s)` is SHA-256 of the ASCII string `s`. `i` is written in decimal, no padding.

| Value | Rule |
|---|---|
| `N` | 4096 (more than the 1024-entry per-key caches in libschnorr256k1 and the Kotlin library) |
| `seckey[i]` | `h = H("schnorr256k1-bench/v1/seckey/" + i)`; while `h` is 0 or ≥ n (the group order), `h = SHA-256(h)` |
| `tweak[i]` | the same, with label `.../v1/tweak/` |
| `msg[i]` | `H("schnorr256k1-bench/v1/msg/" + i)` (32 bytes) |
| `aux[i]` | `H("schnorr256k1-bench/v1/aux/" + i)` (32 bytes) |

Derived with the reference library (libsecp256k1) at setup, in every layer:

| Value | Definition |
|---|---|
| `pub65[i]` | uncompressed public key of `seckey[i]` (`04 ‖ x ‖ y`) |
| `pub33[i]` | compressed (`02`/`03 ‖ x`) |
| `xonly[i]` | `pub33[i][1..33]` |
| `tpub33[i]` | compressed public key of `tweak[i]` (the points `tweak.mul` multiplies; no verification uses them, so a per-x cache shared by verify and tweak-mul (KI-3) cannot change the result) |
| `sig[i]` | BIP-340 signature of `msg[i]` by `seckey[i]` with `aux[i]` |
| `batchSig[j]`, j < 200 | BIP-340 signature of `msg[j]` by `seckey[0]` with `aux[j]` |

## Digests (`v1.txt`)

One line per check: `<check> <sha256-hex>`, SHA-256 over the outputs for i = 0 … N-1 concatenated.

| Check | Output per i |
|---|---|
| `pubkey.create` | `pub65[i]` |
| `pubkey.compress` | `pub33[i]` |
| `sign` | `sig[i]` |
| `ecdh.xOnly` | x coordinate of `seckey[i] · lift_x(xonly[(i + 1) mod N])` (32 bytes) |
| `tweak.mul` | `seckey[i] · tpub33[i]`, compressed (33 bytes) |
| `tweak.add` | `(seckey[i] + tweak[i]) mod n` (32 bytes) |
| `verify` | one byte per i: `01` if `sig[i]` verifies for `msg[i]`, `xonly[i]`, else `00` |
| `verify.tampered` | one byte per i: the same with the last byte of `sig[i]` incremented (must be all `00`) |

`sign.knownPub`, `verifyFast` and the batch have no digest of their own: a correct
`sign.knownPub` equals `sign`, and `verifyFast` and the batch must accept every valid input. Each
layer checks those directly.

## BIP-340 vectors (`bip340/`)

`bip340/test-vectors.csv` is BIP-340's own test vectors, verbatim from bitcoin/bips at `a34f044`
(`bip-0340/test-vectors.csv`, the commit `python/vendor/bip340_reference` comes from), under its
`LICENSE` (BSD-2-Clause OR MIT OR CC0-1.0). Where v1's inputs are all valid, these are mostly the
edge cases: a public key not on the curve (5) or not below p (14), R with odd y (6), negated
message or s (7, 8), R at infinity (9, 10), R's x not on the curve (11) or not below p (12), s not
below n (13), and the key and nonce edge cases of the signing rows (0 to 3). Each runner reads
the file beside the vectors file (`<dir of v1.txt>/bip340/test-vectors.csv`) and uses the rows
whose message is 32 bytes, the size every operation here takes: rows 0 to 14 (15 to 18 sign
messages of 0, 1, 17 and 100 bytes). A result records the file's SHA-256 as `bip340Sha256`.

For each row:

| Check | Must hold |
|---|---|
| `sign` | on a row with a secret key: `sign(msg, seckey, aux)` is the row's signature |
| `sign.knownPub` | on a row with a secret key: the same, with the public key from the layer's libsecp256k1 |
| `verify` | `verify(sig, msg, pubkey)` is the row's result, `TRUE` or `FALSE` |
| `verifyFast` | the same, for a library with a fast verify |

And for a library with a batch verify: `batchSig[0..199]` with signature 0, then with signature
199, given the last byte incremented, must be rejected.

A call that throws (or panics) on an input the vectors expect rejected counts as rejecting it; on
an input they expect accepted, as a wrong result. Every runner logs each vector a library gets
wrong, by its index.

Vector 6 is the one KI-4 shows on (`results/SCHEMA.md`): a fast verify that skips the even-y
check of R accepts it.
