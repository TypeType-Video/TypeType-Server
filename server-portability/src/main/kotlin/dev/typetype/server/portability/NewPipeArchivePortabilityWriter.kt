package dev.typetype.server.portability

import java.io.OutputStream
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NewPipeArchivePortabilityWriter {
    fun write(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
        target: NewPipeArchiveTarget,
    ) {
        val database = Files.createTempFile("typetype-portability-", ".db")
        try {
            DriverManager.getConnection("jdbc:sqlite:$database").use { db ->
                db.autoCommit = false
                NewPipeArchiveSchema.create(db, target)
                NewPipeArchiveRecordWriter(db, target).write(source, categories)
                db.commit()
            }
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("newpipe.db"))
                Files.newInputStream(database).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } finally {
            Files.deleteIfExists(database)
            Files.deleteIfExists(database.resolveSibling("${database.fileName}-journal"))
        }
    }
}
