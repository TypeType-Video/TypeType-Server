package dev.typetype.server.services

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProviderSessionEpochTest {
    @Test
    fun `a forced session refresh advances the epoch`() {
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond(token("visitor-a")))

        client.fetch("video")
        client.fetch("video", forceRefresh = true)

        assertEquals(1L, client.sessionEpoch.current())
    }

    @Test
    fun `a new visitor identity from the token service advances the epoch`() {
        val bodies = ArrayDeque(listOf(token("visitor-a"), token("visitor-a"), token("visitor-b")))
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond { bodies.removeFirst() })

        repeat(3) { client.fetch("video") }

        assertEquals(1L, client.sessionEpoch.current())
    }

    @Test
    fun `a new token generation advances the epoch even for the same visitor`() {
        val bodies = ArrayDeque(listOf(token("visitor-a", "g-1"), token("visitor-a", "g-1"), token("visitor-a", "g-0")))
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond { bodies.removeFirst() })

        repeat(3) { client.fetch("video") }

        assertEquals(1L, client.sessionEpoch.current())
    }

    @Test
    fun `a refresh answered with the same generation keeps the epoch`() {
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond(token("visitor-a", "g-1")))

        client.fetch("video")
        client.fetch("video", forceRefresh = true)

        assertEquals(0L, client.sessionEpoch.current())
    }

    @Test
    fun `a failed refresh keeps the epoch`() {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain -> response(chain, 500, "{}") }).build()
        val client = TypetypeTokenSabrTokenClient("https://token.example", http)

        client.fetch("video", forceRefresh = true)

        assertEquals(0L, client.sessionEpoch.current())
    }

    private fun token(visitor: String, generation: String? = null): String =
        """{"poToken":"AAAA","visitorData":"$visitor","streamingPot":"BBBB"${generation?.let { ",\"generation\":\"$it\"" } ?: ""}}"""

    private fun respond(body: String): OkHttpClient = respond { body }

    private fun respond(body: () -> String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain -> response(chain, 200, body()) }).build()

    private fun response(chain: Interceptor.Chain, code: Int, body: String): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status")
        .body(body.toResponseBody())
        .build()
}
