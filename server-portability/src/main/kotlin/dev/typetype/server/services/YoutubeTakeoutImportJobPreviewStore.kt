package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeTakeoutImportJobPreviewStore {
    suspend fun persistPreview(jobId: String, previewJson: String) = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.update({ YoutubeTakeoutImportJobsTable.id eq jobId }) {
            it[YoutubeTakeoutImportJobsTable.previewJson] = previewJson
            it[YoutubeTakeoutImportJobsTable.updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun getPreview(userId: String, jobId: String): String? = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.selectAll()
            .where { (YoutubeTakeoutImportJobsTable.id eq jobId) and (YoutubeTakeoutImportJobsTable.userId eq userId) }
            .singleOrNull()
            ?.get(YoutubeTakeoutImportJobsTable.previewJson)
    }
}
