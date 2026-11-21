package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.TwitchAccountsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

data class TwitchAccount(
    val accessToken: String,
    val refreshToken: String?,
    val login: String,
    val displayName: String,
    val avatarUrl: String,
    val expiresAt: Long,
)

class TwitchAccountStore(private val crypto: TwitchTokenCrypto) {
    suspend fun get(userId: String): TwitchAccount? = DatabaseFactory.query {
        TwitchAccountsTable.selectAll().where { TwitchAccountsTable.userId eq userId }.firstOrNull()?.let { row ->
            TwitchAccount(
                accessToken = crypto.decrypt(row[TwitchAccountsTable.encryptedAccessToken]),
                refreshToken = row[TwitchAccountsTable.encryptedRefreshToken]?.let(crypto::decrypt),
                login = row[TwitchAccountsTable.login],
                displayName = row[TwitchAccountsTable.displayName],
                avatarUrl = row[TwitchAccountsTable.avatarUrl],
                expiresAt = row[TwitchAccountsTable.expiresAt],
            )
        }
    }

    suspend fun save(userId: String, account: TwitchAccount): Unit = DatabaseFactory.query {
        val now = System.currentTimeMillis()
        TwitchAccountsTable.upsert(onUpdateExclude = listOf(TwitchAccountsTable.createdAt)) {
            it[TwitchAccountsTable.userId] = userId
            it[encryptedAccessToken] = crypto.encrypt(account.accessToken)
            it[encryptedRefreshToken] = account.refreshToken?.let(crypto::encrypt)
            it[login] = account.login
            it[displayName] = account.displayName
            it[avatarUrl] = account.avatarUrl
            it[expiresAt] = account.expiresAt
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    suspend fun delete(userId: String): Boolean = DatabaseFactory.query {
        TwitchAccountsTable.deleteWhere { TwitchAccountsTable.userId eq userId } > 0
    }
}
