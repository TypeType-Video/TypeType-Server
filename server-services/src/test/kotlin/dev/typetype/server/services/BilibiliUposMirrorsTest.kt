package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BilibiliUposMirrorsTest {
    @Test
    fun `an upos mirror url gets the other official mirrors with the same signed path`() {
        val url = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/3/v.m4s?deadline=1&os=akam&upsig=abc"

        val alternates = bilibiliUposMirrorAlternates(url)

        assertEquals(
            listOf("upos-sz-mirrorcos.bilivideo.com", "upos-sz-mirrorali.bilivideo.com", "upos-sz-mirrorhw.bilivideo.com"),
            alternates.map { it.substringAfter("://").substringBefore('/') },
        )
        assertEquals(
            setOf("/upgcxcode/1/2/3/v.m4s?deadline=1&os=akam&upsig=abc"),
            alternates.map { "/" + it.substringAfter("://").substringAfter('/') }.toSet(),
        )
    }

    @Test
    fun `upos edge staging hosts get official mirrors with signed path`() {
        val url = "https://upos-sz-estgcos.bilivideo.com/upgcxcode/1/2/3/v.m4s?deadline=1&os=estgcos&upsig=abc"
        val alternates = bilibiliUposMirrorAlternates(url, isAkamaiBlacklisted = true, cdnRegion = "overseas")
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

    @Test
    fun `other Bilibili hosts get no guessed mirrors`() {
        assertEquals(emptyList<String>(), bilibiliUposMirrorAlternates("https://xy1x2x3x4xy.mcdn.bilivideo.cn:4483/v.m4s?os=mcdn"))
        assertEquals(emptyList<String>(), bilibiliUposMirrorAlternates("https://cn-gdfs-ct-01-01.bilivideo.com/v.m4s"))
    }
}
