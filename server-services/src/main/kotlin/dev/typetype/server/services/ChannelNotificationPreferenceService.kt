package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.ChannelNotificationPreferencesTable
import dev.typetype.server.models.ChannelNotificationPreference
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ChannelNotificationPreferenceService(
    private val subscriptionsService: SubscriptionsService,
) {
    suspend fun list(userId: String): List<ChannelNotificationPreference> {
        val subscriptions = subscriptionsService.getAll(userId)
        val urls = subscriptions.map { ChannelUrlCanonicalizer.canonicalize(it.channelUrl) }
        if (urls.isEmpty()) return emptyList()
        val stored = DatabaseFactory.query {
            ChannelNotificationPreferencesTable.selectAll().where {
                ChannelNotificationPreferencesTable.userId eq userId
            }.associateBy { it[ChannelNotificationPreferencesTable.channelUrl] }
        }
        return urls.map { channelUrl ->
            val row = stored[channelUrl]
            ChannelNotificationPreference(
                channelUrl,
                row?.get(ChannelNotificationPreferencesTable.enabled) ?: false,
                row?.get(ChannelNotificationPreferencesTable.updatedAt) ?: 0L,
            )
        }
    }

    suspend fun set(userId: String, rawChannelUrl: String, enabled: Boolean): PreferenceUpdateResult {
        val channelUrl = ChannelUrlCanonicalizer.canonicalize(rawChannelUrl)
        if (!isSubscribed(userId, channelUrl)) return PreferenceUpdateResult.NotSubscribed
        val now = System.currentTimeMillis()
        DatabaseFactory.query { upsert(userId, channelUrl, enabled, now) }
        return PreferenceUpdateResult.Updated(ChannelNotificationPreference(channelUrl, enabled, now))
    }

    suspend fun setAll(
        userId: String,
        enabled: Boolean,
        rawChannelUrls: List<String>?,
    ): List<ChannelNotificationPreference> {
        val subscribed = subscriptionsService.getAll(userId)
            .mapTo(linkedSetOf()) { ChannelUrlCanonicalizer.canonicalize(it.channelUrl) }
        val targets = rawChannelUrls
            ?.mapTo(linkedSetOf(), ChannelUrlCanonicalizer::canonicalize)
            ?.filterTo(linkedSetOf()) { it in subscribed }
            ?: subscribed
        if (targets.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        DatabaseFactory.query { targets.forEach { upsert(userId, it, enabled, now) } }
        return targets.map { ChannelNotificationPreference(it, enabled, now) }
    }

    private fun upsert(userId: String, channelUrl: String, enabled: Boolean, now: Long) {
        val inserted = ChannelNotificationPreferencesTable.insertIgnore {
            it[ChannelNotificationPreferencesTable.userId] = userId
            it[ChannelNotificationPreferencesTable.channelUrl] = channelUrl
            it[ChannelNotificationPreferencesTable.enabled] = enabled
            it[ChannelNotificationPreferencesTable.updatedAt] = now
        }.insertedCount
        if (inserted == 0) {
            ChannelNotificationPreferencesTable.update({
                (ChannelNotificationPreferencesTable.userId eq userId) and
                    (ChannelNotificationPreferencesTable.channelUrl eq channelUrl)
            }) {
                it[ChannelNotificationPreferencesTable.enabled] = enabled
                it[ChannelNotificationPreferencesTable.updatedAt] = now
            }
        }
    }

    suspend fun remove(userId: String, rawChannelUrl: String) {
        val channelUrl = ChannelUrlCanonicalizer.canonicalize(rawChannelUrl)
        DatabaseFactory.query {
            ChannelNotificationPreferencesTable.deleteWhere {
                (ChannelNotificationPreferencesTable.userId eq userId) and
                    (ChannelNotificationPreferencesTable.channelUrl eq channelUrl)
            }
        }
    }

    suspend fun enabledChannelUrls(userId: String): Set<String> = list(userId)
        .filter(ChannelNotificationPreference::enabled)
        .mapTo(mutableSetOf(), ChannelNotificationPreference::channelUrl)

    private suspend fun isSubscribed(userId: String, channelUrl: String): Boolean =
        subscriptionsService.getAll(userId).any {
            ChannelUrlCanonicalizer.canonicalize(it.channelUrl) == channelUrl
        }
}

sealed interface PreferenceUpdateResult {
    data class Updated(val preference: ChannelNotificationPreference) : PreferenceUpdateResult
    data object NotSubscribed : PreferenceUpdateResult
}
