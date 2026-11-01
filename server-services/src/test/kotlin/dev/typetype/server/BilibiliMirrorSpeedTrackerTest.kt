package dev.typetype.server

import dev.typetype.server.services.BilibiliMirrorSpeedTracker
import dev.typetype.server.services.bilibiliUposMirrorAlternates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BilibiliMirrorSpeedTrackerTest {
    @BeforeEach
    fun setup() {
        BilibiliMirrorSpeedTracker.reset()
    }

    @Test
    fun `preserves original mirror order when region is null and no stats exist`() {
        val urls = listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/v.m4s",
            "https://upos-sz-mirrorali.bilivideo.com/v.m4s",
            "https://upos-sz-mirrorhw.bilivideo.com/v.m4s",
        )
        assertEquals(urls, BilibiliMirrorSpeedTracker.prioritize(urls, region = null))
    }

    @Test
    fun `records success latency and prioritizes lower latency mirror`() {
        val slowMirror = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s"
        val fastMirror = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"

        BilibiliMirrorSpeedTracker.recordSuccess(slowMirror, 450)
        BilibiliMirrorSpeedTracker.recordSuccess(fastMirror, 30)

        val prioritized = BilibiliMirrorSpeedTracker.prioritize(listOf(slowMirror, fastMirror))
        assertEquals(listOf(fastMirror, slowMirror), prioritized)
    }

    @Test
    fun `penalizes failing mirror so healthy mirrors take precedence`() {
        val failingMirror = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"
        val fallbackMirror = "https://upos-sz-mirrorcosov.bilivideo.com/video.m4s"

        BilibiliMirrorSpeedTracker.recordSuccess(failingMirror, 20)
        BilibiliMirrorSpeedTracker.recordSuccess(fallbackMirror, 200)
        BilibiliMirrorSpeedTracker.recordFailure(failingMirror)

        val prioritized = BilibiliMirrorSpeedTracker.prioritize(listOf(failingMirror, fallbackMirror))
        assertEquals(listOf(fallbackMirror, failingMirror), prioritized)
        assertTrue(BilibiliMirrorSpeedTracker.score(failingMirror) > BilibiliMirrorSpeedTracker.score(fallbackMirror))
    }

    @Test
    fun `prioritizes overseas mirrors when overseas region is configured`() {
        val url = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/1/2/3/v.m4s?os=cos"
        val alternates = bilibiliUposMirrorAlternates(url, isAkamaiBlacklisted = true, cdnRegion = "overseas")
        assertEquals(
            listOf(
                "upos-sz-mirroraliov.bilivideo.com",
                "upos-sz-mirrorcosov.bilivideo.com",
                "upos-sz-mirrorali.bilivideo.com",
                "upos-sz-mirrorhw.bilivideo.com",
            ),
            alternates.map { it.substringAfter("://").substringBefore('/') },
        )
    }
}
