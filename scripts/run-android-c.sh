#!/usr/bin/env bash
# The c layer on Android: c/ built with the NDK for the connected device's ABI and run over adb,
# C calling C with no ART in between. Writes results/c/<date>-<device>.json, beside the host C
# results (context.os tells them apart).
#
#   scripts/run-android-c.sh --machine dedicated
#
# Needs the NDK: $ANDROID_NDK_HOME, or the newest $ANDROID_HOME/ndk/<version>
# (sdkmanager "ndk;<version>"). The same build flags as libschnorr256k1-kmp's Android libraries.
set -euo pipefail
cd "$(dirname "$0")/.."
. scripts/common.sh

machine=""
quick=""
out="results"
api=26 # gradle/libs.versions.toml android-minSdk
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

ndk="${ANDROID_NDK_HOME:-}"
if [ -z "$ndk" ] && [ -d "${ANDROID_HOME:-/nonexistent}/ndk" ]; then
  ndk=$(ls -d "$ANDROID_HOME"/ndk/*/ | sort -V | tail -1)
fi
[ -f "$ndk/build/cmake/android.toolchain.cmake" ] || { echo "no NDK: set ANDROID_NDK_HOME" >&2; exit 1; }

ensure_submodules
abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
device="$(adb shell getprop ro.product.manufacturer | tr -d '\r') $(adb shell getprop ro.product.model | tr -d '\r')"
build="build/c-android-$abi"
cmake -S c -B "$build" -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$abi" -DANDROID_PLATFORM="android-$api" > /dev/null
cmake --build "$build" --parallel > /dev/null

dir=/data/local/tmp/schnorr256k1-bench
adb shell mkdir -p "$dir"
adb push "$build/bench" vectors/v1.txt datasets/nostr-v1.bin datasets/nostr-v1.txt "$dir/" > /dev/null
file=$(result_file "$out" c "$device" "$quick")
adb shell "cd $dir && ./bench run --vectors v1.txt --dataset nostr-v1.bin --dataset-digests nostr-v1.txt --device '$device' --machine $machine --harness $(harness_id) $quick" > "$file.tmp"
tr -d '\r' < "$file.tmp" > "$file"
rm "$file.tmp"
echo "wrote $file" >&2
