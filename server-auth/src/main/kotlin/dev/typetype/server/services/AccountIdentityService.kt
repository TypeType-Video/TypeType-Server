package dev.typetype.server.services

import com.password4j.Password
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AccountIdentityItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class AccountIdentityService(private val profileAccountService: ProfileAccountService? = null) {
    suspend fun get(userId: String): AccountIdentityItem? {
        val identityUserId = profileAccountService?.ownerUserId(userId) ?: userId
        return DatabaseFactory.query {
            UsersTable.selectAll().where { UsersTable.id eq identityUserId }.singleOrNull()?.let {
            AccountIdentityItem(
                email = it[UsersTable.email],
                name = it[UsersTable.name],
                managedByOidc = it[UsersTable.oidcIssuer] != null,
            )
            }
        }
    }

    suspend fun updateSelf(
        userId: String,
        email: String,
        name: String,
        currentPassword: String,
    ): AccountIdentityUpdateResult {
        val normalized = validate(email, name) ?: return AccountIdentityUpdateResult.InvalidInput
        val identityUserId = profileAccountService?.ownerUserId(userId) ?: userId
        val credentials = DatabaseFactory.query {
            UsersTable.selectAll().where { UsersTable.id eq identityUserId }.singleOrNull()?.let {
                Credentials(it[UsersTable.passwordHash], it[UsersTable.oidcIssuer] != null)
            }
        } ?: return AccountIdentityUpdateResult.UserNotFound
        if (credentials.managedByOidc) return AccountIdentityUpdateResult.ManagedByOidc
        if (!Password.check(currentPassword, credentials.passwordHash).withArgon2()) {
            return AccountIdentityUpdateResult.InvalidPassword
        }
        return update(identityUserId, normalized)
    }

    suspend fun updateAdmin(userId: String, email: String, name: String): AccountIdentityUpdateResult {
        val normalized = validate(email, name) ?: return AccountIdentityUpdateResult.InvalidInput
        return update(userId, normalized)
    }

    private suspend fun update(userId: String, identity: NormalizedIdentity): AccountIdentityUpdateResult = DatabaseFactory.query {
        val current = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull()
            ?: return@query AccountIdentityUpdateResult.UserNotFound
        val taken = UsersTable.selectAll().where {
            (UsersTable.email.lowerCase() eq identity.email) and (UsersTable.id neq userId)
        }.empty().not()
        if (taken) return@query AccountIdentityUpdateResult.EmailTaken
        UsersTable.update({ UsersTable.id eq userId }) {
            it[email] = identity.email
            it[name] = identity.name
            if (!current[UsersTable.email].equals(identity.email, ignoreCase = true)) {
                it[verified] = false
            }
            it[updatedAt] = System.currentTimeMillis()
        }
        AccountIdentityUpdateResult.Updated
    }

    private fun validate(email: String, name: String): NormalizedIdentity? {
        val normalizedEmail = email.trim().lowercase()
        val normalizedName = name.trim()
        if (normalizedEmail.length !in 3..254 || !EMAIL_REGEX.matches(normalizedEmail)) return null
        if (normalizedName.length !in 1..80) return null
        return NormalizedIdentity(normalizedEmail, normalizedName)
    }

    private data class Credentials(val passwordHash: String, val managedByOidc: Boolean)
    private data class NormalizedIdentity(val email: String, val name: String)

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

sealed interface AccountIdentityUpdateResult {
    data object Updated : AccountIdentityUpdateResult
    data object InvalidInput : AccountIdentityUpdateResult
    data object InvalidPassword : AccountIdentityUpdateResult
    data object ManagedByOidc : AccountIdentityUpdateResult
    data object EmailTaken : AccountIdentityUpdateResult
    data object UserNotFound : AccountIdentityUpdateResult
}
