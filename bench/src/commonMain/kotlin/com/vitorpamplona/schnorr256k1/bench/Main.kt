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

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * bench [--dataset FILE] [--quick] [--library NAME] [--device TEXT] [--machine dedicated|shared] [--harness COMMIT]
 * Prints a schema-v2 result (results/SCHEMA.md) on stdout. Without --dataset, BENCH_DATASET.
 */
@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) {
    var quick = false
    var only: String? = null
    var datasetPath: String? = null
    val ctx =
        linkedMapOf(
            "date" to
                Clock.System
                    .now()
                    .toString()
                    .take(10),
        )
    ctx.putAll(platformContext())
    if ("device" !in ctx) ctx["device"] = "unknown"
    ctx["machine"] = "unknown"
    ctx["harness"] = "unknown"
    var a = 0
    while (a < args.size) {
        when (args[a]) {
            "--quick" -> quick = true
            "--dataset" -> datasetPath = args[++a]
            "--library" -> only = args[++a]
            "--device" -> ctx["device"] = args[++a]
            "--machine" -> ctx["machine"] = args[++a]
            "--harness" -> ctx["harness"] = args[++a]
            else -> error("unknown option ${args[a]}")
        }
        a++
    }
    println(Runner(platformLayer, platformImpls, loadDataset(datasetPath), quick, ::logErr).runAll(ctx, only))
}
