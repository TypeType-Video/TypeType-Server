package dev.typetype.server.services

import dev.typetype.server.models.RestorePipePipeResultItem
import java.nio.file.Files
import java.nio.file.Path

class PipePipeBackupImporterService(
    private val sqliteReader: PipePipeBackupSqliteReader = PipePipeBackupSqliteReader(),
    private val persister: PipePipeBackupPersisterService = PipePipeBackupPersisterService(),
) {

    suspend fun restore(userId: String, backupZipPath: Path, timeMode: PipePipeBackupTimeMode): RestorePipePipeResultItem {
        val sqlitePath = PipePipeBackupZipExtractor.extractDatabase(backupZipPath)
        return try {
            val rawSnapshot = sqliteReader.read(sqlitePath)
            val snapshot = PipePipeBackupSnapshotTimeMode.apply(rawSnapshot, timeMode)
            val result = persister.persist(userId, snapshot)
            result.counts.copy(
                timeMode = timeMode.wireValue,
                historyMinWatchedAt = result.historyMinWatchedAt,
                historyMaxWatchedAt = result.historyMaxWatchedAt,
            )
        } finally {
            Files.deleteIfExists(sqlitePath)
            Files.deleteIfExists(backupZipPath)
        }
    }
}
