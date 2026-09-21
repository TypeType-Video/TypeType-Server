package dev.typetype.server.services

import dev.typetype.server.db.tables.SettingsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminAllowListUserItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class AdminUserLookupService {
    fun search(query: String, limit: Int): List<AdminAllowListUserItem> = transaction {
        val normalizedQuery = "%${query.trim().lowercase()}%"
        val rows = UsersTable.selectAll()
            .where {
                UsersTable.email.lowerCase() like normalizedQuery or
                    (UsersTable.name.lowerCase() like normalizedQuery)
            }
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .limit(limit.coerceIn(1, MAX_SEARCH_LIMIT))
            .toList()
        val modes = accessModesFor(rows.map { it[UsersTable.id] })
        rows.map { it.toAllowListUser(modes) }
    }

    fun get(userId: String): AdminAllowListUserItem? = transaction {
        val row = UsersTable.selectAll().where { UsersTable.id eq userId }.singleOrNull() ?: return@transaction null
        row.toAllowListUser(accessModesFor(listOf(userId)))
    }

    private fun accessModesFor(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return SettingsTable.selectAll()
            .where { SettingsTable.userId inList userIds }
            .associate { it[SettingsTable.userId] to it[SettingsTable.accessMode].toAccessMode() }
    }

    private fun ResultRow.toAllowListUser(accessModes: Map<String, String>): AdminAllowListUserItem =
        AdminAllowListUserItem(
            id = this[UsersTable.id],
            email = this[UsersTable.email],
            name = this[UsersTable.name],
            accessMode = accessModes[this[UsersTable.id]] ?: ACCESS_MODE_UNRESTRICTED,
        )

    private companion object {
        const val MAX_SEARCH_LIMIT = 50
    }
}
