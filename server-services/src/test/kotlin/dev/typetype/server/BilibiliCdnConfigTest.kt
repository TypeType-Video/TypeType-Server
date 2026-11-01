package dev.typetype.server

import dev.typetype.server.services.BilibiliAkamaiConfig
import dev.typetype.server.services.BilibiliCdnConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliCdnConfigTest {
    @Test
    fun `defaults to null region and disabled prefetch and subchunks when unset`() {
        val emptyEnv = { _: String -> null }
        assertNull(BilibiliCdnConfig.defaultRegion(emptyEnv))
        assertNull(BilibiliCdnConfig.resolveRegion(null, emptyEnv))
        assertFalse(BilibiliCdnConfig.isPrefetchEnabled(emptyEnv))
        assertFalse(BilibiliCdnConfig.isSubchunkProxyEnabled(emptyEnv))
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted(emptyEnv))
    }

    @Test
    fun `parses region and enables subchunk proxy only for subchunks or prefetch`() {
        val overseasEnv = mapOf("BILIBILI_CDN_REGION" to "overseas")::get
        assertEquals("overseas", BilibiliCdnConfig.defaultRegion(overseasEnv))
        assertFalse(BilibiliCdnConfig.isSubchunkProxyEnabled(overseasEnv))

        val domesticEnv = mapOf("BILIBILI_CDN_REGION" to "cn")::get
        assertEquals("domestic", BilibiliCdnConfig.defaultRegion(domesticEnv))

        val prefetchEnv = mapOf("BILIBILI_CONCURRENT_PREFETCH" to "true")::get
        assertTrue(BilibiliCdnConfig.isPrefetchEnabled(prefetchEnv))
        assertTrue(BilibiliCdnConfig.isSubchunkProxyEnabled(prefetchEnv))
    }

    @Test
    fun `matches only akamaized hosts for Akamai blacklist`() {
        assertTrue(BilibiliAkamaiConfig.isAkamaiHost("upos-hz-mirrorakam.akamaized.net"))
        assertTrue(BilibiliAkamaiConfig.isAkamaiUrl("https://upos-hz-mirrorakam.akamaized.net/video.m4s"))
        assertFalse(BilibiliAkamaiConfig.isAkamaiHost("akam-custom.bilivideo.com"))
        assertFalse(BilibiliAkamaiConfig.isAkamaiHost("upos-sz-mirrorcos.bilivideo.com"))
    }
}
