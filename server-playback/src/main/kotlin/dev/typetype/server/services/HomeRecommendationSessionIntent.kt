package dev.typetype.server.services

enum class HomeRecommendationSessionIntent {
    AUTO,
    QUICK,
    DEEP;

    companion object {
        fun parse(raw: String?): HomeRecommendationSessionIntent = when (raw?.lowercase()) {
            "quick", "news", "rapid" -> QUICK
            "deep", "long", "focus" -> DEEP
            else -> AUTO
        }
    }
}
