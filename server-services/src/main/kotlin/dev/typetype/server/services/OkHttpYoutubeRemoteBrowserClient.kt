package dev.typetype.server.services

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OkHttpYoutubeRemoteBrowserClient(
    private val serviceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) : YoutubeRemoteBrowserClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun start(request: YoutubeRemoteBrowserTokenStartRequest, internalToken: String): YoutubeRemoteBrowserTokenStartResponse? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("${serviceUrl.trimEnd('/')}/youtube-remote-login/start")
            .header(INTERNAL_HEADER, internalToken)
            .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatching {
            client.newCall(httpRequest).execute().use(::decodeStartResponse)
        }.getOrNull()
    }

    override suspend fun cancel(tokenSessionId: String, internalToken: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url("${serviceUrl.trimEnd('/')}/youtube-remote-login/$tokenSessionId")
                .header(INTERNAL_HEADER, internalToken)
                .delete()
                .build()
            runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
        }

    override suspend fun bridge(
        serverSession: DefaultWebSocketServerSession,
        tokenSessionId: String,
        internalToken: String,
        config: YoutubeRemoteBrowserConfig,
    ): Unit = coroutineScope {
        val done = CompletableDeferred<Unit>()
        val outbound = Channel<Frame>(config.outboundQueueSize, BufferOverflow.DROP_OLDEST)
        val listener = YoutubeRemoteBrowserBridgeListener(tokenSessionId, outbound, done, config)
        val socket = client.newWebSocket(tokenWebSocketRequest(tokenSessionId, internalToken), listener)
        var inputs = 0
        val outboundJob = launch { for (frame in outbound) serverSession.send(frame) }
        val inboundJob = launch {
            for (frame in serverSession.incoming) {
                if (frame is Frame.Text) {
                    YoutubeRemoteBrowserMessageGuard.frontendText(frame.readText(), config.maxInputBytes)?.let {
                        inputs += 1
                        socket.send(it)
                    }
                }
                if (frame is Frame.Close) done.complete(Unit)
            }
        }
        outboundJob.invokeOnCompletion { done.complete(Unit) }
        inboundJob.invokeOnCompletion { done.complete(Unit) }
        done.await()
        socket.close(NORMAL_CLOSE, null)
        outbound.close()
        outboundJob.cancel()
        inboundJob.cancel()
        YoutubeRemoteBrowserAudit.bridgeEnded(tokenSessionId, listener.texts.get(), listener.frames.get(), inputs)
    }

    private fun decodeStartResponse(response: Response): YoutubeRemoteBrowserTokenStartResponse? {
        if (!response.isSuccessful) return null
        return json.decodeFromString<YoutubeRemoteBrowserTokenStartResponse>(response.body.string())
    }

    private fun tokenWebSocketRequest(tokenSessionId: String, internalToken: String): Request =
        Request.Builder()
            .url("${webSocketBaseUrl()}/youtube-remote-login/$tokenSessionId")
            .header(INTERNAL_HEADER, internalToken)
            .build()


    private fun webSocketBaseUrl(): String =
        serviceUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")

    companion object {
        private const val INTERNAL_HEADER = "X-Internal-Token"
        private const val NORMAL_CLOSE = 1000
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
