package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import kotlinx.serialization.json.JsonObject
import java.io.OutputStream

class InvidiousPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format = PortabilityFormat.INVIDIOUS,
        adapterVersion = 1,
        capabilities = setOf(
            capability(PortabilityCategory.SUBSCRIPTIONS, PortabilityFidelity.COMPLETE),
            capability(PortabilityCategory.HISTORY, PortabilityFidelity.PARTIAL),
            capability(PortabilityCategory.PLAYLISTS, PortabilityFidelity.PARTIAL),
            capability(PortabilityCategory.SETTINGS, PortabilityFidelity.PARTIAL),
        ),
        defaultExtension = "json",
        contentType = "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString()
        val hasHistory = probe.contains("\"watch_history\"")
        val hasPreferences = probe.contains("\"preferences\"")
        val hasSubscriptions = probe.contains("\"subscriptions\"")
        if (!hasSubscriptions || (!hasHistory && !hasPreferences)) return null
        return PortabilityDetection(PortabilityFormat.INVIDIOUS, null, 96, "Invidious account export fields")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) = input.withJsonParser { parser ->
        parser.requireObject()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when {
                field == "subscriptions" && token == JsonToken.START_ARRAY -> {
                    sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
                    readSubscriptions(parser, sink)
                }
                field == "watch_history" && token == JsonToken.START_ARRAY -> {
                    sink.markCategory(PortabilityCategory.HISTORY)
                    readHistory(parser, sink)
                }
                field == "playlists" && token == JsonToken.START_ARRAY -> {
                    sink.markCategory(PortabilityCategory.PLAYLISTS)
                    readPlaylists(parser, sink)
                }
                field == "preferences" && token == JsonToken.START_OBJECT -> {
                    sink.markCategory(PortabilityCategory.SETTINGS)
                    sink.write(PortabilitySettings(JsonObject(mapOf("invidiousPreferences" to parser.readJsonElement()))))
                }
                else -> parser.skipChildren()
            }
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            writeSubscriptions(json, source, categories)
            writeHistory(json, source, categories)
            json.writeObjectFieldStart("preferences")
            json.writeEndObject()
            writePlaylists(json, source, categories)
            json.writeEndObject()
        }
    }

    private fun readSubscriptions(parser: JsonParser, sink: PortabilityRecordSink) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            val channel = youtubeChannelUrl(parser.textOrEmpty())
            if (channel.isNotBlank()) sink.write(PortabilitySubscription(channel))
        }
    }

    private fun readHistory(parser: JsonParser, sink: PortabilityRecordSink) {
        var missingDates = 0L
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            val url = youtubeVideoUrl(parser.textOrEmpty())
            if (url.isNotBlank()) {
                sink.write(PortabilityHistory(PortabilityVideo(url), watchedAt = 0L))
                missingDates += 1
            }
        }
        if (missingDates > 0L) {
            sink.issue(
                PortabilityIssue(
                    PortabilityCategory.HISTORY,
                    "missing_history_dates",
                    "Invidious does not include original watch dates",
                    missingDates,
                ),
            )
        }
    }

    private fun readPlaylists(parser: JsonParser, sink: PortabilityRecordSink) {
        var index = 0
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(parser.currentToken() == JsonToken.START_OBJECT) { "Invalid Invidious playlist" }
            val playlist = readPlaylist(parser, index++)
            sink.write(PortabilityPlaylist(playlist.id, playlist.title, playlist.description))
            playlist.videoIds.forEachIndexed { position, videoId ->
                sink.write(
                    PortabilityPlaylistVideo(
                        playlist.id,
                        position,
                        PortabilityVideo(youtubeVideoUrl(videoId)),
                    ),
                )
            }
        }
    }

    private fun readPlaylist(parser: JsonParser, index: Int): ParsedPlaylist {
        var title = ""
        var description = ""
        val videoIds = mutableListOf<String>()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when {
                field == "title" -> title = parser.textOrEmpty()
                field == "description" -> description = parser.textOrEmpty()
                field == "videos" && token == JsonToken.START_ARRAY -> {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        require(videoIds.size < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Playlist contains too many videos" }
                        videoIds += parser.textOrEmpty()
                    }
                }
                else -> parser.skipChildren()
            }
        }
        val id = "invidious:$index:${title.trim().lowercase()}"
        return ParsedPlaylist(id, title, description, videoIds)
    }

    private fun writeSubscriptions(
        json: com.fasterxml.jackson.core.JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        json.writeArrayFieldStart("subscriptions")
        if (PortabilityCategory.SUBSCRIPTIONS in categories) {
            source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
                json.writeString(youtubeId((record as PortabilitySubscription).channelUrl))
            }
        }
        json.writeEndArray()
    }

    private fun writeHistory(
        json: com.fasterxml.jackson.core.JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        json.writeArrayFieldStart("watch_history")
        if (PortabilityCategory.HISTORY in categories) {
            source.forEach(PortabilityCategory.HISTORY) { record ->
                json.writeString(youtubeId((record as PortabilityHistory).video.url))
            }
        }
        json.writeEndArray()
    }

    private fun writePlaylists(
        json: com.fasterxml.jackson.core.JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        json.writeArrayFieldStart("playlists")
        if (PortabilityCategory.PLAYLISTS in categories) {
            source.forEach(PortabilityCategory.PLAYLISTS) { record ->
                if (record !is PortabilityPlaylist) return@forEach
                json.writeStartObject()
                json.writeStringField("title", record.name)
                json.writeStringField("description", record.description)
                json.writeStringField("privacy", "Private")
                json.writeArrayFieldStart("videos")
                source.forEachChild(PortabilityCategory.PLAYLISTS, record.sourceId) { child ->
                    if (child is PortabilityPlaylistVideo) json.writeString(youtubeId(child.video.url))
                }
                json.writeEndArray()
                json.writeEndObject()
            }
        }
        json.writeEndArray()
    }

    private data class ParsedPlaylist(
        val id: String,
        val title: String,
        val description: String,
        val videoIds: List<String>,
    )
}

private fun capability(category: PortabilityCategory, fidelity: PortabilityFidelity) = PortabilityCapability(
    category,
    setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
    fidelity,
)
