package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class YoutubeTakeoutImportJobArchiveStore {
    suspend fun getArchivePath(userId: String, jobId: String): String = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.selectAll()
            .where { (YoutubeTakeoutImportJobsTable.id eq jobId) and (YoutubeTakeoutImportJobsTable.userId eq userId) }
            .singleOrNull()
            ?.get(YoutubeTakeoutImportJobsTable.archivePath)
            ?: error("Import job not found")
    }
}
