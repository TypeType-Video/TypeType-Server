package dev.typetype.server.services

import dev.typetype.server.models.AdminSettingsItem

object AdminSettingsCache {
    @Volatile
    private var cachedSettings: AdminSettingsItem? = null

    fun get(): AdminSettingsItem? = cachedSettings

    fun set(value: AdminSettingsItem?) {
        cachedSettings = value
    }

    fun clear() {
        cachedSettings = null
    }
}
