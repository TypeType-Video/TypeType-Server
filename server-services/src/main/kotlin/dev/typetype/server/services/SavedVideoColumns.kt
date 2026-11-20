package dev.typetype.server.services

import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.WatchLaterTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

internal class SavedVideoColumns(
    private val table: Table,
    private val userId: Column<String>,
    private val url: Column<String>,
    private val title: Column<String>,
    private val thumbnail: Column<String>,
    private val duration: Column<Long>,
    private val channelName: Column<String>,
    private val channelUrl: Column<String>,
    private val channelAvatar: Column<String>,
    private val viewCount: Column<Long>,
    private val publishedAt: Column<Long>,
    private val refreshedAt: Column<Long>,
) {
    fun candidateUrls(owner: String, now: Long): List<String> = table.select(url, refreshedAt)
        .where { (userId eq owner) and needsRefresh(now) }
        .orderBy(refreshedAt to SortOrder.ASC)
        .map { it[url] }
        .distinct()

    fun apply(owner: String, item: VideoMetadataItem, now: Long): Int =
        table.update({ (userId eq owner) and (url eq item.url) }) {
            if (item.title.isNotBlank()) it[title] = item.title
            if (item.thumbnail.isNotBlank()) it[thumbnail] = item.thumbnail
            if (item.duration > 0L) it[duration] = item.duration
            if (item.channelName.isNotBlank()) it[channelName] = item.channelName
            if (item.channelUrl.isNotBlank()) it[channelUrl] = item.channelUrl
            if (item.channelAvatar.isNotBlank()) it[channelAvatar] = item.channelAvatar
            if (item.viewCount > 0L) it[viewCount] = item.viewCount
            if (item.publishedAt > 0L) it[publishedAt] = item.publishedAt
            it[refreshedAt] = now
        }

    fun touch(owner: String, urls: Collection<String>, now: Long): Int =
        table.update({ (userId eq owner) and (url inList urls) }) { it[refreshedAt] = now }

    private fun needsRefresh(now: Long) =
        (title like FALLBACK_TITLE_PATTERN) or
            (thumbnail eq "") or
            (duration lessEq 0L) or
            (channelName eq "") or
            (channelUrl eq "") or
            (refreshedAt less now - STALE_AFTER_MS) or
            ((channelAvatar eq "") and (refreshedAt less now - MISSING_AVATAR_RETRY_MS))

    companion object {
        private const val FALLBACK_TITLE_PATTERN = "YouTube video %"
        private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
        private const val MISSING_AVATAR_RETRY_MS = 60 * 60 * 1000L

        val playlistVideos = with(PlaylistVideosTable) {
            SavedVideoColumns(
                this, userId, url, title, thumbnail, duration,
                channelName, channelUrl, channelAvatar, viewCount, publishedAt, metadataRefreshedAt,
            )
        }

        val watchLater = with(WatchLaterTable) {
            SavedVideoColumns(
                this, userId, url, title, thumbnail, duration,
                channelName, channelUrl, channelAvatar, viewCount, publishedAt, metadataRefreshedAt,
            )
        }

        val favorites = with(FavoritesTable) {
            SavedVideoColumns(
                this, userId, videoUrl, title, thumbnail, duration,
                channelName, channelUrl, channelAvatar, viewCount, publishedAt, metadataRefreshedAt,
            )
        }

        val all = listOf(playlistVideos, watchLater, favorites)
    }
}
