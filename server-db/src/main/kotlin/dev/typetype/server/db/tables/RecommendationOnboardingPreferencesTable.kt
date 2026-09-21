package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RecommendationOnboardingPreferencesTable : Table("recommendation_onboarding_preferences") {
    val userId = text("user_id")
    val preferenceType = text("preference_type")
    val value = text("value")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, preferenceType, value)
}
