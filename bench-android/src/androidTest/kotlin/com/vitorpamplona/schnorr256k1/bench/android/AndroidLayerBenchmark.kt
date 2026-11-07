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
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.schnorr256k1.bench.Runner
import com.vitorpamplona.schnorr256k1.bench.logErr
import com.vitorpamplona.schnorr256k1.bench.platformContext
import com.vitorpamplona.schnorr256k1.bench.platformImpls
import com.vitorpamplona.schnorr256k1.bench.platformLayer
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Runs the Android layer and writes its schema-v1 result to the test's additional output
 * directory, which AGP copies to build/outputs/connected_android_test_additional_output/.
 *
 * Instrumentation arguments (-Pandroid.testInstrumentationRunnerArguments.<name>=...):
 * `quick` (true for a smoke run), `machine` (dedicated | shared), `harness` (commit).
 */
@RunWith(AndroidJUnit4::class)
class AndroidLayerBenchmark {
    @OptIn(ExperimentalTime::class)
    @Test
    fun allOperations() {
        val args = InstrumentationRegistry.getArguments()
        val quick =
            args.getString("quick") == "true" ||
                args.getString("androidx.benchmark.dryRunMode.enable") == "true"
        val ctx =
            linkedMapOf(
                "date" to
                    Clock.System
                        .now()
                        .toString()
                        .take(10),
            )
        ctx.putAll(platformContext())
        ctx["machine"] = args.getString("machine") ?: "unknown"
        ctx["harness"] = args.getString("harness") ?: "unknown"
        val json = Runner(platformLayer, platformImpls, quick, ::logErr).runAll(ctx)

        val dir =
            args.getString("additionalTestOutputDir")?.let(::File)
                ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        dir.mkdirs()
        File(dir, "schnorr256k1-bench-android.json").writeText(json)
        logErr("wrote ${File(dir, "schnorr256k1-bench-android.json")}")
    }
}
