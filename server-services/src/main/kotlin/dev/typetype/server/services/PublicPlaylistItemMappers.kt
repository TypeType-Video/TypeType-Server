package dev.typetype.server.services

import dev.typetype.server.models.PublicPlaylistItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

fun PlaylistInfo.toPublicPlaylistItem(): PublicPlaylistItem = PublicPlaylistItem(
    id = url ?: "",
    title = name ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl.toAbsoluteUrl(),
    uploaderName = uploaderName ?: "",
    streamCount = streamCount,
    playlistType = playlistType?.name?.lowercase() ?: "",
)

fun emptyPublicPlaylistItem(url: String): PublicPlaylistItem = PublicPlaylistItem(
    id = url,
    title = "",
    url = url,
    thumbnailUrl = "",
    uploaderName = "",
    streamCount = -1L,
    playlistType = "",
)
