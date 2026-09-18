package com.alfleyla.zeituna.utils

expect fun base64Encode(input: ByteArray): String

expect fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray

expect fun platformLog(tag: String, message: String, isError: Boolean = false)

expect fun platformOpenUrl(url: String)

expect fun platformGetCurrentUrl(): String

expect fun platformClearUrlParams()

expect fun platformPutSessionData(key: String, value: String)

expect fun platformGetSessionData(key: String): String?

expect fun platformGetTimeZoneId(): String

/**
 * Opens a platform-specific file picker and returns the file bytes and name.
 */
expect fun platformPickFile(allowedExtensions: List<String>, onResult: (ByteArray?, String?) -> Unit)
