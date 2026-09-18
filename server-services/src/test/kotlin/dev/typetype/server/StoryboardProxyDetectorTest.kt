package dev.typetype.server

import dev.typetype.server.services.StoryboardProxyDetector
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryboardProxyDetectorTest {

    @Test
    fun `detects ytimg storyboard path`() {
        val url = "https://i.ytimg.com/sb/dQw4w9WgXcQ/storyboard3_L1/M0.jpg"
        assertTrue(StoryboardProxyDetector.isYouTubeStoryboard(url))
    }

    @Test
    fun `rejects non storyboard image host`() {
        val url = "https://img.youtube.com/vi/dQw4w9WgXcQ/default.jpg"
        assertFalse(StoryboardProxyDetector.isYouTubeStoryboard(url))
    }
}
