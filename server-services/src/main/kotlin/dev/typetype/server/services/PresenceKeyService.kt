package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PresenceKeysTable
import dev.typetype.server.models.PresenceKeyCreatedResponse
import dev.typetype.server.models.PresenceKeyItem
import java.util.UUID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class PresenceTokenPrincipal(
    val keyId: String,
    val userId: String,
)

class PresenceKeyLimitException(message: String) : IllegalStateException(message)

class PresenceKeyService(
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    suspend fun create(userId: String, requestedName: String?): PresenceKeyCreatedResponse {
        val name = requestedName?.trim().orEmpty()
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
            "Presence key name must contain 1 to $MAX_NAME_LENGTH characters"
        }
        return DatabaseFactory.query {
            val activeCount = PresenceKeysTable.selectAll()
                .where { PresenceKeysTable.userId eq userId }
                .count()
            if (activeCount >= MAX_KEYS_PER_USER) {
                throw PresenceKeyLimitException("Presence key limit reached")
            }
            val now = nowProvider()
            val id = UUID.randomUUID().toString()
            val token = PresenceTokenCodec.issue()
            val tokenPrefix = PresenceTokenCodec.prefix(token)
            PresenceKeysTable.insert {
                it[PresenceKeysTable.id] = id
                it[PresenceKeysTable.userId] = userId
                it[PresenceKeysTable.name] = name
                it[PresenceKeysTable.tokenPrefix] = tokenPrefix
                it[PresenceKeysTable.tokenHash] = PresenceTokenCodec.hash(token)
                it[createdAt] = now
                it[lastUsedAt] = null
            }
            PresenceKeyCreatedResponse(
                key = item(id, name, tokenPrefix, now, null),
                token = token,
            )
        }
    }

    suspend fun list(userId: String): List<PresenceKeyItem> = DatabaseFactory.query {
        PresenceKeysTable.selectAll()
            .where { PresenceKeysTable.userId eq userId }
            .sortedBy { it[PresenceKeysTable.createdAt] }
            .map {
                item(
                    id = it[PresenceKeysTable.id],
                    name = it[PresenceKeysTable.name],
                    tokenPrefix = it[PresenceKeysTable.tokenPrefix],
                    createdAt = it[PresenceKeysTable.createdAt],
                    lastUsedAt = it[PresenceKeysTable.lastUsedAt],
                )
            }
    }

    suspend fun revoke(userId: String, keyId: String): Boolean = DatabaseFactory.query {
        PresenceKeysTable.deleteWhere {
            PresenceKeysTable.id eq keyId
            PresenceKeysTable.userId eq userId
        } > 0
    }

    suspend fun resolve(token: String): PresenceTokenPrincipal? {
        if (!token.startsWith(PresenceTokenCodec.PREFIX)) return null
        val hash = PresenceTokenCodec.hash(token)
        val now = nowProvider()
        return DatabaseFactory.query {
            PresenceKeysTable.selectAll()
                .where { PresenceKeysTable.tokenHash eq hash }
                .singleOrNull()
                ?.let {
                    PresenceKeysTable.update({ PresenceKeysTable.id eq it[PresenceKeysTable.id] }) {
                        it[lastUsedAt] = now
                    }
                    PresenceTokenPrincipal(
                        keyId = it[PresenceKeysTable.id],
                        userId = it[PresenceKeysTable.userId],
                    )
                }
        }
    }

    suspend fun hasActiveKey(userId: String): Boolean = DatabaseFactory.query {
        PresenceKeysTable.selectAll()
            .where { PresenceKeysTable.userId eq userId }
            .empty()
            .not()
    }

    private fun item(
        id: String,
        name: String,
        tokenPrefix: String,
        createdAt: Long,
        lastUsedAt: Long?,
    ) = PresenceKeyItem(
        id = id,
        name = name,
        tokenPrefix = tokenPrefix,
        scope = dev.typetype.server.models.PresenceScopes.READ,
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
    )

    private companion object {
        const val MAX_KEYS_PER_USER = 10L
        const val MAX_NAME_LENGTH = 50
    }
}
