#!/usr/bin/env python3
"""Renders schema-v3 results (results/SCHEMA.md) as Markdown comparison tables.

    scripts/table.py                     # every full run under results/, one table per device
    scripts/table.py results/jvm/X.json results/c/Y.json
    scripts/table.py --quick DIR         # include --quick runs (smoke runs; not comparable)
    scripts/table.py --check --quick DIR # also exit 1 on a wrong row no known issue explains (CI)
    scripts/table.py --readme            # README.md's ranked tables and results/report.md from the full runs
    scripts/table.py --readme --check    # exit 1 if either is not what --readme would write (CI)

Each device gets one table per layer: a row per operation, a column per library, with the median in
microseconds and, beside it, the ratio to libsecp256k1 in the same layer. A wrong result shows as
"wrong" and the known issue that explains it; an operation a library lacks shows "-". Files whose
schema, vectors or vectors file hash are not this checkout's are skipped with a warning.

--readme uses the full runs from a dedicated machine under results/, or, while there are none,
the full shared ones, labelled as such (never --quick runs). It writes one ranked table per operation (README_OPS, verification first) for the device of the
newest run between README_BEGIN and README_END in README.md, and every table to results/report.md.
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
    "nostr.verify", "nostr.verifyFast", "nostr.verifyBatchByAuthor", "nostr.ecdh",
]
LAYERS = ["c", "rust", "go", "native", "jvm", "android", "node", "bun", "python"]
# Column order: the reference, the two this bench started with, then every other library a run
# reports, alphabetically (libraries()).
LIBRARIES = ["libsecp256k1", "libschnorr256k1", "kotlin"]
SCHEMA = 3


def libraries(run):
    """The libraries one run reports, in column order."""
    present = []
    for row in run["rows"]:
        if row["library"] not in present:
            present.append(row["library"])
    return [l for l in LIBRARIES if l in present] + sorted(l for l in present if l not in LIBRARIES)


# How the tables name a result's layer and binding (the JSON keeps "layer" and "binding",
# results/SCHEMA.md). The caller is the runtime the app's code runs on; "via" is how it reaches
# the library: directly, through a binding package (named with its version), or by being written
# in the caller's own language.
CALLERS = {"c": "C", "rust": "Rust", "go": "Go", "native": "Kotlin/Native", "jvm": "JVM",
           "android": "Android", "node": "Node.js", "bun": "Bun", "python": "Python"}
PURE = {"kotlin": "Kotlin", "java": "Java", "scala": "Scala", "rust": "Rust", "go": "Go",
        "js": "JavaScript", "python": "Python"}
MECHANISMS = {"jni": "JNI", "cinterop": "cinterop", "wasm": "wasm", "cffi": "CFFI",
              "rust-secp256k1": "FFI", "cgo": "cgo"}


def caller(layer):
    return CALLERS.get(layer, layer)


def via(run, library, binding):
    """'direct', 'pure <language>', or '<mechanism>: <binding package and version>'."""
    if binding == "c":
        return "direct"
    if binding in PURE:
        return f"pure {PURE[binding]}"
    mechanism = MECHANISMS.get(binding, binding)
    if binding == "cgo":  # the go layer's own cgo binding: no package
        return "cgo (the bench's own binding)"
    package = run["context"]["libraries"].get(library, "")
    package = package.split(" (")[0]  # drop the parenthetical detail; the versions list keeps it
    return f"{mechanism}: {package}" if package else mechanism


def layer_order(layer):
    return LAYERS.index(layer) if layer in LAYERS else len(LAYERS)
REFERENCE = "libsecp256k1"
README = os.path.join(ROOT, "README.md")
README_BEGIN = "<!-- results:begin: scripts/table.py --readme writes this section; do not edit it by hand -->"
README_END = "<!-- results:end -->"


def file_sha(*path):
    with open(os.path.join(ROOT, *path), "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def collect(args, quick):
    paths = []
    for a in args or [os.path.join(ROOT, "results")]:
        if os.path.isdir(a):
            for d, _, files in os.walk(a):
                paths += [os.path.join(d, f) for f in files if f.endswith(".json")]
        else:
            paths.append(a)
    sha = file_sha("vectors", "v1.txt")
    ds_sha = file_sha("datasets", "nostr-v1.bin")
    runs = []
    for p in sorted(paths):
        with open(p) as f:
            r = json.load(f)
        if (r.get("schema") != SCHEMA or r.get("vectors") != "v1" or r.get("vectorsSha256") != sha
                or r.get("dataset") != "nostr-v1" or r.get("datasetSha256") != ds_sha):
            print(f"skipping {p}: not schema {SCHEMA} on this checkout's vectors and dataset", file=sys.stderr)
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
    """Per device, one table per layer: a row per operation, a column per library."""
    by_device = defaultdict(list)
    for r in runs:
        by_device[r["context"]["device"]].append(r)
    out = []
    for device, rs in sorted(by_device.items()):
        # The newest run per layer; c runs on a host and on Android share a device only by name.
        latest = {}
        for r in sorted(rs, key=lambda r: r["context"]["date"]):
            latest[(r["layer"], r["context"]["os"])] = r
        layers = sorted(latest.values(), key=lambda r: layer_order(r["layer"]))
        first = layers[0]["context"]
        quick = any(r.get("quick") for r in layers)
        out.append(f"### {device}{' (quick, not comparable)' if quick else ''}\n")
        out.append(f"{first['cpu']}; machine: {', '.join(sorted({r['context']['machine'] for r in layers}))}. "
                   "Median µs per operation (ratio to libsecp256k1 from the same caller).\n")
        for r in layers:
            libs = libraries(r)
            rows = {(row["op"], row["library"]): row for row in r["rows"]}
            binding = {row["library"]: via(r, row["library"], row["binding"]) for row in r["rows"]}
            c = r["context"]
            out.append(f"#### {caller(r['layer'])}\n")
            out.append("| op | " + " | ".join(f"{lib} ({binding[lib]})" for lib in libs) + " |")
            out.append("|---|" + "---:|" * len(libs))
            for op in OPS:
                if not any((op, lib) in rows for lib in libs):
                    continue
                cells = [cell(rows.get((op, lib)), rows.get((op, REFERENCE))) for lib in libs]
                out.append(f"| {op} | " + " | ".join(cells) + " |")
            versions = ", ".join(f"{k} {v}" for k, v in c["libraries"].items())
            out.append(f"\n{c['date']}, {c['os']}, {c['runtime']}; {versions}; harness {c['harness']}\n")
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


# The README's tables: one per operation, verification first (a Nostr client verifies every
# event every relay sends), each ranking every library and layer on the newest device.
README_OPS = [
    ("nostr.verify", "Verify real Nostr events",
     "the 29,614 events of `datasets/nostr-v1` in the order relays sent them: a few prolific authors and thousands seen once"),
    ("verify.manyKeys", "Verify, a new key each time", "4,096 keys in strict rotation, so no per-key cache helps"),
    ("verify.sameKey", "Verify, one key", "the same key every call: the best case for a per-key cache"),
    ("nostr.verifyBatchByAuthor", "Batch verify, real events",
     "the dataset in pages of 200, each page's events batched by author; per event"),
    ("verifyBatch200.perSig", "Batch verify, 200 signatures by one key", "per signature"),
    ("nostr.verifyFast", "Fast verify, real events", "a library's fast verify where it has one, else its full verify"),
    ("verifyFast.sameKey", "Fast verify, one key", "likewise"),
    ("sign", "Sign", "BIP-340 with aux randomness, from the secret key"),
    ("sign.knownPub", "Sign with a known public key", "where the library can skip deriving it"),
    ("pubkey.create", "Create a public key", "uncompressed, 65 bytes"),
    ("nostr.ecdh", "ECDH with real authors' keys", "x-only ECDH against the dataset's 4,926 distinct pubkeys (NIP-04/NIP-44)"),
    ("ecdh.xOnly", "ECDH", "x-only, against 4,096 keys"),
    ("tweak.mul", "Tweak-multiply a public key", "a compressed point times a scalar"),
    ("pubkey.compress", "Compress a public key", "65 bytes to 33"),
    ("tweak.add", "Tweak-add a secret key", "(secret + tweak) mod n"),
    ("seckey.verify", "Check a secret key", "0 < key < n"),
]
# Our libraries, in bold: libschnorr256k1-kotlin, and libschnorr256k1 (C) from every caller,
# directly or through libschnorr256k1-kmp.
HIGHLIGHT = {"kotlin", "libschnorr256k1"}
REPORT = os.path.join(ROOT, "results", "report.md")


def published_runs():
    """The full runs under results/ the README shows: the dedicated ones, or, while there are none,
    the shared ones (a cloud VM), which the README then labels as such. Never a --quick run."""
    full = [r for r in collect([], quick=False) if not r.get("quick")]
    dedicated = [r for r in full if r["context"]["machine"] == "dedicated"]
    return dedicated or full


def latest_by_layer(runs):
    latest = {}
    for r in sorted(runs, key=lambda r: r["context"]["date"]):
        latest[(r["layer"], r["context"]["os"])] = r
    return sorted(latest.values(), key=lambda r: layer_order(r["layer"]))


def op_table(op, layers):
    """One operation ranked: every library on every layer that has it; wrong results last, unranked."""
    ranked, wrong = [], []
    for r in layers:
        by = {(row["op"], row["library"]): row for row in r["rows"]}
        for lib in libraries(r):
            row = by.get((op, lib))
            if row is not None:
                (ranked if row["correct"] else wrong).append((r, lib, row, by.get((op, REFERENCE))))
    ranked.sort(key=lambda x: x[2]["median_ns"])
    out = ["| # | caller | library | via | µs | vs libsecp256k1 |", "|---:|---|---|---|---:|---:|"]
    for i, (r, lib, row, ref) in enumerate(ranked + wrong, 1):
        rank = str(i) if row["correct"] else "–"
        us = fmt_us(row["median_ns"]) + ("" if row["correct"] else " **wrong**")
        if row["known_issues"]:
            us += " " + ",".join(row["known_issues"])
        ratio = "–"
        if ref is not None and lib != REFERENCE and ref["median_ns"] > 0:
            ratio = f"{row['median_ns'] / ref['median_ns']:.2f}x"
        cells = [rank, caller(r["layer"]), f"`{lib}`", via(r, lib, row["binding"]), us, ratio]
        if lib in HIGHLIGHT:
            cells = [f"**{c}**" if c not in ("-", "–") else c for c in cells]
        out.append("| " + " | ".join(cells) + " |")
    return "\n".join(out) if ranked or wrong else None


def headline(runs):
    """A ranked table per operation for the device of the newest published run."""
    device = max(runs, key=lambda r: r["context"]["date"])["context"]["device"]
    layers = latest_by_layer([r for r in runs if r["context"]["device"] == device])
    first = layers[0]["context"]
    dates = sorted({r["context"]["date"] for r in layers})
    when = dates[0] if len(dates) == 1 else f"{dates[0]} to {dates[-1]}"
    machine = ("a dedicated machine" if all(r["context"]["machine"] == "dedicated" for r in layers)
               else "**a shared machine** (a cloud VM: other work may have run beside it, so the numbers are "
                    "noisier than a dedicated run's, which will replace them)")
    parts = [f"Measured on {device} ({first['cpu']}), {machine}, single thread, {when}. "
             "Each table ranks one operation: median µs per call, and its ratio to libsecp256k1 from the "
             "same caller (below 1x is faster). A wrong result is listed last, unranked; KI-n marks a "
             "known issue (below).\n"]
    for op, title, what in README_OPS:
        table = op_table(op, layers)
        if table:
            parts.append(f"### {title}\n\n`{op}`: {what}.\n\n{table}\n")
    notes = []
    for r in layers:
        c = r["context"]
        libs = ", ".join(f"{k} {v}" for k, v in c["libraries"].items())
        notes.append(f"- {caller(r['layer'])}: {c['os']}, {c['runtime']}; {libs}")
    parts.append("### Versions\n\n" + "\n".join(notes) + "\n")
    return "\n".join(parts)


def readme_section(runs):
    """What README.md holds between the markers: the headline table, or a note that there is none."""
    if not runs:
        body = "No runs yet.\n"
    else:
        body = headline(runs) + ("\nEvery operation, layer and device: [results/report.md](results/report.md).\n")
    return f"{README_BEGIN}\n\n{body.rstrip()}\n\n{README_END}"


def report_text(runs):
    head = ("# Results\n\nWritten by `scripts/table.py --readme` from the dedicated runs under "
            "`results/`, or, while there are none, the full shared ones (results/SCHEMA.md). One table per device: each layer's newest run, median "
            "µs per operation and its ratio to libsecp256k1 in the same layer.\n\n")
    return head + (render(runs) if runs else "No runs yet.\n")


def readme(check):
    """Writes README.md's results section and results/report.md; with check, only compares."""
    with open(README) as f:
        text = f.read()
    begin, end = text.find(README_BEGIN), text.find(README_END)
    if begin < 0 or end < begin:
        sys.exit(f"README.md has no {README_BEGIN} ... {README_END} section")
    runs = published_runs()
    updated = text[:begin] + readme_section(runs) + text[end + len(README_END):]
    report = report_text(runs)
    old_report = open(REPORT).read() if os.path.exists(REPORT) else None
    if check:
        stale = [n for n, new, old in (("README.md", updated, text), ("results/report.md", report, old_report)) if new != old]
        if stale:
            print(f"stale: {', '.join(stale)}: run scripts/table.py --readme and commit", file=sys.stderr)
            sys.exit(1)
        print("README.md and results/report.md match the runs under results/")
        return
    with open(README, "w") as f:
        f.write(updated)
    with open(REPORT, "w") as f:
        f.write(report)
    print(readme_section(runs))


def main():
    args = sys.argv[1:]
    quick = "--quick" in args
    check = "--check" in args
    if "--readme" in args:
        if quick or len(args) > (2 if check else 1):
            sys.exit("--readme takes only --check: it renders the full runs under results/")
        readme(check)
        return
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
