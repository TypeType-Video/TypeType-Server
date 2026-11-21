package dev.typetype.server.services

import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.HomeRecommendationsResponse
import dev.typetype.server.models.PublicPlaylistItem
import dev.typetype.server.models.PublicPlaylistResponse
import dev.typetype.server.models.SubscriptionFeedResponse
import dev.typetype.server.models.VideoItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusProfileTest {
    private val funChannel = "https://www.youtube.com/channel/UCfun"
    private val studyChannel = "https://www.youtube.com/channel/UCstudy"
    private val pendingChannel = "https://www.youtube.com/channel/UCpending"
    private val otherChannel = "https://www.youtube.com/channel/UCother"
    private val now = 1_800_000_000_000L

    private fun profile(share: Double) = FocusProfile(
        seed = "user-1",
        entertainmentChannels = setOf(normalizeChannelKey(funChannel)),
        studyChannels = setOf(normalizeChannelKey(studyChannel)),
        pendingStudyUnlocksAt = mapOf(normalizeChannelKey(pendingChannel) to (now + FocusProfile.DAY_MS)),
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
    fun `entertainment channel pages stop at the archive window and ungrouped channels are blocked`() {
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

        val pendingPage = channel(listOf(video("p", now - FocusProfile.DAY_MS, pendingChannel)), "next")
            .limitFocusArchive(profile(1.0), pendingChannel, true)
        assertTrue(pendingPage.focusBlocked)
        assertEquals("focus_study_pending", pendingPage.focusBlockCode)
        assertEquals(now + FocusProfile.DAY_MS, pendingPage.focusStudyUnlockAt)
        assertTrue(pendingPage.videos.isEmpty())
        assertNull(pendingPage.nextpage)

        val otherPage = channel(listOf(video("o", now - FocusProfile.DAY_MS, otherChannel)), "next")
            .limitFocusArchive(profile(1.0), otherChannel, true)
        assertTrue(otherPage.focusBlocked)
        assertEquals("focus_channel_blocked", otherPage.focusBlockCode)
        assertNull(otherPage.focusStudyUnlockAt)
        assertTrue(otherPage.videos.isEmpty())

        assertEquals(page, page.limitFocusArchive(null, funChannel, true))
    }

    @Test
    fun `passive feeds hide ungrouped and cooling-off channels while prioritizing study`() {
        val home = HomeRecommendationsResponse(
            items = listOf(
                video("a", now),
                video("b", now, otherChannel),
                video("p", now, pendingChannel),
                video("c", now, studyChannel),
            ),
            nextCursor = null,
            hasMore = false,
        )
        assertEquals(listOf("c", "a"), home.filterFocus(profile(1.0)).items.map { it.id })
        assertEquals(listOf("c"), home.filterFocus(profile(0.0)).items.map { it.id })

        val feed = SubscriptionFeedResponse(
            videos = listOf(
                video("recent-fun", now - FocusProfile.DAY_MS),
                video("old-fun", now - 30 * FocusProfile.DAY_MS),
                video("other", now - FocusProfile.DAY_MS, otherChannel),
                video("pending", now - FocusProfile.DAY_MS, pendingChannel),
                video("old-study", now - 30 * FocusProfile.DAY_MS, studyChannel),
            ),
            nextpage = null,
        )
        assertEquals(listOf("recent-fun", "old-study"), feed.filterFocus(profile(1.0)).videos.map { it.id })
        assertEquals(listOf("old-study"), feed.filterFocus(profile(0.0)).videos.map { it.id })

        val playlist = PublicPlaylistResponse(
            playlist = PublicPlaylistItem("pl", "Playlist", "https://www.youtube.com/playlist?list=1", "", "User", 3L, "NORMAL"),
            videos = listOf(video("a", now), video("b", now, otherChannel), video("c", now, studyChannel)),
            nextpage = null,
        )
        assertEquals(listOf("a", "c"), playlist.filterFocus(profile(1.0)).videos.map { it.id })
    }

    @Test
    fun `playback access enforces study, entertainment fade and archive, pending, and ungrouped gates`() {
        val full = profile(1.0)
        assertNull(full.blockReasonForPlayback("https://www.youtube.com/watch?v=1", studyChannel, now - 30 * FocusProfile.DAY_MS))
        assertNull(full.blockReasonForPlayback("https://www.youtube.com/watch?v=2", funChannel, now - FocusProfile.DAY_MS))
        assertEquals(
            FocusBlockReason.Entertainment,
            full.blockReasonForPlayback("https://www.youtube.com/watch?v=3", funChannel, now - 30 * FocusProfile.DAY_MS),
        )
        assertEquals(
            FocusBlockReason.Entertainment,
            profile(0.0).blockReasonForPlayback("https://www.youtube.com/watch?v=4", funChannel, now - FocusProfile.DAY_MS),
        )
        assertEquals(
            FocusBlockReason.StudyPending(now + FocusProfile.DAY_MS),
            full.blockReasonForPlayback("https://www.youtube.com/watch?v=5", pendingChannel, now),
        )
        assertEquals(
            FocusBlockReason.ChallengeRequired,
            full.blockReasonForPlayback("https://www.youtube.com/watch?v=6", otherChannel, now),
        )
        assertEquals(
            FocusBlockReason.ChallengeRequired,
            full.blockReasonForPlayback("https://www.youtube.com/watch?v=7", "", now),
        )
    }

    private fun video(id: String, publishedAt: Long, uploaderUrl: String = funChannel) = VideoItem(
        id = id, title = id, url = "https://www.youtube.com/watch?v=$id", thumbnailUrl = "", uploaderName = "Fun",
        uploaderUrl = uploaderUrl, uploaderAvatarUrl = "", duration = 60, viewCount = 1, uploadDate = "", streamType = "video_stream",
        isShortFormContent = false, uploaderVerified = false, shortDescription = null, publishedAt = publishedAt,
    )

    private fun channel(videos: List<VideoItem>, nextpage: String?) = ChannelResponse(
        name = "Fun", description = "", avatarUrl = "", bannerUrl = "", subscriberCount = 1, isVerified = false,
        videos = videos, nextpage = nextpage,
    )
}
