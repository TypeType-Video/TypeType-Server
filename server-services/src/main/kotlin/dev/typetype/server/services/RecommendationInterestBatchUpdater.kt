package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class RecommendationInterestBatchUpdater {
    suspend fun apply(
        userId: String,
        uploaderUrl: String?,
        title: String?,
        delta: Double,
        now: Long,
    ) {
        DatabaseFactory.query {
            upsertChannel(userId = userId, uploaderUrl = uploaderUrl, delta = delta, now = now)
            upsertTopics(userId = userId, title = title, delta = delta, now = now)
        }
    }

    private fun upsertChannel(userId: String, uploaderUrl: String?, delta: Double, now: Long) {
        if (uploaderUrl.isNullOrBlank()) return
        exec(sql = CHANNEL_UPSERT_SQL, values = listOf(userId, uploaderUrl, delta, now))
    }

    private fun upsertTopics(userId: String, title: String?, delta: Double, now: Long) {
        val topics = RecommendationTopicTokenizer.tokenize(title.orEmpty())
        topics.forEach { topic ->
            exec(sql = TOPIC_UPSERT_SQL, values = listOf(userId, topic, delta, now))
        }
    }

    private fun exec(sql: String, values: List<Any>) {
        val transaction = TransactionManager.current()
        val preparedSql = values.fold(sql) { acc, value -> acc.replaceFirst("?", encode(value)) }
        transaction.exec(preparedSql)
    }

    private fun encode(value: Any): String = when (value) {
        is String -> "'${value.replace("'", "''")}'"
        is Long -> value.toString()
        is Double -> value.toString()
        else -> error("Unsupported SQL value type")
    }

    private companion object {
        const val CHANNEL_UPSERT_SQL = """
            INSERT INTO user_channel_interest (user_id, uploader_url, score, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, uploader_url)
            DO UPDATE SET
                score = user_channel_interest.score + EXCLUDED.score,
                updated_at = EXCLUDED.updated_at
        """
        const val TOPIC_UPSERT_SQL = """
            INSERT INTO user_topic_interest (user_id, topic, score, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, topic)
            DO UPDATE SET
                score = user_topic_interest.score + EXCLUDED.score,
                updated_at = EXCLUDED.updated_at
        """
    }
}
