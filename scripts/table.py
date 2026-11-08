#!/usr/bin/env python3
"""Renders schema-v2 results (results/SCHEMA.md) as Markdown comparison tables.

    scripts/table.py                     # every full run under results/, one table per device
    scripts/table.py results/jvm/X.json results/c/Y.json
    scripts/table.py --quick DIR         # include --quick runs (smoke runs; not comparable)
    scripts/table.py --check --quick DIR # also exit 1 on a wrong row no known issue explains (CI)
    scripts/table.py --readme            # README.md's headline and results/report.md from the dedicated runs
    scripts/table.py --readme --check    # exit 1 if either is not what --readme would write (CI)

Each table is one device: a row per operation, a column per layer and library, with the median in
microseconds and, beside it, the ratio to libsecp256k1 in the same layer. A wrong result shows as
"wrong" and the known issue that explains it; an operation a library lacks shows "-". Files whose
schema, vectors or vectors file hash are not this checkout's are skipped with a warning.

--readme uses only full runs from a dedicated machine under results/ (never --quick or shared runs,
results/SCHEMA.md). It writes the headline table, ranked by HEADLINE_RANK, for the device of the
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
LAYERS = ["c", "native", "jvm", "android"]
LIBRARIES = ["libsecp256k1", "libschnorr256k1", "kotlin"]
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
        if (r.get("schema") != 2 or r.get("vectors") != "v1" or r.get("vectorsSha256") != sha
                or r.get("dataset") != "nostr-v1" or r.get("datasetSha256") != ds_sha):
            print(f"skipping {p}: not schema 2 on this checkout's vectors and dataset", file=sys.stderr)
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


# The README's headline: one row per layer and library, ranked by the real-traffic verify.
HEADLINE_RANK = "nostr.verify"
HEADLINE_OPS = [
    ("nostr.verify", "verify, real events"),
    ("nostr.verifyBatchByAuthor", "batch by author, per event"),
    ("verify.manyKeys", "verify, a new key each time"),
    ("verify.sameKey", "verify, one key"),
    ("sign", "sign"),
    ("pubkey.create", "pubkey.create"),
    ("ecdh.xOnly", "ecdh.xOnly"),
]
HIGHLIGHT = "kotlin"  # the library this bench was built for, in bold
REPORT = os.path.join(ROOT, "results", "report.md")


def dedicated_runs():
    return [r for r in collect([], quick=False) if r["context"]["machine"] == "dedicated" and not r.get("quick")]


def latest_by_layer(runs):
    latest = {}
    for r in sorted(runs, key=lambda r: r["context"]["date"]):
        latest[(r["layer"], r["context"]["os"])] = r
    return sorted(latest.values(), key=lambda r: LAYERS.index(r["layer"]))


def headline(runs):
    """The ranked table for the device of the newest dedicated run."""
    device = max(runs, key=lambda r: r["context"]["date"])["context"]["device"]
    layers = latest_by_layer([r for r in runs if r["context"]["device"] == device])
    rows = []
    for r in layers:
        by = {(row["op"], row["library"]): row for row in r["rows"]}
        for lib in LIBRARIES:
            if (HEADLINE_RANK, lib) in by:
                rows.append((r, lib, by))
    rows.sort(key=lambda x: x[2][(HEADLINE_RANK, x[1])]["median_ns"])
    head = "| # | layer | library | binding | " + " | ".join(t for _, t in HEADLINE_OPS) + " |"
    out = [head, "|---:|---|---|---|" + "---:|" * len(HEADLINE_OPS)]
    for i, (r, lib, by) in enumerate(rows, 1):
        binding = by[(HEADLINE_RANK, lib)]["binding"]
        cells = [str(i), r["layer"], f"`{lib}`", binding]
        cells += [cell(by.get((op, lib)), by.get((op, REFERENCE))) for op, _ in HEADLINE_OPS]
        if lib == HIGHLIGHT:
            cells = [f"**{c}**" if c != "-" else c for c in cells]
        out.append("| " + " | ".join(cells) + " |")
    first = layers[0]["context"]
    dates = sorted({r["context"]["date"] for r in layers})
    when = dates[0] if len(dates) == 1 else f"{dates[0]} to {dates[-1]}"
    intro = (f"Measured on {device} ({first['cpu']}), a dedicated machine, single thread, {when}. "
             f"Median µs per operation, and in parentheses its ratio to libsecp256k1 in the same "
             f"layer (below 1x is faster). Ranked by `{HEADLINE_RANK}`: the 29,614 events of "
             f"`datasets/nostr-v1`, verified in the order relays sent them.\n")
    notes = []
    for r in layers:
        c = r["context"]
        libs = ", ".join(f"{k} {v}" for k, v in c["libraries"].items())
        notes.append(f"- {r['layer']}: {c['os']}, {c['runtime']}; {libs}")
    return intro + "\n" + "\n".join(out) + "\n\n" + "\n".join(notes) + "\n"


def readme_section(runs):
    """What README.md holds between the markers: the headline table, or a note that there is none."""
    if not runs:
        body = "No dedicated runs yet.\n"
    else:
        body = headline(runs) + ("\nEvery operation, layer and device: [results/report.md](results/report.md).\n")
    return f"{README_BEGIN}\n\n{body.rstrip()}\n\n{README_END}"


def report_text(runs):
    head = ("# Results\n\nWritten by `scripts/table.py --readme` from the dedicated runs under "
            "`results/` (results/SCHEMA.md). One table per device: each layer's newest run, median "
            "µs per operation and its ratio to libsecp256k1 in the same layer.\n\n")
    return head + (render(runs) if runs else "No dedicated runs yet.\n")


def readme(check):
    """Writes README.md's results section and results/report.md; with check, only compares."""
    with open(README) as f:
        text = f.read()
    begin, end = text.find(README_BEGIN), text.find(README_END)
    if begin < 0 or end < begin:
        sys.exit(f"README.md has no {README_BEGIN} ... {README_END} section")
    runs = dedicated_runs()
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
            sys.exit("--readme takes only --check: it renders the dedicated runs under results/")
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
