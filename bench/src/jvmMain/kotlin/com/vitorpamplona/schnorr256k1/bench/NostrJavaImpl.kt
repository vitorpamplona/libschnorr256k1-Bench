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

import nostr.crypto.schnorr.Schnorr

/**
 * nostr-java (tcheeric), pure Java: its own BIP-340 (`nostr.crypto.schnorr.Schnorr`) on its own
 * BigInteger affine points (`nostr.crypto.Point`). Its API has signing, verification and an
 * x-only key derivation, nothing else, so it has rows for those only
 * (bench/README-jvm-libraries.md). Its sign verifies the signature before returning it.
 */
object NostrJavaImpl : Impl {
    override val library = "nostr-java"
    override val binding = "java"
    override val version = "nostr-java-core 2.4.2"

    override val lacks =
        setOf(
            Runner.Op.PUB_CREATE,
            Runner.Op.PUB_COMPRESS,
            Runner.Op.SECKEY_VERIFY,
            Runner.Op.ECDH,
            Runner.Op.TWEAK_MUL,
            Runner.Op.TWEAK_ADD,
            Runner.Op.NOSTR_ECDH,
        )

    override fun pubkeyCreate(seckey: ByteArray): ByteArray? = null

    override fun pubkeyCompress(pub65: ByteArray): ByteArray? = null

    override fun seckeyVerify(seckey: ByteArray) = false

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = try {
        Schnorr.sign(msg, seckey, aux)
    } catch (_: Exception) {
        null
    }

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
    ) = try {
        Schnorr.verify(msg, xOnly, sig)
    } catch (_: Exception) {
        false
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

    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ): ByteArray? = null

    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ): ByteArray? = null

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray? = null
}
