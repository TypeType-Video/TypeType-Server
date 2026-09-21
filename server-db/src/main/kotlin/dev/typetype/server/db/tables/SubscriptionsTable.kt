package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object SubscriptionsTable : Table("subscriptions") {
    val userId = text("user_id")
    val channelUrl = text("channel_url")
    val name = text("name")
    val avatarUrl = text("avatar_url")
    val subscribedAt = long("subscribed_at")
    override val primaryKey = PrimaryKey(userId, channelUrl)
}
