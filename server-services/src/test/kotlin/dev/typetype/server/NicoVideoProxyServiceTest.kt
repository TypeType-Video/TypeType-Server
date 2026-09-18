package dev.typetype.server

import dev.typetype.server.services.parseNicoCookie
import dev.typetype.server.services.rewriteNicoManifest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NicoVideoProxyServiceTest {

    @Test
    fun `parseNicoCookie extracts domand_bid from encoded fragment`() {
        val fragment = "cookie=domand_bid%3DaAbBcC123&length=1234"
        assertEquals("aAbBcC123", parseNicoCookie(fragment))
    }

    @Test
    fun `parseNicoCookie returns null when cookie key is not domand_bid`() {
        val fragment = "cookie=other_key%3DsomeValue&length=60"
        assertNull(parseNicoCookie(fragment))
    }

    @Test
    fun `parseNicoCookie returns null when fragment is empty`() {
        assertNull(parseNicoCookie(""))
    }

    @Test
    fun `parseNicoCookie returns null when cookie param is absent`() {
        assertNull(parseNicoCookie("length=120"))
    }

    @Test
    fun `rewriteNicoManifest rewrites relative segment URLs`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:6.0,
            seg-000001.ts
            #EXTINF:6.0,
            seg-000002.ts
        """.trimIndent()
        val result = rewriteNicoManifest(manifest, base)
        val lines = result.lines()
        val segLines = lines.filter { it.contains("nicovideo?url=") }
        assertEquals(2, segLines.size)
        assert(segLines[0].contains("seg-000001.ts"))
        assert(segLines[1].contains("seg-000002.ts"))
    }

    @Test
    fun `rewriteNicoManifest rewrites absolute segment URLs`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = """
            #EXTM3U
            #EXTINF:6.0,
            https://delivery.domand.nicovideo.jp/hlsbid/abc/seg-000001.ts
        """.trimIndent()
        val result = rewriteNicoManifest(manifest, base)
        val segLine = result.lines().first { it.contains("nicovideo?url=") }
        assert(segLine.contains("delivery.domand.nicovideo.jp"))
    }

    @Test
    fun `rewriteNicoManifest preserves comment and tag lines`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = "#EXTM3U\n#EXT-X-VERSION:3\nseg-001.ts"
        val result = rewriteNicoManifest(manifest, base)
        val lines = result.lines()
        assert(lines[0] == "#EXTM3U")
        assert(lines[1] == "#EXT-X-VERSION:3")
        assert(lines[2].startsWith("nicovideo?url="))
    }

    @Test
    fun `rewriteNicoManifest rewrites URI attribute in EXT-X-MAP tag`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = "#EXTM3U\n#EXT-X-MAP:URI=\"https://asset.domand.nicovideo.jp/init01.cmfv\"\nseg-001.ts"
        val result = rewriteNicoManifest(manifest, base)
        val mapLine = result.lines().first { it.startsWith("#EXT-X-MAP") }
        assert(mapLine.contains("nicovideo?url=")) { "Expected proxied URI in EXT-X-MAP: $mapLine" }
        assert(mapLine.contains("asset.domand.nicovideo.jp")) { "Expected asset domain in proxied URI: $mapLine" }
    }

    @Test
    fun `rewriteNicoManifest appends domand_bid to rewritten URLs when provided`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val manifest = "#EXTM3U\nseg-001.ts"
        val result = rewriteNicoManifest(manifest, base, domandBid = "abc123")
        val segLine = result.lines().first { it.contains("nicovideo?url=") }
        assert(segLine.contains("domand_bid=abc123")) { "Expected domand_bid in rewritten URL: $segLine" }
    }

    @Test
    fun `rewriteNicoManifest can use generic proxy path`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val result = rewriteNicoManifest("#EXTM3U\nseg-001.ts", base, domandBid = "abc123", proxyPath = "proxy")
        val segLine = result.lines().first { it.contains("proxy?url=") }
        assert(segLine.contains("domand_bid=abc123")) { "Expected domand_bid in generic proxy URL: $segLine" }
    }

    @Test
    fun `rewriteNicoManifest appends domand_bid to EXT-X-KEY URI`() {
        val base = "https://delivery.domand.nicovideo.jp/hlsbid/abc/video.m3u8"
        val keyUrl = "https://delivery.domand.nicovideo.jp/hlsbid/abc/keys/video-h264-720p.key?session=xyz"
        val manifest = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"$keyUrl\"\nseg-001.ts"
        val result = rewriteNicoManifest(manifest, base, domandBid = "abc123")
        val keyLine = result.lines().first { it.startsWith("#EXT-X-KEY") }
        assert(keyLine.contains("domand_bid=abc123")) { "Expected domand_bid in EXT-X-KEY URI: $keyLine" }
    }
}
