package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserAllowListItem(
    val user: AdminAllowListUserItem,
    val globalChannels: List<AllowedChannelItem>,
    val userChannels: List<AllowedChannelItem>,
    val globalPlaylists: List<AllowedPlaylistItem>,
    val userPlaylists: List<AllowedPlaylistItem>,
)
