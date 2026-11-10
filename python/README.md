# The python layer

CPython calling the Python BIP-340 implementations: one binding to libsecp256k1 (the reference,
which derives the inputs) and every maintained pure-Python implementation we found. `bench.py` is
the twin of `c/main.c`: the same derivation (`vectors/README.md`), the same digest, BIP-340
vector and dataset checks before any timing, the same sixteen operations on the same inputs, the same timing (1 s
warm-up, then 20 samples of at least 100 ms; `--quick`: 0.2 s and 5 of 20 ms; median and
5th/95th percentile by nearest rank of time ÷ operations, timed with `time.perf_counter_ns()`),
the same known-issue probes after the timing, and the same JSON, with `"schema": 4` and
`"layer": "python"`.

```bash
python/run.sh --quick --device "Cloud VM (4 vCPU)" --machine shared --out results/python/x.json
python/run.sh --device "ThinkPad X1 Gen 11" --machine dedicated --out results/python/x.json
python/run.sh --check          # the cross-check (CI): no timing; exit 1 on a wrong result no known issue explains
```

`run.sh` makes `python/.venv` (gitignored) from `requirements.txt` with `python3.13` (or
`PYTHON`; 3.11 or newer), rebuilding it when the interpreter or the pins change. `--library NAME`
runs one library (plus the reference, for the inputs); `--jobs N` sets the processes for the
checks.

**The checks take most of the run.** Reproducing every digest is 4,096 vector entries and
29,614 dataset events × 2 verifications per library: seconds for coincurve, minutes for the
fast pure-Python libraries, and about two CPU-hours for the BIP-340 reference (80 ms a verify). On a shared 4-vCPU cloud VM the checks of all five took 47 minutes, the
quick timing a few more.
So the checks run first, for every library, in a process pool (every CPU by default); the
pool is closed before the first timing, and timing and the probes run in the one main process,
library after library. `--jobs 1` runs the checks in the main process in c/main.c's order. The
only difference this can make is a cache a check would have warmed before timing: of these
libraries only btclib's `ecc` keeps caches (`lru_cache` tables for fixed points, at most 128),
and the warm-up fills those anyway.

## Libraries

| `library` | `binding` | Version | License | What it is |
|---|---|---|---|---|
| `libsecp256k1` | `cffi` | coincurve 21.0.0 (bundles libsecp256k1 v0.6.0, `0cdc758`, statically linked) | MIT OR Apache-2.0 (coincurve), MIT (libsecp256k1) | The dominant Python binding to bitcoin-core's libsecp256k1 (pynostr signs through it). |
| `btclib-ecc` | `python` | ecc 2026.11.7 (btclib's curve package) | MIT | btclib's pure-Python elliptic-curve library: Jacobian coordinates, GLV, windowed tables, and the only real BIP-340 batch verify here (random coefficients). Its optional accelerators (`btclib-secp256k1`, `gmpy2`) are not installed, and `ECC_NO_LIBSECP256K1=1` plus a check of `is_libsecp256k1_serving()` make sure its Python arithmetic answers. |
| `embit` | `python` | embit 0.8.0, `embit.util.py_secp256k1` | MIT | embit's pure-Python fallback (a libsecp256k1-shaped API over Bitcoin Core's old test-framework `key.py`). embit normally loads a prebuilt libsecp256k1 through ctypes; the adapter calls the pure module directly so that never happens. |
| `secp256k1lab` | `python` | secp256k1lab `7ed1812` (main after v1.0.0), vendored in `vendor/secp256k1lab` | MIT (`vendor/secp256k1lab/COPYING`) | The pure-Python secp256k1 library of BIP-340's authors and libsecp256k1's maintainers (Wuille, Ruffing, Nick, Falbesoner); its `bip340.py` is reference.py rebuilt on its field and group classes. Not on PyPI; vendored at a commit after v1.0.0 because `Scalar.from_bytes_nonzero_checked` (the secret-key check) is not in v1.0.0. |
| `bip340-reference` | `python` | bitcoin/bips `a34f044`, `bip-0340/reference.py`, vendored in `vendor/bip340_reference` | BSD-2-Clause OR MIT OR CC0-1.0 (`vendor/bip340_reference/LICENSE`) | The spec's own code: affine double-and-add with a modular inverse per addition. A baseline, not something apps ship. |

`verifyFast` is the full verify for every library (none has a fast variant), as for
libsecp256k1 in SCHEMA.md. Only `btclib-ecc` has a batch verify, so only it has
`verifyBatch200.perSig` and `nostr.verifyBatchByAuthor` rows. Known-issue probes run where the
library has the calls: KI-1 needs a batch verify, KI-3 a tweak-mul, KI-4 a fast verify (null
otherwise, so KI-4 is always null here); KI-2 is `false` unless `sign.knownPub` is wrong on odd-y
keys only.

### Skipped

| Candidate | Why |
|---|---|
| `secp256k1` (rustyrussell/secp256k1-py 0.14.0, cffi, MIT) | A second cffi binding to libsecp256k1 (one binding per library per layer); coincurve is the dominant one. It does BIP-340 (`schnorr_sign` with a `bip340tag`). python-nostr uses it. |
| `python-secp256k1` (scgbckbone, ctypes, MIT) | Another libsecp256k1 binding. |
| embit's ctypes path, btclib with `btclib-secp256k1` | libsecp256k1 again, through other bindings. |
| `nostr-sdk` (rust-nostr's UniFFI bindings) | Rust's secp256k1 crate over libsecp256k1: a further binding of the same C library, with UniFFI marshalling in between. |
| `pynostr`, `python-nostr`, `monstr` | Nostr clients that sign through coincurve or secp256k1-py. |
| `bitcoin-utils` (`bitcoinutils/schnorr.py`) | "A slightly modified version of" bip-0340/reference.py: a copy of a library already here. |
| `buidl` | Its pure path is its own code, but the last release is 0.2.36 (February 2022): unmaintained. |
| `ecpy`, `pysecp256k1` (Moustikitos), `fastecdsa`, `ecdsa`, `cryptography` | No BIP-340 (ECPy's Schnorr variants are not BIP-340), or no secp256k1 Schnorr at all. |

## The calls

Every call is the library's public API, made the way an app would make it. The harness slices
bytes, prepends `02` for x-only ECDH, and turns an exception into a failed call (as the C
backends return 0); it does no curve or scalar arithmetic. "no row": the library lacks it.

### `libsecp256k1` (coincurve)

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `PublicKeyXOnly(x).verify(sig, msg)` (xonly_pubkey_parse + schnorrsig_verify) |
| `sign` | `PrivateKey(sk).sign_schnorr(msg, aux)`. Constructing `PrivateKey` runs ec_pubkey_create and keypair_create; `sign_schnorr` runs keypair_create, schnorrsig_sign32 and schnorrsig_verify. |
| `sign.knownPub` | the same (coincurve has no signing call that takes the public key) |
| `pubkey.create` | `PublicKey.from_secret(sk).format(compressed=False)` |
| `pubkey.compress` | `PublicKey(pub65).format(compressed=True)` |
| `seckey.verify` | `coincurve.utils.validate_secret(sk)` (coincurve's own range check, in Python: coincurve does not expose ec_seckey_verify) |
| `ecdh.xOnly`, `nostr.ecdh` | `PublicKey(b"\x02" + x).multiply(sk).format()[1:]` (SCHEMA.md's libsecp256k1 composition; coincurve's `PrivateKey.ecdh` is hashed ECDH) |
| `tweak.mul` | `PublicKey(pub33).multiply(tweak).format()` |
| `tweak.add` | `PrivateKey(sk).add(tweak).secret` (seckey_tweak_add, but both `PrivateKey` constructions derive the public keys) |
| batch | no row |

### `btclib-ecc`

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `ssa.verify_(msg, x, sig)` (`verify_` takes the BIP-340 message as is; `verify` hashes it first) |
| `verifyBatch200.perSig`, `nostr.verifyBatchByAuthor` | `ssa.batch_verify_(msgs, [x] * n, [ssa.Sig.parse(s) for s in sigs])` |
| `sign`, `sign.knownPub` | `ssa.sign_(msg, sk, aux).serialize()` (verifies its signature, BIP-340's default signing) |
| `pubkey.create` | `bytes_from_prv_key_int(scalar_from_prv_key(sk), compressed=False)` |
| `pubkey.compress` | `bytes_from_point(point_from_octets_var(pub65), compressed=True)` |
| `seckey.verify` | `scalar_from_prv_key(sk)` |
| `ecdh.xOnly`, `nostr.ecdh` | `bytes_from_point(mult_pub_key(sk, b"\x02" + x))[1:]` |
| `tweak.mul` | `bytes_from_point(mult_pub_key(tweak, pub33))` |
| `tweak.add` | `prv_key_tweak_add(sk, tweak).to_bytes(32, "big")` (`curves.prv_key_tweak_add`, not ssa's x-only one) |

### `embit` (`embit.util.py_secp256k1` as `e`)

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `e.schnorrsig_verify(sig, msg, e.ec_pubkey_parse(b"\x02" + x))` (no xonly_pubkey_parse; embit.ec parses 02 ‖ x the same way) |
| `sign`, `sign.knownPub` | `e.schnorrsig_sign(msg, e.keypair_create(sk), None, aux)` (embit.ec's `schnorr_sign` takes no aux) |
| `pubkey.create` | `e.ec_pubkey_serialize(e.ec_pubkey_create(sk), e.EC_UNCOMPRESSED)` |
| `pubkey.compress` | `e.ec_pubkey_serialize(e.ec_pubkey_parse(pub65), e.EC_COMPRESSED)` |
| `seckey.verify` | `e.ec_seckey_verify(sk)` |
| `tweak.add` | `e.ec_privkey_tweak_add(bytearray(sk), tweak)` (in place, on a copy) |
| `ecdh.xOnly`, `tweak.mul`, `nostr.ecdh` | no row: the pure module has no ec_pubkey_tweak_mul (commented out) and no ecdh |
| batch | no row |

### `secp256k1lab`

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `bip340.schnorr_verify(msg, x, sig)` |
| `sign`, `sign.knownPub` | `bip340.schnorr_sign(msg, sk, aux)` (asserts its signature verifies) |
| `pubkey.create` | `(Scalar.from_bytes_nonzero_checked(sk) * G).to_bytes_uncompressed()` |
| `pubkey.compress` | `GE.from_bytes_uncompressed(pub65).to_bytes_compressed()` |
| `seckey.verify` | `Scalar.from_bytes_nonzero_checked(sk)` |
| `ecdh.xOnly`, `nostr.ecdh` | `ecdh.ecdh_compressed_in_raw_out(sk, b"\x02" + x).to_bytes_xonly()` |
| `tweak.mul` | `(Scalar.from_bytes_nonzero_checked(tweak) * GE.from_bytes_compressed(pub33)).to_bytes_compressed()` |
| `tweak.add` | `(Scalar.from_bytes_nonzero_checked(sk) + Scalar.from_bytes_checked(tweak)).to_bytes()` |
| batch | no row (`GE.batch_mul` is a multi-scalar multiplication, not a batch verify) |

### `bip340-reference` (`reference` as `r`)

| Op | Call |
|---|---|
| `verify.*`, `verifyFast.*`, `nostr.verify*` | `r.schnorr_verify(msg, x, sig)` |
| `sign`, `sign.knownPub` | `r.schnorr_sign(msg, sk, aux)` (verifies its signature) |
| `pubkey.create` | `P = r.point_mul(r.G, r.int_from_bytes(sk))`, then `04 ‖ r.bytes_from_int(r.x(P)) ‖ r.bytes_from_int(r.y(P))` |
| `ecdh.xOnly`, `nostr.ecdh` | `r.bytes_from_point(r.point_mul(r.lift_x(r.int_from_bytes(x)), r.int_from_bytes(sk)))` |
| `pubkey.compress`, `seckey.verify`, `tweak.mul`, `tweak.add` | no row: the reference has no point parsing (lift_x gives only the even-y point, so a 03 key would need a negation in the harness), no compression, no key check and no scalar addition |
| batch | no row |

## Files

- `bench.py`: the runner (vectors, dataset, checks, timing, probes, JSON).
- `impls.py`: one adapter per library.
- `requirements.txt`: the PyPI pins. `vendor/`: the two libraries that are not on PyPI at what
  the bench calls, each with its license file, unmodified.
