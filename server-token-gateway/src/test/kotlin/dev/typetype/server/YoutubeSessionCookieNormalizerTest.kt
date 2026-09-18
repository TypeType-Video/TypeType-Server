package dev.typetype.server

import dev.typetype.server.services.YoutubeSessionCookieNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YoutubeSessionCookieNormalizerTest {
    @Test
    fun `normalizes cookie header to allowed YouTube cookies only`() {
        val normalized = YoutubeSessionCookieNormalizer.normalize(
            "Cookie: SID=abc; SAPISID=def; unrelated=value; __Secure-3PSID=ghi"
        )
        assertEquals("SID=abc; SAPISID=def; __Secure-3PSID=ghi", normalized)
    }

    @Test
    fun `normalizes Netscape export without unrelated domains`() {
        val normalized = YoutubeSessionCookieNormalizer.normalize(
            """
            # Netscape HTTP Cookie File
            .example.com	TRUE	/	FALSE	0	SID	leak
            .youtube.com	TRUE	/	TRUE	0	SID	yt
            .google.com	TRUE	/	TRUE	0	__Secure-3PSID	google
            .youtube.com	TRUE	/	TRUE	0	unrelated	skip
            """.trimIndent()
        )
        assertTrue(normalized?.contains("SID=yt") == true)
        assertTrue(normalized?.contains("__Secure-3PSID=google") == true)
        assertFalse(normalized?.contains("leak") == true)
        assertFalse(normalized?.contains("unrelated") == true)
    }

    @Test
    fun `rejects oversized raw cookie export`() {
        assertNull(YoutubeSessionCookieNormalizer.normalize("SID=${"a".repeat(1024 * 1024)}"))
    }
}
