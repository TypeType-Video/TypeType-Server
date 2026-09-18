package dev.typetype.server.services

import dev.typetype.server.models.BugReportStatusResponse

sealed interface BugReportStatusUpdateResult {
    data class Updated(val response: BugReportStatusResponse) : BugReportStatusUpdateResult
    data object NotFound : BugReportStatusUpdateResult
    data class InvalidTransition(val currentStatus: String, val requestedStatus: String) : BugReportStatusUpdateResult
}
