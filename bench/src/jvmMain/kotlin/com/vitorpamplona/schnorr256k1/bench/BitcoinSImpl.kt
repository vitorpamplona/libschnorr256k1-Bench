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

import org.bitcoins.crypto.`BouncycastleCryptoRuntime$`
import org.bitcoins.crypto.`CryptoContext$`
import org.bitcoins.crypto.`CryptoUtil$`
import org.bitcoins.crypto.ECPrivateKey
import org.bitcoins.crypto.ECPrivateKeyBytes
import org.bitcoins.crypto.ECPublicKey
import org.bitcoins.crypto.FieldElement
import org.bitcoins.crypto.SchnorrDigitalSignature
import org.bitcoins.crypto.SchnorrPublicKey
import scodec.bits.ByteVector

/**
 * bitcoin-s-crypto (Scala), on its Bouncy Castle runtime: bitcoin-s's own BIP-340
 * (`CryptoRuntime.schnorrSign`/`schnorrVerify`) over Bouncy Castle's point arithmetic. Calls go
 * through its key classes, as a bitcoin-s app makes them (bench/README-jvm-libraries.md).
 *
 * bitcoin-s picks libsecp256k1 (its own JNI) when that loads, unless DISABLE_SECP256K1 is set:
 * that would be a second libsecp256k1 binding, so the Gradle tasks set it and this checks it.
 */
object BitcoinSImpl : Impl {
    override val library = "bitcoin-s"
    override val binding = "scala"
    override val version = "bitcoin-s-crypto_2.13 1.9.12 (Bouncy Castle runtime, bcprov-jdk18on 1.86)"

    private val crypto = `CryptoUtil$`.`MODULE$`

    init {
        val runtime = `CryptoContext$`.`MODULE$`.cryptoRuntime()
        check(runtime === `BouncycastleCryptoRuntime$`.`MODULE$`) {
            "bitcoin-s runs on ${runtime.cryptoContext()}, not Bouncy Castle: set DISABLE_SECP256K1=true"
        }
    }

    private inline fun <T> orNull(f: () -> T): T? =
        try {
            f()
        } catch (_: Exception) {
            null
        }

    private fun bv(b: ByteArray) = ByteVector.view(b)

    override fun pubkeyCreate(seckey: ByteArray) = orNull { crypto.publicKey(ECPrivateKeyBytes(bv(seckey), false)).bytes().toArray() }

    override fun pubkeyCompress(pub65: ByteArray) = orNull { ECPublicKey(bv(pub65)).compressed().bytes().toArray() }

    override fun seckeyVerify(seckey: ByteArray) = seckey.size == 32 && crypto.secKeyVerify(bv(seckey))

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = orNull { ECPrivateKey(bv(seckey)).schnorrSign(bv(msg), bv(aux)).bytes().toArray() }

    // No variant that takes the public key.
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
    ) = orNull { SchnorrPublicKey(bv(xOnly)).verify(bv(msg), SchnorrDigitalSignature.fromBytes(bv(sig))) } ?: false

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

    // As for libsecp256k1: tweak-multiply 02 || x, keep x.
    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = orNull {
        ECPublicKey(bv(byteArrayOf(0x02) + xOnly))
            .multiply(FieldElement(bv(seckey)))
            .bytes()
            .toArray()
            .copyOfRange(1, 33)
    }

    // A compressed key multiplies to a compressed key.
    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ) = orNull { ECPublicKey(bv(pub33)).multiply(FieldElement(bv(tweak))).bytes().toArray() }

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = orNull { ECPrivateKey(bv(seckey)).add(ECPrivateKey(bv(tweak))).bytes().toArray() }
}
