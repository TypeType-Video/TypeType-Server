package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PortabilitySpoolTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `spool deduplicates stable records and preserves insertion order`() {
        val spool = PortabilitySpool.create(directory)
        val first = PortabilitySubscription("https://youtube.com/channel/one", "One")
        val duplicate = first.copy(name = "Renamed")
        val second = PortabilitySubscription("https://youtube.com/channel/two", "Two")

        assertTrue(spool.write(first).inserted)
        assertFalse(spool.write(duplicate).inserted)
        assertTrue(spool.write(second).inserted)

        val records = mutableListOf<PortabilityRecord>()
        spool.forEach(PortabilityCategory.SUBSCRIPTIONS, records::add)
        assertEquals(listOf(first, second), records)
        assertEquals(mapOf(PortabilityCategory.SUBSCRIPTIONS to 2L), spool.counts())
        assertEquals(1L, spool.duplicateCount())
        spool.delete()
    }

    @Test
    fun `spool aggregates matching issues`() {
        val spool = PortabilitySpool.create(directory)
        val issue = PortabilityIssue(
            PortabilityCategory.SETTINGS,
            "unsupported_setting",
            "A source setting cannot be represented",
            count = 2,
        )
        spool.issue(issue)
        spool.issue(issue.copy(count = 3))

        assertEquals(listOf(issue.copy(count = 5)), spool.issues())
        spool.delete()
    }

    @Test
    fun `spool preserves present empty categories`() {
        val spool = PortabilitySpool.create(directory)

        spool.markCategory(PortabilityCategory.HISTORY)

        assertEquals(mapOf(PortabilityCategory.HISTORY to 0L), spool.counts())
        assertEquals(setOf(PortabilityCategory.HISTORY), spool.categories())
        spool.delete()
    }
}
