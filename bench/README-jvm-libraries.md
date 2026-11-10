# More libraries on the `jvm` layer

The `jvm` layer (bench/, `:bench:runJvm`, `scripts/run-host.sh --layers jvm`) runs, besides
libsecp256k1 (ACINQ), libschnorr256k1 (libschnorr256k1-kmp) and libschnorr256k1-kotlin, every
other BIP-340 implementation a JVM app can take from a Maven repository. They run in the same
`Runner` (the twin of c/main.c): same vectors, same dataset, same checks, same timing.

| `library` | `binding` | Package (resolved) | License | What it is |
|---|---|---|---|---|
| `bouncycastle` | `java` | `org.bouncycastle:bcprov-jdk18on:1.86` | MIT | Bouncy Castle: `BIP340Signer` (added in 1.85) on its `SecP256K1Curve` (custom field, GLV, fixed-point comb). |
| `bitcoin-s` | `scala` | `org.bitcoin-s:bitcoin-s-crypto_2.13:1.9.12`, on bcprov 1.86 | MIT | bitcoin-s's own BIP-340 (`CryptoRuntime.schnorrSign`/`schnorrVerify`, scalar math in its `FieldElement`) over Bouncy Castle's points, through its `BouncycastleCryptoRuntime`. |
| `nostr-java` | `java` | `xyz.tcheeric:nostr-java-core:2.4.2`, on bcprov 1.86 | MIT | nostr-java's `nostr.crypto.schnorr.Schnorr`: a port of the BIP-340 reference code on its own `BigInteger` affine points (`nostr.crypto.Point`, one modular exponentiation per point addition). |

Versions are pinned in `gradle/libs.versions.toml`. bitcoin-s (built against bcprov 1.83) and
nostr-java (1.81) run on the bcprov the bench pins, 1.86, since Gradle resolves one version.

## Calls

Each row is made the way an app calls that library. "no row": the library has no call for it,
and the harness does not build one from arithmetic (`Impl.lacks`, below).

| Op | `bouncycastle` | `bitcoin-s` | `nostr-java` |
|---|---|---|---|
| `verify.sameKey`, `verify.manyKeys`, `nostr.verify` | `BIP340Signer.decodePublicKey(x)`, `new BIP340Signer()`, `init(false, pub)`, `update(msg)`, `verifySignature(sig)` | `SchnorrPublicKey(x).verify(msg, SchnorrDigitalSignature.fromBytes(sig))` | `Schnorr.verify(msg, x, sig)` |
| `verifyFast.sameKey`, `nostr.verifyFast` | full verify (no fast variant) | full verify | full verify |
| `verifyBatch200.perSig`, `nostr.verifyBatchByAuthor` | no row (no batch verify) | no row | no row |
| `sign` | `new BIP340Signer()`, `init(true, ParametersWithRandom(ECPrivateKeyParameters(d, BIP340Signer.getDomain()), aux))`, `update(msg)`, `generateSignature()` (init derives d·G) | `ECPrivateKey(sk).schnorrSign(msg, aux)` | `Schnorr.sign(msg, sk, aux)` (it verifies the signature before returning it) |
| `sign.knownPub` | `init(true, AsymmetricCipherKeyPair(ECPublicKeyParameters(curve.decodePoint(pub33)), priv), aux)`: the key-pair init skips d·G | same as `sign` (no variant) | same as `sign` (no variant) |
| `pubkey.create` | `ECPrivateKeyParameters(d, domain)` (checks 1 ≤ d < n), `FixedPointCombMultiplier().multiply(G, d).getEncoded(false)` | `CryptoUtil.publicKey(ECPrivateKeyBytes(sk, isCompressed = false))` | no row (`Schnorr.genPubKey` returns the x-only key only) |
| `pubkey.compress` | `curve.decodePoint(pub65).getEncoded(true)` | `ECPublicKey(pub65).compressed` | no row |
| `seckey.verify` | `ECPrivateKeyParameters(d, domain)` constructs (it checks 1 ≤ d < n) | `CryptoUtil.secKeyVerify(sk)` | no row |
| `ecdh.xOnly`, `nostr.ecdh` | `BIP340Signer.decodePublicKey(x)` (lifts 02 ‖ x), `ECDHBasicAgreement.init(priv)`, `calculateAgreement(pub)` (x of d·Q, constant-time multiplier) | as libsecp256k1's: `ECPublicKey(02 ‖ x).multiply(FieldElement(sk))`, keep x | no row (its NIP-04/NIP-44 code does the multiplication in Bouncy Castle, privately) |
| `tweak.mul` | `curve.decodePoint(pub33).multiply(t).getEncoded(true)`, t checked by `ECPrivateKeyParameters` | `ECPublicKey(pub33).multiply(FieldElement(t))` (compressed in, compressed out) | no row |
| `tweak.add` | no row (no scalar add in its API) | `ECPrivateKey(sk).add(ECPrivateKey(t))` | no row |

Bouncy Castle's `BIP340Signer` draws `aux_rand` from the `SecureRandom` it is initialised with;
the adapter hands it one that returns the vectors' 32 bytes (as secp-bouncy and Bouncy Castle's
tests do), created once.

bitcoin-s chooses its runtime when it loads: its own libsecp256k1 JNI
(`bitcoin-s-secp256k1jni`, which ships the native libraries) unless `DISABLE_SECP256K1=true`.
That would be a second libsecp256k1 binding on this layer, so the Gradle tasks that run the
layer (`:bench:runJvm` and the JVM tests) set the variable, and `BitcoinSImpl` refuses to start
on any runtime other than `BouncycastleCryptoRuntime`.

Known-issue probes: none of the three has a batch or a fast verify (KI-1 and KI-4 `null`) and none
signs wrongly for odd-y keys (KI-2 `false`). KI-3 is probed on `bouncycastle` and `bitcoin-s` (`false`), and is
`null` on `nostr-java`, which has no tweak-mul.

## `Impl.lacks`

Before these libraries, every library had every operation but batch verify. `Impl.lacks` (default
empty) names the operations a library has no call for: `Runner.run` returns false for them (no
timing, no row, as for batch verify), `unexplainedFailures` skips their checks, and the KI-3
probe reports `null` when `tweak.mul` is lacking. Nothing changes for the existing libraries.

## Cost

nostr-java is slow (about 11 ms per verify, 20 ms per sign on the cloud VM), and the checks
verify the 29,614 dataset events twice (valid and tampered), so it adds about 15 minutes to
every `jvm` run and to `:bench:jvmTest` (19 minutes in all on the 4-vCPU cloud VM, most of it
nostr-java's checks).

## Android

Bouncy Castle (bcprov-jdk18on is Java 8 bytecode and runs on ART; Android's own copy is
repackaged, so it does not clash) and nostr-java (Java 21 bytecode: needs D8 desugaring of the
APIs it uses) would also run on the `android` layer; neither is added there. bitcoin-s targets
the JVM (and Scala.js): its Scala runtime has no Android build and it is not tried there.

## Candidates left out

| Candidate | Why |
|---|---|
| secp256k1-jdk `secp-bouncy` 0.3.1 (bitcoinj) | A thin wrapper over Bouncy Castle's `BIP340Signer` (since 0.3.1) and point API: a near-duplicate of `bouncycastle`. Also published only to GitLab's package registry, not Maven Central, and self-described as a prototype. Its ECDH is hashed (libsecp256k1's default) and it has no tweak-add. Apache-2.0. |
| secp256k1-jdk `secp-ffm` 0.3.1 | Needs JDK 25 (Panama FFM, `release 25`); the bench targets JDK 21. It would also be a second libsecp256k1 binding next to ACINQ's. |
| bitcoinj-core 0.17.1 | No BIP-340: grep of v0.17.1 finds Schnorr only in a comment. Apache-2.0. |
| bitcoin-s on `LibSecp256k1CryptoRuntime` (`bitcoin-s-secp256k1jni`) | libsecp256k1 through bitcoin-s's JNI: a second libsecp256k1 binding on the layer. |
| rust-nostr `org.rust-nostr:nostr-sdk-jvm` 0.45.0-alpha.7 | UniFFI over rust-nostr, whose signatures come from rust-secp256k1, i.e. libsecp256k1: a second libsecp256k1 binding, and an alpha. |
| nostr4j 0.4.1 (`org.ngengine:nge-platform-jvm` 0.2.6) | Its JVM Schnorr is a package-private class derived from nostr-java's `Point`/`Schnorr` (Jacobian points added); the public API (`JVMAsyncPlatform.schnorrSign(hex, key)`) draws its own `aux_rand`, so its signatures cannot be checked against v1, and it pulls native WebRTC and allocator libraries. BSD-3-Clause. |
| ACINQ bitcoin-kmp, app.cash nostrino, Quartz | Use ACINQ's secp256k1-kmp, i.e. libsecp256k1. |
| io.eqoty.kryptools `secp256k1` 1.0.0 | Kotlin Multiplatform, but ECDSA only (no Schnorr). |
| Bouncy Castle without `BIP340Signer` (older than 1.85) | Point math only: the harness would have to write BIP-340. |
| Other pure Kotlin Multiplatform BIP-340 | None found on Maven Central besides libschnorr256k1-kotlin. |
