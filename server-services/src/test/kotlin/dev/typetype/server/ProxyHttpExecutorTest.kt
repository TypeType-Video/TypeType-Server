package dev.typetype.server

import dev.typetype.server.services.ProxyHttpExecutor
import dev.typetype.server.services.ValidatingProxyDns
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.UnknownHostException

class ProxyHttpExecutorTest {
    @Test
    fun `blocks private resolutions before sending a request`() {
        var calls = 0
        val client = OkHttpClient.Builder()
            .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
            .addInterceptor { chain ->
                calls += 1
                ok(chain)
            }
            .build()

        assertThrows(UnknownHostException::class.java) {
            ProxyHttpExecutor(client).execute(request("https://i.ytimg.com/image.jpg"))
        }
        assertEquals(0, calls)
    }

    @Test
    fun `blocks a redirect outside the original provider`() {
        var calls = 0
        val client = testClient { chain ->
            calls += 1
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "https://example.com/collect")
                .body("".toResponseBody())
                .build()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            ProxyHttpExecutor(client).execute(request("https://i.ytimg.com/image.jpg"))
        }
        assertEquals("Unsupported proxy host", error.message)
        assertEquals(1, calls)
    }

    @Test
    fun `follows bounded redirects inside one provider`() {
        val requestedHosts = mutableListOf<String>()
        val client = testClient { chain ->
            requestedHosts += chain.request().url.host
            if (requestedHosts.size == 1) {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://yt3.ggpht.com/avatar")
                    .body("".toResponseBody())
                    .build()
            } else {
                ok(chain)
            }
        }

        ProxyHttpExecutor(client).execute(request("https://i.ytimg.com/image.jpg")).use { response ->
            assertTrue(response.isSuccessful)
        }
        assertEquals(listOf("i.ytimg.com", "yt3.ggpht.com"), requestedHosts)
    }

    @Test
    fun `rejects mixed public and private dns answers`() {
        val dns = ValidatingProxyDns(
            Dns {
                listOf(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("10.0.0.1"),
                )
            },
        )

        assertThrows(UnknownHostException::class.java) { dns.lookup("i.ytimg.com") }
    }

    @Test
    fun `rejects a later private dns rebind answer`() {
        var lookups = 0
        val dns = ValidatingProxyDns(
            Dns {
                lookups += 1
                listOf(InetAddress.getByName(if (lookups == 1) "1.1.1.1" else "10.0.0.1"))
            },
        )

        assertEquals(listOf(InetAddress.getByName("1.1.1.1")), dns.lookup("i.ytimg.com"))
        assertThrows(UnknownHostException::class.java) { dns.lookup("i.ytimg.com") }
    }

    @Test
    fun `rejects an https downgrade redirect`() {
        val client = testClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "http://i.ytimg.com/image.jpg")
                .body("".toResponseBody())
                .build()
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            ProxyHttpExecutor(client).execute(request("https://i.ytimg.com/image.jpg"))
        }
        assertEquals("Unsupported URL scheme: http", error.message)
    }

    @Test
    fun `stops a redirect loop at the configured bound`() {
        var calls = 0
        val client = testClient { chain ->
            calls += 1
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "/next")
                .body("".toResponseBody())
                .build()
        }

        val error = assertThrows(java.io.IOException::class.java) {
            ProxyHttpExecutor(client, maxRedirects = 2).execute(request("https://i.ytimg.com/image.jpg"))
        }
        assertEquals("Too many proxy redirects", error.message)
        assertEquals(3, calls)
    }

    private fun testClient(interceptor: Interceptor): OkHttpClient = OkHttpClient.Builder()
        .dns(Dns { listOf(InetAddress.getByName("1.1.1.1")) })
        .addInterceptor(interceptor)
        .build()

    private fun request(url: String): Request = Request.Builder().url(url).build()

    private fun ok(chain: Interceptor.Chain): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("ok".toResponseBody())
        .build()
}
