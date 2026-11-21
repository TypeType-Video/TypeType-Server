package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusSettingsRequest

class FocusService(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val studyChannels = FocusStudyChannelService(clock)

    suspend fun get(userId: String): FocusSettingsItem = DatabaseFactory.query {
        val now = clock()
        val row = readSettledFocusRow(userId, now)
        val active = isActiveRow(row, now)
        val studyKeys = if (active) activeStudyKeys(userId, row, now) else emptyList()
        row.toItem(now, active = active, studyChannelKeys = studyKeys)
    }

    suspend fun update(userId: String, request: FocusSettingsRequest): FocusUpdateResult = DatabaseFactory.query {
        val now = clock()
        val current = readSettledFocusRow(userId, now)
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
        writeFocusSettingsRow(userId, next, now)
        val active = isActiveRow(next, now)
        val studyKeys = if (active) activeStudyKeys(userId, next, now) else emptyList()
        FocusUpdateResult.Success(next.toItem(now, active = active, studyChannelKeys = studyKeys))
    }

    suspend fun profileFor(userId: String): FocusProfile? = DatabaseFactory.query {
        val now = clock()
        val row = readSettledFocusRow(userId, now)
        val entertainment = row.entertainmentGroupId
        if (!isActiveRow(row, now) || entertainment == null) return@query null
        val entertainmentChannels = studyChannels.channelsOf(userId, entertainment)
        val partitionedStudy = studyChannels.partitionStudyChannels(userId, row, now, entertainmentChannels)
        if (!row.strictBlocking && entertainmentChannels.isEmpty() && partitionedStudy.active.isEmpty() && partitionedStudy.pending.isEmpty()) {
            return@query null
        }
        FocusProfile(
            seed = userId,
            entertainmentChannels = entertainmentChannels,
            studyChannels = partitionedStudy.active,
            pendingStudyUnlocksAt = partitionedStudy.pending,
            entertainmentShare = FocusProfile.share(row.startedAt, row.transitionDays, now),
            archiveDays = row.archiveDays,
            archiveCutoffMs = now - row.archiveDays * FocusProfile.DAY_MS,
            strictBlocking = row.strictBlocking,
        )
    }

    suspend fun addStudyChannel(
        userId: String,
        rawChannelUrl: String,
        rawName: String = "",
        rawAvatarUrl: String = "",
    ): FocusStudyChannelResult = studyChannels.addStudyChannel(userId, rawChannelUrl, rawName, rawAvatarUrl)

    suspend fun removeStudyChannel(userId: String, rawChannelUrl: String): FocusStudyChannelRemoveResult =
        studyChannels.removeStudyChannel(userId, rawChannelUrl)

    private fun activeStudyKeys(userId: String, row: FocusSettingsRow, now: Long): List<String> {
        val entertainment = row.entertainmentGroupId?.let { studyChannels.channelsOf(userId, it) }.orEmpty()
        return studyChannels.partitionStudyChannels(userId, row, now, entertainment).active.sorted()
    }

    private fun isActiveRow(row: FocusSettingsRow, now: Long): Boolean =
        row.enabled && row.entertainmentGroupId != null && FocusSchedule.isActive(row.schedule, row.timezone, now)

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
            strictBlocking = request.strictBlocking ?: current.strictBlocking,
            grayscaleThumbnails = request.grayscaleThumbnails ?: current.grayscaleThumbnails,
            schedule = schedule,
            timezone = timezone,
        )
        return FocusChange.Valid(row)
    }

    private sealed interface FocusChange {
        data class Valid(val row: FocusSettingsRow) : FocusChange
        data class Invalid(val result: FocusUpdateResult) : FocusChange
    }

    companion object {
        const val EXIT_DELAY_MS = 24 * 60 * 60 * 1000L
        const val STUDY_ADD_DELAY_MS = FocusStudyChannelService.STUDY_ADD_DELAY_MS
        private val DAY_RANGE = 1..365
    }
}
