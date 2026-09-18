package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.UserChannelInterestTable
import dev.typetype.server.db.tables.UserTopicInterestTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

data class RecommendationInterestProfile(
    val channelScores: Map<String, Double>,
    val topicScores: Map<String, Double>,
)

class RecommendationInterestProfileService {
    suspend fun load(userId: String): RecommendationInterestProfile {
        val channelScores = DatabaseFactory.query {
            UserChannelInterestTable.selectAll()
                .where { UserChannelInterestTable.userId eq userId }
                .orderBy(UserChannelInterestTable.score to SortOrder.DESC)
                .limit(120)
                .associate { row -> row[UserChannelInterestTable.uploaderUrl] to row[UserChannelInterestTable.score] }
        }
        val topicScores = DatabaseFactory.query {
            UserTopicInterestTable.selectAll()
                .where { UserTopicInterestTable.userId eq userId }
                .orderBy(UserTopicInterestTable.score to SortOrder.DESC)
                .limit(200)
                .associate { row -> row[UserTopicInterestTable.topic] to row[UserTopicInterestTable.score] }
        }
        return RecommendationInterestProfile(channelScores = channelScores, topicScores = topicScores)
    }
}
