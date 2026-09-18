package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator
import java.io.OutputStream

object PipedPortabilityWriter {
    fun write(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>) {
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            json.writeStringField("format", "Piped")
            json.writeNumberField("version", 1)
            subscriptions(json, source, categories)
            groups(json, source, categories)
            history(json, source, categories)
            playlists(json, source, categories)
            json.writeEndObject()
        }
    }

    private fun subscriptions(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTIONS !in categories) return
        json.writeArrayFieldStart("subscriptions")
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            json.writeString(youtubeId((record as PortabilitySubscription).channelUrl))
        }
        json.writeEndArray()
    }

    private fun groups(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTION_GROUPS !in categories) return
        json.writeArrayFieldStart("groups")
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            json.writeStartObject()
            json.writeStringField("groupName", record.name)
            json.writeArrayFieldStart("channels")
            source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, record.name) { child ->
                json.writeString(youtubeId((child as PortabilitySubscriptionGroupMembership).channelUrl))
            }
            json.writeEndArray()
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun history(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.HISTORY !in categories) return
        json.writeArrayFieldStart("watchHistory")
        source.forEach(PortabilityCategory.HISTORY) { record ->
            val item = record as PortabilityHistory
            json.writeStartObject()
            json.writeStringField("videoId", youtubeId(item.video.url))
            json.writeStringField("title", item.video.title)
            json.writeStringField("uploaderName", item.video.channelName)
            json.writeStringField("uploaderUrl", item.video.channelUrl)
            json.writeStringField("thumbnail", item.video.thumbnailUrl)
            json.writeNumberField("duration", item.video.durationSeconds)
            json.writeNumberField("watchedAt", item.watchedAt)
            json.writeNumberField("currentTime", item.positionSeconds)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun playlists(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.PLAYLISTS !in categories) return
        json.writeArrayFieldStart("playlists")
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            if (record !is PortabilityPlaylist) return@forEach
            json.writeStartObject()
            json.writeStringField("name", record.name)
            json.writeStringField("type", "playlist")
            json.writeStringField("visibility", "private")
            json.writeArrayFieldStart("videos")
            source.forEachChild(PortabilityCategory.PLAYLISTS, record.sourceId) { child ->
                json.writeString((child as PortabilityPlaylistVideo).video.url)
            }
            json.writeEndArray()
            json.writeEndObject()
        }
        json.writeEndArray()
    }
}
