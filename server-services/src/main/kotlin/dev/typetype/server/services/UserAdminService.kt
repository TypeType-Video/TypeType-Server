package dev.typetype.server.services

import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminUserItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class UserAdminService {

    fun listUsers(): List<AdminUserItem> = transaction {
        val rows = UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .toList()
        val accessModes = accessModesFor(rows.map { it[UsersTable.id] })
        rows.map { toAdminUserItem(it, accessModes) }
    }

    fun listUsers(page: Int, limit: Int): Pair<List<AdminUserItem>, Long> = transaction {
        val total = UsersTable.selectAll().count()
        val offset = (page - 1).toLong() * limit.toLong()
        val rows = UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()
        val accessModes = accessModesFor(rows.map { it[UsersTable.id] })
        val users = rows.map { toAdminUserItem(it, accessModes) }
        users to total
    }

    fun setAccessMode(userId: String, accessMode: String): AdminUserAccessModeResult = transaction {
        if (UsersTable.selectAll().where { UsersTable.id eq userId }.empty()) {
            return@transaction AdminUserAccessModeResult.UserNotFound
        }
        val saved = accessMode.toAccessMode()
        val managedAt = System.currentTimeMillis()
        val updated = SettingsTable.update({ SettingsTable.userId eq userId }) {
            it[SettingsTable.accessMode] = saved
            it[SettingsTable.accessModeAdminManaged] = true
            it[SettingsTable.accessModeAdminManagedAt] = managedAt
        }
        if (updated == 0) {
            SettingsTable.insert {
                it[SettingsTable.userId] = userId
                it[SettingsTable.accessMode] = saved
                it[SettingsTable.accessModeAdminManaged] = true
                it[SettingsTable.accessModeAdminManagedAt] = managedAt
            }
        }
        AdminUserAccessModeResult.Updated(saved)
    }

    fun suspendUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = true
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun unsuspendUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = false
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    fun promoteUser(userId: String, role: String): Boolean {
        if (role !in setOf("user", "moderator", "admin")) return false
        return transaction {
            UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.role] = role
                it[UsersTable.updatedAt] = System.currentTimeMillis()
            } > 0
        }
    }

    fun deleteUser(userId: String): Boolean = transaction {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.suspended] = true
            it[UsersTable.updatedAt] = System.currentTimeMillis()
        } > 0
    }

    private fun toAdminUserItem(row: ResultRow, accessModes: Map<String, String>): AdminUserItem =
        AdminUserItem(
            id = row[UsersTable.id],
            email = row[UsersTable.email],
            name = row[UsersTable.name],
            role = row[UsersTable.role],
            publicUsername = row[UsersTable.publicUsername],
            bio = row[UsersTable.bio],
            avatarUrl = row[UsersTable.avatarUrl],
            avatarType = row[UsersTable.avatarType],
            avatarCode = row[UsersTable.avatarCode],
            suspended = row[UsersTable.suspended],
            verified = row[UsersTable.verified],
            accessMode = accessModes[row[UsersTable.id]] ?: ACCESS_MODE_UNRESTRICTED,
            createdAt = row[UsersTable.createdAt],
        )

    private fun accessModesFor(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return SettingsTable.selectAll()
            .where { SettingsTable.userId inList userIds }
            .associate { it[SettingsTable.userId] to it[SettingsTable.accessMode].toAccessMode() }
    }
}

sealed class AdminUserAccessModeResult {
    data class Updated(val accessMode: String) : AdminUserAccessModeResult()
    data object UserNotFound : AdminUserAccessModeResult()
}
