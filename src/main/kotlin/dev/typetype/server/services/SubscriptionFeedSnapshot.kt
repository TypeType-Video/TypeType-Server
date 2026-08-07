package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
internal data class SubscriptionFeedSnapshot(
    val generation: Long,
    val generatedAt: Long,
    val stale: Boolean,
    val videos: List<VideoItem>,
    val livePromotedAt: Map<String, Long> = emptyMap(),
    val sourceChannelUrls: Map<String, List<String>> = emptyMap(),
)

@Serializable
private data class SubscriptionFeedCursor(
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val filterKey: String = SubscriptionSelection.All.cursorKey,
)

internal object SubscriptionFeedCursorCodec {
    fun encode(generation: Long, offset: Int, limit: Int, filterKey: String): String {
        val payload = CacheJson.encodeToString(
            SubscriptionFeedCursor.serializer(),
            SubscriptionFeedCursor(generation, offset, limit, filterKey),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }

    fun decode(value: String): SubscriptionFeedCursorState? = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(value))
        val cursor = CacheJson.decodeFromString(SubscriptionFeedCursor.serializer(), payload)
        cursor.takeIf { it.generation > 0L && it.offset >= 0 && it.limit in 1..100 }
            ?.let { SubscriptionFeedCursorState(it.generation, it.offset, it.limit, it.filterKey) }
    }.getOrNull()
}

internal data class SubscriptionFeedCursorState(
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val filterKey: String,
)

internal fun SubscriptionFeedSnapshot.page(
    offset: Int,
    limit: Int,
    refreshing: Boolean,
    selection: SubscriptionSelection = SubscriptionSelection.All,
    selectedChannelUrls: Set<String>? = null,
): SubscriptionFeedResponse {
    val projectedVideos = projectedVideos(selection, selectedChannelUrls)
    val from = offset.coerceAtMost(projectedVideos.size)
    val to = minOf(from + limit, projectedVideos.size)
    val nextpage = if (to < projectedVideos.size) {
        SubscriptionFeedCursorCodec.encode(generation, to, limit, selection.cursorKey)
    } else {
        null
    }
    return SubscriptionFeedResponse(
        videos = projectedVideos.subList(from, to),
        nextpage = nextpage,
        generation = generation,
        generatedAt = generatedAt,
        refreshing = refreshing,
    )
}

private fun SubscriptionFeedSnapshot.projectedVideos(
    selection: SubscriptionSelection,
    selectedChannelUrls: Set<String>?,
): List<VideoItem> {
    if (selection == SubscriptionSelection.All) return videos
    val allowed = selectedChannelUrls.orEmpty()
    if (allowed.isEmpty()) return emptyList()
    return videos.filter { video ->
        val sources = sourceChannelUrls[video.subscriptionFeedKey()]
        if (sources != null) {
            sources.any { ChannelUrlCanonicalizer.canonicalize(it) in allowed }
        } else {
            ChannelUrlCanonicalizer.canonicalize(video.uploaderUrl) in allowed
        }
    }
}

internal sealed interface SubscriptionFeedPageResult {
    data class Ready(val response: SubscriptionFeedResponse) : SubscriptionFeedPageResult
    data class Preparing(val retryAfterMs: Long) : SubscriptionFeedPageResult
    data object InvalidCursor : SubscriptionFeedPageResult
    data object StaleGeneration : SubscriptionFeedPageResult
}
