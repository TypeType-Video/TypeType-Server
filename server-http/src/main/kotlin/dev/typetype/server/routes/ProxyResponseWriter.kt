package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        respondOutputStream(contentType, status, proxy.contentLength) {
            withContext(Dispatchers.IO) { proxy.stream.copyTo(this@respondOutputStream, PROXY_COPY_BUFFER_SIZE) }
        }
    } finally {
        proxy.close()
    }
}

private const val PROXY_COPY_BUFFER_SIZE = 64 * 1024
