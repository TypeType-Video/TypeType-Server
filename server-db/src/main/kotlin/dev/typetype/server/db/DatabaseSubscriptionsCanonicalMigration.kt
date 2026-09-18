package dev.typetype.server.db

import dev.typetype.server.db.tables.SubscriptionsTable
import dev.typetype.server.services.ChannelUrlCanonicalizer
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

object DatabaseSubscriptionsCanonicalMigration {
    fun apply() {
        val rows = SubscriptionsTable.selectAll().map {
            SubscriptionRow(
                userId = it[SubscriptionsTable.userId],
                channelUrl = it[SubscriptionsTable.channelUrl],
                name = it[SubscriptionsTable.name],
                avatarUrl = it[SubscriptionsTable.avatarUrl],
                subscribedAt = it[SubscriptionsTable.subscribedAt],
            )
        }
        if (rows.isEmpty()) return
        val normalized = rows.map { row ->
            row.copy(channelUrl = ChannelUrlCanonicalizer.canonicalize(row.channelUrl))
        }
        val deduped = normalized
            .groupBy { it.userId to it.channelUrl }
            .values
            .map { group ->
                group.maxWith(compareBy<SubscriptionRow> { it.subscribedAt }
                    .thenBy { it.name }
                    .thenBy { it.avatarUrl })
            }
        val needsCanonicalization = rows.zip(normalized).any { (before, after) -> before.channelUrl != after.channelUrl }
        val needsDeduplication = deduped.size != normalized.size
        if (!needsCanonicalization && !needsDeduplication) return
        SubscriptionsTable.deleteAll()
        deduped.forEach { row ->
            SubscriptionsTable.insert {
                it[SubscriptionsTable.userId] = row.userId
                it[SubscriptionsTable.channelUrl] = row.channelUrl
                it[SubscriptionsTable.name] = row.name
                it[SubscriptionsTable.avatarUrl] = row.avatarUrl
                it[SubscriptionsTable.subscribedAt] = row.subscribedAt
            }
        }
    }

    private data class SubscriptionRow(
        val userId: String,
        val channelUrl: String,
        val name: String,
        val avatarUrl: String,
        val subscribedAt: Long,
    )
}
