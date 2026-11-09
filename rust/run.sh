#!/usr/bin/env bash
# The rust layer (rust/README.md): builds the harness (cargo, release profile, Cargo.lock as
# committed) and runs it.
#
#   rust/run.sh [--quick] --device NAME --machine dedicated|shared --out FILE.json
#   rust/run.sh --check        the checks and probes without the timing (CI): exits 1 on a
#                              wrong result that no observed known issue explains
#
# --library NAME runs one library (libsecp256k1 | k256 | secp256kfun).
set -euo pipefail
cd "$(dirname "$0")/.."

device=""
machine=""
out=""
quick=""
check=""
library=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="$2"; shift ;;
    --machine) machine="$2"; shift ;;
    --out) out="$2"; shift ;;
    --quick) quick="--quick" ;;
    --check) check=1 ;;
    --library) library=(--library "$2"); shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done

. scripts/common.sh

cargo build --release --locked --quiet --manifest-path rust/Cargo.toml
bin=rust/target/release/schnorr256k1-bench-rust
inputs=(--vectors vectors/v1.txt --dataset datasets/nostr-v1.bin --dataset-digests datasets/nostr-v1.txt)

if [ -n "$check" ]; then
  exec "$bin" check "${inputs[@]}" "${library[@]}"
fi

[ -n "$device" ] || { echo "--device is required (what the machine is, e.g. \"ThinkPad X1 Gen 11\")" >&2; exit 2; }
[ -n "$out" ] || { echo "--out FILE.json is required" >&2; exit 2; }
require_machine "$machine"

# The libsecp256k1 commit that secp256k1-sys bundles (its depend/secp256k1-HEAD-revision.txt), for
# the secp256k1-sys this crate builds (the resolve graph: Cargo.lock also lists unused versions).
sys_dir=$(cargo metadata --format-version 1 --locked --manifest-path rust/Cargo.toml |
  python3 -I -c 'import json, os, sys
m = json.load(sys.stdin)
nodes = {n["id"]: n for n in m["resolve"]["nodes"]}
pkgs = {p["id"]: p for p in m["packages"]}
def dep(node, name):
    return next(d["pkg"] for d in nodes[node]["deps"] if pkgs[d["pkg"]]["name"] == name)
sys_id = dep(dep(m["resolve"]["root"], "secp256k1"), "secp256k1-sys")
print(os.path.dirname(pkgs[sys_id]["manifest_path"]))')
libsecp=$(grep -E '^[0-9a-f]{40}$' "$sys_dir/depend/secp256k1-HEAD-revision.txt" | cut -c1-7 || echo unknown)

mkdir -p "$(dirname "$out")"
"$bin" run "${inputs[@]}" "${library[@]}" --device "$device" --machine "$machine" \
  --harness "$(harness_id)" --libsecp256k1-commit "$libsecp" $quick > "$out.tmp"
mv "$out.tmp" "$out"
echo "wrote $out" >&2
