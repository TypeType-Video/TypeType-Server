package dev.typetype.server.services

import com.password4j.Password
import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class OidcUserService(private val authService: AuthService) {
    suspend fun login(identity: OidcIdentity): AuthSessionTokens {
        val userId = transaction { resolveUserId(identity) }
        return authService.issueSessionForOwner(userId) ?: throw IllegalStateException("Failed to create session")
    }

    private fun resolveUserId(identity: OidcIdentity): String {
        findByOidc(identity)?.let { return it }
        findByEmail(identity)?.let {
            linkUser(userId = it, identity = identity)
            return it
        }
        return createUser(identity)
    }

    private fun findByOidc(identity: OidcIdentity): String? = UsersTable.selectAll()
        .where { (UsersTable.oidcIssuer eq identity.issuer) and (UsersTable.oidcSubject eq identity.subject) }
        .singleOrNull()
        ?.get(UsersTable.id)

    private fun findByEmail(identity: OidcIdentity): String? = UsersTable.selectAll()
        .where { UsersTable.email eq identity.email }
        .singleOrNull()
        ?.get(UsersTable.id)

    private fun linkUser(userId: String, identity: OidcIdentity) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[oidcIssuer] = identity.issuer
            it[oidcSubject] = identity.subject
            it[updatedAt] = System.currentTimeMillis()
        }
    }

    private fun createUser(identity: OidcIdentity): String {
        val userId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val role = if (UsersTable.selectAll().empty()) "admin" else "user"
        UsersTable.insert {
            it[id] = userId
            it[email] = identity.email
            it[passwordHash] = Password.hash(UUID.randomUUID().toString()).withArgon2().result
            it[name] = identity.name
            it[UsersTable.role] = role
            it[oidcIssuer] = identity.issuer
            it[oidcSubject] = identity.subject
            it[verified] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        return userId
    }
}
