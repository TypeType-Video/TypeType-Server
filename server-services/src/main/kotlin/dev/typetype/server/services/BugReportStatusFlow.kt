package dev.typetype.server.services

object BugReportStatusFlow {
    private val nextStatus: Map<String, String> = mapOf(
        "new" to "triaged",
        "triaged" to "in_progress",
        "in_progress" to "fixed",
        "fixed" to "closed",
    )

    fun isValidTransition(currentStatus: String, requestedStatus: String): Boolean =
        requestedStatus == currentStatus || nextStatus[currentStatus] == requestedStatus
}
