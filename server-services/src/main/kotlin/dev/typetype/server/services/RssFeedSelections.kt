package dev.typetype.server.services

import dev.typetype.server.db.tables.RssFeedChannelsTable
import dev.typetype.server.db.tables.RssFeedServicesTable
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

data class RssFeedSelections(
    val channels: Map<String, List<String>>,
    val services: Map<String, List<Int>>,
)

fun loadRssFeedSelections(feedIds: List<String>): RssFeedSelections {
    if (feedIds.isEmpty()) return RssFeedSelections(emptyMap(), emptyMap())
    val channels = RssFeedChannelsTable.selectAll()
        .where { RssFeedChannelsTable.feedId inList feedIds }
        .groupBy({ it[RssFeedChannelsTable.feedId] }, { it[RssFeedChannelsTable.channelUrl] })
    val services = RssFeedServicesTable.selectAll()
        .where { RssFeedServicesTable.feedId inList feedIds }
        .groupBy({ it[RssFeedServicesTable.feedId] }, { it[RssFeedServicesTable.serviceId] })
        .mapValues { (_, values) -> values.sorted() }
    return RssFeedSelections(channels, services)
}
