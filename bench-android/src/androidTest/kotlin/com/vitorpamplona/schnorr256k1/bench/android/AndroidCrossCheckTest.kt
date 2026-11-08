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
package com.vitorpamplona.schnorr256k1.bench.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.schnorr256k1.bench.Runner
import com.vitorpamplona.schnorr256k1.bench.logErr
import com.vitorpamplona.schnorr256k1.bench.platformImpls
import com.vitorpamplona.schnorr256k1.bench.platformLayer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** bench's CrossCheckTest on the device: no wrong result that a known issue doesn't explain. */
@RunWith(AndroidJUnit4::class)
class AndroidCrossCheckTest {
    @Test
    fun everyLibraryIsCorrectOrExplainedByAKnownIssue() {
        val failures = Runner(platformLayer, platformImpls, datasetFromAssets(), log = ::logErr).unexplainedFailures()
        assertTrue("wrong results no known issue explains: $failures", failures.isEmpty())
    }
}
