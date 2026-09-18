package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.models.SettingsItem
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class SettingsService {

    suspend fun get(userId: String): SettingsItem = DatabaseFactory.query {
        SettingsTable.selectAll().where { SettingsTable.userId eq userId }.singleOrNull()?.toSettingsItem() ?: SettingsItem()
    }

    suspend fun upsert(userId: String, settings: SettingsItem): SettingsItem {
        val saved = settings.normalized()
        DatabaseFactory.query {
            val updated = SettingsTable.update({ SettingsTable.userId eq userId }) {
                it.writeSettings(saved)
            }
            if (updated == 0) {
                SettingsTable.insert {
                    it[SettingsTable.userId] = userId
                    it.writeSettings(saved)
                }
            }
        }
        return saved
    }

    suspend fun isWatchHistoryDisabled(userId: String): Boolean = DatabaseFactory.query {
        SettingsTable.selectAll().where { SettingsTable.userId eq userId }.singleOrNull()
            ?.get(SettingsTable.disableWatchHistory) ?: false
    }

    suspend fun subscriptionFeedVisibility(userId: String): SubscriptionFeedVisibility = DatabaseFactory.query {
        SettingsTable.selectAll().where { SettingsTable.userId eq userId }.singleOrNull()?.let {
            SubscriptionFeedVisibility(
                hideLiveStreams = it[SettingsTable.hideSubscriptionLiveStreams],
                hideMembersOnlyContent = it[SettingsTable.hideMembersOnlyContent],
            )
        } ?: SubscriptionFeedVisibility()
    }

    suspend fun getAccessModePolicy(userId: String): AccessModePolicy = DatabaseFactory.query {
        SettingsTable.selectAll().where { SettingsTable.userId eq userId }.singleOrNull()?.let {
            AccessModePolicy(
                accessMode = it[SettingsTable.accessMode].toAccessMode(),
                adminManaged = it[SettingsTable.accessModeAdminManaged],
            )
        } ?: AccessModePolicy(ACCESS_MODE_UNRESTRICTED, adminManaged = false)
    }
}

data class SubscriptionFeedVisibility(
    val hideLiveStreams: Boolean = false,
    val hideMembersOnlyContent: Boolean = false,
)

data class AccessModePolicy(val accessMode: String, val adminManaged: Boolean)
