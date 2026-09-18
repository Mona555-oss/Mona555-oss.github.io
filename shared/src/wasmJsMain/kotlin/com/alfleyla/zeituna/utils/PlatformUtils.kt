package com.alfleyla.zeituna.utils

import kotlinx.browser.window
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import kotlinx.browser.document
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.FileReader
import org.w3c.files.get

actual fun base64Encode(input: ByteArray): String {
    var binary = ""
    for (i in 0 until input.size) {
        binary += (input[i].toInt() and 0xFF).toChar()
    }
    return window.btoa(binary)
}

actual fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray {
    fun rotateLeft(v: Int, c: Int): Int = (v shl c) or (v ushr (32 - c))

    fun md5(input: ByteArray): ByteArray {
        val S = intArrayOf(7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21)
        val K = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin(i + 1.0)) * 4294967296.0).toLong().toInt() }
        val initialLen = input.size
        val newLen = ((initialLen + 8) shr 6).plus(1) shl 6
        val msg = ByteArray(newLen)
        for (i in input.indices) msg[i] = input[i]
        msg[initialLen] = 0x80.toByte()
        val lenBits = initialLen.toLong() * 8
        for (i in 0..7) msg[newLen - 8 + i] = (lenBits shr (i * 8)).toByte()
        var a = 0x67452301
        var b = 0xEFCDAB89.toInt()
        var c = 0x98BADCFE.toInt()
        var d = 0x10325476
        val words = IntArray(newLen shr 2)
        for (i in words.indices) words[i] = (msg[i*4].toInt() and 0xFF) or ((msg[i*4+1].toInt() and 0xFF) shl 8) or ((msg[i*4+2].toInt() and 0xFF) shl 16) or ((msg[i*4+3].toInt() and 0xFF) shl 24)
        for (i in 0 until (newLen shr 6)) {
            val aa = a; val bb = b; val cc = c; val dd = d
            val offset = i shl 4
            for (j in 0..63) {
                var f = 0; var g = 0
                if (j < 16) { f = (b and c) or (b.inv() and d); g = j }
                else if (j < 32) { f = (d and b) or (d.inv() and c); g = (5 * j + 1) % 16 }
                else if (j < 48) { f = b xor c xor d; g = (3 * j + 5) % 16 }
                else { f = c xor (b or d.inv()); g = (7 * j) % 16 }
                val temp = d; d = c; c = b
                b = b + rotateLeft(a + f + K[j] + words[offset + g], S[j])
                a = temp
            }
            a += aa; b += bb; c += cc; d += dd
        }
        val res = ByteArray(16)
        fun write(v: Int, o: Int) { res[o]=v.toByte(); res[o+1]=(v shr 8).toByte(); res[o+2]=(v shr 16).toByte(); res[o+3]=(v shr 24).toByte() }
        write(a, 0); write(b, 4); write(c, 8); write(d, 12)
        return res
    }

    var k = if (key.size > 64) md5(key) else key
    if (k.size < 64) k = k.copyOf(64)
    val iwd = ByteArray(64) { i -> (k[i].toInt() xor 0x36).toByte() }
    val owd = ByteArray(64) { i -> (k[i].toInt() xor 0x5c).toByte() }
    return md5(owd + md5(iwd + data))
}

actual fun platformLog(tag: String, message: String, isError: Boolean) {
    val logMessage = "$tag: $message"
    if (isError) {
        println("ERROR: $logMessage")
    } else {
        println(logMessage)
    }
}

actual fun platformOpenUrl(url: String) {
    window.location.href = url
}

actual fun platformGetCurrentUrl(): String {
    return window.location.href
}

actual fun platformClearUrlParams() {
    try {
        window.history.replaceState(null, "", window.location.pathname)
    } catch (e: Throwable) {}
}

actual fun platformPutSessionData(key: String, value: String) {
    window.sessionStorage.setItem(key, value)
}

actual fun platformGetSessionData(key: String): String? {
    return window.sessionStorage.getItem(key)
}

private fun getTimeZoneJS(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")

actual fun platformGetTimeZoneId(): String {
    return try {
        getTimeZoneJS()
    } catch (e: Throwable) {
        "UTC"
    }
}

actual fun platformPickFile(allowedExtensions: List<String>, onResult: (ByteArray?, String?) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"
    input.accept = allowedExtensions.joinToString(",") { if (it.startsWith(".")) it else ".$it" }

    input.onchange = {
        val file = input.files?.get(0)
        if (file != null) {
            val reader = FileReader()
            reader.onload = {
                val arrayBuffer = reader.result as ArrayBuffer
                val uint8Array = Uint8Array(arrayBuffer)
                val byteArray = ByteArray(uint8Array.length) { i -> uint8Array[i] }
                onResult(byteArray, file.name)
            }
            reader.readAsArrayBuffer(file)
        } else {
            onResult(null, null)
        }
    }
    input.click()
}
