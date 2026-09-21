package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.json.jsonObject

object FlowPortabilityReader {
    fun subscriptions(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.SUBSCRIPTIONS, parser, token) { item ->
            val id = item.string("channelId")
            if (id.isNotBlank()) {
                sink.write(
                    PortabilitySubscription(
                        youtubeChannelUrl(id),
                        item.string("channelName"),
                        item.string("channelThumbnail"),
                        item.long("subscribedAt"),
                    ),
                )
            }
        }

    fun groups(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.SUBSCRIPTION_GROUPS, parser, token) { item ->
            val name = item.string("name")
            if (name.isBlank()) return@readFlowArray
            sink.write(PortabilitySubscriptionGroup(name))
            var memberships = 0
            item.string("channelIds").splitToSequence(',').map(String::trim).filter(String::isNotBlank).forEach { channel ->
                require(memberships++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Flow group contains too many channels" }
                sink.write(PortabilitySubscriptionGroupMembership(name, youtubeChannelUrl(channel)))
            }
        }

    fun history(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.markCategory(PortabilityCategory.PROGRESS)
        sink.readFlowArray(PortabilityCategory.HISTORY, parser, token) { item ->
            val id = item.string("videoId")
            if (id.isBlank()) return@readFlowArray
            val video = PortabilityVideo(
                youtubeVideoUrl(id),
                item.string("title"),
                item.string("thumbnailUrl"),
                item.long("duration") / 1_000L,
                item.string("channelName"),
                youtubeChannelUrl(item.string("channelId")),
            )
            sink.write(PortabilityHistory(video, item.long("timestamp"), item.long("position") / 1_000L))
            sink.write(PortabilityProgress(video.url, item.long("position") / 1_000L, item.long("timestamp")))
        }
    }

    fun searchHistory(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.SEARCH_HISTORY, parser, token) { item ->
            val query = item.string("query")
            if (query.isNotBlank()) sink.write(PortabilitySearchHistory(query, item.long("timestamp")))
        }

    fun playlists(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.PLAYLISTS, parser, token) { item ->
            val id = item.string("id")
            if (id.isNotBlank()) sink.write(PortabilityPlaylist(id, item.string("name"), item.string("description"), item.long("createdAt")))
        }

    fun playlistVideos(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.PLAYLISTS, parser, token) { item ->
            val playlistId = item.string("playlistId")
            val videoId = item.string("videoId")
            if (playlistId.isNotBlank() && videoId.isNotBlank()) {
                sink.write(
                    PortabilityPlaylistVideo(
                        playlistId,
                        item.long("position").coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                        PortabilityVideo(youtubeVideoUrl(videoId)),
                        item.long("addedAt"),
                    ),
                )
            }
        }

    fun favorites(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) =
        sink.readFlowArray(PortabilityCategory.FAVORITES, parser, token) { item ->
            val id = item.string("videoId")
            if (id.isNotBlank()) {
                sink.write(
                    PortabilityFavorite(
                        PortabilityVideo(youtubeVideoUrl(id), item.string("title"), item.string("thumbnail"), channelName = item.string("channelName")),
                        item.long("likedAt"),
                    ),
                )
            }
        }

    fun contentFilters(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        require(token == JsonToken.START_OBJECT) { "Invalid Flow content preferences" }
        sink.markCategory(PortabilityCategory.CONTENT_FILTERS)
        val item = parser.readJsonElement().jsonObject
        item.array("blockedChannels").forEach { channel ->
            val value = channel.stringValue()
            if (value.isNotBlank()) sink.write(PortabilityContentFilter("blockedChannel", youtubeChannelUrl(value)))
        }
    }
}

private fun PortabilityRecordSink.readFlowArray(
    category: PortabilityCategory,
    parser: JsonParser,
    token: JsonToken,
    block: (kotlinx.serialization.json.JsonObject) -> Unit,
) {
    if (token == JsonToken.VALUE_NULL) return
    require(token == JsonToken.START_ARRAY) { "Invalid Flow backup section" }
    markCategory(category)
    var count = 0
    while (parser.nextToken() != JsonToken.END_ARRAY) {
        require(count++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Backup section contains too many records" }
        block(parser.readJsonElement().jsonObject)
    }
}
