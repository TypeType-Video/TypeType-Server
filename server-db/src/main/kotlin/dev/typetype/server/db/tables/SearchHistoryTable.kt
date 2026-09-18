package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SearchHistoryTable : Table("search_history") {
    val id = text("id")
    val userId = text("user_id")
    val term = text("term")
    val searchedAt = long("searched_at")
    override val primaryKey = PrimaryKey(id)
}
