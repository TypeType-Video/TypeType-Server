package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenServiceReadinessTest {
    @Test
    fun `a token service that is up but not ready is not healthy`() = runTest {
        val paths = mutableListOf<String>()

        val ready = TokenServiceReadiness("https://token.example", answer(paths, ready = 503, health = 200)).isReady()

        assertFalse(ready)
        assertEquals(listOf("/ready"), paths)
    }

    @Test
    fun `a ready token service is healthy`() = runTest {
        assertTrue(TokenServiceReadiness("https://token.example", answer(mutableListOf(), ready = 200, health = 200)).isReady())
    }

    @Test
    fun `an older token image without ready falls back to health`() = runTest {
        val paths = mutableListOf<String>()

        val ready = TokenServiceReadiness("https://token.example", answer(paths, ready = 404, health = 200)).isReady()

        assertTrue(ready)
        assertEquals(listOf("/ready", "/health"), paths)
    }

    private fun answer(paths: MutableList<String>, ready: Int, health: Int): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val path = chain.request().url.encodedPath
            paths += path
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (path == "/ready") ready else health)
                .message("status")
                .body("{}".toResponseBody())
                .build()
        }).build()
}
