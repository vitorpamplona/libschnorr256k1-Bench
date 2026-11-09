# Copyright (c) 2025 Vitor Pamplona
"""The python layer: the twin of c/main.c for the Python libraries in impls.py.

  bench.py run   [--quick] [--library NAME] [--device TEXT] [--machine dedicated|shared]
                 [--harness COMMIT] [--jobs N]
      check every library against vectors/v1.txt and datasets/nostr-v1.txt, time every
      operation, probe the known issues, print a schema-3 result (results/SCHEMA.md) as JSON
  bench.py check [--library NAME] [--jobs N]
      the checks and probes without the timing; exits 1 on a wrong result that no observed
      known issue explains (the cross-check for CI)
  bench.py vectors
      print the v1 digests from the reference (libsecp256k1 through coincurve)

The checks are the expensive part for the pure-Python libraries (hours of CPU for the BIP-340
reference), so they run in a process pool (--jobs, default: every CPU) before anything is
timed; the pool is closed before the first timing. --jobs 1 runs them in this process, in
c/main.c's order. Timing, and the probes after it, always run in this one process.
"""

from __future__ import annotations

import hashlib
import json
import os
import platform
import sys
import time
from datetime import datetime, timezone

import impls

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
VECTORS_TXT = os.path.join(ROOT, "vectors", "v1.txt")
DATASET_BIN = os.path.join(ROOT, "datasets", "nostr-v1.bin")
DATASET_TXT = os.path.join(ROOT, "datasets", "nostr-v1.txt")

N = 4096
BATCH = 200
RECORD = 128
PAGE = 200
ORDER = bytes.fromhex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141")

sink = 0


def log(s):
    print(s, file=sys.stderr, flush=True)


def die(what):
    log(f"error: {what}")
    sys.exit(2)


def sha256(b):
    return hashlib.sha256(b).digest()


# ---------------------------------------------------------------- vectors (vectors/README.md)


class Vectors:
    def __init__(self, ref):
        def label(field, i):
            return sha256(f"schnorr256k1-bench/v1/{field}/{i}".encode())

        def scalar(field, i):
            h = label(field, i)
            while h == bytes(32) or h >= ORDER:
                h = sha256(h)
            return h

        self.seckey = [scalar("seckey", i) for i in range(N)]
        self.tweak = [scalar("tweak", i) for i in range(N)]
        self.msg = [label("msg", i) for i in range(N)]
        self.aux = [label("aux", i) for i in range(N)]
        # derived with the reference library
        self.pub65, self.pub33, self.xonly, self.tpub33, self.sig = [], [], [], [], []
        for i in range(N):
            p65 = ref.pubkey_create(self.seckey[i])
            p33 = ref.pubkey_compress(p65) if p65 else None
            t65 = ref.pubkey_create(self.tweak[i])
            t33 = ref.pubkey_compress(t65) if t65 else None
            s = ref.sign(self.msg[i], self.seckey[i], self.aux[i])
            if not (p65 and p33 and t33 and s):
                die("reference derivation failed")
            self.pub65.append(p65)
            self.pub33.append(p33)
            self.xonly.append(p33[1:])
            self.tpub33.append(t33)
            self.sig.append(s)
        self.batch_sig = [ref.sign(self.msg[j], self.seckey[0], self.aux[j]) for j in range(BATCH)]
        self.batch_msg = self.msg[:BATCH]
        if None in self.batch_sig:
            die("reference batch sign failed")


# ---------------------------------------------------------------- dataset (datasets/README.md)


class Dataset:
    def __init__(self, path):
        with open(path, "rb") as f:
            b = f.read()
        if not b or len(b) % RECORD:
            die("the dataset is not whole 128-byte records")
        self.sha256_hex = sha256(b).hex()
        self.n = len(b) // RECORD
        self.id = [b[i * RECORD : i * RECORD + 32] for i in range(self.n)]
        self.pub = [b[i * RECORD + 32 : i * RECORD + 64] for i in range(self.n)]
        self.sig = [b[i * RECORD + 64 : i * RECORD + 128] for i in range(self.n)]
        seen = set()
        self.keys = []  # distinct pubkeys, first appearance first
        for p in self.pub:
            if p not in seen:
                seen.add(p)
                self.keys.append(p)
        # whole pages of PAGE events, each split into its authors' groups (first appearance first)
        self.pages = self.n // PAGE
        self.page_groups = []
        for p in range(self.pages):
            groups = {}
            for e in range(p * PAGE, (p + 1) * PAGE):
                groups.setdefault(self.pub[e], []).append(e)
            self.page_groups.append(
                [(pub, [self.sig[e] for e in es], [self.id[e] for e in es]) for pub, es in groups.items()]
            )


def verify_page(b, ds, p):
    """The page's author groups through the library: batch verify for groups of two or more."""
    ok = True
    for pub, sigs, msgs in ds.page_groups[p]:
        ok &= bool(b.verify(sigs[0], msgs[0], pub) if len(sigs) == 1 else b.verify_batch(pub, sigs, msgs))
    return ok


def read_digests(path):
    out = {}
    with open(path) as f:
        for line in f:
            parts = line.split()
            if len(parts) == 2:
                out[parts[0]] = parts[1]
    return out


# ---------------------------------------------------------------- correctness

# The per-index work of the checks, in chunks, so a process pool can share it. Each chunk
# returns what c/main.c's digests() / dataset_digests() / the direct checks see for its range.

_state = None  # (impls by name, vectors, dataset) in this process


def _init_state(only):
    global _state
    if _state is None:
        bs = impls.build(only)
        _state = ({b.library: b for b in bs}, Vectors(bs[0]), Dataset(DATASET_BIN))
    return _state


def _vector_chunk(lib, start, end):
    bs, v, _ = _state
    b = bs[lib]
    out = {k: bytearray() for k in ("pubkey.create", "pubkey.compress", "sign", "ecdh.xOnly",
                                     "tweak.mul", "tweak.add", "verify", "verify.tampered")}
    fast_ok = known_pub_ok = known_pub_odd_only = seckey_ok = True
    for i in range(start, end):
        if b.pubkey_create:
            out["pubkey.create"] += b.pubkey_create(v.seckey[i]) or bytes(65)
        if b.pubkey_compress:
            out["pubkey.compress"] += b.pubkey_compress(v.pub65[i]) or bytes(33)
        out["sign"] += b.sign(v.msg[i], v.seckey[i], v.aux[i]) or bytes(64)
        if b.ecdh_xonly:
            out["ecdh.xOnly"] += b.ecdh_xonly(v.xonly[(i + 1) % N], v.seckey[i]) or bytes(32)
        if b.tweak_mul:
            out["tweak.mul"] += b.tweak_mul(v.tpub33[i], v.seckey[i]) or bytes(33)
        if b.tweak_add:
            out["tweak.add"] += b.tweak_add(v.seckey[i], v.tweak[i]) or bytes(32)
        out["verify"].append(1 if b.verify(v.sig[i], v.msg[i], v.xonly[i]) else 0)
        bad = bytearray(v.sig[i])
        bad[63] = (bad[63] + 1) & 0xFF
        out["verify.tampered"].append(1 if b.verify(bytes(bad), v.msg[i], v.xonly[i]) else 0)
        # the direct checks
        if b.verify_fast and not b.verify_fast(v.sig[i], v.msg[i], v.xonly[i]):
            fast_ok = False
        s = b.sign_known_pub(v.msg[i], v.seckey[i], v.pub33[i], v.xonly[i], v.aux[i])
        if s != v.sig[i]:
            known_pub_ok = False
            if v.pub33[i][0] != 0x03:
                known_pub_odd_only = False
        if b.seckey_verify and not b.seckey_verify(v.seckey[i]):
            seckey_ok = False
    return {k: bytes(x) for k, x in out.items()}, (fast_ok, known_pub_ok, known_pub_odd_only, seckey_ok)


def _dataset_chunk(lib, start, end):
    bs, _, ds = _state
    b = bs[lib]
    ok, bad_ok, fast_ok = bytearray(), bytearray(), True
    for i in range(start, end):
        ok.append(1 if b.verify(ds.sig[i], ds.id[i], ds.pub[i]) else 0)
        bad = bytearray(ds.sig[i])
        bad[63] = (bad[63] + 1) & 0xFF
        bad_ok.append(1 if b.verify(bytes(bad), ds.id[i], ds.pub[i]) else 0)
        if b.verify_fast and not b.verify_fast(ds.sig[i], ds.id[i], ds.pub[i]):
            fast_ok = False
    return bytes(ok), bytes(bad_ok), fast_ok


def _keys_chunk(lib, start, end):
    bs, v, ds = _state
    b = bs[lib]
    out = bytearray()
    for d in range(start, end):
        out += b.ecdh_xonly(ds.keys[d], v.seckey[d % N]) or bytes(32)
    return bytes(out)


def _pages_chunk(lib, start, end):
    bs, _, ds = _state
    return all(verify_page(bs[lib], ds, p) for p in range(start, end))


def _task(t):
    kind, lib, start, end = t
    f = {"vectors": _vector_chunk, "dataset": _dataset_chunk, "keys": _keys_chunk, "pages": _pages_chunk}[kind]
    return t, f(lib, start, end)


def _worker_init(only):
    _init_state(only)


def _tasks(b, ds):
    def chunks(kind, n, size):
        return [(kind, b.library, s, min(s + size, n)) for s in range(0, n, size)]

    t = chunks("vectors", N, 64) + chunks("dataset", ds.n, 256)
    if b.ecdh_xonly:
        t += chunks("keys", len(ds.keys), 256)
    if b.verify_batch:
        t += chunks("pages", ds.pages, 4)
    return t


def run_checks(bs, ds, jobs, only):
    """Every chunk of every library's checks; returns {task: result}."""
    tasks = [t for b in bs for t in _tasks(b, ds)]
    if jobs <= 1:
        return dict(_task(t) for t in tasks)
    import multiprocessing

    results = {}
    done = 0
    with multiprocessing.Pool(jobs, initializer=_worker_init, initargs=(only,)) as pool:
        # the heaviest library's chunks first, so the pool drains evenly
        for t, r in pool.imap_unordered(_task, list(reversed(tasks))):
            results[t] = r
            done += 1
            if done % 50 == 0 or done == len(tasks):
                log(f"checks: {done}/{len(tasks)} chunks")
    return results


def correctness(b, v, ds, results, vec_digests, ds_digests):
    """The correctness of each of [b]'s operations, as c/main.c computes it, from the chunks."""
    lib = b.library
    vt = sorted((t for t in results if t[0] == "vectors" and t[1] == lib), key=lambda t: t[2])
    outs = {}
    fast_ok = known_pub_ok = known_pub_odd_only = seckey_ok = True
    for t in vt:
        part, (f, k, ko, s) = results[t]
        for name, data in part.items():
            outs[name] = outs.get(name, b"") + data
        fast_ok &= f
        known_pub_ok &= k
        known_pub_odd_only &= ko
        seckey_ok &= s
    has = {
        "pubkey.create": b.pubkey_create, "pubkey.compress": b.pubkey_compress, "sign": b.sign,
        "ecdh.xOnly": b.ecdh_xonly, "tweak.mul": b.tweak_mul, "tweak.add": b.tweak_add,
        "verify": b.verify, "verify.tampered": b.verify,
    }
    good = {}
    for name, data in outs.items():
        if not has[name]:
            continue
        want = vec_digests.get(name)
        if want is None:
            die("vectors file lacks a check")
        good[name] = sha256(data).hex() == want
        if not good[name]:
            log(f"{lib}: {name} digest differs from vectors")
    if b.seckey_verify and (b.seckey_verify(bytes(32)) or b.seckey_verify(ORDER)):
        seckey_ok = False
    batch_ok = True
    if b.verify_batch and not b.verify_batch(v.xonly[0], v.batch_sig, v.batch_msg):
        batch_ok = False
    verify_ok = good["verify"] and good["verify.tampered"]

    dt = sorted((t for t in results if t[0] == "dataset" and t[1] == lib), key=lambda t: t[2])
    ok_bytes = b"".join(results[t][0] for t in dt)
    bad_bytes = b"".join(results[t][1] for t in dt)
    ds_fast_ok = all(results[t][2] for t in dt)
    dgood = {
        "nostr.verify": sha256(ok_bytes).hex() == ds_digests["nostr.verify"],
        "nostr.verify.tampered": sha256(bad_bytes).hex() == ds_digests["nostr.verify.tampered"],
    }
    if b.ecdh_xonly:
        kt = sorted((t for t in results if t[0] == "keys" and t[1] == lib), key=lambda t: t[2])
        dgood["nostr.ecdh"] = sha256(b"".join(results[t] for t in kt)).hex() == ds_digests["nostr.ecdh"]
    for name, g in dgood.items():
        if not g:
            log(f"{lib}: {name} digest differs from the dataset's")
    ds_batch_ok = all(results[t] for t in results if t[0] == "pages" and t[1] == lib)
    ds_verify_ok = dgood["nostr.verify"] and dgood["nostr.verify.tampered"]
    ok = {
        "verify.sameKey": verify_ok,
        "verify.manyKeys": verify_ok,
        "verifyFast.sameKey": verify_ok and fast_ok,
        "verifyBatch200.perSig": batch_ok,
        "sign": good["sign"],
        "sign.knownPub": known_pub_ok,
        "pubkey.create": good.get("pubkey.create", False),
        "pubkey.compress": good.get("pubkey.compress", False),
        "seckey.verify": seckey_ok,
        "ecdh.xOnly": good.get("ecdh.xOnly", False),
        "tweak.mul": good.get("tweak.mul", False),
        "tweak.add": good.get("tweak.add", False),
        "nostr.verify": ds_verify_ok,
        "nostr.verifyFast": ds_verify_ok and ds_fast_ok,
        "nostr.verifyBatchByAuthor": ds_batch_ok,
        "nostr.ecdh": dgood.get("nostr.ecdh", False),
    }
    return ok, (not known_pub_ok) and known_pub_odd_only


# ---------------------------------------------------------------- operations

OPS = [
    "verify.sameKey", "verify.manyKeys", "verifyFast.sameKey", "verifyBatch200.perSig", "sign",
    "sign.knownPub", "pubkey.create", "pubkey.compress", "seckey.verify", "ecdh.xOnly", "tweak.mul",
    "tweak.add", "nostr.verify", "nostr.verifyFast", "nostr.verifyBatchByAuthor", "nostr.ecdh",
]

next_index = 0
Z = bytes(65)  # what a failed call contributes to the accumulator


def make_run(b, op, v, ds):
    """A function running n calls of [op] (c/main.c's run()), or None if the library lacks it.

    Each loop body is the call and its index arithmetic, nothing else: the inputs are bound to
    locals first, and every result goes into the accumulator, which ends in the sink.
    """
    seckey, tweak, msg, aux, sig, xonly = v.seckey, v.tweak, v.msg, v.aux, v.sig, v.xonly
    pub65, pub33, tpub33 = v.pub65, v.pub33, v.tpub33
    verify, fast = b.verify, b.verify_fast or b.verify

    def done(k, acc):
        global next_index, sink
        next_index = k
        sink += acc

    if op == "verify.sameKey" or op == "verifyFast.sameKey":
        f = verify if op == "verify.sameKey" else fast
        s0, m0, x0 = sig[0], msg[0], xonly[0]

        def run(n):
            acc = 0
            for _ in range(n):
                acc += f(s0, m0, x0)
            done(next_index, acc)

    elif op == "verify.manyKeys":

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                i = k % N
                acc += verify(sig[i], msg[i], xonly[i])
                k += 1
            done(k, acc)

    elif op == "verifyBatch200.perSig":
        if not b.verify_batch:
            return None
        batch, x0, sigs, msgs = b.verify_batch, xonly[0], v.batch_sig, v.batch_msg

        def run(n):
            acc = 0
            for _ in range(n):
                acc += batch(x0, sigs, msgs)
            done(next_index, acc)

    elif op in ("sign", "sign.knownPub"):
        if op == "sign":
            sign = b.sign

            def run(n):
                acc, k = 0, next_index
                for _ in range(n):
                    i = k % N
                    acc += (sign(msg[i], seckey[i], aux[i]) or Z)[7]
                    k += 1
                done(k, acc)
        else:
            skp = b.sign_known_pub

            def run(n):
                acc, k = 0, next_index
                for _ in range(n):
                    i = k % N
                    acc += (skp(msg[i], seckey[i], pub33[i], xonly[i], aux[i]) or Z)[7]
                    k += 1
                done(k, acc)

    elif op == "pubkey.create":
        if not b.pubkey_create:
            return None
        f = b.pubkey_create

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                acc += (f(seckey[k % N]) or Z)[7]
                k += 1
            done(k, acc)

    elif op == "pubkey.compress":
        if not b.pubkey_compress:
            return None
        f = b.pubkey_compress

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                acc += (f(pub65[k % N]) or Z)[7]
                k += 1
            done(k, acc)

    elif op == "seckey.verify":
        if not b.seckey_verify:
            return None
        f = b.seckey_verify

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                acc += f(seckey[k % N])
                k += 1
            done(k, acc)

    elif op == "ecdh.xOnly":
        if not b.ecdh_xonly:
            return None
        f = b.ecdh_xonly

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                i = k % N
                acc += (f(xonly[(i + 1) % N], seckey[i]) or Z)[7]
                k += 1
            done(k, acc)

    elif op == "tweak.mul":
        if not b.tweak_mul:
            return None
        f = b.tweak_mul

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                i = k % N
                acc += (f(tpub33[i], seckey[i]) or Z)[7]
                k += 1
            done(k, acc)

    elif op == "tweak.add":
        if not b.tweak_add:
            return None
        f = b.tweak_add

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                i = k % N
                acc += (f(seckey[i], tweak[i]) or Z)[7]
                k += 1
            done(k, acc)

    elif op in ("nostr.verify", "nostr.verifyFast"):
        f = verify if op == "nostr.verify" else fast
        dsig, did, dpub, dn = ds.sig, ds.id, ds.pub, ds.n

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                i = k % dn
                acc += f(dsig[i], did[i], dpub[i])
                k += 1
            done(k, acc)

    elif op == "nostr.verifyBatchByAuthor":  # one call is one page
        if not b.verify_batch:
            return None
        pages = ds.pages

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                acc += verify_page(b, ds, k % pages)
                k += 1
            done(k, acc)

    elif op == "nostr.ecdh":
        if not b.ecdh_xonly:
            return None
        f, keys = b.ecdh_xonly, ds.keys
        nk = len(keys)

        def run(n):
            acc, k = 0, next_index
            for _ in range(n):
                d = k % nk
                acc += (f(keys[d], seckey[d % N]) or Z)[7]
                k += 1
            done(k, acc)

    else:
        return None
    return run


def pct(v, p):
    """Nearest-rank percentile of sorted v, as c/main.c computes it."""
    r = int(p / 100.0 * len(v) + 0.999999)
    r = max(1, min(r, len(v)))
    return v[r - 1]


def time_op(b, op, v, ds, quick):
    run = make_run(b, op, v, ds)
    if run is None:
        return None
    clock = time.perf_counter_ns
    warm_ns = 200_000_000 if quick else 1_000_000_000
    sample_ns = 20_000_000 if quick else 100_000_000
    n_samples = 5 if quick else 20
    per_call = BATCH if op == "verifyBatch200.perSig" else PAGE if op == "nostr.verifyBatchByAuthor" else 1
    run(1)
    start = clock()
    while clock() - start < warm_ns:
        run(16)
    n = 1
    while True:  # calibrate: n calls take at least one sample's time
        s = clock()
        run(n)
        if clock() - s >= sample_ns:
            break
        n *= 2
    vals = []
    for _ in range(n_samples):
        a = clock()
        run(n)
        vals.append((clock() - a) / (n * per_call))
    vals.sort()
    return pct(vals, 50), pct(vals, 5), pct(vals, 95), n_samples


# ---------------------------------------------------------------- known issues


def add_small_to_s(sig, d):
    """Adds d (+-1) to the s half of a signature, as c/main.c does (byte carries, no arithmetic
    on scalars: the result is an invalid signature for the probe)."""
    s = bytearray(sig)
    carry, i = d, 63
    while carry != 0 and i >= 32:
        x = s[i] + carry
        s[i] = x & 0xFF
        carry = 1 if x > 255 else -1 if x < 0 else 0
        i -= 1
    return bytes(s)


def probe_issues(b, ref, v, known_pub_odd_only):
    """KI-1..KI-3 on keys apart from the vectors, after the timing (c/main.c's probe_issues)."""
    ki1 = None
    if b.verify_batch:
        s1 = add_small_to_s(v.batch_sig[0], 1)
        s2 = add_small_to_s(v.batch_sig[1], -1)
        each_invalid = not ref.verify(s1, v.msg[0], v.xonly[0]) and not ref.verify(s2, v.msg[1], v.xonly[0])
        ki1 = bool(each_invalid and b.verify_batch(v.xonly[0], [s1, s2], [v.msg[0], v.msg[1]]))
    ki3 = None
    if b.tweak_mul:
        rejected = found = 0
        for i in range(4096):
            if found >= 16:
                break
            sk = sha256(f"schnorr256k1-bench/v1/probe/{i}".encode())
            p65 = ref.pubkey_create(sk)
            p33 = ref.pubkey_compress(p65) if p65 else None
            if not p33 or p33[0] != 0x03:
                continue
            found += 1
            msg = sha256(sk)
            sig = ref.sign(msg, sk, msg)
            b.tweak_mul(p33, v.tweak[0])
            if not b.verify(sig, msg, p33[1:]):
                rejected += 1
        ki3 = rejected > 0
    return {"KI-1": ki1, "KI-2": bool(known_pub_odd_only), "KI-3": ki3}


# The ops each known issue makes wrong on the vectors (bench's Runner.COVERS).
COVERS = {"sign.knownPub": "KI-2"}

# ---------------------------------------------------------------- context


def cpu_model():
    if sys.platform == "darwin":
        import subprocess

        try:
            return subprocess.run(["sysctl", "-n", "machdep.cpu.brand_string"], capture_output=True,
                                  text=True, check=True).stdout.strip() or "unknown"
        except (OSError, subprocess.CalledProcessError):
            return "unknown"
    try:
        with open("/proc/cpuinfo") as f:
            for line in f:
                if line.startswith("model name") or line.startswith("Hardware"):
                    return line.split(":", 1)[1].strip()
    except OSError:
        pass
    return platform.processor() or "unknown"


def os_name():
    if sys.platform == "darwin":
        return f"macOS {platform.mac_ver()[0]}"
    return f"{platform.system()} {platform.release()}"


def runtime():
    return f"{platform.python_implementation()} {platform.python_version()} ({platform.python_compiler().strip()})"


# ---------------------------------------------------------------- main


def fmt(x):
    return f"{x:.1f}"


def main(argv):
    if not argv or argv[0] not in ("run", "check", "vectors"):
        die(__doc__)
    cmd = argv[0]
    quick, only, device, machine, harness = False, None, "unknown", "unknown", "unknown"
    jobs = os.cpu_count() or 1
    a = 1
    while a < len(argv):
        o = argv[a]
        if o == "--quick":
            quick = True
        elif o in ("--library", "--device", "--machine", "--harness", "--jobs") and a + 1 < len(argv):
            a += 1
            val = argv[a]
            if o == "--library":
                if val not in impls.LIBRARIES:
                    die(f"unknown library {val} ({', '.join(impls.LIBRARIES)})")
                only = val
            elif o == "--device":
                device = val
            elif o == "--machine":
                machine = val
            elif o == "--harness":
                harness = val
            else:
                jobs = int(val)
        else:
            die(f"unknown option {o}")
        a += 1

    bs, v, ds = _init_state(only)
    bs = list(bs.values())
    ref = bs[0]
    if cmd == "vectors":
        r = dict(_task(t) for t in _tasks(ref, ds) if t[0] == "vectors")
        parts = [r[t][0] for t in sorted(r, key=lambda t: t[2])]
        for name in parts[0]:
            print(name, sha256(b"".join(p[name] for p in parts)).hex())
        return 0

    with open(VECTORS_TXT, "rb") as f:
        vec_sha = sha256(f.read()).hex()
    vec_digests = read_digests(VECTORS_TXT)
    ds_digests = read_digests(DATASET_TXT)
    if ds_digests.get("file") != ds.sha256_hex:
        die("the dataset is not the one its digests file describes")
    run_bs = [b for b in bs if only is None or b.library == only]

    log(f"checking {', '.join(b.library for b in run_bs)} ({jobs} jobs)")
    t0 = time.monotonic()
    results = run_checks(run_bs, ds, jobs, only)
    log(f"checks took {time.monotonic() - t0:.0f} s")
    checks = {b.library: correctness(b, v, ds, results, vec_digests, ds_digests) for b in run_bs}

    if cmd == "check":
        unexplained = []
        for b in run_bs:
            ok, odd_only = checks[b.library]
            ki = probe_issues(b, ref, v, odd_only)
            log(f"{b.library} ({b.binding}): known issues {ki}")
            for op in OPS:
                if make_run(b, op, v, ds) is None or ok[op]:
                    continue
                k = COVERS.get(op)
                if k and ki.get(k):
                    log(f"{b.library} {op}: wrong, as {k} says")
                else:
                    unexplained.append(f"{b.library} {op}")
        for u in unexplained:
            log(f"WRONG: {u}")
        log("all correct, or explained by a known issue" if not unexplained else f"{len(unexplained)} unexplained")
        return 1 if unexplained else 0

    rows, issues = [], {}
    for b in run_bs:
        ok, odd_only = checks[b.library]
        t = {}
        for op in OPS:
            log(f"{b.library} {op}...")
            t[op] = time_op(b, op, v, ds, quick)
        ki = probe_issues(b, ref, v, odd_only)
        issues[b.library] = ki
        for op in OPS:
            if t[op] is None:
                continue
            med, p5, p95, n = t[op]
            kis = []
            if op in ("verifyBatch200.perSig", "nostr.verifyBatchByAuthor") and ki["KI-1"]:
                kis.append("KI-1")
            if op == "sign.knownPub" and ki["KI-2"]:
                kis.append("KI-2")
            rows.append(
                f'    {{ "op": "{op}", "library": "{b.library}", "binding": "{b.binding}", '
                f'"median_ns": {fmt(med)}, "p5_ns": {fmt(p5)}, "p95_ns": {fmt(p95)}, "samples": {n}, '
                f'"correct": {"true" if ok[op] else "false"}, "known_issues": [{", ".join(json.dumps(k) for k in kis)}] }}'
            )

    ctx = {
        "date": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
        "device": device,
        "cpu": cpu_model(),
        "os": os_name(),
        "runtime": runtime(),
        "machine": machine,
        "harness": harness,
    }
    libs = ", ".join(f"{json.dumps(b.library)}: {json.dumps(b.version)}" for b in run_bs)
    jv = lambda x: "null" if x is None else "true" if x else "false"  # noqa: E731
    out = [
        "{",
        '  "schema": 3,',
        '  "vectors": "v1",',
        f'  "vectorsSha256": "{vec_sha}",',
        '  "dataset": "nostr-v1",',
        f'  "datasetSha256": "{ds.sha256_hex}",',
        '  "layer": "python",',
        f'  "quick": {"true" if quick else "false"},',
        '  "context": {',
        *[f"    {json.dumps(k)}: {json.dumps(val)}," for k, val in ctx.items()],
        f"    \"libraries\": {{ {libs} }}",
        "  },",
        '  "rows": [',
        ",\n".join(rows),
        "  ],",
        '  "known_issues_observed": {',
        ",\n".join(
            f'    {json.dumps(lib)}: {{ "KI-1": {jv(k["KI-1"])}, "KI-2": {jv(k["KI-2"])}, "KI-3": {jv(k["KI-3"])} }}'
            for lib, k in issues.items()
        ),
        "  }",
        "}",
    ]
    print("\n".join(out))
    return 0 if sink >= 0 else 0  # keep the sink alive


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
