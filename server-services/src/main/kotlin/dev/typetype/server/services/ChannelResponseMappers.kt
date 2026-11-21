package dev.typetype.server.services

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.VideoItem
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException

fun ChannelInfo.toChannelResponse(fallbackUploaderUrl: String = ""): ChannelResponse {
    requireItems(relatedItems.isEmpty(), errors, "Channel")
    return ChannelResponse(
        name = name ?: "",
        description = description ?: "",
        avatarUrl = avatarUrl ?: "",
        bannerUrl = bannerUrl ?: "",
        subscriberCount = subscriberCount,
        isVerified = isVerified,
        verification = verification.toApiValue(),
        videos = relatedItems.map {
            it.toVideoItem(fallbackAvatarUrl = avatarUrl ?: "", fallbackUploaderUrl = fallbackUploaderUrl)
        },
        nextpage = nextPage?.toCursor(),
        hasCourses = hasCoursesTab(),
    )
}

fun ChannelInfo.hasCoursesTab(): Boolean =
    tabs.any { tab -> tab.contentFilters.any { it.name == ChannelTabs.COURSES } }

fun InfoItemsPage<StreamInfoItem>.toChannelResponse(fallbackUploaderUrl: String = ""): ChannelResponse {
    requireItems(items.isEmpty(), errors, "Channel")
    return channelPage(items.map { it.toVideoItem(fallbackUploaderUrl = fallbackUploaderUrl) }, nextPage?.toCursor())
}

fun ChannelTabInfo.toChannelTabResponse(
    metadata: ChannelInfo? = null,
    fallbackUploaderUrl: String = "",
): ChannelResponse {
    val videoItems = relatedItems.filterIsInstance<StreamInfoItem>()
    requireItems(videoItems.isEmpty(), errors, "Channel")
    return ChannelResponse(
        name = metadata?.name ?: name ?: "",
        description = metadata?.description ?: "",
        avatarUrl = metadata?.avatarUrl ?: "",
        bannerUrl = metadata?.bannerUrl ?: "",
        subscriberCount = metadata?.subscriberCount ?: -1L,
        isVerified = metadata?.isVerified ?: false,
        verification = metadata?.verification.toApiValue(),
        videos = videoItems.map {
            it.toVideoItem(fallbackAvatarUrl = metadata?.avatarUrl ?: "", fallbackUploaderUrl = fallbackUploaderUrl)
        },
        nextpage = nextPage?.toCursor(),
        hasCourses = metadata?.hasCoursesTab() ?: false,
    )
}

fun InfoItemsPage<InfoItem>.toChannelTabResponse(fallbackUploaderUrl: String = ""): ChannelResponse {
    val videoItems = items.filterIsInstance<StreamInfoItem>()
    requireItems(videoItems.isEmpty(), errors, "Channel")
    return channelPage(videoItems.map { it.toVideoItem(fallbackUploaderUrl = fallbackUploaderUrl) }, nextPage?.toCursor())
}

/**
 * Backfills blank video uploader URLs on channel pages/caches (e.g. Bilibili search/client fallbacks
 * whose archive items omit per-item mid) with the canonicalized channel URL.
 */
fun ChannelResponse.withFallbackUploaderUrl(channelUrl: String): ChannelResponse {
    val base = channelUrl.toChannelTab(null)?.let { channelUrl.toBaseChannelUrl(it) } ?: channelUrl
    val canonical = ChannelUrlCanonicalizer.canonicalize(base)
    if (canonical.isBlank() || videos.all { it.uploaderUrl.isNotBlank() }) return this
    return copy(videos = videos.map { if (it.uploaderUrl.isNotBlank()) it else it.copy(uploaderUrl = canonical) })
}

fun ChannelTabInfo.toChannelPlaylistsResponse(): ChannelPlaylistsResponse {
    val playlists = relatedItems.filterIsInstance<PlaylistInfoItem>()
    requireItems(playlists.isEmpty(), errors, "Channel playlists")
    return ChannelPlaylistsResponse(playlists.map { it.toPlaylistResultItem() }, nextPage?.toCursor())
}

fun InfoItemsPage<InfoItem>.toChannelPlaylistsResponse(): ChannelPlaylistsResponse {
    val playlists = items.filterIsInstance<PlaylistInfoItem>()
    requireItems(playlists.isEmpty(), errors, "Channel playlists")
    return ChannelPlaylistsResponse(playlists.map { it.toPlaylistResultItem() }, nextPage?.toCursor())
}

private fun requireItems(empty: Boolean, errors: List<Throwable>, source: String) {
    if (empty && errors.isNotEmpty()) throw ExtractionException("$source extraction returned no items", errors.first())
}

private fun channelPage(videos: List<VideoItem>, nextpage: String?): ChannelResponse = ChannelResponse(
    name = "",
    description = "",
    avatarUrl = "",
    bannerUrl = "",
    subscriberCount = -1L,
    isVerified = false,
    videos = videos,
    nextpage = nextpage,
)
