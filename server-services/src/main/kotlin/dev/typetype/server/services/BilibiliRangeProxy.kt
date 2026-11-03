package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ProxyResponse
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal const val BILIBILI_RANGE_ATTEMPTS = 3

fun readBilibiliRangeWithRetry(
    execute: (Request) -> Response,
    request: Request,
    checkActive: () -> Unit = {},
    alternateUrls: List<String> = emptyList(),
    mirrorExecute: ((Request) -> Response)? = null,
    mirrorPermits: Semaphore? = null,
    streamReadTimeoutMillis: Int = 10_000,
    nanoTime: () -> Long = System::nanoTime,
    cdnRegion: String? = BilibiliCdnConfig.defaultRegion(),
    enableSubchunks: Boolean = BilibiliCdnConfig.isSubchunkProxyEnabled(),
    enablePrefetch: Boolean = BilibiliCdnConfig.isPrefetchEnabled(),
): ExtractionResult<ProxyResponse> {
    val isAkamaiBlacklisted = BilibiliAkamaiConfig.isAkamaiBlacklisted()
    val isPrimaryAkamai = BilibiliAkamaiConfig.isAkamaiUrl(request.url.toString())
    val effectiveRegion = BilibiliCdnConfig.resolveRegion(cdnRegion)
    val effectiveAlternates = if (alternateUrls.isEmpty()) {
        bilibiliUposMirrorAlternates(request.url.toString(), isAkamaiBlacklisted, effectiveRegion)
    } else {
        alternateUrls
    }
    val sortedAlternates = prioritizeUposMirrors(effectiveAlternates.distinct(), effectiveRegion)

    val primaryHost = request.url.host
    val isPrimaryPreferred = when (effectiveRegion) {
        "overseas" -> isOverseasUposHost(primaryHost)
        "domestic" -> isDomesticUposHost(primaryHost)
        else -> true
    }

    val mirrors = if ((isAkamaiBlacklisted && isPrimaryAkamai) || (!isPrimaryPreferred && sortedAlternates.isNotEmpty())) {
        val nonAkamai = if (isAkamaiBlacklisted) {
            sortedAlternates.filter { !BilibiliAkamaiConfig.isAkamaiUrl(it) }
        } else {
            sortedAlternates
        }
        val remaining = if (isAkamaiBlacklisted && isPrimaryAkamai) emptyList() else listOf(request.url.toString())
        (nonAkamai + remaining).distinct().ifEmpty { listOf(request.url.toString()) }
    } else {
        val base = listOf(request.url.toString()) + sortedAlternates.filter { it != request.url.toString() }
        if (isAkamaiBlacklisted) {
            base.filter { !BilibiliAkamaiConfig.isAkamaiUrl(it) }.ifEmpty { base }
        } else {
            base
        }
    }

    if (enableSubchunks) {
        val parsedRange = BilibiliRangeSplitter.parseRange(request.header("Range"))
        val start = parsedRange?.first
        val end = parsedRange?.second
        if (start != null && end != null && start <= end && (end - start + 1) > MAX_BILIBILI_SUBCHUNK_SIZE) {
            return readBilibiliSubchunkedRange(
                execute = execute,
                request = request,
                start = start,
                end = end,
                checkActive = checkActive,
                mirrors = mirrors,
                mirrorExecute = mirrorExecute,
                mirrorPermits = mirrorPermits,
                streamReadTimeoutMillis = streamReadTimeoutMillis,
                nanoTime = nanoTime,
                enablePrefetch = enablePrefetch,
            )
        }
    }

    return readBilibiliDirectRange(
        execute = execute,
        request = request,
        checkActive = checkActive,
        mirrors = mirrors,
        mirrorExecute = mirrorExecute,
        mirrorPermits = mirrorPermits,
        streamReadTimeoutMillis = streamReadTimeoutMillis,
        nanoTime = nanoTime,
    )
}

private fun readBilibiliSubchunkedRange(
    execute: (Request) -> Response,
    request: Request,
    start: Long,
    end: Long,
    checkActive: () -> Unit,
    mirrors: List<String>,
    mirrorExecute: ((Request) -> Response)? = null,
    mirrorPermits: Semaphore? = null,
    streamReadTimeoutMillis: Int = 10_000,
    nanoTime: () -> Long = System::nanoTime,
    enablePrefetch: Boolean = BilibiliCdnConfig.isPrefetchEnabled(),
): ExtractionResult<ProxyResponse> {
    val subRanges = BilibiliRangeSplitter.splitRange(start, end, MAX_BILIBILI_SUBCHUNK_SIZE)
    val firstRange = subRanges[0]

    var lastMessage = "Proxy fetch failed"
    for (attempt in 1..BILIBILI_RANGE_ATTEMPTS) {
        checkActive()
        val activeUrl = mirrors[(attempt - 1) % mirrors.size]
        val baseWithActiveUrl = if (activeUrl == request.url.toString()) request else request.newBuilder().url(activeUrl).build()
        val firstRequest = baseWithActiveUrl.newBuilder()
            .header("Range", "bytes=${firstRange.start}-${firstRange.end}")
            .build()
        val openStarted = nanoTime()
        val response = try {
            execute(firstRequest)
        } catch (error: IOException) {
            BilibiliMirrorSpeedTracker.recordFailure(activeUrl)
            lastMessage = error.message ?: "Proxy fetch failed"
            continue
        }
        if (!response.isSuccessful) {
            BilibiliMirrorSpeedTracker.recordFailure(activeUrl)
            lastMessage = "Upstream returned ${response.code}"
            response.close()
            continue
        }
        val openDurationNanos = (nanoTime() - openStarted).coerceAtLeast(0)
        BilibiliMirrorSpeedTracker.recordSuccess(activeUrl, openDurationNanos / 1_000_000)

        val otherMirrors = mirrors - activeUrl
        if (response.code != 206) {
            val switcher = if (otherMirrors.isNotEmpty() && mirrorExecute != null && mirrorPermits != null) {
                BilibiliSlowMirrorSwitcher(
                    mirrorExecute, otherMirrors, mirrorPermits, checkActive, nanoTime, streamReadTimeoutMillis,
                )
            } else {
                null
            }
            val stream = RetryingProxyInputStream(
                execute, firstRequest, response, BILIBILI_RANGE_ATTEMPTS - attempt,
                checkActive, switcher, openDurationNanos, nanoTime, otherMirrors,
            )
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

        val totalLength = BilibiliRangeSplitter.parseContentRangeTotal(response.header("Content-Range"))
        val effectiveEnd = if (totalLength != null && totalLength > start) {
            minOf(end, totalLength - 1)
        } else {
            end
        }
        val effectiveSubRanges = BilibiliRangeSplitter.splitRange(start, effectiveEnd, MAX_BILIBILI_SUBCHUNK_SIZE)
        val totalPart = totalLength?.toString() ?: "*"
        val compositeContentRange = "bytes $start-$effectiveEnd/$totalPart"
        val totalRequestedBytes = effectiveEnd - start + 1

        val stream = BilibiliSubchunkInputStream(
            execute = execute,
            baseRequest = baseWithActiveUrl,
            subRanges = effectiveSubRanges,
            firstResponse = response,
            attemptsPerChunk = BILIBILI_RANGE_ATTEMPTS,
            checkActive = checkActive,
            alternateUrls = otherMirrors,
            mirrorExecute = mirrorExecute,
            mirrorPermits = mirrorPermits,
            streamReadTimeoutMillis = streamReadTimeoutMillis,
            nanoTime = nanoTime,
            firstOpenDurationNanos = openDurationNanos,
            enablePrefetch = enablePrefetch,
        )

        return ExtractionResult.Success(ProxyResponse(
            status = 206,
            contentType = response.header("Content-Type") ?: "application/octet-stream",
            contentLength = totalRequestedBytes,
            contentRange = compositeContentRange,
            acceptRanges = response.header("Accept-Ranges") ?: "bytes",
            cacheControl = response.header("Cache-Control"),
            stream = stream,
            close = stream::close,
        ))
    }
    return ExtractionResult.Failure(lastMessage)
}

private fun readBilibiliDirectRange(
    execute: (Request) -> Response,
    request: Request,
    checkActive: () -> Unit,
    mirrors: List<String>,
    mirrorExecute: ((Request) -> Response)? = null,
    mirrorPermits: Semaphore? = null,
    streamReadTimeoutMillis: Int = 10_000,
    nanoTime: () -> Long = System::nanoTime,
): ExtractionResult<ProxyResponse> {
    var lastMessage = "Proxy fetch failed"
    for (attempt in 1..BILIBILI_RANGE_ATTEMPTS) {
        checkActive()
        val mirror = mirrors[(attempt - 1) % mirrors.size]
        val opening = if (mirror == request.url.toString()) request else request.newBuilder().url(mirror).build()
        val openStarted = nanoTime()
        val response = try {
            execute(opening)
        } catch (error: IOException) {
            BilibiliMirrorSpeedTracker.recordFailure(mirror)
            lastMessage = error.message ?: "Proxy fetch failed"
            continue
        }
        if (!response.isSuccessful) {
            BilibiliMirrorSpeedTracker.recordFailure(mirror)
            lastMessage = "Upstream returned ${response.code}"
            response.close()
            continue
        }
        val openDurationNanos = (nanoTime() - openStarted).coerceAtLeast(0)
        BilibiliMirrorSpeedTracker.recordSuccess(mirror, openDurationNanos / 1_000_000)
        val otherMirrors = mirrors - mirror
        val switcher = if (otherMirrors.isNotEmpty() && mirrorExecute != null && mirrorPermits != null) {
            BilibiliSlowMirrorSwitcher(
                mirrorExecute, otherMirrors, mirrorPermits, checkActive, nanoTime, streamReadTimeoutMillis,
            )
        } else {
            null
        }
        val stream = RetryingProxyInputStream(
            execute, opening, response, BILIBILI_RANGE_ATTEMPTS - attempt,
            checkActive, switcher, openDurationNanos, nanoTime, otherMirrors,
        )
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
