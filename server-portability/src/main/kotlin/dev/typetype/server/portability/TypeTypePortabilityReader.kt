package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.AllowedChannelItem
import dev.typetype.server.models.AllowedPlaylistItem
import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem
import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.ProgressItem
import dev.typetype.server.models.SavedPlaylistItem
import dev.typetype.server.models.SearchHistoryItem
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.models.SubscriptionGroupBackupItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.TypeTypeContentFiltersBackup
import dev.typetype.server.models.WatchLaterItem
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal object TypeTypePortabilityReader {
    fun subscriptions(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<SubscriptionItem>(token) { sink.write(PortabilitySubscription(it.channelUrl, it.name, it.avatarUrl, it.subscribedAt)) }

    fun groups(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<SubscriptionGroupBackupItem>(token) { group ->
            sink.write(PortabilitySubscriptionGroup(group.name))
            group.channelUrls.forEach { sink.write(PortabilitySubscriptionGroupMembership(group.name, it)) }
        }

    fun history(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<HistoryItem>(token) { item ->
            sink.write(
                PortabilityHistory(
                    PortabilityVideo(
                        item.url,
                        item.title,
                        item.thumbnail,
                        item.duration,
                        item.channelName,
                        item.channelUrl,
                        item.channelAvatar,
                    ),
                    item.watchedAt,
                    item.progress,
                ),
            )
        }

    fun playlists(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<PlaylistItem>(token) { playlist ->
            val sourceId = playlist.id.ifBlank { "${playlist.name}:${playlist.createdAt}" }
            sink.write(PortabilityPlaylist(sourceId, playlist.name, playlist.description, playlist.createdAt))
            playlist.videos.forEach { video ->
                sink.write(
                    PortabilityPlaylistVideo(
                        sourceId,
                        video.position,
                        PortabilityVideo(
                            video.url,
                            video.title,
                            video.thumbnail,
                            video.duration,
                            video.channelName,
                            video.channelUrl,
                            video.channelAvatar,
                            video.viewCount,
                            video.publishedAt,
                        ),
                        video.addedAt,
                    ),
                )
            }
        }

    fun watchLater(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<WatchLaterItem>(token) { item ->
            sink.write(
                PortabilityWatchLater(
                    PortabilityVideo(item.url, item.title, item.thumbnail, item.duration, item.channelName, item.channelUrl, item.channelAvatar, item.viewCount, item.publishedAt),
                    item.addedAt,
                ),
            )
        }

    fun favorites(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<FavoriteItem>(token) { item ->
            sink.write(
                PortabilityFavorite(
                    PortabilityVideo(item.videoUrl, item.title, item.thumbnail, item.duration, item.channelName, item.channelUrl, item.channelAvatar, item.viewCount, item.publishedAt),
                    item.favoritedAt,
                ),
            )
        }

    fun progress(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<ProgressItem>(token) { sink.write(PortabilityProgress(it.videoUrl, it.position, it.updatedAt)) }

    fun searchHistory(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<SearchHistoryItem>(token) { sink.write(PortabilitySearchHistory(it.term, it.searchedAt)) }

    fun savedPlaylists(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) =
        parser.readArray<SavedPlaylistItem>(token) {
            sink.write(PortabilitySavedPlaylist(it.publicPlaylistId, it.url, it.title, it.thumbnailUrl, it.uploaderName, it.streamCount, it.playlistType, it.savedAt))
        }

    fun settings(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) {
        if (token == JsonToken.VALUE_NULL) return
        val item = parser.decodeCurrent<SettingsItem>()
        sink.write(PortabilitySettings(CacheJson.parseToJsonElement(CacheJson.encodeToString(SettingsItem.serializer(), item)).jsonObject))
    }

    fun contentFilters(parser: JsonParser, token: JsonToken, sink: PortabilityRecordSink) {
        if (token == JsonToken.VALUE_NULL) return
        val filters = parser.decodeCurrent<TypeTypeContentFiltersBackup>()
        filters.blockedChannels.forEach { sink.write(it.toFilter("blockedChannel")) }
        filters.blockedVideos.forEach { sink.write(it.toFilter("blockedVideo")) }
        filters.blockedKeywords.forEach { sink.write(PortabilityContentFilter("blockedKeyword", it.keyword, createdAt = it.blockedAt)) }
        filters.allowedChannels.forEach { sink.write(it.toFilter()) }
        filters.allowedPlaylists.forEach { sink.write(it.toFilter()) }
    }
}

private inline fun <reified T> JsonParser.readArray(token: JsonToken, block: (T) -> Unit) {
    if (token == JsonToken.VALUE_NULL) return
    require(token == JsonToken.START_ARRAY) { "Invalid TypeType backup section" }
    while (nextToken() != JsonToken.END_ARRAY) block(decodeCurrent())
}

private inline fun <reified T> JsonParser.decodeCurrent(): T =
    CacheJson.decodeFromString(readJsonElement().toString())

private fun BlockedItem.toFilter(kind: String) = PortabilityContentFilter(
    kind,
    url,
    name.orEmpty(),
    thumbnailUrl.orEmpty(),
    blockedAt,
)

private fun AllowedChannelItem.toFilter() = PortabilityContentFilter(
    "allowedChannel",
    url,
    name.orEmpty(),
    thumbnailUrl.orEmpty(),
    allowedAt,
)

private fun AllowedPlaylistItem.toFilter() = PortabilityContentFilter(
    "allowedPlaylist",
    url,
    title.orEmpty(),
    thumbnailUrl.orEmpty(),
    allowedAt,
    kotlinx.serialization.json.buildJsonObject { put("uploaderName", uploaderName.orEmpty()) },
)
