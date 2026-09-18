package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator

object TypeTypePortabilityLibraryWriter {
    fun write(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        writeVideos(json, source, categories, PortabilityCategory.WATCH_LATER, "watchLater")
        writeVideos(json, source, categories, PortabilityCategory.FAVORITES, "favorites")
        progress(json, source, categories)
        searchHistory(json, source, categories)
        savedPlaylists(json, source, categories)
    }

    private fun writeVideos(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
        category: PortabilityCategory,
        field: String,
    ) {
        if (category !in categories) return
        json.writeArrayFieldStart(field)
        source.forEach(category) { record ->
            val video = when (record) {
                is PortabilityWatchLater -> record.video
                is PortabilityFavorite -> record.video
                else -> return@forEach
            }
            json.writeStartObject()
            if (record is PortabilityFavorite) {
                json.writeStringField("videoUrl", video.url)
                json.writeNumberField("favoritedAt", record.favoritedAt)
            } else {
                json.writeStringField("url", video.url)
                json.writeNumberField("addedAt", (record as PortabilityWatchLater).addedAt)
            }
            json.writeVideoFields(video)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun progress(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.PROGRESS !in categories) return
        json.writeArrayFieldStart("progress")
        source.forEach(PortabilityCategory.PROGRESS) { record ->
            if (record !is PortabilityProgress) return@forEach
            json.writeStartObject()
            json.writeStringField("videoUrl", record.videoUrl)
            json.writeNumberField("position", record.positionSeconds)
            json.writeNumberField("updatedAt", record.updatedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun searchHistory(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.SEARCH_HISTORY !in categories) return
        json.writeArrayFieldStart("searchHistory")
        source.forEach(PortabilityCategory.SEARCH_HISTORY) { record ->
            if (record !is PortabilitySearchHistory) return@forEach
            json.writeStartObject()
            json.writeStringField("term", record.term)
            json.writeNumberField("searchedAt", record.searchedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }

    private fun savedPlaylists(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.SAVED_PLAYLISTS !in categories) return
        json.writeArrayFieldStart("savedPlaylists")
        source.forEach(PortabilityCategory.SAVED_PLAYLISTS) { record ->
            if (record !is PortabilitySavedPlaylist) return@forEach
            json.writeStartObject()
            json.writeStringField("id", record.sourceId)
            json.writeStringField("publicPlaylistId", record.sourceId)
            json.writeStringField("url", record.url)
            json.writeStringField("title", record.title)
            json.writeStringField("thumbnailUrl", record.thumbnailUrl)
            json.writeStringField("uploaderName", record.uploaderName)
            json.writeNumberField("streamCount", record.streamCount)
            json.writeStringField("playlistType", record.playlistType)
            json.writeNumberField("savedAt", record.savedAt)
            json.writeEndObject()
        }
        json.writeEndArray()
    }
}
