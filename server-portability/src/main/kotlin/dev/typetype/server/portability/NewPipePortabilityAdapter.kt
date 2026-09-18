package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonToken
import java.io.OutputStream

class NewPipePortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format = PortabilityFormat.NEW_PIPE,
        adapterVersion = 3,
        capabilities = newPipeArchiveCapabilities(),
        defaultExtension = "zip",
        contentType = "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        if (input.archive != null) {
            val version = NewPipeArchiveDatabase.userVersion(input) ?: return null
            if (version !in 1 until PIPE_PIPE_DATABASE_VERSION) return null
            return PortabilityDetection(PortabilityFormat.NEW_PIPE, version.toString(), 100, "NewPipe SQLite schema version")
        }
        val probe = input.probe.decodeToString()
        if (!probe.contains("\"subscriptions\"") || !probe.contains("\"app_version")) return null
        return PortabilityDetection(PortabilityFormat.NEW_PIPE, null, 98, "NewPipe subscription fields")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        if (input.archive != null) {
            requireNotNull(detect(input)) { "Unsupported NewPipe backup" }
            NewPipeArchiveDatabase.read(input) { NewPipeDatabasePortabilityReader.read(it, sink) }
            return
        }
        decodeSubscriptions(input, sink)
    }

    private fun decodeSubscriptions(input: PortabilityInput, sink: PortabilityRecordSink) = input.withJsonParser { parser ->
        parser.requireObject()
        var foundSubscriptions = false
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val field = parser.currentName
            val valueToken = parser.nextToken()
            if (field == "subscriptions" && valueToken == JsonToken.START_ARRAY) {
                foundSubscriptions = true
                sink.markCategory(PortabilityCategory.SUBSCRIPTIONS)
                readSubscriptions(parser, sink)
            } else {
                parser.skipChildren()
            }
        }
        require(foundSubscriptions) { "NewPipe backup does not contain subscriptions" }
    }

    private companion object {
        const val PIPE_PIPE_DATABASE_VERSION = 900
    }

    override fun assessExport(
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ): List<PortabilityIssue> {
        return super.assessExport(source, categories) +
            newPipeArchiveExportIssues(source, categories, NewPipeArchiveTarget.NEW_PIPE)
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        NewPipeArchivePortabilityWriter.write(source, output, categories, NewPipeArchiveTarget.NEW_PIPE)
    }

    private fun readSubscriptions(
        parser: com.fasterxml.jackson.core.JsonParser,
        sink: PortabilityRecordSink,
    ) {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            require(parser.currentToken() == JsonToken.START_OBJECT) { "Invalid NewPipe subscription" }
            var serviceId = -1
            var url = ""
            var name = ""
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val field = parser.currentName
                parser.nextToken()
                when (field) {
                    "service_id" -> serviceId = parser.intValue
                    "url" -> url = parser.textOrEmpty()
                    "name" -> name = parser.textOrEmpty()
                    else -> parser.skipChildren()
                }
            }
            if (serviceId != 0) {
                sink.issue(
                    PortabilityIssue(
                        PortabilityCategory.SUBSCRIPTIONS,
                        "unsupported_subscription_provider",
                        "A non-YouTube NewPipe subscription was skipped",
                    ),
                )
            } else if (url.isNotBlank()) {
                sink.write(PortabilitySubscription(url.trim(), name.trim()))
            }
        }
    }
}
