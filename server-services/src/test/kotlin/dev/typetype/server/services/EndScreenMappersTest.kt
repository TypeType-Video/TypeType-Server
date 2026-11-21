package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.EndScreenElement

class EndScreenMappersTest {
    private fun element(
        type: String = "VIDEO",
        url: String = "https://www.youtube.com/watch?v=x",
        startMs: Long = 590_000,
        endMs: Long = 610_000,
        left: Double = 0.65,
        width: Double = 0.32,
        thumbnails: List<Image> = emptyList(),
    ) = EndScreenElement("id", type, "Title", url, thumbnails, startMs, endMs, left, 0.13, width, 1.77)

    @Test
    fun `maps a placed element with its largest thumbnail and a lowercase type`() {
        val small = Image("https://i.ytimg.com/small.jpg", 90, 160, Image.ResolutionLevel.LOW)
        val large = Image("https://i.ytimg.com/large.jpg", 360, 640, Image.ResolutionLevel.MEDIUM)

        val item = listOf(element(type = "PLAYLIST", thumbnails = listOf(small, large))).toEndScreenItems().single()

        assertEquals("playlist", item.type)
        assertEquals("https://i.ytimg.com/large.jpg", item.thumbnailUrl)
        assertEquals(590_000L, item.startMs)
        assertEquals(0.65, item.left)
        assertEquals(1.77, item.aspectRatio)
    }

    @Test
    fun `drops elements a client cannot place or open`() {
        val elements = listOf(
            element(type = "FUTURE_KIND"),
            element(url = "javascript:alert(1)"),
            element(url = ""),
            element(startMs = -1),
            element(endMs = 590_000),
            element(left = 1.4),
            element(width = 0.0),
        )

        assertEquals(emptyList<Any>(), elements.toEndScreenItems())
    }
}
