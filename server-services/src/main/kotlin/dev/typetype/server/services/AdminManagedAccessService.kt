package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.models.AdminManagedAccessUserItem
import dev.typetype.server.models.AdminManagedAccessUsersResponse
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class AdminManagedAccessService {
    suspend fun list(limit: Int, page: String?): AdminManagedAccessUsersResponse = DatabaseFactory.query {
        val offset = page?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val rows = mutableListOf<AdminManagedAccessUserItem>()
        val sql = """
            SELECT u.id, u.email, u.name, s.access_mode
            FROM settings s
            JOIN users u ON u.id = s.user_id
            WHERE s.access_mode_admin_managed = true
            ORDER BY s.access_mode_admin_managed_at DESC, lower(u.email), lower(u.name), u.id
            LIMIT ${limit + 1} OFFSET $offset
        """.trimIndent()
        TransactionManager.current().exec(sql) { rs ->
            while (rs.next()) {
                rows.add(
                    AdminManagedAccessUserItem(
                        id = rs.getString("id"),
                        email = rs.getString("email"),
                        name = rs.getString("name"),
                        accessMode = rs.getString("access_mode").toAccessMode(),
                        adminManagedAccessMode = true,
                    ),
                )
            }
        }
        val items = rows.take(limit)
        AdminManagedAccessUsersResponse(
            items = items,
            nextpage = if (rows.size > limit) (offset + limit).toString() else null,
        )
    }
}
