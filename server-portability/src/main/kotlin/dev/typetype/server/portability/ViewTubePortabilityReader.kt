package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.time.Instant

object ViewTubePortabilityReader {
    fun read(parser: JsonParser, sink: PortabilityRecordSink) {
        parser.requireObject()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when (field) {
                "subscriptions" -> readContainer(parser, token, "channels") { readSubscription(parser, sink) }
                "history" -> readContainer(parser, token, "videos") { readHistory(parser, sink) }
                "settings" -> {
                    parser.skipChildren()
                    sink.issue(PortabilityIssue(PortabilityCategory.SETTINGS, "viewtube_settings_ignored", "ViewTube settings are not portable to TypeType"))
                }
                else -> parser.skipChildren()
            }
        }
    }

    private fun readContainer(parser: JsonParser, token: JsonToken, arrayField: String, item: () -> Unit) {
        if (token != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val value = parser.nextToken()
            if (field == arrayField && value == JsonToken.START_ARRAY) {
                var count = 0
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    require(++count <= PortabilityLimits.MAX_CONTAINER_RECORDS) { "$arrayField contains too many records" }
                    item()
                }
            } else {
                parser.skipChildren()
            }
        }
    }

    private fun readSubscription(parser: JsonParser, sink: PortabilityRecordSink) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return
        }
        var id = ""
        var name = ""
        var url = ""
        var avatar = ""
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            parser.nextToken()
            when (field) {
                "authorId" -> id = parser.textOrEmpty()
                "author" -> name = parser.textOrEmpty()
                "authorUrl" -> url = parser.textOrEmpty()
                "authorThumbnailUrl" -> avatar = parser.textOrEmpty()
                "authorThumbnails" -> avatar = avatar.ifBlank { readFirstUrl(parser) }
                else -> parser.skipChildren()
            }
        }
        val channelUrl = url.ifBlank { youtubeChannelUrl(id) }
        if (channelUrl.isNotBlank()) sink.write(PortabilitySubscription(channelUrl, name, avatar))
    }

    private fun readHistory(parser: JsonParser, sink: PortabilityRecordSink) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return
        }
        val fields = ViewTubeHistoryFields()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            parser.nextToken()
            when (field) {
                "videoId" -> fields.videoId = parser.textOrEmpty()
                "progressSeconds" -> fields.progress = parser.longOrZero()
                "lengthSeconds" -> fields.duration = parser.longOrZero()
                "lastVisit" -> fields.watchedAt = parseInstant(parser.textOrEmpty())
                "videoDetails" -> readVideoDetails(parser, fields)
                else -> parser.skipChildren()
            }
        }
        val url = youtubeVideoUrl(fields.videoId)
        if (url.isBlank()) return
        val video = PortabilityVideo(
            url = url,
            title = fields.title,
            thumbnailUrl = fields.thumbnail,
            durationSeconds = fields.duration,
            channelName = fields.channelName,
            channelUrl = youtubeChannelUrl(fields.channelId),
            channelAvatarUrl = fields.channelAvatar,
            viewCount = fields.views,
            publishedAt = fields.publishedAt,
        )
        sink.write(PortabilityHistory(video, fields.watchedAt, fields.progress))
        sink.write(PortabilityProgress(url, fields.progress, fields.watchedAt))
    }

    private fun readVideoDetails(parser: JsonParser, fields: ViewTubeHistoryFields) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.skipChildren()
            return
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            parser.nextToken()
            when (field) {
                "videoId" -> fields.videoId = parser.textOrEmpty()
                "title" -> fields.title = parser.textOrEmpty()
                "author" -> fields.channelName = parser.textOrEmpty()
                "authorId" -> fields.channelId = parser.textOrEmpty()
                "authorThumbnailUrl" -> fields.channelAvatar = parser.textOrEmpty()
                "lengthSeconds" -> fields.duration = parser.longOrZero()
                "viewCount" -> fields.views = parser.longOrZero()
                "published" -> fields.publishedAt = parser.longOrZero()
                "videoThumbnails" -> fields.thumbnail = readFirstUrl(parser)
                else -> parser.skipChildren()
            }
        }
    }

    private fun readFirstUrl(parser: JsonParser): String {
        if (parser.currentToken() != JsonToken.START_ARRAY) return parser.skipChildren().let { "" }
        var url = ""
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren()
                continue
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val field = parser.currentName
                parser.nextToken()
                if (field == "url" && url.isBlank()) url = parser.textOrEmpty() else parser.skipChildren()
            }
        }
        return url
    }

    private fun parseInstant(value: String): Long = runCatching { Instant.parse(value).epochSecond }.getOrDefault(0L)
}

private data class ViewTubeHistoryFields(
    var videoId: String = "",
    var progress: Long = 0,
    var duration: Long = 0,
    var watchedAt: Long = 0,
    var title: String = "",
    var thumbnail: String = "",
    var channelName: String = "",
    var channelId: String = "",
    var channelAvatar: String = "",
    var views: Long = 0,
    var publishedAt: Long = -1,
)
