package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.InputStream

internal suspend fun ApplicationCall.respondProxyResult(
    result: ExtractionResult<ProxyResponse>,
    contentTypeOverride: String? = null,
): Unit = when (result) {
    is ExtractionResult.Success -> respondProxy(result.data, contentTypeOverride)
    is ExtractionResult.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
    is ExtractionResult.Failure -> respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
}

private suspend fun ApplicationCall.respondProxy(proxy: ProxyResponse, contentTypeOverride: String?): Unit {
    try {
        val status = HttpStatusCode.fromValue(proxy.status)
        val contentType = ContentType.parse(contentTypeOverride ?: proxy.contentType)
        proxy.contentRange?.let { response.headers.append("Content-Range", it) }
        proxy.acceptRanges?.let { response.headers.append("Accept-Ranges", it) }
        proxy.cacheControl?.let { response.headers.append("Cache-Control", it, safeOnly = false) }
        respondBytesWriter(contentType, status, proxy.contentLength) {
            copyProxyStream(proxy.stream, this)
        }
    } finally {
        proxy.close()
    }
}

internal suspend fun copyProxyStream(source: InputStream, destination: ByteWriteChannel): Unit = stopOnClientDisconnect {
    val buffer = ByteArray(PROXY_COPY_BUFFER_SIZE)
    while (true) {
        val read = runInterruptible(Dispatchers.IO) { source.read(buffer) }
        if (read < 0) break
        destination.writeFully(buffer, 0, read)
    }
    destination.flush()
}

private const val PROXY_COPY_BUFFER_SIZE = 64 * 1024
