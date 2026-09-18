package dev.typetype.server

import dev.typetype.server.services.PipePipeBackupHistoryItem
import dev.typetype.server.services.PipePipeBackupTimelineNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PipePipeBackupTimelineNormalizerTest {

    @Test
    fun `normalize keeps order and remaps into recent window`() {
        val items = listOf(
            history("a", 1000),
            history("b", 2000),
            history("c", 3000),
        )
        val normalized = PipePipeBackupTimelineNormalizer.normalize(items)
        assertEquals(listOf("c", "b", "a"), normalized.map { it.title })
        val min = normalized.minOf { it.watchedAt }
        val max = normalized.maxOf { it.watchedAt }
        val now = System.currentTimeMillis()
        assertTrue(max <= now && min >= now - 31L * 24 * 60 * 60 * 1000)
    }

    private fun history(title: String, watchedAt: Long): PipePipeBackupHistoryItem {
        return PipePipeBackupHistoryItem(
            watchedAt = watchedAt,
            url = "https://$title",
            title = title,
            duration = 1L,
            uploader = "uploader",
            uploaderUrl = "https://uploader",
            thumbnail = "",
        )
    }
}
