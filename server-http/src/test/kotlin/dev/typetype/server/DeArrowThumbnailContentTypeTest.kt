package dev.typetype.server

import dev.typetype.server.routes.deArrowThumbnailContentType
import io.ktor.http.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeArrowThumbnailContentTypeTest {
    @Test
    fun `detects webp thumbnails`() {
        val bytes = "RIFF1234WEBP".encodeToByteArray()

        assertEquals("image/webp", deArrowThumbnailContentType(bytes).toString())
    }

    @Test
    fun `keeps jpeg fallback`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        assertEquals(ContentType.Image.JPEG, deArrowThumbnailContentType(bytes))
    }
}
