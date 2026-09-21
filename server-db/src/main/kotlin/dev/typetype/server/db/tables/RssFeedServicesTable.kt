package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RssFeedServicesTable : Table("rss_feed_services") {
    val feedId = text("feed_id")
    val serviceId = integer("service_id")
    override val primaryKey = PrimaryKey(feedId, serviceId)
}
