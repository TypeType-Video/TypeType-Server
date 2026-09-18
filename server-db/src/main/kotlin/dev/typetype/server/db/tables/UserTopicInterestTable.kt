package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object UserTopicInterestTable : Table("user_topic_interest") {
    val userId = text("user_id")
    val topic = text("topic")
    val score = double("score")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(userId, topic)
}
