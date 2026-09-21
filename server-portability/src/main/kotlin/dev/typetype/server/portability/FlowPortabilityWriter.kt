package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator
import java.io.OutputStream

object FlowPortabilityWriter {
    fun write(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>) {
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            json.writeNumberField("version", 2)
            json.writeNumberField("timestamp", System.currentTimeMillis())
            subscriptions(json, source, categories)
            groups(json, source, categories)
            history(json, source, categories)
            searchHistory(json, source, categories)
            favorites(json, source, categories)
            filters(json, source, categories)
            json.writeEndObject()
        }
    }

    private fun subscriptions(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTIONS !in categories) return
        json.writeArrayFieldStart("subscriptions")
        source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
            val item = record as PortabilitySubscription
            json.writeStartObject()
            json.writeStringField("channelId", youtubeId(item.channelUrl))
            json.writeStringField("channelName", item.name)
            json.writeStringField("channelThumbnail", item.avatarUrl)
            json.writeNumberField("subscribedAt", item.subscribedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun groups(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SUBSCRIPTION_GROUPS !in categories) return
        json.writeArrayFieldStart("subscriptionGroups")
        source.forEach(PortabilityCategory.SUBSCRIPTION_GROUPS) { record ->
            if (record !is PortabilitySubscriptionGroup) return@forEach
            val channels = boundedChannelIds(source, record.name)
            json.writeStartObject()
            json.writeStringField("name", record.name)
            json.writeStringField("channelIds", channels)
            json.writeNumberField("sortOrder", 0)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun boundedChannelIds(source: PortabilityRecordSource, groupName: String): String {
        val channels = StringBuilder()
        var count = 0
        source.forEachChild(PortabilityCategory.SUBSCRIPTION_GROUPS, groupName) { child ->
            require(count++ < PortabilityLimits.MAX_CONTAINER_RECORDS) { "Flow group contains too many channels" }
            val channel = youtubeId((child as PortabilitySubscriptionGroupMembership).channelUrl)
            require(channels.length + channel.length + 1 <= PortabilityLimits.MAX_RECORD_JSON_BYTES) {
                "Flow group channel list is too large"
            }
            if (channels.isNotEmpty()) channels.append(',')
            channels.append(channel)
        }
        return channels.toString()
    }

    private fun history(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.HISTORY !in categories) return
        json.writeArrayFieldStart("viewHistory")
        source.forEach(PortabilityCategory.HISTORY) { record ->
            val item = record as PortabilityHistory
            json.writeStartObject()
            json.writeStringField("videoId", youtubeId(item.video.url))
            json.writeNumberField("position", item.positionSeconds * 1_000L)
            json.writeNumberField("duration", item.video.durationSeconds * 1_000L)
            json.writeNumberField("timestamp", item.watchedAt)
            json.writeStringField("title", item.video.title)
            json.writeStringField("thumbnailUrl", item.video.thumbnailUrl)
            json.writeStringField("channelName", item.video.channelName)
            json.writeStringField("channelId", youtubeId(item.video.channelUrl))
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun searchHistory(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.SEARCH_HISTORY !in categories) return
        json.writeArrayFieldStart("searchHistory")
        source.forEach(PortabilityCategory.SEARCH_HISTORY) { record ->
            val item = record as PortabilitySearchHistory
            json.writeStartObject()
            json.writeStringField("query", item.term)
            json.writeNumberField("timestamp", item.searchedAt)
            json.writeStringField("type", "TEXT")
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun favorites(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.FAVORITES !in categories) return
        json.writeArrayFieldStart("likedVideos")
        source.forEach(PortabilityCategory.FAVORITES) { record ->
            val item = record as PortabilityFavorite
            json.writeStartObject()
            json.writeStringField("videoId", youtubeId(item.video.url))
            json.writeStringField("title", item.video.title)
            json.writeStringField("thumbnail", item.video.thumbnailUrl)
            json.writeStringField("channelName", item.video.channelName)
            json.writeNumberField("likedAt", item.favoritedAt)
            json.writeBooleanField("isMusic", false)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun filters(json: JsonGenerator, source: PortabilityRecordSource, categories: Set<PortabilityCategory>) {
        if (PortabilityCategory.CONTENT_FILTERS !in categories) return
        json.writeObjectFieldStart("contentPreferences")
        json.writeArrayFieldStart("blockedChannels")
        source.forEach(PortabilityCategory.CONTENT_FILTERS) { record ->
            val item = record as PortabilityContentFilter
            if (item.kind == "blockedChannel") json.writeString(youtubeId(item.value))
        }
        json.writeEndArray()
        json.writeArrayFieldStart("preferredTopics")
        json.writeEndArray()
        json.writeArrayFieldStart("blockedTopics")
        json.writeEndArray()
        json.writeEndObject()
    }
}
