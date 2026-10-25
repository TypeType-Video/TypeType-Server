package dev.typetype.server

import dev.typetype.server.services.toChannelPlaylistsResponse
import dev.typetype.server.services.toChannelResponse
import dev.typetype.server.services.toChannelTabResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException

class ChannelResponseMappersTest {

    @Test
    fun `empty channel tab with extraction errors is not treated as success`() {
        val cause = ParsingException("temporary upstream denial")
        val info = mockk<ChannelTabInfo>()
        every { info.relatedItems } returns emptyList()
        every { info.errors } returns listOf(cause)

        val error = assertThrows(ExtractionException::class.java) { info.toChannelTabResponse() }

        assertSame(cause, error.cause)
    }

    @Test
    fun `genuinely empty channel tab remains a valid response`() {
        val info = mockk<ChannelTabInfo>()
        every { info.relatedItems } returns emptyList()
        every { info.errors } returns emptyList()
        every { info.name } returns "Empty channel"
        every { info.nextPage } returns null

        val response = info.toChannelTabResponse()

        assertEquals("Empty channel", response.name)
        assertEquals(0, response.videos.size)
    }

    @Test
    fun `empty channel home page with extraction errors is not treated as success`() {
        val info = mockk<ChannelInfo>()
        every { info.relatedItems } returns emptyList()
        every { info.errors } returns listOf(ParsingException("sign in to confirm"))

        assertThrows(ExtractionException::class.java) { info.toChannelResponse() }
    }

    @Test
    fun `empty next channel pages with extraction errors are not treated as success`() {
        val errors = listOf<Throwable>(ParsingException("bot page"))
        val streams = InfoItemsPage<StreamInfoItem>(emptyList(), null, errors)
        val items = InfoItemsPage<InfoItem>(emptyList(), null, errors)

        assertThrows(ExtractionException::class.java) { streams.toChannelResponse() }
        assertThrows(ExtractionException::class.java) { items.toChannelTabResponse() }
        assertThrows(ExtractionException::class.java) { items.toChannelPlaylistsResponse() }
    }

    @Test
    fun `empty channel playlists tab with extraction errors is not treated as success`() {
        val info = mockk<ChannelTabInfo>()
        every { info.relatedItems } returns emptyList()
        every { info.errors } returns listOf(ParsingException("bot page"))

        assertThrows(ExtractionException::class.java) { info.toChannelPlaylistsResponse() }
    }
}
