package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

object LibreTubePortabilityReader {
    fun history(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.readArray(PortabilityCategory.HISTORY, parser, token) { element ->
            val item = element.jsonObject
            val id = item.string("videoId")
            if (id.isBlank()) return@readArray
            sink.write(
                PortabilityHistory(
                    PortabilityVideo(
                        youtubeVideoUrl(id),
                        item.string("title"),
                        item.string("thumbnailUrl"),
                        item.long("duration"),
                        item.string("uploader"),
                        item.string("uploaderUrl"),
                        item.string("uploaderAvatar"),
                    ),
                    0L,
                ),
            )
        }
    }

    fun positions(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.readArray(PortabilityCategory.PROGRESS, parser, token) { element ->
            val item = element.jsonObject
            val id = item.string("videoId")
            if (id.isNotBlank()) sink.write(PortabilityProgress(youtubeVideoUrl(id), item.long("position") / 1_000L))
        }
    }

    fun searchHistory(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.readArray(PortabilityCategory.SEARCH_HISTORY, parser, token) { element ->
            val query = element.jsonObject.string("query")
            if (query.isNotBlank()) sink.write(PortabilitySearchHistory(query, 0L))
        }
    }

    fun localPlaylists(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.readArray(PortabilityCategory.PLAYLISTS, parser, token) { element ->
            val item = element.jsonObject
            val playlist = item["playlist"]?.objectOrNull() ?: return@readArray
            val sourceId = "libretube:${playlist.string("id")}:${playlist.string("name").lowercase()}"
            sink.write(PortabilityPlaylist(sourceId, playlist.string("name"), playlist.string("description")))
            item.array("videos").forEachIndexed { position, videoElement ->
                val video = videoElement.jsonObject
                sink.write(
                    PortabilityPlaylistVideo(
                        sourceId,
                        position,
                        PortabilityVideo(
                            youtubeVideoUrl(video.string("videoId")),
                            video.string("title"),
                            video.string("thumbnailUrl"),
                            video.long("duration"),
                            video.string("uploader"),
                            video.string("uploaderUrl"),
                            video.string("uploaderAvatar"),
                        ),
                    ),
                )
            }
        }
    }

    fun bookmarks(parser: JsonParser, sink: PortabilityRecordSink, token: JsonToken) {
        sink.readArray(PortabilityCategory.SAVED_PLAYLISTS, parser, token) { element ->
            val item = element.jsonObject
            val id = item.string("playlistId")
            if (id.isNotBlank()) {
                sink.write(
                    PortabilitySavedPlaylist(
                        id,
                        "https://www.youtube.com/playlist?list=$id",
                        item.string("playlistName"),
                        item.string("thumbnailUrl"),
                        item.string("uploader"),
                        item.long("videos"),
                    ),
                )
            }
        }
    }
}

private fun PortabilityRecordSink.readArray(
    category: PortabilityCategory,
    parser: JsonParser,
    token: JsonToken,
    block: (JsonElement) -> Unit,
) {
    require(token == JsonToken.START_ARRAY) { "Invalid LibreTube backup section" }
    markCategory(category)
    var count = 0
    while (parser.nextToken() != JsonToken.END_ARRAY) {
        require(count++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Backup section contains too many records" }
        block(parser.readJsonElement())
    }
}
