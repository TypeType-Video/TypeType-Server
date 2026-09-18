package dev.typetype.server.services

import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.models.AdminBugReportDetailResponse
import dev.typetype.server.models.AdminBugReportItem
import dev.typetype.server.models.BugReportContextItem
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow

object BugReportRowMapper {
    fun toAdminListItem(row: ResultRow, userEmail: String): AdminBugReportItem = AdminBugReportItem(
        id = row[BugReportsTable.id],
        category = row[BugReportsTable.category],
        description = row[BugReportsTable.description],
        status = row[BugReportsTable.status],
        userId = row[BugReportsTable.userId],
        userEmail = userEmail,
        githubIssueUrl = row[BugReportsTable.githubIssueUrl],
        createdAt = row[BugReportsTable.createdAt],
        updatedAt = row[BugReportsTable.updatedAt],
    )

    fun toAdminDetailItem(json: Json, row: ResultRow, userEmail: String): AdminBugReportDetailResponse =
        AdminBugReportDetailResponse(
            id = row[BugReportsTable.id],
            category = row[BugReportsTable.category],
            description = row[BugReportsTable.description],
            status = row[BugReportsTable.status],
            userId = row[BugReportsTable.userId],
            userEmail = userEmail,
            context = json.decodeFromString(BugReportContextItem.serializer(), row[BugReportsTable.context]),
            githubIssueUrl = row[BugReportsTable.githubIssueUrl],
            createdAt = row[BugReportsTable.createdAt],
            updatedAt = row[BugReportsTable.updatedAt],
        )
}
