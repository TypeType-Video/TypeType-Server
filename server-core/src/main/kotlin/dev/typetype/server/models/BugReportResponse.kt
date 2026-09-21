package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BugReportCreateResponse(
    val id: String,
    val status: String,
    val createdAt: Long,
)

@Serializable
data class AdminBugReportItem(
    val id: String,
    val category: String,
    val description: String,
    val status: String,
    val userId: String,
    val userEmail: String,
    val githubIssueUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AdminBugReportsPageResponse(
    val items: List<AdminBugReportItem>,
    val page: Int,
    val limit: Int,
    val total: Long,
)

@Serializable
data class AdminBugReportDetailResponse(
    val id: String,
    val category: String,
    val description: String,
    val status: String,
    val userId: String,
    val userEmail: String,
    val context: BugReportContextItem,
    val githubIssueUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BugReportStatusResponse(
    val id: String,
    val status: String,
    val updatedAt: Long,
)

@Serializable
data class BugReportGithubIssueResponse(
    val id: String,
    val githubIssueUrl: String,
    val updatedAt: Long,
)
