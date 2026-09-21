package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import dev.typetype.server.models.YoutubeTakeoutImportJobStatus
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeTakeoutImportJobStatusStore {
    suspend fun getStatus(userId: String, jobId: String): YoutubeTakeoutImportJobStatus? = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.selectAll()
            .where { (YoutubeTakeoutImportJobsTable.id eq jobId) and (YoutubeTakeoutImportJobsTable.userId eq userId) }
            .singleOrNull()
            ?.let { row ->
                YoutubeTakeoutImportJobStatus(
                    jobId = row[YoutubeTakeoutImportJobsTable.id],
                    status = row[YoutubeTakeoutImportJobsTable.status],
                    phase = row[YoutubeTakeoutImportJobsTable.phase],
                    progress = row[YoutubeTakeoutImportJobsTable.progress],
                    createdAt = row[YoutubeTakeoutImportJobsTable.createdAt],
                    updatedAt = row[YoutubeTakeoutImportJobsTable.updatedAt],
                    expiresAt = row[YoutubeTakeoutImportJobsTable.expiresAt],
                    error = row[YoutubeTakeoutImportJobsTable.error],
                )
            }
    }

    suspend fun updateStatus(jobId: String, status: String, phase: String, progress: Int) = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.update({ YoutubeTakeoutImportJobsTable.id eq jobId }) {
            it[YoutubeTakeoutImportJobsTable.status] = status
            it[YoutubeTakeoutImportJobsTable.phase] = phase
            it[YoutubeTakeoutImportJobsTable.progress] = progress.coerceIn(0, 100)
            it[YoutubeTakeoutImportJobsTable.error] = null
            it[YoutubeTakeoutImportJobsTable.updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun failStatus(jobId: String, phase: String, error: String) = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.update({ YoutubeTakeoutImportJobsTable.id eq jobId }) {
            it[YoutubeTakeoutImportJobsTable.status] = "failed"
            it[YoutubeTakeoutImportJobsTable.phase] = phase
            it[YoutubeTakeoutImportJobsTable.progress] = 100
            it[YoutubeTakeoutImportJobsTable.error] = error.take(MAX_ERROR_LENGTH)
            it[YoutubeTakeoutImportJobsTable.updatedAt] = System.currentTimeMillis()
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 500
    }
}
