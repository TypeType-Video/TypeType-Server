package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FocusSettingsTable
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.FocusSettingsItem
import dev.typetype.server.models.FocusSettingsRequest
import dev.typetype.server.models.FocusStudyChannelResponse
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

sealed interface FocusUpdateResult {
    data class Success(val item: FocusSettingsItem) : FocusUpdateResult
    data object InvalidGroup : FocusUpdateResult
    data object InvalidDays : FocusUpdateResult
    data object InvalidSchedule : FocusUpdateResult
}

sealed interface FocusStudyChannelResult {
    data class Success(val response: FocusStudyChannelResponse) : FocusStudyChannelResult
    data object InvalidChannel : FocusStudyChannelResult
    data object NoStudyGroup : FocusStudyChannelResult
    data object EntertainmentConflict : FocusStudyChannelResult
}

sealed interface FocusStudyChannelRemoveResult {
    data object Success : FocusStudyChannelRemoveResult
    data object InvalidChannel : FocusStudyChannelRemoveResult
    data object NoStudyGroup : FocusStudyChannelRemoveResult
    data object NotFound : FocusStudyChannelRemoveResult
}

class FocusService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val focusAiEnabled: () -> Boolean = { false },
) {
    suspend fun get(userId: String): FocusSettingsItem = DatabaseFactory.query {
        val now = clock()
        val row = settled(userId)
        val active = isActiveRow(row, now)
        val studyKeys = if (active) {
            val entertainment = row.entertainmentGroupId?.let { channelsOf(userId, it) }.orEmpty()
            partitionStudyChannels(userId, row, now, entertainment).active.sorted()
        } else {
            emptyList()
        }
        row.toItem(now, active = active, focusAiEnabled = focusAiEnabled(), studyChannelKeys = studyKeys)
    }

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
        val active = isActiveRow(next, now)
        val studyKeys = if (active) {
            val entertainment = next.entertainmentGroupId?.let { channelsOf(userId, it) }.orEmpty()
            partitionStudyChannels(userId, next, now, entertainment).active.sorted()
        } else {
            emptyList()
        }
        FocusUpdateResult.Success(
            next.toItem(now, active = active, focusAiEnabled = focusAiEnabled(), studyChannelKeys = studyKeys),
        )
    }

    suspend fun profileFor(userId: String): FocusProfile? = DatabaseFactory.query {
        val row = settled(userId)
        val entertainment = row.entertainmentGroupId
        val now = clock()
        if (!isActiveRow(row, now) || entertainment == null) {
            return@query null
        }
        val entertainmentChannels = channelsOf(userId, entertainment)
        val partitionedStudy = partitionStudyChannels(userId, row, now, entertainmentChannels)
        if (entertainmentChannels.isEmpty() && partitionedStudy.active.isEmpty() && partitionedStudy.pending.isEmpty()) {
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
        )
    }

    suspend fun addStudyChannel(
        userId: String,
        rawChannelUrl: String,
        rawName: String = "",
        rawAvatarUrl: String = "",
    ): FocusStudyChannelResult {
        val trimmed = rawChannelUrl.trim()
        val isHttpUrl = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        if (trimmed.isEmpty() || trimmed.length > MAX_CHANNEL_URL_LENGTH || !isHttpUrl) {
            return FocusStudyChannelResult.InvalidChannel
        }
        val canonicalUrl = ChannelUrlCanonicalizer.canonicalize(trimmed)
        val normalizedKey = normalizeChannelKey(canonicalUrl)
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val now = clock()
            val row = settled(userId)
            val studyGroupId = row.studyGroupId?.takeIf { SubscriptionGroupQueries.exists(userId, it) }
                ?: return@query FocusStudyChannelResult.NoStudyGroup
            if (row.enabled && row.entertainmentGroupId != null) {
                if (normalizedKey in channelsOf(userId, row.entertainmentGroupId)) {
                    return@query FocusStudyChannelResult.EntertainmentConflict
                }
            }
            val alreadySubscribed = SubscriptionsTable.select(SubscriptionsTable.channelUrl)
                .where { SubscriptionsTable.userId eq userId }
                .any { normalizeChannelKey(it[SubscriptionsTable.channelUrl]) == normalizedKey }
            if (!alreadySubscribed) {
                SubscriptionsTable.insert {
                    it[SubscriptionsTable.userId] = userId
                    it[channelUrl] = canonicalUrl
                    it[name] = rawName.trim().ifBlank { canonicalUrl }
                    it[avatarUrl] = rawAvatarUrl.trim()
                    it[subscribedAt] = now
                }
            }
            val existingAddedAt = SubscriptionGroupMembershipsTable
                .select(SubscriptionGroupMembershipsTable.channelUrl, SubscriptionGroupMembershipsTable.addedAt)
                .where {
                    (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and
                        (SubscriptionGroupMembershipsTable.userId eq userId)
                }
                .firstOrNull { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) == normalizedKey }
                ?.get(SubscriptionGroupMembershipsTable.addedAt)
            val effectiveAddedAt = existingAddedAt ?: now.also {
                SubscriptionGroupMembershipsTable.insert {
                    it[SubscriptionGroupMembershipsTable.groupId] = studyGroupId
                    it[SubscriptionGroupMembershipsTable.userId] = userId
                    it[SubscriptionGroupMembershipsTable.channelUrl] = canonicalUrl
                    it[addedAt] = now
                }
            }
            val coolingOff = row.enabled && effectiveAddedAt > row.startedAt && (now - effectiveAddedAt) < STUDY_ADD_DELAY_MS
            FocusStudyChannelResult.Success(
                FocusStudyChannelResponse(
                    channelUrl = canonicalUrl,
                    status = if (coolingOff) "pending" else "active",
                    unlockAt = if (coolingOff) effectiveAddedAt + STUDY_ADD_DELAY_MS else null,
                ),
            )
        }
    }

    suspend fun removeStudyChannel(userId: String, rawChannelUrl: String): FocusStudyChannelRemoveResult {
        val trimmed = rawChannelUrl.trim()
        val isHttpUrl = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        if (trimmed.isEmpty() || trimmed.length > MAX_CHANNEL_URL_LENGTH || !isHttpUrl) {
            return FocusStudyChannelRemoveResult.InvalidChannel
        }
        val targetKey = normalizeChannelKey(trimmed)
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val row = settled(userId)
            val studyGroupId = row.studyGroupId?.takeIf { SubscriptionGroupQueries.exists(userId, it) }
                ?: return@query FocusStudyChannelRemoveResult.NoStudyGroup
            val matchingRows = SubscriptionGroupMembershipsTable
                .select(SubscriptionGroupMembershipsTable.channelUrl, SubscriptionGroupMembershipsTable.addedAt)
                .where {
                    (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and
                        (SubscriptionGroupMembershipsTable.userId eq userId)
                }
                .filter { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) == targetKey }
            if (matchingRows.isEmpty()) return@query FocusStudyChannelRemoveResult.NotFound
            val matchingUrls = matchingRows.map { it[SubscriptionGroupMembershipsTable.channelUrl] }
            val removedAddedAts = matchingRows.mapTo(hashSetOf()) { it[SubscriptionGroupMembershipsTable.addedAt] }
            val deleted = SubscriptionGroupMembershipsTable.deleteWhere {
                (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and
                    (SubscriptionGroupMembershipsTable.userId eq userId) and
                    (SubscriptionGroupMembershipsTable.channelUrl inList matchingUrls)
            }
            if (deleted <= 0) return@query FocusStudyChannelRemoveResult.NotFound
            deleteAutoCreatedSubscriptionIfUnused(userId, targetKey, removedAddedAts)
            FocusStudyChannelRemoveResult.Success
        }
    }

    /**
     * Undoes the auto-subscription created by [addStudyChannel] when cancelling a study addition,
     * while preserving pre-existing subscriptions or channels still belonging to other groups.
     */
    private fun deleteAutoCreatedSubscriptionIfUnused(
        userId: String,
        targetKey: String,
        removedAddedAts: Set<Long>,
    ) {
        val stillGrouped = SubscriptionGroupMembershipsTable
            .select(SubscriptionGroupMembershipsTable.channelUrl)
            .where { SubscriptionGroupMembershipsTable.userId eq userId }
            .any { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) == targetKey }
        if (stillGrouped) return
        val autoSubscribedUrls = SubscriptionsTable
            .select(SubscriptionsTable.channelUrl, SubscriptionsTable.subscribedAt)
            .where { SubscriptionsTable.userId eq userId }
            .filter {
                normalizeChannelKey(it[SubscriptionsTable.channelUrl]) == targetKey &&
                    it[SubscriptionsTable.subscribedAt] in removedAddedAts
            }
            .map { it[SubscriptionsTable.channelUrl] }
        if (autoSubscribedUrls.isNotEmpty()) {
            SubscriptionsTable.deleteWhere {
                (SubscriptionsTable.userId eq userId) and
                    (SubscriptionsTable.channelUrl inList autoSubscribedUrls)
            }
        }
    }

    private fun isActiveRow(row: FocusSettingsRow, now: Long): Boolean =
        row.enabled && row.entertainmentGroupId != null && FocusSchedule.isActive(row.schedule, row.timezone, now)

    private fun partitionStudyChannels(
        userId: String,
        row: FocusSettingsRow,
        now: Long,
        entertainmentChannels: Set<String>,
    ): StudyPartition {
        val studyGroupId = row.studyGroupId ?: return StudyPartition()
        val active = HashSet<String>()
        val pending = HashMap<String, Long>()
        SubscriptionGroupMembershipsTable
            .select(SubscriptionGroupMembershipsTable.channelUrl, SubscriptionGroupMembershipsTable.addedAt)
            .where { (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
            .forEach { membership ->
                val key = normalizeChannelKey(membership[SubscriptionGroupMembershipsTable.channelUrl])
                if (key in entertainmentChannels) return@forEach
                val addedAt = membership[SubscriptionGroupMembershipsTable.addedAt]
                if (row.enabled && addedAt > row.startedAt && (now - addedAt) < STUDY_ADD_DELAY_MS) {
                    val unlockAt = addedAt + STUDY_ADD_DELAY_MS
                    val previous = pending[key]
                    if (previous == null || unlockAt < previous) pending[key] = unlockAt
                } else {
                    active.add(key)
                }
            }
        pending.keys.removeAll(active)
        return StudyPartition(active, pending)
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
            grayscaleThumbnails = request.grayscaleThumbnails ?: current.grayscaleThumbnails,
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
        grayscaleThumbnails = this[FocusSettingsTable.grayscaleThumbnails],
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
            it[grayscaleThumbnails] = row.grayscaleThumbnails
            it[schedule] = row.schedule
            it[timezone] = row.timezone
            it[startedAt] = row.startedAt
            it[disableAt] = row.disableAt
            it[updatedAt] = now
        }
    }

    companion object {
        const val EXIT_DELAY_MS = 24 * 60 * 60 * 1000L
        const val STUDY_ADD_DELAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_CHANNEL_URL_LENGTH = 512
        private val DAY_RANGE = 1..365
    }
}

private data class StudyPartition(
    val active: Set<String> = emptySet(),
    val pending: Map<String, Long> = emptyMap(),
)

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
    val grayscaleThumbnails: Boolean = true,
    val schedule: String = FocusSchedule.ALWAYS,
    val timezone: String? = null,
    val startedAt: Long = 0,
    val disableAt: Long? = null,
) {
    fun toItem(
        now: Long,
        active: Boolean = false,
        focusAiEnabled: Boolean = false,
        studyChannelKeys: List<String> = emptyList(),
    ) = FocusSettingsItem(
        enabled = enabled,
        entertainmentGroupId = entertainmentGroupId,
        studyGroupId = studyGroupId,
        schedule = schedule,
        timezone = timezone,
        transitionDays = transitionDays,
        archiveDays = archiveDays,
        grayscaleThumbnails = grayscaleThumbnails,
        startedAt = startedAt.takeIf { enabled },
        disableAt = disableAt,
        entertainmentShare = if (enabled) FocusProfile.share(startedAt, transitionDays, now) else 1.0,
        active = active,
        focusAiEnabled = focusAiEnabled,
        studyChannelKeys = studyChannelKeys,
    )
}
