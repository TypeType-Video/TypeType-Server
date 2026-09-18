package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutImportReportItem(
    val subscriptions: YoutubeTakeoutImportStats,
    val playlists: YoutubeTakeoutImportStats,
    val playlistItems: YoutubeTakeoutImportStats,
    val favorites: YoutubeTakeoutImportStats = YoutubeTakeoutImportStats(0, 0, 0),
    val watchLater: YoutubeTakeoutImportStats = YoutubeTakeoutImportStats(0, 0, 0),
    val history: YoutubeTakeoutImportStats = YoutubeTakeoutImportStats(0, 0, 0),
    val skippedItems: YoutubeTakeoutCategoryCounts = YoutubeTakeoutCategoryCounts(0, 0, 0),
    val warnings: List<String>,
    val errors: List<String>,
    val issues: List<YoutubeTakeoutIssueItem> = emptyList(),
    val issueSummary: YoutubeTakeoutIssueSummary = YoutubeTakeoutIssueSummary(0, 0, 0),
    val finishedAt: Long,
)

@Serializable
data class YoutubeTakeoutImportStats(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
)
