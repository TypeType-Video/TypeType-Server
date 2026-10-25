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
    playlistType = when {
        url?.contains("business=space_collection") == true -> "collection"
        url?.contains("business=space_series") == true -> "series"
        else -> playlistType?.name?.lowercase() ?: ""
    },
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
