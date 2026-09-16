package dev.typetype.server.portability

import dev.typetype.server.TEST_USER_ID
import dev.typetype.server.TestDatabase
import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.HistoryTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class TypeTypePortabilityHistoryImportTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `history import batches records and skips existing and source duplicates`() = runBlocking {
        insertExisting(history(0))
        val records = (0..1_000).map(::history) + history(1_000)
        var processed = 0L

        val imported = DatabaseFactory.query {
            TypeTypePortabilityHistoryImport.write(
                TEST_USER_ID,
                Source(records),
                PortabilityDuplicatePolicy.SKIP,
            ) { processed++ }
        }

        assertEquals(1_000L, imported)
        assertEquals(1_002L, processed)
        assertEquals(1_001L, transaction { HistoryTable.selectAll().count() })
    }

    private suspend fun insertExisting(record: PortabilityHistory) {
        DatabaseFactory.query {
            HistoryTable.insert {
                it[HistoryTable.id] = UUID.randomUUID().toString()
                it[HistoryTable.userId] = TEST_USER_ID
                it[HistoryTable.url] = record.video.url
                it[HistoryTable.title] = record.video.title
                it[HistoryTable.thumbnail] = record.video.thumbnailUrl
                it[HistoryTable.channelName] = record.video.channelName
                it[HistoryTable.channelUrl] = record.video.channelUrl
                it[HistoryTable.channelAvatar] = record.video.channelAvatarUrl
                it[HistoryTable.duration] = record.video.durationSeconds
                it[HistoryTable.progress] = record.positionSeconds
                it[HistoryTable.watchedAt] = record.watchedAt
            }
        }
    }

    private fun history(index: Int) = PortabilityHistory(
        video = PortabilityVideo(
            url = "https://video.example/$index",
            title = "Video $index",
            thumbnailUrl = "",
            durationSeconds = 60L,
            channelName = "Channel",
            channelUrl = "https://channel.example",
        ),
        watchedAt = index.toLong(),
    )
}

private class Source(private val records: List<PortabilityRecord>) : PortabilityRecordSource {
    override fun categories() = setOf(PortabilityCategory.HISTORY)

    override fun counts() = mapOf(PortabilityCategory.HISTORY to records.size.toLong())

    override fun forEach(category: PortabilityCategory, block: (PortabilityRecord) -> Unit) {
        if (category == PortabilityCategory.HISTORY) records.forEach(block)
    }
}
