package dev.typetype.server

import dev.typetype.server.models.RssFeedItem
import dev.typetype.server.services.RssDocumentRenderer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RssDocumentRendererTest {
    @Test
    fun `renders a media thumbnail for HTTP(S) video thumbnails`() {
        val video = testVideoItem().copy(thumbnailUrl = "https://img.example/thumb.jpg")

        val xml = RssDocumentRenderer.render(feed(), listOf(video), "https://video.example", 1_000L)
            .toString(Charsets.UTF_8)

        assertTrue(xml.contains("xmlns:media=\"http://search.yahoo.com/mrss/\""))
        assertTrue(xml.contains("<media:thumbnail url=\"https://img.example/thumb.jpg\""))
    }

    @Test
    fun `omits a media thumbnail when the video has no HTTP(S) thumbnail`() {
        val xml = RssDocumentRenderer.render(feed(), listOf(testVideoItem()), "https://video.example", 1_000L)
            .toString(Charsets.UTF_8)

        assertFalse(xml.contains("media:thumbnail"))
    }

    @Test
    fun `omits a media thumbnail for unsupported or relative URLs`() {
        val video = testVideoItem().copy(thumbnailUrl = "ftp://img.example/thumb.jpg")

        val xml = RssDocumentRenderer.render(feed(), listOf(video), "https://video.example", 1_000L)
            .toString(Charsets.UTF_8)

        assertFalse(xml.contains("media:thumbnail"))

        val relativeScheme = testVideoItem().copy(thumbnailUrl = "https:/img.example/thumb.jpg")
        val relativeXml = RssDocumentRenderer.render(feed(), listOf(relativeScheme), "https://video.example", 1_000L)
            .toString(Charsets.UTF_8)

        assertFalse(relativeXml.contains("media:thumbnail"))
    }

    private fun feed() = RssFeedItem(
        id = "feed-id",
        name = "Feed",
        scope = "all",
        channelUrls = emptyList(),
        serviceIds = listOf(0, 5, 6),
        includeVideos = true,
        includeShorts = true,
        includeLive = true,
        includeUpcoming = true,
        enabled = true,
        createdAt = 1L,
        updatedAt = 1L,
        lastUsedAt = null,
    )
}
