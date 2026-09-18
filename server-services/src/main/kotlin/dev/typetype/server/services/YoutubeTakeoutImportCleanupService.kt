package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeTakeoutImportJobsTable
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll

class YoutubeTakeoutImportCleanupService(private val privacyService: YoutubeTakeoutPrivacyService) {
    suspend fun purgeExpiredJobs() {
        val now = System.currentTimeMillis()
        val archives = DatabaseFactory.query {
            YoutubeTakeoutImportJobsTable.selectAll().where { YoutubeTakeoutImportJobsTable.expiresAt lessEq now }
                .mapNotNull { it[YoutubeTakeoutImportJobsTable.archivePath] }
        }
        archives.forEach { privacyService.deleteArchive(it) }
        DatabaseFactory.query {
            YoutubeTakeoutImportJobsTable.deleteWhere { YoutubeTakeoutImportJobsTable.expiresAt lessEq now }
        }
    }
}
