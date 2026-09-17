package dev.typetype.server

import dev.typetype.server.services.YoutubeTakeoutDateParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class YoutubeTakeoutDateParserLocaleTest {
    @Test
    fun `parses CLDR periods used by Chinese Japanese and Korean Takeout exports`() {
        assertEquals(
            1_788_576_327_000L,
            YoutubeTakeoutDateParser.parseEpochMillis("2026年9月4日 晚上9:45:27 CST"),
        )
        assertEquals(
            1_788_525_927_000L,
            YoutubeTakeoutDateParser.parseEpochMillis("2026年9月4日 午後9:45:27 JST"),
        )
        assertNotNull(YoutubeTakeoutDateParser.parseEpochMillis("2026년 9월 4일 오후 9:45:27 KST"))
    }

    @Test
    fun `parses year first and localized day period variants`() {
        assertNotNull(YoutubeTakeoutDateParser.parseEpochMillis("2026/09/16 18:02:08 CEST"))
        assertNotNull(YoutubeTakeoutDateParser.parseEpochMillis("2026-09-16, 18:02:08 CEST"))
        assertNotNull(YoutubeTakeoutDateParser.parseEpochMillis("4 سبتمبر 2026, 9:45:27 مساءً CEST"))
        assertNotNull(YoutubeTakeoutDateParser.parseEpochMillis("4 सितंबर 2026, 9:45:27 शाम CEST"))
    }
}
