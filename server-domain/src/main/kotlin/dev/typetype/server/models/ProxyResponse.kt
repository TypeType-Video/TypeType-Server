package dev.typetype.server.models

import java.io.InputStream

class ProxyResponse(
    val status: Int,
    val contentType: String,
    val contentLength: Long?,
    val contentRange: String?,
    val acceptRanges: String?,
    val stream: InputStream,
    val close: () -> Unit,
    val cacheControl: String? = null,
)
