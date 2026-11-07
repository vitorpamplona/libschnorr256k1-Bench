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

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/**
 * Every operation, for each implementation ([impl]), on the same inputs. Results are per call;
 * `verifyBatch200` verifies 200 signatures per call.
 *
 * `verifySameKey` repeats one public key (libschnorr256k1 and the Kotlin library cache the
 * decompressed key and its tables, as a Nostr feed of one author would hit); `verifyManyKeys`
 * cycles through 4096 keys, more than either cache holds.
 */
@State(Scope.Benchmark)
class SchnorrBenchmark {
    @Param("acinq", "libschnorr256k1", "kotlin")
    var impl: String = "kotlin"

    private lateinit var i: Impl
    private lateinit var f: Fixture
    private lateinit var batchKey: ByteArray
    private lateinit var batchSigs: List<ByteArray>
    private lateinit var batchMsgs: List<ByteArray>
    private var next = 0

    @Setup
    fun setup() {
        i = Impl.named(impl)
        f = Fixture(4096)
        val (k, s, m) = f.batch(200)
        batchKey = k
        batchSigs = s
        batchMsgs = m
    }

    private fun key(): Fixture.Key = f.keys[next++ and 4095]

    private val k0 get() = f.keys[0]

    @Benchmark
    fun verifySameKey(): Boolean = i.verify(k0.sig, k0.msg, k0.xOnly)

    @Benchmark
    fun verifyManyKeys(): Boolean {
        val k = key()
        return i.verify(k.sig, k.msg, k.xOnly)
    }

    @Benchmark
    fun verifyFastSameKey(): Boolean = i.verifyFast(k0.sig, k0.msg, k0.xOnly)

    @Benchmark
    fun verifyBatch200(): Boolean? = i.verifyBatch(batchKey, batchSigs, batchMsgs)

    @Benchmark
    fun sign(): ByteArray {
        val k = key()
        return i.sign(k.msg, k.seckey, k.aux)
    }

    @Benchmark
    fun signKnownPub(): ByteArray {
        val k = key()
        return i.signKnownPub(k.msg, k.seckey, k.compressed, k.xOnly, k.aux)
    }

    @Benchmark
    fun pubkeyCreate(): ByteArray = i.pubkeyCreate(key().seckey)

    @Benchmark
    fun ecdhXOnly(): ByteArray {
        val k = key()
        return i.ecdhXOnly(f.keys[(next + 7) and 4095].xOnly, k.seckey)
    }

    @Benchmark
    fun tweakMul(): ByteArray {
        val k = key()
        return i.tweakMul(k.compressed, f.keys[(next + 7) and 4095].seckey)
    }

    @Benchmark
    fun tweakAdd(): ByteArray {
        val k = key()
        return i.tweakAdd(k.seckey, f.keys[(next + 7) and 4095].seckey)
    }
}
