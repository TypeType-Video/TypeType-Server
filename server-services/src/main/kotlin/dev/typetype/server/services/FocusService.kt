package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FocusSettingsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusSettingsRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

sealed interface FocusUpdateResult {
    data class Success(val item: FocusSettingsItem) : FocusUpdateResult
    data object InvalidGroup : FocusUpdateResult
    data object InvalidDays : FocusUpdateResult
    data object InvalidSchedule : FocusUpdateResult
}

class FocusService(private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun get(userId: String): FocusSettingsItem = DatabaseFactory.query { settled(userId).toItem(clock()) }

    suspend fun update(userId: String, request: FocusSettingsRequest): FocusUpdateResult = DatabaseFactory.query {
        val now = clock()
        val current = settled(userId)
        val next = when {
            current.enabled && request.enabled -> current.copy(disableAt = null)
            current.enabled -> current.copy(disableAt = current.disableAt ?: (now + EXIT_DELAY_MS))
            else -> when (val change = applyRequest(userId, current, request)) {
                is FocusChange.Invalid -> return@query change.result
                is FocusChange.Valid -> change.row.copy(
                    enabled = request.enabled,
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
        val entertainment = row.entertainmentGroupId
        val now = clock()
        if (!row.enabled || entertainment == null || !FocusSchedule.isActive(row.schedule, row.timezone, now)) {
            return@query null
        }
        val channels = channelsOf(userId, entertainment)
        if (channels.isEmpty()) return@query null
        FocusProfile(
            seed = userId,
            entertainmentChannels = channels,
            studyChannels = row.studyGroupId?.let { channelsOf(userId, it) }.orEmpty(),
            entertainmentShare = FocusProfile.share(row.startedAt, row.transitionDays, now),
            archiveDays = row.archiveDays,
            archiveCutoffMs = now - row.archiveDays * FocusProfile.DAY_MS,
        )
    }

    private fun applyRequest(userId: String, current: FocusSettingsRow, request: FocusSettingsRequest): FocusChange {
        val transitionDays = request.transitionDays ?: current.transitionDays
        val archiveDays = request.archiveDays ?: current.archiveDays
        if (transitionDays !in DAY_RANGE || archiveDays !in DAY_RANGE) return FocusChange.Invalid(FocusUpdateResult.InvalidDays)
        val schedule = request.schedule ?: current.schedule
        val timezone = request.timezone?.takeIf { it.isNotBlank() } ?: current.timezone
        if (!FocusSchedule.isValid(schedule, timezone)) return FocusChange.Invalid(FocusUpdateResult.InvalidSchedule)
        val entertainment = request.entertainmentGroupId ?: current.entertainmentGroupId
        val study = when (val requested = request.studyGroupId) {
            null -> current.studyGroupId
            "" -> null
            else -> requested
        }
        val unknown = listOfNotNull(entertainment, study).any { !SubscriptionGroupQueries.exists(userId, it) }
        val conflicting = study != null && study == entertainment
        if (unknown || conflicting || (request.enabled && entertainment == null)) {
            return FocusChange.Invalid(FocusUpdateResult.InvalidGroup)
        }
        val row = current.copy(
            entertainmentGroupId = entertainment,
            studyGroupId = study,
            transitionDays = transitionDays,
            archiveDays = archiveDays,
            schedule = schedule,
            timezone = timezone,
        )
        return FocusChange.Valid(row)
    }

    private fun channelsOf(userId: String, groupId: String): Set<String> =
        SubscriptionGroupMembershipsTable.select(SubscriptionGroupMembershipsTable.channelUrl)
            .where { (SubscriptionGroupMembershipsTable.groupId eq groupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
            .mapTo(HashSet()) { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) }

    private fun settled(userId: String): FocusSettingsRow {
        val row = FocusSettingsTable.selectAll().where { FocusSettingsTable.userId eq userId }.singleOrNull()
            ?.toRow() ?: return FocusSettingsRow()
        val now = clock()
        val due = row.disableAt?.let { now >= it } == true
        if (!row.enabled || !due) return row
        return row.copy(enabled = false, disableAt = null).also { write(userId, it, now) }
    }

    private fun ResultRow.toRow() = FocusSettingsRow(
        enabled = this[FocusSettingsTable.enabled],
        entertainmentGroupId = this[FocusSettingsTable.entertainmentGroupId],
        studyGroupId = this[FocusSettingsTable.studyGroupId],
        transitionDays = this[FocusSettingsTable.transitionDays],
        archiveDays = this[FocusSettingsTable.archiveDays],
        schedule = this[FocusSettingsTable.schedule],
        timezone = this[FocusSettingsTable.timezone],
        startedAt = this[FocusSettingsTable.startedAt],
        disableAt = this[FocusSettingsTable.disableAt],
    )

    private fun write(userId: String, row: FocusSettingsRow, now: Long) {
        FocusSettingsTable.upsert {
            it[FocusSettingsTable.userId] = userId
            it[enabled] = row.enabled
            it[entertainmentGroupId] = row.entertainmentGroupId
            it[studyGroupId] = row.studyGroupId
            it[transitionDays] = row.transitionDays
            it[archiveDays] = row.archiveDays
            it[schedule] = row.schedule
            it[timezone] = row.timezone
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

private sealed interface FocusChange {
    data class Valid(val row: FocusSettingsRow) : FocusChange
    data class Invalid(val result: FocusUpdateResult) : FocusChange
}

internal data class FocusSettingsRow(
    val enabled: Boolean = false,
    val entertainmentGroupId: String? = null,
    val studyGroupId: String? = null,
    val transitionDays: Int = 60,
    val archiveDays: Int = 7,
    val schedule: String = FocusSchedule.ALWAYS,
    val timezone: String? = null,
    val startedAt: Long = 0,
    val disableAt: Long? = null,
) {
    fun toItem(now: Long) = FocusSettingsItem(
        enabled = enabled,
        entertainmentGroupId = entertainmentGroupId,
        studyGroupId = studyGroupId,
        schedule = schedule,
        timezone = timezone,
        transitionDays = transitionDays,
        archiveDays = archiveDays,
        startedAt = startedAt.takeIf { enabled },
        disableAt = disableAt,
        entertainmentShare = if (enabled) FocusProfile.share(startedAt, transitionDays, now) else 1.0,
    )
}
