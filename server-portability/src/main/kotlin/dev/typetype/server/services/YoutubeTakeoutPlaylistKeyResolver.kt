package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem

object YoutubeTakeoutPlaylistKeyResolver {
    fun resolveAll(
        items: Map<String, List<PlaylistVideoItem>>,
        playlists: List<PlaylistItem>,
    ): Map<String, List<PlaylistVideoItem>> {
        val resolved = mutableMapOf<String, MutableList<PlaylistVideoItem>>()
        items.forEach { (key, videos) ->
            resolved.getOrPut(resolve(key, playlists)) { mutableListOf() }.addAll(videos)
        }
        return resolved
    }

    private fun resolve(key: String, playlists: List<PlaylistItem>): String {
        YoutubeTakeoutSystemPlaylist.canonicalKey(key)?.let { return it }
        val normalizedKey = normalize(key)
        playlists.firstOrNull { normalize(it.id) == normalizedKey }?.let { return it.id }
        playlists.firstOrNull { normalize(it.name) == normalizedKey }?.let { return it.name }
        val suffixMatch = playlists
            .filter { it.name.isNotBlank() }
            .sortedByDescending { normalize(it.name).length }
            .firstOrNull { playlist ->
                val name = normalize(playlist.name)
                normalizedKey.endsWith(" $name") ||
                    normalizedKey.startsWith("$name ") ||
                    (name.length > 2 && normalizedKey.endsWith(name))
            }
        return suffixMatch?.name ?: key
    }

    private fun normalize(value: String): String = YoutubeTakeoutTextNormalizer.normalize(value)
}
