package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BilibiliUposMirrorsTest {
    @BeforeEach
    fun setup() {
        BilibiliMirrorSpeedTracker.reset()
    }
    @Test
    fun `an upos mirror url gets the other official mirrors with the same signed path`() {
        val url = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/3/v.m4s?deadline=1&os=akam&upsig=abc"

        val alternates = bilibiliUposMirrorAlternates(url)

        assertEquals(
            listOf(
                "upos-sz-mirroraliov.bilivideo.com",
                "upos-sz-mirrorcosov.bilivideo.com",
                "upos-sz-mirrorali.bilivideo.com",
                "upos-sz-mirrorhw.bilivideo.com",
                "upos-sz-mirrorcos.bilivideo.com",
            ),
            alternates.map { it.substringAfter("://").substringBefore('/') },
        )
        assertEquals(
            setOf("/upgcxcode/1/2/3/v.m4s?deadline=1&os=akam&upsig=abc"),
            alternates.map { "/" + it.substringAfter("://").substringAfter('/') }.toSet(),
        )
    }

    @Test
    fun `other Bilibili hosts get no guessed mirrors`() {
        assertEquals(emptyList<String>(), bilibiliUposMirrorAlternates("https://xy1x2x3x4xy.mcdn.bilivideo.cn:4483/v.m4s?os=mcdn"))
        assertEquals(emptyList<String>(), bilibiliUposMirrorAlternates("https://cn-gdfs-ct-01-01.bilivideo.com/v.m4s"))
    }

    @Test
    fun `prioritizeUposMirrors sorts hosts according to priority order`() {
        val input = listOf(
            "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            "https://upos-sz-mirroraliov.bilivideo.com/video.m4s",
            "https://upos-sz-mirrorhw.bilivideo.com/video.m4s",
        )
        val sorted = prioritizeUposMirrors(input)
        assertEquals(
            listOf(
                "https://upos-sz-mirroraliov.bilivideo.com/video.m4s",
                "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
                "https://upos-sz-mirrorhw.bilivideo.com/video.m4s",
                "https://upos-sz-mirrorcos.bilivideo.com/video.m4s",
            ),
            sorted,
        )
    }
}
