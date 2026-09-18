package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object BugReportsTable : Table("bug_reports") {
    val id = varchar("id", 64)
    val userId = text("user_id")
    val category = varchar("category", 50)
    val description = text("description")
    val context = text("context")
    val status = varchar("status", 20)
    val githubIssueUrl = text("github_issue_url").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, status)
        index(false, category)
    }
}
