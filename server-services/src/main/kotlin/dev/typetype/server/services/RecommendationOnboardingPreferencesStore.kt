package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.RecommendationOnboardingPreferencesTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class RecommendationOnboardingPreferencesStore {
    suspend fun readTopics(userId: String): List<String> = DatabaseFactory.query {
        readByType(userId = userId, type = "topic")
    }

    suspend fun readChannels(userId: String): List<String> = DatabaseFactory.query {
        readByType(userId = userId, type = "channel")
    }

    suspend fun save(userId: String, topics: List<String>, channels: List<String>) {
        DatabaseFactory.query {
            clear(userId = userId, type = "topic")
            clear(userId = userId, type = "channel")
            val now = System.currentTimeMillis()
            topics.forEach { value -> insert(userId = userId, type = "topic", value = value, now = now) }
            channels.forEach { value -> insert(userId = userId, type = "channel", value = value, now = now) }
        }
    }

    private fun readByType(userId: String, type: String): List<String> =
        RecommendationOnboardingPreferencesTable.selectAll()
            .where {
                (RecommendationOnboardingPreferencesTable.userId eq userId) and
                    (RecommendationOnboardingPreferencesTable.preferenceType eq type)
            }
            .map { it[RecommendationOnboardingPreferencesTable.value] }

    private fun clear(userId: String, type: String) {
        RecommendationOnboardingPreferencesTable.deleteWhere {
            (RecommendationOnboardingPreferencesTable.userId eq userId) and
                (RecommendationOnboardingPreferencesTable.preferenceType eq type)
        }
    }

    private fun insert(userId: String, type: String, value: String, now: Long) {
        RecommendationOnboardingPreferencesTable.insert {
            it[RecommendationOnboardingPreferencesTable.userId] = userId
            it[preferenceType] = type
            it[RecommendationOnboardingPreferencesTable.value] = value
            it[createdAt] = now
        }
    }
}
