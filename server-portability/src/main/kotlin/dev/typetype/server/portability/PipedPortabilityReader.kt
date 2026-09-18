package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object PipedPortabilityReader {
    fun subscriptions(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken = JsonToken.START_ARRAY) {
        require(token == JsonToken.START_ARRAY) { "Invalid Piped subscriptions" }
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        readArray(parser) { element ->
            val item = element as? JsonObject
            val channel = item?.string("channelId") ?: item?.string("url") ?: element.stringValue()
            if (channel.isNotBlank()) {
                sink.write(
                    PortabilitySubscription(
                        youtubeChannelUrl(channel),
                        item?.string("name") ?: item?.string("channelName").orEmpty(),
                        item?.string("avatar") ?: item?.string("channelThumbnail").orEmpty(),
                        item?.long("subscribedAt") ?: 0L,
                    ),
                )
            }
        }
    }

    fun groups(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        require(token == JsonToken.START_ARRAY) { "Invalid Piped groups" }
        sink.markCategory(PortabilityCategory.SUBSCRIPTION_GROUPS)
        readArray(parser) { element ->
            val item = element.jsonObject
            val name = item.string("groupName").ifBlank { item.string("name") }
            if (name.isBlank()) return@readArray
            sink.write(PortabilitySubscriptionGroup(name))
            item.array("channels").forEach { channel ->
                val value = channel.stringValue()
                if (value.isNotBlank()) sink.write(PortabilitySubscriptionGroupMembership(name, youtubeChannelUrl(value)))
            }
        }
    }

    fun history(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        require(token == JsonToken.START_ARRAY) { "Invalid Piped history" }
        sink.markCategory(PortabilityCategory.HISTORY)
        readArray(parser) { element ->
            val item = element.jsonObject
            val videoId = item.string("videoId")
            if (videoId.isBlank()) return@readArray
            sink.write(
                PortabilityHistory(
                    PortabilityVideo(
                        youtubeVideoUrl(videoId),
                        item.string("title"),
                        item.string("thumbnail"),
                        item.long("duration"),
                        item.string("uploaderName"),
                        item.string("uploaderUrl"),
                    ),
                    item.long("watchedAt"),
                    item.long("currentTime"),
                ),
            )
        }
    }

    fun playlists(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        require(token == JsonToken.START_ARRAY) { "Invalid Piped playlists" }
        sink.markCategory(PortabilityCategory.PLAYLISTS)
        var index = 0
        readArray(parser) { element ->
            val item = element.jsonObject
            val name = item.string("name")
            val sourceId = "piped:${index++}:${name.lowercase()}"
            sink.write(PortabilityPlaylist(sourceId, name))
            item.array("videos").forEachIndexed { position, video ->
                sink.write(PortabilityPlaylistVideo(sourceId, position, PortabilityVideo(youtubeVideoUrl(video.stringValue()))))
            }
        }
    }

    private fun readArray(parser: JsonParser, block: (JsonElement) -> Unit) {
        var count = 0
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(count++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Backup section contains too many records" }
            block(parser.readJsonElement())
        }
    }
}
