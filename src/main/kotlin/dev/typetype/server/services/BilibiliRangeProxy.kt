package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private const val BILIBILI_RANGE_ATTEMPTS = 3

internal fun readBilibiliRangeWithRetry(
    execute: (Request) -> Response,
    request: Request,
    checkActive: () -> Unit = {},
): ExtractionResult<ProxyResponse> {
    var lastMessage = "Proxy fetch failed"
    for (attempt in 1..BILIBILI_RANGE_ATTEMPTS) {
        checkActive()
        val response = try {
            execute(request)
        } catch (error: IOException) {
            lastMessage = error.message ?: "Proxy fetch failed"
            continue
        }
        if (!response.isSuccessful) {
            lastMessage = "Upstream returned ${response.code}"
            response.close()
            continue
        }
        val stream = RetryingProxyInputStream(execute, request, response, BILIBILI_RANGE_ATTEMPTS - attempt, checkActive)
        return ExtractionResult.Success(ProxyResponse(
            status = response.code,
            contentType = response.header("Content-Type") ?: "application/octet-stream",
            contentLength = response.body.contentLength().takeIf { it >= 0 },
            contentRange = response.header("Content-Range"),
            acceptRanges = response.header("Accept-Ranges"),
            cacheControl = response.header("Cache-Control"),
            stream = stream,
            close = stream::close,
        ))
    }
    return ExtractionResult.Failure(lastMessage)
}
