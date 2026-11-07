# libschnorr256k1-bench

Benchmarks and cross-checks of BIP-340 Schnorr / secp256k1 implementations, on every layer an
app runs them from: C calling C, the JVM, Kotlin/Native and Android. Three libraries:

| `library` | What | Bindings |
|---|---|---|
| `libsecp256k1` | bitcoin-core's libsecp256k1 (the reference) | C; ACINQ's secp256k1-kmp (JNI, cinterop) |
| `libschnorr256k1` | libschnorr256k1 (C) | C; libschnorr256k1-kmp (JNI, cinterop on Apple) |
| `kotlin` | libschnorr256k1-kotlin (pure Kotlin), built from the submodule | none |

## Layout

- `vectors/`: the inputs (v1, derived from SHA-256 of labels) and `v1.txt`, the digests every
  library's outputs must match. `results/SCHEMA.md`: the frozen result format, the twelve
  operations with the exact call per library, and the known issues (KI-1..KI-3).
- `c/`: the c layer. `main.c` (checks, timing, known-issue probes, JSON), `vectors.c` (its own
  SHA-256 and the derivation), one `backend_*.c` per library, `CMakeLists.txt` (both libraries
  from `third_party/` with their own build flags).
- `bench/`: the Kotlin layers. `Impl.kt` (the interface), `Impls.kt` (ACINQ, Kotlin), `cMain/CImpl.kt`
  (libschnorr256k1-kmp, on JVM, Android and Apple only: 1.0.5 has no linuxX64 artifact),
  `Vectors.kt` (the twin of `vectors.c`), `Runner.kt` (the twin of `main.c`), `Main.kt`, and a
  `Platform` actual per target. `:bench:generateVectors` turns `vectors/v1.txt` into Kotlin.
  `commonTest/CrossCheckTest` runs the checks without the timing.
- `bench-android/`: the android layer, the Runner inside a release instrumentation test
  (`AndroidLayerBenchmark`), plus `AndroidCrossCheckTest`.
- `scripts/`: `run-host.sh` (c, jvm, native), `run-android.sh`, `run-android-c.sh` (NDK + adb),
  `table.py` (results to Markdown tables; `--check` for CI).
- `third_party/`: submodules. `libschnorr256k1-kotlin` (substituted for the Maven artifact in
  `settings.gradle.kts`), `secp256k1` (at the commit ACINQ 0.24.0 bundles), `libschnorr256k1`.

## Rules

- **Same inputs, same work, same method.** Every library gets the vectors' bytes and is asked
  for the same output format; when it lacks an operation, its adapter says how it is emulated
  (libsecp256k1's x-only ECDH is a tweak-mul of 02||x) or it has no row. The C and Kotlin
  runners time the same way (results/SCHEMA.md `samples`): change one, change the other.
- **The schema is frozen.** A new operation, column or statistic, or a change to what one
  measures, is schema v2 (results/SCHEMA.md); so is any change to the vectors (v2, a new digest
  file).
- **A wrong result is reported, never hidden.** A row with `correct: false` stays in the table.
  It fails the tests and `table.py --check` unless an observed known issue explains it
  (`Runner.COVERS`, `COVERS` in `table.py`: keep them equal). A new bug gets a KI-n entry in
  SCHEMA.md and a probe in both runners; a fixed one keeps its entry, and its probe reports `false`.
- **Only numbers from a dedicated machine are published** (`--machine dedicated`: nothing else
  running, on AC power or charged and cool). CI and cloud VMs are `shared` and run `--quick`.
- Allocation and cycle counts are not in the schema (not reliable on Android); they belong in the
  libraries' own tests.

## Run

```bash
git submodule update --init --recursive
./gradlew :bench:jvmTest :bench:linuxX64Test          # cross-checks (macOS: :bench:macosArm64Test)
scripts/run-host.sh --device "ThinkPad X1 Gen 11" --machine dedicated
scripts/run-android.sh --machine dedicated            # a device on adb
scripts/run-android-c.sh --machine dedicated          # needs the NDK
scripts/table.py                                      # every full run under results/
```

`--quick` on any script is a smoke run. The Android builds need `ANDROID_HOME` (the included
libschnorr256k1-kotlin build reads it too).

## Remotes and pull requests

The repo is on GitHub, `github.com/vitorpamplona/libschnorr256k1-bench`. Open a PR only when the
human you work for asks.
