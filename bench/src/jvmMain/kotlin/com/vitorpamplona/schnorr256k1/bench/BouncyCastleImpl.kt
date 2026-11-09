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

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.ParametersWithRandom
import org.bouncycastle.crypto.signers.BIP340Signer
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.util.BigIntegers
import java.security.SecureRandom

/**
 * Bouncy Castle (bcprov-jdk18on), pure Java: BIP-340 through its `BIP340Signer`, the other
 * operations through its EC point API (bench/README-jvm-libraries.md lists every call). It has
 * no scalar tweak-add, so no `tweak.add` row.
 */
object BouncyCastleImpl : Impl {
    override val library = "bouncycastle"
    override val binding = "java"
    override val version = "bcprov-jdk18on 1.86"

    private val domain = BIP340Signer.getDomain()
    private val curve = domain.curve
    private val baseMul = FixedPointCombMultiplier()

    /**
     * BIP340Signer draws `aux_rand` from the SecureRandom it is given: this one hands it the
     * vectors' 32 bytes, as Bouncy Castle's own tests and secp-bouncy do.
     */
    private object Aux : SecureRandom() {
        var next: ByteArray = ByteArray(32)

        override fun nextBytes(bytes: ByteArray) {
            next.copyInto(bytes)
        }
    }

    override val lacks = setOf(Runner.Op.TWEAK_ADD)

    private inline fun <T> orNull(f: () -> T): T? =
        try {
            f()
        } catch (_: RuntimeException) {
            null
        }

    /** The constructor checks 1 <= d < n. */
    private fun priv(seckey: ByteArray) = ECPrivateKeyParameters(BigIntegers.fromUnsignedByteArray(seckey), domain)

    override fun pubkeyCreate(seckey: ByteArray) = orNull { baseMul.multiply(domain.g, priv(seckey).d).getEncoded(false) }

    override fun pubkeyCompress(pub65: ByteArray) = orNull { curve.decodePoint(pub65).getEncoded(true) }

    override fun seckeyVerify(seckey: ByteArray) = seckey.size == 32 && orNull { priv(seckey) } != null

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = orNull {
        Aux.next = aux
        val s = BIP340Signer()
        s.init(true, ParametersWithRandom(priv(seckey), Aux))
        s.update(msg, 0, msg.size)
        s.generateSignature()
    }

    // init with a key pair skips the d*G that init with a private key does.
    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        pub33: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray,
    ) = orNull {
        Aux.next = aux
        val s = BIP340Signer()
        s.init(true, AsymmetricCipherKeyPair(ECPublicKeyParameters(curve.decodePoint(pub33), domain), priv(seckey)), Aux)
        s.update(msg, 0, msg.size)
        s.generateSignature()
    }

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ): Boolean {
        val pub = BIP340Signer.decodePublicKey(xOnly) ?: return false
        return orNull {
            val s = BIP340Signer()
            s.init(false, pub)
            s.update(msg, 0, msg.size)
            s.verifySignature(sig)
        } ?: false
    }

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

    // ECDHBasicAgreement returns x of d*Q: lift x with BIP340Signer.decodePublicKey (02 || x).
    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = orNull {
        val pub = BIP340Signer.decodePublicKey(xOnly) ?: return@orNull null
        val agreement = ECDHBasicAgreement()
        agreement.init(priv(seckey))
        BigIntegers.asUnsignedByteArray(32, agreement.calculateAgreement(pub))
    }

    // The curve's default multiplier (GLV for secp256k1); infinity encodes as one byte.
    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ) = orNull {
        curve
            .decodePoint(pub33)
            .multiply(priv(tweak).d)
            .getEncoded(true)
            .takeIf { it.size == 33 }
    }

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray? = null
}
