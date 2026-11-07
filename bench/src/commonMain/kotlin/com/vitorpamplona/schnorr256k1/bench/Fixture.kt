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

import kotlin.random.Random

/** Deterministic test data, generated with libsecp256k1 (ACINQ) as the reference. */
class Fixture(
    keys: Int,
    seed: Int = 0x5EC9,
) {
    private val r = Random(seed)

    class Key(
        val seckey: ByteArray,
        val pubkey: ByteArray, // 65 bytes
        val compressed: ByteArray, // 33 bytes
        val xOnly: ByteArray, // 32 bytes
        val msg: ByteArray, // 32 bytes
        val aux: ByteArray,
        val sig: ByteArray, // valid BIP-340 signature of msg
    )

    fun seckey(): ByteArray {
        while (true) {
            val s = r.nextBytes(32)
            if (s.any { it != 0.toByte() } && s.toUnsignedHexCompare(N_HEX) < 0) return s
        }
    }

    val keys: List<Key> =
        List(keys) {
            val sk = seckey()
            val pub = AcinqImpl.pubkeyCreate(sk)
            val comp = AcinqImpl.pubkeyCompress(pub)
            val msg = r.nextBytes(32)
            val aux = r.nextBytes(32)
            Key(sk, pub, comp, comp.copyOfRange(1, 33), msg, aux, AcinqImpl.sign(msg, sk, aux))
        }

    /** [count] signatures by one key, for batch verification. */
    fun batch(count: Int): Triple<ByteArray, List<ByteArray>, List<ByteArray>> {
        val k = keys[0]
        val msgs = List(count) { r.nextBytes(32) }
        return Triple(k.xOnly, msgs.map { AcinqImpl.sign(it, k.seckey, r.nextBytes(32)) }, msgs)
    }

    companion object {
        const val N_HEX = "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"

        private fun ByteArray.toUnsignedHexCompare(hex: String): Int {
            for (i in indices) {
                val a = this[i].toInt() and 0xFF
                val b = hex.substring(2 * i, 2 * i + 2).toInt(16)
                if (a != b) return a.compareTo(b)
            }
            return 0
        }
    }
}
