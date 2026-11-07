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

import kotlin.test.Test

/**
 * Reports whether each known bug still reproduces, without failing the build. A line that turns
 * from REPRODUCES to fixed means an implementation changed; update CrossCheckTest and README.md.
 */
class KnownIssuesTest {
    private fun report(
        what: String,
        failures: Int,
        of: Int,
    ) = println("KNOWN-ISSUE ${if (failures > 0) "REPRODUCES" else "fixed     "} $failures/$of  $what")

    @Test
    fun signXOnlyWithOddYKeys() {
        val odd = Fixture(400, seed = 10).keys.filter { it.compressed[0] == 0x03.toByte() }
        for (i in listOf(CImpl, KotlinImpl)) {
            val bad = odd.count { !AcinqImpl.verify(i.signKnownPub(it.msg, it.seckey, it.compressed, it.xOnly, it.aux), it.msg, it.xOnly) }
            report("${i.name}: signature from the known-public-key sign call is invalid for an odd-y key", bad, odd.size)
        }
    }

    @Test
    fun nullAuxDiffersFromLibsecp256k1() {
        // Not a security issue: any fresh nonce is valid. libsecp256k1 treats a null aux as 32
        // zero bytes (masks the key with tagged_hash("BIP0340/aux", 0^32)); the others do not mask.
        val keys = Fixture(100, seed = 14).keys
        for (i in listOf(CImpl, KotlinImpl)) {
            val differ = keys.count { !i.sign(it.msg, it.seckey, null).contentEquals(AcinqImpl.sign(it.msg, it.seckey, null)) }
            println(
                "KNOWN-ISSUE ${if (differ > 0) "DIFFERS   " else "same      "} $differ/${keys.size}  ${i.name}: signature with a null aux differs from libsecp256k1's",
            )
        }
    }

    @Test
    fun verifyAfterTweakMulOfTheOddYPoint() {
        val keys = Fixture(400, seed = 11).keys.filter { it.compressed[0] == 0x03.toByte() }.take(100)
        val tweak = Fixture(1, seed = 12).keys[0].seckey
        for (i in listOf(CImpl, KotlinImpl)) {
            val bad =
                keys.count {
                    i.tweakMul(it.pubkey, tweak)
                    !i.verify(it.sig, it.msg, it.xOnly)
                }
            report("${i.name}: valid signature rejected after a tweak-mul of the odd-y form of its key", bad, keys.size)
        }
    }

    @Test
    fun batchAcceptsCancellingInvalidSignatures() {
        val (key, sigs, msgs) = Fixture(1, seed = 13).batch(2)
        // s1 + d and s2 - d (mod n), d = 1: each signature is invalid, their sum is unchanged.
        val n = Fixture.N_HEX
        val s1 = sigs[0].copyOf().also { addSmall(it, 1) }
        val s2 = sigs[1].copyOf().also { addSmall(it, -1) }
        check(!AcinqImpl.verify(s1, msgs[0], key) && !AcinqImpl.verify(s2, msgs[1], key)) { "both invalid on their own ($n)" }
        for (i in listOf(CImpl, KotlinImpl)) {
            val accepted = i.verifyBatch(key, listOf(s1, s2), msgs) == true
            report("${i.name}: batch verify accepts two invalid signatures that cancel", if (accepted) 1 else 0, 1)
        }
    }

    /** Adds [d] (±1) to the s half (bytes 32..63) of a signature; no wrap past n in practice. */
    private fun addSmall(
        sig: ByteArray,
        d: Int,
    ) {
        var carry = d
        var i = 63
        while (carry != 0 && i >= 32) {
            val v = (sig[i].toInt() and 0xFF) + carry
            sig[i] = v.toByte()
            carry =
                if (v > 255) {
                    1
                } else if (v < 0) {
                    -1
                } else {
                    0
                }
            i--
        }
    }
}
