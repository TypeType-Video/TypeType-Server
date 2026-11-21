package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
data class SubscriptionFeedSnapshot(
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
    val hideLiveStreams: Boolean = false,
    val hideMembersOnlyContent: Boolean = false,
    val filterKey: String = SubscriptionSelection.All.cursorKey,
    val selectionToken: String? = null,
    val oldestFirst: Boolean = false,
)

object SubscriptionFeedCursorCodec {
    fun encode(
        generation: Long,
        offset: Int,
        limit: Int,
        hideLiveStreams: Boolean,
        hideMembersOnlyContent: Boolean,
        filterKey: String,
        selectionToken: String?,
        oldestFirst: Boolean = false,
    ): String {
        val payload = CacheJson.encodeToString(
            SubscriptionFeedCursor.serializer(),
            SubscriptionFeedCursor(
                generation, offset, limit, hideLiveStreams, hideMembersOnlyContent, filterKey, selectionToken,
                oldestFirst,
            ),
        )
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }

    fun decode(value: String): SubscriptionFeedCursorState? = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(value))
        val cursor = CacheJson.decodeFromString(SubscriptionFeedCursor.serializer(), payload)
        cursor.takeIf {
            it.generation > 0L && it.offset >= 0 && it.limit in 1..100 &&
                ((it.filterKey == SubscriptionSelection.All.cursorKey) == (it.selectionToken == null)) &&
                (it.selectionToken == null || SELECTION_TOKEN.matches(it.selectionToken))
        }
            ?.let {
                SubscriptionFeedCursorState(
                    it.generation,
                    it.offset,
                    it.limit,
                    it.hideLiveStreams,
                    it.hideMembersOnlyContent,
                    it.filterKey,
                    it.selectionToken,
                    it.oldestFirst,
                )
            }
    }.getOrNull()

    private val SELECTION_TOKEN = Regex("[0-9a-f]{64}")
}

data class SubscriptionFeedCursorState(
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val hideLiveStreams: Boolean,
    val hideMembersOnlyContent: Boolean,
    val filterKey: String,
    val selectionToken: String?,
    val oldestFirst: Boolean = false,
) {
    internal fun matches(
        limit: Int,
        hideLiveStreams: Boolean,
        hideMembersOnlyContent: Boolean,
        filterKey: String,
        oldestFirst: Boolean,
    ): Boolean = this.limit == limit &&
        this.hideLiveStreams == hideLiveStreams &&
        this.hideMembersOnlyContent == hideMembersOnlyContent &&
        this.filterKey == filterKey &&
        this.oldestFirst == oldestFirst
}

internal fun SubscriptionFeedSnapshot.resolvedVideos(): List<VideoItem> =
    videos.map(::withResolvedUploaderUrl)

fun SubscriptionFeedSnapshot.hasCompleteSourceAttribution(): Boolean =
    videos.all { sourceChannelUrls.containsKey(it.subscriptionFeedKey()) }

fun SubscriptionFeedSnapshot.page(
    offset: Int,
    limit: Int,
    refreshing: Boolean,
    hideLiveStreams: Boolean = false,
    hideMembersOnlyContent: Boolean = false,
    selection: SubscriptionSelection = SubscriptionSelection.All,
    selectedChannelUrls: Set<String>? = null,
    selectionToken: String? = null,
    oldestFirst: Boolean = false,
    focusProfile: FocusProfile? = null,
): SubscriptionFeedResponse {
    val visibleVideos = projectedVideos(selection, selectedChannelUrls).filterNot { video ->
        (hideLiveStreams && video.isLiveContentOrUpcomingAt(generatedAt)) ||
            (hideMembersOnlyContent && video.requiresMembership) ||
            (focusProfile != null && !keepsFocusVideo(video, focusProfile))
    }
    val projectedVideos = if (oldestFirst) visibleVideos.asReversed() else visibleVideos
    val from = offset.coerceAtMost(projectedVideos.size)
    val to = minOf(from + limit, projectedVideos.size)
    val nextpage = if (to < projectedVideos.size) {
        SubscriptionFeedCursorCodec.encode(
            generation,
            to,
            limit,
            hideLiveStreams,
            hideMembersOnlyContent,
            selection.cursorKey,
            selectionToken,
            oldestFirst,
        )
    } else {
        null
    }
    return SubscriptionFeedResponse(
        videos = projectedVideos.subList(from, to).map(::withResolvedUploaderUrl),
        nextpage = nextpage,
        generation = generation,
        generatedAt = generatedAt,
        refreshing = refreshing,
    )
}

internal fun SubscriptionFeedSnapshot.withResolvedUploaderUrl(video: VideoItem): VideoItem {
    if (video.uploaderUrl.isNotBlank()) return video
    val fallback = sourceChannelUrls[video.subscriptionFeedKey()]
        ?.firstOrNull { it.isNotBlank() }
        ?.let(ChannelUrlCanonicalizer::canonicalize)
        ?: return video
    return video.copy(uploaderUrl = fallback)
}

private fun SubscriptionFeedSnapshot.keepsFocusVideo(video: VideoItem, profile: FocusProfile): Boolean {
    if (video.uploaderUrl.isNotBlank()) return profile.keepsFeedVideo(video)
    val sources = sourceChannelUrls[video.subscriptionFeedKey()].orEmpty()
    if (sources.isEmpty()) return profile.keepsFeedVideo(video)
    val publishedAt = video.publishedAt ?: video.uploaded.takeIf { it > 0 }
    return sources.any { source ->
        profile.keepsFeedVideo(video.url, ChannelUrlCanonicalizer.canonicalize(source), publishedAt)
    }
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

sealed interface SubscriptionFeedPageResult {
    data class Ready(val response: SubscriptionFeedResponse) : SubscriptionFeedPageResult
    data class Preparing(val retryAfterMs: Long) : SubscriptionFeedPageResult
    data object InvalidCursor : SubscriptionFeedPageResult
    data object StaleGeneration : SubscriptionFeedPageResult
    data object CursorCapacityReached : SubscriptionFeedPageResult
}
