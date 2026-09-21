package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SabrSessionKeyTest {
    @Test
    fun `audio track id isolates otherwise identical sabr sessions`() {
        val english = SabrSessionKey(
            videoId = "dqWhXeGQkgU",
            userId = "user-1",
            audioItag = 140,
            audioTrackId = "en.0",
            videoItag = 137,
            startTimeMs = 0L,
        )
        val french = english.copy(audioTrackId = "fr.0")

        assertNotEquals(english, french)
    }

    @Test
    fun `session purpose isolates playback and downloads`() {
        val playback = SabrSessionKey(
            videoId = "dqWhXeGQkgU",
            userId = "user-1",
            audioItag = 140,
            audioTrackId = null,
            videoItag = 137,
            startTimeMs = 0L,
            purpose = SabrSessionPurpose.PLAYBACK,
        )

        assertNotEquals(playback, playback.copy(purpose = SabrSessionPurpose.DOWNLOAD))
    }

    @Test
    fun `audio only mode isolates otherwise identical playback sessions`() {
        val playback = SabrSessionKey(
            videoId = "dqWhXeGQkgU",
            userId = "user-1",
            audioItag = 140,
            audioTrackId = null,
            videoItag = 137,
            startTimeMs = 0L,
            purpose = SabrSessionPurpose.PLAYBACK,
        )

        assertNotEquals(playback, playback.copy(audioOnly = true))
    }
}
