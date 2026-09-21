package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutPlaylistKeysTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeTakeoutPlaylistKeyService {
    suspend fun getMappings(userId: String): Map<String, String> = DatabaseFactory.query {
        YoutubeTakeoutPlaylistKeysTable.selectAll()
            .where { YoutubeTakeoutPlaylistKeysTable.userId eq userId }
            .associate { row -> row[YoutubeTakeoutPlaylistKeysTable.sourceKey] to row[YoutubeTakeoutPlaylistKeysTable.playlistId] }
    }

    suspend fun putMapping(userId: String, sourceKey: String, playlistId: String) = DatabaseFactory.query {
        val normalizedKey = sourceKey.trim().lowercase()
        val existing = YoutubeTakeoutPlaylistKeysTable.selectAll()
            .where { (YoutubeTakeoutPlaylistKeysTable.userId eq userId) and (YoutubeTakeoutPlaylistKeysTable.sourceKey eq normalizedKey) }
            .singleOrNull()
        if (existing == null) {
            YoutubeTakeoutPlaylistKeysTable.insert {
                it[YoutubeTakeoutPlaylistKeysTable.userId] = userId
                it[YoutubeTakeoutPlaylistKeysTable.sourceKey] = normalizedKey
                it[YoutubeTakeoutPlaylistKeysTable.playlistId] = playlistId
                it[updatedAt] = System.currentTimeMillis()
            }
        } else {
            YoutubeTakeoutPlaylistKeysTable.update({ (YoutubeTakeoutPlaylistKeysTable.userId eq userId) and (YoutubeTakeoutPlaylistKeysTable.sourceKey eq normalizedKey) }) {
                it[YoutubeTakeoutPlaylistKeysTable.playlistId] = playlistId
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }
}
