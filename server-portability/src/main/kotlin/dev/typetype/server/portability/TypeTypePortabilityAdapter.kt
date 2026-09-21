package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import dev.typetype.server.models.TYPE_TYPE_BACKUP_FORMAT
import dev.typetype.server.models.TYPE_TYPE_BACKUP_VERSION
import java.io.OutputStream

class TypeTypePortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format = PortabilityFormat.TYPE_TYPE,
        adapterVersion = 1,
        capabilities = PortabilityCategory.entries.mapTo(linkedSetOf()) { category ->
            PortabilityCapability(
                category,
                setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
                PortabilityFidelity.COMPLETE,
            )
        },
        defaultExtension = "json",
        contentType = "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString()
        val formatMarker = Regex("\"format\"\\s*:\\s*\"${Regex.escape(TYPE_TYPE_BACKUP_FORMAT)}\"")
        if (!formatMarker.containsMatchIn(probe)) return null
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(probe)?.groupValues?.get(1)
        return PortabilityDetection(PortabilityFormat.TYPE_TYPE, version, 100, "TypeType backup marker")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) = input.withJsonParser { parser ->
        parser.requireObject()
        var format: String? = null
        var version: Int? = null
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when (field) {
                "format" -> format = parser.textOrEmpty()
                "version" -> version = parser.intValue
                "subscriptions" -> sink.read(PortabilityCategory.SUBSCRIPTIONS) { TypeTypePortabilityReader.subscriptions(parser, token, sink) }
                "subscriptionGroups" -> sink.read(PortabilityCategory.SUBSCRIPTION_GROUPS) { TypeTypePortabilityReader.groups(parser, token, sink) }
                "history" -> sink.read(PortabilityCategory.HISTORY) { TypeTypePortabilityReader.history(parser, token, sink) }
                "playlists" -> sink.read(PortabilityCategory.PLAYLISTS) { TypeTypePortabilityReader.playlists(parser, token, sink) }
                "watchLater" -> sink.read(PortabilityCategory.WATCH_LATER) { TypeTypePortabilityReader.watchLater(parser, token, sink) }
                "favorites" -> sink.read(PortabilityCategory.FAVORITES) { TypeTypePortabilityReader.favorites(parser, token, sink) }
                "progress" -> sink.read(PortabilityCategory.PROGRESS) { TypeTypePortabilityReader.progress(parser, token, sink) }
                "searchHistory" -> sink.read(PortabilityCategory.SEARCH_HISTORY) { TypeTypePortabilityReader.searchHistory(parser, token, sink) }
                "savedPlaylists" -> sink.read(PortabilityCategory.SAVED_PLAYLISTS) { TypeTypePortabilityReader.savedPlaylists(parser, token, sink) }
                "settings" -> sink.read(PortabilityCategory.SETTINGS) { TypeTypePortabilityReader.settings(parser, token, sink) }
                "contentFilters" -> sink.read(PortabilityCategory.CONTENT_FILTERS) { TypeTypePortabilityReader.contentFilters(parser, token, sink) }
                else -> parser.skipChildren()
            }
        }
        require(format == TYPE_TYPE_BACKUP_FORMAT) { "Unsupported TypeType backup format" }
        require(version == TYPE_TYPE_BACKUP_VERSION) { "Unsupported TypeType backup version" }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) = TypeTypePortabilityWriter.write(source, output, categories)
}

private inline fun PortabilityRecordSink.read(category: PortabilityCategory, block: () -> Unit) {
    markCategory(category)
    block()
}
