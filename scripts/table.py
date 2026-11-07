#!/usr/bin/env python3
"""Renders schema-v1 results (results/SCHEMA.md) as Markdown comparison tables.

    scripts/table.py                     # every full run under results/, one table per device
    scripts/table.py results/jvm/X.json results/c/Y.json
    scripts/table.py --quick DIR         # include --quick runs (smoke runs; not comparable)
    scripts/table.py --check --quick DIR # also exit 1 on a wrong row no known issue explains (CI)

Each table is one device: a row per operation, a column per layer and library, with the median in
microseconds and, beside it, the ratio to libsecp256k1 in the same layer. A wrong result shows as
"wrong" and the known issue that explains it; an operation a library lacks shows "-". Files whose
schema, vectors or vectors file hash are not this checkout's are skipped with a warning.
"""

import hashlib
import json
import os
import sys
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OPS = [
    "verify.sameKey", "verify.manyKeys", "verifyFast.sameKey", "verifyBatch200.perSig",
    "sign", "sign.knownPub", "pubkey.create", "pubkey.compress", "seckey.verify",
    "ecdh.xOnly", "tweak.mul", "tweak.add",
]
LAYERS = ["c", "native", "jvm", "android"]
LIBRARIES = ["libsecp256k1", "libschnorr256k1", "kotlin"]
REFERENCE = "libsecp256k1"


def vectors_sha():
    with open(os.path.join(ROOT, "vectors", "v1.txt"), "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def collect(args, quick):
    paths = []
    for a in args or [os.path.join(ROOT, "results")]:
        if os.path.isdir(a):
            for d, _, files in os.walk(a):
                paths += [os.path.join(d, f) for f in files if f.endswith(".json")]
        else:
            paths.append(a)
    sha = vectors_sha()
    runs = []
    for p in sorted(paths):
        with open(p) as f:
            r = json.load(f)
        if r.get("schema") != 1 or r.get("vectors") != "v1" or r.get("vectorsSha256") != sha:
            print(f"skipping {p}: not schema 1 on this checkout's vectors/v1.txt", file=sys.stderr)
            continue
        if r.get("quick") and not quick:
            continue
        runs.append(r)
    return runs


def fmt_us(ns):
    us = ns / 1000
    return f"{us:.2f}" if us < 10 else f"{us:.1f}"


def cell(row, ref):
    if row is None:
        return "-"
    text = fmt_us(row["median_ns"])
    if ref is not None and row["library"] != REFERENCE and ref["median_ns"] > 0:
        text += f" ({row['median_ns'] / ref['median_ns']:.2f}x)"
    if not row["correct"]:
        text += " **wrong**"
    if row["known_issues"]:
        text += " " + ",".join(row["known_issues"])
    return text


def render(runs):
    by_device = defaultdict(list)
    for r in runs:
        by_device[r["context"]["device"]].append(r)
    out = []
    for device, rs in sorted(by_device.items()):
        # The newest run per layer; c runs on a host and on Android share a device only by name.
        latest = {}
        for r in sorted(rs, key=lambda r: r["context"]["date"]):
            latest[(r["layer"], r["context"]["os"])] = r
        cols = []
        for (layer, os_name), r in sorted(latest.items(), key=lambda kv: LAYERS.index(kv[0][0])):
            libs = {row["library"] for row in r["rows"]}
            for lib in LIBRARIES:
                if lib in libs:
                    binding = next(row["binding"] for row in r["rows"] if row["library"] == lib)
                    cols.append((r, lib, f"{layer} {lib} ({binding})"))
        first = next(iter(latest.values()))["context"]
        quick = any(r.get("quick") for r in latest.values())
        out.append(f"### {device}{' (quick, not comparable)' if quick else ''}\n")
        out.append(f"{first['cpu']}; machine: {', '.join(sorted({r['context']['machine'] for r in latest.values()}))}. "
                   "Median µs per operation (ratio to libsecp256k1 in the same layer).\n")
        out.append("| op | " + " | ".join(c[2] for c in cols) + " |")
        out.append("|---|" + "---:|" * len(cols))
        for op in OPS:
            cells = []
            for r, lib, _ in cols:
                rows = {(row["op"], row["library"]): row for row in r["rows"]}
                cells.append(cell(rows.get((op, lib)), rows.get((op, REFERENCE))))
            out.append(f"| {op} | " + " | ".join(cells) + " |")
        out.append("")
        for (layer, _), r in sorted(latest.items(), key=lambda kv: LAYERS.index(kv[0][0])):
            c = r["context"]
            libs = ", ".join(f"{k} {v}" for k, v in c["libraries"].items())
            out.append(f"- {layer}: {c['date']}, {c['os']}, {c['runtime']}; {libs}; harness {c['harness']}")
        out.append("")
    return "\n".join(out)


# The ops each known issue makes wrong (Runner.COVERS in the Kotlin layers).
COVERS = {"sign.knownPub": "KI-2"}


def unexplained(runs):
    bad = []
    for r in runs:
        for row in r["rows"]:
            if not row["correct"] and COVERS.get(row["op"]) not in row["known_issues"]:
                bad.append(f"{r['layer']} {r['context']['os']}: {row['library']} {row['op']}")
        ref = r["known_issues_observed"].get(REFERENCE, {})
        bad += [f"{r['layer']}: the reference shows {k}" for k, v in ref.items() if v]
    return bad


def main():
    args = sys.argv[1:]
    quick = "--quick" in args
    check = "--check" in args
    args = [a for a in args if a not in ("--quick", "--check")]
    runs = collect(args, quick)
    if not runs:
        sys.exit("no results")
    print(render(runs))
    if check:
        bad = unexplained(runs)
        for b in bad:
            print(f"wrong, and no known issue explains it: {b}", file=sys.stderr)
        sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
