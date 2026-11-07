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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * All implementations give identical results on the same inputs (libsecp256k1 via ACINQ is the
 * reference). Known bugs are excluded here and reported by KnownIssuesTest instead.
 */
class CrossCheckTest {
    private val ref = AcinqImpl
    private val others = listOf(CImpl, KotlinImpl)

    @Test
    fun keysSignaturesAndVerification() {
        val f = Fixture(300, seed = 1)
        for (k in f.keys) {
            for (i in others) {
                assertContentEquals(k.pubkey, i.pubkeyCreate(k.seckey), "${i.name} pubkeyCreate")
                assertContentEquals(k.compressed, i.pubkeyCompress(k.pubkey), "${i.name} pubkeyCompress")
                assertContentEquals(k.sig, i.sign(k.msg, k.seckey, k.aux), "${i.name} sign")
                // No aux: libsecp256k1 masks with 32 zero bytes, the others skip the mask
                // (KnownIssuesTest), so the signatures differ; both must be valid.
                assertTrue(ref.verify(i.sign(k.msg, k.seckey, null), k.msg, k.xOnly), "${i.name} sign, no aux")
                assertTrue(i.verify(k.sig, k.msg, k.xOnly), "${i.name} verify")
                assertTrue(i.verifyFast(k.sig, k.msg, k.xOnly), "${i.name} verifyFast")
                val bad = k.sig.copyOf().also { it[63] = (it[63] + 1).toByte() }
                assertEquals(false, i.verify(bad, k.msg, k.xOnly), "${i.name} verify tampered")
                val badR = k.sig.copyOf().also { it[5] = (it[5] + 1).toByte() }
                assertEquals(ref.verify(badR, k.msg, k.xOnly), i.verify(badR, k.msg, k.xOnly), "${i.name} verify tampered R")
            }
            assertContentEquals(k.sig, KotlinImpl.signKnownPub(k.msg, k.seckey, k.compressed, k.xOnly, k.aux), "kotlin signKnownPub")
            if (k.compressed[0] == 0x02.toByte()) {
                // C's sign_xonly is only right for even-y keys (KnownIssuesTest)
                assertContentEquals(k.sig, CImpl.signKnownPub(k.msg, k.seckey, k.compressed, k.xOnly, k.aux), "C signKnownPub")
            }
        }
    }

    @Test
    fun ecdhAndTweaks() {
        // Keys never verified in this process: C's tweak-mul is wrong for an odd-y key whose x it
        // has verified for (KnownIssuesTest).
        val f = Fixture(200, seed = 2)
        val g = Fixture(200, seed = 3)
        for ((k, t) in f.keys.zip(g.keys)) {
            for (i in others) {
                assertContentEquals(ref.ecdhXOnly(t.xOnly, k.seckey), i.ecdhXOnly(t.xOnly, k.seckey), "${i.name} ecdhXOnly")
                assertContentEquals(ref.tweakMul(k.compressed, t.seckey), i.tweakMul(k.compressed, t.seckey), "${i.name} tweakMul 33")
                assertContentEquals(ref.tweakMul(k.pubkey, t.seckey), i.tweakMul(k.pubkey, t.seckey), "${i.name} tweakMul 65")
                assertContentEquals(ref.tweakAdd(k.seckey, t.seckey), i.tweakAdd(k.seckey, t.seckey), "${i.name} tweakAdd")
            }
        }
    }

    @Test
    fun batchOfValidSignatures() {
        val (key, sigs, msgs) = Fixture(1, seed = 4).batch(64)
        for (i in others) assertEquals(true, i.verifyBatch(key, sigs, msgs), "${i.name} verifyBatch")
        val tampered = sigs.toMutableList().also { it[10] = it[10].copyOf().also { b -> b[40] = (b[40] + 1).toByte() } }
        for (i in others) assertEquals(false, i.verifyBatch(key, tampered, msgs), "${i.name} verifyBatch tampered")
    }
}
