package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.FocusStudyChannelResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select

class FocusStudyChannelService(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun addStudyChannel(
        userId: String,
        rawChannelUrl: String,
        rawName: String = "",
        rawAvatarUrl: String = "",
    ): FocusStudyChannelResult {
        val canonicalUrl = validCanonicalChannelUrl(rawChannelUrl) ?: return FocusStudyChannelResult.InvalidChannel
        val normalizedKey = normalizeChannelKey(canonicalUrl)
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val now = clock()
            val row = readSettledFocusRow(userId, now)
            val studyGroupId = row.studyGroupId?.takeIf { SubscriptionGroupQueries.exists(userId, it) }
                ?: return@query FocusStudyChannelResult.NoStudyGroup
            if (row.enabled && row.entertainmentGroupId != null && normalizedKey in channelsOf(userId, row.entertainmentGroupId)) {
                return@query FocusStudyChannelResult.EntertainmentConflict
            }
            ensureSubscribed(userId, canonicalUrl, normalizedKey, rawName, rawAvatarUrl, now)
            val effectiveAddedAt = existingAddedAt(userId, studyGroupId, normalizedKey) ?: now.also {
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
        val canonicalUrl = validCanonicalChannelUrl(rawChannelUrl) ?: return FocusStudyChannelRemoveResult.InvalidChannel
        val targetKey = normalizeChannelKey(canonicalUrl)
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            val row = readSettledFocusRow(userId, clock())
            val studyGroupId = row.studyGroupId?.takeIf { SubscriptionGroupQueries.exists(userId, it) }
                ?: return@query FocusStudyChannelRemoveResult.NoStudyGroup
            val matchingRows = SubscriptionGroupMembershipsTable
                .select(SubscriptionGroupMembershipsTable.channelUrl, SubscriptionGroupMembershipsTable.addedAt)
                .where { (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
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

    internal fun partitionStudyChannels(
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

    internal fun channelsOf(userId: String, groupId: String): Set<String> =
        SubscriptionGroupMembershipsTable.select(SubscriptionGroupMembershipsTable.channelUrl)
            .where { (SubscriptionGroupMembershipsTable.groupId eq groupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
            .mapTo(HashSet()) { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) }

    private fun validCanonicalChannelUrl(rawChannelUrl: String): String? {
        val trimmed = rawChannelUrl.trim()
        val isHttp = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        return trimmed.takeIf { it.isNotEmpty() && it.length <= MAX_CHANNEL_URL_LENGTH && isHttp }
            ?.let(ChannelUrlCanonicalizer::canonicalize)
    }

    private fun ensureSubscribed(userId: String, canonicalUrl: String, normalizedKey: String, rawName: String, rawAvatarUrl: String, now: Long) {
        val exists = SubscriptionsTable.select(SubscriptionsTable.channelUrl)
            .where { SubscriptionsTable.userId eq userId }
            .any { normalizeChannelKey(it[SubscriptionsTable.channelUrl]) == normalizedKey }
        if (!exists) {
            SubscriptionsTable.insert {
                it[SubscriptionsTable.userId] = userId
                it[channelUrl] = canonicalUrl
                it[name] = rawName.trim().ifBlank { canonicalUrl }
                it[avatarUrl] = rawAvatarUrl.trim()
                it[subscribedAt] = now
            }
        }
    }

    private fun existingAddedAt(userId: String, studyGroupId: String, normalizedKey: String): Long? =
        SubscriptionGroupMembershipsTable
            .select(SubscriptionGroupMembershipsTable.channelUrl, SubscriptionGroupMembershipsTable.addedAt)
            .where { (SubscriptionGroupMembershipsTable.groupId eq studyGroupId) and (SubscriptionGroupMembershipsTable.userId eq userId) }
            .firstOrNull { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) == normalizedKey }
            ?.get(SubscriptionGroupMembershipsTable.addedAt)

    private fun deleteAutoCreatedSubscriptionIfUnused(userId: String, targetKey: String, removedAddedAts: Set<Long>) {
        val stillGrouped = SubscriptionGroupMembershipsTable.select(SubscriptionGroupMembershipsTable.channelUrl)
            .where { SubscriptionGroupMembershipsTable.userId eq userId }
            .any { normalizeChannelKey(it[SubscriptionGroupMembershipsTable.channelUrl]) == targetKey }
        if (stillGrouped) return
        val autoSubscribedUrls = SubscriptionsTable.select(SubscriptionsTable.channelUrl, SubscriptionsTable.subscribedAt)
            .where { SubscriptionsTable.userId eq userId }
            .filter { normalizeChannelKey(it[SubscriptionsTable.channelUrl]) == targetKey && it[SubscriptionsTable.subscribedAt] in removedAddedAts }
            .map { it[SubscriptionsTable.channelUrl] }
        if (autoSubscribedUrls.isNotEmpty()) {
            SubscriptionsTable.deleteWhere {
                (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.channelUrl inList autoSubscribedUrls)
            }
        }
    }

    companion object {
        const val STUDY_ADD_DELAY_MS = 24 * 60 * 60 * 1000L
        private const val MAX_CHANNEL_URL_LENGTH = 512
    }
}
