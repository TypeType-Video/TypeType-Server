package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeTakeoutImportJobFlagsStore {
    suspend fun getFlags(userId: String, jobId: String): YoutubeTakeoutImportJobFlags = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.selectAll()
            .where { (YoutubeTakeoutImportJobsTable.id eq jobId) and (YoutubeTakeoutImportJobsTable.userId eq userId) }
            .singleOrNull()
            ?.let { row ->
                YoutubeTakeoutImportJobFlags(
                    parseCompleted = row[YoutubeTakeoutImportJobsTable.parseCompleted],
                    importStarted = row[YoutubeTakeoutImportJobsTable.importStarted],
                    importCompleted = row[YoutubeTakeoutImportJobsTable.importCompleted],
                )
            }
            ?: error("Import job not found")
    }

    suspend fun setParseCompleted(jobId: String) = setFlags(jobId, parseCompleted = true)

    suspend fun setImportStarted(jobId: String) = setFlags(jobId, importStarted = true)

    suspend fun setImportCompleted(jobId: String) = setFlags(jobId, importCompleted = true)

    private suspend fun setFlags(jobId: String, parseCompleted: Boolean? = null, importStarted: Boolean? = null, importCompleted: Boolean? = null) {
        DatabaseFactory.query {
            YoutubeTakeoutImportJobsTable.update({ YoutubeTakeoutImportJobsTable.id eq jobId }) {
                if (parseCompleted != null) it[YoutubeTakeoutImportJobsTable.parseCompleted] = parseCompleted
                if (importStarted != null) it[YoutubeTakeoutImportJobsTable.importStarted] = importStarted
                if (importCompleted != null) it[YoutubeTakeoutImportJobsTable.importCompleted] = importCompleted
                it[YoutubeTakeoutImportJobsTable.updatedAt] = System.currentTimeMillis()
            }
        }
    }
}
