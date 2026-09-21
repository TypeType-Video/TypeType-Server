package dev.typetype.server.services

object HomeRecommendationLiveTitleDetector {
    private val liveMarkers = listOf(
        " live",
        "is live",
        "🔴",
        "en direct",
        "direct:",
        "livestream",
        "live:",
        " stream",
        "streamed",
    )

    private val eventMarkers = listOf(
        " vs ",
        " versus ",
        "full match",
        "full game",
        "playoff",
        "semi-final",
        "quarter-final",
        "final 🏆",
        "tournament",
        "championship",
        "main stream",
        " esports",
        "marathon",
        "night of ",
        "khotba",
        "prayer",
        " sermon",
        " show",
        " event",
        " concert",
        " awards",
    )

    private val yearPattern = Regex("""\b202[4-6]\b""")
    private val dayPattern = Regex("""\bday\s+\d+\b""", RegexOption.IGNORE_CASE)
    private val datePattern = Regex("""\b\d{1,2}[-/]\d{1,2}[-/]202[4-6]\b""")
    private val multiDayPattern = Regex("""\b\d+\s+(days?|nights?)\b""", RegexOption.IGNORE_CASE)

    fun isLiveLike(title: String): Boolean {
        val normalized = title.lowercase()
        if (liveMarkers.any { marker -> marker in normalized }) return true
        val hasYear = yearPattern.containsMatchIn(title)
        val hasDay = dayPattern.containsMatchIn(title)
        val hasDate = datePattern.containsMatchIn(title)
        val hasMultiDay = multiDayPattern.containsMatchIn(title)
        if (hasYear && (hasDay || hasDate)) return true
        if (hasMultiDay) return true
        if (hasDay && eventMarkers.any { marker -> marker in normalized }) return true
        if (eventMarkers.count { marker -> marker in normalized } >= 2) return true
        return false
    }
}
