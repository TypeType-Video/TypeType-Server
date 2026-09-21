package dev.typetype.server

import dev.typetype.server.services.ProxyProvider
import dev.typetype.server.services.isPublicProxyAddress
import dev.typetype.server.services.requireProxyTarget
import dev.typetype.server.services.validateProxyUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class UrlValidatorTest {
    @Test
    fun `rejects malformed and unsupported urls`() {
        assertEquals("Malformed URL", validateProxyUrl("not a url"))
        assertEquals("Missing URL scheme", validateProxyUrl("i.ytimg.com/video"))
        assertEquals("Unsupported URL scheme: http", validateProxyUrl("http://i.ytimg.com/video"))
        assertEquals("Unsupported URL scheme: ftp", validateProxyUrl("ftp://i.ytimg.com/video"))
        assertEquals("Missing URL host", validateProxyUrl("https:///video"))
        assertEquals("URL credentials are not allowed", validateProxyUrl("https://user@i.ytimg.com/video"))
        assertEquals("Unsupported proxy port", validateProxyUrl("https://i.ytimg.com:8443/video"))
    }

    @Test
    fun `allows only supported provider hosts`() {
        assertEquals(ProxyProvider.YOUTUBE, requireProxyTarget("https://i.ytimg.com/image.jpg").provider)
        assertEquals(ProxyProvider.YOUTUBE, requireProxyTarget("https://yt3.googleusercontent.com/avatar").provider)
        assertEquals(ProxyProvider.BILIBILI, requireProxyTarget("https://i2.hdslb.com/image.jpg").provider)
        assertEquals(
            ProxyProvider.BILIBILI,
            requireProxyTarget("https://upos-hz-mirrorakam.akamaized.net/video.m4s").provider,
        )
        assertEquals(
            ProxyProvider.NICONICO,
            requireProxyTarget("https://delivery.domand.nicovideo.jp/video.m3u8").provider,
        )
        assertNull(validateProxyUrl("https://r1---sn-a5mekn6z.googlevideo.com/videoplayback"))
    }

    @Test
    fun `rejects arbitrary and lookalike hosts`() {
        assertEquals("Unsupported proxy host", validateProxyUrl("https://example.com/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://evilgooglevideo.com/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://googlevideo.com.example.com/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://example.googleusercontent.com/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://other.akamaized.net/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://127.0.0.1/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://[::1]/content"))
        assertEquals("Unsupported proxy host", validateProxyUrl("https://i.ytimg.com.evil.example/content"))
        assertEquals("URL credentials are not allowed", validateProxyUrl("https://evil.example@i.ytimg.com/content"))
    }

    @Test
    fun `rejects non-public ipv4 addresses`() {
        val blocked = listOf(
            "0.0.0.1",
            "10.0.0.1",
            "100.64.0.1",
            "127.0.0.1",
            "169.254.169.254",
            "172.16.0.1",
            "192.168.1.1",
            "198.18.0.1",
            "224.0.0.1",
            "255.255.255.255",
        )
        blocked.forEach { assertFalse(isPublicProxyAddress(InetAddress.getByName(it)), it) }
        assertTrue(isPublicProxyAddress(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun `rejects non-public ipv6 addresses`() {
        val blocked = listOf(
            "::",
            "::1",
            "fc00::1",
            "fe80::1",
            "2001:10::1",
            "2001:20::1",
            "2001:db8::1",
            "2002:7f00:1::",
            "ff02::1",
        )
        blocked.forEach { assertFalse(isPublicProxyAddress(InetAddress.getByName(it)), it) }
        assertTrue(isPublicProxyAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
}
