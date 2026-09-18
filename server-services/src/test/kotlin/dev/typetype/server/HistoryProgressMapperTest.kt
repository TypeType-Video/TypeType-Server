package dev.typetype.server

import dev.typetype.server.services.HistoryProgressMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryProgressMapperTest {
    @Test
    fun `progress lookups stay below the postgres parameter limit`() {
        val urls = List(71_037) { index -> "https://youtube.com/watch?v=$index" }

        val batches = HistoryProgressMapper.progressLookupBatches(urls)

        assertEquals(urls, batches.flatten())
        assertTrue(batches.all { it.size <= 1_000 })
    }
}
