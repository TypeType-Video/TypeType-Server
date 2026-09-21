package dev.typetype.server.portability

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.services.TypeTypeBackupLibraryRestore

object TypeTypePortabilitySettingsImport {
    fun write(userId: String, source: PortabilityRecordSource, onRecord: () -> Unit): Long {
        var count = 0L
        source.forEach(PortabilityCategory.SETTINGS) { record ->
            if (record is PortabilitySettings) {
                val settings = CacheJson.decodeFromString<SettingsItem>(record.values.toString())
                count += TypeTypeBackupLibraryRestore.settings(userId, settings)
            }
            onRecord()
        }
        return count
    }
}
