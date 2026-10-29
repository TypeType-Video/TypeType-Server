package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlinx.coroutines.sync.Semaphore

private const val BILIBILI_RANGE_ATTEMPTS = 3

fun readBilibiliRangeWithRetry(
    execute: (Request) -> Response,
    request: Request,
    checkActive: () -> Unit = {},
    alternateUrls: List<String> = emptyList(),
    mirrorExecute: ((Request) -> Response)? = null,
    mirrorPermits: Semaphore? = null,
    streamReadTimeoutMillis: Int = 10_000,
    nanoTime: () -> Long = System::nanoTime,
): ExtractionResult<ProxyResponse> {
    var lastMessage = "Proxy fetch failed"
    val mirrors = listOf(request.url.toString()) + alternateUrls
    for (attempt in 1..BILIBILI_RANGE_ATTEMPTS) {
        checkActive()
        val mirror = mirrors[(attempt - 1) % mirrors.size]
        val opening = if (mirror == mirrors.first()) request else request.newBuilder().url(mirror).build()
        val openStarted = nanoTime()
        val response = try {
            execute(opening)
        } catch (error: IOException) {
            lastMessage = error.message ?: "Proxy fetch failed"
            continue
        }
        if (!response.isSuccessful) {
            lastMessage = "Upstream returned ${response.code}"
            response.close()
            continue
        }
        val otherMirrors = mirrors - mirror
        val switcher = if (otherMirrors.isNotEmpty() && mirrorExecute != null && mirrorPermits != null) {
            BilibiliSlowMirrorSwitcher(
                mirrorExecute, otherMirrors, mirrorPermits, checkActive, nanoTime, streamReadTimeoutMillis,
            )
        } else {
            null
        }
        val stream = RetryingProxyInputStream(execute, opening, response, BILIBILI_RANGE_ATTEMPTS - attempt,
            checkActive, switcher, (nanoTime() - openStarted).coerceAtLeast(0), nanoTime, otherMirrors)
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
