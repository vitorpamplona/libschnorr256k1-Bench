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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The checks of every benchmark run, without the timing, on every library this platform has
 * (bench-android runs the same on a device): the reference re-derives vectors/v1.txt, and every
 * other library gives its results, except where an observed known issue explains it.
 */
class CrossCheckTest {
    private val dataset = loadDataset(null) // BENCH_DATASET, set by the Gradle test tasks
    private val runner = Runner(platformLayer, platformImpls, dataset, log = ::println)
    private val reference = platformImpls.first { it.library == "libsecp256k1" }

    @Test
    fun theReferenceReproducesVectorsV1() {
        val digests = runner.digests(reference)
        assertEquals(VectorsV1.digests.keys, digests.keys)
        val wrong = digests.filter { (k, h) -> VectorsV1.digests[k] != h }.keys
        assertTrue(wrong.isEmpty(), "libsecp256k1 does not reproduce vectors/v1.txt: $wrong")
    }

    @Test
    fun theReferenceAcceptsEveryDatasetSignature() {
        assertEquals(DatasetV1.EVENTS, dataset.n)
        val digests = runner.datasetDigests(reference)
        val wrong = digests.filter { (k, h) -> DatasetV1.digests[k] != h }.keys
        assertTrue(wrong.isEmpty(), "libsecp256k1 does not reproduce datasets/nostr-v1.txt: $wrong")
    }

    @Test
    fun everyLibraryIsCorrectOrExplainedByAKnownIssue() {
        val failures = runner.unexplainedFailures()
        assertTrue(failures.isEmpty(), "wrong results no known issue explains: $failures")
    }

    @Test
    fun theReferenceShowsNoKnownIssue() {
        val seen = runner.check(reference).knownIssues.filterValues { it == true }
        assertTrue(seen.isEmpty(), "the reference shows $seen: the probes are wrong")
    }
}
