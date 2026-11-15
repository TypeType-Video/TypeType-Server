package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FocusSettingsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusSettingsRequest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

sealed interface FocusUpdateResult {
    data class Success(val item: FocusSettingsItem) : FocusUpdateResult
    data object InvalidGroup : FocusUpdateResult
    data object InvalidDays : FocusUpdateResult
}

class FocusService(private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun get(userId: String): FocusSettingsItem = DatabaseFactory.query { settled(userId).toItem(clock()) }

    suspend fun update(userId: String, request: FocusSettingsRequest): FocusUpdateResult = DatabaseFactory.query {
        val now = clock()
        val current = settled(userId)
        val next = when {
            current.enabled && request.enabled -> current.copy(disableAt = null)
            current.enabled -> current.copy(disableAt = current.disableAt ?: (now + EXIT_DELAY_MS))
            else -> {
                val transitionDays = request.transitionDays ?: current.transitionDays
                val archiveDays = request.archiveDays ?: current.archiveDays
                if (transitionDays !in DAY_RANGE || archiveDays !in DAY_RANGE) return@query FocusUpdateResult.InvalidDays
                val groupId = request.entertainmentGroupId ?: current.entertainmentGroupId
                if (groupId != null && !SubscriptionGroupQueries.exists(userId, groupId)) {
                    return@query FocusUpdateResult.InvalidGroup
                }
                if (request.enabled && groupId == null) return@query FocusUpdateResult.InvalidGroup
                FocusSettingsRow(
                    enabled = request.enabled,
                    entertainmentGroupId = groupId,
                    transitionDays = transitionDays,
                    archiveDays = archiveDays,
                    startedAt = if (request.enabled) now else current.startedAt,
                    disableAt = null,
                )
            }
        }
        write(userId, next, now)
        FocusUpdateResult.Success(next.toItem(now))
    }

    suspend fun profileFor(userId: String): FocusProfile? = DatabaseFactory.query {
        val row = settled(userId)
        val groupId = row.entertainmentGroupId
        if (!row.enabled || groupId == null) return@query null
        val channels = SubscriptionGroupMembershipsTable.select(SubscriptionGroupMembershipsTable.channelUrl)
            .where { (SubscriptionGroupMembershipsTable.groupId eq groupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
            .mapTo(HashSet()) { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) }
        if (channels.isEmpty()) return@query null
        val now = clock()
        FocusProfile(
            seed = userId,
            entertainmentChannels = channels,
            entertainmentShare = FocusProfile.share(row.startedAt, row.transitionDays, now),
            archiveDays = row.archiveDays,
            archiveCutoffMs = now - row.archiveDays * FocusProfile.DAY_MS,
        )
    }

    private fun settled(userId: String): FocusSettingsRow {
        val row = FocusSettingsTable.selectAll().where { FocusSettingsTable.userId eq userId }.singleOrNull()
            ?.let {
                FocusSettingsRow(
                    enabled = it[FocusSettingsTable.enabled],
                    entertainmentGroupId = it[FocusSettingsTable.entertainmentGroupId],
                    transitionDays = it[FocusSettingsTable.transitionDays],
                    archiveDays = it[FocusSettingsTable.archiveDays],
                    startedAt = it[FocusSettingsTable.startedAt],
                    disableAt = it[FocusSettingsTable.disableAt],
                )
            } ?: return FocusSettingsRow()
        val now = clock()
        val due = row.disableAt?.let { now >= it } == true
        if (!row.enabled || !due) return row
        return row.copy(enabled = false, disableAt = null).also { write(userId, it, now) }
    }

    private fun write(userId: String, row: FocusSettingsRow, now: Long) {
        FocusSettingsTable.upsert {
            it[FocusSettingsTable.userId] = userId
            it[enabled] = row.enabled
            it[entertainmentGroupId] = row.entertainmentGroupId
            it[transitionDays] = row.transitionDays
            it[archiveDays] = row.archiveDays
            it[startedAt] = row.startedAt
            it[disableAt] = row.disableAt
            it[updatedAt] = now
        }
    }

    companion object {
        const val EXIT_DELAY_MS = 24 * 60 * 60 * 1000L
        private val DAY_RANGE = 1..365
    }
}

internal data class FocusSettingsRow(
    val enabled: Boolean = false,
    val entertainmentGroupId: String? = null,
    val transitionDays: Int = 60,
    val archiveDays: Int = 7,
    val startedAt: Long = 0,
    val disableAt: Long? = null,
) {
    fun toItem(now: Long) = FocusSettingsItem(
        enabled = enabled,
        entertainmentGroupId = entertainmentGroupId,
        transitionDays = transitionDays,
        archiveDays = archiveDays,
        startedAt = startedAt.takeIf { enabled },
        disableAt = disableAt,
        entertainmentShare = if (enabled) FocusProfile.share(startedAt, transitionDays, now) else 1.0,
    )
}
