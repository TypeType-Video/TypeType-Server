package dev.typetype.server.services

import dev.typetype.server.db.tables.FocusSettingsTable
import dev.typetype.server.models.FocusSettingsItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

internal data class FocusSettingsRow(
    val enabled: Boolean = false,
    val entertainmentGroupId: String? = null,
    val studyGroupId: String? = null,
    val transitionDays: Int = 60,
    val archiveDays: Int = 7,
    val strictBlocking: Boolean = false,
    val grayscaleThumbnails: Boolean = false,
    val schedule: String = FocusSchedule.ALWAYS,
    val timezone: String? = null,
    val startedAt: Long = 0,
    val disableAt: Long? = null,
) {
    fun toItem(
        now: Long,
        active: Boolean = false,
        studyChannelKeys: List<String> = emptyList(),
    ): FocusSettingsItem = FocusSettingsItem(
        enabled = enabled,
        entertainmentGroupId = entertainmentGroupId,
        studyGroupId = studyGroupId,
        schedule = schedule,
        timezone = timezone,
        transitionDays = transitionDays,
        archiveDays = archiveDays,
        strictBlocking = strictBlocking,
        grayscaleThumbnails = grayscaleThumbnails,
        startedAt = startedAt.takeIf { enabled },
        disableAt = disableAt,
        entertainmentShare = if (enabled) FocusProfile.share(startedAt, transitionDays, now) else 1.0,
        active = active,
        studyChannelKeys = studyChannelKeys,
    )
}

internal fun readSettledFocusRow(userId: String, now: Long): FocusSettingsRow {
    val row = FocusSettingsTable.selectAll().where { FocusSettingsTable.userId eq userId }.singleOrNull()
        ?.toFocusSettingsRow() ?: return FocusSettingsRow()
    val due = row.disableAt?.let { now >= it } == true
    if (!row.enabled || !due) return row
    return row.copy(enabled = false, disableAt = null).also { writeFocusSettingsRow(userId, it, now) }
}

internal fun writeFocusSettingsRow(userId: String, row: FocusSettingsRow, now: Long) {
    FocusSettingsTable.upsert {
        it[FocusSettingsTable.userId] = userId
        it[enabled] = row.enabled
        it[entertainmentGroupId] = row.entertainmentGroupId
        it[studyGroupId] = row.studyGroupId
        it[transitionDays] = row.transitionDays
        it[archiveDays] = row.archiveDays
        it[strictBlocking] = row.strictBlocking
        it[grayscaleThumbnails] = row.grayscaleThumbnails
        it[schedule] = row.schedule
        it[timezone] = row.timezone
        it[startedAt] = row.startedAt
        it[disableAt] = row.disableAt
        it[updatedAt] = now
    }
}

private fun ResultRow.toFocusSettingsRow(): FocusSettingsRow = FocusSettingsRow(
    enabled = this[FocusSettingsTable.enabled],
    entertainmentGroupId = this[FocusSettingsTable.entertainmentGroupId],
    studyGroupId = this[FocusSettingsTable.studyGroupId],
    transitionDays = this[FocusSettingsTable.transitionDays],
    archiveDays = this[FocusSettingsTable.archiveDays],
    strictBlocking = this[FocusSettingsTable.strictBlocking],
    grayscaleThumbnails = this[FocusSettingsTable.grayscaleThumbnails],
    schedule = this[FocusSettingsTable.schedule],
    timezone = this[FocusSettingsTable.timezone],
    startedAt = this[FocusSettingsTable.startedAt],
    disableAt = this[FocusSettingsTable.disableAt],
)
