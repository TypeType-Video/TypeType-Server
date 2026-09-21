package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import java.io.OutputStream

class MaterialiousPortabilityAdapter : PortabilityAdapter {
    private val opml = OpmlPortabilityAdapter(PortabilityFormat.MATERIALIOUS, autoDetect = false)

    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.MATERIALIOUS,
        1,
        setOf(
            PortabilityCapability(
                PortabilityCategory.SUBSCRIPTIONS,
                setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT),
                PortabilityFidelity.PARTIAL,
            ),
        ),
        "json",
        "application/json",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) return null
        if (opml.detect(input) != null) {
            return PortabilityDetection(PortabilityFormat.MATERIALIOUS, "opml", 82, "Materialious-compatible OPML")
        }
        val probe = input.probe.decodeToString()
        if (!probe.trimStart().startsWith("{") || !probe.contains("\"subscriptions\"")) return null
        if (probe.contains("\"app_version")) return null
        val confidence = if (input.filename.contains("materialious", ignoreCase = true)) 99 else 86
        return PortabilityDetection(PortabilityFormat.MATERIALIOUS, "invidious-subscriptions", confidence, "Materialious Invidious subscription JSON")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        val detection = requireNotNull(detect(input)) { "Unsupported Materialious export" }
        if (detection.formatVersion == "opml") return opml.decode(input, sink)
        input.withJsonParser { parser ->
            parser.requireObject()
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val field = parser.currentName
                val token = parser.nextToken()
                if (field == "subscriptions" && token == JsonToken.START_ARRAY) readSubscriptions(parser, sink)
                else parser.skipChildren()
            }
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        require(PortabilityCategory.SUBSCRIPTIONS in categories) { "Materialious export requires subscriptions" }
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            json.writeArrayFieldStart("subscriptions")
            source.forEach(PortabilityCategory.SUBSCRIPTIONS) { record ->
                (record as? PortabilitySubscription)?.let { json.writeString(youtubeId(it.channelUrl)) }
            }
            json.writeEndArray()
            json.writeEndObject()
        }
    }

    private fun readSubscriptions(parser: com.fasterxml.jackson.core.JsonParser, sink: PortabilityRecordSink) {
        sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
        var count = 0
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(++count <= PortabilityLimits.MAX_CONTAINER_RECORDS) { "Materialious export contains too many subscriptions" }
            val channelUrl = youtubeChannelUrl(parser.textOrEmpty())
            if (channelUrl.isNotBlank()) sink.write(PortabilitySubscription(channelUrl))
        }
    }
}
