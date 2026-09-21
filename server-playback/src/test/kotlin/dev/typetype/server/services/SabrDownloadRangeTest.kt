package dev.typetype.server.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SabrDownloadFrameWriterTest {
    @Test
    fun `writes deterministic framed stream`() {
        val output = ByteArrayOutputStream()
        val writer = SabrDownloadFrameWriter(output)

        writer.start()
        writer.initialization(140, byteArrayOf(1, 2))
        writer.media(140, 1, 3, ByteArrayInputStream(byteArrayOf(3, 4, 5)))
        writer.finish()

        val input = ByteBuffer.wrap(output.toByteArray()).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(SabrDownloadFrameWriter.MAGIC.size)
        input.get(magic)
        assertArrayEquals(SabrDownloadFrameWriter.MAGIC, magic)
        assertFrame(input, SabrDownloadFrameWriter.FRAME_INITIALIZATION, 140, 0, byteArrayOf(1, 2))
        assertFrame(input, SabrDownloadFrameWriter.FRAME_MEDIA, 140, 1, byteArrayOf(3, 4, 5))
        assertFrame(input, SabrDownloadFrameWriter.FRAME_COMPLETE, 0, 0, byteArrayOf())
        assertEquals(0, input.remaining())
    }

    @Test
    fun `rejects payload shorter than declared frame`() {
        val writer = SabrDownloadFrameWriter(ByteArrayOutputStream())
        assertThrows(IllegalStateException::class.java) {
            writer.media(137, 1, 3, ByteArrayInputStream(byteArrayOf(1, 2)))
        }
    }

    private fun assertFrame(
        input: ByteBuffer,
        expectedType: Int,
        expectedItag: Int,
        expectedSequence: Int,
        expectedPayload: ByteArray,
    ) {
        assertEquals(expectedType, input.get().toInt())
        assertEquals(expectedItag, input.int)
        assertEquals(expectedSequence, input.int)
        assertEquals(expectedPayload.size.toLong(), input.long)
        val payload = ByteArray(expectedPayload.size)
        input.get(payload)
        assertArrayEquals(expectedPayload, payload)
    }
}
