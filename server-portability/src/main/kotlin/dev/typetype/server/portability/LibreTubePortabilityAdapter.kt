package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import java.io.OutputStream

class LibreTubePortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.LIBRE_TUBE,
        1,
        setOf(
            libreCapability(PortabilityCategory.SUBSCRIPTIONS, PortabilityFidelity.COMPLETE),
            libreCapability(PortabilityCategory.SUBSCRIPTION_GROUPS, PortabilityFidelity.COMPLETE),
            libreCapability(PortabilityCategory.HISTORY, PortabilityFidelity.PARTIAL),
            libreCapability(PortabilityCategory.PROGRESS, PortabilityFidelity.COMPLETE),
            libreCapability(PortabilityCategory.SEARCH_HISTORY, PortabilityFidelity.PARTIAL),
            libreCapability(PortabilityCategory.PLAYLISTS, PortabilityFidelity.PARTIAL),
            libreCapability(PortabilityCategory.SAVED_PLAYLISTS, PortabilityFidelity.COMPLETE),
        ),
        "json",
        "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString()
        if (!probe.contains("\"format\"") || !probe.contains("\"Piped\"")) return null
        val evidence = UNIQUE_FIELDS.firstOrNull(probe::contains) ?: return null
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(probe)?.groupValues?.get(1)
        return PortabilityDetection(PortabilityFormat.LIBRE_TUBE, version, 99, "LibreTube field $evidence")
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
                "subscriptions", "localSubscriptions" -> PipedPortabilityReader.subscriptions(parser, sink, token)
                "groups", "channelGroups" -> PipedPortabilityReader.groups(parser, sink, token)
                "watchHistory" -> LibreTubePortabilityReader.history(parser, sink, token)
                "watchPositions" -> LibreTubePortabilityReader.positions(parser, sink, token)
                "searchHistory" -> LibreTubePortabilityReader.searchHistory(parser, sink, token)
                "localPlaylists" -> LibreTubePortabilityReader.localPlaylists(parser, sink, token)
                "playlistBookmarks" -> LibreTubePortabilityReader.bookmarks(parser, sink, token)
                else -> parser.skipChildren()
            }
        }
        require(format == "Piped" && version == 1) { "Unsupported LibreTube backup version" }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) = LibreTubePortabilityWriter.write(source, output, categories)

    private companion object {
        val UNIQUE_FIELDS = listOf(
            "\"watchPositions\"",
            "\"customInstances\"",
            "\"playlistBookmarks\"",
            "\"localPlaylists\"",
            "\"preferences\"",
        )
    }
}

private fun libreCapability(category: PortabilityCategory, fidelity: PortabilityFidelity) = PortabilityCapability(
    category,
    setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
    fidelity,
)
