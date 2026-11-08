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

import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.schnorr256k1.bench.Dataset
import com.vitorpamplona.schnorr256k1.bench.DatasetV1

/** datasets/nostr-v1.bin, from the instrumentation APK's assets (bench-android/build.gradle.kts). */
fun datasetFromAssets(): Dataset {
    val bytes =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open("nostr-v1.bin")
            .use { it.readBytes() }
    return Dataset(bytes).also { check(it.sha256Hex == DatasetV1.FILE_SHA256) { "the APK's dataset is not nostr-v1" } }
}
