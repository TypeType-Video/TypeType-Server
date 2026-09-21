package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class YoutubeTakeoutImportJobReportStore {
    suspend fun persistReport(jobId: String, reportJson: String) = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.update({ YoutubeTakeoutImportJobsTable.id eq jobId }) {
            it[YoutubeTakeoutImportJobsTable.reportJson] = reportJson
            it[YoutubeTakeoutImportJobsTable.updatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun getReport(userId: String, jobId: String): String? = DatabaseFactory.query {
        YoutubeTakeoutImportJobsTable.selectAll()
            .where { (YoutubeTakeoutImportJobsTable.id eq jobId) and (YoutubeTakeoutImportJobsTable.userId eq userId) }
            .singleOrNull()
            ?.get(YoutubeTakeoutImportJobsTable.reportJson)
    }
}
