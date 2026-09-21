package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.util.zip.ZipFile

object GrayjayPortabilityReader {
    fun read(zip: ZipFile, sink: PortabilityRecordSink) {
        readStrings(zip, "stores/subscriptions") { readSubscription(it, sink) }
        readStrings(zip, "stores/subscription_groups") { readGroup(it, sink) }
        readStrings(zip, "stores/history") { readHistory(it, sink) }
        readStrings(zip, "stores/playlists") { readPlaylist(it, sink) }
        readStrings(zip, "stores/watch_later") { readWatchLater(it, sink) }
    }

    private fun readStrings(zip: ZipFile, name: String, consume: (String) -> Unit) {
        val entry = zip.getEntry(name) ?: return
        zip.getInputStream(entry).buffered().use { input ->
            PortabilityJsonFactory.createParser(input).use { parser ->
                require(parser.nextToken() == JsonToken.START_ARRAY) { "$name must contain an array" }
                var count = 0
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    require(++count <= PortabilityLimits.MAX_CONTAINER_RECORDS) { "$name contains too many records" }
                    require(parser.currentToken() == JsonToken.VALUE_STRING) { "$name contains an invalid record" }
                    consume(parser.text)
                }
            }
        }
    }

    private fun readSubscription(value: String, sink: PortabilityRecordSink) {
        val url = value.trim()
        if (url.isNotBlank()) sink.write(PortabilitySubscription(url))
    }

    private fun readWatchLater(value: String, sink: PortabilityRecordSink) {
        val url = value.trim()
        if (url.isNotBlank()) sink.write(PortabilityWatchLater(PortabilityVideo(url)))
    }

    private fun readHistory(value: String, sink: PortabilityRecordSink) {
        val parts = value.split("|||", limit = 4)
        if (parts.size != 4) {
            sink.issue(PortabilityIssue(PortabilityCategory.HISTORY, "grayjay_history_invalid", "A Grayjay history record could not be parsed"))
            return
        }
        val url = parts[0].trim()
        val watchedAt = parts[1].toLongOrNull() ?: 0L
        val position = parts[2].toLongOrNull() ?: 0L
        if (url.isBlank()) return
        sink.write(PortabilityHistory(PortabilityVideo(url, title = parts[3]), watchedAt, position))
        sink.write(PortabilityProgress(url, position, watchedAt))
    }

    private fun readPlaylist(value: String, sink: PortabilityRecordSink) {
        val lines = value.lineSequence().filter(String::isNotBlank).toList()
        if (lines.isEmpty()) return
        val header = lines.first()
        val separator = header.indexOf(":::")
        val name = if (separator >= 0) header.substring(0, separator) else header
        val sourceId = if (separator >= 0) header.substring(separator + 3).ifBlank { name } else name
        sink.write(PortabilityPlaylist(sourceId, name))
        lines.drop(1).forEachIndexed { index, url ->
            sink.write(PortabilityPlaylistVideo(sourceId, index, PortabilityVideo(url.trim())))
        }
    }

    private fun readGroup(value: String, sink: PortabilityRecordSink) {
        PortabilityJsonFactory.createParser(value).use { parser -> readGroupObject(parser, sink) }
    }

    private fun readGroupObject(parser: JsonParser, sink: PortabilityRecordSink) {
        parser.requireObject()
        var name = ""
        val urls = ArrayList<String>()
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when (field) {
                "name" -> name = parser.textOrEmpty()
                "urls" -> readUrlArray(parser, token, urls)
                else -> parser.skipChildren()
            }
        }
        if (name.isBlank()) return
        sink.write(PortabilitySubscriptionGroup(name))
        urls.forEach { sink.write(PortabilitySubscriptionGroupMembership(name, it)) }
    }

    private fun readUrlArray(parser: JsonParser, token: JsonToken, urls: MutableList<String>) {
        if (token != JsonToken.START_ARRAY) {
            parser.skipChildren()
            return
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(urls.size < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Grayjay group contains too many channels" }
            if (parser.currentToken() == JsonToken.VALUE_STRING && parser.text.isNotBlank()) urls += parser.text
            else parser.skipChildren()
        }
    }
}
