package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator

object TypeTypePortabilityFilterWriter {
    fun write(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.CONTENT_FILTERS !in categories) return
        json.writeObjectFieldStart("contentFilters")
        writeArray(json, source, "blockedChannels", "blockedChannel")
        writeArray(json, source, "blockedVideos", "blockedVideo")
        writeArray(json, source, "blockedKeywords", "blockedKeyword")
        writeArray(json, source, "allowedChannels", "allowedChannel")
        writeArray(json, source, "allowedPlaylists", "allowedPlaylist")
        json.writeEndObject()
    }

    private fun writeArray(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        field: String,
        kind: String,
    ) {
        json.writeArrayFieldStart(field)
        source.forEach(PortabilityCategory.CONTENT_FILTERS) { record ->
            if (record !is PortabilityContentFilter || record.kind != kind) return@forEach
            json.writeStartObject()
            when (kind) {
                "blockedKeyword" -> json.writeStringField("keyword", record.value)
                else -> json.writeStringField("url", record.value)
            }
            if (kind == "allowedPlaylist") {
                json.writeStringField("title", record.label)
                json.writeStringField("thumbnailUrl", record.imageUrl)
                json.writeStringField("uploaderName", record.metadata["uploaderName"]?.toString()?.trim('"').orEmpty())
                json.writeNumberField("allowedAt", record.createdAt)
            } else if (kind.startsWith("allowed")) {
                json.writeStringField("name", record.label)
                json.writeStringField("thumbnailUrl", record.imageUrl)
                json.writeNumberField("allowedAt", record.createdAt)
            } else {
                json.writeStringField("name", record.label)
                json.writeStringField("thumbnailUrl", record.imageUrl)
                json.writeNumberField("blockedAt", record.createdAt)
            }
            json.writeEndObject()
        }
        json.writeEndArray()
    }
}
