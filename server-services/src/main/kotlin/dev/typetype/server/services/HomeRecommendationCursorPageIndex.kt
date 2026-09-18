package dev.typetype.server.services

object HomeRecommendationCursorPageIndex {
    fun from(cursor: HomeRecommendationCursor, limit: Int): Int {
        if (limit <= 0) return 0
        val consumed = maxOf(cursor.subscriptionIndex, cursor.discoveryIndex)
        return consumed / limit
    }
}
