package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.UnifiedPushNotificationPayload
import dev.typetype.server.models.VideoItem
import java.security.MessageDigest

internal object PushNotificationSupport {
    fun candidates(
        video: VideoItem,
        sources: Map<String, List<String>>,
        followedSince: Map<String, Long>,
    ): List<PushCandidate> {
        if (video.isShortFormContent || video.isLiveContent || video.isPostLive || video.isLive) return emptyList()
        val channelUrls = sources[video.subscriptionFeedKey()].orEmpty().ifEmpty { listOf(video.uploaderUrl) }
        val publishedAt = RssVideoMetadata.publishedAtMillis(video)
        if (publishedAt <= 0L) return emptyList()
        val videoId = video.id.trim().ifBlank { video.url.trim() }
        if (videoId.isBlank()) return emptyList()
        return channelUrls.map { ChannelUrlCanonicalizer.canonicalize(it) }
            .filter { channel ->
                val subscribedAt = followedSince[channel] ?: return@filter false
                subscribedAt <= 0L || publishedAt >= subscribedAt
            }
            .distinct()
            .map { channel -> PushCandidate(RssVideoMetadata.serviceId(video), channel, videoId, publishedAt, video) }
    }

    fun payload(instanceId: String, eventId: String, candidate: PushCandidate, userId: String): String = CacheJson.encodeToString(
        UnifiedPushNotificationPayload.serializer(),
        UnifiedPushNotificationPayload(
            eventType = "subscription_new_video",
            serviceId = candidate.serviceId,
            serviceName = serviceName(candidate.serviceId),
            eventId = eventId,
            videoId = candidate.videoId,
            videoUrl = candidate.video.url,
            channelId = candidate.channelId,
            channelName = candidate.video.uploaderName,
            channelAvatarUrl = candidate.video.uploaderAvatarUrl,
            instanceId = instanceId,
            accountId = userId,
            publishedAt = candidate.publishedAt,
            title = candidate.video.title,
        ),
    )

    fun eventId(instanceId: String, candidate: PushCandidate): String = sha256(
        "$instanceId|subscription_new_video|${candidate.serviceId}|${candidate.channelId}|${candidate.videoId}",
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }

    private fun serviceName(serviceId: Int): String = when (serviceId) {
        YOUTUBE_SERVICE_ID -> "YouTube"
        BILIBILI_SERVICE_ID -> "BiliBili"
        NICONICO_SERVICE_ID -> "NicoNico"
        SOUNDCLOUD_SERVICE_ID -> "SoundCloud"
        MEDIA_CCC_SERVICE_ID -> "MediaCCC"
        else -> "Video service"
    }
}
