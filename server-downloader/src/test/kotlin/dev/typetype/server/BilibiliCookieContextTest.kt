package dev.typetype.server

import dev.typetype.server.downloader.BilibiliCookieContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BilibiliCookieContextTest {
    @Test
    fun `limits cookie injection to bilibili api hosts`() {
        BilibiliCookieContext.set("SESSDATA=session; bili_jct=csrf")
        try {
            assertEquals(
                "SESSDATA=session; bili_jct=csrf",
                BilibiliCookieContext.headerFor("https://api.bilibili.com/x/player"),
            )
            assertEquals(
                "SESSDATA=session; bili_jct=csrf",
                BilibiliCookieContext.headerFor("https://www.bilibili.com/video/BV1"),
            )
            assertNull(BilibiliCookieContext.headerFor("http://api.bilibili.com/x/player"))
            assertNull(BilibiliCookieContext.headerFor("https://upos-sz-mirrorali.bilivideo.com/video"))
            assertNull(BilibiliCookieContext.headerFor("https://example.com/video"))
        } finally {
            BilibiliCookieContext.set(null)
        }
    }
}
