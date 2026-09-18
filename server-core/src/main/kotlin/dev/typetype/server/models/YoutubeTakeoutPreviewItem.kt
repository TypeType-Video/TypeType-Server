package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeTakeoutPreviewItem(
    val counts: YoutubeTakeoutCategoryCounts,
    val dedup: YoutubeTakeoutCategoryCounts,
    val samples: YoutubeTakeoutPreviewSamples,
    val warnings: List<String>,
    val errors: List<String>,
    val issues: List<YoutubeTakeoutIssueItem> = emptyList(),
    val issueSummary: YoutubeTakeoutIssueSummary = YoutubeTakeoutIssueSummary(0, 0, 0),
)

@Serializable
data class YoutubeTakeoutCategoryCounts(
    val subscriptions: Int,
    val playlists: Int,
    val playlistItems: Int,
    val favorites: Int = 0,
    val watchLater: Int = 0,
    val history: Int = 0,
)
