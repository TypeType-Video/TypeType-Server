package dev.typetype.server.services

import dev.typetype.server.models.ChannelPlaylistsResponse
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.StreamResponse

fun SearchPageResponse.filterAllowed(profile: AccessControlProfile): SearchPageResponse = copy(
    items = items.filterAllowed(profile),
    channels = channels.filter { profile.allowsChannel(url = it.url, name = it.name) },
    playlists = playlists.filter { profile.allowsPlaylist(it.url) || profile.allowsChannel(url = "", name = it.uploaderName) },
)

fun StreamResponse.filterAllowed(profile: AccessControlProfile): StreamResponse = copy(
    relatedStreams = relatedStreams.filterAllowed(profile),
)

fun PublicPlaylistResponse.filterAllowed(profile: AccessControlProfile): PublicPlaylistResponse = copy(
    videos = if (profile.allowsPlaylist(playlist.url)) videos else videos.filterAllowed(profile),
)

fun ChannelResponse.filterAllowed(profile: AccessControlProfile): ChannelResponse = copy(
    videos = videos.filterAllowed(profile),
)

fun ChannelPlaylistsResponse.filterAllowed(profile: AccessControlProfile): ChannelPlaylistsResponse = copy(
    playlists = playlists.filter { profile.allowsPlaylist(it.url) || profile.allowsChannel(url = "", name = it.uploaderName) },
)

fun HomeRecommendationsResponse.filterAllowed(profile: AccessControlProfile): HomeRecommendationsResponse = copy(
    items = items.filterAllowed(profile),
)
