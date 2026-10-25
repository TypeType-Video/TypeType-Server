package dev.typetype.server.services

import dev.typetype.server.models.StreamResponse
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val DEFAULT_STREAM_TTL_SECONDS = 21_600L
private const val DISLIKE_UNAVAILABLE_STREAM_TTL_SECONDS = 300L
private const val SIGNED_PROVIDER_STREAM_MAX_TTL_SECONDS = 3_600L
private const val SIGNED_STREAM_DEADLINE_SAFETY_SECONDS = 300L
private const val MIN_CACHEABLE_STREAM_TTL_SECONDS = 60L
private const val FINISHED_LIVE_STREAM_TTL_SECONDS = 300L
private const val PER_CLIENT_HLS_MANIFEST_PREFIX = "/streams/hls-manifest?token="

fun StreamResponse.streamCacheTtlSeconds(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Long {
    if (isLive || isPostLive || hlsUrl.startsWith(PER_CLIENT_HLS_MANIFEST_PREFIX)) return 0L
    val ttl = signedStreamTtlSeconds(nowEpochSeconds)
    return if (isLiveContent) minOf(ttl, FINISHED_LIVE_STREAM_TTL_SECONDS) else ttl
}

private fun StreamResponse.signedStreamTtlSeconds(nowEpochSeconds: Long): Long {
    val urls = signedMediaUrls().toList()
    val deadline = urls.mapNotNull { it.mediaExpiryEpochSeconds() }.minOrNull()
        ?: return stableMetadataTtlSeconds()
    val ttl = deadline - nowEpochSeconds - SIGNED_STREAM_DEADLINE_SAFETY_SECONDS
    if (ttl < MIN_CACHEABLE_STREAM_TTL_SECONDS) return 0L
    val providerCap = if (urls.any { it.isSignedProviderUrl() }) {
        SIGNED_PROVIDER_STREAM_MAX_TTL_SECONDS
    } else {
        Long.MAX_VALUE
    }
    return minOf(ttl, providerCap, stableMetadataTtlSeconds())
}

private fun StreamResponse.stableMetadataTtlSeconds(): Long =
    if (dislikeCount < 0L) DISLIKE_UNAVAILABLE_STREAM_TTL_SECONDS else DEFAULT_STREAM_TTL_SECONDS

private fun StreamResponse.signedMediaUrls(): Sequence<String> = sequence {
    yield(hlsUrl)
    yield(dashMpdUrl)
    videoStreams.forEach {
        yield(it.url)
        yield(it.manifestUrl.orEmpty())
        yield(it.sabrSessionUrl.orEmpty())
    }
    videoOnlyStreams.forEach {
        yield(it.url)
        yield(it.manifestUrl.orEmpty())
        yield(it.sabrSessionUrl.orEmpty())
    }
    audioStreams.forEach {
        yield(it.url)
        yield(it.manifestUrl.orEmpty())
        yield(it.sabrSessionUrl.orEmpty())
    }
}

private fun String.mediaExpiryEpochSeconds(): Long? {
    var decoded = this
    repeat(3) {
        val next = runCatching { URLDecoder.decode(decoded, StandardCharsets.UTF_8) }.getOrNull()
            ?: return@repeat
        if (next != decoded) decoded = next
    }
    return MEDIA_EXPIRY_REGEX.findAll(decoded)
        .mapNotNull { it.groupValues[1].toLongOrNull() }
        .minOrNull()
}

private fun String.isSignedProviderUrl(): Boolean {
    val host = runCatching { URI(this).host.orEmpty() }.getOrDefault("")
    val provider = providerForProxyHost(host)
    return provider == ProxyProvider.BILIBILI || provider == ProxyProvider.NICONICO
}

private val MEDIA_EXPIRY_REGEX = Regex(
    "(?:^|[?&#=])(?:deadline|expire|expires|exp|expiry|expire_at|expires_at)=([0-9]+)",
    RegexOption.IGNORE_CASE,
)
