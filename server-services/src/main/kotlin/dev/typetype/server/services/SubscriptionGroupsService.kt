package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import dev.typetype.server.db.tables.SubscriptionGroupsTable
import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.models.SubscriptionGroupItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.sql.SQLException
import java.util.Locale
import java.util.UUID

class SubscriptionGroupsService {
    suspend fun getAll(userId: String): List<SubscriptionGroupItem> = DatabaseFactory.query {
        val counts = SubscriptionGroupMembershipsTable.selectAll()
            .where { SubscriptionGroupMembershipsTable.userId eq userId }
            .groupingBy { it[SubscriptionGroupMembershipsTable.groupId] }
            .eachCount()
        SubscriptionGroupsTable.selectAll()
            .where { SubscriptionGroupsTable.userId eq userId }
            .orderBy(SubscriptionGroupsTable.createdAt to SortOrder.DESC)
            .map { it.toItem(counts[it[SubscriptionGroupsTable.id]] ?: 0) }
    }

    suspend fun exists(userId: String, groupId: String): Boolean = DatabaseFactory.query {
        groupExists(userId, groupId)
    }

    suspend fun create(userId: String, rawName: String): SubscriptionGroupWriteResult {
        val name = normalizeDisplayName(rawName) ?: return SubscriptionGroupWriteResult.InvalidName
        val normalizedName = normalizeUniqueName(name)
        return DatabaseFactory.query {
            SubscriptionMutationLock.acquire(userId)
            if (nameExists(userId, normalizedName)) return@query SubscriptionGroupWriteResult.DuplicateName
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val inserted = SubscriptionGroupsTable.insertIgnore {
                it[SubscriptionGroupsTable.id] = id
                it[SubscriptionGroupsTable.userId] = userId
                it[SubscriptionGroupsTable.name] = name
                it[SubscriptionGroupsTable.normalizedName] = normalizedName
                it[createdAt] = now
                it[updatedAt] = now
            }.insertedCount
            if (inserted == 0) SubscriptionGroupWriteResult.DuplicateName else {
                SubscriptionGroupWriteResult.Success(SubscriptionGroupItem(id, name, 0, now, now))
            }
        }
    }

    suspend fun rename(userId: String, groupId: String, rawName: String): SubscriptionGroupWriteResult {
        val name = normalizeDisplayName(rawName) ?: return SubscriptionGroupWriteResult.InvalidName
        val normalizedName = normalizeUniqueName(name)
        return try {
            DatabaseFactory.query {
                SubscriptionMutationLock.acquire(userId)
                val current = SubscriptionGroupsTable.selectAll().where {
                    (SubscriptionGroupsTable.id eq groupId) and (SubscriptionGroupsTable.userId eq userId)
                }.singleOrNull() ?: return@query SubscriptionGroupWriteResult.NotFound
                val duplicate = SubscriptionGroupsTable.selectAll().where {
                    (SubscriptionGroupsTable.userId eq userId) and
                        (SubscriptionGroupsTable.normalizedName eq normalizedName)
                }.any { it[SubscriptionGroupsTable.id] != groupId }
                if (duplicate) return@query SubscriptionGroupWriteResult.DuplicateName
                val now = System.currentTimeMillis()
                SubscriptionGroupsTable.update({
                    (SubscriptionGroupsTable.id eq groupId) and (SubscriptionGroupsTable.userId eq userId)
                }) {
                    it[SubscriptionGroupsTable.name] = name
                    it[SubscriptionGroupsTable.normalizedName] = normalizedName
                    it[updatedAt] = now
                }
                val count = membershipCount(userId, groupId)
                SubscriptionGroupWriteResult.Success(
                    SubscriptionGroupItem(groupId, name, count, current[SubscriptionGroupsTable.createdAt], now),
                )
            }
        } catch (error: Throwable) {
            if (error.isUniqueConstraintViolation()) SubscriptionGroupWriteResult.DuplicateName else throw error
        }
    }

    suspend fun delete(userId: String, groupId: String): Boolean = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        if (!groupExists(userId, groupId)) return@query false
        SubscriptionGroupMembershipsTable.deleteWhere {
            (SubscriptionGroupMembershipsTable.groupId eq groupId) and
                (SubscriptionGroupMembershipsTable.userId eq userId)
        }
        SubscriptionGroupsTable.deleteWhere {
            (SubscriptionGroupsTable.id eq groupId) and (SubscriptionGroupsTable.userId eq userId)
        } > 0
    }

    suspend fun addSubscription(
        userId: String,
        groupId: String,
        rawChannelUrl: String,
    ): SubscriptionGroupMembershipResult = addSubscriptions(userId, groupId, listOf(rawChannelUrl))

    suspend fun addSubscriptions(
        userId: String,
        groupId: String,
        rawChannelUrls: List<String>,
    ): SubscriptionGroupMembershipResult = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        if (!groupExists(userId, groupId)) return@query SubscriptionGroupMembershipResult.GroupNotFound
        val channelUrls = rawChannelUrls.mapTo(linkedSetOf(), ChannelUrlCanonicalizer::canonicalize)
        val subscribed = SubscriptionsTable.selectAll().where {
            (SubscriptionsTable.userId eq userId) and (SubscriptionsTable.channelUrl inList channelUrls)
        }.mapTo(hashSetOf()) { it[SubscriptionsTable.channelUrl] }
        if (subscribed.size != channelUrls.size) return@query SubscriptionGroupMembershipResult.SubscriptionNotFound
        val existing = SubscriptionGroupMembershipsTable.selectAll().where {
            (SubscriptionGroupMembershipsTable.groupId eq groupId) and
                (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.channelUrl inList channelUrls)
        }.mapTo(hashSetOf()) { it[SubscriptionGroupMembershipsTable.channelUrl] }
        val addedAt = System.currentTimeMillis()
        SubscriptionGroupMembershipsTable.batchInsert(channelUrls - existing, shouldReturnGeneratedValues = false) { url ->
            this[SubscriptionGroupMembershipsTable.groupId] = groupId
            this[SubscriptionGroupMembershipsTable.userId] = userId
            this[SubscriptionGroupMembershipsTable.channelUrl] = url
            this[SubscriptionGroupMembershipsTable.addedAt] = addedAt
        }
        SubscriptionGroupMembershipResult.Success
    }

    suspend fun removeSubscription(
        userId: String,
        groupId: String,
        rawChannelUrl: String,
    ): SubscriptionGroupMembershipResult = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        if (!groupExists(userId, groupId)) return@query SubscriptionGroupMembershipResult.GroupNotFound
        val channelUrl = ChannelUrlCanonicalizer.canonicalize(rawChannelUrl)
        val deleted = SubscriptionGroupMembershipsTable.deleteWhere {
            (SubscriptionGroupMembershipsTable.groupId eq groupId) and
                (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.channelUrl eq channelUrl)
        }
        if (deleted > 0) SubscriptionGroupMembershipResult.Success else {
            SubscriptionGroupMembershipResult.MembershipNotFound
        }
    }

    suspend fun removeSubscriptions(
        userId: String,
        groupId: String,
        rawChannelUrls: List<String>,
    ): SubscriptionGroupMembershipResult = DatabaseFactory.query {
        SubscriptionMutationLock.acquire(userId)
        if (!groupExists(userId, groupId)) return@query SubscriptionGroupMembershipResult.GroupNotFound
        val channelUrls = rawChannelUrls.mapTo(hashSetOf(), ChannelUrlCanonicalizer::canonicalize)
        SubscriptionGroupMembershipsTable.deleteWhere {
            (SubscriptionGroupMembershipsTable.groupId eq groupId) and
                (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.channelUrl inList channelUrls)
        }
        SubscriptionGroupMembershipResult.Success
    }

    suspend fun getChannelUrls(userId: String, groupId: String): List<String> = DatabaseFactory.query {
        SubscriptionGroupMembershipsTable.selectAll().where {
            (SubscriptionGroupMembershipsTable.groupId eq groupId) and
                (SubscriptionGroupMembershipsTable.userId eq userId)
        }.orderBy(SubscriptionGroupMembershipsTable.addedAt to SortOrder.DESC)
            .map { it[SubscriptionGroupMembershipsTable.channelUrl] }
    }

    private fun groupExists(userId: String, groupId: String): Boolean =
        SubscriptionGroupsTable.selectAll().where {
            (SubscriptionGroupsTable.id eq groupId) and (SubscriptionGroupsTable.userId eq userId)
        }.any()

    private fun nameExists(userId: String, normalizedName: String): Boolean =
        SubscriptionGroupsTable.selectAll().where {
            (SubscriptionGroupsTable.userId eq userId) and
                (SubscriptionGroupsTable.normalizedName eq normalizedName)
        }.any()

    private fun membershipCount(userId: String, groupId: String): Int =
        SubscriptionGroupMembershipsTable.selectAll().where {
            (SubscriptionGroupMembershipsTable.userId eq userId) and
                (SubscriptionGroupMembershipsTable.groupId eq groupId)
        }.count().toInt()

    private fun ResultRow.toItem(channelCount: Int): SubscriptionGroupItem = SubscriptionGroupItem(
        id = this[SubscriptionGroupsTable.id],
        name = this[SubscriptionGroupsTable.name],
        channelCount = channelCount,
        createdAt = this[SubscriptionGroupsTable.createdAt],
        updatedAt = this[SubscriptionGroupsTable.updatedAt],
    )

    private fun normalizeDisplayName(value: String): String? =
        value.trim().takeIf { it.length in 1..MAX_GROUP_NAME_LENGTH }

    private fun normalizeUniqueName(value: String): String = value.lowercase(Locale.ROOT)

    private fun Throwable.isUniqueConstraintViolation(): Boolean = generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .any { it.sqlState == UNIQUE_VIOLATION_SQL_STATE }

    companion object {
        const val MAX_GROUP_NAME_LENGTH = 100
        const val MAX_MEMBERSHIP_CHANNELS = 500
        private const val UNIQUE_VIOLATION_SQL_STATE = "23505"
    }
}
