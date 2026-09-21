package dev.typetype.server.services

object RecommendationInterestWeight {
    fun of(eventType: String, watchRatio: Double?): Double = when (eventType) {
        "favorite" -> 5.0
        "watch_later_add" -> 4.0
        "watch" -> if ((watchRatio ?: 0.0) >= 0.7) 3.0 else 1.2
        "click" -> 1.0
        "impression" -> 0.2
        "short_skip" -> -1.0
        "not_interested" -> -6.0
        "less_from_channel" -> -10.0
        "block_video", "block_channel" -> -100.0
        else -> 0.0
    }
}
