package dev.typetype.server.db.tables

import org.jetbrains.exposed.v1.core.Table

object RssFeedChannelsTable : Table("rss_feed_channels") {
    val feedId = text("feed_id")
    val channelUrl = text("channel_url")
    override val primaryKey = PrimaryKey(feedId, channelUrl)
}
