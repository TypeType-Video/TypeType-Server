package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.BiliBiliSessionsTable
import dev.typetype.server.models.BiliBiliSessionStatusResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class BiliBiliSessionStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun completeForUser(
        userId: String,
        encryptedCookies: String,
        expiresAt: Long,
    ): Unit = DatabaseFactory.query {
        upsertSession(userId, encryptedCookies, expiresAt, nowMillis())
    }

    suspend fun status(userId: String): BiliBiliSessionStatusResponse = DatabaseFactory.query {
        BiliBiliSessionsTable.selectAll()
            .where { BiliBiliSessionsTable.userId eq userId }
            .singleOrNull()
            ?.let {
                BiliBiliSessionStatusResponse(
                    status = BiliBiliSessionStatus.from(it[BiliBiliSessionsTable.status]).value,
                    updatedAt = it[BiliBiliSessionsTable.updatedAt],
                    lastUsedAt = it[BiliBiliSessionsTable.lastUsedAt],
                    expiresAt = it[BiliBiliSessionsTable.expiresAt],
                )
            }
            ?: BiliBiliSessionStatusResponse(BiliBiliSessionStatus.Disconnected.value, 0, 0)
    }

    suspend fun delete(userId: String): Boolean = DatabaseFactory.query {
        BiliBiliSessionsTable.deleteWhere { BiliBiliSessionsTable.userId eq userId } > 0
    }

    suspend fun connectedEncrypted(userId: String): String? = DatabaseFactory.query {
        BiliBiliSessionsTable.selectAll()
            .where { BiliBiliSessionsTable.userId eq userId }
            .singleOrNull()
            ?.takeIf { BiliBiliSessionStatus.from(it[BiliBiliSessionsTable.status]) == BiliBiliSessionStatus.Connected }
            ?.get(BiliBiliSessionsTable.encryptedCookies)
    }

    suspend fun markUsed(userId: String): Unit = DatabaseFactory.query {
        BiliBiliSessionsTable.update({ BiliBiliSessionsTable.userId eq userId }) { it[lastUsedAt] = nowMillis() }
    }

    suspend fun markNeedsReconnect(userId: String): Unit = DatabaseFactory.query {
        val now = nowMillis()
        BiliBiliSessionsTable.update({ BiliBiliSessionsTable.userId eq userId }) {
            it[status] = BiliBiliSessionStatus.NeedsReconnect.value
            it[updatedAt] = now
            it[lastUsedAt] = now
        }
    }

    private fun upsertSession(
        userId: String,
        encryptedCookies: String,
        expiresAt: Long,
        now: Long,
    ) {
        val updated = BiliBiliSessionsTable.update({ BiliBiliSessionsTable.userId eq userId }) {
            it[BiliBiliSessionsTable.encryptedCookies] = encryptedCookies
            it[BiliBiliSessionsTable.status] = BiliBiliSessionStatus.Connected.value
            it[BiliBiliSessionsTable.updatedAt] = now
            it[BiliBiliSessionsTable.lastUsedAt] = 0
            it[BiliBiliSessionsTable.expiresAt] = expiresAt
        }
        if (updated == 0) {
            BiliBiliSessionsTable.insert {
                it[BiliBiliSessionsTable.userId] = userId
                it[BiliBiliSessionsTable.encryptedCookies] = encryptedCookies
                it[BiliBiliSessionsTable.status] = BiliBiliSessionStatus.Connected.value
                it[BiliBiliSessionsTable.createdAt] = now
                it[BiliBiliSessionsTable.updatedAt] = now
                it[BiliBiliSessionsTable.lastUsedAt] = 0
                it[BiliBiliSessionsTable.expiresAt] = expiresAt
            }
        }
    }
}
