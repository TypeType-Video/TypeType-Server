package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RecommendationOnboardingStateTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class RecommendationOnboardingStateStore {
    suspend fun readCompletedAt(userId: String): Long? = DatabaseFactory.query {
        RecommendationOnboardingStateTable.selectAll()
            .where { RecommendationOnboardingStateTable.userId eq userId }
            .singleOrNull()
            ?.get(RecommendationOnboardingStateTable.completedAt)
    }

    suspend fun markCompleted(userId: String, at: Long) {
        DatabaseFactory.query {
            val updated = RecommendationOnboardingStateTable.update({ RecommendationOnboardingStateTable.userId eq userId }) {
                it[completedAt] = at
                it[updatedAt] = at
            }
            if (updated == 0) {
                RecommendationOnboardingStateTable.insert {
                    it[RecommendationOnboardingStateTable.userId] = userId
                    it[completedAt] = at
                    it[updatedAt] = at
                }
            }
        }
    }
}
