package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class RecommendationFeedHistoryService {
    suspend fun load(userId: String): Map<String, RecommendationFeedHistoryEntry> = DatabaseFactory.query {
        val rows = mutableMapOf<String, RecommendationFeedHistoryEntry>()
        TransactionManager.current().exec(
            """
            SELECT video_url, show_count, last_shown
            FROM recommendation_feed_history
            WHERE user_id = ${encode(userId)}
            ORDER BY last_shown DESC
            LIMIT $MAX_ENTRIES
            """.trimIndent(),
        ) { rs ->
            while (rs.next()) {
                rows[rs.getString("video_url")] = RecommendationFeedHistoryEntry(
                    showCount = rs.getInt("show_count"),
                    lastShown = rs.getLong("last_shown"),
                )
            }
        }
        rows
    }

    suspend fun recordShown(userId: String, videoUrls: List<String>, now: Long = System.currentTimeMillis()) {
        val deduped = videoUrls.filter { it.isNotBlank() }.distinct()
        if (deduped.isEmpty()) return
        DatabaseFactory.query {
            deduped.forEach { url ->
                TransactionManager.current().exec(
                    """
                    INSERT INTO recommendation_feed_history (user_id, video_url, show_count, last_shown)
                    VALUES (${encode(userId)}, ${encode(url)}, 1, $now)
                    ON CONFLICT (user_id, video_url)
                    DO UPDATE SET
                        show_count = LEAST(recommendation_feed_history.show_count + 1, $MAX_SHOW_COUNT),
                        last_shown = EXCLUDED.last_shown
                    """.trimIndent(),
                )
            }
            pruneExpired(userId = userId, now = now)
            trimOverflow(userId)
        }
    }

    private fun pruneExpired(userId: String, now: Long) {
        val expiry = now - EXPIRY_MS
        TransactionManager.current().exec(
            """
            DELETE FROM recommendation_feed_history
            WHERE user_id = ${encode(userId)}
              AND last_shown < $expiry
            """.trimIndent(),
        )
    }

    private fun trimOverflow(userId: String) {
        TransactionManager.current().exec(
            """
            DELETE FROM recommendation_feed_history
            WHERE user_id = ${encode(userId)}
              AND video_url NOT IN (
                  SELECT video_url FROM recommendation_feed_history
                  WHERE user_id = ${encode(userId)}
                  ORDER BY last_shown DESC
                  LIMIT $MAX_ENTRIES
              )
            """.trimIndent(),
        )
    }

    private fun encode(value: String): String = "'${value.replace("'", "''")}'"

    companion object {
        private const val MAX_ENTRIES = 3000
        private const val MAX_SHOW_COUNT = 1000
        private const val EXPIRY_MS = 14L * 24L * 60L * 60L * 1000L
    }
}
