package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Collections
import java.util.WeakHashMap

internal object SabrInitializationData {
    private const val CACHE_TTL_SECONDS = 21_600L
    private val memoryCache = BoundedExpiringCache<String, ByteArray>(
        maxEntries = 512,
        maxWeight = 32L * 1024L * 1024L,
        ttl = Duration.ofSeconds(CACHE_TTL_SECONDS),
        weigher = { it.size.toLong() },
    )
    private val formatCache = Collections.synchronizedMap(WeakHashMap<YoutubeSabrFormat, ByteArray>())

    suspend fun ingest(
        format: YoutubeSabrFormat,
        holder: SabrSessionHolder,
        cache: CacheService? = null,
    ): Boolean {
        val data = fetch(holder.key.videoId, format, cache) ?: return false
        return holder.session.streamState.ingestInitializationData(format, data)
    }

    suspend fun fetch(videoId: String, format: YoutubeSabrFormat, cache: CacheService? = null): ByteArray? {
        val key = cacheKey(videoId, format)
        formatCache[format]?.let { return it }
        memoryCache.get(key)?.let {
            formatCache[format] = it
            return it
        }
        cache?.getBytes(key)?.let { bytes ->
            memoryCache.put(key, bytes)
            formatCache[format] = bytes
            return bytes
        }
        return null
    }

    suspend fun remember(
        videoId: String,
        format: YoutubeSabrFormat,
        bytes: ByteArray,
        cache: CacheService? = null,
    ): Unit {
        val key = cacheKey(videoId, format)
        memoryCache.put(key, bytes)
        formatCache[format] = bytes
        cache?.setBytes(key, bytes, CACHE_TTL_SECONDS)
    }

    fun ingestRemembered(format: YoutubeSabrFormat, holder: SabrSessionHolder): Boolean {
        val bytes = formatCache[format] ?: return false
        return holder.session.streamState.ingestInitializationData(format, bytes)
    }

    fun evictExpired(): Unit = memoryCache.evictExpired()

    suspend fun bootstrap(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        cache: CacheService? = null,
    ): ByteArray? {
        val initialized = runCatchingNonCancellation {
            holder.pumpMutex.withLock {
                holder.withPlayerContext { bootstrapInitialization(Localization("en", "US")) }
                listOf(holder.audioFormat, holder.videoFormat).mapNotNull { candidate ->
                    holder.session.getCachedSegment(SabrSegmentRequest.initialization(candidate))
                        ?.data
                        ?.let { candidate to it }
                }
            }
        }.getOrNull() ?: return null
        initialized.forEach { (candidate, bytes) -> remember(holder.key.videoId, candidate, bytes, cache) }
        return initialized.firstOrNull { (candidate) -> candidate.matches(format) }?.second
    }

    private suspend fun CacheService.getBytes(key: String): ByteArray? = runCatching {
        get(key)?.let { Base64.getDecoder().decode(it) }
    }.getOrNull()

    private suspend fun CacheService.setBytes(key: String, bytes: ByteArray, ttlSeconds: Long): Unit {
        runCatching { set(key, Base64.getEncoder().encodeToString(bytes), ttlSeconds) }
    }

    private fun cacheKey(videoId: String, format: YoutubeSabrFormat): String {
        val raw = listOf(
            videoId,
            format.itag,
            format.lastModified,
            format.xtags.orEmpty(),
            format.mimeType.orEmpty(),
            format.audioTrackId.orEmpty(),
        )
            .joinToString("|")
        return "sabr:init:v3:${sha256(raw)}"
    }

    private fun YoutubeSabrFormat.matches(other: YoutubeSabrFormat): Boolean =
        itag == other.itag && audioTrackId == other.audioTrackId && xtags == other.xtags

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
