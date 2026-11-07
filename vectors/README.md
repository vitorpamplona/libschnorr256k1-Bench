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
