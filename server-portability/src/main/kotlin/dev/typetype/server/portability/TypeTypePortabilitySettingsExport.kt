package dev.typetype.server.portability

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.toSettingsItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

object TypeTypePortabilitySettingsExport {
    fun write(userId: String, sink: PortabilityRecordSink) {
        val settings = SettingsTable.selectAll().where { SettingsTable.userId eq userId }
            .singleOrNull()?.toSettingsItem() ?: SettingsItem()
        val json = CacheJson.parseToJsonElement(CacheJson.encodeToString(settings)).jsonObject
        sink.write(PortabilitySettings(json))
    }
}
