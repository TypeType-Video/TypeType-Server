package dev.typetype.server.portability

import dev.typetype.server.cache.CacheJson
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.EnumSet

class PortabilitySpool private constructor(
    val path: Path,
    private val connection: Connection,
) : PortabilityRecordSink, PortabilityRecordSource, Closeable {
    private val insertRecord = connection.prepareStatement(
        "INSERT OR IGNORE INTO records(category, stable_key, parent_key, payload) VALUES (?, ?, ?, ?)",
    )
    private val markCategory = connection.prepareStatement(
        "INSERT OR IGNORE INTO categories(category) VALUES (?)",
    )
    private val upsertIssue = connection.prepareStatement(
        """
        INSERT INTO issues(category, code, message, item_count) VALUES (?, ?, ?, ?)
        ON CONFLICT(category, code, message) DO UPDATE SET item_count = item_count + excluded.item_count
        """.trimIndent(),
    )
    private val putLookup = connection.prepareStatement(
        "INSERT OR REPLACE INTO lookups(namespace, lookup_key, value) VALUES (?, ?, ?)",
    )
    private val getLookup = connection.prepareStatement(
        "SELECT value FROM lookups WHERE namespace = ? AND lookup_key = ?",
    )
    private val markedCategories = EnumSet.noneOf(PortabilityCategory::class.java)
    private var pendingWrites = 0
    private var attemptedRecords = 0L
    private var closed = false

    override fun markCategory(category: PortabilityCategory) {
        checkOpen()
        if (!markedCategories.add(category)) return
        markCategory.setString(1, category.wireName)
        markCategory.executeUpdate()
        pendingWrites += 1
        flushWhenNeeded()
    }

    override fun write(record: PortabilityRecord): PortabilityWriteResult {
        checkOpen()
        markCategory(record.category)
        require(attemptedRecords < PortabilityLimits.MAX_RECORDS) { "Backup contains too many records" }
        val payload = CacheJson.encodeToString<PortabilityRecord>(record)
        require(payload.toByteArray().size <= PortabilityLimits.MAX_RECORD_JSON_BYTES) {
            "Backup record is too large"
        }
        insertRecord.setString(1, record.category.wireName)
        insertRecord.setString(2, portabilityStableHash(record.stableKey()))
        insertRecord.setString(3, record.parentKey()?.let(::portabilityStableHash))
        insertRecord.setString(4, payload)
        attemptedRecords += 1
        val inserted = insertRecord.executeUpdate() == 1
        pendingWrites += 1
        flushWhenNeeded()
        return PortabilityWriteResult(inserted)
    }

    override fun issue(issue: PortabilityIssue) {
        checkOpen()
        require(issue.code.isNotBlank() && issue.message.isNotBlank() && issue.count > 0L)
        upsertIssue.setString(1, issue.category?.wireName ?: "")
        upsertIssue.setString(2, issue.code)
        upsertIssue.setString(3, issue.message)
        upsertIssue.setLong(4, issue.count)
        upsertIssue.executeUpdate()
        pendingWrites += 1
        flushWhenNeeded()
    }

    override fun putLookup(namespace: String, key: String, value: String) {
        checkOpen()
        require(namespace.isNotBlank() && key.isNotBlank())
        require(value.toByteArray().size <= PortabilityLimits.MAX_RECORD_JSON_BYTES) { "Lookup value is too large" }
        putLookup.setString(1, namespace)
        putLookup.setString(2, portabilityStableHash(key.trim().lowercase()))
        putLookup.setString(3, value)
        putLookup.executeUpdate()
        pendingWrites += 1
        flushWhenNeeded()
    }

    override fun lookup(namespace: String, key: String): String? {
        checkOpen()
        flush()
        getLookup.setString(1, namespace)
        getLookup.setString(2, portabilityStableHash(key.trim().lowercase()))
        return getLookup.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
    }

    override fun categories(): Set<PortabilityCategory> = counts().keys

    override fun counts(): Map<PortabilityCategory, Long> {
        flush()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT categories.category, COUNT(records.ordinal)
                FROM categories
                LEFT JOIN records ON records.category = categories.category
                GROUP BY categories.category
                ORDER BY categories.category
                """.trimIndent(),
            ).use { rows ->
                return buildMap {
                    while (rows.next()) {
                        val category = portabilityCategoryByWireName(rows.getString(1))
                        put(category, rows.getLong(2))
                    }
                }
            }
        }
    }

    fun duplicateCount(): Long = attemptedRecords - counts().values.sum()

    fun issues(): List<PortabilityIssue> {
        flush()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT category, code, message, item_count FROM issues ORDER BY rowid",
            ).use { rows ->
                return buildList {
                    while (rows.next()) {
                        add(
                            PortabilityIssue(
                                category = rows.getString(1)
                                    .takeIf(String::isNotBlank)
                                    ?.let(::portabilityCategoryByWireName),
                                code = rows.getString(2),
                                message = rows.getString(3),
                                count = rows.getLong(4),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun forEach(category: PortabilityCategory, block: (PortabilityRecord) -> Unit) {
        flush()
        connection.prepareStatement(
            "SELECT payload FROM records WHERE category = ? ORDER BY ordinal",
        ).use { statement ->
            statement.setString(1, category.wireName)
            statement.executeQuery().use { rows ->
                while (rows.next()) block(CacheJson.decodeFromString<PortabilityRecord>(rows.getString(1)))
            }
        }
    }

    override fun forEachChild(
        category: PortabilityCategory,
        parentKey: String,
        block: (PortabilityRecord) -> Unit,
    ) {
        flush()
        connection.prepareStatement(
            "SELECT payload FROM records WHERE category = ? AND parent_key = ? ORDER BY ordinal",
        ).use { statement ->
            statement.setString(1, category.wireName)
            statement.setString(2, portabilityStableHash(parentKey.trim().lowercase()))
            statement.executeQuery().use { rows ->
                while (rows.next()) block(CacheJson.decodeFromString<PortabilityRecord>(rows.getString(1)))
            }
        }
    }

    fun flush() {
        checkOpen()
        if (pendingWrites == 0) return
        connection.commit()
        pendingWrites = 0
    }

    override fun close() {
        if (closed) return
        runCatching { flush() }
        insertRecord.close()
        markCategory.close()
        upsertIssue.close()
        putLookup.close()
        getLookup.close()
        connection.close()
        closed = true
    }

    fun delete() {
        close()
        Files.deleteIfExists(path)
        Files.deleteIfExists(path.resolveSibling("${path.fileName}-wal"))
        Files.deleteIfExists(path.resolveSibling("${path.fileName}-shm"))
    }

    private fun flushWhenNeeded() {
        if (pendingWrites >= COMMIT_INTERVAL) flush()
    }

    private fun checkOpen() = check(!closed) { "Portability spool is closed" }

    companion object {
        private const val COMMIT_INTERVAL = 500

        fun create(directory: Path): PortabilitySpool = PortabilitySpoolSchema.create(directory) { path, connection ->
            PortabilitySpool(path, connection)
        }
    }
}
