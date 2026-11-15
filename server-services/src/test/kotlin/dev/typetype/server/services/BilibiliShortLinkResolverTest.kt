package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BilibiliShortLinkResolverTest {
    private val requested = mutableListOf<String>()

    private fun resolver(location: String?, code: Int = 302) = BilibiliShortLinkResolver(
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requested += chain.request().url.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .apply { if (location != null) header("Location", location) }
                .body("".toResponseBody()).build()
        }).build(),
    )

    @Test
    fun `follows the redirect to a bilibili page and drops the tracking query`() = runBlocking {
        val target = resolver("https://www.bilibili.com/video/BV1xx411c7mD?p=2&share_source=copy&vd_source=abc")
            .resolve("abc123")
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD?p=2", target)
        assertEquals(listOf("https://b23.tv/abc123"), requested)
    }

    @Test
    fun `refuses a redirect to any other host`() = runBlocking {
        assertNull(resolver("https://evil.example/video/BV1xx411c7mD").resolve("abc123"))
        assertNull(resolver("https://bilibili.com.evil.example/x").resolve("abc123"))
        assertNull(resolver("http://www.bilibili.com/video/BV1xx411c7mD").resolve("abc123"))
    }

    @Test
    fun `refuses a malformed code before any request`() = runBlocking {
        assertNull(resolver("https://www.bilibili.com/video/x").resolve("../admin"))
        assertNull(resolver("https://www.bilibili.com/video/x").resolve("a"))
        assertEquals(emptyList<String>(), requested)
    }

    @Test
    fun `answers nothing when b23 does not redirect`() = runBlocking {
        assertNull(resolver(null, code = 404).resolve("abc123"))
    }

    @Test
    fun `remembers a resolved code`() = runBlocking {
        val resolver = resolver("https://www.bilibili.com/video/BV1xx411c7mD")
        resolver.resolve("abc123")
        resolver.resolve("abc123")
        assertEquals(1, requested.size)
    }
}
