package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.FavoritesTable
import dev.typetype.server.models.FavoriteItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class FavoritesService {

    suspend fun get(userId: String, videoUrl: String): FavoriteItem? = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { FavoritesTable.userId eq userId and (FavoritesTable.videoUrl eq videoUrl) }
            .limit(1)
            .firstOrNull()
            ?.toItem()
    }

    suspend fun getAll(userId: String): List<FavoriteItem> = DatabaseFactory.query {
        FavoritesTable.selectAll()
            .where { FavoritesTable.userId eq userId }
            .orderBy(FavoritesTable.favoritedAt to SortOrder.DESC)
            .map { it.toItem() }
    }

    suspend fun add(userId: String, videoUrl: String): FavoriteItem = add(userId, FavoriteItem(videoUrl = videoUrl))

    suspend fun add(userId: String, item: FavoriteItem): FavoriteItem {
        val now = item.favoritedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        DatabaseFactory.query {
            FavoritesTable.insert {
                it[FavoritesTable.userId] = userId
                it[videoUrl] = item.videoUrl
                it[favoritedAt] = now
                it[title] = item.title
                it[thumbnail] = item.thumbnail
                it[duration] = item.duration
                it[channelName] = item.channelName
                it[channelUrl] = item.channelUrl
                it[channelAvatar] = item.channelAvatar
                it[viewCount] = item.viewCount
                it[publishedAt] = item.publishedAt
            }
        }
        return item.copy(favoritedAt = now)
    }

    suspend fun delete(userId: String, videoUrl: String): Boolean = DatabaseFactory.query {
        FavoritesTable.deleteWhere { FavoritesTable.videoUrl eq videoUrl and (FavoritesTable.userId eq userId) } > 0
    }

    private fun ResultRow.toItem() = FavoriteItem(
        videoUrl = this[FavoritesTable.videoUrl],
        favoritedAt = this[FavoritesTable.favoritedAt],
        title = this[FavoritesTable.title],
        thumbnail = this[FavoritesTable.thumbnail],
        duration = this[FavoritesTable.duration],
        channelName = this[FavoritesTable.channelName],
        channelUrl = this[FavoritesTable.channelUrl],
        channelAvatar = this[FavoritesTable.channelAvatar],
        viewCount = this[FavoritesTable.viewCount],
        publishedAt = this[FavoritesTable.publishedAt],
    )
}
