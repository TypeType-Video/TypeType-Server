package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator

object TypeTypePortabilityCoreWriter {
    fun write(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        subscriptions(json, source, categories)
        groups(json, source, categories)
        history(json, source, categories)
        playlists(json, source, categories)
    }

    private fun subscriptions(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.SUBSCRIPTIONS !in categories && PortabilityCategory.SUBSCRIPTION_GROUPS !in categories) return
        json.writeArrayFieldStart("subscriptions")
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            if (record !is PortabilitySubscription) return@forEach
            json.writeStartObject()
            json.writeStringField("channelUrl", record.channelUrl)
            json.writeStringField("name", record.name)
            json.writeStringField("avatarUrl", record.avatarUrl)
            json.writeNumberField("subscribedAt", record.subscribedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun groups(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.SUBSCRIPTION_GROUPS !in categories) return
        json.writeArrayFieldStart("subscriptionGroups")
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            json.writeStartObject()
            json.writeStringField("name", record.name)
            json.writeArrayFieldStart("channelUrls")
            source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, record.name) { child ->
                if (child is PortabilitySubscriptionGroupMembership) json.writeString(child.channelUrl)
            }
            json.writeEndArray()
            json.writeNumberField("createdAt", 0L)
            json.writeNumberField("updatedAt", 0L)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun history(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.HISTORY !in categories) return
        json.writeArrayFieldStart("history")
        source.forEach(PortabilityCategory.HISTORY) { record ->
            if (record !is PortabilityHistory) return@forEach
            json.writeStartObject()
            json.writeStringField("url", record.video.url)
            json.writeVideoFields(record.video)
            json.writeNumberField("progress", record.positionSeconds)
            json.writeNumberField("watchedAt", record.watchedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun playlists(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.PLAYLISTS !in categories) return
        json.writeArrayFieldStart("playlists")
        source.forEach(PortabilityCategory.PLAYLISTS) { record ->
            if (record !is PortabilityPlaylist) return@forEach
            json.writeStartObject()
            json.writeStringField("id", record.sourceId)
            json.writeStringField("name", record.name)
            json.writeStringField("description", record.description)
            json.writeArrayFieldStart("videos")
            var count = 0
            source.forEachChild(PortabilityCategory.PLAYLISTS, record.sourceId) { child ->
                if (child !is PortabilityPlaylistVideo) return@forEachChild
                json.writeStartObject()
                json.writeStringField("url", child.video.url)
                json.writeVideoFields(child.video)
                json.writeNumberField("position", child.position)
                json.writeNumberField("addedAt", child.addedAt)
                json.writeEndObject()
                count += 1
            }
            json.writeEndArray()
            json.writeNumberField("videoCount", count)
            json.writeNumberField("createdAt", record.createdAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }
}

fun JsonGenerator.writeVideoFields(video: PortabilityVideo) {
    writeStringField("title", video.title)
    writeStringField("thumbnail", video.thumbnailUrl)
    writeNumberField("duration", video.durationSeconds)
    writeStringField("channelName", video.channelName)
    writeStringField("channelUrl", video.channelUrl)
    writeStringField("channelAvatar", video.channelAvatarUrl)
    writeNumberField("viewCount", video.viewCount)
    writeNumberField("publishedAt", video.publishedAt)
}
