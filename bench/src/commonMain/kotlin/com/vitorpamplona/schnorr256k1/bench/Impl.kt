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

/**
 * One library under test, the Kotlin twin of c/backend.h. Every call is the one an app would
 * make through that library's Kotlin API (results/SCHEMA.md, "Operations"). Returns null or
 * false on failure. Messages are 32 bytes.
 */
interface Impl {
    /** results/SCHEMA.md "library". */
    val library: String

    /** results/SCHEMA.md "binding": jni, cinterop or kotlin. */
    val binding: String

    val version: String

    fun pubkeyCreate(seckey: ByteArray): ByteArray?

    fun pubkeyCompress(pub65: ByteArray): ByteArray?

    fun seckeyVerify(seckey: ByteArray): Boolean

    fun sign(
        msg: ByteArray,
        seckey: ByteArray,
        aux: ByteArray,
    ): ByteArray?

    /** The cheapest signing call when the caller already holds its public key. */
    fun signKnownPub(
        msg: ByteArray,
        seckey: ByteArray,
        pub33: ByteArray,
        xOnly: ByteArray,
        aux: ByteArray,
    ): ByteArray?

    fun verify(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ): Boolean

    /** False when the library has no fast verify: verify is used and timed instead. */
    val hasVerifyFast: Boolean

    fun verifyFast(
        sig: ByteArray,
        msg: ByteArray,
        xOnly: ByteArray,
    ): Boolean

    /** False when the library has no batch verify. */
    val hasVerifyBatch: Boolean

    fun verifyBatch(
        xOnly: ByteArray,
        sigs: List<ByteArray>,
        msgs: List<ByteArray>,
    ): Boolean

    /** x of seckey·lift_x(xOnly). */
    fun ecdhXOnly(
        xOnly: ByteArray,
        seckey: ByteArray,
    ): ByteArray?

    /** tweak·P for a 33-byte compressed P, compressed. */
    fun tweakMul(
        pub33: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    fun tweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    /**
     * The operations this library has no call for (results/SCHEMA.md: no row). The runner
     * neither times nor checks them, and a known-issue probe that needs one reports null.
     * Batch rows follow [hasVerifyBatch] instead.
     */
    val lacks: Set<Runner.Op> get() = emptySet()
}

/**
 * The libraries this platform has: ACINQ and Kotlin everywhere, libschnorr256k1-kmp where it is
 * published, and the JVM-only libraries on the JVM.
 */
expect val platformImpls: List<Impl>
