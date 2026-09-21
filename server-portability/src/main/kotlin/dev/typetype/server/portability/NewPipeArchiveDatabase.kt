package dev.typetype.server.portability

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipFile

object NewPipeArchiveDatabase {
    private val databaseNames = setOf("newpipe.db", "pipepipe.db")

    fun userVersion(input: PortabilityInput): Int? {
        val entryName = databaseEntry(input) ?: return null
        return ZipFile(input.path.toFile()).use { zip ->
            val entry = requireNotNull(zip.getEntry(entryName))
            val header = zip.getInputStream(entry).use { it.readNBytes(SQLITE_HEADER_BYTES) }
            if (header.size < SQLITE_HEADER_BYTES || !header.startsWith(SQLITE_SIGNATURE)) return null
            ByteBuffer.wrap(header, USER_VERSION_OFFSET, Int.SIZE_BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .int
        }
    }

    fun <T> read(input: PortabilityInput, block: (Connection) -> T): T {
        val entryName = requireNotNull(databaseEntry(input)) { "Backup database is missing" }
        val extracted = Files.createTempFile(requireNotNull(input.path.parent), "portability-newpipe-", ".sqlite")
        try {
            ZipFile(input.path.toFile()).use { zip ->
                val entry = requireNotNull(zip.getEntry(entryName))
                zip.getInputStream(entry).use { source ->
                    Files.newOutputStream(extracted).use { target -> source.copyTo(target) }
                }
            }
            require(Files.size(extracted) in 1..PortabilityLimits.MAX_ARCHIVE_ENTRY_BYTES) {
                "Backup database is outside the allowed range"
            }
            Class.forName("org.sqlite.JDBC")
            return DriverManager.getConnection("jdbc:sqlite:file:${extracted.toAbsolutePath()}?mode=ro&immutable=1").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA query_only = ON")
                    statement.execute("PRAGMA trusted_schema = OFF")
                    statement.execute("PRAGMA cell_size_check = ON")
                }
                block(connection)
            }
        } finally {
            Files.deleteIfExists(extracted)
        }
    }

    private fun databaseEntry(input: PortabilityInput): String? = input.archive?.entries
        ?.map(PortabilityArchiveEntry::name)
        ?.filter { it.substringAfterLast('/').lowercase() in databaseNames }
        ?.singleOrNull()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val SQLITE_HEADER_BYTES = 64
    private const val USER_VERSION_OFFSET = 60
    private val SQLITE_SIGNATURE = "SQLite format 3\u0000".toByteArray()
}
