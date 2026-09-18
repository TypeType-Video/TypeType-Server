package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeSessionPairingsTable
import dev.typetype.server.db.tables.YoutubeSessionsTable
import dev.typetype.server.models.YoutubeSessionStatusResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeSessionStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun complete(
        code: String,
        encryptedCookies: String,
        encryptedPoToken: String,
        authUser: Int,
    ): YoutubeSessionCompleteResult {
        val now = nowMillis()
        return DatabaseFactory.query {
            val pairing = YoutubeSessionPairingsTable.selectAll()
                .where { YoutubeSessionPairingsTable.code eq code }
                .singleOrNull() ?: return@query YoutubeSessionCompleteResult.InvalidCode
            if (pairing[YoutubeSessionPairingsTable.expiresAt] <= now) {
                YoutubeSessionPairingsTable.deleteWhere { YoutubeSessionPairingsTable.code eq code }
                return@query YoutubeSessionCompleteResult.ExpiredCode
            }
            upsertSession(pairing[YoutubeSessionPairingsTable.userId], encryptedCookies, encryptedPoToken, authUser, now)
            YoutubeSessionPairingsTable.deleteWhere { YoutubeSessionPairingsTable.code eq code }
            YoutubeSessionCompleteResult.Completed
        }
    }
    suspend fun status(userId: String): YoutubeSessionStatusResponse = DatabaseFactory.query {
        YoutubeSessionsTable.selectAll()
            .where { YoutubeSessionsTable.userId eq userId }
            .singleOrNull()
            ?.let {
                YoutubeSessionStatusResponse(
                    status = YoutubeSessionStatus.from(it[YoutubeSessionsTable.status]).value,
                    updatedAt = it[YoutubeSessionsTable.updatedAt],
                    lastUsedAt = it[YoutubeSessionsTable.lastUsedAt],
                )
            }
            ?: YoutubeSessionStatusResponse(YoutubeSessionStatus.Disconnected.value, 0, 0)
    }
    suspend fun delete(userId: String): Boolean = DatabaseFactory.query {
        YoutubeSessionsTable.deleteWhere { YoutubeSessionsTable.userId eq userId } > 0
    }

    suspend fun completeForUser(
        userId: String,
        encryptedCookies: String,
        encryptedPoToken: String,
        authUser: Int,
    ): Unit =
        DatabaseFactory.query {
            upsertSession(userId, encryptedCookies, encryptedPoToken, authUser, nowMillis())
        }

    suspend fun connectedEncrypted(userId: String): EncryptedYoutubeSessionCredentials? = DatabaseFactory.query {
        YoutubeSessionsTable.selectAll()
            .where { YoutubeSessionsTable.userId eq userId }
            .singleOrNull()
            ?.takeIf { YoutubeSessionStatus.from(it[YoutubeSessionsTable.status]) == YoutubeSessionStatus.Connected }
            ?.let {
                EncryptedYoutubeSessionCredentials(
                    cookies = it[YoutubeSessionsTable.encryptedCookies],
                    poToken = it[YoutubeSessionsTable.encryptedPoToken],
                    authUser = it[YoutubeSessionsTable.authUser],
                )
            }
    }

    suspend fun markUsed(userId: String): Unit = DatabaseFactory.query {
        YoutubeSessionsTable.update({ YoutubeSessionsTable.userId eq userId }) { it[lastUsedAt] = nowMillis() }
    }

    suspend fun markNeedsReconnect(userId: String): Unit = DatabaseFactory.query {
        val now = nowMillis()
        YoutubeSessionsTable.update({ YoutubeSessionsTable.userId eq userId }) {
            it[status] = YoutubeSessionStatus.NeedsReconnect.value
            it[updatedAt] = now
            it[lastUsedAt] = now
        }
    }

    private fun upsertSession(
        userId: String,
        encryptedCookies: String,
        encryptedPoToken: String,
        authUser: Int,
        now: Long,
    ) {
        val updated = YoutubeSessionsTable.update({ YoutubeSessionsTable.userId eq userId }) {
            it[YoutubeSessionsTable.encryptedCookies] = encryptedCookies
            it[YoutubeSessionsTable.encryptedPoToken] = encryptedPoToken
            it[YoutubeSessionsTable.authUser] = authUser
            it[status] = YoutubeSessionStatus.Connected.value
            it[updatedAt] = now
            it[lastUsedAt] = 0
        }
        if (updated == 0) insertSession(userId, encryptedCookies, encryptedPoToken, authUser, now)
    }

    private fun insertSession(
        userId: String,
        encryptedCookies: String,
        encryptedPoToken: String,
        authUser: Int,
        now: Long,
    ) {
        YoutubeSessionsTable.insert {
            it[YoutubeSessionsTable.userId] = userId
            it[YoutubeSessionsTable.encryptedCookies] = encryptedCookies
            it[YoutubeSessionsTable.encryptedPoToken] = encryptedPoToken
            it[YoutubeSessionsTable.authUser] = authUser
            it[status] = YoutubeSessionStatus.Connected.value
            it[createdAt] = now
            it[updatedAt] = now
            it[lastUsedAt] = 0
        }
    }
}

data class EncryptedYoutubeSessionCredentials(
    val cookies: String,
    val poToken: String,
    val authUser: Int,
)
