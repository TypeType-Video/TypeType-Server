package dev.typetype.server.services

import dev.typetype.server.models.BlockedItem
import dev.typetype.server.models.BlockedKeywordItem
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlockedContentProfileTest {
    @Test
    fun `matches equivalent youtube video urls`() {
        val profile = profile(
            videos = listOf(
                BlockedItem(url = "https://www.youtube.com/watch?v=AbC_123-xyZ", blockedAt = 1),
            ),
        )

        assertTrue(profile.blocksVideo("https://youtu.be/AbC_123-xyZ?t=12"))
        assertTrue(profile.blocksVideo("https://m.youtube.com/shorts/AbC_123-xyZ"))
    }

    @Test
    fun `matches youtube channel hosts and normalized names`() {
        val profile = profile(
            channels = listOf(
                BlockedItem(
                    url = "http://www.youtube.com/@Example/?view=0",
                    name = "Ｔｅｓｔ Channel",
                    blockedAt = 1,
                ),
            ),
        )

        assertTrue(profile.blocksChannel("https://m.youtube.com/@Example/", "Other"))
        assertTrue(profile.blocksChannel("https://youtube.com/@Example/streams", "Other"))
        assertTrue(profile.blocksChannel("", "test channel"))
    }

    @Test
    fun `filters video url channel and keyword independently`() {
        val profile = profile(
            videos = listOf(BlockedItem(url = "https://youtube.com/watch?v=blocked-video", blockedAt = 1)),
            channels = listOf(BlockedItem("https://youtube.com/@blocked", "Blocked", null, 1)),
            keywords = listOf(BlockedKeywordItem("spoiler", 1)),
        )

        assertFalse(profile.allowsVideo("https://youtu.be/blocked-video", "One", "", ""))
        assertFalse(
            profile.allowsVideo(
                "https://youtube.com/watch?v=other-video",
                "Two",
                "https://www.youtube.com/@blocked",
                "Blocked",
            ),
        )
        assertFalse(profile.allowsVideo("https://youtube.com/watch?v=third-video", "A spoiler", "", ""))
        assertTrue(profile.allowsVideo("https://youtube.com/watch?v=visible-video", "Visible", "", ""))
    }

    private fun profile(
        videos: List<BlockedItem> = emptyList(),
        channels: List<BlockedItem> = emptyList(),
        keywords: List<BlockedKeywordItem> = emptyList(),
    ) = BlockedContentProfile(videos, channels, keywords)
}
