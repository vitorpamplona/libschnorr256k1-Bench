#!/usr/bin/env bash
# The go layer (go/README.md): builds the Go harness (libsecp256k1 compiled in through cgo from
# third_party/secp256k1) and runs it.
#
#   go/run.sh [--quick] --device "ThinkPad X1 Gen 11" --machine dedicated --out results/go/x.json
#   go/run.sh --check          the checks and probes without timing; exits 1 on a wrong result
#                              that no observed known issue explains
#
# Needs Go (go.mod's toolchain line: an older go downloads it, GOTOOLCHAIN=auto) and a C compiler.
set -euo pipefail
cd "$(dirname "$0")/.."

device=""
machine=""
quick=""
out=""
mode="run"
library=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="$2"; shift ;;
    --machine) machine="$2"; shift ;;
    --quick) quick="--quick" ;;
    --out) out="$2"; shift ;;
    --library) library=(--library "$2"); shift ;;
    --check) mode="check" ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
. scripts/common.sh
if [ "$mode" = run ]; then
  [ -n "$device" ] || { echo "--device is required (what the machine is, e.g. \"ThinkPad X1 Gen 11\")" >&2; exit 2; }
  [ -n "$out" ] || { echo "--out FILE.json is required" >&2; exit 2; }
  require_machine "$machine"
fi

if git submodule status third_party/secp256k1 | grep -q '^-'; then
  git submodule update --init third_party/secp256k1
fi
secp="$(git -C third_party/secp256k1 rev-parse --short HEAD)$(git -C third_party/secp256k1 diff --quiet HEAD || echo "-dirty")"
cc="$(cd go && CGO_ENABLED=1 go env CC)"
ccver="$cc $("$cc" -dumpfullversion 2>/dev/null || "$cc" -dumpversion)"

mkdir -p build/go
(cd go && CGO_ENABLED=1 go build -trimpath -o ../build/go/bench \
  -ldflags "-X 'main.libsecp256k1Version=$secp' -X 'main.cgoCompiler=$ccver'" .)

args=(--vectors vectors/v1.txt --dataset datasets/nostr-v1.bin --dataset-digests datasets/nostr-v1.txt ${library[@]+"${library[@]}"})
if [ "$mode" = check ]; then
  exec build/go/bench check "${args[@]}"
fi
mkdir -p "$(dirname "$out")"
build/go/bench run "${args[@]}" --device "$device" --machine "$machine" --harness "$(harness_id)" $quick > "$out.tmp"
mv "$out.tmp" "$out"
echo "wrote $out" >&2
