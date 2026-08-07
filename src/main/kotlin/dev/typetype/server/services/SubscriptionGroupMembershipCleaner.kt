package dev.typetype.server.services

import dev.typetype.server.db.tables.SubscriptionGroupMembershipsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.jdbc.deleteWhere

internal object SubscriptionGroupMembershipCleaner {
    fun retain(userId: String, channelUrls: Collection<String>) {
        val retained = channelUrls.mapTo(linkedSetOf(), ChannelUrlCanonicalizer::canonicalize)
        SubscriptionGroupMembershipsTable.deleteWhere {
            val ownedByUser = SubscriptionGroupMembershipsTable.userId eq userId
            if (retained.isEmpty()) ownedByUser else {
                ownedByUser and (SubscriptionGroupMembershipsTable.channelUrl notInList retained)
            }
        }
    }
}
