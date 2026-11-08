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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fprintf
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.stderr
import platform.posix.uname
import platform.posix.utsname
import kotlin.experimental.ExperimentalNativeApi

actual val platformLayer: String = "native"

@OptIn(ExperimentalNativeApi::class)
actual fun platformContext(): Map<String, String> =
    linkedMapOf(
        "cpu" to (nativeCpuName() ?: Platform.cpuArchitecture.name),
        "os" to (unameOs() ?: Platform.osFamily.name),
        "runtime" to "Kotlin/Native ${KotlinVersion.CURRENT}",
    )

/** "Linux 6.8.0", "Darwin 24.1.0": as the JVM layer's os.name and os.version. */
@OptIn(ExperimentalForeignApi::class)
private fun unameOs(): String? =
    memScoped {
        val u = alloc<utsname>()
        if (uname(u.ptr) == 0) "${u.sysname.toKString()} ${u.release.toKString()}" else null
    }

/** The CPU's marketing name, from the OS (/proc/cpuinfo, sysctl), or null. */
internal expect fun nativeCpuName(): String?

@OptIn(ExperimentalForeignApi::class)
actual fun readFileBytes(path: String): ByteArray? {
    val f = fopen(path, "rb") ?: return null
    try {
        if (fseek(f, 0, SEEK_END) != 0) return null
        val len = ftell(f)
        if (len < 0 || fseek(f, 0, SEEK_SET) != 0) return null
        val out = ByteArray(len.toInt())
        if (out.isEmpty()) return out
        val read = out.usePinned { fread(it.addressOf(0), 1u, len.toULong(), f) }
        return if (read == len.toULong()) out else null
    } finally {
        fclose(f)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun environment(name: String): String? = getenv(name)?.toKString()

@OptIn(ExperimentalForeignApi::class)
actual fun logErr(message: String) {
    fprintf(stderr, "%s\n", message)
    fflush(stderr)
}
