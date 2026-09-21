package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import java.nio.file.Path
import java.util.UUID

class YoutubeTakeoutImportJobStore {
    suspend fun create(userId: String, archivePath: Path): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        DatabaseFactory.query {
            YoutubeTakeoutImportJobsTable.insert {
                it[YoutubeTakeoutImportJobsTable.id] = id
                it[YoutubeTakeoutImportJobsTable.userId] = userId
                it[status] = "pending"
                it[phase] = "pending"
                it[progress] = 0
                it[parseCompleted] = false
                it[importStarted] = false
                it[importCompleted] = false
                it[YoutubeTakeoutImportJobsTable.archivePath] = archivePath.toString()
                it[previewJson] = null
                it[error] = null
                it[reportJson] = null
                it[createdAt] = now
                it[updatedAt] = now
                it[expiresAt] = now + YoutubeTakeoutLimits.JOB_TTL_MS
            }
        }
        return id
    }
}
