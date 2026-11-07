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

import com.vitorpamplona.schnorr256k1.Schnorr256k1 as CLib
import com.vitorpamplona.schnorr256k1.kotlin.Schnorr256k1 as KotlinLib
import fr.acinq.secp256k1.Secp256k1 as Acinq

/**
 * One implementation under test. Every operation takes and returns the same byte formats as
 * libschnorr256k1-kmp: 32-byte secret keys, 65-byte uncompressed and 33-byte compressed public
 * keys, 32-byte x-only keys, 64-byte signatures. Messages are 32 bytes (ACINQ's limit).
 */
interface Impl {
    val name: String

    fun pubkeyCreate(seckey: ByteArray): ByteArray

    fun pubkeyCompress(pubkey: ByteArray): ByteArray

    fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray?,
    ): ByteArray

    /** The cheapest signing call when the caller already holds its public key. */
    fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        compressed: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray?,
    ): ByteArray

    fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ): Boolean

    /** Verify without BIP-340's y-parity check of R, or a full verify if there is none. */
    fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ): Boolean

    /** Same-key batch verification, or null if the implementation has none. */
    fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ): Boolean?

    /** x coordinate of seckey·P, P lifted (even y) from an x-only key: NIP-04/NIP-44's shared secret. */
    fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ): ByteArray

    fun tweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray

    fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray

    companion object {
        val all: List<Impl> by lazy { listOf(AcinqImpl, CImpl, KotlinImpl) }

        fun named(name: String): Impl = all.first { it.name == name }
    }
}

/** libsecp256k1 through ACINQ's secp256k1-kmp (JNI on the JVM, cinterop on native). */
object AcinqImpl : Impl {
    private val s = Acinq.get()
    private val h02 = byteArrayOf(0x02)
    override val name = "acinq"

    override fun pubkeyCreate(seckey: ByteArray) = s.pubkeyCreate(seckey)

    override fun pubkeyCompress(pubkey: ByteArray) = s.pubKeyCompress(pubkey)

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray?,
    ) = s.signSchnorr(msg, seckey, aux)

    // libsecp256k1's API always derives the key pair from the secret.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        compressed: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray?,
    ) = s.signSchnorr(msg, seckey, aux)

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = s.verifySchnorr(sig, msg, xOnly)

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = s.verifySchnorr(sig, msg, xOnly)

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ): Boolean? = null

    // How Quartz's ACINQ wrapper does x-only ECDH: tweak-mul 02||x, keep x.
    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = s.pubKeyTweakMul(h02 + xOnly, seckey).copyOfRange(1, 33)

    // ACINQ returns 65 bytes whatever the input; keep the input's format like the others.
    override fun tweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray {
        val r = s.pubKeyTweakMul(pubkey, tweak)
        return if (pubkey.size == 33) s.pubKeyCompress(r) else r
    }

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = s.privKeyTweakAdd(seckey, tweak)
}

/** libschnorr256k1 (C) through libschnorr256k1-kmp. */
object CImpl : Impl {
    override val name = "libschnorr256k1"

    init {
        CLib.ensureLoaded()
    }

    override fun pubkeyCreate(seckey: ByteArray) = CLib.pubkeyCreate(seckey)!!

    override fun pubkeyCompress(pubkey: ByteArray) = CLib.pubkeyCompress(pubkey)!!

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray?,
    ) = CLib.schnorrSign(msg, seckey, aux)!!

    // Note: secp256k1c_schnorr_sign_xonly returns invalid signatures for odd-y keys.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        compressed: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray?,
    ) = CLib.schnorrSignXOnly(msg, seckey, xOnly, aux)!!

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = CLib.schnorrVerify(sig, msg, xOnly)

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = CLib.schnorrVerifyFast(sig, msg, xOnly)

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ): Boolean? = CLib.schnorrVerifyBatch(xOnly, sigs, msgs)

    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = CLib.ecdhXOnly(xOnly, seckey)!!

    override fun tweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ) = CLib.pubkeyTweakMul(pubkey, tweak)!!

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = CLib.privkeyTweakAdd(seckey, tweak)!!
}

/** Pure Kotlin, libschnorr256k1-kotlin. */
object KotlinImpl : Impl {
    override val name = "kotlin"

    override fun pubkeyCreate(seckey: ByteArray) = KotlinLib.pubkeyCreate(seckey)!!

    override fun pubkeyCompress(pubkey: ByteArray) = KotlinLib.pubkeyCompress(pubkey)!!

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray?,
    ) = KotlinLib.schnorrSign(msg, seckey, aux)!!

    // The compressed key carries the y parity, so signing skips deriving the key.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        compressed: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray?,
    ) = KotlinLib.schnorrSignWithPubKey(msg, seckey, compressed, aux)!!

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = KotlinLib.schnorrVerify(sig, msg, xOnly)

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = KotlinLib.schnorrVerifyFast(sig, msg, xOnly)

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ): Boolean? = KotlinLib.schnorrVerifyBatch(xOnly, sigs, msgs)

    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = KotlinLib.ecdhXOnly(xOnly, seckey)!!

    override fun tweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ) = KotlinLib.pubkeyTweakMul(pubkey, tweak)!!

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = KotlinLib.privkeyTweakAdd(seckey, tweak)!!
}
