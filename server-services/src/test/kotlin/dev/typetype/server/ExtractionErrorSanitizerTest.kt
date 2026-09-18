package dev.typetype.server

import dev.typetype.server.services.ExtractionErrorSanitizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractionErrorSanitizerTest {
    @Test
    fun `normalizes bot prompt with mixed locales to sign-in verification message`() {
        val raw = "Ngena ngemvume ukuze uqinisekise ukuthi awuyona i-bot"
        assertEquals(
            "Sign in is required to verify access to this video",
            ExtractionErrorSanitizer.sanitize(raw),
        )
    }

    @Test
    fun `keeps normal english error message`() {
        val raw = "This video is only available for members"
        assertEquals(raw, ExtractionErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `normalizes login confirm prompt to sign-in verification message`() {
        val raw = "Sign in to confirm you are not a bot"
        assertEquals("Sign in is required to verify access to this video", ExtractionErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `keeps members-only paywall prompt as members message`() {
        val raw = "Join this channel to get access to members-only content like this video"
        assertEquals("This video is only available for members", ExtractionErrorSanitizer.sanitize(raw))
    }

    @Test
    fun `returns null for blank input`() {
        assertEquals(null, ExtractionErrorSanitizer.sanitize(""))
    }
}
