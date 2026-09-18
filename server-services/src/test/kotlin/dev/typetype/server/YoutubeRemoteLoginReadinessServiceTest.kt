package dev.typetype.server

import dev.typetype.server.models.YoutubeRemoteLoginStatus
import dev.typetype.server.services.YoutubeRemoteBrowserConfig
import dev.typetype.server.services.YoutubeRemoteLoginReadinessService
import dev.typetype.server.services.YoutubeSessionCrypto
import dev.typetype.server.services.YoutubeSessionService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YoutubeRemoteLoginReadinessServiceTest {
    @Test
    fun `disabled setting does not call token`() = runBlocking {
        val calls = Counter()
        val service = service(config("secret"), sessionConfigured = true, client = client(200, calls))

        assertEquals(YoutubeRemoteLoginStatus.Disabled, service.status(adminEnabled = false))
        assertEquals(0, calls.value)
    }

    @Test
    fun `missing config returns not configured`() = runBlocking {
        val calls = Counter()
        val service = service(config(null), sessionConfigured = true, client = client(200, calls))

        assertEquals(YoutubeRemoteLoginStatus.NotConfigured, service.status(adminEnabled = true))
        assertEquals(0, calls.value)
    }

    @Test
    fun `token without capability endpoint returns token unreachable`() = runBlocking {
        val service = service(config("secret"), sessionConfigured = true, client = client(404))

        assertEquals(YoutubeRemoteLoginStatus.TokenUnreachable, service.status(adminEnabled = true))
    }

    @Test
    fun `token capability success returns ready`() = runBlocking {
        val recorder = RequestRecorder()
        val service = service(config("secret"), sessionConfigured = true, client = client(204, recorder = recorder))

        assertEquals(YoutubeRemoteLoginStatus.Ready, service.status(adminEnabled = true))
        assertEquals("POST", recorder.method)
        assertEquals("/youtube-remote-login/readiness", recorder.path)
        assertEquals("secret", recorder.internalToken)
        assertEquals("{\"callbackUrl\":\"http://server/internal/youtube-remote-login/callback\"}", recorder.body)
    }

    private fun service(
        config: YoutubeRemoteBrowserConfig,
        sessionConfigured: Boolean,
        client: OkHttpClient,
    ): YoutubeRemoteLoginReadinessService =
        YoutubeRemoteLoginReadinessService(
            config,
            YoutubeSessionService(if (sessionConfigured) YoutubeSessionCrypto.fromSecret("test-youtube-session-key-32-bytes") else null),
            client,
        )

    private fun config(internalToken: String?): YoutubeRemoteBrowserConfig =
        YoutubeRemoteBrowserConfig("http://token", "http://server", internalToken, 480_000, 2, 524_288, 4096, 2)

    private fun client(
        code: Int,
        calls: Counter = Counter(),
        recorder: RequestRecorder? = null,
    ): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            calls.value += 1
            recorder?.record(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body("{}".toResponseBody())
                .build()
        }).build()

    private class Counter {
        var value: Int = 0
    }

    private class RequestRecorder {
        var method: String? = null
        var path: String? = null
        var internalToken: String? = null
        var body: String? = null

        fun record(request: okhttp3.Request) {
            method = request.method
            path = request.url.encodedPath
            internalToken = request.header("X-Internal-Token")
            body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        }
    }
}
