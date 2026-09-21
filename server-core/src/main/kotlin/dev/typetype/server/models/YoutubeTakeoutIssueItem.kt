package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutIssueItem(
    val code: String,
    val severity: String,
    val stage: String,
    val message: String,
    val count: Int = 1,
)

@Serializable
data class YoutubeTakeoutIssueSummary(
    val total: Int,
    val warnings: Int,
    val errors: Int,
    val byCode: Map<String, Int> = emptyMap(),
)
