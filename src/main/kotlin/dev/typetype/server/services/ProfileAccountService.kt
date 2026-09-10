package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.ProfileAccountsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AccountProfileItem
import dev.typetype.server.models.AccountProfilesResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class ProfileAccountService {
    suspend fun ownerUserId(profileId: String): String? = DatabaseFactory.query {
        ownerIdInTransaction(profileId)
    }

    suspend fun isOwnerProfile(profileId: String): Boolean = DatabaseFactory.query {
        if (profileId.startsWith("guest:")) return@query false
        ProfileAccountsTable.selectAll().where { ProfileAccountsTable.profileId eq profileId }
            .singleOrNull()?.let { it[ProfileAccountsTable.ownerUserId] == profileId } ?: true
    }

    suspend fun list(activeProfileId: String): AccountProfilesResponse? = DatabaseFactory.query {
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query null
        ensureDefaultInTransaction(ownerId)
        val rows = ProfileAccountsTable.selectAll().where { ProfileAccountsTable.ownerUserId eq ownerId }
            .map { row -> profileRow(row, activeProfileId) }
            .sortedWith(compareByDescending<AccountProfileItem> { it.isDefault }.thenBy { it.name.lowercase() })
        val defaultId = rows.firstOrNull { it.isDefault }?.id ?: activeProfileId
        AccountProfilesResponse(rows, activeProfileId, defaultId)
    }

    suspend fun resolveSignInProfile(ownerUserId: String): String = DatabaseFactory.query {
        val ownerId = ownerIdInTransaction(ownerUserId) ?: return@query ownerUserId
        ensureDefaultInTransaction(ownerId)
        val rows = ProfileAccountsTable.selectAll().where { ProfileAccountsTable.ownerUserId eq ownerId }.toList()
        val selected = rows.filter { it[ProfileAccountsTable.lastUsedAt] > 0L }
            .maxByOrNull { it[ProfileAccountsTable.lastUsedAt] }
            ?: rows.firstOrNull { it[ProfileAccountsTable.isDefault] }
            ?: rows.firstOrNull()
        val profileId = selected?.get(ProfileAccountsTable.profileId) ?: ownerId
        markUsedInTransaction(profileId, System.currentTimeMillis())
        profileId
    }

    suspend fun create(activeProfileId: String, name: String): ProfileMutationResult = DatabaseFactory.query {
        val normalized = normalizeName(name) ?: return@query ProfileMutationResult.InvalidName
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query ProfileMutationResult.NotFound
        val profileId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        UsersTable.insertIgnore {
            it[id] = profileId
            it[email] = "profile-$profileId@profiles.invalid"
            it[passwordHash] = "profile:$profileId"
            it[UsersTable.name] = normalized
            it[role] = "user"
            it[verified] = true
            it[createdAt] = now
            it[updatedAt] = now
        }
        ProfileAccountsTable.insertIgnore {
            it[ProfileAccountsTable.profileId] = profileId
            it[ownerUserId] = ownerId
            it[displayName] = normalized
            it[isDefault] = false
            it[lastUsedAt] = 0L
            it[createdAt] = now
            it[updatedAt] = now
        }
        ProfileMutationResult.Success(profileRowById(profileId, activeProfileId))
    }

    suspend fun rename(activeProfileId: String, profileId: String, name: String): ProfileMutationResult = DatabaseFactory.query {
        val normalized = normalizeName(name) ?: return@query ProfileMutationResult.InvalidName
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query ProfileMutationResult.NotFound
        val target = targetInTransaction(ownerId, profileId) ?: return@query ProfileMutationResult.NotFound
        ProfileAccountsTable.update({ ProfileAccountsTable.profileId eq profileId }) {
            it[displayName] = normalized
            it[updatedAt] = System.currentTimeMillis()
        }
        ProfileMutationResult.Success(profileRowById(target, activeProfileId))
    }

    suspend fun setDefault(activeProfileId: String, profileId: String): ProfileMutationResult = DatabaseFactory.query {
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query ProfileMutationResult.NotFound
        val target = targetInTransaction(ownerId, profileId) ?: return@query ProfileMutationResult.NotFound
        ProfileAccountsTable.update({ ProfileAccountsTable.ownerUserId eq ownerId }) { it[isDefault] = false }
        ProfileAccountsTable.update({ ProfileAccountsTable.profileId eq target }) {
            it[isDefault] = true
            it[updatedAt] = System.currentTimeMillis()
        }
        ProfileMutationResult.Success(profileRowById(target, activeProfileId))
    }

    suspend fun switch(activeProfileId: String, profileId: String): AccountProfileItem? = DatabaseFactory.query {
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query null
        val target = targetInTransaction(ownerId, profileId) ?: return@query null
        markUsedInTransaction(target, System.currentTimeMillis())
        profileRowById(target, target)
    }

    suspend fun delete(activeProfileId: String, profileId: String): ProfileMutationResult = DatabaseFactory.query {
        val ownerId = ownerIdInTransaction(activeProfileId) ?: return@query ProfileMutationResult.NotFound
        val target = targetInTransaction(ownerId, profileId) ?: return@query ProfileMutationResult.NotFound
        if (target == ownerId) return@query ProfileMutationResult.CannotDeleteOwner
        if (target == activeProfileId) return@query ProfileMutationResult.CannotDeleteActive
        ProfileDataDeletionService.deleteUser(target)
        ProfileAccountsTable.deleteWhere { ProfileAccountsTable.profileId eq target }
        ensureDefaultInTransaction(ownerId)
        ProfileMutationResult.Deleted
    }

    private fun ownerIdInTransaction(profileId: String): String? {
        if (profileId.startsWith("guest:")) return null
        ProfileAccountsTable.selectAll().where { ProfileAccountsTable.profileId eq profileId }
            .singleOrNull()?.let { return it[ProfileAccountsTable.ownerUserId] }
        val userExists = UsersTable.selectAll().where { UsersTable.id eq profileId }.any()
        if (!userExists) return null
        val now = System.currentTimeMillis()
        ProfileAccountsTable.insertIgnore {
            it[ProfileAccountsTable.profileId] = profileId
            it[ownerUserId] = profileId
            it[displayName] = "Profile"
            it[isDefault] = true
            it[lastUsedAt] = 0L
            it[createdAt] = now
            it[updatedAt] = now
        }
        return profileId
    }

    private fun targetInTransaction(ownerId: String, profileId: String): String? =
        ProfileAccountsTable.selectAll().where {
            (ProfileAccountsTable.ownerUserId eq ownerId) and (ProfileAccountsTable.profileId eq profileId)
        }.singleOrNull()?.get(ProfileAccountsTable.profileId)

    private fun profileRowById(profileId: String, activeProfileId: String): AccountProfileItem =
        ProfileAccountsTable.selectAll().where { ProfileAccountsTable.profileId eq profileId }
            .single().let { profileRow(it, activeProfileId) }

    private fun profileRow(row: org.jetbrains.exposed.v1.core.ResultRow, activeProfileId: String): AccountProfileItem {
        val profileId = row[ProfileAccountsTable.profileId]
        val user = UsersTable.selectAll().where { UsersTable.id eq profileId }.single()
        return AccountProfileItem(
            id = profileId,
            name = row[ProfileAccountsTable.displayName],
            isActive = profileId == activeProfileId,
            isDefault = row[ProfileAccountsTable.isDefault],
            lastUsedAt = row[ProfileAccountsTable.lastUsedAt],
            publicUsername = user[UsersTable.publicUsername],
            avatarUrl = user[UsersTable.avatarUrl],
            avatarType = user[UsersTable.avatarType],
            avatarCode = user[UsersTable.avatarCode],
        )
    }

    private fun markUsedInTransaction(profileId: String, now: Long) {
        ProfileAccountsTable.update({ ProfileAccountsTable.profileId eq profileId }) { it[lastUsedAt] = now }
    }

    private fun ensureDefaultInTransaction(ownerId: String) {
        val rows = ProfileAccountsTable.selectAll().where { ProfileAccountsTable.ownerUserId eq ownerId }.toList()
        if (rows.isEmpty()) return
        val selected = rows.filter { it[ProfileAccountsTable.isDefault] }
            .maxByOrNull { it[ProfileAccountsTable.lastUsedAt] }
            ?: rows.minByOrNull { it[ProfileAccountsTable.createdAt] }
            ?: return
        val selectedId = selected[ProfileAccountsTable.profileId]
        ProfileAccountsTable.update({ ProfileAccountsTable.ownerUserId eq ownerId }) {
            it[isDefault] = false
        }
        ProfileAccountsTable.update({ ProfileAccountsTable.profileId eq selectedId }) {
            it[isDefault] = true
        }
    }

    private fun normalizeName(value: String): String? = value.trim().takeIf { it.length in 1..40 }
}

sealed interface ProfileMutationResult {
    data class Success(val profile: AccountProfileItem) : ProfileMutationResult
    data object Deleted : ProfileMutationResult
    data object InvalidName : ProfileMutationResult
    data object CannotDeleteOwner : ProfileMutationResult
    data object CannotDeleteActive : ProfileMutationResult
    data object NotFound : ProfileMutationResult
}
