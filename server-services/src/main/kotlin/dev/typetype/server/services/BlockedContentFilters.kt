package dev.typetype.server.services

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.SubscriptionFeedResponse

fun HomeRecommendationsResponse.filterBlocked(profile: BlockedContentProfile): HomeRecommendationsResponse = copy(
    items = items.filter {
        profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName)
    },
)

fun SearchPageResponse.filterBlocked(profile: BlockedContentProfile): SearchPageResponse = copy(
    items = items.filter { profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName) },
    channels = channels.filter { profile.allowsChannel(url = it.url, name = it.name) },
    playlists = playlists.filter { profile.allowsChannel(url = "", name = it.uploaderName) },
)

fun StreamResponse.filterBlocked(profile: BlockedContentProfile): StreamResponse = copy(
    relatedStreams = relatedStreams.filter {
        profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName)
    },
)

fun PublicPlaylistResponse.filterBlocked(profile: BlockedContentProfile): PublicPlaylistResponse = copy(
    videos = videos.filter {
        profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName)
    },
)

fun ChannelResponse.filterBlocked(profile: BlockedContentProfile): ChannelResponse = copy(
    videos = videos.filter {
        profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName)
    },
)

fun ChannelPlaylistsResponse.filterBlocked(profile: BlockedContentProfile): ChannelPlaylistsResponse = copy(
    playlists = playlists.filter { profile.allowsChannel(url = "", name = it.uploaderName) },
)

fun SubscriptionFeedResponse.filterBlocked(profile: BlockedContentProfile): SubscriptionFeedResponse = copy(
    videos = videos.filter { profile.allowsVideo(it.url, it.title, it.uploaderUrl, it.uploaderName) },
)
