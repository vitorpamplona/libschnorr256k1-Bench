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

import com.vitorpamplona.crypto.sha2.sha256

/**
 * Input vectors v1 (vectors/README.md), the Kotlin twin of c/vectors.c. Inputs come from
 * SHA-256 of fixed labels; the derived values are made with the reference library
 * ([reference], libsecp256k1 through ACINQ).
 */
class Vectors(
    reference: Impl,
) {
    val seckey = Array(N) { scalarFromLabel("seckey", it) }
    val tweak = Array(N) { scalarFromLabel("tweak", it) }
    val msg = Array(N) { labelHash("msg", it) }
    val aux = Array(N) { labelHash("aux", it) }

    val pub65 = Array(N) { reference.pubkeyCreate(seckey[it]) ?: error("reference pubkeyCreate") }
    val pub33 = Array(N) { reference.pubkeyCompress(pub65[it]) ?: error("reference pubkeyCompress") }
    val xOnly = Array(N) { pub33[it].copyOfRange(1, 33) }
    val tpub33 =
        Array(N) {
            reference.pubkeyCompress(reference.pubkeyCreate(tweak[it]) ?: error("reference tweak pubkey")) ?: error("reference compress")
        }
    val sig = Array(N) { reference.sign(msg[it], seckey[it], aux[it]) ?: error("reference sign") }
    val batchSig = List(BATCH) { reference.sign(msg[it], seckey[0], aux[it]) ?: error("reference batch sign") }
    val batchMsg = List(BATCH) { msg[it] }

    companion object {
        const val N = 4096
        const val BATCH = 200

        /** n, the group order, big-endian. */
        private val ORDER = hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141")

        fun labelHash(
            field: String,
            i: Int,
        ): ByteArray = sha256("schnorr256k1-bench/v1/$field/$i".encodeToByteArray())

        fun validScalar(s: ByteArray): Boolean {
            if (s.all { it == 0.toByte() }) return false
            for (k in 0 until 32) {
                val a = s[k].toInt() and 0xFF
                val b = ORDER[k].toInt() and 0xFF
                if (a != b) return a < b
            }
            return false
        }

        private fun scalarFromLabel(
            field: String,
            i: Int,
        ): ByteArray {
            var h = labelHash(field, i)
            while (!validScalar(h)) h = sha256(h)
            return h
        }

        fun hex(s: String): ByteArray =
            ByteArray(s.length / 2) { ((s[2 * it].digitToInt(16) shl 4) or s[2 * it + 1].digitToInt(16)).toByte() }

        fun toHex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
