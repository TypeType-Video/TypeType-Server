package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class BilibiliCookieConfigTest {
    @Test
    fun `normalizes a browser cookie header`() {
        val config = BilibiliCookieConfig.fromRaw(
            "Cookie: SESSDATA=session; bili_jct=csrf; buvid3=device; theme=dark",
        )

        assertTrue(config.wasProvided)
        assertTrue(config.isConfigured)
        assertEquals(
            "SESSDATA=session; bili_jct=csrf; buvid3=device; theme=dark",
            config.cookieHeader,
        )
    }

    @Test
    fun `reads a mounted cookie file through the secret convention`() {
        val file = Files.createTempFile("typetype-bilibili", ".cookie")
        try {
            Files.writeString(file, "SESSDATA=from-file; bili_jct=csrf")
            val config = BilibiliCookieConfig.fromEnvironment { name ->
                if (name == "BILIBILI_COOKIE_FILE") file.toString() else null
            }
            assertEquals("SESSDATA=from-file; bili_jct=csrf", config.cookieHeader)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `rejects malformed and unrelated cookies`() {
        assertFalse(BilibiliCookieConfig.fromRaw("theme=dark").isConfigured)
        assertNull(BilibiliCookieConfig.fromRaw("SESSDATA=session; SESSDATA=duplicate").cookieHeader)
        assertNull(BilibiliCookieConfig.fromRaw("SESSDATA=session\r\nInjected: yes").cookieHeader)
    }
}
