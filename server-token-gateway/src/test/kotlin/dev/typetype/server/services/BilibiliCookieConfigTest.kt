package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliCookieConfigTest {
    @Test
    fun `normalizes a browser cookie header`() {
        val config = BilibiliCookieConfig.fromRaw(
            "Cookie: SESSDATA=session; bili_jct=csrf; buvid3=device; theme=dark",
        )

        assertTrue(config.isConfigured)
        assertEquals(
            "SESSDATA=session; bili_jct=csrf; buvid3=device; theme=dark",
            config.cookieHeader,
        )
    }

    @Test
    fun `rejects malformed and unrelated cookies`() {
        assertFalse(BilibiliCookieConfig.fromRaw("theme=dark").isConfigured)
        assertNull(BilibiliCookieConfig.fromRaw("SESSDATA=session; SESSDATA=duplicate").cookieHeader)
        assertNull(BilibiliCookieConfig.fromRaw("SESSDATA=session\r\nInjected: yes").cookieHeader)
    }
}
