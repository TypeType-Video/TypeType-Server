package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FocusAllowOnceProfileTest {
    private val funChannel = "https://www.youtube.com/channel/UCfun"
    private val pendingChannel = "https://www.youtube.com/channel/UCpending"
    private val otherChannel = "https://www.youtube.com/channel/UCother"
    private val now = 1_800_000_000_000L

    @Test
    fun `temporary allow-once grants bypass playback blocks only for pending and outside-study videos`() {
        val profile = FocusProfile(
            seed = "user-1",
            entertainmentChannels = setOf(normalizeChannelKey(funChannel)),
            studyChannels = setOf(normalizeChannelKey("https://www.youtube.com/channel/UCstudy")),
            pendingStudyUnlocksAt = mapOf(normalizeChannelKey(pendingChannel) to (now + FocusProfile.DAY_MS)),
            entertainmentShare = 1.0,
            archiveDays = 7,
            archiveCutoffMs = now - 7 * FocusProfile.DAY_MS,
            strictBlocking = true,
            temporaryAllowedVideos = setOf(
                normalizeBlockedVideoKey("https://www.youtube.com/watch?v=vidStudy5"),
                normalizeBlockedVideoKey("https://www.youtube.com/watch?v=vidOther6"),
                normalizeBlockedVideoKey("https://www.youtube.com/watch?v=vidFun4"),
            ),
        )

        assertNull(profile.blockReasonForPlayback("https://youtu.be/vidStudy5", pendingChannel, now))
        assertNull(profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidOther6", otherChannel, now))
        assertEquals(
            FocusBlockReason.Entertainment,
            profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidFun4", funChannel, now - 30 * FocusProfile.DAY_MS),
        )
        assertEquals(
            FocusBlockReason.OutsideStudy,
            profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidOther8", otherChannel, now),
        )
        assertNull(
            profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidRecentFun", funChannel, now - FocusProfile.DAY_MS, alreadyWatched = false),
        )
        assertEquals(
            FocusBlockReason.EntertainmentRewatch,
            profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidRecentFun", funChannel, now - FocusProfile.DAY_MS, alreadyWatched = true),
        )
        assertNull(
            profile.blockReasonForPlayback("https://www.youtube.com/watch?v=vidFun4", funChannel, now - FocusProfile.DAY_MS, alreadyWatched = true),
        )
        assertFalse(profile.keepsFeedVideo("https://www.youtube.com/watch?v=vidOther6", otherChannel, now))
    }
}
