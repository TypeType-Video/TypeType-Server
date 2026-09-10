package dev.typetype.server.services

import dev.typetype.server.models.StreamResponse
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val DEFAULT_STREAM_TTL_SECONDS = 21_600L
private const val DISLIKE_UNAVAILABLE_STREAM_TTL_SECONDS = 300L
private const val BILIBILI_SIGNED_STREAM_MAX_TTL_SECONDS = 3_600L
private const val SIGNED_STREAM_DEADLINE_SAFETY_SECONDS = 300L
private const val MIN_CACHEABLE_STREAM_TTL_SECONDS = 60L

internal fun StreamResponse.streamCacheTtlSeconds(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long {
    val deadline = signedMediaUrls().mapNotNull { it.bilibiliDeadline() }.minOrNull()
        ?: return stableMetadataTtlSeconds()
    val ttl = deadline - nowEpochSeconds - SIGNED_STREAM_DEADLINE_SAFETY_SECONDS
    if (ttl < MIN_CACHEABLE_STREAM_TTL_SECONDS) return 0L
    return minOf(ttl, BILIBILI_SIGNED_STREAM_MAX_TTL_SECONDS, stableMetadataTtlSeconds())
}

private fun StreamResponse.stableMetadataTtlSeconds(): Long =
    if (dislikeCount < 0L) DISLIKE_UNAVAILABLE_STREAM_TTL_SECONDS else DEFAULT_STREAM_TTL_SECONDS

private fun StreamResponse.signedMediaUrls(): Sequence<String> = sequence {
    yield(hlsUrl)
    yield(dashMpdUrl)
    videoStreams.forEach { yield(it.url) }
    videoOnlyStreams.forEach { yield(it.url) }
    audioStreams.forEach { yield(it.url) }
}

private fun String.bilibiliDeadline(): Long? = providerMediaExpiry()

private fun String.providerMediaExpiry(): Long? {
    val host = runCatching { URI(this).host.orEmpty() }.getOrDefault("")
    val provider = providerForProxyHost(host)
    if (provider != ProxyProvider.BILIBILI && provider != ProxyProvider.NICONICO) return null
    val decoded = runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8) }.getOrDefault(this)
    return PROVIDER_EXPIRY_REGEX.findAll(decoded)
        .mapNotNull { it.groupValues[1].toLongOrNull() }
        .minOrNull()
}

private val PROVIDER_EXPIRY_REGEX =
    Regex("(?:^|[?&#=])(?:deadline|expires|exp)=(\\d+)", RegexOption.IGNORE_CASE)
