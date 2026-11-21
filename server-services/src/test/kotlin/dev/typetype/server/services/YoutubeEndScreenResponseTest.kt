package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeEndScreenResponseTest {
    @Test
    fun `reads the end screen of the requested video`() {
        val items = parseEndScreenResponse("AJUy9Q3RGl8", response("AJUy9Q3RGl8"))

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("video", item.type)
        assertEquals("Helicopter", item.title)
        assertEquals("https://www.youtube.com/watch?v=d19r9sPihGM", item.url)
        assertEquals(146_959L, item.startMs)
        assertEquals(166_528L, item.endMs)
        assertEquals(0.0228, item.left, 0.0001)
    }

    @Test
    fun `ignores a response for another video or a challenge`() {
        assertEquals(emptyList<Any>(), parseEndScreenResponse("AJUy9Q3RGl8", response("other")))
        assertEquals(emptyList<Any>(), parseEndScreenResponse("AJUy9Q3RGl8", """{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}"""))
    }

    private fun response(videoId: String) = """
        {"videoDetails":{"videoId":"$videoId"},"endscreen":{"endscreenRenderer":{"elements":[
          {"endscreenElementRenderer":{"style":"VIDEO","id":"e1",
            "image":{"thumbnails":[{"url":"https://i.ytimg.com/vi/d19r9sPihGM/hqdefault.jpg","width":168,"height":94}]},
            "title":{"simpleText":"Helicopter"},
            "endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/watch?v=d19r9sPihGM&pp=x"}},
              "watchEndpoint":{"videoId":"d19r9sPihGM"}},
            "startMs":"146959","endMs":"166528","left":0.022807021,"top":0.52024925,
            "width":0.32280701,"aspectRatio":1.7777778}}]}}}
    """.trimIndent()
}
