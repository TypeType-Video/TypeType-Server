package dev.typetype.server.services

import dev.typetype.server.db.tables.RssFeedsTable
import dev.typetype.server.models.RssFeedItem
import org.jetbrains.exposed.v1.core.ResultRow

fun ResultRow.toStoredFeed(): StoredRssFeed =
    toStoredFeed(loadRssFeedSelections(listOf(this[RssFeedsTable.id])))

fun ResultRow.toStoredFeed(selections: RssFeedSelections): StoredRssFeed {
    val id = this[RssFeedsTable.id]
    return StoredRssFeed(
        item = RssFeedItem(
            id = id,
            name = this[RssFeedsTable.name],
            scope = this[RssFeedsTable.scope],
            channelUrls = selections.channels[id].orEmpty(),
            serviceIds = selections.services[id].orEmpty(),
            includeVideos = this[RssFeedsTable.includeVideos],
            includeShorts = this[RssFeedsTable.includeShorts],
            includeLive = this[RssFeedsTable.includeLive],
            includeUpcoming = this[RssFeedsTable.includeUpcoming],
            enabled = this[RssFeedsTable.enabled],
            createdAt = this[RssFeedsTable.createdAt],
            updatedAt = this[RssFeedsTable.updatedAt],
            lastUsedAt = this[RssFeedsTable.lastUsedAt],
        ),
        userId = this[RssFeedsTable.userId],
        tokenHash = this[RssFeedsTable.tokenHash],
    )
}
