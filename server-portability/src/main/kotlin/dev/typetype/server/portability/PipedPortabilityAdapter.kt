package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import java.io.OutputStream

class PipedPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.PIPED,
        1,
        setOf(
            pipedCapability(PortabilityCategory.SUBSCRIPTIONS, PortabilityFidelity.COMPLETE),
            pipedCapability(PortabilityCategory.SUBSCRIPTION_GROUPS, PortabilityFidelity.COMPLETE),
            pipedCapability(PortabilityCategory.HISTORY, PortabilityFidelity.COMPLETE),
            pipedCapability(PortabilityCategory.PLAYLISTS, PortabilityFidelity.PARTIAL),
        ),
        "json",
        "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        val probe = input.probe.decodeToString()
        if (LIBRE_TUBE_FIELDS.any(probe::contains)) return null
        if (probe.contains("\"format\"") && probe.contains("\"Piped\"")) {
            val version = Regex("\"version\"\\s*:\\s*(\\d+)").find(probe)?.groupValues?.get(1)
            return PortabilityDetection(PortabilityFormat.PIPED, version, 96, "Piped format marker")
        }
        val trimmed = probe.trimStart()
        if (trimmed.startsWith("[") && (probe.contains("UC") || probe.contains("youtube.com/channel/"))) {
            return PortabilityDetection(PortabilityFormat.PIPED, null, 70, "Piped subscription array")
        }
        return null
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) = input.withJsonParser { parser ->
        when (parser.nextToken()) {
            JsonToken.START_ARRAY -> PipedPortabilityReader.subscriptions(parser, sink)
            JsonToken.START_OBJECT -> readObject(parser, sink)
            else -> error("Invalid Piped backup")
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) = PipedPortabilityWriter.write(source, output, categories)

    private fun readObject(parser: com.fasterxml.jackson.core.JsonParser, sink: PortabilityRecordSink) {
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
                "watchHistory" -> PipedPortabilityReader.history(parser, sink, token)
                "playlists" -> PipedPortabilityReader.playlists(parser, sink, token)
                else -> parser.skipChildren()
            }
        }
        require(format == "Piped" && version == 1) { "Unsupported Piped backup version" }
    }

    private companion object {
        val LIBRE_TUBE_FIELDS = listOf(
            "\"watchPositions\"",
            "\"customInstances\"",
            "\"playlistBookmarks\"",
            "\"localPlaylists\"",
        )
    }
}

private fun pipedCapability(category: PortabilityCategory, fidelity: PortabilityFidelity) = PortabilityCapability(
    category,
    setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
    fidelity,
)
