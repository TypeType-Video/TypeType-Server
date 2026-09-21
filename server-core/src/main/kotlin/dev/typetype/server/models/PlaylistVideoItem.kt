package dev.typetype.server.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PlaylistVideoItem(
    val id: String = "",
    val url: String,
    val title: String,
    @JsonNames("thumbnailUrl")
    val thumbnail: String,
    val duration: Long,
    val position: Int = 0,
    @JsonNames("uploaderName")
    val channelName: String = "",
    @JsonNames("uploaderUrl")
    val channelUrl: String = "",
    @JsonNames("channelAvatarUrl", "uploaderAvatarUrl")
    val channelAvatar: String = "",
    val viewCount: Long = 0L,
    val addedAt: Long = 0L,
    val publishedAt: Long = -1L,
    val watchPosition: Long = 0L,
    val watched: Boolean = false,
    val progressUpdatedAt: Long = 0L,
)
