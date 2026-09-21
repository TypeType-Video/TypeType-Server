package dev.typetype.server.services

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object PipePipeBackupZipExtractor {
    fun extractDatabase(backupZipPath: Path): Path {
        val extractedDb = Files.createTempFile("pipepipe-db-", ".sqlite")
        try {
            ZipFile(backupZipPath.toFile()).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                if (entries.size > PipePipeBackupLimits.MAX_ZIP_ENTRIES) throw IllegalArgumentException("Backup archive has too many entries")
                val dbEntries = entries.filter { entry ->
                    val name = entry.name.substringAfterLast('/').lowercase()
                    name == "newpipe.db" || name == "pipepipe.db"
                }
                if (dbEntries.size != 1) throw IllegalArgumentException("Missing backup database file")
                val dbEntry = dbEntries.first()
                val uncompressed = dbEntry.size
                val compressed = dbEntry.compressedSize
                if (uncompressed <= 0L || uncompressed > PipePipeBackupLimits.MAX_DB_BYTES) throw IllegalArgumentException("Backup database is too large")
                if (compressed > 0L && uncompressed.toDouble() / compressed.toDouble() > PipePipeBackupLimits.MAX_COMPRESSION_RATIO) {
                    throw IllegalArgumentException("Invalid compressed backup")
                }
                zip.getInputStream(dbEntry).use { input ->
                    Files.newOutputStream(extractedDb).use { output ->
                        input.copyTo(output)
                    }
                }
                val extractedSize = Files.size(extractedDb)
                if (extractedSize <= 0L || extractedSize > PipePipeBackupLimits.MAX_DB_BYTES) throw IllegalArgumentException("Backup database is too large")
            }
            return extractedDb
        } catch (e: Exception) {
            Files.deleteIfExists(extractedDb)
            throw e
        }
    }
}
