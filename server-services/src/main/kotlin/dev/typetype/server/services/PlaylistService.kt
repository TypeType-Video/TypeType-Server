package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PlaylistVideosTable
import dev.typetype.server.db.tables.PlaylistsTable
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistReorderResult
import dev.typetype.server.models.PlaylistVideoItem
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class PlaylistService {
    suspend fun getAll(userId: String): List<PlaylistItem> = DatabaseFactory.query {
        val playlists = PlaylistsTable.selectAll()
            .where { PlaylistsTable.userId eq userId }
            .orderBy(PlaylistsTable.createdAt to SortOrder.DESC)
            .toList()
        val videoCount = PlaylistVideosTable.id.count()
        val videoCounts = PlaylistVideosTable
            .select(PlaylistVideosTable.playlistId, videoCount)
            .where { PlaylistVideosTable.userId eq userId }
            .groupBy(PlaylistVideosTable.playlistId)
            .associate { row -> row[PlaylistVideosTable.playlistId] to row[videoCount].toInt() }
        playlists.map { row ->
            row.toPlaylistSummary(videoCounts[row[PlaylistsTable.id]] ?: 0)
        }
    }

    suspend fun getById(userId: String, id: String): PlaylistItem? = DatabaseFactory.query {
        val row = PlaylistsTable.selectAll().where { (PlaylistsTable.id eq id) and (PlaylistsTable.userId eq userId) }.singleOrNull() ?: return@query null
        val videoRows = PlaylistVideosTable.selectAll()
            .where { (PlaylistVideosTable.playlistId eq id) and (PlaylistVideosTable.userId eq userId) }
            .orderBy(PlaylistVideosTable.position to SortOrder.ASC)
            .toList()
        val progressByUrl = playlistProgressByUrl(userId, videoRows.map { it[PlaylistVideosTable.url] })
        val videos = videoRows.map { it.toPlaylistVideoItem(progressByUrl) }
        PlaylistItem(id = row[PlaylistsTable.id], name = row[PlaylistsTable.name], description = row[PlaylistsTable.description], videos = videos, createdAt = row[PlaylistsTable.createdAt])
    }

    suspend fun create(userId: String, item: PlaylistItem): PlaylistItem {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            PlaylistsTable.insert {
                it[PlaylistsTable.id] = id
                it[PlaylistsTable.userId] = userId
                it[name] = item.name
                it[description] = item.description
                it[createdAt] = now
            }
        }
        return item.copy(id = id, createdAt = now, videos = emptyList())
    }

    suspend fun update(userId: String, id: String, item: PlaylistItem): Boolean = DatabaseFactory.query {
        PlaylistsTable.update({ (PlaylistsTable.id eq id) and (PlaylistsTable.userId eq userId) }) {
            it[name] = item.name
            it[description] = item.description
        } > 0
    }

    suspend fun delete(userId: String, id: String): Boolean = DatabaseFactory.query {
        PlaylistsTable.deleteWhere { (PlaylistsTable.id eq id) and (PlaylistsTable.userId eq userId) } > 0
    }

    suspend fun addVideo(userId: String, playlistId: String, video: PlaylistVideoItem): PlaylistVideoItem {
        val videoId = UUID.randomUUID().toString()
        val now = video.addedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val pos = DatabaseFactory.query { PlaylistVideosTable.selectAll().where { (PlaylistVideosTable.playlistId eq playlistId) and (PlaylistVideosTable.userId eq userId) }.count().toInt() }
        DatabaseFactory.query {
            PlaylistVideosTable.insert {
                it[PlaylistVideosTable.id] = videoId
                it[PlaylistVideosTable.playlistId] = playlistId
                it[PlaylistVideosTable.userId] = userId
                it[url] = video.url
                it[title] = video.title
                it[thumbnail] = video.thumbnail
                it[duration] = video.duration
                it[position] = pos
                it[channelName] = video.channelName
                it[channelUrl] = video.channelUrl
                it[channelAvatar] = video.channelAvatar
                it[viewCount] = video.viewCount
                it[addedAt] = now
                it[publishedAt] = video.publishedAt
            }
        }
        return video.copy(id = videoId, position = pos, addedAt = now)
    }

    suspend fun removeVideo(userId: String, playlistId: String, videoUrl: String): Boolean = DatabaseFactory.query {
        PlaylistVideosTable.deleteWhere { (PlaylistVideosTable.playlistId eq playlistId) and (PlaylistVideosTable.url eq videoUrl) and (PlaylistVideosTable.userId eq userId) } > 0
    }

    suspend fun reorder(userId: String, playlistId: String, order: List<String>): PlaylistReorderResult = DatabaseFactory.query {
        val playlistExists = PlaylistsTable.selectAll()
            .where { (PlaylistsTable.id eq playlistId) and (PlaylistsTable.userId eq userId) }
            .any()
        if (!playlistExists) return@query PlaylistReorderResult.NotFound
        val currentUrls = PlaylistVideosTable.selectAll()
            .where { (PlaylistVideosTable.playlistId eq playlistId) and (PlaylistVideosTable.userId eq userId) }
            .map { it[PlaylistVideosTable.url] }
        if (order.size != order.distinct().size) return@query PlaylistReorderResult.InvalidOrder("Duplicate video URLs")
        if (order.toSet() != currentUrls.toSet()) return@query PlaylistReorderResult.InvalidOrder("Order must include every playlist video URL exactly once")
        order.forEachIndexed { index, url ->
            PlaylistVideosTable.update({ (PlaylistVideosTable.playlistId eq playlistId) and (PlaylistVideosTable.userId eq userId) and (PlaylistVideosTable.url eq url) }) {
                it[position] = index
            }
        }
        PlaylistReorderResult.Success
    }
}
