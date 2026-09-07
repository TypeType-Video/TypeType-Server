package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.models.ProgressItem
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.SortOrder

class ProgressService {

    suspend fun getAll(userId: String): List<ProgressItem> = DatabaseFactory.query {
        ProgressTable.selectAll()
            .where { ProgressTable.userId eq userId }
            .orderBy(ProgressTable.updatedAt to SortOrder.DESC)
            .map {
                ProgressItem(
                    videoUrl = it[ProgressTable.videoUrl],
                    position = it[ProgressTable.position],
                    updatedAt = it[ProgressTable.updatedAt],
                )
            }
    }

    suspend fun get(userId: String, videoUrl: String): ProgressItem? = DatabaseFactory.query {
        ProgressTable.selectAll()
            .where { (ProgressTable.videoUrl eq videoUrl) and (ProgressTable.userId eq userId) }
            .singleOrNull()
            ?.let {
                ProgressItem(
                    videoUrl = it[ProgressTable.videoUrl],
                    position = it[ProgressTable.position],
                    updatedAt = it[ProgressTable.updatedAt],
                )
            }
    }

    suspend fun getMany(userId: String, videoUrls: List<String>): List<ProgressItem> = DatabaseFactory.query {
        val orderedUrls = videoUrls.distinct()
        if (orderedUrls.isEmpty()) return@query emptyList()
        val progressByUrl = ProgressTable.selectAll()
            .where { (ProgressTable.userId eq userId) and (ProgressTable.videoUrl inList orderedUrls) }
            .associate { row ->
                row[ProgressTable.videoUrl] to ProgressItem(
                    videoUrl = row[ProgressTable.videoUrl],
                    position = row[ProgressTable.position],
                    updatedAt = row[ProgressTable.updatedAt],
                )
            }
        orderedUrls.map { videoUrl ->
            progressByUrl[videoUrl] ?: ProgressItem(videoUrl = videoUrl, position = 0L)
        }
    }

    suspend fun upsert(userId: String, videoUrl: String, position: Long): ProgressItem {
        val now = System.currentTimeMillis()
        val safePosition = position.coerceAtLeast(0L)
        DatabaseFactory.query {
            val updated = ProgressTable.update({ (ProgressTable.videoUrl eq videoUrl) and (ProgressTable.userId eq userId) }) {
                it[ProgressTable.position] = safePosition
                it[updatedAt] = now
            }
            if (updated == 0) {
                ProgressTable.insert {
                    it[ProgressTable.userId] = userId
                    it[ProgressTable.videoUrl] = videoUrl
                    it[ProgressTable.position] = safePosition
                    it[updatedAt] = now
                }
            }
        }
        return ProgressItem(videoUrl = videoUrl, position = safePosition, updatedAt = now)
    }
}
