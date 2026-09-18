package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateBugReportRequest(
    val category: String,
    val description: String,
    val context: BugReportContextItem,
)

@Serializable
data class UpdateBugReportStatusRequest(
    val status: String,
)
