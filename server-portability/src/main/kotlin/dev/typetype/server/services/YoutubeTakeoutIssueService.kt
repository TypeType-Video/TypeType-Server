package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutIssueItem
import dev.typetype.server.models.YoutubeTakeoutIssueSummary

object YoutubeTakeoutIssueService {
    fun build(warnings: List<String>, errors: List<String>, stage: String): Pair<List<YoutubeTakeoutIssueItem>, YoutubeTakeoutIssueSummary> {
        val counters = linkedMapOf<String, Int>()
        warnings.forEach { counters["warn::${it.trim()}"] = (counters["warn::${it.trim()}"] ?: 0) + 1 }
        errors.forEach { counters["error::${it.trim()}"] = (counters["error::${it.trim()}"] ?: 0) + 1 }
        val items = counters.entries.map { (key, count) ->
            val parts = key.split("::", limit = 2)
            val severity = parts.first()
            val message = parts.last()
            YoutubeTakeoutIssueItem(
                code = toCode(severity, message),
                severity = if (severity == "warn") "warning" else "error",
                stage = stage,
                message = message,
                count = count,
            )
        }
        val byCode = items.groupBy { it.code }.mapValues { (_, values) -> values.sumOf { it.count } }
        val warningCount = items.filter { it.severity == "warning" }.sumOf { it.count }
        val errorCount = items.filter { it.severity == "error" }.sumOf { it.count }
        return items to YoutubeTakeoutIssueSummary(total = warningCount + errorCount, warnings = warningCount, errors = errorCount, byCode = byCode)
    }

    private fun toCode(severity: String, message: String): String {
        if (message.startsWith("Unsupported CSV schema")) return "unsupported_csv_schema"
        if (message == "Invalid subscription row") return "invalid_subscription_row"
        if (message == "Invalid playlist row") return "invalid_playlist_row"
        if (message == "Invalid playlist item row") return "invalid_playlist_item_row"
        if (message == "No subscription rows detected") return "no_subscription_rows"
        if (message == "No watch history rows detected") return "no_watch_history_rows"
        return if (severity == "warn") "warning_generic" else "error_generic"
    }
}
