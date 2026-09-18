package dev.typetype.server.services

import dev.typetype.server.db.tables.SessionsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class AuthSessionStore {
    fun findByRefreshHash(refreshHash: String): SessionRecord? = transaction {
        SessionsTable.selectAll().where { SessionsTable.refreshTokenHash eq refreshHash }
            .singleOrNull()?.toRecord()
    }

    fun findBySessionId(sessionId: String): SessionRecord? = transaction {
        SessionsTable.selectAll().where { SessionsTable.id eq sessionId }
            .singleOrNull()?.toRecord()
    }

    fun upsert(sessionId: String, userId: String, accessToken: String, refreshHash: String, now: Long, expiresAt: Long): Boolean = transaction {
        val existing = SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull()
        if (existing != null && existing[SessionsTable.userId] != userId) return@transaction false
        if (existing == null) {
            SessionsTable.insert {
                it[SessionsTable.id] = sessionId
                it[SessionsTable.userId] = userId
                it[SessionsTable.token] = accessToken
                it[SessionsTable.refreshTokenHash] = refreshHash
                it[SessionsTable.createdAt] = now
                it[SessionsTable.expiresAt] = expiresAt
                it[SessionsTable.revokedAt] = null
            }
        } else {
            rotate(sessionId, accessToken, refreshHash, expiresAt)
        }
        true
    }

    fun rotate(sessionId: String, accessToken: String, refreshHash: String, expiresAt: Long) {
        transaction {
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[SessionsTable.token] = accessToken
                it[SessionsTable.refreshTokenHash] = refreshHash
                it[SessionsTable.expiresAt] = expiresAt
                it[SessionsTable.revokedAt] = null
            }
        }
    }

    fun revokeByRefreshHash(refreshHash: String, revokedAt: Long) = transaction {
        SessionsTable.update({ SessionsTable.refreshTokenHash eq refreshHash }) {
            it[SessionsTable.revokedAt] = revokedAt
        }
    }

    data class SessionRecord(val sessionId: String, val userId: String, val expiresAt: Long, val revokedAt: Long?)
}

private fun org.jetbrains.exposed.v1.core.ResultRow.toRecord(): AuthSessionStore.SessionRecord =
    AuthSessionStore.SessionRecord(this[SessionsTable.id], this[SessionsTable.userId], this[SessionsTable.expiresAt], this[SessionsTable.revokedAt])
