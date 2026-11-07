#!/usr/bin/env bash
# The android layer (results/SCHEMA.md): the Kotlin runner inside a release instrumentation test
# on the connected device (adb), libsecp256k1 and libschnorr256k1 through JNI and pure Kotlin.
# Also runs AndroidCrossCheckTest. Writes results/android/<date>-<device>.json.
#
#   scripts/run-android.sh --machine dedicated            # one device connected (adb devices)
#   ANDROID_SERIAL=emulator-5554 scripts/run-android.sh --quick --machine shared
#
# A dedicated run: the phone charged and cool, screen on, nothing else open, flight mode. Emulator
# numbers say nothing about phones; an emulator run is a correctness check (--quick).
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/common.sh

machine=""
quick=""
out="results"
while [ $# -gt 0 ]; do
  case "$1" in
    --machine) machine="$2"; shift ;;
    --quick) quick="--quick" ;;
    --out) out="$2"; shift ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
  shift
done
require_machine "$machine"
ensure_submodules

outputs=bench-android/build/outputs/connected_android_test_additional_output
rm -rf "$outputs"
./gradlew --console=plain :bench-android:connectedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.quick="$([ -n "$quick" ] && echo true || echo false)" \
  -Pandroid.testInstrumentationRunnerArguments.machine="$machine" \
  -Pandroid.testInstrumentationRunnerArguments.harness="$(harness_id)"

found=0
while IFS= read -r json; do
  device=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["context"]["device"])' "$json")
  file=$(result_file "$out" android "$device" "$quick")
  cp "$json" "$file"
  echo "wrote $file" >&2
  found=1
done < <(find "$outputs" -name schnorr256k1-bench-android.json)
[ "$found" = 1 ] || { echo "no result in $outputs" >&2; exit 1; }
