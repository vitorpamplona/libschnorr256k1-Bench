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

import android.os.Build
import android.util.Log
import java.io.File

// libschnorr256k1-kmp ships arm64-v8a and x86_64 only: on a 32-bit device its row is left out.
actual val platformImpls: List<Impl> =
    listOfNotNull(
        AcinqImpl("jni"),
        try {
            CImpl("jni")
        } catch (e: UnsatisfiedLinkError) {
            logErr("libschnorr256k1 skipped: no ${Build.SUPPORTED_ABIS.firstOrNull()} library (${e.message})")
            null
        },
        KotlinImpl,
    )

actual val platformLayer: String = "android"

actual fun platformContext(): Map<String, String> =
    linkedMapOf(
        "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "cpu" to
            (if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE) +
            " (${Build.SUPPORTED_ABIS.firstOrNull()})",
        "os" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "runtime" to "ART ${System.getProperty("java.vm.version")}",
    )

actual fun logErr(message: String) {
    Log.i("schnorr256k1-bench", message)
}

// On a device the dataset comes from the instrumentation APK's assets (bench-android).
actual fun readFileBytes(path: String): ByteArray? = runCatching { File(path).readBytes() }.getOrNull()

actual fun environment(name: String): String? = System.getenv(name)
