package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun insertActiveSessionUser(
    id: String = TEST_USER_ID,
    role: String = "admin",
    name: String = "Admin",
    publicUsername: String? = "admin",
): Unit = transaction {
    UsersTable.insert {
        it[UsersTable.id] = id
        it[email] = "$id@test.local"
        it[passwordHash] = "hash"
        it[UsersTable.name] = name
        it[UsersTable.role] = role
        it[UsersTable.publicUsername] = publicUsername
        it[createdAt] = 10L
        it[updatedAt] = 10L
    }
}
