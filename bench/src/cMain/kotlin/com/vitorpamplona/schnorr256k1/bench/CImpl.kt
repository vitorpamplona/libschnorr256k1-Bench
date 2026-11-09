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

/**
 * libschnorr256k1 (C) through libschnorr256k1-kmp: JNI on the JVM and Android, cinterop on
 * Apple. Not on Linux Native: libschnorr256k1-kmp 1.0.5 publishes no linuxX64 artifact.
 */
class CImpl(
    override val binding: String,
) : Impl {
    override val library = "libschnorr256k1"
    override val version = "schnorr256k1-kmp 1.1.0"

    init {
        CLib.ensureLoaded()
    }

    override fun pubkeyCreate(seckey: ByteArray) = CLib.pubkeyCreate(seckey)

    override fun pubkeyCompress(pub65: ByteArray) = CLib.pubkeyCompress(pub65)

    override fun seckeyVerify(seckey: ByteArray) = CLib.seckeyVerify(seckey)

    override fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ) = CLib.schnorrSign(msg, seckey, aux)

    override fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        pub33: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray,
    ) = CLib.schnorrSignWithPubKey(msg, seckey, pub33, aux)

    override fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = CLib.schnorrVerify(sig, msg, xOnly)

    override val hasVerifyFast = true

    override fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ) = CLib.schnorrVerifyFast(sig, msg, xOnly)

    override val hasVerifyBatch = true

    override fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ) = CLib.schnorrVerifyBatch(xOnly, sigs, msgs)

    override fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ) = CLib.ecdhXOnly(xOnly, seckey)

    override fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ) = CLib.pubkeyTweakMul(pub33, tweak)

    override fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ) = CLib.privkeyTweakAdd(seckey, tweak)
}
