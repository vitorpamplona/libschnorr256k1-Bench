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
import kotlin.math.round
import kotlin.time.TimeSource

/** Keeps the benchmark loops' results alive. */
var sink: Long = 0

/**
 * The Kotlin layers' benchmark, the twin of c/main.c: check every library against
 * vectors/v1.txt, BIP-340's own vectors and the nostr-v1 dataset, time every frozen operation with the same method
 * (warm-up, samples of at least [sampleNs], nearest-rank percentiles), probe the known issues on separate keys, and
 * render a schema-v4 result (results/SCHEMA.md).
 */
class Runner(
    private val layer: String,
    private val impls: List<Impl>,
    private val ds: Dataset,
    private val quick: Boolean = false,
    private val log: (String) -> Unit = {},
) {
    private val reference = impls.first { it.library == "libsecp256k1" }
    private val v = Vectors(reference)
    private val warmNs = if (quick) 200_000_000L else 1_000_000_000L
    private val sampleNs = if (quick) 20_000_000L else 100_000_000L
    private val nSamples = if (quick) 5 else 20
    private var next = 0

    enum class Op(
        val id: String,
    ) {
        VERIFY_SAME("verify.sameKey"),
        VERIFY_MANY("verify.manyKeys"),
        VERIFY_FAST_SAME("verifyFast.sameKey"),
        VERIFY_BATCH("verifyBatch200.perSig"),
        SIGN("sign"),
        SIGN_KNOWN_PUB("sign.knownPub"),
        PUB_CREATE("pubkey.create"),
        PUB_COMPRESS("pubkey.compress"),
        SECKEY_VERIFY("seckey.verify"),
        ECDH("ecdh.xOnly"),
        TWEAK_MUL("tweak.mul"),
        TWEAK_ADD("tweak.add"),
        NOSTR_VERIFY("nostr.verify"),
        NOSTR_VERIFY_FAST("nostr.verifyFast"),
        NOSTR_BATCH("nostr.verifyBatchByAuthor"),
        NOSTR_ECDH("nostr.ecdh"),
    }

    // ------------------------------------------------------------ correctness

    /** The v1 digests for [i]'s outputs, in vectors/v1.txt's order and names. */
    fun digests(i: Impl): Map<String, String> {
        val out = linkedMapOf<String, ByteArray>()

        fun collect(
            name: String,
            f: (Int) -> ByteArray,
        ) {
            val parts = Array(Vectors.N) { f(it) }
            val all = ByteArray(parts.sumOf { it.size })
            var o = 0
            for (p in parts) {
                p.copyInto(all, o)
                o += p.size
            }
            out[name] = sha256(all)
        }
        val fail65 = ByteArray(65)
        collect("pubkey.create") { i.pubkeyCreate(v.seckey[it]) ?: fail65 }
        collect("pubkey.compress") { i.pubkeyCompress(v.pub65[it]) ?: ByteArray(33) }
        collect("sign") { i.sign(v.msg[it], v.seckey[it], v.aux[it]) ?: ByteArray(64) }
        collect("ecdh.xOnly") { i.ecdhXOnly(v.xOnly[(it + 1) % Vectors.N], v.seckey[it]) ?: ByteArray(32) }
        collect("tweak.mul") { i.tweakMul(v.tpub33[it], v.seckey[it]) ?: ByteArray(33) }
        collect("tweak.add") { i.tweakAdd(v.seckey[it], v.tweak[it]) ?: ByteArray(32) }
        collect("verify") { byteArrayOf(if (i.verify(v.sig[it], v.msg[it], v.xOnly[it])) 1 else 0) }
        collect("verify.tampered") {
            val bad = v.sig[it].copyOf().also { b -> b[63] = (b[63] + 1).toByte() }
            byteArrayOf(if (i.verify(bad, v.msg[it], v.xOnly[it])) 1 else 0)
        }
        return out.mapValues { Vectors.toHex(it.value) }
    }

    /** The dataset digests for [i]'s outputs, in datasets/nostr-v1.txt's order and names. */
    fun datasetDigests(i: Impl): Map<String, String> {
        val ok = ByteArray(ds.n)
        val bad = ByteArray(ds.n)
        for (e in 0 until ds.n) {
            ok[e] = if (i.verify(ds.sig[e], ds.id[e], ds.pub[e])) 1 else 0
            val t = ds.sig[e].copyOf().also { b -> b[63] = (b[63] + 1).toByte() }
            bad[e] = if (i.verify(t, ds.id[e], ds.pub[e])) 1 else 0
        }
        val ecdh = ByteArray(32 * ds.keys.size)
        ds.keys.forEachIndexed { d, k -> (i.ecdhXOnly(k, v.seckey[d % Vectors.N]) ?: ByteArray(32)).copyInto(ecdh, 32 * d) }
        return linkedMapOf(
            "nostr.verify" to Vectors.toHex(sha256(ok)),
            "nostr.verify.tampered" to Vectors.toHex(sha256(bad)),
            "nostr.ecdh" to Vectors.toHex(sha256(ecdh)),
        )
    }

    // ------------------------------------------------------------ BIP-340 vectors

    private class Bip340Result(
        val signOk: Boolean,
        val knownPubOk: Boolean,
        val knownPubWrongOnlyOddY: Boolean,
        val verifyOk: Boolean,
        val fastOk: Boolean,
        /** KI-4: verifyFast accepts [BIP340_ODD_R]... */
        val fastAcceptsOddR: Boolean,
        /** ...and gets every other vector right. */
        val fastOkButOddR: Boolean,
        val batchOk: Boolean,
    )

    /**
     * BIP-340's vectors through [i] (vectors/README.md, "BIP-340 vectors"), and a batch with one bad
     * signature, first and then last. A call that throws on an invalid input counts as a rejection;
     * on a valid one, as a wrong result.
     */
    private fun bip340(i: Impl): Bip340Result {
        var signOk = true
        var knownPubOk = true
        var knownPubOddOnly = true
        var verifyOk = true
        var fastOk = true
        var fastAcceptsOddR = false
        var fastOkButOddR = true
        for (b in BIP340) {
            if (b.seckey != null && b.aux != null) {
                if (Op.SIGN !in i.lacks) {
                    val s = runCatching { i.sign(b.msg, b.seckey, b.aux) }.getOrNull()
                    if (s == null || !s.contentEquals(b.sig)) {
                        log("${i.library}: BIP-340 vector ${b.index}: sign differs")
                        signOk = false
                    }
                }
                if (Op.SIGN_KNOWN_PUB !in i.lacks) {
                    val pub33 = reference.pubkeyCreate(b.seckey)?.let { reference.pubkeyCompress(it) } ?: error("reference BIP-340 pubkey")
                    val s = runCatching { i.signKnownPub(b.msg, b.seckey, pub33, pub33.copyOfRange(1, 33), b.aux) }.getOrNull()
                    if (s == null || !s.contentEquals(b.sig)) {
                        log("${i.library}: BIP-340 vector ${b.index}: sign.knownPub differs")
                        knownPubOk = false
                        if (pub33[0] != 0x03.toByte()) knownPubOddOnly = false
                    }
                }
            }
            if (runCatching { i.verify(b.sig, b.msg, b.pub) }.getOrDefault(false) != b.expected) {
                log("${i.library}: BIP-340 vector ${b.index}: verify ${if (b.expected) "rejects" else "accepts"} it")
                verifyOk = false
            }
            if (i.hasVerifyFast && runCatching { i.verifyFast(b.sig, b.msg, b.pub) }.getOrDefault(false) != b.expected) {
                log("${i.library}: BIP-340 vector ${b.index}: verifyFast ${if (b.expected) "rejects" else "accepts"} it")
                fastOk = false
                if (b.index == BIP340_ODD_R) fastAcceptsOddR = true else fastOkButOddR = false
            }
        }
        var batchOk = true
        if (i.hasVerifyBatch) {
            for (at in listOf(0, v.batchSig.size - 1)) {
                val sigs = v.batchSig.toMutableList()
                sigs[at] = sigs[at].copyOf().also { it[63] = (it[63] + 1).toByte() }
                if (runCatching { i.verifyBatch(v.xOnly[0], sigs, v.batchMsg) }.getOrDefault(false)) {
                    log("${i.library}: verifyBatch accepts a batch whose signature $at is bad")
                    batchOk = false
                }
            }
        }
        return Bip340Result(signOk, knownPubOk, !knownPubOk && knownPubOddOnly, verifyOk, fastOk, fastAcceptsOddR, fastOkButOddR, batchOk)
    }

    /** One page's author groups: batch verify for groups of two or more. */
    private fun verifyPage(
        i: Impl,
        p: Int,
    ): Boolean {
        var all = true
        for (g in ds.pageGroups[p]) {
            all = all and if (g.sigs.size == 1) i.verify(g.sigs[0], g.msgs[0], g.pub) else i.verifyBatch(g.pub, g.sigs, g.msgs)
        }
        return all
    }

    private class Correctness(
        val ok: Map<Op, Boolean>,
        val knownPubWrongOnlyOddY: Boolean,
        /** KI-4 as observed, and the fast ops it explains: those whose only fault is the odd-y R. */
        val fastAcceptsOddR: Boolean?,
        val explainedByOddR: Set<Op>,
    )

    /** Whether [digest] is the output of an op [i] lacks (its digest is not checked). */
    private fun lacked(
        i: Impl,
        digest: String,
    ) = i.lacks.any { it.id == digest }

    private fun correctness(i: Impl): Correctness {
        val d = digests(i)
        val good = d.mapValues { (k, h) -> VectorsV1.digests[k] == h }
        good
            .filterValues { !it }
            .keys
            .filter { !lacked(i, it) }
            .forEach { log("${i.library}: $it digest differs from vectors") }
        var fastOk = true
        var knownPubOk = true
        var knownPubOddOnly = true
        var seckeyOk = true
        for (k in 0 until Vectors.N) {
            if (i.hasVerifyFast && !i.verifyFast(v.sig[k], v.msg[k], v.xOnly[k])) fastOk = false
            val s = i.signKnownPub(v.msg[k], v.seckey[k], v.pub33[k], v.xOnly[k], v.aux[k])
            if (s == null || !s.contentEquals(v.sig[k])) {
                knownPubOk = false
                if (v.pub33[k][0] != 0x03.toByte()) knownPubOddOnly = false
            }
            if (!i.seckeyVerify(v.seckey[k])) seckeyOk = false
        }
        if (i.seckeyVerify(ByteArray(32)) ||
            i.seckeyVerify(Vectors.hex("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141"))
        ) {
            seckeyOk = false
        }
        val bip = bip340(i)
        val batchOk = (!i.hasVerifyBatch || i.verifyBatch(v.xOnly[0], v.batchSig, v.batchMsg)) && bip.batchOk
        val verifyOk = good["verify"] == true && good["verify.tampered"] == true && bip.verifyOk
        val fastOkButOddR = fastOk && bip.fastOkButOddR
        fastOk = fastOk && bip.fastOk
        if (!bip.knownPubOk) {
            knownPubOk = false
            if (!bip.knownPubWrongOnlyOddY) knownPubOddOnly = false
        }

        val dd = datasetDigests(i).mapValues { (k, h) -> DatasetV1.digests[k] == h }
        dd
            .filterValues { !it }
            .keys
            .filter { !lacked(i, it) }
            .forEach { log("${i.library}: $it digest differs from the dataset's") }
        val dsVerifyOk = dd["nostr.verify"] == true && dd["nostr.verify.tampered"] == true && bip.verifyOk
        val dsFastOk = !i.hasVerifyFast || (0 until ds.n).all { i.verifyFast(ds.sig[it], ds.id[it], ds.pub[it]) }
        val dsBatchOk = (!i.hasVerifyBatch || (0 until ds.pages).all { verifyPage(i, it) }) && bip.batchOk
        return Correctness(
            mapOf(
                Op.VERIFY_SAME to verifyOk,
                Op.VERIFY_MANY to verifyOk,
                Op.VERIFY_FAST_SAME to (verifyOk && fastOk),
                Op.VERIFY_BATCH to batchOk,
                Op.SIGN to (good["sign"] == true && bip.signOk),
                Op.SIGN_KNOWN_PUB to knownPubOk,
                Op.PUB_CREATE to (good["pubkey.create"] == true),
                Op.PUB_COMPRESS to (good["pubkey.compress"] == true),
                Op.SECKEY_VERIFY to seckeyOk,
                Op.ECDH to (good["ecdh.xOnly"] == true),
                Op.TWEAK_MUL to (good["tweak.mul"] == true),
                Op.TWEAK_ADD to (good["tweak.add"] == true),
                Op.NOSTR_VERIFY to dsVerifyOk,
                Op.NOSTR_VERIFY_FAST to (dsVerifyOk && dsFastOk && bip.fastOk),
                Op.NOSTR_BATCH to dsBatchOk,
                Op.NOSTR_ECDH to (dd["nostr.ecdh"] == true),
            ),
            !knownPubOk && knownPubOddOnly,
            if (i.hasVerifyFast) bip.fastAcceptsOddR else null,
            buildSet {
                if (bip.fastAcceptsOddR && fastOkButOddR) {
                    if (verifyOk) add(Op.VERIFY_FAST_SAME)
                    if (dsVerifyOk && dsFastOk) add(Op.NOSTR_VERIFY_FAST)
                }
            },
        )
    }

    // ------------------------------------------------------------ timing

    /** Runs [n] calls of [op]. False if the library lacks it. */
    private fun run(
        i: Impl,
        op: Op,
        n: Long,
    ): Boolean {
        if (op in i.lacks) return false
        var acc = 0L
        var k = next
        val m = Vectors.N
        when (op) {
            Op.VERIFY_SAME -> for (r in 0 until n) if (i.verify(v.sig[0], v.msg[0], v.xOnly[0])) acc++
            Op.VERIFY_MANY ->
                for (r in 0 until n) {
                    val x = k++ % m
                    if (i.verify(v.sig[x], v.msg[x], v.xOnly[x])) acc++
                }
            Op.VERIFY_FAST_SAME -> for (r in 0 until n) if (i.verifyFast(v.sig[0], v.msg[0], v.xOnly[0])) acc++
            Op.VERIFY_BATCH -> {
                if (!i.hasVerifyBatch) return false
                for (r in 0 until n) if (i.verifyBatch(v.xOnly[0], v.batchSig, v.batchMsg)) acc++
            }
            Op.SIGN ->
                for (r in 0 until n) {
                    val x = k++ % m
                    acc += i.sign(v.msg[x], v.seckey[x], v.aux[x])?.get(7) ?: 0
                }
            Op.SIGN_KNOWN_PUB ->
                for (r in 0 until n) {
                    val x = k++ % m
                    acc += i.signKnownPub(v.msg[x], v.seckey[x], v.pub33[x], v.xOnly[x], v.aux[x])?.get(7) ?: 0
                }
            Op.PUB_CREATE -> for (r in 0 until n) acc += i.pubkeyCreate(v.seckey[k++ % m])?.get(7) ?: 0
            Op.PUB_COMPRESS -> for (r in 0 until n) acc += i.pubkeyCompress(v.pub65[k++ % m])?.get(7) ?: 0
            Op.SECKEY_VERIFY -> for (r in 0 until n) if (i.seckeyVerify(v.seckey[k++ % m])) acc++
            Op.ECDH ->
                for (r in 0 until n) {
                    val x = k++ % m
                    acc += i.ecdhXOnly(v.xOnly[(x + 1) % m], v.seckey[x])?.get(7) ?: 0
                }
            Op.TWEAK_MUL ->
                for (r in 0 until n) {
                    val x = k++ % m
                    acc += i.tweakMul(v.tpub33[x], v.seckey[x])?.get(7) ?: 0
                }
            Op.TWEAK_ADD ->
                for (r in 0 until n) {
                    val x = k++ % m
                    acc += i.tweakAdd(v.seckey[x], v.tweak[x])?.get(7) ?: 0
                }
            Op.NOSTR_VERIFY ->
                for (r in 0 until n) {
                    val e = k++ % ds.n
                    if (i.verify(ds.sig[e], ds.id[e], ds.pub[e])) acc++
                }
            Op.NOSTR_VERIFY_FAST ->
                for (r in 0 until n) {
                    val e = k++ % ds.n
                    if (i.verifyFast(ds.sig[e], ds.id[e], ds.pub[e])) acc++
                }
            Op.NOSTR_BATCH -> {
                // One call is one page.
                if (!i.hasVerifyBatch) return false
                for (r in 0 until n) if (verifyPage(i, k++ % ds.pages)) acc++
            }
            Op.NOSTR_ECDH ->
                for (r in 0 until n) {
                    val d = k++ % ds.keys.size
                    acc += i.ecdhXOnly(ds.keys[d], v.seckey[d % m])?.get(7) ?: 0
                }
        }
        next = k
        sink += acc
        return true
    }

    class Timing(
        val median: Double,
        val p5: Double,
        val p95: Double,
        val samples: Int,
    )

    private fun timeOp(
        i: Impl,
        op: Op,
    ): Timing? {
        if (!run(i, op, 1)) return null
        val clock = TimeSource.Monotonic
        val warm = clock.markNow()
        while (warm.elapsedNow().inWholeNanoseconds < warmNs) run(i, op, 16)
        var n = 1L
        while (true) {
            val s = clock.markNow()
            run(i, op, n)
            if (s.elapsedNow().inWholeNanoseconds >= sampleNs) break
            n *= 2
        }
        val perCall =
            when (op) {
                Op.VERIFY_BATCH -> Vectors.BATCH.toDouble()
                Op.NOSTR_BATCH -> Dataset.PAGE.toDouble()
                else -> 1.0
            }
        val values =
            DoubleArray(nSamples) {
                val s = clock.markNow()
                run(i, op, n)
                s.elapsedNow().inWholeNanoseconds.toDouble() / (n * perCall)
            }
        values.sort()
        return Timing(pct(values, 50.0), pct(values, 5.0), pct(values, 95.0), nSamples)
    }

    /** Nearest-rank percentile of sorted [v], as c/main.c computes it. */
    private fun pct(
        v: DoubleArray,
        p: Double,
    ): Double {
        var r = (p / 100.0 * v.size + 0.999999).toInt()
        if (r < 1) r = 1
        if (r > v.size) r = v.size
        return v[r - 1]
    }

    // ------------------------------------------------------------ known issues

    /** Adds [d] (±1) to the s half of a signature. */
    private fun addSmallToS(
        sig: ByteArray,
        d: Int,
    ): ByteArray {
        val s = sig.copyOf()
        var carry = d
        var i = 63
        while (carry != 0 && i >= 32) {
            val x = (s[i].toInt() and 0xFF) + carry
            s[i] = x.toByte()
            carry =
                if (x > 255) {
                    1
                } else if (x < 0) {
                    -1
                } else {
                    0
                }
            i--
        }
        return s
    }

    private fun probeIssues(
        i: Impl,
        knownPubOddOnly: Boolean,
        fastAcceptsOddR: Boolean?,
    ): Map<String, Boolean?> {
        val ki1: Boolean? =
            if (!i.hasVerifyBatch) {
                null
            } else {
                val s1 = addSmallToS(v.batchSig[0], 1)
                val s2 = addSmallToS(v.batchSig[1], -1)
                val eachInvalid = !reference.verify(s1, v.msg[0], v.xOnly[0]) && !reference.verify(s2, v.msg[1], v.xOnly[0])
                eachInvalid && i.verifyBatch(v.xOnly[0], listOf(s1, s2), listOf(v.msg[0], v.msg[1]))
            }
        // KI-3, on odd-y keys of a separate set: tweak-mul the odd-y point, then verify a valid
        // signature for the x-only form.
        if (Op.TWEAK_MUL in i.lacks) return linkedMapOf("KI-1" to ki1, "KI-2" to knownPubOddOnly, "KI-3" to null, "KI-4" to fastAcceptsOddR)
        var rejected = 0
        var found = 0
        var k = 0
        while (found < 16 && k < 4096) {
            val sk = sha256("schnorr256k1-bench/v1/probe/$k".encodeToByteArray())
            k++
            val pub33 = reference.pubkeyCreate(sk)?.let { reference.pubkeyCompress(it) } ?: continue
            if (pub33[0] != 0x03.toByte()) continue
            found++
            val msg = sha256(sk)
            val sig = reference.sign(msg, sk, msg) ?: continue
            i.tweakMul(pub33, v.tweak[0])
            if (!i.verify(sig, msg, pub33.copyOfRange(1, 33))) rejected++
        }
        return linkedMapOf("KI-1" to ki1, "KI-2" to knownPubOddOnly, "KI-3" to (rejected > 0), "KI-4" to fastAcceptsOddR)
    }

    /** What [runAll] checks, without the timing: each op's correctness, and the known issues seen. */
    class Checks(
        val correct: Map<Op, Boolean>,
        val knownIssues: Map<String, Boolean?>,
        /** The wrong ops KI-4 explains: a fast verify whose only fault is BIP-340's odd-y R. */
        val explainedByOddR: Set<Op>,
    )

    fun check(i: Impl): Checks {
        val c = correctness(i)
        return Checks(c.ok, probeIssues(i, c.knownPubWrongOnlyOddY, c.fastAcceptsOddR), c.explainedByOddR)
    }

    /**
     * "library op" for every wrong result that no observed known issue explains: what a test
     * fails on. A known issue only explains the ops it makes wrong on the vectors ([COVERS]);
     * the issues themselves are logged, not asserted, so fixing one never fails a test.
     */
    fun unexplainedFailures(): List<String> =
        impls.flatMap { i ->
            val c = check(i)
            log("${i.library} (${i.binding}): known issues ${c.knownIssues}")
            c.correct.filterValues { !it }.keys.filter { it !in i.lacks }.mapNotNull { op ->
                val ki = COVERS[op]
                if (ki != null && c.knownIssues[ki] == true && (ki != "KI-4" || op in c.explainedByOddR)) {
                    log("${i.library} ${op.id}: wrong, as $ki says")
                    null
                } else {
                    "${i.library} ${op.id}"
                }
            }
        }

    companion object {
        /**
         * The ops each known issue makes wrong on the vectors. KI-1 and KI-3 leave every row
         * correct: a valid batch is accepted, and the vectors' tweak-mul keys share no x with the
         * verify keys. KI-4 explains a fast-verify row only when the odd-y R is all that is wrong
         * with it ([Checks.explainedByOddR]).
         */
        val COVERS =
            mapOf(
                Op.SIGN_KNOWN_PUB to "KI-2",
                Op.VERIFY_FAST_SAME to "KI-4",
                Op.NOSTR_VERIFY_FAST to "KI-4",
            )

        /** The BIP-340 vector whose only fault is R's odd y (KI-4). */
        const val BIP340_ODD_R = 6

        /** BIP-340's test vectors with a 32-byte message, the size every operation here takes. */
        val BIP340: List<Bip340Vector> =
            Bip340Csv.rows.filter { it[4].length == 64 }.map { f ->
                Bip340Vector(
                    index = f[0].toInt(),
                    seckey = f[1].takeIf { it.isNotEmpty() }?.let { Vectors.hex(it) },
                    pub = Vectors.hex(f[2]),
                    aux = f[3].takeIf { it.isNotEmpty() }?.let { Vectors.hex(it) },
                    msg = Vectors.hex(f[4]),
                    sig = Vectors.hex(f[5]),
                    expected =
                        when (f[6]) {
                            "TRUE" -> true
                            "FALSE" -> false
                            else -> error("BIP-340 vector ${f[0]}: result ${f[6]}")
                        },
                )
            }
    }

    /** A row of vectors/bip340/test-vectors.csv. */
    class Bip340Vector(
        val index: Int,
        val seckey: ByteArray?,
        val pub: ByteArray,
        val aux: ByteArray?,
        val msg: ByteArray,
        val sig: ByteArray,
        val expected: Boolean,
    )

    // ------------------------------------------------------------ result

    /** Checks, times and probes every library; returns the schema-v4 JSON. */
    fun runAll(
        context: Map<String, String>,
        only: String? = null,
    ): String {
        val rows = StringBuilder()
        val issues = StringBuilder()
        for (i in impls) {
            if (only != null && i.library != only) continue
            log("${i.library}: checking")
            val c = correctness(i)
            val t =
                Op.entries.associateWith { op ->
                    log("${i.library} ${op.id}")
                    timeOp(i, op)
                }
            // After the timing too: KI-3 is about state the calls leave behind.
            val ki = probeIssues(i, c.knownPubWrongOnlyOddY, c.fastAcceptsOddR)
            for (op in Op.entries) {
                val tm = t[op] ?: continue
                val kis =
                    buildList {
                        if ((op == Op.VERIFY_BATCH || op == Op.NOSTR_BATCH) && ki["KI-1"] == true) add("\"KI-1\"")
                        if (op == Op.SIGN_KNOWN_PUB && ki["KI-2"] == true) add("\"KI-2\"")
                        if ((op == Op.VERIFY_FAST_SAME || op == Op.NOSTR_VERIFY_FAST) && ki["KI-4"] == true) add("\"KI-4\"")
                    }.joinToString(", ")
                if (rows.isNotEmpty()) rows.append(",")
                rows.append(
                    "\n    { \"op\": \"${op.id}\", \"library\": \"${i.library}\", \"binding\": \"${i.binding}\", " +
                        "\"median_ns\": ${fmt(tm.median)}, \"p5_ns\": ${fmt(tm.p5)}, \"p95_ns\": ${fmt(tm.p95)}, " +
                        "\"samples\": ${tm.samples}, \"correct\": ${c.ok[op] == true}, \"known_issues\": [$kis] }",
                )
            }
            if (issues.isNotEmpty()) issues.append(",")
            issues.append("\n    \"${i.library}\": { " + ki.entries.joinToString(", ") { "\"${it.key}\": ${it.value}" } + " }")
        }
        val ctx = context.entries.joinToString(",\n") { "    \"${it.key}\": ${jsonString(it.value)}" }
        val libs = impls.filter { only == null || it.library == only }.joinToString(", ") { "\"${it.library}\": ${jsonString(it.version)}" }
        return "{\n  \"schema\": 4,\n  \"vectors\": \"v1\",\n  \"vectorsSha256\": \"${VectorsV1.FILE_SHA256}\",\n" +
            "  \"bip340Sha256\": \"${Bip340Csv.FILE_SHA256}\",\n" +
            "  \"dataset\": \"nostr-v1\",\n  \"datasetSha256\": \"${ds.sha256Hex}\",\n" +
            "  \"layer\": \"$layer\",\n  \"quick\": $quick,\n  \"context\": {\n$ctx,\n    \"libraries\": { $libs }\n  },\n" +
            "  \"rows\": [$rows\n  ],\n  \"known_issues_observed\": {$issues\n  }\n}\n"
    }

    private fun fmt(d: Double): String {
        val tenths = round(d * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }

    private fun jsonString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
