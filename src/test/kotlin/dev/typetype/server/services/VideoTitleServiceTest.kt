package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class VideoTitleServiceTest {
    @Test
    fun `ids are recognised in their usual spellings`() {
        assertEquals("BV1xx411c7mD", normalizeVideoId(" BV1xx411c7mD "))
        assertEquals("av170001", normalizeVideoId("AV170001"))
        assertEquals("sm9", normalizeVideoId("SM9"))
        assertEquals("so12345", normalizeVideoId("so12345"))
        assertNull(normalizeVideoId("BV1short"))
        assertNull(normalizeVideoId("av0"))
        assertNull(normalizeVideoId("hello"))
        assertNull(normalizeVideoId("https://www.bilibili.com/video/BV1xx411c7mD"))
    }

    @Test
    fun `titles are looked up once and missing ones are left out`() = runBlocking {
        val asked = mutableListOf<String>()
        val service = VideoTitleService(fetch = { id -> asked += id; if (id == "sm9") "Classic" else null })
        assertEquals(mapOf("sm9" to "Classic"), service.titles(listOf("sm9", "SM9", "av5", "nope")))
        assertEquals(mapOf("sm9" to "Classic"), service.titles(listOf("sm9", "av5")))
        assertEquals(listOf("sm9", "av5"), asked)
    }

    @Test
    fun `a failing lookup does not fail the others`() = runBlocking {
        val service = VideoTitleService(fetch = { id -> if (id == "sm1") error("down") else "Title $id" })
        assertEquals(mapOf("sm2" to "Title sm2"), service.titles(listOf("sm1", "sm2")))
    }

    @Test
    fun `a request is capped`() = runBlocking {
        val service = VideoTitleService(fetch = { id -> id })
        assertEquals(MAX_VIDEO_TITLE_IDS, service.titles((1..50).map { "sm$it" }).size)
    }
}
