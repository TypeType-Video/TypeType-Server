package dev.typetype.server.services

import dev.typetype.server.cache.CacheService

/**
 * A bot check is handed to the same recovery as a session kept on hold, so one thing at a time
 * replaces the token session or moves the egress: two of them at once judge exits with each
 * other's sessions.
 */
class YoutubeStreamGuards(
    private val recoverSession: (reason: String) -> Boolean,
) {
    val watch = StreamRequestWatch()
    private val pace = StreamOpenPace()
    private val renewal = ProviderSessionRenewal { recoverSession("bot_check") }

    fun cached(delegate: StreamService, cache: CacheService, cachePrefix: String): CachedStreamService =
        CachedStreamService(delegate, cache, cachePrefix, watch, pace, renewal)
}
