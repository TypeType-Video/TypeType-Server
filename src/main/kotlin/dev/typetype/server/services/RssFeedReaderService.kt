package dev.typetype.server.services

import dev.typetype.server.models.VideoItem
import java.security.MessageDigest
import java.util.HexFormat

class RssFeedReaderService internal constructor(
    private val settings: AdminSettingsService,
    private val subscriptionFeed: SubscriptionFeedService,
    private val blocked: BlockedService,
    private val repository: RssFeedRepository = RssFeedRepository(),
    private val throttle: RssFeedThrottle = RssFeedThrottle(),
    private val secrets: RssFeedSecret = RssFeedSecret(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun read(feedId: String, secret: String): RssFeedReadResult {
        val config = settings.get()
        val baseUrl = config.rssPublicBaseUrl
        if (!config.rssEnabled || baseUrl == null) return RssFeedReadResult.NotFound
        val stored = repository.find(feedId) ?: return RssFeedReadResult.NotFound
        if (!stored.item.enabled || !repository.userEnabled(stored.userId)) return RssFeedReadResult.NotFound
        if (!secrets.matches(secret, stored.tokenHash)) return RssFeedReadResult.NotFound
        throttle.acquire(feedId, config.rssRateLimitPerMinute)?.let { return RssFeedReadResult.Throttled(it) }

        val scopeChannels = stored.item.channelUrls.takeIf { stored.item.scope == "channels" }?.toSet()
        val profile = blocked.profileFor(stored.userId)
        val now = clock()
        val videos = subscriptionFeed.getCachedAll(stored.userId).orEmpty()
            .asSequence()
            .filter { scopeChannels == null || ChannelUrlCanonicalizer.canonicalize(it.uploaderUrl) in scopeChannels }
            .filter { RssVideoMetadata.serviceId(it) in stored.item.serviceIds }
            .filter { RssVideoTypeFilter.includes(stored.item, it, now) }
            .filter { profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName) }
            .take(config.rssMaxItems)
            .toList()
        val lastModified = RssDocumentRenderer.lastModified(stored.item, videos, now)
        val bytes = RssDocumentRenderer.render(stored.item, videos, baseUrl, lastModified)
        val etag = "\"${HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))}\""
        val lastUsedAt = stored.item.lastUsedAt
        if (lastUsedAt == null || now - lastUsedAt >= LAST_USED_WRITE_INTERVAL_MS) {
            repository.touch(feedId, now)
        }
        return RssFeedReadResult.Ready(bytes, etag, lastModified, config.rssMinimumPollMinutes * 60)
    }

    companion object {
        private const val LAST_USED_WRITE_INTERVAL_MS = 60_000L
    }
}

sealed interface RssFeedReadResult {
    data class Ready(
        val bytes: ByteArray,
        val etag: String,
        val lastModified: Long,
        val maxAgeSeconds: Int,
    ) : RssFeedReadResult

    data class Throttled(val retryAfterSeconds: Int) : RssFeedReadResult
    data object NotFound : RssFeedReadResult
}
