package com.alfleyla.zeituna.utils

import platform.Foundation.*
import platform.CoreCrypto.*
import platform.UIKit.UIApplication
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual fun base64Encode(input: ByteArray): String {
    val data = input.toNSData()
    return data.base64EncodedStringWithOptions(0UL)
}

@OptIn(ExperimentalForeignApi::class)
actual fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray {
    val hash = ByteArray(CC_MD5_DIGEST_LENGTH)
    key.usePinned { keyPinned ->
        data.usePinned { dataPinned ->
            hash.usePinned { hashPinned ->
                CCHmac(
                    kCCHmacAlgMD5,
                    keyPinned.addressOf(0),
                    key.size.toULong(),
                    dataPinned.addressOf(0),
                    data.size.toULong(),
                    hashPinned.addressOf(0)
                )
            }
        }
    }
    return hash
}

actual fun platformLog(tag: String, message: String, isError: Boolean) {
    NSLog("%@: %@", tag, message)
}

actual fun platformOpenUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl != null) {
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
    }
}

actual fun platformGetTimeZoneId(): String {
    return NSTimeZone.localTimeZone.name
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.dataWithBytes(refTo(0), size.toULong())
}
