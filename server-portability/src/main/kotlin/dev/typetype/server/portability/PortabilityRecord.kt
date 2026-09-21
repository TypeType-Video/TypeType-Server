package dev.typetype.server.portability

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed interface PortabilityRecord {
    val category: PortabilityCategory
    fun stableKey(): String
    fun parentKey(): String? = null
}

@Serializable
@SerialName("subscription")
data class PortabilitySubscription(
    val channelUrl: String,
    val name: String = "",
    val avatarUrl: String = "",
    val subscribedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.SUBSCRIPTIONS
    override fun stableKey() = channelUrl.trim().lowercase()
}

@Serializable
@SerialName("subscriptionGroup")
data class PortabilitySubscriptionGroup(
    val name: String,
) : PortabilityRecord {
    override val category = PortabilityCategory.SUBSCRIPTION_GROUPS
    override fun stableKey() = "group:${name.trim().lowercase()}"
}

@Serializable
@SerialName("subscriptionGroupMembership")
data class PortabilitySubscriptionGroupMembership(
    val groupName: String,
    val channelUrl: String,
) : PortabilityRecord {
    override val category = PortabilityCategory.SUBSCRIPTION_GROUPS
    override fun stableKey() = "member:${groupName.trim().lowercase()}:${channelUrl.trim().lowercase()}"
    override fun parentKey() = groupName.trim().lowercase()
}

@Serializable
@SerialName("history")
data class PortabilityHistory(
    val video: PortabilityVideo,
    val watchedAt: Long,
    val positionSeconds: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.HISTORY
    override fun stableKey() = "${video.url.trim().lowercase()}:$watchedAt"
}

@Serializable
@SerialName("playlist")
data class PortabilityPlaylist(
    val sourceId: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.PLAYLISTS
    override fun stableKey() = "playlist:${sourceId.ifBlank { name }.trim().lowercase()}"
}

@Serializable
@SerialName("playlistVideo")
data class PortabilityPlaylistVideo(
    val playlistSourceId: String,
    val position: Int,
    val video: PortabilityVideo,
    val addedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.PLAYLISTS
    override fun stableKey() = "playlist-video:${playlistSourceId.trim().lowercase()}:$position"
    override fun parentKey() = playlistSourceId.trim().lowercase()
}

@Serializable
@SerialName("watchLater")
data class PortabilityWatchLater(
    val video: PortabilityVideo,
    val addedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.WATCH_LATER
    override fun stableKey() = video.url.trim().lowercase()
}

@Serializable
@SerialName("favorite")
data class PortabilityFavorite(
    val video: PortabilityVideo,
    val favoritedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.FAVORITES
    override fun stableKey() = video.url.trim().lowercase()
}

@Serializable
@SerialName("progress")
data class PortabilityProgress(
    val videoUrl: String,
    val positionSeconds: Long,
    val updatedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.PROGRESS
    override fun stableKey() = videoUrl.trim().lowercase()
}

@Serializable
@SerialName("searchHistory")
data class PortabilitySearchHistory(
    val term: String,
    val searchedAt: Long,
) : PortabilityRecord {
    override val category = PortabilityCategory.SEARCH_HISTORY
    override fun stableKey() = "${term.trim().lowercase()}:$searchedAt"
}

@Serializable
@SerialName("savedPlaylist")
data class PortabilitySavedPlaylist(
    val sourceId: String,
    val url: String,
    val title: String = "",
    val thumbnailUrl: String = "",
    val uploaderName: String = "",
    val streamCount: Long = 0L,
    val playlistType: String = "",
    val savedAt: Long = 0L,
) : PortabilityRecord {
    override val category = PortabilityCategory.SAVED_PLAYLISTS
    override fun stableKey() = url.trim().lowercase()
}

@Serializable
@SerialName("settings")
data class PortabilitySettings(
    val values: JsonObject,
) : PortabilityRecord {
    override val category = PortabilityCategory.SETTINGS
    override fun stableKey() = "settings"
}

@Serializable
@SerialName("contentFilter")
data class PortabilityContentFilter(
    val kind: String,
    val value: String,
    val label: String = "",
    val imageUrl: String = "",
    val createdAt: Long = 0L,
    val metadata: JsonObject = JsonObject(emptyMap()),
) : PortabilityRecord {
    override val category = PortabilityCategory.CONTENT_FILTERS
    override fun stableKey() = "${kind.trim().lowercase()}:${value.trim().lowercase()}"
}
