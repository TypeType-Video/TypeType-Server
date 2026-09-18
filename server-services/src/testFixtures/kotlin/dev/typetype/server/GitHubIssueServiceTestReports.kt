package dev.typetype.server

import dev.typetype.server.models.AdminBugReportDetailResponse
import dev.typetype.server.models.BugApiErrorItem
import dev.typetype.server.models.BugReportContextItem

object GitHubIssueServiceTestReports {
    fun sampleReport(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id",
        category = "player",
        description = "Video freezes after 10s",
        status = "new",
        userId = "user-id",
        userEmail = "user@test.local",
        context = BugReportContextItem(
            route = "/watch",
            timestamp = 1775200000000,
            userAgent = "Mozilla/5.0",
            browserLanguage = "fr-FR",
            apiErrors = listOf(apiError("req-123", "/streams", 400, "BAD_REQUEST", "Invalid url", 1775200000001)),
        ),
        githubIssueUrl = null,
        createdAt = 1775200000000,
        updatedAt = 1775200000000,
    )

    fun sampleReportWithDomain(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id-2",
        category = "ui",
        description = "Fails on https://internal.local/watch?url=abc",
        status = "new",
        userId = "user-id-2",
        userEmail = "user@private.example",
        context = BugReportContextItem(
            route = "https://private.example/watch?url=abc",
            timestamp = 1775200001000,
            userAgent = "Mozilla/5.0 (see https://ua.example/meta)",
            browserLanguage = "fr-FR",
            videoUrl = "https://private.example/video?id=1",
            apiErrors = listOf(
                apiError("req-redact", "https://internal.local/api/streams?url=1", 500, "INTERNAL_ERROR", "Upstream from https://internal.local failed", 1775200001001),
            ),
        ),
        githubIssueUrl = null,
        createdAt = 1775200001000,
        updatedAt = 1775200001000,
    )

    fun sampleReportWithHostTokens(): AdminBugReportDetailResponse = AdminBugReportDetailResponse(
        id = "report-id-3",
        category = "functionality",
        description = "Host token redaction case",
        status = "new",
        userId = "user-id-3",
        userEmail = "user3@test.local",
        context = BugReportContextItem(
            route = "/shorts",
            timestamp = 1775200002000,
            userAgent = "Mozilla (private.host.internal/client)",
            browserLanguage = "fr-FR",
            apiErrors = listOf(apiError("req.private.local", "/admin/bug-reports", 409, "CONFLICT", "already exists", 1775200002001)),
        ),
        githubIssueUrl = null,
        createdAt = 1775200002000,
        updatedAt = 1775200002000,
    )

    private fun apiError(
        requestId: String,
        endpoint: String,
        status: Int,
        code: String,
        message: String,
        timestamp: Long,
    ): BugApiErrorItem = BugApiErrorItem(
        requestId = requestId,
        endpoint = endpoint,
        status = status,
        code = code,
        message = message,
        timestamp = timestamp,
    )
}
