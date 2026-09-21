package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator

object TypeTypePortabilitySettingsWriter {
    fun write(
        json: JsonGenerator,
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ) {
        if (PortabilityCategory.SETTINGS !in categories) return
        json.writeFieldName("settings")
        var written = false
        source.forEach(PortabilityCategory.SETTINGS) { record ->
            if (record is PortabilitySettings && !written) {
                json.writeRawValue(record.values.toString())
                written = true
            }
        }
        if (!written) json.writeNull()
    }
}
