package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

class SubscriptionFeedOrderer {
    fun order(
        videos: List<VideoItem>,
        previous: SubscriptionFeedSnapshot?,
        refreshedAt: Long,
    ): SubscriptionFeedOrdering {
        val previousByKey = previous?.videos.orEmpty().associateBy(VideoItem::subscriptionFeedKey)
        val promotedAt = buildMap {
            videos.filter { it.isLiveOrUpcomingAt(refreshedAt) }.forEach { video ->
                val key = video.subscriptionFeedKey()
                val previousVideo = previousByKey[key]
                val promotion = when {
                    previous == null || previousVideo == null -> refreshedAt
                    samePromotionPhase(video, previousVideo, previous) ->
                        previous.livePromotedAt[key] ?: previous.generatedAt
                    else -> refreshedAt
                }
                put(key, promotion)
            }
        }
        val ordered = videos.sortedWith(
            compareByDescending<VideoItem> { video ->
                promotedAt[video.subscriptionFeedKey()] ?: video.feedTimestamp()
            }.thenBy(VideoItem::subscriptionFeedKey),
        )
        return SubscriptionFeedOrdering(ordered, promotedAt)
    }

    private fun samePromotionPhase(
        video: VideoItem,
        previousVideo: VideoItem,
        previous: SubscriptionFeedSnapshot,
    ): Boolean = when {
        video.isLive -> previousVideo.isLive
        else -> previousVideo.isUpcomingAt(previous.generatedAt)
    }

    private fun VideoItem.feedTimestamp(): Long = when {
        uploaded >= 0L -> uploaded
        else -> publishedAt?.takeIf { it >= 0L } ?: Long.MIN_VALUE
    }
}

data class SubscriptionFeedOrdering(
    val videos: List<VideoItem>,
    val livePromotedAt: Map<String, Long>,
)

fun VideoItem.subscriptionFeedKey(): String =
    url.ifBlank { id.ifBlank { "$uploaderUrl|$title" } }
