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
 * The nostr-v1 dataset (datasets/README.md), the Kotlin twin of c/main.c's load_dataset: the
 * signed events of a 2024 Amethyst startup session as id (the signed message), x-only pubkey and
 * signature, in the order a relay sent them.
 */
class Dataset(
    bytes: ByteArray,
) {
    val sha256Hex: String = Vectors.toHex(sha256(bytes))
    val n: Int = bytes.size / RECORD

    init {
        require(bytes.isNotEmpty() && bytes.size % RECORD == 0) { "the dataset is not whole $RECORD-byte records" }
    }

    val id = Array(n) { bytes.copyOfRange(it * RECORD, it * RECORD + 32) }
    val pub = Array(n) { bytes.copyOfRange(it * RECORD + 32, it * RECORD + 64) }
    val sig = Array(n) { bytes.copyOfRange(it * RECORD + 64, it * RECORD + 128) }

    /** Distinct pubkeys, in order of first appearance. */
    val keys: List<ByteArray> =
        buildList {
            val seen = HashSet<String>()
            for (p in pub) if (seen.add(Vectors.toHex(p))) add(p)
        }

    class Group(
        val pub: ByteArray,
        val sigs: List<ByteArray>,
        val msgs: List<ByteArray>,
    )

    /** Whole pages of [PAGE] events; the remainder is left out of the batch operation. */
    val pages: Int = n / PAGE

    /** Each page's events grouped by author, authors in order of first appearance in the page. */
    val pageGroups: Array<List<Group>> =
        Array(pages) { p ->
            val base = p * PAGE
            val byAuthor = LinkedHashMap<String, MutableList<Int>>()
            for (e in base until base + PAGE) byAuthor.getOrPut(Vectors.toHex(pub[e])) { mutableListOf() }.add(e)
            byAuthor.values.map { es -> Group(pub[es[0]], es.map { sig[it] }, es.map { id[it] }) }
        }

    companion object {
        const val RECORD = 128
        const val PAGE = 200
    }
}
