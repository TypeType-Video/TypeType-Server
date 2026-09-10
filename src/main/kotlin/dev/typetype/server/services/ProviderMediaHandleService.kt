package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

internal enum class ProviderMediaType {
    BILIBILI,
    NICONICO,
}

@Serializable
internal data class ProviderMediaTarget(
    val url: String,
    val domandBid: String? = null,
)

class ProviderMediaHandleService(
    private val cache: CacheService,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val writeLimiter = Semaphore(MAX_CONCURRENT_CACHE_WRITES)
    private val inFlightWrites = ConcurrentHashMap<String, CompletableDeferred<String>>()

    internal suspend fun materialize(response: StreamResponse, provider: ProviderMediaType): StreamResponse = coroutineScope {
        val hls = async { response.hlsUrl.handleIfRemote(provider) }
        val dash = async { response.dashMpdUrl.handleIfRemote(provider) }
        val video = async { response.videoStreams.withHandledVideoUrls(provider) }
        val audio = async { response.audioStreams.withHandledAudioUrls(provider) }
        val videoOnly = async { response.videoOnlyStreams.withHandledVideoUrls(provider) }
        response.copy(
            hlsUrl = hls.await(),
            dashMpdUrl = dash.await(),
            videoStreams = video.await(),
            audioStreams = audio.await(),
            videoOnlyStreams = videoOnly.await(),
        )
    }

    internal suspend fun createPath(rawUrl: String, domandBid: String? = null): String {
        val (cleanUrl, fragment) = rawUrl.splitFragment()
        val resolvedBid = domandBid?.takeIf { it.isNotBlank() }
            ?: fragment.takeIf { it.isNotBlank() }?.let(::parseNicoCookie)
        val target = requireProxyTarget(stripTrackingParams(cleanUrl))
        require(target.provider == ProxyProvider.BILIBILI || target.provider == ProxyProvider.NICONICO) {
            "Unsupported provider media URL"
        }
        val normalizedUrl = stripTrackingParams(target.url.toString())
        val handle = handleId(normalizedUrl, resolvedBid)
        val ttl = mediaHandleTtlSeconds(normalizedUrl)
        val key = cacheKey(handle)
        val path = "/media/$handle"
        val pending = CompletableDeferred<String>()
        val existing = inFlightWrites.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        return try {
            val value = CacheJson.encodeToString(ProviderMediaTarget(normalizedUrl, resolvedBid))
            writeLimiter.withPermit { cache.set(key, value, ttl) }
            pending.complete(path)
            path
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlightWrites.remove(key, pending)
        }
    }

    internal suspend fun resolve(handle: String): ProviderMediaTarget? {
        if (!HANDLE_PATTERN.matches(handle)) return null
        return cache.get(cacheKey(handle))?.let { encoded ->
            runCatching { CacheJson.decodeFromString<ProviderMediaTarget>(encoded) }.getOrNull()
        }
    }

    internal fun relativeManifestPath(path: String): String =
        "../media/${path.substringAfterLast('/')}"

    private suspend fun String.handleIfRemote(provider: ProviderMediaType): String {
        if (isBlank() || (startsWith("/") && !startsWith("//"))) return this
        val candidate = if (startsWith("//")) "https:$this" else this
        return if (supportsProvider(candidate, provider)) createPath(candidate) else this
    }

    private suspend fun List<VideoStreamItem>.withHandledVideoUrls(provider: ProviderMediaType): List<VideoStreamItem> = coroutineScope {
        map { item ->
            async {
                item.copy(
                    url = item.url.handleIfRemote(provider),
                    manifestUrl = item.manifestUrl?.handleIfRemote(provider),
                    sabrSessionUrl = item.sabrSessionUrl?.handleIfRemote(provider),
                )
            }
        }.awaitAll()
    }

    private suspend fun List<AudioStreamItem>.withHandledAudioUrls(provider: ProviderMediaType): List<AudioStreamItem> = coroutineScope {
        map { item ->
            async {
                item.copy(
                    url = item.url.handleIfRemote(provider),
                    manifestUrl = item.manifestUrl?.handleIfRemote(provider),
                    sabrSessionUrl = item.sabrSessionUrl?.handleIfRemote(provider),
                )
            }
        }.awaitAll()
    }

    private fun supportsProvider(url: String, provider: ProviderMediaType): Boolean =
        runCatching { requireProxyTarget(url).provider }.getOrNull() == provider.toProxyProvider()

    private fun handleId(url: String, domandBid: String?): String {
        val input = buildString {
            append("provider-media:v1:")
            append(url)
            append('\u0000')
            append(domandBid.orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(HANDLE_BYTES))
        return "m1_$encoded"
    }

    private fun mediaHandleTtlSeconds(url: String): Long {
        val decoded = runCatching { URLDecoder.decode(url, StandardCharsets.UTF_8) }.getOrDefault(url)
        val expiry = EXPIRY_PATTERN.findAll(decoded)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .minOrNull()
        val ttl = expiry?.minus(nowSeconds())?.minus(EXPIRY_SAFETY_SECONDS)
            ?: DEFAULT_TTL_SECONDS
        if (ttl <= 0L) throw IllegalArgumentException("Provider media URL has expired")
        return ttl.coerceAtMost(MAX_TTL_SECONDS)
    }

    private fun String.splitFragment(): Pair<String, String> {
        val index = indexOf('#')
        return if (index < 0) this to "" else substring(0, index) to substring(index + 1)
    }

    private companion object {
        const val HANDLE_BYTES = 18
        const val DEFAULT_TTL_SECONDS = 1_800L
        const val MAX_TTL_SECONDS = 3_600L
        const val EXPIRY_SAFETY_SECONDS = 30L
        const val MAX_CONCURRENT_CACHE_WRITES = 32
        val HANDLE_PATTERN = Regex("m1_[A-Za-z0-9_-]{24}")
        val EXPIRY_PATTERN = Regex("(?:^|[?&#=])(?:deadline|Expires|exp)=(\\d+)", RegexOption.IGNORE_CASE)

        fun cacheKey(handle: String): String = "provider-media:v1:$handle"

        fun ProviderMediaType.toProxyProvider(): ProxyProvider = when (this) {
            ProviderMediaType.BILIBILI -> ProxyProvider.BILIBILI
            ProviderMediaType.NICONICO -> ProxyProvider.NICONICO
        }
    }
}
