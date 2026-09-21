package dev.typetype.server.services

import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.AllowedPlaylistItem

data class AccessControlProfile(
    val enabled: Boolean,
    val channels: List<AllowedChannelItem> = emptyList(),
    val playlists: List<AllowedPlaylistItem> = emptyList(),
) {
    fun allowsChannel(url: String, name: String? = null): Boolean {
        if (!enabled) return true
        val normalizedUrl = normalizeChannelKey(url)
        val normalizedName = name?.trim().orEmpty()
        return channels.any { item ->
            normalizeChannelKey(item.url) == normalizedUrl || namesMatch(item.name, normalizedName)
        }
    }

    fun allowsUploader(url: String, name: String): Boolean {
        if (!enabled) return true
        if (url.isBlank() && name.isBlank()) return false
        return allowsChannel(url, name)
    }

    fun allowsPlaylist(url: String): Boolean {
        if (!enabled) return true
        val normalizedUrl = normalizePlaylistKey(url)
        return playlists.any { normalizePlaylistKey(it.url) == normalizedUrl }
    }

    companion object {
        val unrestricted = AccessControlProfile(enabled = false)
    }
}

private fun namesMatch(allowed: String?, actual: String): Boolean {
    val normalizedAllowed = allowed?.trim().orEmpty()
    return normalizedAllowed.isNotBlank() && actual.isNotBlank() && normalizedAllowed.equals(actual, ignoreCase = true)
}
