package dev.typetype.server

import dev.typetype.server.services.BilibiliCdnConfig
import dev.typetype.server.services.bilibiliUposMirrorAlternates
import dev.typetype.server.services.prioritizeUposMirrors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliCdnConfigTest {
    @Test
    fun `resolves region from client or environment default`() {
        assertEquals("overseas", BilibiliCdnConfig.resolveRegion("overseas"))
        assertEquals("domestic", BilibiliCdnConfig.resolveRegion("domestic"))
        assertEquals("overseas", BilibiliCdnConfig.resolveRegion("auto") { null })
        assertEquals("overseas", BilibiliCdnConfig.resolveRegion("auto") { "overseas" })
        assertEquals("domestic", BilibiliCdnConfig.resolveRegion("auto") { "domestic" })
        assertEquals("overseas", BilibiliCdnConfig.resolveRegion(null) { null })
        assertEquals("overseas", BilibiliCdnConfig.resolveRegion(null) { "overseas" })
        assertEquals("domestic", BilibiliCdnConfig.resolveRegion(null) { "domestic" })
    }

    @Test
    fun `domestic region prioritizes domestic mirrors`() {
        val domesticUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s"
        val overseasUrl = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"

        val prioritized = prioritizeUposMirrors(listOf(overseasUrl, domesticUrl), cdnRegion = "domestic")
        assertEquals(domesticUrl, prioritized.first())
    }

    @Test
    fun `overseas region prioritizes overseas mirrors`() {
        val domesticUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s"
        val overseasUrl = "https://upos-sz-mirroraliov.bilivideo.com/video.m4s"

        val prioritized = prioritizeUposMirrors(listOf(domesticUrl, overseasUrl), cdnRegion = "overseas")
        assertEquals(overseasUrl, prioritized.first())
    }

    @Test
    fun `mirror alternates order follows configured region`() {
        val input = "https://upos-sz-mirroraliov.bilivideo.com/dummy.m4s"
        val domesticAlternates = bilibiliUposMirrorAlternates(input, isAkamaiBlacklisted = true, cdnRegion = "domestic")
        assertTrue(domesticAlternates.first().contains("mirrorali.bilivideo.com"))

        val overseasAlternates = bilibiliUposMirrorAlternates(input, isAkamaiBlacklisted = true, cdnRegion = "overseas")
        assertTrue(overseasAlternates.first().contains("mirrorcosov.bilivideo.com"))
    }
}
