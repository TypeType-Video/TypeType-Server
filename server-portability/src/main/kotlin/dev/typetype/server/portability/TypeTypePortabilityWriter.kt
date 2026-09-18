package dev.typetype.server.portability

import com.fasterxml.jackson.core.JsonGenerator
import dev.typetype.server.models.TYPE_TYPE_BACKUP_FORMAT
import dev.typetype.server.models.TYPE_TYPE_BACKUP_VERSION
import java.io.OutputStream

object TypeTypePortabilityWriter {
    fun write(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) {
        PortabilityJsonFactory.createGenerator(output).use { json ->
            json.writeStartObject()
            json.writeStringField("format", TYPE_TYPE_BACKUP_FORMAT)
            json.writeNumberField("version", TYPE_TYPE_BACKUP_VERSION)
            json.writeNumberField("exportedAt", System.currentTimeMillis())
            writeCategories(json, categories)
            TypeTypePortabilityCoreWriter.write(json, source, categories)
            TypeTypePortabilityLibraryWriter.write(json, source, categories)
            TypeTypePortabilitySettingsWriter.write(json, source, categories)
            TypeTypePortabilityFilterWriter.write(json, source, categories)
            json.writeEndObject()
        }
    }

    private fun writeCategories(json: JsonGenerator, categories: Set<PortabilityCategory>) {
        val legacy = categories.mapTo(linkedSetOf()) {
            if (it == PortabilityCategory.SUBSCRIPTION_GROUPS) PortabilityCategory.SUBSCRIPTIONS.wireName else it.wireName
        }
        json.writeArrayFieldStart("categories")
        legacy.sorted().forEach(json::writeString)
        json.writeEndArray()
    }
}
