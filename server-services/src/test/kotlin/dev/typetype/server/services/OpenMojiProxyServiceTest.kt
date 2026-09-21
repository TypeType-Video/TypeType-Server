package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenMojiProxyServiceTest {
    @Test
    fun `successful SVG is served from the bounded local cache`() = runTest {
        var requests = 0
        val bytes = "<svg />".toByteArray()
        val client = client {
            requests++
            ResponseBody(bytes)
        }
        val service = OpenMojiProxyService(EmptyCache, client)

        assertArrayEquals(bytes, service.getSvg("1F60A"))
        assertArrayEquals(bytes, service.getSvg("1F60A"))
        assertEquals(1, requests)
    }

    @Test
    fun `failed fetch is cooled down and retried after expiry`() = runTest {
        var now = 0L
        var requests = 0
        val client = client {
            requests++
            ResponseBody("unavailable".toByteArray(), code = 503)
        }
        val service = OpenMojiProxyService(EmptyCache, client, clock = { now })

        assertNull(service.getSvg("missing"))
        assertNull(service.getSvg("missing"))
        assertEquals(1, requests)
        now = 15_000L
        assertNull(service.getSvg("missing"))
        assertEquals(2, requests)
    }

    private fun client(response: () -> ResponseBody): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val body = response()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(body.code)
                .message("test")
                .body(body.bytes.toResponseBody("image/svg+xml".toMediaType()))
                .build()
        }
        .build()

    private data class ResponseBody(val bytes: ByteArray, val code: Int = 200)

    private object EmptyCache : CacheService {
        override suspend fun get(key: String): String? = null
        override suspend fun set(key: String, value: String, ttlSeconds: Long): Unit = Unit
        override suspend fun delete(key: String): Unit = Unit
    }
}
