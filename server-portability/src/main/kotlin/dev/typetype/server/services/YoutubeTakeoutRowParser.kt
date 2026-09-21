package dev.typetype.server.services

import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem

object YoutubeTakeoutRowParser {
    fun parseSubscription(header: List<String>, row: List<String>): SubscriptionItem? {
        val values = header.zip(row).toMap()
        val channelId = values.pickHeader(YoutubeTakeoutSchemaHints::isChannelIdHeader)
            ?: values.pickValue(YoutubeTakeoutSchemaHints::looksLikeChannelId)
        val channelUrl = values.pickHeader(YoutubeTakeoutSchemaHints::isChannelUrlHeader)
            ?: values.pickValue(YoutubeTakeoutSchemaHints::containsChannelUrl)
            ?: channelId?.let { "https://www.youtube.com/channel/$it" }
        val name = values.pickHeader(YoutubeTakeoutSchemaHints::isChannelTitleHeader)
            ?: values.firstTextValue()
            ?: return null
        if (channelUrl.isNullOrBlank()) return null
        return SubscriptionItem(channelUrl = channelUrl, name = name, avatarUrl = values.pickExact("thumbnail", "avatar") ?: "")
    }

    fun parsePlaylist(header: List<String>, row: List<String>): PlaylistItem? {
        val values = header.zip(row).toMap()
        val title = values.pickHeader(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
            ?: values.firstTextValue()
            ?: return null
        val id = values.pickHeader(YoutubeTakeoutSchemaHints::isPlaylistIdHeader)
            ?: values.pickValue(YoutubeTakeoutSchemaHints::looksLikePlaylistId)
            ?: ""
        val description = values.pickExact("description", "description de la playlist") ?: ""
        return PlaylistItem(id = id, name = title, description = description)
    }

    fun parsePlaylistItem(header: List<String>, row: List<String>): Pair<String, PlaylistVideoItem>? {
        val values = header.zip(row).toMap()
        if (isUnavailablePlaylistItem(values)) return null
        val playlistKey = values.pickExact("playlist source key")
            ?: values.pickHeader(YoutubeTakeoutSchemaHints::isPlaylistIdHeader)
            ?: values.pickHeader(YoutubeTakeoutSchemaHints::isPlaylistTitleHeader)
            ?: values.pickExact("playlist")
            ?: return null
        val videoId = values.pickHeader(YoutubeTakeoutSchemaHints::isVideoIdHeader)
            ?: values.pickValue(YoutubeTakeoutSchemaHints::looksLikeVideoId)
        val videoUrl = values.pickHeader(YoutubeTakeoutSchemaHints::isUrlHeader)
            ?: values.pickValue(YoutubeTakeoutSchemaHints::containsWatchUrl)
            ?: videoId?.let { "https://www.youtube.com/watch?v=$it" }
        if (videoUrl.isNullOrBlank()) return null
        val title = values.pickHeader(YoutubeTakeoutSchemaHints::isVideoTitleHeader) ?: ""
        val thumbnail = values.pickExact("thumbnail") ?: ""
        val duration = values.pickExact("duration")?.toLongOrNull() ?: 0L
        val position = values.pickExact("position")?.toIntOrNull() ?: 0
        val addedAt = values.pickHeader(YoutubeTakeoutSchemaHints::isPlaylistItemAddedAtHeader)
            ?.let(YoutubeTakeoutDateParser::parseEpochMillis)
            ?: values.pickValue { YoutubeTakeoutDateParser.parseEpochMillis(it) != null }
                ?.let(YoutubeTakeoutDateParser::parseEpochMillis)
            ?: 0L
        return playlistKey to PlaylistVideoItem(
            url = videoUrl,
            title = title,
            thumbnail = thumbnail,
            duration = duration,
            position = position,
            addedAt = addedAt,
        )
    }

    fun isUnavailablePlaylistItem(header: List<String>, row: List<String>): Boolean =
        isUnavailablePlaylistItem(header.zip(row).toMap())

    private fun isUnavailablePlaylistItem(values: Map<String, String>): Boolean =
        values.pickHeader(YoutubeTakeoutSchemaHints::isVideoTitleHeader)
            ?.let(YoutubeTakeoutUnavailableItem::matches)
            ?: false

    private fun Map<String, String>.pickExact(vararg keys: String): String? {
        val normalized = entries.associate { YoutubeTakeoutSchemaHints.normalize(it.key) to it.value.trim() }
        return keys.asSequence().mapNotNull { normalized[it] }.firstOrNull { it.isNotBlank() }
    }

    private fun Map<String, String>.pickHeader(predicate: (String) -> Boolean): String? =
        entries.firstOrNull { predicate(it.key) && it.value.isNotBlank() }?.value?.trim()

    private fun Map<String, String>.pickValue(predicate: (String) -> Boolean): String? =
        values.firstOrNull { predicate(it) && it.isNotBlank() }?.trim()

    private fun Map<String, String>.firstTextValue(): String? = values.firstOrNull {
        val value = it.trim()
        value.isNotBlank() &&
            !YoutubeTakeoutSchemaHints.looksLikeChannelId(value) &&
            !YoutubeTakeoutSchemaHints.looksLikePlaylistId(value) &&
            !YoutubeTakeoutSchemaHints.looksLikeLikelyVideoId(value) &&
            !YoutubeTakeoutSchemaHints.containsChannelUrl(value) &&
            !YoutubeTakeoutSchemaHints.containsWatchUrl(value) &&
            !isMetadataValue(value)
    }?.trim()

    private fun isMetadataValue(value: String): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(value)
        return normalized in METADATA_VALUES || Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(value)
    }

    private val METADATA_VALUES = setOf(
        "true",
        "false",
        "public",
        "private",
        "prive",
        "publique",
        "unlisted",
        "non repertorie",
        "yes",
        "no",
        "oui",
        "non",
    )
}
