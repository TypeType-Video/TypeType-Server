package dev.typetype.server.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Proxy

class AiOutboundProxyTest {
    @Test
    fun `returns null when proxy url is null or blank`() {
        assertNull(AiOutboundProxy.fromUrl(null))
        assertNull(AiOutboundProxy.fromUrl(""))
        assertNull(AiOutboundProxy.fromUrl("   "))
    }

    @Test
    fun `parses http proxy with explicit and default ports`() {
        val explicit = AiOutboundProxy.fromUrl("  http://proxy.internal:10808 ")
        assertNotNull(explicit)
        assertEquals(Proxy.Type.HTTP, explicit?.type())
        assertEquals(InetSocketAddress.createUnresolved("proxy.internal", 10808), explicit?.address())

        val implicit = AiOutboundProxy.fromUrl("HTTP://proxy.internal")
        assertNotNull(implicit)
        assertEquals(Proxy.Type.HTTP, implicit?.type())
        assertEquals(InetSocketAddress.createUnresolved("proxy.internal", 80), implicit?.address())
    }

    @Test
    fun `parses socks5 proxy with explicit and default ports`() {
        val explicit = AiOutboundProxy.fromUrl("socks5://192.0.2.10:10809")
        assertNotNull(explicit)
        assertEquals(Proxy.Type.SOCKS, explicit?.type())
        assertEquals(InetSocketAddress.createUnresolved("192.0.2.10", 10809), explicit?.address())

        val implicit = AiOutboundProxy.fromUrl("SOCKS5://192.0.2.10")
        assertNotNull(implicit)
        assertEquals(Proxy.Type.SOCKS, implicit?.type())
        assertEquals(InetSocketAddress.createUnresolved("192.0.2.10", 1080), implicit?.address())
    }

    @Test
    fun `rejects invalid schemes hosts ports and extra url components`() {
        val invalidUrls = listOf(
            "https://proxy.internal:443",
            "ftp://proxy.internal:21",
            "http://:8080",
            "http://user:pass@proxy.internal:8080",
            "http://proxy.internal:8080/path",
            "http://proxy.internal:8080?x=1",
            "http://proxy.internal:8080#frag",
            "http://proxy.internal:",
            "http://proxy.internal:0",
            "http://proxy.internal:70000",
            "not a url",
        )
        invalidUrls.forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) {
                AiOutboundProxy.fromUrl(raw)
            }
        }
    }
}
