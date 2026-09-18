package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import java.io.OutputStream

class FlowPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.FLOW,
        2,
        setOf(
            flowCapability(PortabilityCategory.SUBSCRIPTIONS),
            flowCapability(PortabilityCategory.SUBSCRIPTION_GROUPS),
            flowCapability(PortabilityCategory.HISTORY),
            flowCapability(PortabilityCategory.PROGRESS),
            flowCapability(PortabilityCategory.SEARCH_HISTORY),
            flowCapability(PortabilityCategory.FAVORITES),
            flowCapability(PortabilityCategory.CONTENT_FILTERS, PortabilityFidelity.PARTIAL),
            PortabilityCapability(PortabilityCategory.PLAYLISTS, setOf(PortabilityDirection.IMPORT), PortabilityFidelity.PARTIAL),
        ),
        "json",
        "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString()
        if (!probe.contains("\"viewHistory\"") || !probe.contains("\"subscriptionGroups\"")) return null
        if (!probe.contains("\"playlistVideos\"") && !probe.contains("\"likedVideos\"")) return null
        val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(probe)?.groupValues?.get(1)
        return PortabilityDetection(PortabilityFormat.FLOW, version, 98, "Flow backup fields")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) = input.withJsonParser { parser ->
        parser.requireObject()
        var version: Int? = null
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val token = parser.nextToken()
            when (field) {
                "version" -> version = parser.intValue
                "subscriptions" -> FlowPortabilityReader.subscriptions(parser, sink, token)
                "subscriptionGroups" -> FlowPortabilityReader.groups(parser, sink, token)
                "viewHistory" -> FlowPortabilityReader.history(parser, sink, token)
                "searchHistory" -> FlowPortabilityReader.searchHistory(parser, sink, token)
                "playlists" -> FlowPortabilityReader.playlists(parser, sink, token)
                "playlistVideos" -> FlowPortabilityReader.playlistVideos(parser, sink, token)
                "likedVideos" -> FlowPortabilityReader.favorites(parser, sink, token)
                "contentPreferences" -> FlowPortabilityReader.contentFilters(parser, sink, token)
                else -> parser.skipChildren()
            }
        }
        require(version == 2) { "Unsupported Flow backup version" }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) = FlowPortabilityWriter.write(source, output, categories)
}

private fun flowCapability(
    category: PortabilityCategory,
    fidelity: PortabilityFidelity = PortabilityFidelity.COMPLETE,
) = PortabilityCapability(
    category,
    setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
    fidelity,
)
