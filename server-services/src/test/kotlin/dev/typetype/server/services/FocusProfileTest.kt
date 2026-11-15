package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.VideoItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusProfileTest {
    private val funChannel = "https://www.youtube.com/channel/UCfun"
    private val studyChannel = "https://www.youtube.com/channel/UCstudy"
    private val now = 1_800_000_000_000L

    private fun profile(share: Double) = FocusProfile(
        seed = "user-1",
        entertainmentChannels = setOf(normalizeChannelKey(funChannel)),
        studyChannels = setOf(normalizeChannelKey(studyChannel)),
        entertainmentShare = share,
        archiveDays = 7,
        archiveCutoffMs = now - 7 * FocusProfile.DAY_MS,
    )

    @Test
    fun `share drops linearly over the transition`() {
        val start = now
        assertEquals(1.0, FocusProfile.share(start, 60, start), 1e-9)
        assertEquals(0.5, FocusProfile.share(start, 60, start + 30 * FocusProfile.DAY_MS), 1e-9)
        assertEquals(0.0, FocusProfile.share(start, 60, start + 90 * FocusProfile.DAY_MS), 1e-9)
        assertEquals(1.0, FocusProfile.share(start, 60, start - FocusProfile.DAY_MS), 1e-9)
    }

    @Test
    fun `other channels are never touched`() {
        assertTrue(profile(0.0).keepsVideo("https://www.youtube.com/watch?v=abc123", "https://www.youtube.com/channel/UCstudy"))
    }

    @Test
    fun `entertainment videos are kept by a stable share`() {
        val urls = (0 until 2000).map { "https://www.youtube.com/watch?v=vid$it" }
        val half = profile(0.5)
        val kept = urls.filter { half.keepsVideo(it, funChannel) }
        assertTrue(kept.size in 850..1150, "kept ${kept.size}")
        assertEquals(kept, urls.filter { half.keepsVideo(it, funChannel) })
        val lower = profile(0.2)
        assertTrue(urls.filter { lower.keepsVideo(it, funChannel) }.all { it in kept })
        assertTrue(urls.all { profile(1.0).keepsVideo(it, "https://youtube.com/channel/UCfun/") })
        assertFalse(urls.any { profile(0.0).keepsVideo(it, funChannel) })
    }

    @Test
    fun `entertainment channel pages stop at the archive window`() {
        val recent = video("recent", now - FocusProfile.DAY_MS)
        val old = video("old", now - 30 * FocusProfile.DAY_MS)
        val page = channel(listOf(recent, old), "next")
        val limited = page.limitFocusArchive(profile(0.5), funChannel, newestFirst = true)
        assertEquals(listOf("recent"), limited.videos.map { it.id })
        assertNull(limited.nextpage)
        assertEquals(7, limited.focusArchiveDays)
        assertEquals("next", page.limitFocusArchive(profile(0.5), funChannel, newestFirst = false).nextpage)
        assertEquals(7, page.limitFocusArchive(profile(0.5), "https://www.youtube.com/@fun", true).focusArchiveDays)
        val studyPage = channel(listOf(video("study", now - 30 * FocusProfile.DAY_MS, studyChannel)), "next")
        assertEquals(studyPage, studyPage.limitFocusArchive(profile(0.5), "https://www.youtube.com/@study", true))
        assertEquals(page, page.limitFocusArchive(null, funChannel, true))
    }

    private fun video(id: String, publishedAt: Long, uploaderUrl: String = funChannel) = VideoItem(
        id = id, title = id, url = "https://www.youtube.com/watch?v=$id", thumbnailUrl = "", uploaderName = "Fun",
        uploaderUrl = uploaderUrl, uploaderAvatarUrl = "", duration = 60, viewCount = 1, uploadDate = "", streamType = "video_stream",
        isShortFormContent = false, uploaderVerified = false, shortDescription = null, publishedAt = publishedAt,
    )

    @Test
    fun `study channels come first on the home`() {
        val home = HomeRecommendationsResponse(
            items = listOf(video("a", now), video("b", now, "https://www.youtube.com/channel/UCother"), video("c", now, studyChannel)),
            nextCursor = null,
            hasMore = false,
        )
        assertEquals(listOf("c", "a", "b"), home.filterFocus(profile(1.0)).items.map { it.id })
    }

    private fun channel(videos: List<VideoItem>, nextpage: String?) = ChannelResponse(
        name = "Fun", description = "", avatarUrl = "", bannerUrl = "", subscriberCount = 1, isVerified = false,
        videos = videos, nextpage = nextpage,
    )
}
