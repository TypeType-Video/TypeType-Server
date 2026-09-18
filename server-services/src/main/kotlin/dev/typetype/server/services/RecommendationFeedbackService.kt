package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RecommendationFeedbackTable
import dev.typetype.server.models.RecommendationFeedbackItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

class RecommendationFeedbackService(
    private val eventService: RecommendationEventService,
) {
    suspend fun getAll(userId: String): List<RecommendationFeedbackItem> {
        return DatabaseFactory.query {
        RecommendationFeedbackTable.selectAll()
            .where { RecommendationFeedbackTable.userId eq userId }
            .orderBy(RecommendationFeedbackTable.createdAt to SortOrder.DESC)
            .limit(300)
            .map { it.toItem() }
        }
    }

    suspend fun add(userId: String, feedbackType: String, videoUrl: String?, uploaderUrl: String?): RecommendationFeedbackItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            RecommendationFeedbackTable.insert {
                it[RecommendationFeedbackTable.id] = id
                it[RecommendationFeedbackTable.userId] = userId
                it[RecommendationFeedbackTable.feedbackType] = feedbackType
                it[RecommendationFeedbackTable.videoUrl] = videoUrl
                it[RecommendationFeedbackTable.uploaderUrl] = uploaderUrl
                it[RecommendationFeedbackTable.createdAt] = now
            }
        }
        val eventType = if (feedbackType == "not_interested") "not_interested" else "less_from_channel"
        eventService.add(
            userId = userId,
            eventType = eventType,
            videoUrl = videoUrl,
            uploaderUrl = uploaderUrl,
            title = null,
            watchRatio = null,
            watchDurationMs = null,
        )
        return RecommendationFeedbackItem(id, feedbackType, videoUrl, uploaderUrl, now)
    }

    private fun ResultRow.toItem() = RecommendationFeedbackItem(
        id = this[RecommendationFeedbackTable.id],
        feedbackType = this[RecommendationFeedbackTable.feedbackType],
        videoUrl = this[RecommendationFeedbackTable.videoUrl],
        uploaderUrl = this[RecommendationFeedbackTable.uploaderUrl],
        createdAt = this[RecommendationFeedbackTable.createdAt],
    )
}
