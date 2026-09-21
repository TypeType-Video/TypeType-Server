package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RecommendationOnboardingStateTable : Table("recommendation_onboarding_state") {
    val userId = text("user_id")
    val completedAt = long("completed_at").nullable()
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId)
}
