#!/usr/bin/env python3
"""
Builds datasets/nostr-v1.bin from nostrLibBench's corpus: the signed events of an Amethyst
startup session in 2024, reduced to what signature verification reads.

    python3 datasets/extract-nostr.py PATH/TO/nostrlibbench/corpus/nostr-2024-startup.ndjson.gz

Output: one 128-byte record per event with a signature, in corpus order:
id (32 bytes, the signed message) || pubkey (32, x-only) || sig (64). Events without a
signature ("sig": "", unsigned NIP-17 rumors) are left out. Nothing else is filtered: whether
every signature verifies is checked by the bench itself, against libsecp256k1, on every run.
"""
import gzip
import hashlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    out = bytearray()
    n = 0
    with gzip.open(sys.argv[1], "rt", encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            if not e.get("sig"):
                continue
            rec = bytes.fromhex(e["id"]) + bytes.fromhex(e["pubkey"]) + bytes.fromhex(e["sig"])
            if len(rec) != 128:
                sys.exit(f"malformed event {e.get('id')}")
            out += rec
            n += 1
    path = os.path.join(HERE, "nostr-v1.bin")
    with open(path, "wb") as f:
        f.write(out)
    print(f"{n} events, {len(out)} bytes, sha256 {hashlib.sha256(out).hexdigest()}")


if __name__ == "__main__":
    main()
