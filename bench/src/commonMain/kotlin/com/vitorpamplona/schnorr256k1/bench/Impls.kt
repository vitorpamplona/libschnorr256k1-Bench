/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.schnorr256k1.bench

import com.vitorpamplona.schnorr256k1.kotlin.Schnorr256k1 as KotlinLib
import fr.acinq.secp256k1.Secp256k1 as Acinq

/** libsecp256k1 through ACINQ's secp256k1-kmp: JNI on the JVM and Android, cinterop on native. */
class AcinqImpl(
    override val binding: String,
) : Impl {
    private val s = Acinq.get()
    private val h02 = byteArrayOf(0x02)
    override val library = "libsecp256k1"
    override val version = "secp256k1-kmp 0.24.0"

    private inline fun <T> orNull(f: () -> T): T? =
        try {
            f()
        } catch (_: Exception) {
            null
        }

    override fun pubkeyCreate(seckey: ByteArray) = orNull { s.pubkeyCreate(seckey) }

    override fun pubkeyCompress(pub65: ByteArray) = orNull { s.pubKeyCompress(pub65) }

    override fun seckeyVerify(seckey: ByteArray) = s.secKeyVerify(seckey)

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = orNull { s.signSchnorr(msg, seckey, aux) }

    // libsecp256k1's API always derives the key pair from the secret.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        pub33: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray,
    ) = sign(msg, seckey, aux)

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = orNull { s.verifySchnorr(sig, msg, xOnly) } ?: false

    override val hasVerifyFast = false

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = verify(sig, msg, xOnly)

    override val hasVerifyBatch = false

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ) = false

    // As Quartz does through ACINQ: tweak-mul 02 || x, keep x.
    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = orNull { s.pubKeyTweakMul(h02 + xOnly, seckey).copyOfRange(1, 33) }

    // ACINQ returns 65 bytes whatever the input: an app compresses it.
    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ) = orNull { s.pubKeyCompress(s.pubKeyTweakMul(pub33, tweak)) }

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = orNull { s.privKeyTweakAdd(seckey, tweak) }
}

/** libschnorr256k1-kotlin, pure Kotlin. */
object KotlinImpl : Impl {
    override val library = "libschnorr256k1-kotlin"
    override val binding = "kotlin"
    override val version = "schnorr256k1-kotlin ${VectorsV1.KOTLIN_LIB_COMMIT}"

    override fun pubkeyCreate(seckey: ByteArray) = KotlinLib.pubkeyCreate(seckey)

    override fun pubkeyCompress(pub65: ByteArray) = KotlinLib.pubkeyCompress(pub65)

    override fun seckeyVerify(seckey: ByteArray) = KotlinLib.seckeyVerify(seckey)

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = KotlinLib.schnorrSign(msg, seckey, aux)

    // The compressed key carries the y parity, so signing skips deriving the key.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        pub33: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray,
    ) = KotlinLib.schnorrSignWithPubKey(msg, seckey, pub33, aux)

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = KotlinLib.schnorrVerify(sig, msg, xOnly)

    override val hasVerifyFast = true

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = KotlinLib.schnorrVerifyFast(sig, msg, xOnly)

    override val hasVerifyBatch = true

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ) = KotlinLib.schnorrVerifyBatch(xOnly, sigs, msgs)

    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = KotlinLib.ecdhXOnly(xOnly, seckey)

    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ) = KotlinLib.pubkeyTweakMul(pub33, tweak)

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = KotlinLib.privkeyTweakAdd(seckey, tweak)
}
