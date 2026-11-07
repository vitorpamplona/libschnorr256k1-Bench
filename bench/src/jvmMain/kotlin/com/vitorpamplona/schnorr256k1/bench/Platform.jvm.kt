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

import java.io.File

actual val platformImpls: List<Impl> = listOf(AcinqImpl("jni"), CImpl("jni"), KotlinImpl)

actual val platformLayer: String = "jvm"

actual fun platformContext(): Map<String, String> {
    val cpu =
        runCatching {
            File("/proc/cpuinfo")
                .readLines()
                .firstOrNull { it.startsWith("model name") }
                ?.substringAfter(":")
                ?.trim()
        }.getOrNull() ?: System.getProperty("os.arch")
    return linkedMapOf(
        "cpu" to cpu,
        "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        "runtime" to
            "${System.getProperty("java.vm.vendor")} ${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
    )
}

actual fun logErr(message: String) = System.err.println(message)
