package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BilibiliAkamaiConfigTest {

    @Test
    fun `isAkamaiBlacklisted defaults to false when env is unset`() {
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { null })
    }

    @Test
    fun `isAkamaiBlacklisted supports BILIBILI_BLACKLIST_AKAMAI_CDN`() {
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "true" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "1" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "yes" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "on" else null })

        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "false" else null })
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_BLACKLIST_AKAMAI_CDN") "0" else null })
    }

    @Test
    fun `isAkamaiBlacklisted supports BILIBILI_DISABLE_AKAMAI_CDN`() {
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_DISABLE_AKAMAI_CDN") "true" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_DISABLE_AKAMAI_CDN") "1" else null })
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_DISABLE_AKAMAI_CDN") "false" else null })
    }

    @Test
    fun `isAkamaiBlacklisted supports BILIBILI_AKAMAI_CDN flag`() {
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_AKAMAI_CDN") "off" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_AKAMAI_CDN") "false" else null })
        assertTrue(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_AKAMAI_CDN") "0" else null })
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_AKAMAI_CDN") "on" else null })
        assertFalse(BilibiliAkamaiConfig.isAkamaiBlacklisted { if (it == "BILIBILI_AKAMAI_CDN") "true" else null })
    }

    @Test
    fun `bilibiliUposMirrorAlternates excludes akamai when blacklisted`() {
        val cosUrl = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/1/2/3/v.m4s?deadline=1"

        val withAkamai = bilibiliUposMirrorAlternates(cosUrl, isAkamaiBlacklisted = false)
        assertTrue(withAkamai.any { it.contains("akamaized.net") })

        val withoutAkamai = bilibiliUposMirrorAlternates(cosUrl, isAkamaiBlacklisted = true)
        assertFalse(withoutAkamai.any { it.contains("akamaized.net") })
        assertEquals(
            listOf(
                "upos-sz-mirroraliov.bilivideo.com",
                "upos-sz-mirrorcosov.bilivideo.com",
                "upos-sz-mirrorali.bilivideo.com",
                "upos-sz-mirrorhw.bilivideo.com",
            ),
            withoutAkamai.map { it.substringAfter("://").substringBefore('/') },
        )
    }

    @Test
    fun `bilibiliUposMirrorAlternates gives non-akamai alternatives for akamai url when blacklisted`() {
        val akamUrl = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/3/v.m4s?deadline=1"

        val alternates = bilibiliUposMirrorAlternates(akamUrl, isAkamaiBlacklisted = true)
        assertFalse(alternates.any { it.contains("akamaized.net") })
        assertEquals(5, alternates.size)
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
    }
}
