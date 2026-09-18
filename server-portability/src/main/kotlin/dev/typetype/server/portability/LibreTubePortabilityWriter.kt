package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator
import java.io.OutputStream

object LibreTubePortabilityWriter {
    fun write(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>) {
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            json.writeStringField("format", "Piped")
            json.writeNumberField("version", 1)
            subscriptions(json, source, categories)
            groups(json, source, categories)
            history(json, source, categories)
            positions(json, source, categories)
            searchHistory(json, source, categories)
            localPlaylists(json, source, categories)
            bookmarks(json, source, categories)
            json.writeEndObject()
        }
    }

    private fun subscriptions(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTIONS !in categories) return
        json.writeArrayFieldStart("localSubscriptions")
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            val item = record as PortabilitySubscription
            json.writeStartObject()
            json.writeStringField("channelId", youtubeId(item.channelUrl))
            json.writeStringField("name", item.name)
            json.writeStringField("avatar", item.avatarUrl)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun groups(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTION_GROUPS !in categories) return
        json.writeArrayFieldStart("channelGroups")
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            json.writeStartObject()
            json.writeStringField("groupName", record.name)
            json.writeArrayFieldStart("channels")
            source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, record.name) { child ->
                json.writeString(youtubeId((child as PortabilitySubscriptionGroupMembership).channelUrl))
            }
            json.writeEndArray()
            json.writeNumberField("index", 0)
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
            json.writeStringField("uploader", item.video.channelName)
            json.writeStringField("uploaderUrl", item.video.channelUrl)
            json.writeStringField("uploaderAvatar", item.video.channelAvatarUrl)
            json.writeStringField("thumbnailUrl", item.video.thumbnailUrl)
            json.writeNumberField("duration", item.video.durationSeconds)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun positions(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.PROGRESS !in categories) return
        json.writeArrayFieldStart("watchPositions")
        source.forEach(PortabilityCategory.PROGRESS) { record ->
            val item = record as PortabilityProgress
            json.writeStartObject()
            json.writeStringField("videoId", youtubeId(item.videoUrl))
            json.writeNumberField("position", item.positionSeconds * 1_000L)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun searchHistory(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SEARCH_HISTORY !in categories) return
        json.writeArrayFieldStart("searchHistory")
        source.forEach(PortabilityCategory.SEARCH_HISTORY) { record ->
            json.writeStartObject()
            json.writeStringField("query", (record as PortabilitySearchHistory).term)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun localPlaylists(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.PLAYLISTS !in categories) return
        json.writeArrayFieldStart("localPlaylists")
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            if (record !is PortabilityPlaylist) return@forEach
            json.writeStartObject()
            json.writeObjectFieldStart("playlist")
            json.writeNumberField("id", 0)
            json.writeStringField("name", record.name)
            json.writeStringField("description", record.description)
            json.writeStringField("thumbnailUrl", "")
            json.writeEndObject()
            json.writeArrayFieldStart("videos")
            source.forEachChild(PortabilityCategory.PLAYLISTS, record.sourceId) { child ->
                val video = (child as PortabilityPlaylistVideo).video
                json.writeStartObject()
                json.writeNumberField("id", 0)
                json.writeNumberField("playlistId", 0)
                json.writeStringField("videoId", youtubeId(video.url))
                json.writeStringField("title", video.title)
                json.writeStringField("thumbnailUrl", video.thumbnailUrl)
                json.writeNumberField("duration", video.durationSeconds)
                json.writeStringField("uploader", video.channelName)
                json.writeStringField("uploaderUrl", video.channelUrl)
                json.writeStringField("uploaderAvatar", video.channelAvatarUrl)
                json.writeEndObject()
            }
            json.writeEndArray()
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun bookmarks(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SAVED_PLAYLISTS !in categories) return
        json.writeArrayFieldStart("playlistBookmarks")
        source.forEach(PortabilityCategory.SAVED_PLAYLISTS) { record ->
            val item = record as PortabilitySavedPlaylist
            json.writeStartObject()
            json.writeStringField("playlistId", item.sourceId)
            json.writeStringField("playlistName", item.title)
            json.writeStringField("thumbnailUrl", item.thumbnailUrl)
            json.writeStringField("uploader", item.uploaderName)
            json.writeNumberField("videos", item.streamCount)
            json.writeEndObject()
        }
        json.writeEndArray()
    }
}
