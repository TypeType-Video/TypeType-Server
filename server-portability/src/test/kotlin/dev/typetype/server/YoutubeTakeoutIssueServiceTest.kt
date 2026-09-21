package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutIssueService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeTakeoutIssueServiceTest {
    @Test
    fun `build aggregates duplicate issues and errors`() {
        val warnings = listOf("Unsupported CSV schema: Takeout/a.csv", "No subscription rows detected", "No subscription rows detected")
        val errors = listOf("Invalid playlist row", "Invalid playlist row", "Invalid subscription row")
        val (issues, summary) = YoutubeTakeoutIssueService.build(warnings, errors, stage = "preview")
        assertEquals(6, summary.total)
        assertEquals(3, summary.warnings)
        assertEquals(3, summary.errors)
        assertEquals(1, summary.byCode["unsupported_csv_schema"])
        assertEquals(2, summary.byCode["no_subscription_rows"])
        assertEquals(2, summary.byCode["invalid_playlist_row"])
        assertEquals(1, summary.byCode["invalid_subscription_row"])
        assertTrue(issues.all { it.stage == "preview" })
    }
}
