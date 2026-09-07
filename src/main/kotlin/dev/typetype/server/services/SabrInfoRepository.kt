package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.time.Duration

internal class SabrInfoRepository(
    infoCache: SabrPreparedInfoCache,
    sharedCache: CacheService?,
) {
    private val preparedInfos = infoCache
    private val sharedInfos = SabrInfoSharedCache(sharedCache)
    private val initializationInfos = BoundedExpiringCache<String, YoutubeSabrInfo>(
        maxEntries = 256,
        ttl = Duration.ofHours(6),
    )

    fun local(videoId: String, startTimeMs: Long): SabrPreparedInfo? {
        val cachedAtStart = preparedInfos.get(videoId, startTimeMs)
        val cached = cachedAtStart ?: if (startTimeMs > 0L) preparedInfos.get(videoId, startTimeMs = 0L) else null
        cached ?: return null
        if (cached.hasAudioAndVideoFormats()) return cached
        preparedInfos.remove(videoId, if (cachedAtStart == null) 0L else startTimeMs)
        return null
    }

    suspend fun shared(videoId: String, token: SabrTokenBundle): SabrPreparedInfo? {
        sharedInfos.getInitialization(videoId)?.let { initializationInfos.put(videoId, it) }
        val info = sharedInfos.getPlayback(videoId) ?: return null
        if (info.visitorData != token.visitorData) return null
        return putPrepared(videoId, startTimeMs = 0L, SabrPreparedInfo(info, token), share = false)
    }

    suspend fun rememberInitialization(videoId: String, info: YoutubeSabrInfo): Unit {
        if (!SabrPreparedInfo(info, null).hasAudioAndVideoFormats()) return
        initializationInfos.put(videoId, info)
        sharedInfos.putInitialization(videoId, info)
    }

    suspend fun invalidatePlayback(videoId: String): Unit {
        preparedInfos.remove(videoId)
        sharedInfos.removePlayback(videoId)
    }

    suspend fun putPrepared(
        videoId: String,
        startTimeMs: Long,
        prepared: SabrPreparedInfo,
        share: Boolean = true,
    ): SabrPreparedInfo {
        preparedInfos.put(videoId, startTimeMs, prepared)
        if (share) sharedInfos.putPlayback(videoId, prepared.info)
        return prepared
    }

    fun initializationFormat(videoId: String, target: YoutubeSabrFormat): YoutubeSabrFormat? =
        initializationInfos.get(videoId)?.formats?.firstOrNull {
            it.itag == target.itag && it.audioTrackId == target.audioTrackId && it.xtags == target.xtags
        }

    fun evictExpired() {
        preparedInfos.evictExpired()
        initializationInfos.evictExpired()
    }
}
