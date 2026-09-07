package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrRecoverableException
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrStreamState

class TypetypeTokenSabrTokenClientTest {

    @Test
    fun forceRefreshUsesVisitorRefresh(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example/base/", recorder.client)

        val token = client.fetch("a b", forceRefresh = true)

        assertNotNull(token)
        val url = recorder.urls.single()
        assertEquals("a b", url.queryParameter("videoId"))
        assertEquals("true", url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    @Test
    fun refreshVideoUsesVideoRefreshOnly(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)

        val token = client.fetch("video", refreshVideo = true)

        assertNotNull(token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertNull(url.queryParameter("refresh"))
        assertEquals("true", url.queryParameter("refreshVideo"))
    }

    @Test
    fun providerFetchesVideoTokenWithoutRefresh(): Unit {
        val recorder = PotokenRequestRecorder()
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)
        val provider = TypetypeTokenSabrPoTokenProvider(client)
        val token = provider.getPoToken(info("visitor"), mockk())

        assertNotNull(token)
        assertArrayEquals(byteArrayOf(2), token)
        val url = recorder.urls.single()
        assertEquals("video", url.queryParameter("videoId"))
        assertNull(url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    @Test
    fun providerRejectsVideoTokenFromDifferentVisitorSession(): Unit {
        val recorder = PotokenRequestRecorder(MISMATCHED_TOKEN_JSON)
        val provider = TypetypeTokenSabrPoTokenProvider(
            TypetypeTokenSabrTokenClient("https://token.example", recorder.client),
        )

        val error = assertThrows(SabrRecoverableException::class.java) {
            provider.getPoToken(info("visitor"), mockk())
        }

        assertEquals(SABR_TOKEN_BINDING_FAILURE, error.message)
        val url = recorder.urls.single()
        assertNull(url.queryParameter("refresh"))
        assertNull(url.queryParameter("refreshVideo"))
    }

    @Test
    fun sessionFetchPostsOneExplicitlyBoundTokenPair(): Unit {
        val recorder = PotokenRequestRecorder(SESSION_TOKEN_JSON)
        val client = TypetypeTokenSabrTokenClient("https://token.example", recorder.client)

        val token = client.fetchSession("video", "connected-visitor", refreshVideo = true)

        assertNotNull(token)
        val request = recorder.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/potoken/session", request.url.encodedPath)
        val body = JSONObject(request.body!!.let { body -> okio.Buffer().also(body::writeTo).readUtf8() })
        assertEquals("video", body.getString("videoId"))
        assertEquals("connected-visitor", body.getString("sessionBinding"))
        assertEquals(true, body.getBoolean("refreshVideo"))
        assertArrayEquals(byteArrayOf(2), token!!.streamingPoTokenBytesFor(info("connected-visitor")))
        assertNull(token.streamingPoTokenBytesFor(info("different-visitor")))
    }

    private fun info(expectedVisitorData: String): YoutubeSabrInfo = mockk {
        every { videoId } returns "video"
        every { visitorData } returns expectedVisitorData
    }

    private class PotokenRequestRecorder(tokenJson: String = TOKEN_JSON) {
        val requests = mutableListOf<Request>()
        val urls: List<okhttp3.HttpUrl> get() = requests.map { it.url }
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(tokenJson.toResponseBody())
                    .build()
            })
            .build()
    }

    private companion object {
        const val TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"visitor","videoBoundPoToken":"Ag"}"""
        const val MISMATCHED_TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"other-visitor","videoBoundPoToken":"Ag"}"""
        const val SESSION_TOKEN_JSON =
            """{"visitorBoundPoToken":"AQ","visitorData":"public","videoBoundPoToken":"Ag","sessionBoundPoToken":"Aw"}"""
    }
}
