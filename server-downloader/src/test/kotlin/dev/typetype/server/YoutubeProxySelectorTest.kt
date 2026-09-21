package dev.typetype.server

import dev.typetype.server.downloader.YoutubeProxySelector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class YoutubeProxySelectorTest {
    @Test
    fun `routes youtube extraction and media hosts through proxy`() {
        val selector = YoutubeProxySelector.fromUrl("http://proxy.internal:8080")!!

        listOf(
            "https://www.youtube.com/youtubei/v1/player",
            "https://youtu.be/video-id",
            "https://youtubei.googleapis.com/youtubei/v1/player",
            "https://rr1---sn.example.googlevideo.com/videoplayback",
            "https://i.ytimg.com/vi/id/hqdefault.jpg",
            "https://yt3.googleusercontent.com/avatar",
        ).forEach { url ->
            val proxy = selector.select(URI(url)).single()
            assertEquals(Proxy.Type.HTTP, proxy.type())
            assertEquals(InetSocketAddress.createUnresolved("proxy.internal", 8080), proxy.address())
        }
    }

    @Test
    fun `keeps unrelated and deceptive hosts direct`() {
        val selector = YoutubeProxySelector.fromUrl("http://proxy.internal:8080")!!

        listOf(
            "http://typetype-token:8081/youtube/sabr/session",
            "https://www.nicovideo.jp/watch/id",
            "https://evilgooglevideo.com/video",
        ).forEach { url ->
            assertEquals(Proxy.NO_PROXY, selector.select(URI(url)).single())
        }
    }

    @Test
    fun `accepts an absent proxy and defaults the http port`() {
        assertNull(YoutubeProxySelector.fromUrl(null))
        assertNull(YoutubeProxySelector.fromUrl("  "))

        val selector = YoutubeProxySelector.fromUrl("http://proxy.internal")!!
        val address = selector.select(URI("https://www.youtube.com")).first().address()
        assertEquals(InetSocketAddress.createUnresolved("proxy.internal", 80), address)
    }

    @Test
    fun `rejects unsupported or ambiguous proxy urls`() {
        listOf(
            "https://proxy.internal:8080",
            "http:///missing-host",
            "http://user:pass@proxy.internal:8080",
            "http://proxy.internal:8080/path",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                YoutubeProxySelector.fromUrl(value)
            }
        }
    }
}
