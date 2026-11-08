#!/usr/bin/env bash
# The node and bun layers (js/README.md): installs the pinned packages (package-lock.json) and
# runs the JavaScript twin of c/main.c on one runtime.
#
#   js/run.sh --runtime node --device "ThinkPad X1 Gen 11" --machine dedicated --out results/node/2026-11-08-thinkpad.json
#   js/run.sh --runtime bun --quick --device "CI runner" --machine shared --out /tmp/bun.json
#   js/run.sh --runtime node --check                   # the checks without timing; exit 1 on a wrong result
#
# --library NAME runs one library (libsecp256k1 | noble-curves | noble-secp256k1).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/.." && pwd)"

runtime=""
device=""
machine=""
out=""
quick=""
check=""
library=()
while [ $# -gt 0 ]; do
  case "$1" in
    --runtime) runtime="$2"; shift ;;
    --device) device="$2"; shift ;;
    --machine) machine="$2"; shift ;;
    --out) out="$2"; shift ;;
    --library) library=(--library "$2"); shift ;;
    --quick) quick="--quick" ;;
    --check) check=1 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
case "$runtime" in
  node|bun) command -v "$runtime" > /dev/null || { echo "$runtime is not installed" >&2; exit 2; } ;;
  *) echo "--runtime node|bun is required" >&2; exit 2 ;;
esac

# The same node_modules for both runtimes, from the lockfile (npm ci), installed once.
stamp="$here/node_modules/.package-lock.json"
if [ ! -f "$stamp" ] || [ "$here/package-lock.json" -nt "$stamp" ]; then
  (cd "$here" && npm ci --no-audit --no-fund --loglevel=error) >&2
fi

inputs=(--vectors "$root/vectors/v1.txt" --dataset "$root/datasets/nostr-v1.bin" --dataset-digests "$root/datasets/nostr-v1.txt")

if [ -n "$check" ]; then
  exec "$runtime" "$here/src/main.js" check "${inputs[@]}" "${library[@]}"
fi

[ -n "$device" ] || { echo "--device is required (what the machine is, e.g. \"ThinkPad X1 Gen 11\")" >&2; exit 2; }
[ -n "$out" ] || { echo "--out FILE.json is required" >&2; exit 2; }
cd "$root"
. scripts/common.sh
require_machine "$machine"
harness=$(harness_id)

mkdir -p "$(dirname "$out")"
"$runtime" "$here/src/main.js" run "${inputs[@]}" "${library[@]}" $quick \
  --device "$device" --machine "$machine" --harness "$harness" > "$out.tmp"
mv "$out.tmp" "$out"
echo "wrote $out" >&2
