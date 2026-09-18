package dev.typetype.server

import dev.typetype.server.services.BilibiliRelatedService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BilibiliRelatedServiceTest {

    @Test
    fun `caches uploader lookups shared by repeated stream extraction`() = runTest {
        var lookups = 0
        val service = BilibiliRelatedService { _ ->
            lookups++
            mapOf("BV1Related" to "https://space.bilibili.com/42")
        }
        val response = testStreamResponse().copy(
            relatedStreams = listOf(
                testVideoItem().copy(
                    url = "https://www.bilibili.com/video/BV1Related",
                    uploaderUrl = "",
                ),
            ),
        )

        val first = service.patchRelatedStreams(response, SOURCE_URL)
        val second = service.patchRelatedStreams(response, SOURCE_URL)

        assertEquals("https://space.bilibili.com/42", first.relatedStreams.single().uploaderUrl)
        assertEquals("https://space.bilibili.com/42", second.relatedStreams.single().uploaderUrl)
        assertEquals(1, lookups)
    }

    @Test
    fun `skips lookup when source or related item has no BVID`() = runTest {
        var lookups = 0
        val service = BilibiliRelatedService { _ ->
            lookups++
            emptyMap()
        }
        val response = testStreamResponse().copy(
            relatedStreams = listOf(testVideoItem().copy(url = "https://example.com/video")),
        )

        val result = service.patchRelatedStreams(response, "https://example.com/source")

        assertEquals(response, result)
        assertEquals(0, lookups)
    }

    private companion object {
        const val SOURCE_URL = "https://www.bilibili.com/video/BV1Source"
    }
}
