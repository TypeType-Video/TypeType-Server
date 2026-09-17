package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutPathHints
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeTakeoutPathHintsTest {
    @Test
    fun `recognizes localized history paths after accent normalization`() {
        assertTrue(YoutubeTakeoutPathHints.isHistoryEntry("Takeout/YouTube/lịch sử xem.html"))
        assertTrue(YoutubeTakeoutPathHints.isHistoryEntry("Takeout/YouTube/ประวัติการรับชม.html"))
        assertTrue(YoutubeTakeoutPathHints.isHistoryEntry("Takeout/YouTube/Wiedergabeverlauf.html"))
    }
}
