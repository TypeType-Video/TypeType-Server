package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

class YoutubeStreamGuards(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val egressRotation: GluetunEgressRotation? = null,
) {
    val watch = StreamRequestWatch()
    private val pace = StreamOpenPace()
    private val renewal = ProviderSessionRenewal { videoUrl ->
        val videoId = YoutubeTypeTypeMapper.videoId(videoUrl) ?: return@ProviderSessionRenewal false
        egressRotation?.rotate("bot_check")
        tokenClient.fetch(videoId, forceRefresh = true) != null
    }

    fun cached(delegate: StreamService, cache: CacheService, cachePrefix: String): CachedStreamService =
        CachedStreamService(delegate, cache, cachePrefix, watch, pace, renewal)
}
