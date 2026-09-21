package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object SubscriptionGroupMembershipsTable : Table("subscription_group_memberships") {
    val groupId = text("group_id").references(SubscriptionGroupsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = text("user_id")
    val channelUrl = text("channel_url")
    val addedAt = long("added_at")

    init {
        index(false, userId, channelUrl)
    }

    override val primaryKey = PrimaryKey(groupId, channelUrl)
}
