package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.BugReportsTable
import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminBugReportDetailResponse
import dev.typetype.server.models.AdminBugReportItem
import dev.typetype.server.models.BugReportContextItem
import dev.typetype.server.models.BugReportCreateResponse
import dev.typetype.server.models.BugReportStatusResponse
import dev.typetype.server.models.CreateBugReportRequest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class BugReportService(private val json: Json = Json { ignoreUnknownKeys = true }) {
    suspend fun create(userId: String, request: CreateBugReportRequest): BugReportCreateResponse {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        DatabaseFactory.query {
            BugReportsTable.insert {
                it[BugReportsTable.id] = id
                it[BugReportsTable.userId] = userId
                it[category] = request.category
                it[description] = request.description.trim()
                it[context] = json.encodeToString(BugReportContextItem.serializer(), request.context)
                it[status] = "new"
                it[githubIssueUrl] = null
                it[createdAt] = now
                it[updatedAt] = now
            }
        }
        return BugReportCreateResponse(id = id, status = "new", createdAt = now)
    }

    suspend fun list(page: Int, limit: Int, status: String?, category: String?): Pair<List<AdminBugReportItem>, Long> = DatabaseFactory.query {
        val query = BugReportsTable.selectAll()
        if (status != null) query.andWhere { BugReportsTable.status eq status }
        if (category != null) query.andWhere { BugReportsTable.category eq category }
        val total = query.count()
        val offset = (page - 1).toLong() * limit.toLong()
        val reports = query.orderBy(BugReportsTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()
        val emailByUserId = userEmailByUserId(reports.map { it[BugReportsTable.userId] }.toSet())
        val items = reports.map { row ->
            BugReportRowMapper.toAdminListItem(row, emailByUserId[row[BugReportsTable.userId]].orEmpty())
        }
        items to total
    }

    suspend fun detail(id: String): AdminBugReportDetailResponse? = DatabaseFactory.query {
        val row = BugReportsTable.selectAll().where { BugReportsTable.id eq id }.singleOrNull() ?: return@query null
        val email = UsersTable.selectAll().where { UsersTable.id eq row[BugReportsTable.userId] }.singleOrNull()?.get(UsersTable.email).orEmpty()
        BugReportRowMapper.toAdminDetailItem(json, row, email)
    }

    suspend fun updateStatus(id: String, status: String): BugReportStatusUpdateResult = DatabaseFactory.query {
        val row = BugReportsTable.selectAll().where { BugReportsTable.id eq id }.singleOrNull()
            ?: return@query BugReportStatusUpdateResult.NotFound
        val currentStatus = row[BugReportsTable.status]
        if (!BugReportStatusFlow.isValidTransition(currentStatus, status)) {
            return@query BugReportStatusUpdateResult.InvalidTransition(currentStatus, status)
        }
        val now = System.currentTimeMillis()
        BugReportsTable.update({ BugReportsTable.id eq id }) {
            it[BugReportsTable.status] = status
            it[updatedAt] = now
        }
        BugReportStatusUpdateResult.Updated(BugReportStatusResponse(id = id, status = status, updatedAt = now))
    }

    suspend fun markGithubIssue(id: String, issueUrl: String): Long? {
        val now = System.currentTimeMillis()
        val updated = DatabaseFactory.query {
            val existing = BugReportsTable.selectAll().where { BugReportsTable.id eq id }.singleOrNull() ?: return@query 0
            if (existing[BugReportsTable.githubIssueUrl] != null) return@query 0
            BugReportsTable.update({ BugReportsTable.id eq id }) {
                it[githubIssueUrl] = issueUrl
                it[updatedAt] = now
            }
        }
        return if (updated > 0) now else null
    }

    suspend fun existingGithubIssue(id: String): String? = DatabaseFactory.query {
        BugReportsTable.selectAll().where { BugReportsTable.id eq id }.singleOrNull()?.get(BugReportsTable.githubIssueUrl)
    }

    private fun userEmailByUserId(userIds: Set<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return UsersTable.selectAll()
            .filter { row -> row[UsersTable.id] in userIds }
            .associate { row -> row[UsersTable.id] to row[UsersTable.email] }
    }
}
