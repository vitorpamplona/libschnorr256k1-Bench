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

/** results/SCHEMA.md "layer": jvm, android or native. */
expect val platformLayer: String

/** The run context this platform can tell about itself: os, cpu, runtime, device. */
expect fun platformContext(): Map<String, String>

/** A whole file, or null if it cannot be read. */
expect fun readFileBytes(path: String): ByteArray?

/** An environment variable (BENCH_DATASET: the dataset's path, for the tests and runJvm). */
expect fun environment(name: String): String?

/** The dataset at [path], else at BENCH_DATASET; fails if neither is readable. */
fun loadDataset(path: String?): Dataset {
    val p = path ?: environment("BENCH_DATASET") ?: error("no dataset: pass --dataset FILE or set BENCH_DATASET")
    val ds = Dataset(readFileBytes(p) ?: error("cannot read the dataset at $p"))
    check(ds.sha256Hex == DatasetV1.FILE_SHA256) { "$p is not datasets/nostr-v1.bin (sha256 ${ds.sha256Hex})" }
    return ds
}

/** Progress messages, kept off the JSON on stdout. */
expect fun logErr(message: String)
