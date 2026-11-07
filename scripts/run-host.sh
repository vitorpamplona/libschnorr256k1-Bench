#!/usr/bin/env bash
# Runs the host layers (results/SCHEMA.md): c, jvm and native, on this machine, and writes each
# result to results/<layer>/<date>-<device>.json.
#
#   scripts/run-host.sh --device "MacBook Pro M3 Pro" --machine dedicated
#   scripts/run-host.sh --quick --device "CI runner" --machine shared --layers "c jvm"
#
# --machine says whether anything else ran meanwhile (results/SCHEMA.md); numbers to publish come
# from a dedicated machine on AC power with nothing else running.
set -euo pipefail
cd "$(dirname "$0")/.."

device=""
machine=""
quick=""
layers="c jvm native"
out="results"
while [ $# -gt 0 ]; do
  case "$1" in
    --device) device="$2"; shift ;;
    --machine) machine="$2"; shift ;;
    --quick) quick="--quick" ;;
    --layers) layers="$2"; shift ;;
    --out) out="$2"; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
[ -n "$device" ] || { echo "--device is required (what the machine is, e.g. \"ThinkPad X1 Gen 11\")" >&2; exit 2; }
. scripts/common.sh
require_machine "$machine"

ensure_submodules
harness=$(harness_id)

write() { # layer, then the command that prints the JSON
  local layer="$1"; shift
  local file
  file=$(result_file "$out" "$layer" "$device" "$quick")
  "$@" > "$file.tmp"
  mv "$file.tmp" "$file"
  echo "wrote $file" >&2
}

for layer in $layers; do
  case "$layer" in
    c)
      cmake -S c -B build/c -DCMAKE_BUILD_TYPE=Release > /dev/null
      cmake --build build/c --parallel > /dev/null
      write c build/c/bench run --vectors vectors/v1.txt --device "$device" --machine "$machine" --harness "$harness" $quick
      ;;
    jvm)
      write jvm ./gradlew --console=plain -q :bench:runJvm \
        -Pargs="$quick --device '$device' --machine $machine --harness $harness"
      ;;
    native)
      case "$(uname -s)-$(uname -m)" in
        Linux-x86_64) target=LinuxX64; dir=linuxX64 ;;
        Darwin-arm64) target=MacosArm64; dir=macosArm64 ;;
        *) echo "no Kotlin/Native target for $(uname -s)-$(uname -m)" >&2; exit 1 ;;
      esac
      ./gradlew --console=plain -q ":bench:linkReleaseExecutable$target"
      write native "bench/build/bin/$dir/releaseExecutable/bench.kexe" \
        $quick --device "$device" --machine "$machine" --harness "$harness"
      ;;
    *) echo "unknown layer $layer (c, jvm, native; android: scripts/run-android.sh)" >&2; exit 2 ;;
  esac
done
