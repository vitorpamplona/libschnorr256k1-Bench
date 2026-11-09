# Copyright (c) 2025 Vitor Pamplona
"""The libraries under test, one adapter each: the twin of c/backend.h.

Every method takes and returns bytes in the formats of results/SCHEMA.md (32-byte messages,
33/65-byte SEC keys, 32-byte x-only keys, 64-byte signatures). A failed call (invalid input, a
library exception) returns None, or False for the boolean ones, as the C backends return 0. An
operation a library lacks is None on the adapter: it gets no row (and no digest check).

Each call is the library's own public API, made the way an app would make it; the harness only
copies and slices bytes around it. Nothing here does curve or scalar arithmetic.
"""

from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
VENDOR = os.path.join(HERE, "vendor")
for p in (os.path.join(VENDOR, "bip340_reference"), VENDOR):
    if p not in sys.path:
        sys.path.insert(0, p)

# btclib's ecc delegates to libsecp256k1 when the optional btclib-secp256k1 bindings are
# installed; they are not, and this makes sure its pure-Python arithmetic answers anyway.
os.environ["ECC_NO_LIBSECP256K1"] = "1"


class Impl:
    library = ""
    binding = ""
    version = ""

    # Optional operations: None when the library lacks them.
    pubkey_create = None  # (seckey) -> pub65
    pubkey_compress = None  # (pub65) -> pub33
    seckey_verify = None  # (seckey) -> bool
    sign = None  # (msg, seckey, aux) -> sig
    sign_known_pub = None  # (msg, seckey, pub33, xonly, aux) -> sig
    verify = None  # (sig, msg, xonly) -> bool
    verify_fast = None  # no library here has one: the full verify is timed instead
    verify_batch = None  # (xonly, sigs, msgs) -> bool
    ecdh_xonly = None  # (xonly, seckey) -> x (32)
    tweak_mul = None  # (pub33, tweak) -> pub33
    tweak_add = None  # (seckey, tweak) -> seckey


# ---------------------------------------------------------------- libsecp256k1 through coincurve


class Coincurve(Impl):
    """libsecp256k1 (bundled, static) through coincurve's cffi binding, the dominant one on PyPI.

    The calls are coincurve's classes, as an app makes them: PrivateKey derives its public
    keys when it is constructed, and sign_schnorr verifies the signature it made.
    """

    library = "libsecp256k1"
    binding = "cffi"

    def __init__(self):
        import coincurve
        from coincurve import PrivateKey, PublicKey, PublicKeyXOnly
        from coincurve.utils import validate_secret

        # coincurve 21.0.0's pyproject.toml pins VENDORED_UPSTREAM_REF to this commit (v0.6.0).
        self.version = f"coincurve {coincurve.__version__} (libsecp256k1 v0.6.0, 0cdc758)"

        def pubkey_create(sk):
            try:
                return PublicKey.from_secret(sk).format(compressed=False)
            except ValueError:
                return None

        def pubkey_compress(pub65):
            try:
                return PublicKey(pub65).format(compressed=True)
            except ValueError:
                return None

        def seckey_verify(sk):
            try:
                validate_secret(sk)
                return True
            except ValueError:
                return False

        def sign(msg, sk, aux):
            try:
                return PrivateKey(sk).sign_schnorr(msg, aux)
            except ValueError:
                return None

        def sign_known_pub(msg, sk, pub33, xonly, aux):
            # coincurve has no signing call that takes the public key: PrivateKey derives it.
            try:
                return PrivateKey(sk).sign_schnorr(msg, aux)
            except ValueError:
                return None

        def verify(sig, msg, xonly):
            try:
                return PublicKeyXOnly(xonly).verify(sig, msg)
            except ValueError:
                return False

        def ecdh_xonly(xonly, sk):
            # As SCHEMA.md's libsecp256k1 x-only ECDH: parse 02 || x, tweak-mul, serialize, take x.
            try:
                return PublicKey(b"\x02" + xonly).multiply(sk).format()[1:]
            except ValueError:
                return None

        def tweak_mul(pub33, tweak):
            try:
                return PublicKey(pub33).multiply(tweak).format()
            except ValueError:
                return None

        def tweak_add(sk, tweak):
            try:
                return PrivateKey(sk).add(tweak).secret
            except ValueError:
                return None

        self.pubkey_create = pubkey_create
        self.pubkey_compress = pubkey_compress
        self.seckey_verify = seckey_verify
        self.sign = sign
        self.sign_known_pub = sign_known_pub
        self.verify = verify
        self.ecdh_xonly = ecdh_xonly
        self.tweak_mul = tweak_mul
        self.tweak_add = tweak_add


# ---------------------------------------------------------------- btclib's ecc (pure Python)


class BtclibEcc(Impl):
    """btclib's `ecc` package, its pure-Python arithmetic (no btclib-secp256k1, no gmpy2)."""

    library = "btclib-ecc"
    binding = "python"

    def __init__(self):
        import ecc
        from ecc.curves import (
            bytes_from_point,
            bytes_from_prv_key_int,
            is_libsecp256k1_serving,
            mult_pub_key,
            point_from_octets_var,
            prv_key_tweak_add,
            scalar_from_prv_key,
        )
        from ecc.ecc import ssa

        if is_libsecp256k1_serving():
            raise RuntimeError("btclib's ecc is delegating to libsecp256k1, not its Python arithmetic")
        self.version = f"ecc {ecc.__version__}"
        Sig = ssa.Sig
        errors = (ValueError, ArithmeticError, TypeError, RuntimeError)

        def pubkey_create(sk):
            try:
                return bytes_from_prv_key_int(scalar_from_prv_key(sk), compressed=False)
            except errors:
                return None

        def pubkey_compress(pub65):
            try:
                return bytes_from_point(point_from_octets_var(pub65), compressed=True)
            except errors:
                return None

        def seckey_verify(sk):
            try:
                scalar_from_prv_key(sk)
                return True
            except errors:
                return False

        def sign(msg, sk, aux):
            # sign_ takes the BIP-340 message as it is (sign hashes it first).
            try:
                return ssa.sign_(msg, sk, aux).serialize()
            except errors:
                return None

        def sign_known_pub(msg, sk, pub33, xonly, aux):
            # ssa has no signing call that takes the public key.
            try:
                return ssa.sign_(msg, sk, aux).serialize()
            except errors:
                return None

        def verify(sig, msg, xonly):
            try:
                return ssa.verify_(msg, xonly, sig)
            except errors:
                return False

        def verify_batch(xonly, sigs, msgs):
            try:
                return ssa.batch_verify_(msgs, [xonly] * len(sigs), [Sig.parse(s) for s in sigs])
            except errors:
                return False

        def ecdh_xonly(xonly, sk):
            try:
                return bytes_from_point(mult_pub_key(sk, b"\x02" + xonly))[1:]
            except errors:
                return None

        def tweak_mul(pub33, tweak):
            try:
                return bytes_from_point(mult_pub_key(tweak, pub33))
            except errors:
                return None

        def tweak_add(sk, tweak):
            try:
                return prv_key_tweak_add(sk, tweak).to_bytes(32, "big")
            except errors:
                return None

        self.pubkey_create = pubkey_create
        self.pubkey_compress = pubkey_compress
        self.seckey_verify = seckey_verify
        self.sign = sign
        self.sign_known_pub = sign_known_pub
        self.verify = verify
        self.verify_batch = verify_batch
        self.ecdh_xonly = ecdh_xonly
        self.tweak_mul = tweak_mul
        self.tweak_add = tweak_add


# ---------------------------------------------------------------- embit's pure-Python fallback


class EmbitPure(Impl):
    """embit's pure-Python secp256k1 (embit.util.py_secp256k1, over Bitcoin Core's old
    test-framework key.py), called directly so its ctypes libsecp256k1 is never loaded.

    Its module mirrors libsecp256k1's API, so the calls are libsecp256k1's compositions. It has
    no ec_pubkey_tweak_mul and no ecdh (both are commented out or absent in the pure module):
    no ecdh.xOnly, tweak.mul or nostr.ecdh rows.
    """

    library = "embit"
    binding = "python"

    def __init__(self):
        from importlib.metadata import version

        from embit.util import py_secp256k1 as e

        self.version = f"embit {version('embit')} (embit.util.py_secp256k1)"
        errors = (ValueError, ArithmeticError, TypeError, AssertionError)
        UNCOMPRESSED, COMPRESSED = e.EC_UNCOMPRESSED, e.EC_COMPRESSED

        def pubkey_create(sk):
            try:
                return e.ec_pubkey_serialize(e.ec_pubkey_create(sk), UNCOMPRESSED)
            except errors:
                return None

        def pubkey_compress(pub65):
            try:
                return e.ec_pubkey_serialize(e.ec_pubkey_parse(pub65), COMPRESSED)
            except errors:
                return None

        def seckey_verify(sk):
            try:
                return bool(e.ec_seckey_verify(sk))
            except errors:
                return False

        def sign(msg, sk, aux):
            try:
                return e.schnorrsig_sign(msg, e.keypair_create(sk), None, aux)
            except errors:
                return None

        def sign_known_pub(msg, sk, pub33, xonly, aux):
            # Its keypair holds the public key, but schnorrsig_sign derives it again anyway.
            try:
                return e.schnorrsig_sign(msg, e.keypair_create(sk), None, aux)
            except errors:
                return None

        def verify(sig, msg, xonly):
            # The pure module has no xonly_pubkey_parse: embit.ec parses 02 || x the same way.
            try:
                return bool(e.schnorrsig_verify(sig, msg, e.ec_pubkey_parse(b"\x02" + xonly)))
            except errors:
                return False

        def tweak_add(sk, tweak):
            try:
                out = bytearray(sk)
                e.ec_privkey_tweak_add(out, tweak)
                return bytes(out)
            except errors:
                return None

        self.pubkey_create = pubkey_create
        self.pubkey_compress = pubkey_compress
        self.seckey_verify = seckey_verify
        self.sign = sign
        self.sign_known_pub = sign_known_pub
        self.verify = verify
        self.tweak_add = tweak_add


# ---------------------------------------------------------------- secp256k1lab (vendored)


class Secp256k1Lab(Impl):
    """secp256k1lab, the pure-Python secp256k1 of the BIP authors (vendor/secp256k1lab)."""

    library = "secp256k1lab"
    binding = "python"
    COMMIT = "7ed1812"

    def __init__(self):
        from secp256k1lab import bip340
        from secp256k1lab.ecdh import ecdh_compressed_in_raw_out
        from secp256k1lab.secp256k1 import GE, G, Scalar

        self.version = f"secp256k1lab {self.COMMIT} (vendored)"
        errors = (ValueError, ArithmeticError, TypeError, AssertionError)
        nonzero = Scalar.from_bytes_nonzero_checked
        checked = Scalar.from_bytes_checked

        def pubkey_create(sk):
            try:
                return (nonzero(sk) * G).to_bytes_uncompressed()
            except errors:
                return None

        def pubkey_compress(pub65):
            try:
                return GE.from_bytes_uncompressed(pub65).to_bytes_compressed()
            except errors:
                return None

        def seckey_verify(sk):
            try:
                nonzero(sk)
                return True
            except errors:
                return False

        def sign(msg, sk, aux):
            try:
                return bip340.schnorr_sign(msg, sk, aux)
            except errors:
                return None

        def sign_known_pub(msg, sk, pub33, xonly, aux):
            try:
                return bip340.schnorr_sign(msg, sk, aux)
            except errors:
                return None

        def verify(sig, msg, xonly):
            try:
                return bip340.schnorr_verify(msg, xonly, sig)
            except errors:
                return False

        def ecdh_xonly(xonly, sk):
            try:
                return ecdh_compressed_in_raw_out(sk, b"\x02" + xonly).to_bytes_xonly()
            except errors:
                return None

        def tweak_mul(pub33, tweak):
            try:
                return (nonzero(tweak) * GE.from_bytes_compressed(pub33)).to_bytes_compressed()
            except errors:
                return None

        def tweak_add(sk, tweak):
            try:
                return (nonzero(sk) + checked(tweak)).to_bytes()
            except errors:
                return None

        self.pubkey_create = pubkey_create
        self.pubkey_compress = pubkey_compress
        self.seckey_verify = seckey_verify
        self.sign = sign
        self.sign_known_pub = sign_known_pub
        self.verify = verify
        self.ecdh_xonly = ecdh_xonly
        self.tweak_mul = tweak_mul
        self.tweak_add = tweak_add


# ---------------------------------------------------------------- the BIP-340 reference (vendored)


class Bip340Reference(Impl):
    """bip-0340/reference.py from bitcoin/bips (vendor/bip340_reference): the spec's own code.

    Its functions are the BIP's notation (point_mul, lift_x, bytes(P), ...). It has no point
    parsing, compression, secret-key check or scalar addition, so no pubkey.compress,
    seckey.verify, tweak.mul or tweak.add rows.
    """

    library = "bip340-reference"
    binding = "python"
    COMMIT = "a34f044"

    def __init__(self):
        import reference as r

        self.version = f"bips {self.COMMIT} bip-0340/reference.py (vendored)"
        errors = (ValueError, ArithmeticError, TypeError, AssertionError, RuntimeError)
        G, point_mul, lift_x, x, y = r.G, r.point_mul, r.lift_x, r.x, r.y
        bytes_from_int, bytes_from_point, int_from_bytes = r.bytes_from_int, r.bytes_from_point, r.int_from_bytes

        def pubkey_create(sk):
            try:
                P = point_mul(G, int_from_bytes(sk))
                return b"\x04" + bytes_from_int(x(P)) + bytes_from_int(y(P))
            except errors:
                return None

        def sign(msg, sk, aux):
            try:
                return r.schnorr_sign(msg, sk, aux)
            except errors:
                return None

        def sign_known_pub(msg, sk, pub33, xonly, aux):
            try:
                return r.schnorr_sign(msg, sk, aux)
            except errors:
                return None

        def verify(sig, msg, xonly):
            try:
                return r.schnorr_verify(msg, xonly, sig)
            except errors:
                return False

        def ecdh_xonly(xonly, sk):
            try:
                return bytes_from_point(point_mul(lift_x(int_from_bytes(xonly)), int_from_bytes(sk)))
            except errors:
                return None

        self.pubkey_create = pubkey_create
        self.sign = sign
        self.sign_known_pub = sign_known_pub
        self.verify = verify
        self.ecdh_xonly = ecdh_xonly


# The first is the reference that derives the inputs (vectors/README.md).
ALL = [Coincurve, BtclibEcc, EmbitPure, Secp256k1Lab, Bip340Reference]


LIBRARIES = [cls.library for cls in ALL]


def build(only=None):
    """The reference, and every other library (or only [only])."""
    return [cls() for cls in ALL if cls is ALL[0] or only is None or cls.library == only]
