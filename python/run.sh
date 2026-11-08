#!/usr/bin/env bash
# The python layer: CPython calling the Python BIP-340 libraries (python/README.md).
#
#   python/run.sh [--quick] --device NAME --machine dedicated|shared --out FILE.json
#   python/run.sh --check                 the cross-check: no timing, exit 1 on an unexplained wrong result
#
# Options passed through: --library NAME (one library, plus the libsecp256k1 reference that
# derives the inputs), --jobs N (processes for the checks; default: every CPU).
# PYTHON picks the interpreter (default python3.13, else python3; 3.11 or newer).
set -euo pipefail
cd "$(dirname "$0")/.."

device="" machine="" out="" quick="" check="" extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="$2"; shift ;;
    --machine) machine="$2"; shift ;;
    --out) out="$2"; shift ;;
    --quick) quick="--quick" ;;
    --check) check=1 ;;
    --library|--jobs) extra+=("$1" "$2"); shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
. scripts/common.sh

python="${PYTHON:-$(command -v python3.13 || command -v python3)}"
"$python" -c 'import sys; sys.exit(sys.version_info < (3, 11))' || { echo "$python is older than 3.11" >&2; exit 2; }

# The venv, rebuilt when the interpreter or requirements.txt change.
venv=python/.venv
stamp="$("$python" -VV | tr -d '\n') $(sha256sum python/requirements.txt 2>/dev/null || shasum -a 256 python/requirements.txt)"
if [ ! -x "$venv/bin/python" ] || [ "$(cat "$venv/.stamp" 2>/dev/null)" != "$stamp" ]; then
  rm -rf "$venv"
  "$python" -m venv "$venv"
  "$venv/bin/python" -m pip install --quiet --disable-pip-version-check -r python/requirements.txt >&2
  echo "$stamp" > "$venv/.stamp"
fi

if [ -n "$check" ]; then
  exec "$venv/bin/python" python/bench.py check ${extra[@]+"${extra[@]}"}
fi

[ -n "$device" ] || { echo "--device is required (what the machine is, e.g. \"ThinkPad X1 Gen 11\")" >&2; exit 2; }
require_machine "$machine"
[ -n "$out" ] || { echo "--out FILE.json is required" >&2; exit 2; }
mkdir -p "$(dirname "$out")"
"$venv/bin/python" python/bench.py run $quick --device "$device" --machine "$machine" \
  --harness "$(harness_id)" ${extra[@]+"${extra[@]}"} > "$out.tmp"
mv "$out.tmp" "$out"
echo "wrote $out" >&2
