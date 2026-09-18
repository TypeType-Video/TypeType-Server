package dev.typetype.server.services

import com.password4j.Password
import dev.typetype.server.db.tables.PasswordResetTable
import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class PasswordResetService {

    fun generateToken(userId: String): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + TOKEN_TTL_MS
        transaction {
            PasswordResetTable.insert {
                it[PasswordResetTable.id] = UUID.randomUUID().toString()
                it[PasswordResetTable.userId] = userId
                it[PasswordResetTable.token] = token
                it[PasswordResetTable.expiresAt] = expiresAt
            }
        }
        return token
    }

    fun resetPassword(token: String, newPassword: String): Boolean {
        val now = System.currentTimeMillis()
        val row = transaction {
            PasswordResetTable.selectAll()
                .where { PasswordResetTable.token eq token }
                .singleOrNull()
        } ?: return false

        if (row[PasswordResetTable.expiresAt] < now) return false
        if (row[PasswordResetTable.usedAt] != null) return false

        val userId = row[PasswordResetTable.userId]
        val hashed = Password.hash(newPassword).withArgon2().result

        transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.passwordHash] = hashed
                it[UsersTable.updatedAt] = now
            }
            PasswordResetTable.update({ PasswordResetTable.token eq token }) {
                it[PasswordResetTable.usedAt] = now
            }
        }
        return true
    }

    companion object {
        private const val TOKEN_TTL_MS = 60 * 60 * 1000L
    }
}
