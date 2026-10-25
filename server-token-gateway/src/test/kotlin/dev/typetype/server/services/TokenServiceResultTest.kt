package dev.typetype.server.services

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.InterruptedIOException

class TokenServiceResultTest {
    @Test
    fun `token refusal carries the status and the provider reason`() {
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond(500, BOT_CHECK_BODY))

        val result = client.fetchResult("video") as TokenServiceResult.Failure

        assertEquals(TokenServiceFailureReason.HTTP_STATUS, result.reason)
        assertEquals(500, result.status)
        assertTrue(result.isProviderRefusal())
        assertFalse(result.isTransient())
    }

    @Test
    fun `token service unavailability is transient`() {
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond(503, "{}"))

        val result = client.fetchResult("video") as TokenServiceResult.Failure

        assertEquals(503, result.status)
        assertTrue(result.isTransient())
        assertFalse(result.isProviderRefusal())
    }

    @Test
    fun `a timed out token call is reported as a timeout`() {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { throw InterruptedIOException("timeout") }).build()

        val result = TypetypeTokenSabrTokenClient("https://token.example", http).fetchResult("video")

        assertEquals(TokenServiceFailureReason.TIMEOUT, (result as TokenServiceResult.Failure).reason)
        assertNull(result.valueOrNull())
    }

    @Test
    fun `parses optional generation and expiry from the token`() {
        val body = """{"poToken":"AAAA","visitorData":"visitor","streamingPot":"BBBB","generation":"bg-7","expiresAt":1800000000000}"""
        val client = TypetypeTokenSabrTokenClient("https://token.example", respond(200, body))

        val token = client.fetch("video")

        assertEquals("bg-7", token?.generation)
        assertEquals(1_800_000_000_000L, token?.expiresAtMs)
    }

    @Test
    fun `missing generation and expiry stay unknown`() {
        val body = """{"poToken":"AAAA","visitorData":"visitor","streamingPot":"BBBB"}"""

        val token = TypetypeTokenSabrTokenClient("https://token.example", respond(200, body)).fetch("video")

        assertNull(token?.generation)
        assertNull(token?.expiresAtMs)
        assertFalse(token?.isExpired() ?: true)
    }

    @Test
    fun `sabr session token keeps the token generation`() = runTest {
        val body = """{"visitorData":"v","poToken":"AAAA","streamingPot":"BBBB","tokenGeneration":"bg-9","serverAbrStreamingUrl":"https://r.googlevideo.com/sabr?cver=2.0","adaptiveFormats":[],"videoPlaybackUstreamerConfig":"cfg"}"""
        val client = TypetypeTokenYoutubeSessionClient("https://token.example", respond(200, body))

        val session = client.fetchPlaybackSessionResult("video").valueOrNull()

        assertEquals("bg-9", session?.token?.generation)
    }

    @Test
    fun `sabr session failure keeps the token service status`() = runTest {
        val client = TypetypeTokenYoutubeSessionClient("https://token.example", respond(500, BOT_CHECK_BODY))

        val result = client.fetchPlaybackSessionResult("video") as TokenServiceResult.Failure

        assertEquals(500, result.status)
        assertTrue(result.isProviderRefusal())
    }

    @Test
    fun `tv token refresh asks the token service for a fresh token`() = runTest {
        var refresh: String? = null
        val body = """{"poToken":"p","visitorData":"v","livingRoomPoTokenId":"l","generation":"3","expiresAt":"2026-10-25T12:00:00Z"}"""
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            refresh = chain.request().url.queryParameter("refresh")
            response(chain, 200, body)
        }).build()

        val token = TypetypeTokenYoutubeSessionClient("https://token.example", http).fetchTvPoToken(refresh = true)

        assertEquals("true", refresh)
        assertEquals("3", token?.generation)
        assertEquals(1_792_929_600_000L, token?.expiresAtMs)
    }

    private fun respond(code: Int, body: String): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain -> response(chain, code, body) }).build()

    private fun response(chain: Interceptor.Chain, code: Int, body: String): Response = Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status")
        .body(body.toResponseBody())
        .build()

    private companion object {
        const val BOT_CHECK_BODY =
            """{"error":"YouTube MWEB player response is LOGIN_REQUIRED: Sign in to confirm you're not a bot"}"""
    }
}
